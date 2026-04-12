using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.Core.Utilities;

namespace VirtualFunds.WPF.ViewModels;

/// <summary>
/// ViewModel for the portfolio detail screen — fund list and fund management (PR-5, E5.4–E5.10).
/// Shows the funds within a single portfolio with name, balance, and allocation percentage.
/// Header buttons allow renaming and deleting the portfolio itself.
/// Context menu on fund items provides Rename and Delete actions for individual funds.
/// <para>
/// UI interactions that require dialogs (name input, fund creation, confirmation) are delegated
/// to the View via events, keeping the ViewModel testable without UI dependencies.
/// </para>
/// </summary>
public sealed partial class PortfolioViewModel : ObservableObject
{
    private readonly IFundService _fundService;
    private readonly IPortfolioService _portfolioService;
    private readonly IScheduledDepositService _scheduledDepositService;
    private readonly IDeviceIdStore _deviceIdStore;
    private readonly Guid _portfolioId;
    private readonly TransactionHistoryViewModel _historyViewModel;

    // -----------------------------------------------------------------------------------------
    // Events — the View code-behind subscribes to handle UI-specific concerns.
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Raised when the ViewModel needs a name from the user (fund rename).
    /// Parameters: (dialog title, existing name).
    /// Returns: the entered name, or <c>null</c> if the user cancelled.
    /// </summary>
    public event Func<string, string?, Task<string?>>? NameInputRequested;

    /// <summary>
    /// Raised when the ViewModel needs fund creation input (name + optional initial amount).
    /// Returns: a tuple of (name, amountAgoras), or <c>null</c> if the user cancelled.
    /// </summary>
    public event Func<Task<(string Name, long AmountAgoras)?>>? FundCreateRequested;

    /// <summary>
    /// Raised when the ViewModel needs a yes/no confirmation from the user.
    /// Parameter: the message to display.
    /// Returns: <c>true</c> if the user confirmed.
    /// </summary>
    public event Func<string, Task<bool>>? ConfirmationRequested;

    /// <summary>
    /// Raised when the ViewModel needs a positive shekel amount from the user (deposit / withdrawal).
    /// Parameters: (dialog title, fund name for display).
    /// Returns: the amount in agoras, or <c>null</c> if the user cancelled.
    /// </summary>
    public event Func<string, string, Task<long?>>? AmountInputRequested;

    /// <summary>
    /// Raised when the ViewModel needs transfer input from the user (E6.10).
    /// Parameters: (source fund, list of other funds).
    /// Returns: a tuple of (destinationFundId, amountAgoras), or <c>null</c> if the user cancelled.
    /// </summary>
    public event Func<FundListItem, IReadOnlyList<FundListItem>, Task<(Guid DestinationFundId, long AmountAgoras)?>>? TransferRequested;

    /// <summary>
    /// Raised when the user wants to go back to the portfolio list.
    /// The View should navigate to MainWindow.
    /// </summary>
    public event Action? BackRequested;

    /// <summary>
    /// Raised when the user wants to manage scheduled deposits (PR-8).
    /// The View should show the <see cref="ScheduledDepositsViewModel"/> in a dialog.
    /// </summary>
    public event Func<ScheduledDepositsViewModel, Task>? ScheduledDepositsRequested;

    // -----------------------------------------------------------------------------------------
    // Sort state (E5.10) — presentation-only, no money logic depends on this
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Remembers the current custom order as a sequence of fund IDs.
    /// Populated when the user switches to Custom mode or moves funds.
    /// In-memory only — resets when the app restarts (Option A, YAGNI).
    /// </summary>
    private List<Guid> _customOrderedFundIds = new();

    /// <summary>Available sort options shown in the sort dropdown.</summary>
    public IReadOnlyList<FundSortOption> SortOptions { get; } = new List<FundSortOption>
    {
        new("תאריך יצירה", FundSortMode.CreatedDate),
        new("שם", FundSortMode.Name),
        new("יתרה", FundSortMode.Balance),
        new("אחוז הקצאה", FundSortMode.AllocationPercent),
        new("מותאם אישית", FundSortMode.Custom),
    };

