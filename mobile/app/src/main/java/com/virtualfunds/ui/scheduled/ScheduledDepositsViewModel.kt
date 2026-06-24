package com.virtualfunds.ui.scheduled

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.ShekelParseResult
import com.virtualfunds.domain.israelDateTimeToUtc
import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.domain.model.ScheduleKind
import com.virtualfunds.domain.model.ScheduledDepositListItem
import com.virtualfunds.domain.parseShekelInput
import com.virtualfunds.domain.repository.FundRepository
import com.virtualfunds.domain.repository.ScheduledDepositRepository
import com.virtualfunds.domain.toIsraelTime
import com.virtualfunds.sync.SyncCoordinator
import com.virtualfunds.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Default time-of-day for a new schedule: 09:00 Israel time (mirrors the desktop form default). */
private const val DEFAULT_TIME_OF_DAY_MINUTES = 9 * 60

/**
 * Editor state for the create/edit full-screen form. `null` on the parent state means the list
 * is shown; non-null means the form is shown instead. Mirrors the desktop's
 * `ScheduledDepositFormDialog` fields, but as immutable UI state edited via the VM's handlers.
 *
 * The schedule-specific fields are *all* kept populated (time / weekday / day-of-month / date)
 * regardless of [scheduleKind]; only the relevant ones are sent on save (E8.2). This lets the
 * user switch schedule kinds back and forth without losing what they already typed.
 *
 * [oneTimeDateEpochDay] is the OneTime calendar day as an epoch-day (days since 1970-01-01),
 * interpreted in Israel time. Epoch-day is timezone-neutral, which keeps the value stable as it
 * round-trips through the Material date picker (which works in UTC).
 */
data class ScheduledDepositFormState(
    val editingId: String?,
    val funds: List<FundListItem>,
    val name: String = "",
    val fundId: String? = null,
    val amountText: String = "",
    val scheduleKind: ScheduleKind = ScheduleKind.Daily,
    val timeOfDayMinutes: Int = DEFAULT_TIME_OF_DAY_MINUTES,
    val weekdayMask: Int = 0,
    val dayOfMonth: Int = 1,
    val oneTimeDateEpochDay: Long = 0L,
    val note: String = "",
    val isEnabled: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessageRes: Int = 0,
) {
    /** True for the "new deposit" case; drives the form title (create vs edit). */
    val isCreate: Boolean get() = editingId == null
}

/**
 * UI state for the scheduled-deposits screen (PR-8, E8).
 *
 * [items] are rendered as-is (server already ordered them by `created_at_utc` desc and resolved
 * each target fund's name). [confirmDelete] non-null shows the delete-confirmation dialog.
 * [form] non-null replaces the list with the full-screen create/edit form.
 */
data class ScheduledDepositsUiState(
    val portfolioName: String,
    val items: List<ScheduledDepositListItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessageRes: Int = 0,
    val confirmDelete: ScheduledDepositListItem? = null,
    val deleteSubmitting: Boolean = false,
    val form: ScheduledDepositFormState? = null,
)

/**
 * ViewModel for scheduled-deposit management (PR-8, E8).
 *
 * Mirrors `desktop/VirtualFunds.WPF/ViewModels/ScheduledDepositsViewModel.cs`: list + create /
 * edit / enable-disable / delete. Enable/disable is just an upsert with the `isEnabled` flag
 * flipped — there is no separate RPC (E8.7). For `OneTime` the client computes `next_run_at_utc`
 * from the picked Israel date+time (E8.12); for Daily/Weekly/Monthly the server computes it.
 *
 * Execution itself is not driven here — it runs device-triggered via [SyncCoordinator] (E8.9).
 * After any successful mutation we call [SyncCoordinator.triggerPostMutation] so (a) a freshly
 * created due deposit executes promptly and (b) the fund list refreshes its balances.
 */
