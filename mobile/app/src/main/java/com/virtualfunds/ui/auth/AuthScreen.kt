package com.virtualfunds.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.virtualfunds.R
import com.virtualfunds.ui.theme.VirtualFundsTheme

/**
 * The auth screen — sign in / sign up with email and password (E3.4).
 *
 * Stateful composable: it pulls the [AuthViewModel] via Hilt's `hiltViewModel()` and forwards
 * state + actions to the stateless [AuthScreenContent]. This split — stateful wrapper around
 * a stateless content function — is the standard Compose pattern: it lets us preview the UI
 * without the DI graph, and unit-test the rendering independently of the ViewModel.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AuthScreenContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onToggleMode = viewModel::toggleMode,
        onSubmit = viewModel::submit
    )
}

@Composable
private fun AuthScreenContent(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit
) {
    val titleRes = if (state.isSignUpMode) R.string.auth_title_sign_up else R.string.auth_title_sign_in
    val submitRes = if (state.isSignUpMode) R.string.auth_button_sign_up else R.string.auth_button_sign_in
    val toggleRes = if (state.isSignUpMode) R.string.auth_toggle_to_sign_in else R.string.auth_toggle_to_sign_up

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
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(24.dp))

            // Email + password fields force LTR locally — typed text (especially passwords)
            // can contain Latin chars and symbols whose direction in an RTL field looks wrong.
            // The surrounding screen stays RTL via VirtualFundsTheme. This mirrors the rule
            // from mobile/CLAUDE.md §RTL & Money: "Override to LTR locally where needed".
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.auth_label_email)) },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.auth_label_password)) },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Inline error message (Hebrew). 0 means "no error" — see AuthUiState.errorMessageRes.
            if (state.errorMessageRes != 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(state.errorMessageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(submitRes))
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onToggleMode,
                enabled = !state.isLoading
            ) {
                Text(stringResource(toggleRes))
            }
        }
    }
}

// @Preview renders the composable in Android Studio's design pane without running the app.
// Useful for iterating on layout without a device. The stateless content function is what
// we preview — wiring up a fake state, no Hilt graph required.
@Preview(showBackground = true, locale = "he")
@Composable
private fun AuthScreenSignInPreview() {
    VirtualFundsTheme {
        AuthScreenContent(
            state = AuthUiState(email = "user@example.com", password = "secret"),
            onEmailChange = {}, onPasswordChange = {}, onToggleMode = {}, onSubmit = {}
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun AuthScreenSignUpErrorPreview() {
    VirtualFundsTheme {
        AuthScreenContent(
            state = AuthUiState(
                email = "bad",
                password = "x",
                isSignUpMode = true,
                errorMessageRes = R.string.auth_error_invalid_email
            ),
            onEmailChange = {}, onPasswordChange = {}, onToggleMode = {}, onSubmit = {}
        )
    }
}
