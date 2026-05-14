package com.virtualfunds.ui.fund

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.ShekelParseResult
import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.domain.parseShekelInput
import com.virtualfunds.domain.repository.FundRepository
import com.virtualfunds.sync.SyncCoordinator
import com.virtualfunds.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Presentation-only sort modes for the fund list (E5.10).
 *
 * Mirrors `desktop/VirtualFunds.WPF/ViewModels/FundSortOption.cs`. No money algorithm may
 * depend on these values — they affect display order only.
 */
enum class FundSortMode {
    /** Sort by creation date, oldest first (UI default per E5.10). */
    CreatedDate,
    /** Sort alphabetically by fund name (matches server order). */
    Name,
    /** Sort by balance, highest first. */
    Balance,
    /** Sort by derived allocation percentage, highest first. */
    AllocationPercent,
    /** User-defined order, maintained in memory via up/down buttons. */
    Custom,
}

/**
 * Discriminates which (if any) modal dialog is currently visible. Same single-sealed-type
 * approach as [com.virtualfunds.ui.portfolio.PortfolioDialog] — the View renders
 * `when (state.dialog)`.
 */
sealed class FundDialog {
    data object None : FundDialog()

    /**
     * Dialog for creating a new fund (E5.6): name + optional initial shekel amount.
     *
     * [amountText] is the raw user input (LTR shekel string). It is parsed on confirm by
     * [parseShekelInput]; results other than `Empty` / `Success` populate [errorMessageRes].
     */
    data class CreateInput(
        val name: String = "",
        val amountText: String = "",
        val isSubmitting: Boolean = false,
        val errorMessageRes: Int = 0,
    ) : FundDialog()

    /**
     * Dialog for renaming an existing fund (E5.7). [target] is the fund being renamed; [name]
     * is the user's editable copy. Pre-filled with `target.name` when the dialog opens.
     */
    data class RenameInput(
        val target: FundListItem,
        val name: String,
        val isSubmitting: Boolean = false,
        val errorMessageRes: Int = 0,
    ) : FundDialog()

    /** Yes/no confirmation before deleting a fund (E5.8). */
    data class ConfirmDelete(
        val target: FundListItem,
        val isSubmitting: Boolean = false,
    ) : FundDialog()
}

/**
 * UI state for the fund list screen.
 *
 * [funds] are already sorted according to [sortMode] — the View renders them as-is.
 * [errorMessageRes] is the screen-level error (e.g. load failure); per-dialog errors live
 * inside the dialog subtypes.
 */
data class FundListUiState(
    val portfolioName: String,
    val funds: List<FundListItem> = emptyList(),
    val totalAgoras: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessageRes: Int = 0,
    val dialog: FundDialog = FundDialog.None,
    val sortMode: FundSortMode = FundSortMode.CreatedDate,
    val selectedFundId: String? = null,
) {
    /** True when the move-up button should be enabled for [selectedFundId] (Custom mode only). */
    val canMoveSelectedUp: Boolean
        get() = sortMode == FundSortMode.Custom &&
            selectedFundId != null &&
            funds.size > 1 &&
            funds.indexOfFirst { it.fundId == selectedFundId } > 0

    /** True when the move-down button should be enabled for [selectedFundId] (Custom mode only). */
    val canMoveSelectedDown: Boolean
        get() = sortMode == FundSortMode.Custom &&
            selectedFundId != null &&
            funds.size > 1 &&
            run {
                val idx = funds.indexOfFirst { it.fundId == selectedFundId }
                idx in 0 until funds.size - 1
            }
}

/**
 * ViewModel for the fund list screen (PR-5, E5.6–E5.10).
 *
 * Mirrors `desktop/VirtualFunds.WPF/ViewModels/PortfolioViewModel.cs` for the fund-management
 * portion (create / rename / delete + sort modes). Money operations (deposit / withdraw /
 * transfer / revalue) and the portfolio's own rename / delete are out of scope for this PR.
 *
 * Responsibilities:
 *   1. Load funds for [portfolioId] and expose them sorted by [FundListUiState.sortMode].
 *   2. Open create / rename / delete dialogs and handle their submissions.
 *   3. Track the user's chosen sort mode (in-memory only — resets on app restart per E5.10).
 *   4. Maintain the in-memory custom order (list of fund IDs) when Custom mode is selected,
 *      with up/down buttons. New funds appended alphabetically; deleted funds dropped.
 *   5. Listen to [SyncCoordinator.events] so the list refreshes on app start / resume.
 *   6. Inform the sync coordinator which portfolio is active (so scheduled-deposit execution
 *      can fire on the next app resume — PR-8 territory but the hook is here today).
 */
