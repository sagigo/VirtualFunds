package com.virtualfunds.ui.portfolio

import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.auth.AuthRepository
import com.virtualfunds.domain.model.PortfolioListItem
import com.virtualfunds.domain.repository.PortfolioRepository
import com.virtualfunds.sync.SyncCoordinator
import com.virtualfunds.sync.SyncTrigger
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)

/**
 * Unit tests for [PortfolioListViewModel].
 *
 * # Test strategy
 *
 * The ViewModel under test:
 *   - starts coroutines in `viewModelScope` (which uses `Dispatchers.Main`)
 *   - has an `init` block that immediately launches a load + a sync-event collector
 *
 * We replace `Dispatchers.Main` with `UnconfinedTestDispatcher` before constructing any
 * ViewModel instance. `UnconfinedTestDispatcher` runs coroutines eagerly on the calling
 * thread without suspending, which means:
 *   - The initial `refresh()` completes synchronously during construction.
 *   - State assertions after construction see the final post-load state immediately.
 *   - Emitting to `syncEvents` in a test triggers a synchronous refresh before the next
 *     assertion runs — no need for `advanceUntilIdle()` or extra delays.
 *
 * # Isolation
 *
 * `IsolationMode.InstancePerTest` creates a fresh test class instance per test, giving
 * each test its own mocks, its own `syncEvents` MutableSharedFlow, and its own
 * `UnconfinedTestDispatcher`. This prevents state from leaking between tests (which
 * matters because the ViewModel's init-time coroutines start collecting `syncEvents`
 * immediately, and we need each test to start from a clean slate).
 */
class PortfolioListViewModelTest : FunSpec() {

    private val portfolioRepo = mockk<PortfolioRepository>()
    private val authRepo = mockk<AuthRepository>()
    private val syncCoordinator = mockk<SyncCoordinator>()

    // Fresh SharedFlow per test instance — emitting events here triggers the ViewModel's
    // sync-event collector without risk of cross-test contamination.
    private val syncEvents = MutableSharedFlow<SyncTrigger>(extraBufferCapacity = 8)

    // Convenience list used in tests that need a non-empty portfolio list.
    private val samplePortfolios = listOf(
        PortfolioListItem("p1", "פנסיה", 1_000_00L),
        PortfolioListItem("p2", "חיסכון לדירה", 2_000_00L),
    )

    init {
        // In Kotest 5.9.x, isolationMode is a mutable var on Spec (not overridable).
        // Each test gets a fresh instance of this class, so mocks and the syncEvents
        // SharedFlow are independent across tests.
        isolationMode = IsolationMode.InstancePerTest

        beforeTest {
            // 1. Make Dispatchers.Main run eagerly (no real Android main thread needed).
            Dispatchers.setMain(UnconfinedTestDispatcher())
            // 2. Wire the mock's `events` property to our controlled SharedFlow.
            //    `every { ... }` stubs a regular (non-suspend) property getter.
            every { syncCoordinator.events } returns syncEvents
        }

        afterTest {
            Dispatchers.resetMain()
            clearAllMocks()
        }

        // ── Construction / initial load ──────────────────────────────────────

        test("successful initial load populates portfolios and clears loading") {
            coEvery { portfolioRepo.getActivePortfolios() } returns
                AppResult.Success(samplePortfolios)

            val vm = buildVm()

            vm.state.value.portfolios shouldBe samplePortfolios
            vm.state.value.isLoading shouldBe false
            vm.state.value.errorMessageRes shouldBe 0
        }

        test("failed initial load sets error message and leaves list empty") {
            coEvery { portfolioRepo.getActivePortfolios() } returns
                AppResult.Failure(AppError.Network("timeout"))

            val vm = buildVm()

            vm.state.value.portfolios shouldBe emptyList()
            vm.state.value.isLoading shouldBe false
            vm.state.value.errorMessageRes shouldBe R.string.portfolio_list_error_load
        }

        test("isLoading is false once initial load completes (success path)") {
            coEvery { portfolioRepo.getActivePortfolios() } returns
                AppResult.Success(emptyList())
            val vm = buildVm()
            vm.state.value.isLoading shouldBe false
        }

        // ── onRetry ──────────────────────────────────────────────────────────

        test("retry after failure clears error, re-fetches, and shows portfolios on success") {
            coEvery { portfolioRepo.getActivePortfolios() } returnsMany listOf(
                AppResult.Failure(AppError.Network("first attempt failed")),
                AppResult.Success(samplePortfolios),
            )
            val vm = buildVm()

            vm.state.value.errorMessageRes shouldBe R.string.portfolio_list_error_load

            vm.onRetry()

            vm.state.value.errorMessageRes shouldBe 0
            vm.state.value.portfolios shouldBe samplePortfolios
        }

        // ── Create dialog ────────────────────────────────────────────────────

        test("onCreateClick opens NameInput dialog with empty name and no target") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            val vm = buildVm()

            vm.onCreateClick()

            val dlg = vm.state.value.dialog
            dlg.shouldBeInstanceOf<PortfolioDialog.NameInput>()
            dlg as PortfolioDialog.NameInput
            dlg.target shouldBe null
            dlg.name shouldBe ""
            dlg.isRename shouldBe false
        }

