package com.virtualfunds.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.auth.AuthRepository
import com.virtualfunds.domain.model.PortfolioListItem
import com.virtualfunds.domain.repository.PortfolioRepository
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
 * Discriminates which (if any) modal dialog is currently visible. A single sealed type keeps
 * "what dialog is open" in one place — the View just renders `when (state.dialog)`.
 *
 * Holding dialog state in the ViewModel (not as a `remember { mutableStateOf(...) }` inside
 * the Composable) lets it survive configuration changes and lets the ViewModel close the
 * dialog itself after a successful mutation, without having to call back into the View.
 */
sealed class PortfolioDialog {
    data object None : PortfolioDialog()

    /**
     * Single dialog used for both "create" and "rename" (E5.3 / E5.4) — the desktop
     * collapses both into one `NameInputDialog` and we follow the same pattern.
     *
     * [target] is `null` for create, the existing portfolio for rename.
     */
    data class NameInput(
        val target: PortfolioListItem?,
        val name: String,
        val isSubmitting: Boolean = false,
        /** String resource id for an inline error inside the dialog, or 0 when none. */
        val errorMessageRes: Int = 0,
    ) : PortfolioDialog() {
        val isRename: Boolean get() = target != null
    }

    /** Yes/no confirmation before [PortfolioRepository.closePortfolio] (E5.5). */
    data class ConfirmDelete(
        val target: PortfolioListItem,
        val isSubmitting: Boolean = false,
    ) : PortfolioDialog()
}

/**
 * UI state for the portfolio list screen. Single immutable snapshot — see [AuthUiState]
 * for the rationale behind the one-StateFlow-per-screen pattern.
 *
 * [errorMessageRes] is the *screen-level* error (e.g. failed list load). Per-dialog errors
 * live inside [PortfolioDialog.NameInput.errorMessageRes].
 */
data class PortfolioListUiState(
    val portfolios: List<PortfolioListItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessageRes: Int = 0,
    val dialog: PortfolioDialog = PortfolioDialog.None,
    val isSigningOut: Boolean = false,
)

/**
 * ViewModel for the portfolio list screen (PR-4, E5.3–E5.5).
 *
 * Mirrors the desktop `MainViewModel.cs`. Responsibilities:
 *   1. Load active portfolios from Supabase and expose them sorted by `normalized_name`.
 *   2. Open create / rename / delete dialogs and handle their submissions.
 *   3. Listen to [SyncCoordinator.events] so the list refreshes on app-start / app-resume
 *      (E1.5 sync model v1 — pull-only).
 *   4. Sign-out (delegates to [AuthRepository]).
 *
 * Errors from the repository are typed [AppError] values; this VM maps each to a Hebrew
 * string resource id. The exact mapping is taken from `desktop/MainViewModel.cs` so users
 * see the same wording on both platforms.
 */