// @HiltViewModel tells Hilt to generate a ViewModel factory for this class and inject the
// constructor dependencies (the two repositories + coordinator) from the DI graph. Composables
// obtain the instance via hiltViewModel(), which pulls it from that generated factory — we never
// call the constructor ourselves. SavedStateHandle is injected automatically and carries the
// navigation route args (portfolioId, portfolioName) by key — same approach as FundListViewModel.
@HiltViewModel
class ScheduledDepositsViewModel @Inject constructor(
    private val scheduledDepositRepository: ScheduledDepositRepository,
    private val fundRepository: FundRepository,
    private val syncCoordinator: SyncCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val portfolioId: String = checkNotNull(savedStateHandle["portfolioId"]) {
        "ScheduledDepositsViewModel: portfolioId missing from SavedStateHandle"
    }
    private val portfolioName: String = checkNotNull(savedStateHandle["portfolioName"]) {
        "ScheduledDepositsViewModel: portfolioName missing from SavedStateHandle"
    }

    private val _state = MutableStateFlow(ScheduledDepositsUiState(portfolioName = portfolioName))
    val state: StateFlow<ScheduledDepositsUiState> = _state.asStateFlow()

    /**
     * One-shot transient messages (string-resource ids) shown as snackbars — used for failures
     * on lightweight actions (toggle, delete) and the "create a fund first" hint. A SharedFlow
     * (not state) is the idiom for events that should fire once and not survive recomposition.
     */
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    init {
        refresh()

        // Keep the list fresh on app start/resume and after any mutation in this portfolio —
        // e.g. a OneTime deposit disables itself after it executes (E8.12), which we want to see.
        viewModelScope.launch {
            syncCoordinator.events.collect { event ->
                when (event) {
                    is SyncTrigger.AppStart,
                    is SyncTrigger.AppResume -> refresh()
                    is SyncTrigger.PostMutation -> if (event.portfolioId == portfolioId) refresh()
                }
            }
        }
    }

    // ── List ────────────────────────────────────────────────────────────────────

    fun onRetry() = refresh()

    private fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessageRes = 0) }
        viewModelScope.launch {
            when (val result = scheduledDepositRepository.getScheduledDeposits(portfolioId)) {
                is AppResult.Success ->
                    _state.update { it.copy(items = result.value, isLoading = false, errorMessageRes = 0) }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, errorMessageRes = R.string.sd_error_load) }
            }
        }
    }

    // ── Open the form (create / edit) ─────────────────────────────────────────────

    /**
     * Opens the create form. Loads the portfolio's funds first (needed for the target-fund
     * picker); if there are none, surfaces a hint instead of an unusable empty-picker form —
     * a scheduled deposit must target an existing fund.
     */
    fun onAddClick() {
        viewModelScope.launch {
            when (val result = fundRepository.getFunds(portfolioId)) {
                is AppResult.Success -> {
                    val funds = result.value
                    if (funds.isEmpty()) {
                        _messages.emit(R.string.sd_need_fund_first)
                    } else {
                        _state.update { it.copy(form = newForm(funds)) }
                    }
                }
                is AppResult.Failure -> _messages.emit(R.string.sd_error_load)
            }
        }
    }

    /** Opens the edit form pre-filled from [item]. Loads funds for the target-fund picker. */
    fun onEditClick(item: ScheduledDepositListItem) {
        viewModelScope.launch {
            when (val result = fundRepository.getFunds(portfolioId)) {
                is AppResult.Success -> _state.update { it.copy(form = editForm(item, result.value)) }
                is AppResult.Failure -> _messages.emit(R.string.sd_error_load)
            }
        }
    }

    private fun newForm(funds: List<FundListItem>) = ScheduledDepositFormState(
        editingId = null,
        funds = funds,
        fundId = funds.firstOrNull()?.fundId,
        scheduleKind = ScheduleKind.Daily,
        timeOfDayMinutes = DEFAULT_TIME_OF_DAY_MINUTES,
        oneTimeDateEpochDay = tomorrowIsraelEpochDay(),
        isEnabled = true,
    )

    private fun editForm(item: ScheduledDepositListItem, funds: List<FundListItem>): ScheduledDepositFormState {
        val israel = item.nextRunAtUtc.toIsraelTime()
        // OneTime stores its time inside next_run_at_utc (time_of_day_minutes is null); recover
        // the time-of-day from the Israel-local next run so the time picker shows it correctly.
        val oneTimeMinutes = israel.hour * 60 + israel.minute
        return ScheduledDepositFormState(
            editingId = item.scheduledDepositId,
            funds = funds,
            name = item.name,
            fundId = item.fundId,
            amountText = agorasToEditableShekels(item.amountAgoras),
            scheduleKind = item.scheduleKind,
            timeOfDayMinutes = when (item.scheduleKind) {
                ScheduleKind.OneTime -> oneTimeMinutes
                else -> item.timeOfDayMinutes ?: DEFAULT_TIME_OF_DAY_MINUTES
            },
            weekdayMask = item.weekdayMask ?: 0,
            dayOfMonth = item.dayOfMonth ?: 1,
            oneTimeDateEpochDay = if (item.scheduleKind == ScheduleKind.OneTime) {
                israel.toLocalDate().toEpochDay()
            } else {
                tomorrowIsraelEpochDay()
            },
            note = item.note ?: "",
            isEnabled = item.isEnabled,
        )
    }

    // ── Form field handlers ───────────────────────────────────────────────────────

    fun onFormNameChange(value: String) = updateForm { it.copy(name = value) }
    fun onFormFundChange(fundId: String) = updateForm { it.copy(fundId = fundId) }
    fun onFormAmountChange(value: String) = updateForm { it.copy(amountText = value) }
    fun onFormKindChange(kind: ScheduleKind) = updateForm { it.copy(scheduleKind = kind) }
    fun onFormTimeChange(minutes: Int) = updateForm { it.copy(timeOfDayMinutes = minutes) }
    fun onFormDayOfMonthChange(day: Int) = updateForm { it.copy(dayOfMonth = day) }
    fun onFormDateChange(epochDay: Long) = updateForm { it.copy(oneTimeDateEpochDay = epochDay) }
    fun onFormNoteChange(value: String) = updateForm { it.copy(note = value) }
    fun onFormEnabledChange(enabled: Boolean) = updateForm { it.copy(isEnabled = enabled) }

    /** Toggles one weekday bit in the mask (E4.3.5: bit 0 = Sunday … bit 6 = Saturday). */
    fun onFormWeekdayToggle(dayIndex: Int) =
        updateForm { it.copy(weekdayMask = it.weekdayMask xor (1 shl dayIndex)) }

    fun onFormCancel() {
        val form = _state.value.form ?: return
        if (form.isSubmitting) return
        _state.update { it.copy(form = null) }
    }

    /**
     * Validates the form and upserts (E8.7). Validation order mirrors the desktop dialog's
     * `OkButton_Click`. On success the form closes, a PostMutation is broadcast, and the list
     * reloads.
     */
    fun onFormSave() {
        val form = _state.value.form ?: return
        if (form.isSubmitting) return

        if (form.name.isBlank()) {
            setFormError(R.string.sd_error_empty_name); return
        }
        val fundId = form.fundId
        if (fundId == null) {
            setFormError(R.string.sd_error_no_fund); return
        }
        val amountAgoras = when (val parsed = parseShekelInput(form.amountText)) {
            is ShekelParseResult.Success ->
                if (parsed.agoras > 0) parsed.agoras
                else { setFormError(R.string.fund_error_amount_must_be_positive); return }
            ShekelParseResult.Empty -> { setFormError(R.string.sd_error_amount_required); return }
            ShekelParseResult.NotANumber -> { setFormError(R.string.fund_error_invalid_number); return }
            ShekelParseResult.Negative -> { setFormError(R.string.fund_error_amount_must_be_positive); return }
            ShekelParseResult.TooManyDecimals -> { setFormError(R.string.fund_error_too_many_decimals); return }
        }

        // Build only the schedule fields that apply to the chosen kind (E8.2). The unused ones
        // must be null or the RPC rejects the row with INVALID_SCHEDULE_FIELDS.
        var timeOfDayMinutes: Int? = null
        var weekdayMask: Int? = null
        var dayOfMonth: Int? = null
        var nextRunAtUtc: Instant? = null
        when (form.scheduleKind) {
            ScheduleKind.Daily -> timeOfDayMinutes = form.timeOfDayMinutes
            ScheduleKind.Weekly -> {
                if (form.weekdayMask == 0) { setFormError(R.string.sd_error_no_weekday); return }
                timeOfDayMinutes = form.timeOfDayMinutes
                weekdayMask = form.weekdayMask
            }
            ScheduleKind.Monthly -> {
                timeOfDayMinutes = form.timeOfDayMinutes
                dayOfMonth = form.dayOfMonth
            }
            ScheduleKind.OneTime ->
                nextRunAtUtc = israelDateTimeToUtc(
                    LocalDate.ofEpochDay(form.oneTimeDateEpochDay),
                    form.timeOfDayMinutes,
                )
        }

        val isCreate = form.isCreate
        _state.update { it.copy(form = form.copy(isSubmitting = true, errorMessageRes = 0)) }
        viewModelScope.launch {
            val result = scheduledDepositRepository.upsertScheduledDeposit(
                portfolioId = portfolioId,
                fundId = fundId,
                name = form.name.trim(),
                amountAgoras = amountAgoras,
                scheduleKind = form.scheduleKind,
                isEnabled = form.isEnabled,
                note = form.note.trim().ifEmpty { null },
                timeOfDayMinutes = timeOfDayMinutes,
                weekdayMask = weekdayMask,
                dayOfMonth = dayOfMonth,
                nextRunAtUtc = nextRunAtUtc,
                scheduledDepositId = form.editingId,
            )
            when (result) {
                is AppResult.Success -> {
                    _state.update { it.copy(form = null) }
                    syncCoordinator.triggerPostMutation(portfolioId)
                    refresh()
                }
                is AppResult.Failure -> setFormError(upsertErrorRes(result.error, isCreate))
            }
        }
    }

    // ── Enable / disable (upsert with flipped flag, E8.7) ─────────────────────────

    fun onToggleEnabled(item: ScheduledDepositListItem) {
        viewModelScope.launch {
            val result = scheduledDepositRepository.upsertScheduledDeposit(
                portfolioId = portfolioId,
                fundId = item.fundId,
                name = item.name,
                amountAgoras = item.amountAgoras,
                scheduleKind = item.scheduleKind,
                isEnabled = !item.isEnabled,
                note = item.note,
                timeOfDayMinutes = item.timeOfDayMinutes,
                weekdayMask = item.weekdayMask,
                dayOfMonth = item.dayOfMonth,
                // OneTime carries its execution time in next_run_at_utc; preserve it. Recurring
                // kinds pass null so the server recomputes the next run from the schedule fields.
                nextRunAtUtc = if (item.scheduleKind == ScheduleKind.OneTime) item.nextRunAtUtc else null,
                scheduledDepositId = item.scheduledDepositId,
            )
            when (result) {
                is AppResult.Success -> {
                    syncCoordinator.triggerPostMutation(portfolioId)
                    refresh()
                }
                is AppResult.Failure -> _messages.emit(toggleErrorRes(result.error))
            }
        }
    }

    // ── Delete (with confirmation, section 4.2) ───────────────────────────────────

    fun onDeleteClick(item: ScheduledDepositListItem) {
        _state.update { it.copy(confirmDelete = item) }
    }

    fun onDismissDelete() {
        if (_state.value.deleteSubmitting) return
        _state.update { it.copy(confirmDelete = null) }
    }

    fun onConfirmDelete() {
        val item = _state.value.confirmDelete ?: return
        if (_state.value.deleteSubmitting) return
        _state.update { it.copy(deleteSubmitting = true) }
        viewModelScope.launch {
            when (val result = scheduledDepositRepository.deleteScheduledDeposit(item.scheduledDepositId)) {
                is AppResult.Success -> {
                    _state.update { it.copy(confirmDelete = null, deleteSubmitting = false) }
                    syncCoordinator.triggerPostMutation(portfolioId)
                    refresh()
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(confirmDelete = null, deleteSubmitting = false) }
                    _messages.emit(deleteErrorRes(result.error))
                }
            }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────────

    private inline fun updateForm(block: (ScheduledDepositFormState) -> ScheduledDepositFormState) {
        _state.update { st ->
            val form = st.form ?: return@update st
            if (form.isSubmitting) return@update st
            // Any field edit clears a pending validation error so it doesn't linger.
            st.copy(form = block(form).copy(errorMessageRes = 0))
        }
    }

    private fun setFormError(messageRes: Int) {
        _state.update { st ->
            val form = st.form ?: return@update st
            st.copy(form = form.copy(isSubmitting = false, errorMessageRes = messageRes))
        }
    }

    private fun tomorrowIsraelEpochDay(): Long =
        Instant.now().toIsraelTime().toLocalDate().plusDays(1).toEpochDay()

    /** Formats agoras as a plain editable "1234.56" string (LTR amount field, dot separator). */
    private fun agorasToEditableShekels(agoras: Long): String {
        val whole = agoras / 100
        val frac = (agoras % 100).toInt()
        return "%d.%02d".format(whole, frac)
    }

    // ── Error → string-resource mapping (mirrors desktop ScheduledDepositsViewModel wording) ──

    private fun upsertErrorRes(error: AppError, isCreate: Boolean): Int = when (error) {
        AppError.Validation.EmptyName -> R.string.sd_error_empty_name
        AppError.Validation.NegativeAmount -> R.string.sd_error_amount_positive_server
        AppError.Validation.InvalidScheduleFields -> R.string.sd_error_invalid_schedule_fields
        AppError.Validation.InvalidScheduleKind -> R.string.sd_error_invalid_schedule_kind
        AppError.Validation.PortfolioClosed -> R.string.fund_error_portfolio_closed
        is AppError.NotFound -> R.string.sd_error_not_found
        else -> if (isCreate) R.string.sd_error_create_generic else R.string.sd_error_update_generic
    }

    private fun toggleErrorRes(error: AppError): Int = when (error) {
        AppError.Validation.PortfolioClosed -> R.string.fund_error_portfolio_closed
        else -> R.string.sd_error_toggle_generic
    }

    private fun deleteErrorRes(error: AppError): Int = when (error) {
        AppError.Validation.PortfolioClosed -> R.string.fund_error_portfolio_closed
        is AppError.NotFound -> R.string.sd_error_not_found
        else -> R.string.sd_error_delete_generic
    }
}
