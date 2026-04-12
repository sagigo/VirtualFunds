using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.Core.Utilities;

namespace VirtualFunds.WPF.ViewModels;

/// <summary>
/// ViewModel for the transaction history tab within the portfolio detail screen (PR-7, E7.6).
/// Loads full history for a portfolio, supports filtering by date range, fund, and type,
/// and exposes CSV export functionality.
/// <para>
/// Filtering is done client-side after an initial full load.
/// This is appropriate for a personal portfolio app with moderate data volume.
/// </para>
/// </summary>
public sealed partial class TransactionHistoryViewModel : ObservableObject
{
    private readonly ITransactionService _transactionService;
    private readonly IFundService _fundService;
    private readonly Guid _portfolioId;

    /// <summary>
    /// Transaction types that can be undone (E6.12). Structural operations,
    /// scheduled deposits, and undo operations themselves are not undoable.
    /// </summary>
    private static readonly HashSet<TransactionType> UndoableTypes =
    [
        TransactionType.FundDeposit,
        TransactionType.FundWithdrawal,
        TransactionType.Transfer,
        TransactionType.PortfolioRevalued,
    ];

    /// <summary>All loaded transaction groups (unfiltered). Used as the source for client-side filtering.</summary>
    private IReadOnlyList<TransactionGroup> _allGroups = Array.Empty<TransactionGroup>();

    // -----------------------------------------------------------------------------------------
    // Events — the View code-behind subscribes to handle UI-specific concerns.
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Raised when the user clicks "Export to CSV". The View shows a SaveFileDialog
    /// and returns the chosen file path, or null if cancelled.
    /// </summary>
    public event Func<Task<string?>>? CsvExportPathRequested;

    /// <summary>
    /// Raised when the ViewModel needs a yes/no confirmation from the user (e.g. before undo).
    /// Parameter: the message to display. Returns: true if confirmed.
    /// </summary>
    public event Func<string, Task<bool>>? ConfirmationRequested;

    /// <summary>
    /// Raised after a successful undo operation so the parent ViewModel can refresh fund balances.
    /// </summary>
    public event Func<Task>? UndoCompleted;

    // -----------------------------------------------------------------------------------------
    // Observable properties
    // -----------------------------------------------------------------------------------------

    /// <summary>The filtered list of transaction groups displayed in the history list.</summary>
    [ObservableProperty]
    private ObservableCollection<TransactionGroup> _transactions = new();

    /// <summary>True while loading history from the server.</summary>
    [ObservableProperty]
    private bool _isLoading;

    /// <summary>Hebrew error message, or empty when there is no error.</summary>
    [ObservableProperty]
    private string _errorMessage = string.Empty;

    /// <summary>True when history has been loaded but no results match the current filters.</summary>
    [ObservableProperty]
    private bool _isEmpty;

    // -----------------------------------------------------------------------------------------
    // Filter properties
    // -----------------------------------------------------------------------------------------

    /// <summary>Date range filter: start date (inclusive). Null means no lower bound.</summary>
    [ObservableProperty]
    private DateTime? _filterFromDate;

    /// <summary>Date range filter: end date (inclusive). Null means no upper bound.</summary>
    [ObservableProperty]
    private DateTime? _filterToDate;

    /// <summary>Available funds for the fund filter dropdown (active + deleted).</summary>
    [ObservableProperty]
    private ObservableCollection<FundFilterOption> _fundFilterOptions = new();

    /// <summary>Currently selected fund filter, or null for "all funds".</summary>
    [ObservableProperty]
    private FundFilterOption? _selectedFundFilter;

    /// <summary>Available transaction types for the type filter dropdown.</summary>
    [ObservableProperty]
    private ObservableCollection<TransactionTypeFilter> _typeFilterOptions = new();

    /// <summary>Currently selected type filter, or null for "all types".</summary>
    [ObservableProperty]
    private TransactionTypeFilter? _selectedTypeFilter;

    // -----------------------------------------------------------------------------------------

    /// <summary>Initializes the ViewModel for a specific portfolio's history.</summary>
    /// <param name="transactionService">The transaction service (injected from DI).</param>
    /// <param name="fundService">The fund service, used for undo operations (injected from DI).</param>
    /// <param name="portfolioId">The portfolio whose history to display.</param>
    public TransactionHistoryViewModel(ITransactionService transactionService, IFundService fundService, Guid portfolioId)
    {
        _transactionService = transactionService;
        _fundService = fundService;
        _portfolioId = portfolioId;

        // Populate the type filter options (static, from E5.2).
        TypeFilterOptions = new ObservableCollection<TransactionTypeFilter>(
            TransactionTypeLabels.GetFilterOptions());
    }