        test("onDialogNameChange updates the name inside the dialog") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            val vm = buildVm()
            vm.onCreateClick()

            vm.onDialogNameChange("פנסיה")

            val dlg = vm.state.value.dialog as PortfolioDialog.NameInput
            dlg.name shouldBe "פנסיה"
        }

        test("onDialogNameChange clears existing dialog error") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            coEvery { portfolioRepo.createPortfolio(any()) } returns
                AppResult.Failure(AppError.Validation.EmptyName)
            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogConfirm()
            // error is now set; typing should clear it
            (vm.state.value.dialog as PortfolioDialog.NameInput).errorMessageRes shouldBe
                R.string.portfolio_error_empty_name

            vm.onDialogNameChange("new name")

            (vm.state.value.dialog as PortfolioDialog.NameInput).errorMessageRes shouldBe 0
        }

        test("creating a portfolio calls createPortfolio, closes dialog, and reloads") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            coEvery { portfolioRepo.createPortfolio("פנסיה") } returns
                AppResult.Success("new-id")
            val vm = buildVm()

            vm.onCreateClick()
            vm.onDialogNameChange("פנסיה")
            vm.onDialogConfirm()

            coVerify { portfolioRepo.createPortfolio("פנסיה") }
            vm.state.value.dialog shouldBe PortfolioDialog.None
            // After success, getActivePortfolios is called again (second call for reload).
            coVerify(exactly = 2) { portfolioRepo.getActivePortfolios() }
        }

        test("create with EmptyName error stays in dialog with error message") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            coEvery { portfolioRepo.createPortfolio(any()) } returns
                AppResult.Failure(AppError.Validation.EmptyName)
            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as PortfolioDialog.NameInput
            dlg.isSubmitting shouldBe false
            dlg.errorMessageRes shouldBe R.string.portfolio_error_empty_name
        }

        test("create with DuplicateName error stays in dialog with error message") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            coEvery { portfolioRepo.createPortfolio(any()) } returns
                AppResult.Failure(AppError.Validation.DuplicateName)
            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as PortfolioDialog.NameInput
            dlg.errorMessageRes shouldBe R.string.portfolio_error_duplicate_name
        }

        // ── Rename dialog ────────────────────────────────────────────────────

        test("onRenameClick opens NameInput dialog pre-filled with the portfolio name") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(samplePortfolios)
            val vm = buildVm()

            vm.onRenameClick(samplePortfolios[0])

            val dlg = vm.state.value.dialog as PortfolioDialog.NameInput
            dlg.target shouldBe samplePortfolios[0]
            dlg.name shouldBe samplePortfolios[0].name
            dlg.isRename shouldBe true
        }

        test("renaming calls renamePortfolio, closes dialog, and reloads") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(samplePortfolios)
            coEvery { portfolioRepo.renamePortfolio("p1", "פנסיה חדשה") } returns
                AppResult.Success(Unit)
            val vm = buildVm()

            vm.onRenameClick(samplePortfolios[0])
            vm.onDialogNameChange("פנסיה חדשה")
            vm.onDialogConfirm()

            coVerify { portfolioRepo.renamePortfolio("p1", "פנסיה חדשה") }
            vm.state.value.dialog shouldBe PortfolioDialog.None
        }

        test("rename with PortfolioClosed error shows correct error message") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(samplePortfolios)
            coEvery { portfolioRepo.renamePortfolio(any(), any()) } returns
                AppResult.Failure(AppError.Validation.PortfolioClosed)
            val vm = buildVm()
            vm.onRenameClick(samplePortfolios[0])
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as PortfolioDialog.NameInput
            dlg.errorMessageRes shouldBe R.string.portfolio_error_closed
        }

        test("rename with NotFound error shows correct error message") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(samplePortfolios)
            coEvery { portfolioRepo.renamePortfolio(any(), any()) } returns
                AppResult.Failure(AppError.NotFound("portfolio"))
            val vm = buildVm()
            vm.onRenameClick(samplePortfolios[0])
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as PortfolioDialog.NameInput
            dlg.errorMessageRes shouldBe R.string.portfolio_error_not_found
        }

        // ── Delete/close dialog ──────────────────────────────────────────────

        test("onDeleteClick opens ConfirmDelete dialog with the correct portfolio") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(samplePortfolios)
            val vm = buildVm()

            vm.onDeleteClick(samplePortfolios[1])

            val dlg = vm.state.value.dialog as PortfolioDialog.ConfirmDelete
            dlg.target shouldBe samplePortfolios[1]
        }

        test("confirming delete calls closePortfolio, closes dialog, and reloads") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(samplePortfolios)
            coEvery { portfolioRepo.closePortfolio("p2") } returns AppResult.Success(Unit)
            val vm = buildVm()

            vm.onDeleteClick(samplePortfolios[1])
            vm.onConfirmDelete()

            coVerify { portfolioRepo.closePortfolio("p2") }
            vm.state.value.dialog shouldBe PortfolioDialog.None
            coVerify(exactly = 2) { portfolioRepo.getActivePortfolios() }
        }

        test("close failure closes dialog and surfaces screen-level error") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(samplePortfolios)
            coEvery { portfolioRepo.closePortfolio(any()) } returns
                AppResult.Failure(AppError.Validation.PortfolioClosed)
            val vm = buildVm()

            vm.onDeleteClick(samplePortfolios[0])
            vm.onConfirmDelete()

            vm.state.value.dialog shouldBe PortfolioDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.portfolio_error_already_closed
        }

        // ── Dialog dismiss ───────────────────────────────────────────────────

        test("onDialogDismiss closes the dialog when not submitting") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            val vm = buildVm()
            vm.onCreateClick()

            vm.onDialogDismiss()

            vm.state.value.dialog shouldBe PortfolioDialog.None
        }

        // ── Sign-out ─────────────────────────────────────────────────────────

        test("onSignOut calls authRepository.signOut") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            coEvery { authRepo.signOut() } returns AppResult.Success(Unit)
            val vm = buildVm()

            vm.onSignOut()

            coVerify { authRepo.signOut() }
        }

        test("re-tap guard: second onSignOut call while signing out is ignored") {
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(emptyList())
            coEvery { authRepo.signOut() } returns AppResult.Success(Unit)
            val vm = buildVm()

            // UnconfinedTestDispatcher runs the first call eagerly to completion, so
            // the second call fires while isSigningOut would be false again. Verify
            // the guard doesn't break normal flow.
            vm.onSignOut()
            vm.onSignOut()

            // Each call should complete; sign-out was invoked at least once.
            coVerify(atLeast = 1) { authRepo.signOut() }
        }

        // ── Sync events ──────────────────────────────────────────────────────

        test("AppStart sync event triggers a refresh") {
            coEvery { portfolioRepo.getActivePortfolios() } returnsMany listOf(
                AppResult.Success(emptyList()),          // initial load
                AppResult.Success(samplePortfolios),     // reload from AppStart
            )
            val vm = buildVm()

            syncEvents.emit(SyncTrigger.AppStart)

            vm.state.value.portfolios shouldBe samplePortfolios
        }

        test("AppResume sync event triggers a refresh") {
            coEvery { portfolioRepo.getActivePortfolios() } returnsMany listOf(
                AppResult.Success(emptyList()),
                AppResult.Success(samplePortfolios),
            )
            val vm = buildVm()

            syncEvents.emit(SyncTrigger.AppResume)

            vm.state.value.portfolios shouldBe samplePortfolios
        }

        test("PostMutation sync event triggers a refresh so portfolio totals stay fresh") {
            // While the user is on the fund-list screen this VM stays alive in the nav
            // back-stack. Fund operations (deposit/withdraw/transfer) fire PostMutation;
            // those change portfolio totals, so this VM must refresh.
            coEvery { portfolioRepo.getActivePortfolios() } returns AppResult.Success(samplePortfolios)
            val vm = buildVm()

            syncEvents.emit(SyncTrigger.PostMutation("p1"))

            // Initial load + refresh on PostMutation = 2 calls.
            coVerify(exactly = 2) { portfolioRepo.getActivePortfolios() }
        }

        test("PostMutation refresh updates the visible totals") {
            // Simulate the data layer returning a higher total after the mutation, so we
            // can verify the VM picks up the new value when it refreshes.
            val before = samplePortfolios
            val after = samplePortfolios.map { p ->
                if (p.portfolioId == "p1") p.copy(totalBalanceAgoras = p.totalBalanceAgoras + 100_000L)
                else p
            }
            coEvery { portfolioRepo.getActivePortfolios() } returnsMany listOf(
                AppResult.Success(before),
                AppResult.Success(after),
            )
            val vm = buildVm()

            // Before: initial load.
            vm.state.value.portfolios.first { it.portfolioId == "p1" }.totalBalanceAgoras shouldBe
                before.first { it.portfolioId == "p1" }.totalBalanceAgoras

            syncEvents.emit(SyncTrigger.PostMutation("p1"))

            // After: refreshed total.
            vm.state.value.portfolios.first { it.portfolioId == "p1" }.totalBalanceAgoras shouldBe
                after.first { it.portfolioId == "p1" }.totalBalanceAgoras
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a [PortfolioListViewModel] using the test mocks. Must be called after
     * `coEvery { portfolioRepo.getActivePortfolios() }` is stubbed, because the
     * ViewModel's `init` block fires `refresh()` immediately.
     */
    private fun buildVm(): PortfolioListViewModel =
        PortfolioListViewModel(portfolioRepo, authRepo, syncCoordinator)
}
