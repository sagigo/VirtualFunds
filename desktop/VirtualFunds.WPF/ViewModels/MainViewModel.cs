using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;

namespace VirtualFunds.WPF.ViewModels;

/// <summary>
/// ViewModel for the main window's portfolio selection screen (PR-4).
/// Shows a list of portfolios with their totals. Context menu provides
/// Open, Rename, and Delete actions. Double-click opens a portfolio.
/// <para>
/// UI interactions that require dialogs (name input, confirmation) are delegated
/// to the View via events, keeping the ViewModel testable without UI dependencies.
/// </para>
/// </summary>
public sealed partial class MainViewModel : ObservableObject
{
    private readonly IPortfolioService _portfolioService;
    private readonly IAuthService _authService;

    // -----------------------------------------------------------------------------------------
    // Events — the View code-behind subscribes to handle UI-specific concerns.
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Raised when the ViewModel needs a name from the user (create or rename).
    /// Parameters: (dialog title, existing name or null for create).
    /// Returns: the entered name, or <c>null</c> if the user cancelled.
    /// </summary>
    public event Func<string, string?, Task<string?>>? NameInputRequested;

    /// <summary>
    /// Raised when the ViewModel needs a yes/no confirmation from the user.
    /// Parameter: the message to display.
    /// Returns: <c>true</c> if the user confirmed.
    /// </summary>
    public event Func<string, Task<bool>>? ConfirmationRequested;

    /// <summary>
    /// Raised when the user has signed out and the View should navigate to AuthWindow.
    /// </summary>
    public event Action? SignOutRequested;

    /// <summary>
    /// Raised when the user opens a portfolio (double-click or context menu "Open").
    /// The View should navigate to the portfolio detail screen.
    /// </summary>
    public event Action<PortfolioListItem>? PortfolioOpenRequested;

    // -----------------------------------------------------------------------------------------
    // Observable properties
    // -----------------------------------------------------------------------------------------

    /// <summary>The list of active portfolios to display.</summary>
    [ObservableProperty]
    private ObservableCollection<PortfolioListItem> _portfolios = new();

    /// <summary>The currently selected portfolio in the list.</summary>
    [ObservableProperty]
    private PortfolioListItem? _selectedPortfolio;

    /// <summary>True while a service operation is in progress.</summary>
    [ObservableProperty]
    private bool _isLoading;

    /// <summary>Hebrew error message, or empty when there is no error.</summary>
    [ObservableProperty]
    private string _errorMessage = string.Empty;

    /// <summary>True when portfolios have been loaded but the list is empty.</summary>
    [ObservableProperty]
    private bool _isEmpty;

    // -----------------------------------------------------------------------------------------

    /// <summary>Initializes the ViewModel with the required services.</summary>
    public MainViewModel(IPortfolioService portfolioService, IAuthService authService)
    {
        _portfolioService = portfolioService;
        _authService = authService;
    }

    // -----------------------------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Loads active portfolios from the server and updates the list.
    /// Called on startup and after every mutation to keep the list fresh (E1.5).
    /// </summary>
    [RelayCommand]
    private async Task LoadPortfoliosAsync()
    {
        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            var result = await _portfolioService.GetActivePortfoliosAsync();

            Portfolios = new ObservableCollection<PortfolioListItem>(result);
            IsEmpty = Portfolios.Count == 0;
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בטעינת התיקים. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Opens a portfolio — navigates to the portfolio detail screen.
    /// Triggered by double-click or context menu "Open".
    /// </summary>
    [RelayCommand]
    private void OpenPortfolio(PortfolioListItem portfolio)
    {
        PortfolioOpenRequested?.Invoke(portfolio);
    }

    /// <summary>
    /// Creates a new portfolio. Shows a name input dialog, calls the service, then reloads the list.
    /// </summary>
    [RelayCommand]
    private async Task CreatePortfolioAsync()
    {
        var name = await NameInputRequested!.Invoke("תיק חדש", null);

        if (name is null)
            return; // User cancelled.

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _portfolioService.CreatePortfolioAsync(name);
            await LoadPortfoliosAsync();
        }
        catch (EmptyPortfolioNameException)
        {
            ErrorMessage = "נא להזין שם לתיק.";
        }
        catch (DuplicatePortfolioNameException)
        {
            ErrorMessage = "כבר קיים תיק עם שם זה.";
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה ביצירת התיק. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Renames a portfolio. Shows a name input dialog pre-filled with the current name.
    /// </summary>
    [RelayCommand]
    private async Task RenamePortfolioAsync(PortfolioListItem portfolio)
    {
        var newName = await NameInputRequested!.Invoke("שינוי שם תיק", portfolio.Name);

        if (newName is null)
            return; // User cancelled.

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _portfolioService.RenamePortfolioAsync(portfolio.PortfolioId, newName);
            await LoadPortfoliosAsync();
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
    /// Deletes (closes/soft-deletes) a portfolio after user confirmation.
    /// </summary>
    [RelayCommand]
    private async Task DeletePortfolioAsync(PortfolioListItem portfolio)
    {
        var confirmed = await ConfirmationRequested!.Invoke($"האם אתה בטוח שברצונך למחוק את התיק? \"{portfolio.Name}\"?");

        if (!confirmed)
            return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _portfolioService.ClosePortfolioAsync(portfolio.PortfolioId);
            await LoadPortfoliosAsync();
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
    /// Signs out the current user and raises <see cref="SignOutRequested"/>
    /// so the View can navigate to AuthWindow.
    /// </summary>
    [RelayCommand]
    private async Task SignOutAsync()
    {
        await _authService.SignOutAsync();
        SignOutRequested?.Invoke();
    }
}
