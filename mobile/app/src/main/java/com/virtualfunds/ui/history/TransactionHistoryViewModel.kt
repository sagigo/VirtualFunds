package com.virtualfunds.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.FundFilterOption
import com.virtualfunds.domain.model.TransactionGroup
import com.virtualfunds.domain.model.TransactionType
import com.virtualfunds.domain.model.TransactionTypeFilter
import com.virtualfunds.domain.model.TransactionTypeLabels
import com.virtualfunds.domain.repository.FundRepository
import com.virtualfunds.domain.repository.TransactionRepository
import com.virtualfunds.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * UI state for the transaction history screen (PR-7, E7.6).
 *
 * [groups] is the filtered history already truncated to the current display page (see paging
 * note on [TransactionHistoryViewModel]). The full unfiltered set lives privately in the VM —
 * the UI only ever sees the slice it should render.
 */
data class TransactionHistoryUiState(
    val portfolioName: String = "",
    val isLoading: Boolean = false,
    val errorMessageRes: Int = 0,
    /** Filtered groups, newest-first, truncated to the current display page (E7.6). */
    val groups: List<TransactionGroup> = emptyList(),
    /** True when the filtered result set is empty after a successful load. */
    val isEmpty: Boolean = false,
    /** True when more filtered groups exist beyond [groups]; drives the "load more" footer. */
    val hasMore: Boolean = false,

    // ── Filters (E7.6) ─────────────────────────────────────────────────────────
    /** Inclusive lower bound (device-local calendar date), or null for no lower bound. */
    val fromDate: LocalDate? = null,
    /** Inclusive upper bound (device-local calendar date), or null for no upper bound. */
    val toDate: LocalDate? = null,
    /** Active + tombstoned funds available in the fund dropdown. */
    val fundFilterOptions: List<FundFilterOption> = emptyList(),
    /** Selected fund_id, or null for "all funds". */
    val selectedFundId: String? = null,
    /** Static list of filterable summary types (E7.6). */
    val typeFilterOptions: List<TransactionTypeFilter> = TransactionTypeLabels.filterOptions(),
    /** Selected summary type, or null for "all types". */
    val selectedType: TransactionType? = null,

    // ── Undo (E6.12) ───────────────────────────────────────────────────────────
    /** Non-null while the undo confirmation dialog is shown; the operation about to be undone. */
    val undoConfirm: TransactionGroup? = null,
    /** True while an undo RPC is in flight (disables the dialog buttons). */
    val isUndoing: Boolean = false,
    /**
     * One-shot undo-failure message resource (0 = none). The screen shows it in a snackbar and
     * then calls [TransactionHistoryViewModel.onUndoErrorShown] to clear it, so it fires once.
     */
    val undoErrorRes: Int = 0,
) {
    /** True when any filter is active — used to pick the right empty-state message. */
    val hasActiveFilters: Boolean
        get() = fromDate != null || toDate != null || selectedFundId != null || selectedType != null
}

/**
 * ViewModel for the transaction history screen (PR-7, E7.6).
 *
 * Mirrors `desktop/VirtualFunds.WPF/ViewModels/TransactionHistoryViewModel.cs`: it loads the
 * **full** history for the portfolio once, then filters by date range / fund / type entirely
 * **client-side**. A full load (rather than server-side paging) is required because undoability
 * detection (E6.12) needs to see every operation, and it matches desktop behaviour exactly.
 *
 * Paging here is therefore a *display* concern: the filtered list is revealed [PAGE_SIZE]
 * operations at a time as the user scrolls, so an enormous history doesn't compose thousands
 * of cards at once. [onLoadMore] reveals the next page from the already-loaded data — no
 * network round-trip.
 *
 * Undo (E6.12) is handled here via [FundRepository.undoOperation], which writes compensating
 * rows. "Already undone" detection is computed in the repository's `getHistory`
 * ([TransactionGroup.isUndoable]), so the VM just reloads after an undo and the button
 * disappears. CSV export (E9.1) is still deferred.
 */
