using System.Windows;
using VirtualFunds.Core.Services;
using VirtualFunds.WPF.Views;

namespace VirtualFunds.WPF;

/// <summary>
/// Interaction logic for MainWindow.xaml.
/// <para>
/// Currently a temporary stub that only provides a sign-out button to complete the
/// end-to-end auth loop. Full MainWindow content will be added in a future step.
/// </para>
/// </summary>
public partial class MainWindow : Window
{
    private readonly IAuthService _authService;
    private readonly Func<AuthWindow> _authWindowFactory;

    /// <summary>
    /// Initializes MainWindow with the auth service and a factory for AuthWindow.
    /// Both are injected from the DI container.
    /// </summary>
    public MainWindow(IAuthService authService, Func<AuthWindow> authWindowFactory)
    {
        InitializeComponent();
        _authService = authService;
        _authWindowFactory = authWindowFactory;
    }

    /// <summary>
    /// Sign-out button click handler.
    /// Signs out, clears the session, then re-shows the AuthWindow.
    /// </summary>
    private async void SignOutButton_Click(object sender, RoutedEventArgs e)
    {
        await _authService.SignOutAsync();

        var authWindow = _authWindowFactory();
        authWindow.Show();
        Close();
    }
}
