using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Utilities;

namespace VirtualFunds.WPF.Views;

/// <summary>
/// A modal dialog for creating or editing a scheduled deposit (PR-8, E8).
/// <para>
/// No ViewModel — lightweight code-behind form following the <see cref="FundCreateDialog"/> pattern.
/// The caller reads result properties after <c>DialogResult == true</c>.
/// </para>
/// </summary>
public partial class ScheduledDepositFormDialog : Window
{
    private readonly CheckBox[] _weekdayCheckBoxes;

    /// <summary>The entered deposit name.</summary>
    public string DepositName => NameTextBox.Text.Trim();

    /// <summary>The selected target fund.</summary>
    public FundListItem? SelectedFund => FundComboBox.SelectedItem as FundListItem;

    /// <summary>The deposit amount in agoras.</summary>
    public long AmountAgoras { get; private set; }

    /// <summary>The selected schedule kind (E8.2).</summary>
    public ScheduleKind ScheduleKind { get; private set; }

    /// <summary>Whether the deposit is enabled.</summary>
    public bool IsDepositEnabled => EnabledCheckBox.IsChecked == true;

    /// <summary>Optional note text.</summary>
    public string? Note => string.IsNullOrWhiteSpace(NoteTextBox.Text) ? null : NoteTextBox.Text.Trim();

    /// <summary>Time of day in minutes (0–1439) for recurring schedules.</summary>
    public int? TimeOfDayMinutes { get; private set; }

    /// <summary>Weekday bitmask for Weekly schedule.</summary>
    public int? WeekdayMask { get; private set; }

    /// <summary>Day of month (1–28) for Monthly schedule.</summary>
    public int? DayOfMonth { get; private set; }

    /// <summary>Execution time in UTC for OneTime schedule.</summary>
    public DateTime? NextRunAtUtc { get; private set; }

    /// <summary>
    /// Initializes the form dialog.
    /// </summary>
    /// <param name="funds">The fund list for the target fund ComboBox.</param>
    /// <param name="existing">An existing deposit to edit, or null for create.</param>
    public ScheduledDepositFormDialog(IReadOnlyList<FundListItem> funds, ScheduledDepositListItem? existing)
    {
        InitializeComponent();

        // Store weekday checkboxes in bit order (Sunday=0 … Saturday=6).
        _weekdayCheckBoxes = [DaySunday, DayMonday, DayTuesday, DayWednesday, DayThursday, DayFriday, DaySaturday];

        // Populate fund ComboBox.
        FundComboBox.ItemsSource = funds;
        if (funds.Count > 0)
            FundComboBox.SelectedIndex = 0;

        // Populate day-of-month ComboBox (1–28).
        for (var day = 1; day <= 28; day++)
            DayOfMonthComboBox.Items.Add(day);
        DayOfMonthComboBox.SelectedIndex = 0;

        // Default schedule kind to Daily.
        ScheduleKindComboBox.SelectedIndex = 0;

        // Pre-fill fields when editing.
        if (existing != null)
        {
            Title = "עריכת הפקדה מתוזמנת";
            NameTextBox.Text = existing.Name;
            NoteTextBox.Text = existing.Note ?? string.Empty;
            EnabledCheckBox.IsChecked = existing.IsEnabled;

            // Amount: convert agoras back to shekel for display.
            var shekel = existing.AmountAgoras / 100m;
            AmountTextBox.Text = shekel.ToString("F2", CultureInfo.InvariantCulture);

            // Select the target fund.
            for (var i = 0; i < funds.Count; i++)
            {
                if (funds[i].FundId == existing.FundId)
                {
                    FundComboBox.SelectedIndex = i;
                    break;
                }
            }

            // Select the schedule kind.
            for (var i = 0; i < ScheduleKindComboBox.Items.Count; i++)
            {
                if (ScheduleKindComboBox.Items[i] is ComboBoxItem item && (string)item.Tag == existing.ScheduleKind.ToString())
                {
                    ScheduleKindComboBox.SelectedIndex = i;
                    break;
                }
            }

            // Pre-fill schedule-specific fields.
            if (existing.TimeOfDayMinutes.HasValue)
                TimeOfDayTextBox.Text = IsraelTimeHelper.FormatTimeOfDay(existing.TimeOfDayMinutes.Value);

            if (existing.WeekdayMask.HasValue)
            {
                var mask = existing.WeekdayMask.Value;
                for (var i = 0; i < 7; i++)
                    _weekdayCheckBoxes[i].IsChecked = (mask & (1 << i)) != 0;
            }

            if (existing.DayOfMonth.HasValue)
                DayOfMonthComboBox.SelectedItem = existing.DayOfMonth.Value;

            if (existing.ScheduleKind == ScheduleKind.OneTime)
            {
                var israelTime = IsraelTimeHelper.ToIsraelTime(existing.NextRunAtUtc);
                OneTimeDatePicker.SelectedDate = israelTime.Date;
                OneTimeTimeTextBox.Text = israelTime.ToString("HH:mm");
            }
        }
        else
        {
            Title = "הפקדה מתוזמנת חדשה";
            // Default OneTime date/time to tomorrow at 09:00 Israel time.
            var israelNow = IsraelTimeHelper.ToIsraelTime(DateTime.UtcNow);
            OneTimeDatePicker.SelectedDate = israelNow.Date.AddDays(1);
            OneTimeTimeTextBox.Text = "09:00";
        }

        Loaded += (_, _) =>
        {
            NameTextBox.Focus();
            NameTextBox.SelectAll();
        };
    }

    /// <summary>
    /// Toggles visibility of schedule-specific fields based on the selected schedule kind.
    /// </summary>
    private void ScheduleKindComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (ScheduleKindComboBox.SelectedItem is not ComboBoxItem selected) return;
        var kind = (string)selected.Tag;

