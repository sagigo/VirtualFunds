using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;

namespace VirtualFunds.WPF.ViewModels;

/// <summary>
/// Result from the scheduled deposit create/edit form dialog.
/// Contains all fields needed for <see cref="IScheduledDepositService.UpsertScheduledDepositAsync"/>.
/// </summary>
public record ScheduledDepositFormResult(
    string Name,
    Guid FundId,
    long AmountAgoras,
    ScheduleKind ScheduleKind,
    bool IsEnabled,
    string? Note,
    int? TimeOfDayMinutes,
    int? WeekdayMask,
    int? DayOfMonth,
    DateTime? NextRunAtUtc);

/// <summary>
/// ViewModel for the scheduled deposits management dialog (PR-8, E8).
/// Shows the list of scheduled deposits for a portfolio and provides CRUD + toggle commands.
/// <para>
/// UI interactions (form dialogs, confirmations) are delegated to the View via events,
/// keeping the ViewModel testable without UI dependencies.
/// </para>
/// </summary>
public sealed partial class ScheduledDepositsViewModel : ObservableObject
{
    private readonly IScheduledDepositService _scheduledDepositService;
    private readonly IFundService _fundService;
    private readonly Guid _portfolioId;

    // -----------------------------------------------------------------------------------------
    // Events — the View code-behind subscribes to handle UI-specific concerns.
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Raised when the ViewModel needs create/edit form input.
    /// Parameters: (fund list for ComboBox, existing deposit to edit — null for create).
    /// Returns: form result, or <c>null</c> if the user cancelled.
    /// </summary>
    public event Func<IReadOnlyList<FundListItem>, ScheduledDepositListItem?, Task<ScheduledDepositFormResult?>>? DepositFormRequested;

    /// <summary>
    /// Raised when the ViewModel needs a yes/no confirmation from the user (delete).
    /// Parameter: the message to display.
    /// Returns: <c>true</c> if the user confirmed.
    /// </summary>
    public event Func<string, Task<bool>>? ConfirmationRequested;

    // -----------------------------------------------------------------------------------------
    // Observable properties
    // -----------------------------------------------------------------------------------------

    /// <summary>The list of scheduled deposits for this portfolio.</summary>
    [ObservableProperty]
    private ObservableCollection<ScheduledDepositListItem> _scheduledDeposits = new();

    /// <summary>The currently selected deposit in the list.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanExecuteDepositOperation))]
    private ScheduledDepositListItem? _selectedDeposit;

    /// <summary>True while a service operation is in progress.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanExecuteDepositOperation))]
    private bool _isLoading;

    /// <summary>Hebrew error message, or empty when there is no error.</summary>
    [ObservableProperty]
    private string _errorMessage = string.Empty;

    /// <summary>True when the list has been loaded but is empty.</summary>
    [ObservableProperty]
    private bool _isEmpty;

    /// <summary>True when a deposit is selected and no operation is in progress.</summary>
    public bool CanExecuteDepositOperation => !IsLoading && SelectedDeposit != null;

    // -----------------------------------------------------------------------------------------

    /// <summary>Initializes the ViewModel for a specific portfolio.</summary>
    /// <param name="scheduledDepositService">The scheduled deposit service (injected from DI).</param>
    /// <param name="fundService">The fund service, used to get the fund list for the form ComboBox.</param>
    /// <param name="portfolioId">The portfolio whose scheduled deposits to manage.</param>
    public ScheduledDepositsViewModel(
        IScheduledDepositService scheduledDepositService,
        IFundService fundService,
        Guid portfolioId)
    {
        _scheduledDepositService = scheduledDepositService;
        _fundService = fundService;
        _portfolioId = portfolioId;
    }

