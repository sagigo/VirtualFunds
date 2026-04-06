using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using System.Windows;
using VirtualFunds.Core.Services;
using VirtualFunds.Core.Supabase;
using VirtualFunds.Core.Models;
using VirtualFunds.WPF.ViewModels;
using VirtualFunds.WPF.Views;

namespace VirtualFunds.WPF;

/// <summary>
/// Application entry point and DI root (E3.4, E3.6).
/// StartupUri is removed from App.xaml — startup is handled here to allow async
/// session restore before any window is shown.
/// </summary>
public partial class App : Application
{
    private IServiceProvider? _services;

    /// <summary>
    /// Overrides the WPF startup hook.
    /// Must be <c>async void</c> because WPF's <see cref="Application.OnStartup"/> is <c>void</c>.
    /// This is one of the rare correct uses of <c>async void</c> — it is an event handler override.
    /// </summary>
    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // 1. Build configuration: appsettings.json (committed, placeholder values) +
        //    user-secrets (actual values, not in git — set via: dotnet user-secrets set "Supabase:Url" "...")
        var configuration = new ConfigurationBuilder()
            .SetBasePath(AppDomain.CurrentDomain.BaseDirectory)
            .AddJsonFile("appsettings.json", optional: false, reloadOnChange: false)
            .AddUserSecrets<App>(optional: true)
            .Build();

        var supabaseUrl = configuration["Supabase:Url"] ?? string.Empty;
        var supabaseAnonKey = configuration["Supabase:AnonKey"] ?? string.Empty;

        // 2. Initialize the Supabase client. InitializeAsync must be called before use and
        //    cannot be done inside a DI constructor, so we do it here before building the container.
        var supabaseClient = await SupabaseClientFactory.CreateAsync(supabaseUrl, supabaseAnonKey);

        // 3. Build the DI container.
        var services = new ServiceCollection();

        services.AddSingleton(configuration);
        services.AddSingleton(supabaseClient);
        services.AddSingleton<ISessionStore, LocalSessionStore>();
        services.AddSingleton<IAuthService, SupabaseAuthService>();
        services.AddSingleton<IPortfolioService, SupabasePortfolioService>();
        services.AddSingleton<IFundService, SupabaseFundService>();
        services.AddSingleton<ITransactionService, SupabaseTransactionService>();

        // Windows and ViewModels are transient — a fresh instance is created each time
        // (e.g., AuthWindow re-opens after sign-out).
        services.AddTransient<AuthViewModel>();
        services.AddTransient<MainViewModel>();
        services.AddTransient<AuthWindow>();
        services.AddTransient<MainWindow>();

        // Register factories so windows can create each other without a direct IServiceProvider dependency.
        services.AddTransient<Func<MainWindow>>(sp => () => sp.GetRequiredService<MainWindow>());
        services.AddTransient<Func<AuthWindow>>(sp => () => sp.GetRequiredService<AuthWindow>());

        // PortfolioWindow factory — takes runtime parameters (portfolioId, portfolioName)
        // because the ViewModel needs to know which portfolio to display.
        services.AddTransient<Func<Guid, string, PortfolioWindow>>(sp =>
            (portfolioId, portfolioName) =>
            {
                var fundService = sp.GetRequiredService<IFundService>();
                var portfolioService = sp.GetRequiredService<IPortfolioService>();
                var transactionService = sp.GetRequiredService<ITransactionService>();
                var historyVm = new TransactionHistoryViewModel(transactionService, fundService, portfolioId);
                var vm = new PortfolioViewModel(fundService, portfolioService, portfolioId, portfolioName, historyVm);
                var mainWindowFactory = sp.GetRequiredService<Func<MainWindow>>();
                return new PortfolioWindow(vm, mainWindowFactory);
            });

        _services = services.BuildServiceProvider();

        // 4. Try to restore a previously persisted session.
        var authService = _services.GetRequiredService<IAuthService>();
        var state = await authService.RestoreSessionAsync();

        // 5. Route to MainWindow (session restored) or AuthWindow (no session).
        if (state is AuthStateSignedIn)
        {
            var mainWindow = _services.GetRequiredService<MainWindow>();
            mainWindow.Show();
        }
        else
        {
            var authWindow = _services.GetRequiredService<AuthWindow>();
            authWindow.Show();
        }
    }
}
