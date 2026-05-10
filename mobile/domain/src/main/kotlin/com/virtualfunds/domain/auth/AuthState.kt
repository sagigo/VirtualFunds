package com.virtualfunds.domain.auth

/**
 * Discriminated union representing the current authentication state of the application (E3.6).
 *
 * Mirrors the desktop's `AuthState` hierarchy in `desktop/VirtualFunds.Core/Models/AuthState.cs`
 * so the two platforms agree byte-for-byte on the auth state machine.
 *
 * Use a `when` expression to handle each state — the compiler enforces exhaustiveness:
 *   when (state) {
 *       AuthState.Unknown    -> showSplash()
 *       AuthState.SignedOut  -> showLogin()
 *       is AuthState.SignedIn -> showHome(state.userId)
 *   }
 */
sealed class AuthState {
    /**
     * Initial state before the persisted session has been checked.
     * The app has not yet determined whether a valid session exists.
     */
    data object Unknown : AuthState()

    /** No valid session exists. The user must sign in or sign up. */
    data object SignedOut : AuthState()

    /**
     * A valid session exists. The user is authenticated.
     *
     * @param userId The Supabase `auth.uid()` for the current user.
     */
    data class SignedIn(val userId: String) : AuthState()
}
