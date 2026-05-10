package com.virtualfunds.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.virtualfunds.R

/**
 * Phase 1 placeholder home screen. Shows that the user is signed in and exposes a
 * sign-out button so we can verify the full Phase 1 flow end-to-end:
 * sign up / sign in → land here → sign out → back to AuthScreen.
 *
 * The real home (portfolios list, etc.) will replace this in a later phase (PR-2+).
 *
 * @param userId The signed-in user's `auth.uid()`. Displayed for sanity-checking during
 *               development; will be removed once the real home screen lands.
 */
@Composable
fun HomeScreen(
    userId: String,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val isSigningOut by viewModel.isSigningOut.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_placeholder_signed_in),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(8.dp))

            // Dev-only display of the user id. Remove when the real home screen ships.
            Text(
                text = userId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = viewModel::signOut,
                enabled = !isSigningOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.auth_button_sign_out))
            }
        }
    }
}
