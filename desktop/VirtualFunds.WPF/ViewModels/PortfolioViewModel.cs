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
    private FundListItem? _selectedFund;

    /// <summary>True while a service operation is in progress.</summary>
    [ObservableProperty]
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

    // -----------------------------------------------------------------------------------------

    /// <summary>Initializes the ViewModel for a specific portfolio.</summary>
    /// <param name="fundService">The fund service (injected from DI).</param>
    /// <param name="portfolioService">The portfolio service, used for rename and delete (injected from DI).</param>
    /// <param name="portfolioId">The ID of the portfolio to display.</param>
    /// <param name="portfolioName">The display name of the portfolio.</param>
    public PortfolioViewModel(IFundService fundService, IPortfolioService portfolioService, Guid portfolioId, string portfolioName)
    {
        _fundService = fundService;
        _portfolioService = portfolioService;
        _portfolioId = portfolioId;
        _portfolioName = portfolioName;
    }

    /// <summary>The portfolio ID this ViewModel operates on.</summary>
    public Guid PortfolioId => _portfolioId;

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
    /// Navigates back to the portfolio list.
    /// </summary>
    [RelayCommand]
    private void GoBack()
    {
        BackRequested?.Invoke();
    }
}
