using System.Windows;
using Microsoft.Win32;
using VirtualFunds.Core.Models;
using VirtualFunds.WPF.ViewModels;

namespace VirtualFunds.WPF.Views;

/// <summary>
/// Portfolio detail window — shows funds and transaction history side by side (PR-5, PR-7).
/// <para>
/// The left panel (RTL right) shows the fund list with operations.
/// The right panel (RTL left) shows the transaction history with filters and CSV export.
/// A draggable <see cref="System.Windows.Controls.GridSplitter"/> lets the user resize each panel.
/// </para>
/// </summary>
public partial class PortfolioWindow : Window
{
    private readonly PortfolioViewModel _viewModel;
    private readonly Func<MainWindow> _mainWindowFactory;

    /// <summary>
    /// Initializes PortfolioWindow with its ViewModel and a factory for MainWindow (back navigation).
    /// Both are injected from the DI container.
    /// </summary>
    public PortfolioWindow(PortfolioViewModel viewModel, Func<MainWindow> mainWindowFactory)
    {
        InitializeComponent();

        _viewModel = viewModel;
        _mainWindowFactory = mainWindowFactory;

        DataContext = _viewModel;

        // Subscribe to ViewModel events for UI interactions.
        _viewModel.NameInputRequested += OnNameInputRequested;
        _viewModel.FundCreateRequested += OnFundCreateRequested;
        _viewModel.ConfirmationRequested += OnConfirmationRequested;
        _viewModel.AmountInputRequested += OnAmountInputRequested;
        _viewModel.TransferRequested += OnTransferRequested;
        _viewModel.BackRequested += OnBackRequested;

        // Subscribe to history VM events.
        _viewModel.HistoryViewModel.CsvExportPathRequested += OnCsvExportPathRequested;

        // Load funds when the window is shown. LoadFundsAsync also triggers
        // a history reload, so both panels get populated.
        Loaded += async (_, _) => await _viewModel.LoadFundsCommand.ExecuteAsync(null);
    }

    /// <summary>
    /// Shows a <see cref="NameInputDialog"/> for the user to enter or edit a fund name (rename).
    /// </summary>
    /// <param name="title">The dialog title (e.g., "שינוי שם קרן").</param>
    /// <param name="existingName">The current name to pre-fill.</param>
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
    /// Shows a <see cref="FundCreateDialog"/> for the user to enter a fund name and optional initial amount.
    /// </summary>
    /// <returns>A tuple of (name, amountAgoras), or <c>null</c> if the user cancelled.</returns>
    private Task<(string Name, long AmountAgoras)?> OnFundCreateRequested()
    {
        var dialog = new FundCreateDialog
        {
            Owner = this,
        };

        if (dialog.ShowDialog() == true)
        {
            return Task.FromResult<(string Name, long AmountAgoras)?>(
                (dialog.FundName, dialog.AmountAgoras));
        }

        return Task.FromResult<(string Name, long AmountAgoras)?>(null);
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
    /// Shows an <see cref="AmountInputDialog"/> for the user to enter a shekel amount (deposit / withdrawal).
    /// </summary>
    /// <param name="title">The dialog title (e.g., "הפקדה לקרן").</param>
    /// <param name="fundName">The fund name shown in the dialog title for context.</param>
    /// <returns>The amount in agoras, or <c>null</c> if the user cancelled.</returns>
    private Task<long?> OnAmountInputRequested(string title, string fundName)
    {
        var dialog = new AmountInputDialog
        {
            Owner = this,
            Title = $"{title} — {fundName}",
        };

        if (dialog.ShowDialog() == true)
        {
            return Task.FromResult<long?>(dialog.AmountAgoras);
        }

        return Task.FromResult<long?>(null);
    }

    /// <summary>
    /// Shows a <see cref="TransferDialog"/> for the user to pick a destination fund and enter an amount.
    /// </summary>
    /// <param name="sourceFund">The source fund (fixed, shown as read-only).</param>
    /// <param name="otherFunds">The list of eligible destination funds.</param>
    /// <returns>A tuple of (destinationFundId, amountAgoras), or <c>null</c> if the user cancelled.</returns>
    private Task<(Guid DestinationFundId, long AmountAgoras)?> OnTransferRequested(
        FundListItem sourceFund, IReadOnlyList<FundListItem> otherFunds)
    {
        var dialog = new TransferDialog(sourceFund, otherFunds)
        {
            Owner = this,
        };

        if (dialog.ShowDialog() == true && dialog.DestinationFund is not null)
        {
            return Task.FromResult<(Guid DestinationFundId, long AmountAgoras)?>(
                (dialog.DestinationFund.FundId, dialog.AmountAgoras));
        }

        return Task.FromResult<(Guid DestinationFundId, long AmountAgoras)?>(null);
    }

    /// <summary>
    /// Shows a <see cref="SaveFileDialog"/> for the user to choose a CSV export file path.
    /// </summary>
    /// <returns>The chosen file path, or <c>null</c> if the user cancelled.</returns>
    private Task<string?> OnCsvExportPathRequested()
    {
        var dialog = new SaveFileDialog
        {
            Filter = "CSV files (*.csv)|*.csv",
            DefaultExt = ".csv",
            FileName = $"היסטוריה_{_viewModel.PortfolioName}_{DateTime.Now:yyyyMMdd}",
        };

        var result = dialog.ShowDialog(this) == true
            ? dialog.FileName
            : null;

        return Task.FromResult(result);
    }

    /// <summary>
    /// Called when the user clicks "Back". Opens MainWindow and closes this window.
    /// </summary>
    private void OnBackRequested()
    {
        var mainWindow = _mainWindowFactory();
        mainWindow.Show();
        Close();
    }
}
