package com.virtualfunds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualfunds.domain.auth.AuthRepository
import com.virtualfunds.domain.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Top-level ViewModel for the entire app shell. Mirrors the desktop's app-level switch
 * between `AuthWindow` and `MainWindow` — except on mobile the swap happens inside a single
 * Activity by re-rendering the Composable tree based on [AuthState] (E3.6).
 *
 * Why this lives separately from [com.virtualfunds.ui.auth.AuthViewModel]:
 *   - [com.virtualfunds.ui.auth.AuthViewModel] is scoped to the auth screen and exists only
 *     while the user is signed-out and on that screen.
 *   - [AppViewModel] is scoped to the Activity and lives for the entire session, so it can
 *     hold the long-lived `authState` flow without being recreated mid-flow.
 *
 * Re-exposing the repo's [AuthRepository.authState] as a fresh [StateFlow] tied to
 * [viewModelScope] ensures the flow is collected for as long as the Activity needs it,
 * with `SharingStarted.WhileSubscribed(5_000)` keeping it alive for 5s after the last
 * subscriber disappears (so config changes don't drop and re-establish the subscription).
 */
// @HiltViewModel tells Hilt to construct this ViewModel and inject its constructor dependencies.
// Composables get an instance via hiltViewModel() — Hilt's generated factory ties it to the
// nearest ViewModelStoreOwner (the Activity here), so it survives configuration changes but
// is cleared when the Activity is finished. @Inject lets Hilt fulfill the AuthRepository param
// from the DI graph (SupabaseAuthRepository, bound in AuthModule).
@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthState.Unknown
        )
}