        var showTimeOfDay = kind is "Daily" or "Weekly" or "Monthly";
        var showWeekday = kind == "Weekly";
        var showDayOfMonth = kind == "Monthly";
        var showOneTime = kind == "OneTime";

        SetVisibility(TimeOfDayLabel, showTimeOfDay);
        SetVisibility(TimeOfDayTextBox, showTimeOfDay);
        SetVisibility(WeekdayLabel, showWeekday);
        SetVisibility(WeekdayPanel, showWeekday);
        SetVisibility(DayOfMonthLabel, showDayOfMonth);
        SetVisibility(DayOfMonthComboBox, showDayOfMonth);
        SetVisibility(OneTimeDateLabel, showOneTime);
        SetVisibility(OneTimeDatePanel, showOneTime);
    }

    /// <summary>
    /// OK button click: validates all fields and closes with a positive result.
    /// </summary>
    private void OkButton_Click(object sender, RoutedEventArgs e)
    {
        ErrorText.Text = string.Empty;

        // 1. Validate name.
        if (string.IsNullOrWhiteSpace(NameTextBox.Text))
        {
            ShowError("נא להזין שם."); return;
        }

        // 2. Validate fund selection.
        if (FundComboBox.SelectedItem is not FundListItem)
        {
            ShowError("נא לבחור קרן."); return;
        }

        // 3. Validate amount.
        var amountText = AmountTextBox.Text.Trim();
        if (string.IsNullOrEmpty(amountText))
        {
            ShowError("נא להזין סכום."); return;
        }
        if (!decimal.TryParse(amountText, NumberStyles.AllowDecimalPoint, CultureInfo.InvariantCulture, out var shekelAmount))
        {
            ShowError("נא להזין מספר חוקי."); return;
        }
        if (shekelAmount <= 0)
        {
            ShowError("הסכום חייב להיות גדול מאפס."); return;
        }
        var fractionalPart = shekelAmount % 1;
        if (fractionalPart != 0 && decimal.Round(fractionalPart, 2) != fractionalPart)
        {
            ShowError("ניתן להזין עד שתי ספרות אחרי הנקודה."); return;
        }

        AmountAgoras = (long)(shekelAmount * 100);

        // 4. Validate schedule kind and specific fields.
        if (ScheduleKindComboBox.SelectedItem is not ComboBoxItem kindItem)
        {
            ShowError("נא לבחור סוג תזמון."); return;
        }

        // Use the tag string for switch logic; convert to enum only after validation.
        var kindTag = (string)kindItem.Tag;

        // Reset schedule-specific outputs.
        TimeOfDayMinutes = null;
        WeekdayMask = null;
        DayOfMonth = null;
        NextRunAtUtc = null;

        switch (kindTag)
        {
            case "Daily":
            case "Weekly":
            case "Monthly":
                if (!TryParseTimeOfDay(out var minutes))
                {
                    ShowError("נא להזין שעה בפורמט HH:MM (לדוגמה: 09:00)."); return;
                }
                TimeOfDayMinutes = minutes;

                if (kindTag == "Weekly")
                {
                    var mask = ComputeWeekdayMask();
                    if (mask == 0)
                    {
                        ShowError("נא לבחור לפחות יום אחד בשבוע."); return;
                    }
                    WeekdayMask = mask;
                }

                if (kindTag == "Monthly")
                {
                    if (DayOfMonthComboBox.SelectedItem is int day)
                        DayOfMonth = day;
                    else
                    {
                        ShowError("נא לבחור יום בחודש."); return;
                    }
                }
                break;

            case "OneTime":
                if (!TryParseOneTimeDateTime(out var utcTime))
                {
                    ShowError("נא להזין תאריך ושעה תקינים."); return;
                }
                NextRunAtUtc = utcTime;
                break;
        }

        ScheduleKind = Enum.Parse<ScheduleKind>(kindTag);

        DialogResult = true;
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private bool TryParseTimeOfDay(out int minutes)
    {
        minutes = 0;
        var text = TimeOfDayTextBox.Text.Trim();
        var parts = text.Split(':');
        if (parts.Length != 2) return false;
        if (!int.TryParse(parts[0], out var hours) || !int.TryParse(parts[1], out var mins)) return false;
        if (hours < 0 || hours > 23 || mins < 0 || mins > 59) return false;
        minutes = hours * 60 + mins;
        return true;
    }

    private int ComputeWeekdayMask()
    {
        var mask = 0;
        for (var i = 0; i < 7; i++)
        {
            if (_weekdayCheckBoxes[i].IsChecked == true)
                mask |= (1 << i);
        }
        return mask;
    }

    private bool TryParseOneTimeDateTime(out DateTime utc)
    {
        utc = default;

        if (OneTimeDatePicker.SelectedDate is not DateTime date)
            return false;

        if (!TryParseTime(OneTimeTimeTextBox.Text.Trim(), out var hours, out var mins))
            return false;

        var israelLocal = date.Date.AddHours(hours).AddMinutes(mins);
        utc = IsraelTimeHelper.OneTimeUtcFromIsraelDateTime(israelLocal);
        return true;
    }

    private static bool TryParseTime(string text, out int hours, out int minutes)
    {
        hours = 0;
        minutes = 0;
        var parts = text.Split(':');
        if (parts.Length != 2) return false;
        if (!int.TryParse(parts[0], out hours) || !int.TryParse(parts[1], out minutes)) return false;
        return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59;
    }

    private void ShowError(string message) => ErrorText.Text = message;

    private static void SetVisibility(UIElement element, bool visible) =>
        element.Visibility = visible ? Visibility.Visible : Visibility.Collapsed;
}