    /// <summary>The currently selected sort option (default: by name).</summary>
    [ObservableProperty]
    private FundSortOption _selectedSortOption = null!; // assigned in constructor before any use

    // -----------------------------------------------------------------------------------------
    // Observable properties
    // -----------------------------------------------------------------------------------------

    /// <summary>The list of funds in this portfolio.</summary>
    [ObservableProperty]
    private ObservableCollection<FundListItem> _funds = new();

    /// <summary>The currently selected fund in the list.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanExecuteFundOperation))]
    private FundListItem? _selectedFund;

    /// <summary>True while a service operation is in progress.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanExecuteFundOperation))]
    private bool _isLoading;

    /// <summary>Hebrew error message, or empty when there is no error.</summary>
    [ObservableProperty]
    private string _errorMessage = string.Empty;

    /// <summary>True when funds have been loaded but the list is empty.</summary>
    [ObservableProperty]
    private bool _isEmpty;

    /// <summary>The display name of the portfolio (shown in the header).</summary>
    [ObservableProperty]
    private string _portfolioName;

    /// <summary>
    /// Human-readable formatted portfolio total (e.g. "1,234.56 ₪").
    /// Computed from the sum of all fund balances after loading.
    /// </summary>
    [ObservableProperty]
    private string _formattedTotal = MoneyFormatter.FormatAgoras(0);

    /// <summary>True when a fund is selected and no operation is in progress — enables fund toolbar buttons.</summary>
    public bool CanExecuteFundOperation => !IsLoading && SelectedFund != null;

    // -----------------------------------------------------------------------------------------

    /// <summary>Initializes the ViewModel for a specific portfolio.</summary>
    /// <param name="fundService">The fund service (injected from DI).</param>
    /// <param name="portfolioService">The portfolio service, used for rename and delete (injected from DI).</param>
    /// <param name="scheduledDepositService">The scheduled deposit service (PR-8).</param>
    /// <param name="deviceIdStore">The device ID store for scheduled deposit execution (E8.4).</param>
    /// <param name="portfolioId">The ID of the portfolio to display.</param>
    /// <param name="portfolioName">The display name of the portfolio.</param>
    /// <param name="historyViewModel">The history sub-ViewModel for the History tab (PR-7).</param>
    public PortfolioViewModel(
        IFundService fundService,
        IPortfolioService portfolioService,
        IScheduledDepositService scheduledDepositService,
        IDeviceIdStore deviceIdStore,
        Guid portfolioId,
        string portfolioName,
        TransactionHistoryViewModel historyViewModel)
    {
        _fundService = fundService;
        _portfolioService = portfolioService;
        _scheduledDepositService = scheduledDepositService;
        _deviceIdStore = deviceIdStore;
        _portfolioId = portfolioId;
        _portfolioName = portfolioName;
        _historyViewModel = historyViewModel;

        // Default sort mode: alphabetical by name (matches server order, E5.10).
        _selectedSortOption = SortOptions[0];

        // When an undo operation completes in the history panel, refresh fund balances.
        _historyViewModel.UndoCompleted += async () => await LoadFundsAsync();
    }

    /// <summary>The portfolio ID this ViewModel operates on.</summary>
    public Guid PortfolioId => _portfolioId;

    /// <summary>The sub-ViewModel for the transaction history tab (PR-7).</summary>
    public TransactionHistoryViewModel HistoryViewModel => _historyViewModel;

