using System.Windows;
using VirtualFunds.Core.Models;
using VirtualFunds.WPF.ViewModels;

namespace VirtualFunds.WPF.Views;

/// <summary>
/// Code-behind for AuthWindow.
/// Responsibilities:
/// <list type="bullet">
///   <item>Set the DataContext to the injected <see cref="AuthViewModel"/>.</item>
///   <item>Forward PasswordBox.PasswordChanged to the ViewModel (PasswordBox is not bindable by WPF design).</item>
///   <item>Subscribe to <see cref="AuthViewModel.AuthSucceeded"/> to navigate to MainWindow.</item>
/// </list>
/// </summary>
public partial class AuthWindow : Window
{
    private readonly AuthViewModel _viewModel;

    // MainWindow factory — resolved lazily to avoid circular construction.
    private readonly Func<MainWindow> _mainWindowFactory;

    /// <summary>
    /// Initializes the window with its ViewModel and a factory for creating MainWindow.
    /// Both are injected from the DI container.
    /// </summary>
    public AuthWindow(AuthViewModel viewModel, Func<MainWindow> mainWindowFactory)
    {
        InitializeComponent();

        _viewModel = viewModel;
        _mainWindowFactory = mainWindowFactory;

        DataContext = _viewModel;
        _viewModel.AuthSucceeded += OnAuthSucceeded;
    }

    /// <summary>
    /// Forwards the PasswordBox value to the ViewModel whenever the user types.
    /// This pattern is the standard MVVM workaround for PasswordBox's non-bindable Password property.
    /// </summary>
    private void PasswordBox_PasswordChanged(object sender, RoutedEventArgs e)
    {
        _viewModel.Password = PasswordBox.Password;
    }

    /// <summary>
    /// Called when authentication succeeds. Opens MainWindow and closes this window.
    /// </summary>
    private void OnAuthSucceeded(AuthState state)
    {
        var mainWindow = _mainWindowFactory();
        mainWindow.Show();
        Close();
    }
}
