package com.virtualfunds.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.virtualfunds.domain.auth.AuthState
import com.virtualfunds.ui.auth.AuthScreen
import com.virtualfunds.ui.home.HomeScreen

/**
 * The root of the app's Composable tree. Implements the auth state machine from E3.6:
 *
 *   AuthState.Unknown    → splash (we are still restoring the persisted session)
 *   AuthState.SignedOut  → AuthScreen (sign-in / sign-up)
 *   AuthState.SignedIn   → HomeScreen (placeholder for now — real home arrives in later phases)
 *
 * This is the mobile analogue of the desktop's `AuthWindow` / `MainWindow` switch — but
 * implemented as a `when` expression over a single source of truth ([AppViewModel.authState])
 * instead of as separate Window classes. Compose recomposes automatically when the state
 * changes, so sign-in transitions us to HomeScreen and sign-out transitions us back to
 * AuthScreen with no manual navigation.
 */
@Composable
fun AppRoot(
    appViewModel: AppViewModel = hiltViewModel()
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthState.Unknown   -> SplashScreen()
        AuthState.SignedOut -> AuthScreen()
        is AuthState.SignedIn -> HomeScreen(userId = state.userId)
    }
}

/**
 * Minimal splash shown while the session manager loads tokens from EncryptedSharedPreferences.
 * On a warm install with a valid session this is visible only for a fraction of a second.
 */
@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