    // -----------------------------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Loads funds from the server and updates the list.
    /// Called on startup and after every mutation to keep the list fresh (E1.5).
    /// </summary>
    [RelayCommand]
    private async Task LoadFundsAsync()
    {
        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            var result = await _fundService.GetFundsAsync(_portfolioId);

            // Apply the current sort mode. For Custom, re-orders by remembered IDs;
            // new funds (not yet in the custom order) are appended alphabetically.
            Funds = new ObservableCollection<FundListItem>(ApplySort(result));
            IsEmpty = Funds.Count == 0;

            // Compute formatted total from the loaded funds.
            var totalAgoras = result.Sum(f => f.BalanceAgoras);
            FormattedTotal = MoneyFormatter.FormatAgoras(totalAgoras);

            // Refresh history panel to show any new transactions from the operation
            // that triggered this reload. Fire-and-forget — history VM handles its own
            // loading state and errors independently.
            _ = _historyViewModel.LoadHistoryCommand.ExecuteAsync(null);
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בטעינת הקרנות. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Creates a new fund. Shows the fund creation dialog (name + optional amount),
    /// calls the service, then reloads the list.
    /// </summary>
    [RelayCommand]
    private async Task CreateFundAsync()
    {
        var input = await FundCreateRequested!.Invoke();

        if (input is null)
            return; // User cancelled.

        var (name, amountAgoras) = input.Value;

        // Confirm before creating — show the initial balance when provided.
        var confirmMsg = amountAgoras > 0
            ? $"יצירת קרן \"{name}\" עם יתרה התחלתית של {MoneyFormatter.FormatAgoras(amountAgoras)}. להמשיך?"
            : $"יצירת קרן \"{name}\". להמשיך?";
        if (!await ConfirmationRequested!.Invoke(confirmMsg)) // ! safe: always subscribed by PortfolioWindow before any command runs
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _fundService.CreateFundAsync(_portfolioId, name, amountAgoras);
            await LoadFundsAsync();
        }
        catch (EmptyFundNameException)
        {
            ErrorMessage = "נא להזין שם לקרן.";
        }
        catch (DuplicateFundNameException)
        {
            ErrorMessage = "כבר קיימת קרן עם שם זה.";
        }
        catch (NegativeFundAmountException)
        {
            ErrorMessage = "הסכום אינו יכול להיות שלילי.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לבצע פעולה בתיק סגור.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה ביצירת הקרן. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Renames a fund. Shows a name input dialog pre-filled with the current name.
    /// </summary>
    [RelayCommand]
    private async Task RenameFundAsync(FundListItem fund)
    {
        var newName = await NameInputRequested!.Invoke("שינוי שם קרן", fund.Name);

        if (newName is null)
            return; // User cancelled.

        // Confirm before renaming — show both old and new name.
        if (!await ConfirmationRequested!.Invoke($"שינוי שם הקרן מ\"{fund.Name}\" ל\"{newName}\". להמשיך?")) // ! safe: always subscribed by PortfolioWindow before any command runs
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _fundService.RenameFundAsync(_portfolioId, fund.FundId, newName);
            await LoadFundsAsync();
        }
        catch (EmptyFundNameException)
        {
            ErrorMessage = "נא להזין שם לקרן.";
        }
        catch (DuplicateFundNameException)
        {
            ErrorMessage = "כבר קיימת קרן עם שם זה.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לבצע פעולה בתיק סגור.";
        }
        catch (FundNotFoundException)
        {
            ErrorMessage = "הקרן לא נמצאה.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בשינוי שם הקרן. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Deletes a fund after user confirmation.
    /// </summary>
    [RelayCommand]
    private async Task DeleteFundAsync(FundListItem fund)
    {
        var confirmed = await ConfirmationRequested!.Invoke($"האם אתה בטוח שברצונך למחוק את הקרן \"{fund.Name}\"?");

        if (!confirmed)
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _fundService.DeleteFundAsync(_portfolioId, fund.FundId);
            await LoadFundsAsync();
        }
        catch (FundNotEmptyException)
        {
            ErrorMessage = "לא ניתן למחוק קרן שיתרתה אינה אפס.";
        }
        catch (FundHasScheduledDepositException)
        {
            ErrorMessage = "לא ניתן למחוק קרן עם הפקדה מתוזמנת פעילה.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לבצע פעולה בתיק סגור.";
        }
        catch (FundNotFoundException)
        {
            ErrorMessage = "הקרן לא נמצאה.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה במחיקת הקרן. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    // -----------------------------------------------------------------------------------------
    // Fund money operations (E6.8–E6.10)
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Deposits money into a fund (E6.8). Shows an amount input dialog, calls the service, reloads.
    /// </summary>
    [RelayCommand]
    private async Task DepositAsync(FundListItem fund)
    {
        var amount = await AmountInputRequested!.Invoke("הפקדה לקרן", fund.Name);

        if (amount is null)
            return; // User cancelled.

        // Confirm before depositing — show the exact amount and target fund.
        if (!await ConfirmationRequested!.Invoke($"הפקדה של {MoneyFormatter.FormatAgoras(amount.Value)} לקרן \"{fund.Name}\". להמשיך?")) // ! safe: always subscribed by PortfolioWindow before any command runs
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _fundService.DepositAsync(_portfolioId, fund.FundId, amount.Value);
            await LoadFundsAsync();
        }
        catch (NegativeFundAmountException)
        {
            ErrorMessage = "הסכום חייב להיות גדול מאפס.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לבצע פעולה בתיק סגור.";
        }
        catch (FundNotFoundException)
        {
            ErrorMessage = "הקרן לא נמצאה.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בהפקדה. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Withdraws money from a fund (E6.9). Shows an amount input dialog, calls the service, reloads.
    /// </summary>
    [RelayCommand]
    private async Task WithdrawAsync(FundListItem fund)
    {
        var amount = await AmountInputRequested!.Invoke("משיכה מקרן", fund.Name);

        if (amount is null)
            return; // User cancelled.

        // Confirm before withdrawing — show the exact amount and source fund.
        if (!await ConfirmationRequested!.Invoke($"משיכה של {MoneyFormatter.FormatAgoras(amount.Value)} מקרן \"{fund.Name}\". להמשיך?")) // ! safe: always subscribed by PortfolioWindow before any command runs
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _fundService.WithdrawAsync(_portfolioId, fund.FundId, amount.Value);
            await LoadFundsAsync();
        }
        catch (InsufficientFundBalanceException)
        {
            ErrorMessage = "אין מספיק יתרה בקרן לביצוע המשיכה.";
        }
        catch (NegativeFundAmountException)
        {
            ErrorMessage = "הסכום חייב להיות גדול מאפס.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לבצע פעולה בתיק סגור.";
        }
        catch (FundNotFoundException)
        {
            ErrorMessage = "הקרן לא נמצאה.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה במשיכה. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Transfers money between two funds (E6.10). Shows a transfer dialog, calls the service, reloads.
    /// </summary>
    [RelayCommand]
    private async Task TransferAsync(FundListItem sourceFund)
    {
        // Build the list of destination candidates (all funds except the source).
        var otherFunds = Funds.Where(f => f.FundId != sourceFund.FundId).ToList();

        if (otherFunds.Count == 0)
        {
            ErrorMessage = "נדרשות לפחות שתי קרנות לביצוע העברה.";
            return;
        }

        var result = await TransferRequested!.Invoke(sourceFund, otherFunds);

        if (result is null)
            return; // User cancelled.

        var (destinationFundId, amountAgoras) = result.Value;

        // Confirm before transferring — show amount, source, and destination fund names.
        var destFund = otherFunds.First(f => f.FundId == destinationFundId);
        if (!await ConfirmationRequested!.Invoke( // ! safe: always subscribed by PortfolioWindow before any command runs
            $"העברה של {MoneyFormatter.FormatAgoras(amountAgoras)} מקרן \"{sourceFund.Name}\" לקרן \"{destFund.Name}\". להמשיך?"))
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _fundService.TransferAsync(_portfolioId, sourceFund.FundId, destinationFundId, amountAgoras);
            await LoadFundsAsync();
        }
        catch (InsufficientFundBalanceException)
        {
            ErrorMessage = "אין מספיק יתרה בקרן המקור לביצוע ההעברה.";
        }
        catch (SameFundTransferException)
        {
            ErrorMessage = "לא ניתן להעביר מקרן לעצמה.";
        }
        catch (NegativeFundAmountException)
        {
            ErrorMessage = "הסכום חייב להיות גדול מאפס.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לבצע פעולה בתיק סגור.";
        }
        catch (FundNotFoundException)
        {
            ErrorMessage = "אחת הקרנות לא נמצאה.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בהעברה. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    // -----------------------------------------------------------------------------------------
    // Portfolio-level commands
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Renames this portfolio. Shows a name input dialog pre-filled with the current name (E5.4).
    /// Updates the window title on success.
    /// </summary>
    [RelayCommand]
    private async Task RenamePortfolioAsync()
    {
        var newName = await NameInputRequested!.Invoke("שינוי שם תיק", PortfolioName);

        if (newName is null)
            return; // User cancelled.

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _portfolioService.RenamePortfolioAsync(_portfolioId, newName);
            PortfolioName = newName;

            // Refresh history — rename creates a PortfolioRenamed transaction.
            _ = _historyViewModel.LoadHistoryCommand.ExecuteAsync(null);
        }
        catch (EmptyPortfolioNameException)
        {
            ErrorMessage = "נא להזין שם לתיק.";
        }
        catch (DuplicatePortfolioNameException)
        {
            ErrorMessage = "כבר קיים תיק עם שם זה.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לשנות שם של תיק סגור.";
        }
        catch (PortfolioNotFoundException)
        {
            ErrorMessage = "התיק לא נמצא.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בשינוי שם התיק. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Deletes (closes) this portfolio after user confirmation (E5.5).
    /// On success, navigates back to the portfolio list.
    /// </summary>
    [RelayCommand]
    private async Task DeletePortfolioAsync()
    {
        var confirmed = await ConfirmationRequested!.Invoke($"האם אתה בטוח שברצונך למחוק את התיק \"{PortfolioName}\"?");

        if (!confirmed)
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _portfolioService.ClosePortfolioAsync(_portfolioId);
            BackRequested?.Invoke();
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "התיק כבר נמחק.";
        }
        catch (PortfolioNotFoundException)
        {
            ErrorMessage = "התיק לא נמצא.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה במחיקת התיק. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Revalues the portfolio total, scaling all fund balances proportionally (E6.11).
    /// Shows an amount input dialog for the new total, confirms, then calls the service.
    /// </summary>
    [RelayCommand]
    private async Task RevaluePortfolioAsync()
    {
        if (Funds.Count == 0)
        {
            ErrorMessage = "לא ניתן לעדכן שווי תיק ללא קרנות.";
            return;
        }

        var newTotal = await AmountInputRequested!.Invoke("עדכון שווי תיק", PortfolioName); // ! safe: always subscribed by PortfolioWindow before any command runs

        if (newTotal is null)
            return; // User cancelled.

        // Confirm before revaluing — show old and new totals.
        var oldTotalAgoras = Funds.Sum(f => f.BalanceAgoras);
        if (!await ConfirmationRequested!.Invoke( // ! safe: always subscribed by PortfolioWindow before any command runs
            $"עדכון שווי התיק מ-{MoneyFormatter.FormatAgoras(oldTotalAgoras)} ל-{MoneyFormatter.FormatAgoras(newTotal.Value)}. להמשיך?"))
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _fundService.RevaluePortfolioAsync(_portfolioId, newTotal.Value);
            await LoadFundsAsync();
        }
        catch (PortfolioTotalIsZeroException)
        {
            ErrorMessage = "לא ניתן לעדכן שווי כאשר סך התיק הוא אפס.";
        }
        catch (RevalueWouldZeroFundException)
        {
            ErrorMessage = "השווי החדש קיצוני מדי — אחת הקרנות תאבד את חלקה לחלוטין.";
        }
        catch (NegativeFundAmountException)
        {
            ErrorMessage = "הסכום חייב להיות גדול מאפס.";
        }
        catch (PortfolioClosedException)
        {
            ErrorMessage = "לא ניתן לבצע פעולה בתיק סגור.";
        }
        catch (TotalMismatchException)
        {
            ErrorMessage = "שגיאה בחישוב העדכון. נסה שוב.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בעדכון שווי התיק. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scheduled deposits (PR-8, E8)
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Opens the scheduled deposits management dialog.
    /// Creates a <see cref="ScheduledDepositsViewModel"/> and raises the event for the View.
    /// After the dialog closes, refreshes fund balances in case deposits were modified.
    /// </summary>
    [RelayCommand]
    private async Task ManageScheduledDepositsAsync()
    {
        if (ScheduledDepositsRequested == null) return;

        var vm = new ScheduledDepositsViewModel(_scheduledDepositService, _fundService, _portfolioId);
        await ScheduledDepositsRequested.Invoke(vm);

        // Refresh funds — the user may have created/deleted/toggled deposits,
        // and the dialog may have triggered executions that changed balances.
        await LoadFundsAsync();
    }

    /// <summary>
    /// Triggers execution of due scheduled deposits for this portfolio (E8.4, E8.9).
    /// Called on window load and periodically by a timer in the View.
    /// If any deposits executed, refreshes fund balances and transaction history.
    /// </summary>
    public async Task TriggerScheduledDepositExecutionAsync()
    {
        try
        {
            var deviceId = await _deviceIdStore.GetOrCreateAsync();
            var executedCount = await _scheduledDepositService.ExecuteDueDepositsAsync(_portfolioId, deviceId);

            if (executedCount > 0)
                await LoadFundsAsync();
        }
        catch
        {
            // Execution trigger failures are silent — the next trigger will retry.
            // No user-facing error because this is a background operation.
        }
    }

    /// <summary>
    /// Navigates back to the portfolio list.
    /// </summary>
    [RelayCommand]
    private void GoBack()
    {
        BackRequested?.Invoke();
    }

    // -----------------------------------------------------------------------------------------
    // Sort (E5.10) — presentation-only, must not influence any money algorithm
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Moves the selected fund one position up in the list.
    /// Only available in Custom sort mode.
    /// </summary>
    [RelayCommand(CanExecute = nameof(CanMoveSelectedFundUp))]
    private void MoveSelectedFundUp()
    {
        var idx = Funds.IndexOf(SelectedFund!);
        Funds.Move(idx, idx - 1);
        // Sync the remembered custom order to the new visual order.
        _customOrderedFundIds = Funds.Select(f => f.FundId).ToList();
        MoveSelectedFundUpCommand.NotifyCanExecuteChanged();
        MoveSelectedFundDownCommand.NotifyCanExecuteChanged();
    }

    /// <summary>True when the selected fund can move up (not already first, Custom mode active).</summary>
    private bool CanMoveSelectedFundUp() =>
        SelectedSortOption.Mode == FundSortMode.Custom &&
        SelectedFund != null &&
        Funds.Count > 1 &&
        Funds.IndexOf(SelectedFund) > 0;

    /// <summary>
    /// Moves the selected fund one position down in the list.
    /// Only available in Custom sort mode.
    /// </summary>
    [RelayCommand(CanExecute = nameof(CanMoveSelectedFundDown))]
    private void MoveSelectedFundDown()
    {
        var idx = Funds.IndexOf(SelectedFund!);
        Funds.Move(idx, idx + 1);
        // Sync the remembered custom order to the new visual order.
        _customOrderedFundIds = Funds.Select(f => f.FundId).ToList();
        MoveSelectedFundUpCommand.NotifyCanExecuteChanged();
        MoveSelectedFundDownCommand.NotifyCanExecuteChanged();
    }

    /// <summary>True when the selected fund can move down (not already last, Custom mode active).</summary>
    private bool CanMoveSelectedFundDown() =>
        SelectedSortOption.Mode == FundSortMode.Custom &&
        SelectedFund != null &&
        Funds.Count > 1 &&
        Funds.IndexOf(SelectedFund) < Funds.Count - 1;

    /// <summary>
    /// Returns <paramref name="funds"/> sorted according to the current <see cref="SelectedSortOption"/>.
    /// For <see cref="FundSortMode.Custom"/>, funds are ordered by <see cref="_customOrderedFundIds"/>;
    /// any funds not yet in the remembered list are appended alphabetically, and the list is updated
    /// to reflect deletions and additions.
    /// </summary>
    internal IReadOnlyList<FundListItem> ApplySort(IReadOnlyList<FundListItem> funds)
    {
        return SelectedSortOption.Mode switch
        {
            FundSortMode.Balance => funds.OrderByDescending(f => f.BalanceAgoras).ToList(),
            FundSortMode.AllocationPercent => funds.OrderByDescending(f => f.AllocationPercent).ToList(),
            FundSortMode.CreatedDate => funds.OrderBy(f => f.CreatedAtUtc).ToList(),
            FundSortMode.Custom => ApplyCustomOrder(funds),
            _ => funds.OrderBy(f => f.Name, StringComparer.OrdinalIgnoreCase).ToList(), // Name (default)
        };
    }

    /// <summary>
    /// Applies the remembered custom order to <paramref name="funds"/>.
    /// Funds in <see cref="_customOrderedFundIds"/> appear first in that order.
    /// New funds (not yet remembered) are appended in alphabetical order.
    /// Deleted fund IDs are silently dropped.
    /// The remembered order is updated to match the result.
    /// </summary>
    private IReadOnlyList<FundListItem> ApplyCustomOrder(IReadOnlyList<FundListItem> funds)
    {
        var byId = funds.ToDictionary(f => f.FundId);
        var ordered = new List<FundListItem>(funds.Count);

        // First: funds already in the remembered order.
        foreach (var id in _customOrderedFundIds)
        {
            if (byId.TryGetValue(id, out var fund))
                ordered.Add(fund);
        }

        // Then: any new funds not yet in the remembered order, alphabetically.
        var remembered = _customOrderedFundIds.ToHashSet();
        var newFunds = funds
            .Where(f => !remembered.Contains(f.FundId))
            .OrderBy(f => f.Name, StringComparer.OrdinalIgnoreCase);
        ordered.AddRange(newFunds);

        // Keep the remembered list in sync (removes deleted IDs, adds new ones).
        _customOrderedFundIds = ordered.Select(f => f.FundId).ToList();

        return ordered;
    }

    /// <summary>
    /// Called by the source generator when <see cref="SelectedSortOption"/> changes.
    /// Re-sorts the current fund list and refreshes the move-button enabled state.
    /// </summary>
    partial void OnSelectedSortOptionChanged(FundSortOption value)
    {
        if (Funds.Count == 0) return;

        // When entering Custom mode for the first time, seed the order from the current display order.
        if (value.Mode == FundSortMode.Custom && _customOrderedFundIds.Count == 0)
            _customOrderedFundIds = Funds.Select(f => f.FundId).ToList();

        var sorted = ApplySort(Funds.ToList());
        Funds = new ObservableCollection<FundListItem>(sorted);

        MoveSelectedFundUpCommand.NotifyCanExecuteChanged();
        MoveSelectedFundDownCommand.NotifyCanExecuteChanged();
    }

    /// <summary>
    /// Called by the source generator when <see cref="SelectedFund"/> changes.
    /// Refreshes the move-button enabled state.
    /// </summary>
    partial void OnSelectedFundChanged(FundListItem? value)
    {
        MoveSelectedFundUpCommand.NotifyCanExecuteChanged();
        MoveSelectedFundDownCommand.NotifyCanExecuteChanged();
    }
}
