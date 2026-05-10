package com.virtualfunds.domain.auth

import com.virtualfunds.domain.AppResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication operations for the Virtual Funds mobile app (E3.4, E3.6).
 *
 * Mirrors the desktop's `IAuthService` (`desktop/VirtualFunds.Core/Services/IAuthService.cs`)
 * with two Kotlin-idiomatic differences:
 *   1. Errors return as [AppResult.Failure] (sealed result type) instead of throwing.
 *   2. The current state is exposed as a [StateFlow] so the UI can react to changes
 *      (e.g. session expiring, sign-out triggered from another screen) without polling.
 *
 * Session restoration is **automatic** on app start — supabase-kt's `Auth` plugin reads
 * the persisted session via the configured `SessionManager` during client initialization.
 * The `authState` flow starts at [AuthState.Unknown] and transitions to [AuthState.SignedIn]
 * or [AuthState.SignedOut] once restoration completes. There is therefore no explicit
 * `restoreSession()` method on the mobile side (the desktop has one because its session
 * file is read manually on app start).
 */
interface AuthRepository {

    /** Observable current auth state. Starts at [AuthState.Unknown] until session restore completes. */
    val authState: StateFlow<AuthState>

    /**
     * Signs in with email and password (E3.4 — Sign in).
     * On success, the session is persisted by the SDK and [authState] transitions to
     * [AuthState.SignedIn]. Returns the new state.
     */
    suspend fun signIn(email: String, password: String): AppResult<AuthState>

    /**
     * Registers a new account and immediately signs in (E3.4 — Sign up).
     * Email confirmation is disabled in our Supabase config (E3.2), so a session is
     * returned synchronously and [authState] transitions to [AuthState.SignedIn].
     */
    suspend fun signUp(email: String, password: String): AppResult<AuthState>

    /**
     * Signs out the current user and clears the persisted session (E3.4 — Sign out).
     * Always best-effort: even if the server call fails, the local session is cleared.
     */
    suspend fun signOut(): AppResult<Unit>
}