// @HiltViewModel + @Inject: Hilt's generated factory constructs this VM with PortfolioRepository,
// AuthRepository, and SyncCoordinator resolved from the DI graph. PortfolioListScreen retrieves
// the instance via hiltViewModel().
@HiltViewModel
class PortfolioListViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val authRepository: AuthRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioListUiState())
    val state: StateFlow<PortfolioListUiState> = _state.asStateFlow()

    init {
        // Initial fetch on first composition.
        refresh()

        // React to sync events so the list refreshes when the app resumes / starts.
        // PostMutation events are not relevant here (we already reload right after our own
        // mutations) but reloading on them too is harmless and keeps multi-VM coherence
        // simple. The coroutine is bound to viewModelScope, so it dies with the VM.
        viewModelScope.launch {
            syncCoordinator.events.collect { event ->
                when (event) {
                    is SyncTrigger.AppStart,
                    is SyncTrigger.AppResume -> refresh()
                    is SyncTrigger.PostMutation -> { /* no-op for now */ }
                }
            }
        }
    }

    // ── Public actions ──────────────────────────────────────────────────────────

    /** Manual refresh from a retry button after a load failure. */
    fun onRetry() = refresh()

    fun onCreateClick() {
        _state.update {
            it.copy(dialog = PortfolioDialog.NameInput(target = null, name = ""))
        }
    }

    fun onRenameClick(portfolio: PortfolioListItem) {
        _state.update {
            it.copy(dialog = PortfolioDialog.NameInput(target = portfolio, name = portfolio.name))
        }
    }

    fun onDeleteClick(portfolio: PortfolioListItem) {
        _state.update {
            it.copy(dialog = PortfolioDialog.ConfirmDelete(target = portfolio))
        }
    }

    fun onDialogNameChange(value: String) {
        _state.update { state ->
            val dlg = state.dialog as? PortfolioDialog.NameInput ?: return@update state
            // Clear any prior error as soon as the user edits the name — same UX as desktop.
            state.copy(dialog = dlg.copy(name = value, errorMessageRes = 0))
        }
    }

    fun onDialogDismiss() {
        // Refuse to dismiss while a request is in flight — otherwise we'd leave the user
        // with no feedback if the call eventually fails / succeeds.
        val current = _state.value.dialog
        val busy = when (current) {
            is PortfolioDialog.NameInput   -> current.isSubmitting
            is PortfolioDialog.ConfirmDelete -> current.isSubmitting
            PortfolioDialog.None           -> false
        }
        if (busy) return
        _state.update { it.copy(dialog = PortfolioDialog.None) }
    }

    /** Confirm button on the name-input dialog (create or rename, depending on target). */
    fun onDialogConfirm() {
        val dlg = _state.value.dialog as? PortfolioDialog.NameInput ?: return
        if (dlg.isSubmitting) return
        if (dlg.target == null) {
            submitCreate(dlg.name)
        } else {
            submitRename(dlg.target, dlg.name)
        }
    }

    /** Confirm button on the delete-confirmation dialog. */
    fun onConfirmDelete() {
        val dlg = _state.value.dialog as? PortfolioDialog.ConfirmDelete ?: return
        if (dlg.isSubmitting) return
        submitClose(dlg.target)
    }

    /**
     * Sign out — best-effort per E3.4: the
     * SDK clears the local session even on network failure, so [AuthRepository.authState]
     * transitions to `SignedOut` and `AppRoot` swaps back to the auth screen automatically.
     */
    fun onSignOut() {
        if (_state.value.isSigningOut) return
        _state.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } finally {
                _state.update { it.copy(isSigningOut = false) }
            }
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessageRes = 0) }

        viewModelScope.launch {
            when (val result = portfolioRepository.getActivePortfolios()) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            portfolios = result.value,
                            isLoading = false,
                            errorMessageRes = 0,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessageRes = R.string.portfolio_list_error_load,
                        )
                    }
                }
            }
        }
    }

    private fun submitCreate(name: String) {
        markDialogSubmitting(true)
        viewModelScope.launch {
            when (val result = portfolioRepository.createPortfolio(name)) {
                is AppResult.Success -> {
                    _state.update { it.copy(dialog = PortfolioDialog.None) }
                    refresh()
                }
                is AppResult.Failure -> {
                    setDialogError(createErrorRes(result.error))
                }
            }
        }
    }

    private fun submitRename(target: PortfolioListItem, newName: String) {
        markDialogSubmitting(true)
        viewModelScope.launch {
            when (val result = portfolioRepository.renamePortfolio(target.portfolioId, newName)) {
                is AppResult.Success -> {
                    _state.update { it.copy(dialog = PortfolioDialog.None) }
                    refresh()
                }
                is AppResult.Failure -> {
                    setDialogError(renameErrorRes(result.error))
                }
            }
        }
    }

    private fun submitClose(target: PortfolioListItem) {
        markDeleteSubmitting(true)
        viewModelScope.launch {
            when (val result = portfolioRepository.closePortfolio(target.portfolioId)) {
                is AppResult.Success -> {
                    _state.update { it.copy(dialog = PortfolioDialog.None) }
                    refresh()
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(
                            dialog = PortfolioDialog.None,
                            errorMessageRes = deleteErrorRes(result.error),
                        )
                    }
                }
            }
        }
    }

    private fun markDialogSubmitting(submitting: Boolean) {
        _state.update { state ->
            val dlg = state.dialog as? PortfolioDialog.NameInput ?: return@update state
            state.copy(dialog = dlg.copy(isSubmitting = submitting, errorMessageRes = 0))
        }
    }

    private fun markDeleteSubmitting(submitting: Boolean) {
        _state.update { state ->
            val dlg = state.dialog as? PortfolioDialog.ConfirmDelete ?: return@update state
            state.copy(dialog = dlg.copy(isSubmitting = submitting))
        }
    }

    private fun setDialogError(messageRes: Int) {
        _state.update { state ->
            val dlg = state.dialog as? PortfolioDialog.NameInput ?: return@update state
            state.copy(dialog = dlg.copy(isSubmitting = false, errorMessageRes = messageRes))
        }
    }

    // ── Error → string-resource mapping (matches desktop MainViewModel.cs wording) ──

    private fun createErrorRes(error: AppError): Int = when (error) {
        AppError.Validation.EmptyName      -> R.string.portfolio_error_empty_name
        AppError.Validation.DuplicateName  -> R.string.portfolio_error_duplicate_name
        else                               -> R.string.portfolio_error_create_generic
    }

    private fun renameErrorRes(error: AppError): Int = when (error) {
        AppError.Validation.EmptyName        -> R.string.portfolio_error_empty_name
        AppError.Validation.DuplicateName    -> R.string.portfolio_error_duplicate_name
        AppError.Validation.PortfolioClosed  -> R.string.portfolio_error_closed
        is AppError.NotFound                 -> R.string.portfolio_error_not_found
        else                                 -> R.string.portfolio_error_rename_generic
    }

    private fun deleteErrorRes(error: AppError): Int = when (error) {
        AppError.Validation.PortfolioClosed  -> R.string.portfolio_error_already_closed
        is AppError.NotFound                 -> R.string.portfolio_error_not_found
        else                                 -> R.string.portfolio_error_delete_generic
    }
}