// @HiltViewModel + @Inject: Hilt builds this VM and injects the two repositories + the sync
// coordinator from the DI graph. SavedStateHandle is provided automatically and carries the
// Navigation route args (portfolioId, portfolioName) — we read them by key, like FundListViewModel.
// FundRepository is here only for undo (the read path uses TransactionRepository); SyncCoordinator
// lets a successful undo broadcast a PostMutation so the fund-list screen behind us refreshes.
@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val fundRepository: FundRepository,
    private val syncCoordinator: SyncCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val portfolioId: String = checkNotNull(savedStateHandle["portfolioId"]) {
        "TransactionHistoryViewModel: portfolioId missing from SavedStateHandle"
    }
    private val portfolioName: String = checkNotNull(savedStateHandle["portfolioName"]) {
        "TransactionHistoryViewModel: portfolioName missing from SavedStateHandle"
    }

    /** Full, unfiltered history loaded once (mirrors desktop's `_allGroups`). */
    private var allGroups: List<TransactionGroup> = emptyList()

    /** How many filtered groups are currently revealed (display paging). */
    private var visibleCount: Int = PAGE_SIZE

    private val _state = MutableStateFlow(TransactionHistoryUiState(portfolioName = portfolioName))
    val state: StateFlow<TransactionHistoryUiState> = _state.asStateFlow()

    init {
        load()
    }

    // ── Public actions ──────────────────────────────────────────────────────────

    fun onRetry() = load()

    fun onFromDateChange(date: LocalDate?) {
        _state.update { it.copy(fromDate = date) }
        resetPagingAndApply()
    }

    fun onToDateChange(date: LocalDate?) {
        _state.update { it.copy(toDate = date) }
        resetPagingAndApply()
    }

    fun onFundFilterChange(fundId: String?) {
        _state.update { it.copy(selectedFundId = fundId) }
        resetPagingAndApply()
    }

    fun onTypeFilterChange(type: TransactionType?) {
        _state.update { it.copy(selectedType = type) }
        resetPagingAndApply()
    }

    fun onClearFilters() {
        _state.update {
            it.copy(fromDate = null, toDate = null, selectedFundId = null, selectedType = null)
        }
        resetPagingAndApply()
    }

    /** Reveal the next page of already-loaded, already-filtered groups (E7.6 display paging). */
    fun onLoadMore() {
        if (!_state.value.hasMore) return
        visibleCount += PAGE_SIZE
        applyFilters()
    }

    // ── Undo (E6.12) ──────────────────────────────────────────────────────────────

    /**
     * Open the undo confirmation dialog for [group]. No-op if the operation isn't undoable
     * (defensive: the button is only shown for undoable rows, but the guard keeps the action
     * honest). Mirrors desktop `UndoOperationAsync`'s `if (!group.IsUndoable) return`.
     */
    fun onUndoClick(group: TransactionGroup) {
        if (!group.isUndoable) return
        _state.update { it.copy(undoConfirm = group) }
    }

    /** Dismiss the undo confirmation dialog (ignored while an undo is in flight). */
    fun onUndoDismiss() {
        if (_state.value.isUndoing) return
        _state.update { it.copy(undoConfirm = null) }
    }

    /**
     * Commit the undo (E6.12 steps 6–8): write compensating rows, broadcast a mutation so the
     * fund-list screen refreshes its balances, then reload history so the undone operation's
     * button disappears and the new "ביטול" entry appears.
     */
    fun onUndoConfirm() {
        val group = _state.value.undoConfirm ?: return
        if (_state.value.isUndoing) return
        _state.update { it.copy(isUndoing = true) }

        viewModelScope.launch {
            when (val result = fundRepository.undoOperation(portfolioId, group.operationId)) {
                is AppResult.Success -> {
                    syncCoordinator.triggerPostMutation(portfolioId)
                    _state.update { it.copy(undoConfirm = null, isUndoing = false) }
                    load()
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(
                            undoConfirm = null,
                            isUndoing = false,
                            undoErrorRes = undoErrorRes(result.error),
                        )
                    }
                }
            }
        }
    }

    /** Clears the one-shot undo error after the screen has shown it in a snackbar. */
    fun onUndoErrorShown() {
        _state.update { it.copy(undoErrorRes = 0) }
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessageRes = 0) }

        viewModelScope.launch {
            when (val history = transactionRepository.getHistory(portfolioId)) {
                is AppResult.Success -> {
                    allGroups = history.value

                    // Fund filter options are best-effort: if that query fails we still show the
                    // history with an empty fund dropdown rather than failing the whole screen.
                    val fundOptions =
                        when (val opts = transactionRepository.getFundFilterOptions(portfolioId)) {
                            is AppResult.Success -> opts.value
                            is AppResult.Failure -> emptyList()
                        }

                    visibleCount = PAGE_SIZE
                    _state.update { it.copy(isLoading = false, fundFilterOptions = fundOptions) }
                    applyFilters()
                }

                is AppResult.Failure -> {
                    _state.update {
                        it.copy(isLoading = false, errorMessageRes = R.string.history_error_load)
                    }
                }
            }
        }
    }

    private fun resetPagingAndApply() {
        visibleCount = PAGE_SIZE
        applyFilters()
    }

    /**
     * Applies the three filters (E7.6) client-side over [allGroups], then truncates to the
     * current display page. Mirrors desktop `TransactionHistoryViewModel.ApplyFilters`.
     */
    private fun applyFilters() {
        val s = _state.value
        var filtered = allGroups.asSequence()

        // Date range — compare device-local calendar dates, like desktop's `.ToLocalTime().Date`.
        s.fromDate?.let { from ->
            filtered = filtered.filter { localDateOf(it.committedAtUtc) >= from }
        }
        s.toDate?.let { to ->
            filtered = filtered.filter { localDateOf(it.committedAtUtc) <= to }
        }
        // Fund filter (E7.6): an operation matches if any of its detail rows references the fund.
        s.selectedFundId?.let { fundId ->
            filtered = filtered.filter { g -> g.details.any { it.fundId == fundId } }
        }
        // Type filter (E7.6): matched against the summary row's transaction_type.
        s.selectedType?.let { type ->
            filtered = filtered.filter { it.transactionType == type }
        }

        val all = filtered.toList()
        val visible = all.take(visibleCount)
        _state.update {
            it.copy(
                groups = visible,
                isEmpty = all.isEmpty(),
                hasMore = all.size > visible.size,
            )
        }
    }

    /** Converts a UTC instant to the device-local calendar date for date-range filtering. */
    private fun localDateOf(instant: Instant): LocalDate =
        instant.atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * Maps an undo failure to a Hebrew message resource. Wording mirrors desktop
     * `UndoOperationAsync`'s catch blocks: the negative-balance case (E6.12 step 5) is the
     * expected, normal rejection; the rest are rarer.
     */
    private fun undoErrorRes(error: AppError): Int = when (error) {
        // Server enforces the negative-balance invariant — compensating amounts would push a
        // fund below zero (E6.12 step 5).
        AppError.Invariant.NegativeBalance -> R.string.history_error_undo_negative_balance
        AppError.Validation.PortfolioClosed -> R.string.history_error_undo_portfolio_closed
        is AppError.NotFound -> R.string.history_error_undo_not_found
        else -> R.string.history_error_undo_generic
    }

    private companion object {
        /** Operation groups revealed per display page (E7.6 paging). */
        const val PAGE_SIZE = 50
    }
}
