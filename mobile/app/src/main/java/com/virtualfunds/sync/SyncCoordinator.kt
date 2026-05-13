package com.virtualfunds.sync

import com.virtualfunds.domain.auth.AuthRepository
import com.virtualfunds.domain.auth.AuthState
import com.virtualfunds.domain.repository.DeviceIdStore
import com.virtualfunds.domain.repository.ScheduledDepositRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discriminates the reason a refresh was triggered. Consumers (ViewModels in later phases)
 * collect [SyncCoordinator.events] and decide what to reload based on the trigger.
 */
sealed class SyncTrigger {
    /** App just launched (or session just restored). Refresh portfolio list + any selected portfolio (E1.5). */
    data object AppStart : SyncTrigger()

    /** App came back to the foreground. Same scope as AppStart but cheaper rate-limiting may apply (E1.5). */
    data object AppResume : SyncTrigger()

    /** A mutation succeeded — refresh affected data (E1.5). [portfolioId] is the portfolio that changed. */
    data class PostMutation(val portfolioId: String) : SyncTrigger()
}

/**
 * Centralised sync coordinator (E1.5, PR-3).
 *
 * Mobile sync v1 is purely client-driven: refresh on app start, app resume, and after each
 * successful mutation. There is no realtime subscription (E1.5 explicitly defers that).
 *
 * **What this class does** in Phase 2:
 *   1. Exposes a [SharedFlow] of [SyncTrigger] events. ViewModels (Phase 3+) will collect
 *      this and refresh their own state.
 *   2. Triggers scheduled-deposit execution on app start / resume (E8.4: "trigger on login
 *      / on app start / on app resume") — but only once a portfolio context has been set
 *      via [setActivePortfolioId], since `rpc_execute_due_scheduled_deposits` is per-portfolio.
 *
 * **What it deliberately does NOT do** in Phase 2:
 *   - Hold portfolio/fund/transaction state. That belongs to ViewModels which observe the
 *     sync events and re-read repositories.
 *   - Implement realtime push. Per E1.5, deferred to a future version.
 *
 * Concurrency: a [Mutex] serialises trigger handling so two near-simultaneous triggers
 * (e.g. app resume firing twice) don't race. The scheduled-deposit RPC itself is also
 * exactly-once at the database level (E8.5), so duplicates are harmless — the mutex just
 * avoids wasted round-trips.
 */
// @Singleton ensures one coordinator per process: the SharedFlow's replay buffer and
// the Mutex would not work correctly across multiple instances.
@Singleton
class SyncCoordinator
    // @Inject on the constructor lets Hilt's generated factory provide all three
    // dependencies — AuthRepository and ScheduledDepositRepository are bound in
    // :data (RepositoryModule, AuthModule); DeviceIdStore is bound in RepositoryModule.
    @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceIdStore: DeviceIdStore,
    private val scheduledDepositRepository: ScheduledDepositRepository,
) {

    /**
     * Application-scoped coroutine scope for fire-and-forget work like calling
     * `rpc_execute_due_scheduled_deposits`. [SupervisorJob] keeps siblings alive if one
     * fails. [Dispatchers.Default] is fine — the repository methods are already suspend
     * functions that hop to IO via Ktor.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val triggerMutex = Mutex()

    /**
     * Active portfolio set by the UI when the user enters/leaves a portfolio screen.
     * `null` means "no portfolio in view"; in that case start/resume triggers only emit
     * the [SyncTrigger.AppStart]/[SyncTrigger.AppResume] event for the portfolio list and
     * skip the scheduled-deposit RPC (it has no portfolio_id to target).
     *
     * @Volatile so a writer thread's update is immediately visible to the trigger reader.
     */
    @Volatile
    private var activePortfolioId: String? = null

    private val _events = MutableSharedFlow<SyncTrigger>(
        replay = 0,
        extraBufferCapacity = 8, // enough to absorb a burst without dropping
    )

    /** Hot flow of sync events. Collectors typically live in ViewModels' `viewModelScope`. */
    val events: SharedFlow<SyncTrigger> = _events.asSharedFlow()

    /**
     * UI calls this when the user navigates into a portfolio (Phase 3+) and again with `null`
     * when they back out. Drives whether scheduled deposits execute on the next app-resume.
     */
    fun setActivePortfolioId(portfolioId: String?) {
        activePortfolioId = portfolioId
    }

    /** Called from [com.virtualfunds.MainActivity.onStart] (lifecycle hook). */
    fun triggerAppStart() = trigger(SyncTrigger.AppStart)

    /** Called from [com.virtualfunds.MainActivity.onResume] (lifecycle hook). */
    fun triggerAppResume() = trigger(SyncTrigger.AppResume)

    /** Called from any ViewModel after a successful mutation finishes. */
    fun triggerPostMutation(portfolioId: String) = trigger(SyncTrigger.PostMutation(portfolioId))

    /**
     * Common path:
     *   1. Broadcast the trigger event for any subscribed ViewModels to pick up.
     *   2. If the user is signed in *and* a portfolio is active, kick off the scheduled-
     *      deposit RPC. Failures are deliberately swallowed — sync is best-effort and we
     *      don't want a transient network blip to surface as a toast on every app resume.
     */
    private fun trigger(t: SyncTrigger) {
        scope.launch {
            triggerMutex.withLock {
                _events.emit(t)

                if (!isSignedIn()) return@withLock

                val portfolioId = when (t) {
                    is SyncTrigger.AppStart, is SyncTrigger.AppResume -> activePortfolioId
                    is SyncTrigger.PostMutation -> t.portfolioId
                }
                if (portfolioId == null) return@withLock

                val deviceId = deviceIdStore.getOrCreate()
                // Result is intentionally discarded — see method doc.
                scheduledDepositRepository.executeDueDeposits(portfolioId, deviceId)
            }
        }
    }

    private fun isSignedIn(): Boolean = authRepository.authState.value is AuthState.SignedIn
}
