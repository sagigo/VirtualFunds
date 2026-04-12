using System.Windows;
using VirtualFunds.Core.Models;
using VirtualFunds.WPF.ViewModels;

namespace VirtualFunds.WPF.Views;

/// <summary>
/// Modal dialog for managing scheduled deposits within a portfolio (PR-8).
/// <para>
/// Binds to <see cref="ScheduledDepositsViewModel"/> and delegates form/confirmation UI
/// via event subscriptions, following the established MVVM event pattern.
/// </para>
/// </summary>
public partial class ScheduledDepositsDialog : Window
{
    private readonly ScheduledDepositsViewModel _viewModel;

    /// <summary>
    /// Initializes the dialog with the given ViewModel.
    /// </summary>
    public ScheduledDepositsDialog(ScheduledDepositsViewModel viewModel)
    {
        InitializeComponent();

        _viewModel = viewModel;
        DataContext = _viewModel;

        // Subscribe to ViewModel events for UI delegation.
        _viewModel.DepositFormRequested += OnDepositFormRequested;
        _viewModel.ConfirmationRequested += OnConfirmationRequested;

        // Manage empty state visibility in code-behind (simpler than multi-trigger in XAML).
        _viewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName is nameof(ScheduledDepositsViewModel.IsEmpty)
                or nameof(ScheduledDepositsViewModel.IsLoading))
            {
                UpdateEmptyState();
            }
        };

        // Load data when the dialog is shown.
        Loaded += async (_, _) =>
        {
            await _viewModel.LoadScheduledDepositsCommand.ExecuteAsync(null);
            UpdateEmptyState();
        };
    }

    /// <summary>
    /// Shows the create/edit form dialog and returns the result.
    /// </summary>
    private Task<ScheduledDepositFormResult?> OnDepositFormRequested(
        IReadOnlyList<FundListItem> funds,
        ScheduledDepositListItem? existing)
    {
        var dialog = new ScheduledDepositFormDialog(funds, existing) { Owner = this };

        if (dialog.ShowDialog() != true)
            return Task.FromResult<ScheduledDepositFormResult?>(null);

        var result = new ScheduledDepositFormResult(
            Name: dialog.DepositName,
            FundId: dialog.SelectedFund!.FundId,
            AmountAgoras: dialog.AmountAgoras,
            ScheduleKind: dialog.ScheduleKind,
            IsEnabled: dialog.IsDepositEnabled,
            Note: dialog.Note,
            TimeOfDayMinutes: dialog.TimeOfDayMinutes,
            WeekdayMask: dialog.WeekdayMask,
            DayOfMonth: dialog.DayOfMonth,
            NextRunAtUtc: dialog.NextRunAtUtc);

        return Task.FromResult<ScheduledDepositFormResult?>(result);
    }

    /// <summary>
    /// Shows a confirmation MessageBox and returns the user's choice.
    /// </summary>
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
    /// Updates empty-state text visibility based on ViewModel state.
    /// </summary>
    private void UpdateEmptyState()
    {
        var showEmpty = _viewModel.IsEmpty && !_viewModel.IsLoading;
        EmptyStateText.Visibility = showEmpty ? Visibility.Visible : Visibility.Collapsed;
        DepositListView.Visibility = showEmpty ? Visibility.Collapsed : Visibility.Visible;
    }
}
