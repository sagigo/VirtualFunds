package com.virtualfunds.ui.scheduled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.virtualfunds.R
import com.virtualfunds.domain.formatTimeOfDay
import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.domain.model.ScheduleKind
import com.virtualfunds.ui.theme.VirtualFundsTheme
import java.time.Instant
import java.time.LocalDate

/** Milliseconds in a calendar day — converts between epoch-day and the date picker's UTC millis. */
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The full-screen create/edit form for a scheduled deposit (PR-8, E8). Stateless: it renders
 * [form] and forwards edits via the lambdas. Its own [Scaffold] gives it a dedicated top bar
 * with Cancel (✕) and Save, so it reads as a distinct screen pushed over the list.
 *
 * Schedule fields appear conditionally on the chosen kind (E8.2): the time picker is shown for
 * every kind; weekday chips only for Weekly; day-of-month only for Monthly; the date picker only
 * for OneTime. All times are Israel time (E2.1 / E8.3) — restated in a hint under the fields.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduledDepositFormContent(
    form: ScheduledDepositFormState,
    onNameChange: (String) -> Unit,
    onFundChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onKindChange: (ScheduleKind) -> Unit,
    onTimeChange: (Int) -> Unit,
    onWeekdayToggle: (Int) -> Unit,
    onDayOfMonthChange: (Int) -> Unit,
    onDateChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // Local UI-only state for which picker dialog is open. Kept here (not in the VM) because it
    // is purely transient view state with no business meaning.
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (form.isCreate) R.string.sd_form_create_title else R.string.sd_form_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !form.isSubmitting) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.sd_form_cd_cancel),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = !form.isSubmitting,
                        // Override the default primary content colour so the label is legible on
                        // the primary-coloured top bar (IconButtons inherit this, TextButtons don't).
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                        ),
                    ) {
                        if (form.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.sd_form_save))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Name
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.sd_form_name_label)) },
                singleLine = true,
                enabled = !form.isSubmitting,
                isError = form.errorMessageRes != 0,
                modifier = Modifier.fillMaxWidth(),
            )

            // Target fund picker
            FundPicker(
                funds = form.funds,
                selectedFundId = form.fundId,
                enabled = !form.isSubmitting,
                isError = form.errorMessageRes != 0,
                onSelect = onFundChange,
            )

            // Amount — LTR so the digits + decimal point read correctly inside the RTL form.
            CompositionLtr {
                OutlinedTextField(
                    value = form.amountText,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.sd_form_amount_label)) },
                    supportingText = { Text(stringResource(R.string.sd_form_amount_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !form.isSubmitting,
                    isError = form.errorMessageRes != 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Schedule kind selector (E8.2). SingleChoiceSegmentedButtonRow is Material 3's
            // segmented control — a compact row of mutually-exclusive toggle segments.
            Text(
                text = stringResource(R.string.sd_form_kind_label),
                style = MaterialTheme.typography.labelLarge,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val kinds = ScheduleKind.entries
                kinds.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = form.scheduleKind == kind,
                        onClick = { onKindChange(kind) },
                        enabled = !form.isSubmitting,
                        // itemShape rounds only the outer ends of the row (RTL-aware).
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size),
                    ) {
                        Text(scheduleKindLabel(kind))
                    }
                }
            }

            // Time of day — shown for every kind. Tapping opens the Material clock time picker.
            PickerField(
                label = stringResource(R.string.sd_form_time_label),
                value = formatTimeOfDay(form.timeOfDayMinutes),
                trailingIcon = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.sd_form_cd_pick_time),
                enabled = !form.isSubmitting,
                onClick = { showTimePicker = true },
            )

            // Conditional schedule fields.
            when (form.scheduleKind) {
                ScheduleKind.Weekly -> WeekdayChips(
                    mask = form.weekdayMask,
                    enabled = !form.isSubmitting,
                    onToggle = onWeekdayToggle,
                )
                ScheduleKind.Monthly -> DayOfMonthPicker(
                    dayOfMonth = form.dayOfMonth,
                    enabled = !form.isSubmitting,
                    onChange = onDayOfMonthChange,
                )
                ScheduleKind.OneTime -> PickerField(
                    label = stringResource(R.string.sd_form_date_label),
                    value = formatEpochDay(form.oneTimeDateEpochDay),
                    trailingIcon = Icons.Default.DateRange,
                    contentDescription = stringResource(R.string.sd_form_cd_pick_date),
                    enabled = !form.isSubmitting,
                    onClick = { showDatePicker = true },
                )
                ScheduleKind.Daily -> Unit // time-of-day above is sufficient
            }

            // Optional note
            OutlinedTextField(
                value = form.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.sd_form_note_label)) },
                singleLine = true,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            // Enabled toggle (E8.7 — enable/disable is part of the upsert)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sd_form_enabled_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = form.isEnabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !form.isSubmitting,
                )
            }

            // Timezone clarification (E2.1 / E8.3): schedules are authored in Israel time.
            Text(
                text = stringResource(R.string.sd_form_timezone_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (form.errorMessageRes != 0) {
                Text(
                    text = stringResource(form.errorMessageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialMinutes = form.timeOfDayMinutes,
            onConfirm = {
                onTimeChange(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showDatePicker) {
        OneTimeDatePickerDialog(
            initialEpochDay = form.oneTimeDateEpochDay,
            onConfirm = {
                onDateChange(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * Target-fund dropdown — Material 3's `ExposedDropdownMenuBox` (the combo-box equivalent),
 * same pattern as the transfer dialog's destination picker on the fund screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FundPicker(
    funds: List<FundListItem>,
    selectedFundId: String?,
    enabled: Boolean,
    isError: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = funds.firstOrNull { it.fundId == selectedFundId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.name ?: stringResource(R.string.sd_form_fund_placeholder),
            onValueChange = { /* read-only — selection only via the dropdown */ },
            readOnly = true,
            label = { Text(stringResource(R.string.sd_form_fund_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            isError = isError,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            funds.forEach { fund ->
                DropdownMenuItem(
                    text = { Text(fund.name) },
                    onClick = {
                        expanded = false
                        onSelect(fund.fundId)
                    },
                )
            }
        }
    }
}

/**
 * Weekday selection for Weekly schedules (E4.3.5). Seven `FilterChip`s, Sunday→Saturday, each
 * toggling its bit in the mask. [FlowRow] wraps the chips to a second line on narrow screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(
    mask: Int,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    val abbreviations = stringArrayResource(R.array.sd_weekday_abbreviations)
    Column {
        Text(
            text = stringResource(R.string.sd_form_weekdays_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0..6) {
                FilterChip(
                    selected = (mask and (1 shl i)) != 0,
                    onClick = { onToggle(i) },
                    enabled = enabled,
                    label = { Text(abbreviations[i]) },
                )
            }
        }
    }
}

/** Day-of-month picker for Monthly schedules: a 1–28 dropdown (28 cap avoids month-length issues, E4.3.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOfMonthPicker(
    dayOfMonth: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = dayOfMonth.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.sd_form_day_of_month_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (day in 1..28) {
                DropdownMenuItem(
                    text = { Text(day.toString()) },
                    onClick = {
                        expanded = false
                        onChange(day)
                    },
                )
            }
        }
    }
}

/**
 * A read-only field that opens a picker on tap (used for time and date). We render a disabled
 * [OutlinedTextField] (so it doesn't grab focus or show a keyboard) with enabled-looking colors,
 * and lay a transparent clickable [Box] over it to capture the tap. Wrapped LTR so the numeric
 * value renders correctly inside the RTL form.
 */
@Composable
private fun PickerField(
    label: String,
    value: String,
    trailingIcon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    CompositionLtr {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(label) },
                trailingIcon = { Icon(trailingIcon, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(OutlinedTextFieldDefaults.shape)
                    .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
            )
        }
    }
}

/**
 * Material clock time picker inside an [AlertDialog]. Material 3 ships the `TimePicker` body but
 * no ready-made dialog wrapper, so we wrap it ourselves. `is24Hour = true` matches the desktop's
 * HH:mm format and Israeli convention.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.sd_dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sd_dialog_cancel)) }
        },
        text = { TimePicker(state = state) },
    )
}

/**
 * Material calendar date picker for the OneTime date. The picker works in UTC millis; we convert
 * to/from epoch-day so the value is the calendar day the user sees (interpreted as Israel time on
 * save — E8.12).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneTimeDatePickerDialog(
    initialEpochDay: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochDay * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) onConfirm(millis / MILLIS_PER_DAY) else onDismiss()
                },
            ) { Text(stringResource(R.string.sd_dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sd_dialog_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/** Renders [content] left-to-right inside the otherwise-RTL form (for numeric/date values). */
@Composable
private fun CompositionLtr(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        content = content,
    )
}

/** Formats an Israel-local epoch-day as "dd/MM/yyyy" for the date field. */
private fun formatEpochDay(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
}

// ──────────────────────────────────────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────────────────────────────────────

private val previewFunds = listOf(
    FundListItem(
        fundId = "f1",
        name = "מניות",
        balanceAgoras = 500_000L,
        allocationPercent = 50.0,
        createdAtUtc = Instant.parse("2026-01-15T10:00:00Z"),
    ),
    FundListItem(
        fundId = "f2",
        name = "אג״ח",
        balanceAgoras = 300_000L,
        allocationPercent = 30.0,
        createdAtUtc = Instant.parse("2026-02-01T10:00:00Z"),
    ),
)

@Preview(showBackground = true, locale = "he")
@Composable
private fun ScheduledDepositFormPreview_Weekly() {
    VirtualFundsTheme {
        ScheduledDepositFormContent(
            form = ScheduledDepositFormState(
                editingId = null,
                funds = previewFunds,
                name = "חיסכון חודשי",
                fundId = "f1",
                amountText = "500.00",
                scheduleKind = ScheduleKind.Weekly,
                timeOfDayMinutes = 9 * 60,
                weekdayMask = 0b0000101, // Sunday + Tuesday
                oneTimeDateEpochDay = 20_000L,
            ),
            onNameChange = {}, onFundChange = {}, onAmountChange = {}, onKindChange = {},
            onTimeChange = {}, onWeekdayToggle = {}, onDayOfMonthChange = {}, onDateChange = {},
            onNoteChange = {}, onEnabledChange = {}, onSave = {}, onCancel = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun ScheduledDepositFormPreview_OneTime() {
    VirtualFundsTheme {
        ScheduledDepositFormContent(
            form = ScheduledDepositFormState(
                editingId = "sd1",
                funds = previewFunds,
                name = "בונוס",
                fundId = "f2",
                amountText = "1200.00",
                scheduleKind = ScheduleKind.OneTime,
                timeOfDayMinutes = 14 * 60 + 30,
                oneTimeDateEpochDay = 20_500L,
            ),
            onNameChange = {}, onFundChange = {}, onAmountChange = {}, onKindChange = {},
            onTimeChange = {}, onWeekdayToggle = {}, onDayOfMonthChange = {}, onDateChange = {},
            onNoteChange = {}, onEnabledChange = {}, onSave = {}, onCancel = {},
        )
    }
}