// @HiltViewModel + @Inject — Hilt constructs this VM and injects FundRepository, SyncCoordinator
// from the DI graph. SavedStateHandle is also injected automatically by Hilt; we read the
// navigation route arguments out of it by key (`portfolioId`, `portfolioName`) — Navigation
// Compose stores each @Serializable route property under its own name in the handle. We
// avoid the `toRoute<Routes.FundList>()` extension because it pulls in navigation runtime
// setup that is awkward to provide in unit tests; reading the keys directly is just as
// type-safe at the boundary and makes the VM trivially testable with a plain SavedStateHandle.
@HiltViewModel
class FundListViewModel @Inject constructor(
    private val fundRepository: FundRepository,
    private val syncCoordinator: SyncCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val portfolioId: String = checkNotNull(savedStateHandle["portfolioId"]) {
        "FundListViewModel: portfolioId missing from SavedStateHandle"
    }
    private val portfolioName: String = checkNotNull(savedStateHandle["portfolioName"]) {
        "FundListViewModel: portfolioName missing from SavedStateHandle"
    }

    /**
     * Remembered custom order — a list of fund IDs in the user's chosen sequence. Populated
     * when the user enters Custom mode or moves funds. In-memory only; reset when this VM
     * is recreated (e.g. fresh app start). Mirrors the desktop's `_customOrderedFundIds`.
     */
    private var customOrderedFundIds: List<String> = emptyList()

    private val _state = MutableStateFlow(FundListUiState(portfolioName = portfolioName))
    val state: StateFlow<FundListUiState> = _state.asStateFlow()

    init {
        // Inform the sync coordinator so scheduled-deposit execution can fire on the next
        // app resume (E8.4 trigger — relevant once PR-8 lands but the hook is harmless now).
        syncCoordinator.setActivePortfolioId(portfolioId)

        refresh()

        viewModelScope.launch {
            syncCoordinator.events.collect { event ->
                when (event) {
                    is SyncTrigger.AppStart,
                    is SyncTrigger.AppResume -> refresh()
                    is SyncTrigger.PostMutation ->
                        if (event.portfolioId == portfolioId) refresh()
                }
            }
        }
    }

    override fun onCleared() {
        // Leave the active-portfolio slot empty so AppStart/Resume in a list-only context
        // does not try to execute deposits with this id after the user has navigated away.
        syncCoordinator.setActivePortfolioId(null)
        super.onCleared()
    }

    // ── Public actions ──────────────────────────────────────────────────────────

    fun onRetry() = refresh()

    fun onCreateClick() {
        _state.update { it.copy(dialog = FundDialog.CreateInput()) }
    }

    fun onRenameClick(fund: FundListItem) {
        _state.update {
            it.copy(dialog = FundDialog.RenameInput(target = fund, name = fund.name))
        }
    }

    fun onDeleteClick(fund: FundListItem) {
        _state.update { it.copy(dialog = FundDialog.ConfirmDelete(target = fund)) }
    }

    /** Single text-change handler that updates whichever dialog field corresponds to [field]. */
    fun onDialogNameChange(value: String) {
        _state.update { state ->
            when (val dlg = state.dialog) {
                is FundDialog.CreateInput ->
                    state.copy(dialog = dlg.copy(name = value, errorMessageRes = 0))
                is FundDialog.RenameInput ->
                    state.copy(dialog = dlg.copy(name = value, errorMessageRes = 0))
                else -> state
            }
        }
    }

    fun onDialogAmountChange(value: String) {
        _state.update { state ->
            val dlg = state.dialog as? FundDialog.CreateInput ?: return@update state
            state.copy(dialog = dlg.copy(amountText = value, errorMessageRes = 0))
        }
    }

    fun onDialogDismiss() {
        val current = _state.value.dialog
        val busy = when (current) {
            is FundDialog.CreateInput   -> current.isSubmitting
            is FundDialog.RenameInput   -> current.isSubmitting
            is FundDialog.ConfirmDelete -> current.isSubmitting
            FundDialog.None             -> false
        }
        if (busy) return
        _state.update { it.copy(dialog = FundDialog.None) }
    }

    fun onDialogConfirm() {
        when (val dlg = _state.value.dialog) {
            is FundDialog.CreateInput -> if (!dlg.isSubmitting) submitCreate(dlg)
            is FundDialog.RenameInput -> if (!dlg.isSubmitting) submitRename(dlg)
            else -> Unit
        }
    }

    fun onConfirmDelete() {
        val dlg = _state.value.dialog as? FundDialog.ConfirmDelete ?: return
        if (dlg.isSubmitting) return
        submitDelete(dlg.target)
    }

    /**
     * Change the sort mode. When entering Custom mode for the first time, seed the remembered
     * order from the current display order (mirrors desktop behaviour).
     */
    fun onSortModeChange(mode: FundSortMode) {
        _state.update { state ->
            if (state.sortMode == mode) return@update state

            if (mode == FundSortMode.Custom && customOrderedFundIds.isEmpty()) {
                customOrderedFundIds = state.funds.map { it.fundId }
            }
            val sorted = applySort(state.funds, mode)
            state.copy(sortMode = mode, funds = sorted)
        }
    }

    /** Select a fund (Custom-mode move-up/down buttons need a selection). */
    fun onSelectFund(fundId: String?) {
        _state.update { it.copy(selectedFundId = fundId) }
    }

    fun onMoveSelectedUp() {
        val s = _state.value
        if (!s.canMoveSelectedUp) return
        moveSelected(delta = -1)
    }

    fun onMoveSelectedDown() {
        val s = _state.value
        if (!s.canMoveSelectedDown) return
        moveSelected(delta = +1)
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessageRes = 0) }

        viewModelScope.launch {
            when (val result = fundRepository.getFunds(portfolioId)) {
                is AppResult.Success -> {
                    val funds = result.value
                    val total = funds.sumOf { it.balanceAgoras }
                    val sorted = applySort(funds, _state.value.sortMode)
                    _state.update {
                        it.copy(
                            funds = sorted,
                            totalAgoras = total,
                            isLoading = false,
                            errorMessageRes = 0,
                            // Drop the selection if the selected fund no longer exists.
                            selectedFundId = it.selectedFundId?.takeIf { id ->
                                sorted.any { f -> f.fundId == id }
                            },
                        )
                    }
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessageRes = R.string.fund_list_error_load,
                        )
                    }
                }
            }
        }
    }

    private fun submitCreate(dlg: FundDialog.CreateInput) {
        // Parse the amount field on submit (the user may type partial values while editing).
        val amountAgoras: Long = when (val parsed = parseShekelInput(dlg.amountText)) {
            ShekelParseResult.Empty -> 0L
            is ShekelParseResult.Success -> parsed.agoras
            ShekelParseResult.NotANumber -> {
                setCreateError(R.string.fund_error_invalid_number)
                return
            }
            ShekelParseResult.Negative -> {
                setCreateError(R.string.fund_error_negative_amount)
                return
            }
            ShekelParseResult.TooManyDecimals -> {
                setCreateError(R.string.fund_error_too_many_decimals)
                return
            }
        }

        markCreateSubmitting(true)
        viewModelScope.launch {
            when (val result = fundRepository.createFund(portfolioId, dlg.name, amountAgoras)) {
                is AppResult.Success -> {
                    _state.update { it.copy(dialog = FundDialog.None) }
                    syncCoordinator.triggerPostMutation(portfolioId)
                    refresh()
                }
                is AppResult.Failure -> setCreateError(createErrorRes(result.error))
            }
        }
    }

    private fun submitRename(dlg: FundDialog.RenameInput) {
        markRenameSubmitting(true)
        viewModelScope.launch {
            when (val result = fundRepository.renameFund(portfolioId, dlg.target.fundId, dlg.name)) {
                is AppResult.Success -> {
                    _state.update { it.copy(dialog = FundDialog.None) }
                    syncCoordinator.triggerPostMutation(portfolioId)
                    refresh()
                }
                is AppResult.Failure -> setRenameError(renameErrorRes(result.error))
            }
        }
    }

    private fun submitDelete(target: FundListItem) {
        markDeleteSubmitting(true)
        viewModelScope.launch {
            when (val result = fundRepository.deleteFund(portfolioId, target.fundId)) {
                is AppResult.Success -> {
                    _state.update { it.copy(dialog = FundDialog.None) }
                    syncCoordinator.triggerPostMutation(portfolioId)
                    refresh()
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(
                            dialog = FundDialog.None,
                            errorMessageRes = deleteErrorRes(result.error),
                        )
                    }
                }
            }
        }
    }

    private fun markCreateSubmitting(submitting: Boolean) {
        _state.update { state ->
            val dlg = state.dialog as? FundDialog.CreateInput ?: return@update state
            state.copy(dialog = dlg.copy(isSubmitting = submitting, errorMessageRes = 0))
        }
    }

    private fun markRenameSubmitting(submitting: Boolean) {
        _state.update { state ->
            val dlg = state.dialog as? FundDialog.RenameInput ?: return@update state
            state.copy(dialog = dlg.copy(isSubmitting = submitting, errorMessageRes = 0))
        }
    }

    private fun markDeleteSubmitting(submitting: Boolean) {
        _state.update { state ->
            val dlg = state.dialog as? FundDialog.ConfirmDelete ?: return@update state
            state.copy(dialog = dlg.copy(isSubmitting = submitting))
        }
    }

    private fun setCreateError(messageRes: Int) {
        _state.update { state ->
            val dlg = state.dialog as? FundDialog.CreateInput ?: return@update state
            state.copy(dialog = dlg.copy(isSubmitting = false, errorMessageRes = messageRes))
        }
    }

    private fun setRenameError(messageRes: Int) {
        _state.update { state ->
            val dlg = state.dialog as? FundDialog.RenameInput ?: return@update state
            state.copy(dialog = dlg.copy(isSubmitting = false, errorMessageRes = messageRes))
        }
    }

    /**
     * Returns [funds] sorted according to [mode]. For [FundSortMode.Custom], funds appear in
     * the remembered order; any funds not yet remembered are appended alphabetically. As a
     * side effect, [customOrderedFundIds] is updated to drop deleted fund IDs and pick up
     * new ones so it stays in sync with the data.
     *
     * Visible for testing — pure function aside from the custom-order sync.
     */
    internal fun applySort(funds: List<FundListItem>, mode: FundSortMode): List<FundListItem> =
        when (mode) {
            FundSortMode.CreatedDate -> funds.sortedBy { it.createdAtUtc }
            FundSortMode.Name -> funds.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            FundSortMode.Balance -> funds.sortedByDescending { it.balanceAgoras }
            FundSortMode.AllocationPercent -> funds.sortedByDescending { it.allocationPercent }
            FundSortMode.Custom -> applyCustomOrder(funds)
        }

    private fun applyCustomOrder(funds: List<FundListItem>): List<FundListItem> {
        val byId = funds.associateBy { it.fundId }
        val ordered = mutableListOf<FundListItem>()

        // First: funds already in the remembered order, in that order.
        for (id in customOrderedFundIds) {
            byId[id]?.let { ordered.add(it) }
        }

        // Then: any new funds not yet remembered, alphabetically (matches desktop fallback).
        val remembered = customOrderedFundIds.toHashSet()
        val newFunds = funds
            .filter { it.fundId !in remembered }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        ordered.addAll(newFunds)

        // Sync the remembered list — drops deleted IDs, adds new ones.
        customOrderedFundIds = ordered.map { it.fundId }
        return ordered
    }

    private fun moveSelected(delta: Int) {
        _state.update { state ->
            val selectedId = state.selectedFundId ?: return@update state
            val idx = state.funds.indexOfFirst { it.fundId == selectedId }
            if (idx < 0) return@update state
            val newIdx = idx + delta
            if (newIdx !in state.funds.indices) return@update state

            val newList = state.funds.toMutableList()
            val item = newList.removeAt(idx)
            newList.add(newIdx, item)
            customOrderedFundIds = newList.map { it.fundId }
            state.copy(funds = newList)
        }
    }

    // ── Error → string-resource mapping (mirrors desktop PortfolioViewModel wording) ──

    private fun createErrorRes(error: AppError): Int = when (error) {
        AppError.Validation.EmptyName        -> R.string.fund_error_empty_name
        AppError.Validation.DuplicateName    -> R.string.fund_error_duplicate_name
        AppError.Validation.NegativeAmount   -> R.string.fund_error_negative_amount
        AppError.Validation.PortfolioClosed  -> R.string.fund_error_portfolio_closed
        else                                 -> R.string.fund_error_create_generic
    }

    private fun renameErrorRes(error: AppError): Int = when (error) {
        AppError.Validation.EmptyName        -> R.string.fund_error_empty_name
        AppError.Validation.DuplicateName    -> R.string.fund_error_duplicate_name
        AppError.Validation.PortfolioClosed  -> R.string.fund_error_portfolio_closed
        is AppError.NotFound                 -> R.string.fund_error_not_found
        else                                 -> R.string.fund_error_rename_generic
    }

    private fun deleteErrorRes(error: AppError): Int = when (error) {
        AppError.Validation.FundNotEmpty                  -> R.string.fund_error_fund_not_empty
        AppError.Validation.FundHasEnabledScheduledDeposit -> R.string.fund_error_fund_has_scheduled_deposit
        AppError.Validation.PortfolioClosed               -> R.string.fund_error_portfolio_closed
        is AppError.NotFound                              -> R.string.fund_error_not_found
        else                                              -> R.string.fund_error_delete_generic
    }
}
