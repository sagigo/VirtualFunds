using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using VirtualFunds.Core.Models;
using VirtualFunds.WPF.ViewModels;
using VirtualFunds.WPF.Views;

namespace VirtualFunds.WPF;

/// <summary>
/// Main application window — portfolio selection screen.
/// <para>
/// Shows a list of portfolios with their total balances. Double-click or
/// right-click → "Open" navigates to the portfolio detail screen.
/// Right-click also provides Rename and Delete actions.
/// </para>
/// </summary>
public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel;
    private readonly Func<AuthWindow> _authWindowFactory;
    private readonly Func<Guid, string, PortfolioWindow> _portfolioWindowFactory;

    /// <summary>
    /// Initializes MainWindow with its ViewModel and factories for navigation.
    /// All parameters are injected from the DI container.
    /// </summary>
    public MainWindow(
        MainViewModel viewModel,
        Func<AuthWindow> authWindowFactory,
        Func<Guid, string, PortfolioWindow> portfolioWindowFactory)
    {
        InitializeComponent();

        _viewModel = viewModel;
        _authWindowFactory = authWindowFactory;
        _portfolioWindowFactory = portfolioWindowFactory;

        DataContext = _viewModel;

        // Subscribe to ViewModel events for UI interactions.
        _viewModel.NameInputRequested += OnNameInputRequested;
        _viewModel.ConfirmationRequested += OnConfirmationRequested;
        _viewModel.SignOutRequested += OnSignOutRequested;
        _viewModel.PortfolioOpenRequested += OnPortfolioOpenRequested;

        // Load portfolios when the window is shown.
        Loaded += async (_, _) => await _viewModel.LoadPortfoliosCommand.ExecuteAsync(null);
    }

    /// <summary>
    /// Handles double-click on a portfolio list item — opens the selected portfolio.
    /// </summary>
    private void PortfolioListBox_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (_viewModel.SelectedPortfolio is { } portfolio)
        {
            _viewModel.OpenPortfolioCommand.Execute(portfolio);
        }
    }

    /// <summary>
    /// Shows a <see cref="NameInputDialog"/> for the user to enter or edit a portfolio name.
    /// </summary>
    /// <param name="title">The dialog title (e.g., "תיק חדש" or "שינוי שם תיק").</param>
    /// <param name="existingName">
    /// The current name to pre-fill (for rename), or <c>null</c> for a blank field (for create).
    /// </param>
    /// <returns>The entered name, or <c>null</c> if the user cancelled.</returns>
    private Task<string?> OnNameInputRequested(string title, string? existingName)
    {
        var dialog = new NameInputDialog
        {
            Owner = this,
            Title = title,
        };

        if (existingName is not null)
        {
            dialog.InputName = existingName;
        }

        var result = dialog.ShowDialog() == true
            ? dialog.InputName
            : null;

        return Task.FromResult(result);
    }

    /// <summary>
    /// Shows a confirmation <see cref="MessageBox"/> with the given message.
    /// </summary>
    /// <returns><c>true</c> if the user clicked Yes.</returns>
    private Task<bool> OnConfirmationRequested(string message)
    {
        var result = MessageBox.Show(
            this,
            message,
            "אישור",
            MessageBoxButton.YesNo,
            MessageBoxImage.Question,
            MessageBoxResult.No);

        return Task.FromResult(result == MessageBoxResult.Yes);
    }

    /// <summary>
    /// Called when the user signs out. Opens AuthWindow and closes this window.
    /// </summary>
    private void OnSignOutRequested()
    {
        var authWindow = _authWindowFactory();
        authWindow.Show();
        Close();
    }

    /// <summary>
    /// Called when the user opens a portfolio. Opens PortfolioWindow and closes this window.
    /// </summary>
    private void OnPortfolioOpenRequested(PortfolioListItem portfolio)
    {
        var portfolioWindow = _portfolioWindowFactory(portfolio.PortfolioId, portfolio.Name);
        portfolioWindow.Show();
        Close();
    }
}
