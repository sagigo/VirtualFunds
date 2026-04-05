using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
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
    private readonly Guid _portfolioId;

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
    /// <param name="portfolioId">The portfolio whose history to display.</param>
    public TransactionHistoryViewModel(ITransactionService transactionService, Guid portfolioId)
    {
        _transactionService = transactionService;
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
    // Property change hooks — re-apply filters when filter values change.
    // -----------------------------------------------------------------------------------------

    partial void OnFilterFromDateChanged(DateTime? value) => ApplyFilters();
    partial void OnFilterToDateChanged(DateTime? value) => ApplyFilters();
    partial void OnSelectedFundFilterChanged(FundFilterOption? value) => ApplyFilters();
    partial void OnSelectedTypeFilterChanged(TransactionTypeFilter? value) => ApplyFilters();
}
