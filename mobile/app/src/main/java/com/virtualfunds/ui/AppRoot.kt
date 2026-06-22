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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.virtualfunds.domain.auth.AuthState
import com.virtualfunds.ui.auth.AuthScreen
import com.virtualfunds.ui.fund.FundListScreen
import com.virtualfunds.ui.history.TransactionHistoryScreen
import com.virtualfunds.ui.portfolio.PortfolioListScreen

/**
 * The root of the app's Composable tree. Implements the auth state machine from E3.6:
 *
 *   AuthState.Unknown    → splash (we are still restoring the persisted session)
 *   AuthState.SignedOut  → AuthScreen (sign-in / sign-up)
 *   AuthState.SignedIn   → SignedInNavHost (NavHost rooted at the portfolio list)
 *
 * This is the mobile analogue of the desktop's `AuthWindow` / `MainWindow` / `PortfolioWindow`
 * navigation. Compose recomposes automatically when the auth state changes, so sign-in
 * transitions us to the portfolio list and sign-out returns to AuthScreen with no manual
 * navigation calls. The inner NavHost is only created while signed in — its back stack is
 * naturally discarded on sign-out.
 */
@Composable
fun AppRoot(
    appViewModel: AppViewModel = hiltViewModel()
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        AuthState.Unknown     -> SplashScreen()
        AuthState.SignedOut   -> AuthScreen()
        is AuthState.SignedIn -> SignedInNavHost()
    }
}

/**
 * The Navigation Compose host used while signed in (PR-5 wires the first inner destination).
 *
 * `rememberNavController()` creates the [NavController] tied to this composition. Navigation
 * Compose 2.8+ takes typed routes — `composable<Routes.PortfolioList> { ... }` registers a
 * destination identified by the `Routes.PortfolioList` data object; the route argument
 * `Routes.FundList(...)` is reconstructed via `backStackEntry.toRoute<Routes.FundList>()`.
 */
@Composable
private fun SignedInNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.PortfolioList,
    ) {
        composable<Routes.PortfolioList> {
            PortfolioListScreen(
                onOpenPortfolio = { portfolio ->
                    navController.navigate(
                        Routes.FundList(
                            portfolioId = portfolio.portfolioId,
                            portfolioName = portfolio.name,
                        ),
                    )
                },
            )
        }

        composable<Routes.FundList> { backStackEntry ->
            val route: Routes.FundList = backStackEntry.toRoute()
            FundListScreen(
                portfolioId = route.portfolioId,
                portfolioName = route.portfolioName,
                onBack = { navController.popBackStack() },
                onOpenHistory = {
                    navController.navigate(
                        Routes.TransactionHistory(
                            portfolioId = route.portfolioId,
                            portfolioName = route.portfolioName,
                        ),
                    )
                },
            )
        }

        composable<Routes.TransactionHistory> { backStackEntry ->
            val route: Routes.TransactionHistory = backStackEntry.toRoute()
            TransactionHistoryScreen(
                portfolioId = route.portfolioId,
                portfolioName = route.portfolioName,
                onBack = { navController.popBackStack() },
            )
        }
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
