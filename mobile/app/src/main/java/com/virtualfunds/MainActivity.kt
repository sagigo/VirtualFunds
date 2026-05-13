package com.virtualfunds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.virtualfunds.sync.SyncCoordinator
import com.virtualfunds.ui.AppRoot
import com.virtualfunds.ui.theme.VirtualFundsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// @AndroidEntryPoint enables Hilt injection in this Activity.
// It also enables hiltViewModel() in all Composables hosted within this Activity —
// without it, Compose screens could not retrieve @HiltViewModel instances.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // @Inject on a non-private field tells Hilt to populate this after super.onCreate()
    // runs. Hilt knows how to build a SyncCoordinator via its @Inject constructor and the
    // bindings declared in :data and :app DI modules.
    @Inject lateinit var syncCoordinator: SyncCoordinator

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

    // ── Sync lifecycle hooks (E1.5, PR-3) ────────────────────────────────────
    //
    // PR-3 sync model v1:
    //   - app startup       → refresh
    //   - app resume        → refresh
    //   - post-mutation     → refresh (called by ViewModels, not lifecycle)
    //
    // Activity.onStart fires when the Activity becomes visible. Activity.onResume fires
    // every time it becomes interactive — including the first time after onStart and
    // again after returning from another app/dialog. Calling both lets us distinguish
    // a true "cold/warm app start" from a "user returned from background" — but for v1
    // the behaviour is identical: refresh once, debounced via SyncCoordinator's mutex.

    override fun onStart() {
        super.onStart()
        syncCoordinator.triggerAppStart()
    }

    override fun onResume() {
        super.onResume()
        syncCoordinator.triggerAppResume()
    }
}
