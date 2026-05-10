package com.virtualfunds.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualfunds.domain.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Placeholder home ViewModel for Phase 1. The only behaviour wired up here is sign-out —
 * everything else (portfolios, funds, transactions, etc.) ships in later phases.
 *
 * On sign-out, the [AuthRepository] clears the session and emits [com.virtualfunds.domain.auth.AuthState.SignedOut].
 * The top-level [com.virtualfunds.ui.AppRoot] reacts to that state change and swaps the UI
 * back to AuthScreen — this ViewModel does not navigate manually.
 */
// @HiltViewModel + @Inject: Hilt builds this ViewModel via its generated factory and resolves
// the AuthRepository parameter from the DI graph. HomeScreen retrieves it via hiltViewModel().
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    fun signOut() {
        if (_isSigningOut.value) return  // re-tap guard
        _isSigningOut.value = true
        viewModelScope.launch {
            try {
                // The AppResult return is intentionally discarded. Per E3.4 + the desktop's
                // SignOutAsync, sign-out is best-effort: even if the network call fails, the
                // SDK clears the local session via SecureSessionManager.deleteSession() and
                // authState transitions to SignedOut. AppRoot then swaps to AuthScreen, so the
                // user always ends up at the right place — there is nothing useful to surface.
                authRepository.signOut()
            } finally {
                _isSigningOut.value = false
            }
        }
    }
}
