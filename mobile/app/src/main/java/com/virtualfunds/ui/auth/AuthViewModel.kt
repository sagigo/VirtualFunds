package com.virtualfunds.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the auth screen — the View renders solely from this immutable snapshot.
 *
 * Combining all fields into a single `data class` exposed as a single `StateFlow` is the
 * idiomatic Compose pattern. It guarantees the UI sees a consistent snapshot (you cannot
 * recompose halfway through a multi-field update) and makes ViewModel tests easier — you
 * assert on one object, not five flows.
 *
 * `errorMessageRes` is a string resource id (Int) rather than a resolved Hebrew string,
 * so the ViewModel stays free of Android Context references and remains unit-testable.
 * The Composable resolves it via [androidx.compose.ui.res.stringResource].
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    /** String resource id for the current error, or 0 when there is no error. */
    val errorMessageRes: Int = 0
)

/**
 * ViewModel for the auth screen. Mirrors the desktop's `AuthViewModel`
 * (`desktop/VirtualFunds.WPF/ViewModels/AuthViewModel.cs`).
 *
 * @HiltViewModel tells Hilt to construct this ViewModel and inject its constructor
 * dependencies. Composables obtain it via `hiltViewModel()` which pulls the instance
 * from Hilt's generated factory — we never call `new` ourselves.
 *
 * After successful sign-in/up, the SDK updates `auth.sessionStatus`, the [AuthRepository]
 * remaps to [com.virtualfunds.domain.auth.AuthState.SignedIn], and the **top-level**
 * `AppViewModel` swaps the UI from `AuthScreen` to the portfolio list. This ViewModel does NOT
 * navigate — it just reports success/failure via [AuthUiState].
 */
// @HiltViewModel + @Inject: Hilt generates a ViewModel factory that knows how to construct
// this class with its AuthRepository dependency resolved from the DI graph. AuthScreen calls
// hiltViewModel() to fetch the instance — the factory is invoked the first time, then the
// same instance is returned for the lifetime of the enclosing ViewModelStoreOwner.
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, errorMessageRes = 0) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, errorMessageRes = 0) }
    }

    fun toggleMode() {
        _state.update { it.copy(isSignUpMode = !it.isSignUpMode, errorMessageRes = 0) }
    }

    /**
     * Submits the form — sign in or sign up depending on [AuthUiState.isSignUpMode].
     * Validation runs inside the repository (calls [com.virtualfunds.domain.auth.AuthValidator]),
     * so we just inspect the result and map errors to the correct Hebrew string resource.
     */
    fun submit() {
        val snapshot = _state.value
        if (snapshot.isLoading) return  // re-tap guard

        _state.update { it.copy(isLoading = true, errorMessageRes = 0) }

        // viewModelScope is the standard scope provided by Jetpack ViewModel — it is cancelled
        // automatically when the ViewModel is cleared, so this coroutine cannot leak.
        viewModelScope.launch {
            val result = if (snapshot.isSignUpMode) {
                authRepository.signUp(snapshot.email, snapshot.password)
            } else {
                authRepository.signIn(snapshot.email, snapshot.password)
            }

            when (result) {
                is AppResult.Success -> {
                    // No screen-level navigation here — the top-level AppViewModel observes
                    // authState and swaps to PortfolioListScreen. We just stop the spinner.
                    _state.update { it.copy(isLoading = false) }
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(isLoading = false, errorMessageRes = errorRes(result.error))
                    }
                }
            }
        }
    }

    /**
     * Maps an [AppError] to the Hebrew string resource that the auth screen should display.
     * Wording matches the desktop's exception → message mapping in AuthViewModel.cs.
     */
    private fun errorRes(error: AppError): Int = when (error) {
        AppError.Auth.EmptyEmail          -> R.string.auth_error_empty_email
        AppError.Auth.InvalidEmailFormat  -> R.string.auth_error_invalid_email
        AppError.Auth.EmptyPassword       -> R.string.auth_error_empty_password
        AppError.Auth.PasswordTooShort    -> R.string.auth_error_password_too_short
        AppError.Auth.InvalidCredentials  -> R.string.auth_error_invalid_credentials
        AppError.Auth.RegistrationFailed  -> R.string.auth_error_registration_failed
        is AppError.Network               -> R.string.auth_error_network
        else                              -> R.string.auth_error_unknown
    }
}
