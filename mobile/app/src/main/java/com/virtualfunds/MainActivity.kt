package com.virtualfunds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.virtualfunds.ui.AppRoot
import com.virtualfunds.ui.theme.VirtualFundsTheme
import dagger.hilt.android.AndroidEntryPoint

// @AndroidEntryPoint enables Hilt injection in this Activity.
// It also enables hiltViewModel() in all Composables hosted within this Activity —
// without it, Compose screens could not retrieve @HiltViewModel instances.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VirtualFundsTheme {
                // AppRoot owns the auth state machine (E3.6) and renders the right screen
                // based on AuthState (Unknown → splash, SignedOut → AuthScreen, SignedIn → Home).
                AppRoot()
            }
        }
    }
}