    // -----------------------------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Loads scheduled deposits from the server and updates the list.
    /// </summary>
    [RelayCommand]
    private async Task LoadScheduledDepositsAsync()
    {
        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            var result = await _scheduledDepositService.GetScheduledDepositsAsync(_portfolioId);
            ScheduledDeposits = new ObservableCollection<ScheduledDepositListItem>(result);
            IsEmpty = ScheduledDeposits.Count == 0;
        }
        catch (Exception)
        {
            ErrorMessage = "שגיאה בטעינת ההפקדות המתוזמנות.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Opens the create form and creates a new scheduled deposit if the user confirms.
    /// </summary>
    [RelayCommand]
    private async Task CreateScheduledDepositAsync()
    {
        if (DepositFormRequested == null) return;

        ErrorMessage = string.Empty;

        try
        {
            var funds = await _fundService.GetFundsAsync(_portfolioId);
            var result = await DepositFormRequested.Invoke(funds, null);

            if (result == null) return; // User cancelled.

            IsLoading = true;

            await _scheduledDepositService.UpsertScheduledDepositAsync(
                _portfolioId,
                result.FundId,
                result.Name,
                result.AmountAgoras,
                result.ScheduleKind,
                result.IsEnabled,
                result.Note,
                result.TimeOfDayMinutes,
                result.WeekdayMask,
                result.DayOfMonth,
                result.NextRunAtUtc,
                scheduledDepositId: null);

            await LoadScheduledDepositsAsync();
        }
        catch (EmptyFundNameException) { ErrorMessage = "שם ההפקדה לא יכול להיות ריק."; }
        catch (NegativeFundAmountException) { ErrorMessage = "הסכום חייב להיות חיובי."; }
        catch (InvalidScheduleFieldsException) { ErrorMessage = "שדות התזמון אינם תקינים."; }
        catch (InvalidScheduleKindException) { ErrorMessage = "סוג תזמון לא חוקי."; }
        catch (PortfolioClosedException) { ErrorMessage = "לא ניתן לבצע פעולות בתיק סגור."; }
        catch (Exception) { ErrorMessage = "שגיאה ביצירת ההפקדה המתוזמנת."; }
        finally { IsLoading = false; }
    }

    /// <summary>
    /// Opens the edit form for the selected deposit and updates it if the user confirms.
    /// </summary>
    [RelayCommand]
    private async Task EditScheduledDepositAsync(ScheduledDepositListItem? deposit)
    {
        if (deposit == null || DepositFormRequested == null) return;

        ErrorMessage = string.Empty;

        try
        {
            var funds = await _fundService.GetFundsAsync(_portfolioId);
            var result = await DepositFormRequested.Invoke(funds, deposit);

            if (result == null) return; // User cancelled.

            IsLoading = true;

            await _scheduledDepositService.UpsertScheduledDepositAsync(
                _portfolioId,
                result.FundId,
                result.Name,
                result.AmountAgoras,
                result.ScheduleKind,
                result.IsEnabled,
                result.Note,
                result.TimeOfDayMinutes,
                result.WeekdayMask,
                result.DayOfMonth,
                result.NextRunAtUtc,
                scheduledDepositId: deposit.ScheduledDepositId);

            await LoadScheduledDepositsAsync();
        }
        catch (EmptyFundNameException) { ErrorMessage = "שם ההפקדה לא יכול להיות ריק."; }
        catch (NegativeFundAmountException) { ErrorMessage = "הסכום חייב להיות חיובי."; }
        catch (InvalidScheduleFieldsException) { ErrorMessage = "שדות התזמון אינם תקינים."; }
        catch (InvalidScheduleKindException) { ErrorMessage = "סוג תזמון לא חוקי."; }
        catch (PortfolioClosedException) { ErrorMessage = "לא ניתן לבצע פעולות בתיק סגור."; }
        catch (Exception) { ErrorMessage = "שגיאה בעדכון ההפקדה המתוזמנת."; }
        finally { IsLoading = false; }
    }

    /// <summary>
    /// Toggles the enabled/disabled state of the selected deposit via upsert.
    /// No confirmation needed — this is a lightweight toggle.
    /// </summary>
    [RelayCommand]
    private async Task ToggleEnabledAsync(ScheduledDepositListItem? deposit)
    {
        if (deposit == null) return;

        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            await _scheduledDepositService.UpsertScheduledDepositAsync(
                _portfolioId,
                deposit.FundId,
                deposit.Name,
                deposit.AmountAgoras,
                deposit.ScheduleKind,
                !deposit.IsEnabled, // Flip the enabled state.
                deposit.Note,
                deposit.TimeOfDayMinutes,
                deposit.WeekdayMask,
                deposit.DayOfMonth,
                deposit.ScheduleKind == ScheduleKind.OneTime ? deposit.NextRunAtUtc : null,
                scheduledDepositId: deposit.ScheduledDepositId);

            await LoadScheduledDepositsAsync();
        }
        catch (PortfolioClosedException) { ErrorMessage = "לא ניתן לבצע פעולות בתיק סגור."; }
        catch (Exception) { ErrorMessage = "שגיאה בשינוי מצב ההפקדה."; }
        finally { IsLoading = false; }
    }

    /// <summary>
    /// Deletes the selected deposit after user confirmation (section 4.2).
    /// </summary>
    [RelayCommand]
    private async Task DeleteScheduledDepositAsync(ScheduledDepositListItem? deposit)
    {
        if (deposit == null) return;

        ErrorMessage = string.Empty;

        var confirmed = ConfirmationRequested != null
            && await ConfirmationRequested.Invoke($"למחוק את ההפקדה \"{deposit.Name}\"?");

        if (!confirmed) return;

        IsLoading = true;

        try
        {
            await _scheduledDepositService.DeleteScheduledDepositAsync(deposit.ScheduledDepositId);
            await LoadScheduledDepositsAsync();
        }
        catch (PortfolioClosedException) { ErrorMessage = "לא ניתן לבצע פעולות בתיק סגור."; }
        catch (Exception) { ErrorMessage = "שגיאה במחיקת ההפקדה המתוזמנת."; }
        finally { IsLoading = false; }
    }
}
