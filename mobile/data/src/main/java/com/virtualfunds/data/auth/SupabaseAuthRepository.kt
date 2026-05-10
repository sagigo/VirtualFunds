package com.virtualfunds.data.auth

import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.auth.AuthRepository
import com.virtualfunds.domain.auth.AuthState
import com.virtualfunds.domain.auth.AuthValidator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed implementation of [AuthRepository] (E3.4, E3.6).
 *
 * Mirrors the desktop's `SupabaseAuthService` (`desktop/VirtualFunds.Core/Supabase/SupabaseAuthService.cs`)
 * with two cross-platform shaping differences:
 *
 *   1. **State derived from the SDK's flow.** supabase-kt exposes `auth.sessionStatus` as a
 *      `StateFlow<SessionStatus>` that reflects the current session in real time (including
 *      asynchronous restoration on app start). We just `.map` it to our [AuthState] sealed type.
 *      No manual `RestoreSessionAsync` method is needed — the SDK does it via [SecureSessionManager].
 *
 *   2. **Errors as values, not exceptions** — per mobile/CLAUDE.md §Error Handling, we return
 *      [AppResult.Failure] instead of throwing. Every call site is forced to handle both branches.
 *
 * @Inject constructor: Hilt instantiates this class and provides the [SupabaseClient] from
 * `SupabaseModule`. @Singleton ensures one instance for the app's lifetime, so the [authState]
 * flow is shared across all observers (e.g. the AuthViewModel and the top-level AppViewModel).
 */
// @Singleton tells Hilt to construct exactly one instance and reuse it everywhere. This is
// essential here because [authState] is a hot StateFlow tied to [repoScope] — multiple instances
// would mean multiple flows fanning out from the same SDK, wasting work and possibly diverging.
@Singleton
// @Inject on the constructor tells Hilt to satisfy the [SupabaseClient] parameter from the DI
// graph (provided by `SupabaseModule.provideSupabaseClient`). We never instantiate this class
// ourselves — Hilt's generated factory does.
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient
) : AuthRepository {

    /**
     * Long-lived scope for sharing the [authState] flow. We use [SupervisorJob] so a single
     * downstream collection failure does not cancel the whole flow. [Dispatchers.Default] is
     * fine because we only do simple state mapping here — no Android UI work.
     */
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Maps supabase-kt's [SessionStatus] to our [AuthState]:
     *   - [SessionStatus.Initializing]                → [AuthState.Unknown] (still loading from storage)
     *   - [SessionStatus.Authenticated]               → [AuthState.SignedIn] with the user id
     *   - [SessionStatus.NotAuthenticated]            → [AuthState.SignedOut]
     *   - [SessionStatus.RefreshFailure]              → [AuthState.SignedOut] (refresh token expired/invalid)
     *
     * `stateIn` converts the cold mapping flow into a hot [StateFlow] with an initial value of
     * [AuthState.Unknown]. `SharingStarted.Eagerly` means the flow starts collecting from the SDK
     * immediately when this repo is constructed — so by the time any UI observes `authState`,
     * the SDK has already begun the auto-restore sequence.
     */
    override val authState: StateFlow<AuthState> = client.auth.sessionStatus
        .map { status -> mapStatus(status) }
        .stateIn(repoScope, SharingStarted.Eagerly, AuthState.Unknown)

    override suspend fun signIn(email: String, password: String): AppResult<AuthState> {
        AuthValidator.validateSignIn(email, password).also { v ->
            if (v is AppResult.Failure) return v
        }

        return try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            // signInWith updates auth.sessionStatus on success → our authState flow emits
            // SignedIn. Return the freshly-emitted state so callers don't need to re-read it.
            AppResult.Success(authState.value)
        } catch (e: RestException) {
            // 400/401/422 from GoTrue — treat as wrong credentials. Same UX bucket as the
            // desktop's `AuthenticationFailedException` in SupabaseAuthService.SignInAsync.
            AppResult.Failure(AppError.Auth.InvalidCredentials)
        } catch (e: HttpRequestException) {
            AppResult.Failure(AppError.Network(e.message ?: "Network error"))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: e::class.simpleName ?: "Unknown error"))
        }
    }

    override suspend fun signUp(email: String, password: String): AppResult<AuthState> {
        AuthValidator.validateSignUp(email, password).also { v ->
            if (v is AppResult.Failure) return v
        }

        return try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            // E3.2 disables email confirmation, so signUpWith returns with an Authenticated
            // session immediately. authState has already emitted SignedIn by the time we return.
            AppResult.Success(authState.value)
        } catch (e: RestException) {
            // Email already registered, weak password rejected by server, etc. — same UX bucket
            // as the desktop's `RegistrationFailedException` in SupabaseAuthService.SignUpAsync.
            AppResult.Failure(AppError.Auth.RegistrationFailed)
        } catch (e: HttpRequestException) {
            AppResult.Failure(AppError.Network(e.message ?: "Network error"))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: e::class.simpleName ?: "Unknown error"))
        }
    }

    override suspend fun signOut(): AppResult<Unit> {
        // Best-effort: even if the server call fails, the SDK clears the local session via
        // SessionManager.deleteSession(), and authState transitions to SignedOut. Matches the
        // desktop's "always clear locally" behaviour in SupabaseAuthService.SignOutAsync.
        return try {
            client.auth.signOut()
            AppResult.Success(Unit)
        } catch (e: HttpRequestException) {
            AppResult.Failure(AppError.Network(e.message ?: "Network error"))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: e::class.simpleName ?: "Unknown error"))
        }
    }

    private fun mapStatus(status: SessionStatus): AuthState = when (status) {
        is SessionStatus.Initializing -> AuthState.Unknown
        is SessionStatus.Authenticated -> {
            val userId = status.session.user?.id
            if (userId.isNullOrBlank()) AuthState.SignedOut else AuthState.SignedIn(userId)
        }
        is SessionStatus.NotAuthenticated -> AuthState.SignedOut
        is SessionStatus.RefreshFailure -> AuthState.SignedOut
    }
}