    // -----------------------------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Loads the full transaction history from the server and populates filter options.
    /// Called when the history tab is first shown.
    /// </summary>
    [RelayCommand]
    private async Task LoadHistoryAsync()
    {
        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            // Load history and fund filter options in parallel.
            var historyTask = _transactionService.GetHistoryAsync(_portfolioId);
            var fundsTask = _transactionService.GetFundFilterOptionsAsync(_portfolioId);

            await Task.WhenAll(historyTask, fundsTask);

            _allGroups = historyTask.Result;
            ComputeUndoability(_allGroups);
            FundFilterOptions = new ObservableCollection<FundFilterOption>(fundsTask.Result);

            ApplyFilters();
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בטעינת ההיסטוריה. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Applies the current filter settings and refreshes the displayed list.
    /// Called automatically when any filter property changes.
    /// </summary>
    [RelayCommand]
    private void ApplyFilters()
    {
        IEnumerable<TransactionGroup> filtered = _allGroups;

        // Date range filter (compare as local dates).
        if (FilterFromDate.HasValue)
        {
            var fromUtc = FilterFromDate.Value.Date;
            filtered = filtered.Where(g => g.CommittedAtUtc.ToLocalTime().Date >= fromUtc);
        }

        if (FilterToDate.HasValue)
        {
            var toUtc = FilterToDate.Value.Date;
            filtered = filtered.Where(g => g.CommittedAtUtc.ToLocalTime().Date <= toUtc);
        }

        // Fund filter (E7.6): an operation matches if any of its detail rows has the selected fund_id.
        if (SelectedFundFilter is not null)
        {
            var fundId = SelectedFundFilter.FundId;
            filtered = filtered.Where(g => g.Details.Any(d => d.FundId == fundId));
        }

        // Type filter (E7.6): applied to the summary row's transaction_type.
        if (SelectedTypeFilter is not null)
        {
            var type = SelectedTypeFilter.TypeValue;
            filtered = filtered.Where(g => g.TransactionType == type);
        }

        Transactions = new ObservableCollection<TransactionGroup>(filtered);
        IsEmpty = Transactions.Count == 0;
    }

    /// <summary>
    /// Clears all filters and shows the full history.
    /// </summary>
    [RelayCommand]
    private void ClearFilters()
    {
        FilterFromDate = null;
        FilterToDate = null;
        SelectedFundFilter = null;
        SelectedTypeFilter = null;
        ApplyFilters();
    }

    /// <summary>
    /// Exports the currently filtered history to a CSV file.
    /// Delegates file path selection to the View via <see cref="CsvExportPathRequested"/>.
    /// </summary>
    [RelayCommand]
    private async Task ExportCsvAsync()
    {
        if (Transactions.Count == 0)
        {
            ErrorMessage = "אין נתונים לייצוא.";
            return;
        }

        var filePath = await CsvExportPathRequested!.Invoke();

        if (filePath is null)
            return; // User cancelled.

        ErrorMessage = string.Empty;

        try
        {
            await TransactionCsvExporter.ExportToFileAsync(Transactions.ToList(), filePath);
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בייצוא הקובץ. נסה שוב.";
        }
    }

    // -----------------------------------------------------------------------------------------
    // Undo (E6.12 — history-based)
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Undoes the specified operation by creating compensating history rows (E6.12).
    /// Triggered from the per-row undo button in the transaction history panel.
    /// </summary>
    [RelayCommand]
    private async Task UndoOperationAsync(TransactionGroup group)
    {
        if (!group.IsUndoable)
            return;

        if (ConfirmationRequested is null ||
            !await ConfirmationRequested.Invoke($"האם לבטל את הפעולה \"{group.TransactionTypeLabel}\"?"))
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _fundService.UndoOperationAsync(_portfolioId, group.OperationId);

            // Reload history to reflect the new undo transaction and update IsUndoable flags.
            await LoadHistoryAsync();

            // Notify parent ViewModel to refresh fund balances.
            if (UndoCompleted is not null)
                await UndoCompleted.Invoke();
        }
        catch (InsufficientFundBalanceException)
        {
            ErrorMessage = "לא ניתן לבטל — הפעולה תגרום ליתרה שלילית באחת הקרנות.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לבצע פעולה בתיק סגור.";
        }
        catch (FundNotFoundException)
        {
            ErrorMessage = "אחת הקרנות שהושפעו מהפעולה לא נמצאה.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בביטול הפעולה. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Computes and sets <see cref="TransactionGroup.IsUndoable"/> on each group.
    /// A group is undoable when its transaction type is in <see cref="UndoableTypes"/>
    /// and no other group references its operation_id via <c>UndoOfOperationId</c>.
    /// </summary>
    private static void ComputeUndoability(IReadOnlyList<TransactionGroup> groups)
    {
        // Collect all operation_ids that have already been undone.
        var undoneOperationIds = new HashSet<Guid>(
            groups
                .Where(g => g.UndoOfOperationId.HasValue)
                .Select(g => g.UndoOfOperationId!.Value)); // ! safe: filtered by HasValue above

        foreach (var group in groups)
        {
            group.IsUndoable =
                UndoableTypes.Contains(group.TransactionType) &&
                !undoneOperationIds.Contains(group.OperationId);
        }
    }

    // -----------------------------------------------------------------------------------------
    // Property change hooks — re-apply filters when filter values change.
    // -----------------------------------------------------------------------------------------

    partial void OnFilterFromDateChanged(DateTime? value) => ApplyFilters();
    partial void OnFilterToDateChanged(DateTime? value) => ApplyFilters();
    partial void OnSelectedFundFilterChanged(FundFilterOption? value) => ApplyFilters();
    partial void OnSelectedTypeFilterChanged(TransactionTypeFilter? value) => ApplyFilters();
}
