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
    /// <param name="portfolioId">The ID of the portfolio to display.</param>
    /// <param name="portfolioName">The display name of the portfolio.</param>
    /// <param name="historyViewModel">The history sub-ViewModel for the History tab (PR-7).</param>
    public PortfolioViewModel(
        IFundService fundService,
        IPortfolioService portfolioService,
        Guid portfolioId,
        string portfolioName,
        TransactionHistoryViewModel historyViewModel)
    {
        _fundService = fundService;
        _portfolioService = portfolioService;
        _portfolioId = portfolioId;
        _portfolioName = portfolioName;
        _historyViewModel = historyViewModel;
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

            Funds = new ObservableCollection<FundListItem>(result);
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

    /// <summary>
    /// Navigates back to the portfolio list.
    /// </summary>
    [RelayCommand]
    private void GoBack()
    {
        BackRequested?.Invoke();
    }
}
