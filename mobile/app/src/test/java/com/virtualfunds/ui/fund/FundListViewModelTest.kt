package com.virtualfunds.ui.fund

import androidx.lifecycle.SavedStateHandle
import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.domain.repository.FundRepository
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
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * Unit tests for [FundListViewModel].
 *
 * # Test strategy
 *
 * Same pattern as [com.virtualfunds.ui.portfolio.PortfolioListViewModelTest]:
 *   - `UnconfinedTestDispatcher` so init-block coroutines run synchronously during
 *     construction.
 *   - `IsolationMode.InstancePerTest` so each test gets fresh mocks + a fresh `syncEvents`
 *     SharedFlow (matters because the VM's init starts collecting it immediately).
 *
 * The VM reads its portfolio id/name out of [SavedStateHandle] via Navigation Compose's
 * `toRoute<Routes.FundList>()` extension. That extension looks up entries by property name,
 * so the test builds a SavedStateHandle pre-populated with `portfolioId` / `portfolioName`.
 */
class FundListViewModelTest : FunSpec() {

    private val fundRepo = mockk<FundRepository>()
    private val syncCoordinator = mockk<SyncCoordinator>()
    private val syncEvents = MutableSharedFlow<SyncTrigger>(extraBufferCapacity = 8)

    private val portfolioId = "p1"
    private val portfolioName = "פנסיה"

    private val fundA = FundListItem(
        fundId = "fA",
        name = "מניות",
        balanceAgoras = 500_00L,
        allocationPercent = 50.0,
        createdAtUtc = Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val fundB = FundListItem(
        fundId = "fB",
        name = "אג״ח",
        balanceAgoras = 300_00L,
        allocationPercent = 30.0,
        createdAtUtc = Instant.parse("2026-02-01T00:00:00Z"),
    )
    private val fundC = FundListItem(
        fundId = "fC",
        name = "מזומן",
        balanceAgoras = 200_00L,
        allocationPercent = 20.0,
        createdAtUtc = Instant.parse("2026-03-01T00:00:00Z"),
    )
    private val sampleFunds = listOf(fundA, fundB, fundC)

    init {
        isolationMode = IsolationMode.InstancePerTest

        beforeTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            every { syncCoordinator.events } returns syncEvents
            justRun { syncCoordinator.setActivePortfolioId(any()) }
            justRun { syncCoordinator.triggerPostMutation(any()) }
        }

        afterTest {
            Dispatchers.resetMain()
            clearAllMocks()
        }

        // ── Construction / initial load ──────────────────────────────────────

        test("successful initial load populates funds, total, clears loading") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)

            val vm = buildVm()

            // Default sort mode is CreatedDate (oldest first) → A, B, C.
            vm.state.value.funds.map { it.fundId } shouldBe listOf("fA", "fB", "fC")
            vm.state.value.totalAgoras shouldBe 1000_00L
            vm.state.value.isLoading shouldBe false
            vm.state.value.errorMessageRes shouldBe 0
            vm.state.value.portfolioName shouldBe portfolioName
        }

        test("failed initial load sets error message and leaves list empty") {
            coEvery { fundRepo.getFunds(portfolioId) } returns
                AppResult.Failure(AppError.Network("boom"))

            val vm = buildVm()

            vm.state.value.funds shouldBe emptyList()
            vm.state.value.errorMessageRes shouldBe R.string.fund_list_error_load
        }

        test("retry after failure clears error and reloads") {
            coEvery { fundRepo.getFunds(portfolioId) } returnsMany listOf(
                AppResult.Failure(AppError.Network("first")),
                AppResult.Success(sampleFunds),
            )
            val vm = buildVm()
            vm.state.value.errorMessageRes shouldBe R.string.fund_list_error_load

            vm.onRetry()

            vm.state.value.errorMessageRes shouldBe 0
            vm.state.value.funds.size shouldBe 3
        }

        test("VM informs the sync coordinator which portfolio is active on init and clears on clear") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())

            val vm = buildVm()
            // `onCleared` is protected — exercising via reflection mirrors what the framework does
            // when the host is destroyed.
            val onCleared = vm.javaClass.superclass.getDeclaredMethod("onCleared")
            onCleared.isAccessible = true
            onCleared.invoke(vm)

            coVerify { syncCoordinator.setActivePortfolioId(portfolioId) }
            coVerify { syncCoordinator.setActivePortfolioId(null) }
        }

        // ── Create dialog ────────────────────────────────────────────────────

        test("onCreateClick opens a CreateInput dialog with empty name and amount") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            val vm = buildVm()

            vm.onCreateClick()

            val dlg = vm.state.value.dialog
            dlg.shouldBeInstanceOf<FundDialog.CreateInput>()
            dlg as FundDialog.CreateInput
            dlg.name shouldBe ""
            dlg.amountText shouldBe ""
        }

        test("create with empty amount → zero agoras, calls createFund and reloads") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.createFund(portfolioId, "מניות", 0L) } returns
                AppResult.Success("new-fund-id")

            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogNameChange("מניות")
            vm.onDialogConfirm()

            coVerify { fundRepo.createFund(portfolioId, "מניות", 0L) }
            vm.state.value.dialog shouldBe FundDialog.None
            coVerify(exactly = 2) { fundRepo.getFunds(portfolioId) }
            coVerify { syncCoordinator.triggerPostMutation(portfolioId) }
        }

        test("create with shekel amount parses to agoras correctly") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.createFund(portfolioId, "מניות", 15050L) } returns
                AppResult.Success("new-fund-id")

            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogNameChange("מניות")
            vm.onDialogAmountChange("150.50")
            vm.onDialogConfirm()

            coVerify { fundRepo.createFund(portfolioId, "מניות", 15050L) }
        }

        test("create with invalid amount stays in dialog with parser error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogNameChange("מניות")
            vm.onDialogAmountChange("abc")
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as FundDialog.CreateInput
            dlg.errorMessageRes shouldBe R.string.fund_error_invalid_number
            coVerify(exactly = 0) { fundRepo.createFund(any(), any(), any()) }
        }

        test("create with too many decimal places stays in dialog with TooManyDecimals error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogAmountChange("1.234")
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as FundDialog.CreateInput
            dlg.errorMessageRes shouldBe R.string.fund_error_too_many_decimals
        }

        test("create with EmptyName from server surfaces empty-name error in dialog") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.createFund(any(), any(), any()) } returns
                AppResult.Failure(AppError.Validation.EmptyName)
            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as FundDialog.CreateInput
            dlg.errorMessageRes shouldBe R.string.fund_error_empty_name
        }

        test("create with DuplicateName from server surfaces duplicate error in dialog") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.createFund(any(), any(), any()) } returns
                AppResult.Failure(AppError.Validation.DuplicateName)
            val vm = buildVm()
            vm.onCreateClick()
            vm.onDialogNameChange("מניות")
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as FundDialog.CreateInput
            dlg.errorMessageRes shouldBe R.string.fund_error_duplicate_name
        }

        // ── Rename dialog ────────────────────────────────────────────────────

        test("onRenameClick opens a RenameInput dialog pre-filled with the fund name") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onRenameClick(fundA)

            val dlg = vm.state.value.dialog as FundDialog.RenameInput
            dlg.target shouldBe fundA
            dlg.name shouldBe fundA.name
        }

        test("renaming a fund calls renameFund, closes the dialog, reloads") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.renameFund(portfolioId, "fA", "שם חדש") } returns
                AppResult.Success(Unit)
            val vm = buildVm()

            vm.onRenameClick(fundA)
            vm.onDialogNameChange("שם חדש")
            vm.onDialogConfirm()

            coVerify { fundRepo.renameFund(portfolioId, "fA", "שם חדש") }
            vm.state.value.dialog shouldBe FundDialog.None
        }

        test("rename with PortfolioClosed surfaces the closed-portfolio error in dialog") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.renameFund(any(), any(), any()) } returns
                AppResult.Failure(AppError.Validation.PortfolioClosed)
            val vm = buildVm()
            vm.onRenameClick(fundA)
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as FundDialog.RenameInput
            dlg.errorMessageRes shouldBe R.string.fund_error_portfolio_closed
        }

        test("rename with NotFound surfaces the not-found error in dialog") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.renameFund(any(), any(), any()) } returns
                AppResult.Failure(AppError.NotFound("fund"))
            val vm = buildVm()
            vm.onRenameClick(fundA)
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog as FundDialog.RenameInput
            dlg.errorMessageRes shouldBe R.string.fund_error_not_found
        }

        // ── Delete dialog ────────────────────────────────────────────────────

        test("onDeleteClick opens ConfirmDelete dialog for the right fund") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onDeleteClick(fundB)

            val dlg = vm.state.value.dialog as FundDialog.ConfirmDelete
            dlg.target shouldBe fundB
        }

        test("confirming delete calls deleteFund, closes the dialog, reloads") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.deleteFund(portfolioId, "fA") } returns AppResult.Success(Unit)
            val vm = buildVm()

            vm.onDeleteClick(fundA)
            vm.onConfirmDelete()

            coVerify { fundRepo.deleteFund(portfolioId, "fA") }
            vm.state.value.dialog shouldBe FundDialog.None
        }

        test("delete with FundNotEmpty closes dialog and surfaces screen-level error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.deleteFund(any(), any()) } returns
                AppResult.Failure(AppError.Validation.FundNotEmpty)
            val vm = buildVm()

            vm.onDeleteClick(fundA)
            vm.onConfirmDelete()

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_fund_not_empty
        }

        test("delete with FundHasEnabledScheduledDeposit surfaces the scheduled-deposit error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.deleteFund(any(), any()) } returns
                AppResult.Failure(AppError.Validation.FundHasEnabledScheduledDeposit)
            val vm = buildVm()

            vm.onDeleteClick(fundA)
            vm.onConfirmDelete()

            vm.state.value.errorMessageRes shouldBe R.string.fund_error_fund_has_scheduled_deposit
        }

        test("delete with PortfolioClosed surfaces the closed-portfolio error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.deleteFund(any(), any()) } returns
                AppResult.Failure(AppError.Validation.PortfolioClosed)
            val vm = buildVm()

            vm.onDeleteClick(fundA)
            vm.onConfirmDelete()

            vm.state.value.errorMessageRes shouldBe R.string.fund_error_portfolio_closed
        }

        test("delete with NotFound surfaces the not-found error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.deleteFund(any(), any()) } returns
                AppResult.Failure(AppError.NotFound("fund"))
            val vm = buildVm()

            vm.onDeleteClick(fundA)
            vm.onConfirmDelete()

            vm.state.value.errorMessageRes shouldBe R.string.fund_error_not_found
        }

        // ── Sort modes (E5.10) ───────────────────────────────────────────────

        test("default sort is CreatedDate (oldest first)") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.state.value.sortMode shouldBe FundSortMode.CreatedDate
            vm.state.value.funds.map { it.fundId } shouldBe listOf("fA", "fB", "fC")
        }

        test("Name sort is alphabetical (case-insensitive)") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onSortModeChange(FundSortMode.Name)

            // Hebrew alphabetical order: א < מ → אג״ח, מזומן, מניות
            vm.state.value.funds.map { it.name } shouldBe listOf("אג״ח", "מזומן", "מניות")
        }

        test("Balance sort is descending") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onSortModeChange(FundSortMode.Balance)

            vm.state.value.funds.map { it.fundId } shouldBe listOf("fA", "fB", "fC")
            vm.state.value.funds.map { it.balanceAgoras } shouldBe listOf(500_00L, 300_00L, 200_00L)
        }

        test("AllocationPercent sort is descending") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onSortModeChange(FundSortMode.AllocationPercent)

            vm.state.value.funds.map { it.allocationPercent } shouldBe listOf(50.0, 30.0, 20.0)
        }

        test("Custom mode initial order seeds from the current display order") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            // Starting from Name sort gives a non-creation order. Switching to Custom must
            // seed the order from what's currently visible — not snap back to creation date.
            vm.onSortModeChange(FundSortMode.Name)
            val nameOrder = vm.state.value.funds.map { it.fundId }
            vm.onSortModeChange(FundSortMode.Custom)
            vm.state.value.funds.map { it.fundId } shouldBe nameOrder
        }

        test("Custom mode move-up swaps the selected fund with its predecessor") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()
            vm.onSortModeChange(FundSortMode.Custom)

            vm.onSelectFund("fB")
            vm.onMoveSelectedUp()

            // B moved up: B, A, C
            vm.state.value.funds.map { it.fundId } shouldBe listOf("fB", "fA", "fC")
        }

        test("Custom mode move-down swaps the selected fund with its successor") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()
            vm.onSortModeChange(FundSortMode.Custom)

            vm.onSelectFund("fA")
            vm.onMoveSelectedDown()

            vm.state.value.funds.map { it.fundId } shouldBe listOf("fB", "fA", "fC")
        }

        test("move-up/down is a no-op outside Custom mode") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()
            // Sort mode is CreatedDate; selection has no meaning here.
            vm.onSelectFund("fB")
            vm.onMoveSelectedUp()

            // No change.
            vm.state.value.funds.map { it.fundId } shouldBe listOf("fA", "fB", "fC")
        }

        test("Custom mode move-up does nothing for the first fund") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()
            vm.onSortModeChange(FundSortMode.Custom)

            vm.onSelectFund("fA")
            vm.onMoveSelectedUp()

            vm.state.value.funds.map { it.fundId } shouldBe listOf("fA", "fB", "fC")
            vm.state.value.canMoveSelectedUp shouldBe false
        }

        test("Custom mode move-down does nothing for the last fund") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()
            vm.onSortModeChange(FundSortMode.Custom)

            vm.onSelectFund("fC")
            vm.onMoveSelectedDown()

            vm.state.value.funds.map { it.fundId } shouldBe listOf("fA", "fB", "fC")
            vm.state.value.canMoveSelectedDown shouldBe false
        }

        test("Custom order persists across a reload — deleted funds drop, new ones appended alphabetically") {
            coEvery { fundRepo.getFunds(portfolioId) } returnsMany listOf(
                AppResult.Success(sampleFunds),
                AppResult.Success(listOf(fundC, fundA, FundListItem(
                    fundId = "fD",
                    name = "זהב",
                    balanceAgoras = 100_00L,
                    allocationPercent = 12.5,
                    createdAtUtc = Instant.parse("2026-04-01T00:00:00Z"),
                ))), // fundB deleted, fundD added (Hebrew alphabet: ז < מ).
            )
            val vm = buildVm()
            vm.onSortModeChange(FundSortMode.Custom)
            // After seeding the order is [A, B, C]. Move A to the end → [B, C, A].
            vm.onSelectFund("fA")
            vm.onMoveSelectedDown()
            vm.onMoveSelectedDown()
            vm.state.value.funds.map { it.fundId } shouldBe listOf("fB", "fC", "fA")

            // Reload now returns A, C, D (B is gone). Custom order keeps B's slot dropped:
            // remembered order was [B, C, A], so the new sorted list = [C, A, D] (D appended).
            vm.onRetry()

            vm.state.value.funds.map { it.fundId } shouldBe listOf("fC", "fA", "fD")
        }

        // ── Sync events ──────────────────────────────────────────────────────

        test("AppStart sync event triggers a refresh") {
            coEvery { fundRepo.getFunds(portfolioId) } returnsMany listOf(
                AppResult.Success(emptyList()),
                AppResult.Success(sampleFunds),
            )
            val vm = buildVm()

            syncEvents.emit(SyncTrigger.AppStart)

            vm.state.value.funds.size shouldBe 3
        }

        test("PostMutation for this portfolio triggers a refresh") {
            coEvery { fundRepo.getFunds(portfolioId) } returnsMany listOf(
                AppResult.Success(emptyList()),
                AppResult.Success(sampleFunds),
            )
            val vm = buildVm()

            syncEvents.emit(SyncTrigger.PostMutation(portfolioId))

            vm.state.value.funds.size shouldBe 3
        }

        test("PostMutation for a different portfolio is ignored") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            syncEvents.emit(SyncTrigger.PostMutation("some-other-portfolio"))

            // Only the initial load — exactly 1 call.
            coVerify(exactly = 1) { fundRepo.getFunds(portfolioId) }
            // Sanity-check the VM did construct correctly.
            vm.state.value.funds.size shouldBe 3
        }

        // ── Deposit (E6.8) ───────────────────────────────────────────────────

        test("deposit click opens the deposit dialog targeting the fund") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onDepositClick(fundA)

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.DepositInput>()
            dlg.target.fundId shouldBe "fA"
            dlg.amountText shouldBe ""
        }

        test("deposit OK after typing amount opens the ConfirmOperation dialog with parsed agoras") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onDepositClick(fundA)
            vm.onDialogAmountChange("250.50")
            vm.onDialogConfirm() // first OK — stages the confirm dialog, does NOT call repo

            val confirm = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.ConfirmOperation>()
            confirm.kind shouldBe FundDialog.ConfirmOperation.Kind.Deposit
            confirm.sourceFund.fundId shouldBe "fA"
            confirm.amountAgoras shouldBe 25_050L
            coVerify(exactly = 0) { fundRepo.deposit(any(), any(), any()) }
        }

        test("deposit fully confirmed calls repository with parsed agoras and refreshes") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.deposit("p1", "fA", 25_050L) } returns AppResult.Success("op-1")
            val vm = buildVm()

            vm.onDepositClick(fundA)
            vm.onDialogAmountChange("250.50")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // confirm — now fires repo

            coVerify(exactly = 1) { fundRepo.deposit("p1", "fA", 25_050L) }
            // On success the dialog closes, sync trigger fires, refresh happens.
            vm.state.value.dialog shouldBe FundDialog.None
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation("p1") }
            coVerify(exactly = 2) { fundRepo.getFunds(portfolioId) } // initial + refresh
        }

        test("dismissing the confirm dialog does not call the repository") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onDepositClick(fundA)
            vm.onDialogAmountChange("100")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogDismiss() // user cancels confirmation

            vm.state.value.dialog shouldBe FundDialog.None
            coVerify(exactly = 0) { fundRepo.deposit(any(), any(), any()) }
        }

        test("deposit rejects empty amount with positive-amount error and does not call repo") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onDepositClick(fundA)
            vm.onDialogConfirm() // amountText is ""

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.DepositInput>()
            dlg.errorMessageRes shouldBe R.string.fund_error_amount_must_be_positive
            coVerify(exactly = 0) { fundRepo.deposit(any(), any(), any()) }
        }

        test("deposit rejects zero amount with positive-amount error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onDepositClick(fundA)
            vm.onDialogAmountChange("0")
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.DepositInput>()
            dlg.errorMessageRes shouldBe R.string.fund_error_amount_must_be_positive
            coVerify(exactly = 0) { fundRepo.deposit(any(), any(), any()) }
        }

        test("deposit rejects non-numeric amount with invalid-number error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onDepositClick(fundA)
            vm.onDialogAmountChange("xyz")
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.DepositInput>()
            dlg.errorMessageRes shouldBe R.string.fund_error_invalid_number
            coVerify(exactly = 0) { fundRepo.deposit(any(), any(), any()) }
        }

        test("deposit rejects too-many-decimals with matching error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onDepositClick(fundA)
            vm.onDialogAmountChange("100.123")
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.DepositInput>()
            dlg.errorMessageRes shouldBe R.string.fund_error_too_many_decimals
            coVerify(exactly = 0) { fundRepo.deposit(any(), any(), any()) }
        }

        test("deposit server PortfolioClosed: after confirm, dialog closes and screen-level error is set") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.deposit("p1", "fA", 10_000L) } returns
                AppResult.Failure(AppError.Validation.PortfolioClosed)
            val vm = buildVm()

            vm.onDepositClick(fundA)
            vm.onDialogAmountChange("100")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // fire repo

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_portfolio_closed
        }

        // ── Withdraw (E6.9) ──────────────────────────────────────────────────

        test("withdraw click opens the withdraw dialog targeting the fund") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onWithdrawClick(fundB)

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.WithdrawInput>()
            dlg.target.fundId shouldBe "fB"
        }

        test("withdraw fully confirmed calls repository with positive agoras and refreshes") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.withdraw("p1", "fA", 5_000L) } returns AppResult.Success("op-2")
            val vm = buildVm()

            vm.onWithdrawClick(fundA)
            vm.onDialogAmountChange("50")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // fire repo

            coVerify(exactly = 1) { fundRepo.withdraw("p1", "fA", 5_000L) }
            vm.state.value.dialog shouldBe FundDialog.None
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation("p1") }
        }

        test("withdraw server NegativeBalance: after confirm, screen-level insufficient-balance error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.withdraw("p1", "fA", 99_999_99L) } returns
                AppResult.Failure(AppError.Invariant.NegativeBalance)
            val vm = buildVm()

            vm.onWithdrawClick(fundA)
            vm.onDialogAmountChange("99999.99")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // fire repo

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_insufficient_balance
        }

        test("withdraw rejects negative input with positive-amount error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onWithdrawClick(fundA)
            vm.onDialogAmountChange("-100")
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.WithdrawInput>()
            dlg.errorMessageRes shouldBe R.string.fund_error_amount_must_be_positive
            coVerify(exactly = 0) { fundRepo.withdraw(any(), any(), any()) }
        }

        // ── Transfer (E6.10) ─────────────────────────────────────────────────

        test("transfer click on portfolio with 2+ funds opens dialog with candidates excluding source") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onTransferClick(fundA)

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.TransferInput>()
            dlg.source.fundId shouldBe "fA"
            dlg.candidates.map { it.fundId } shouldBe listOf("fB", "fC")
            dlg.destinationFundId shouldBe null
        }

        test("transfer click with only one fund surfaces screen-level error and no dialog") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(listOf(fundA))
            val vm = buildVm()

            vm.onTransferClick(fundA)

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_transfer_needs_two_funds
        }

        test("transfer confirm without picking destination shows destination-required error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onTransferClick(fundA)
            vm.onDialogAmountChange("100")
            vm.onDialogConfirm()

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.TransferInput>()
            dlg.errorMessageRes shouldBe R.string.fund_error_transfer_destination_required
            coVerify(exactly = 0) { fundRepo.transfer(any(), any(), any(), any()) }
        }

        test("transfer fully confirmed calls repository with source/destination/amount and refreshes") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.transfer("p1", "fA", "fB", 20_000L) } returns AppResult.Success("op-3")
            val vm = buildVm()

            vm.onTransferClick(fundA)
            vm.onTransferDestinationChange("fB")
            vm.onDialogAmountChange("200")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // fire repo

            coVerify(exactly = 1) { fundRepo.transfer("p1", "fA", "fB", 20_000L) }
            vm.state.value.dialog shouldBe FundDialog.None
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation("p1") }
        }

        test("transfer staging holds source + destination + amount before firing repo") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onTransferClick(fundA)
            vm.onTransferDestinationChange("fB")
            vm.onDialogAmountChange("150.25")
            vm.onDialogConfirm()

            val confirm = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.ConfirmOperation>()
            confirm.kind shouldBe FundDialog.ConfirmOperation.Kind.Transfer
            confirm.sourceFund.fundId shouldBe "fA"
            confirm.destinationFund?.fundId shouldBe "fB"
            confirm.amountAgoras shouldBe 15_025L
        }

        test("transfer server SameFundTransfer: after confirm, screen-level same-fund error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.transfer("p1", "fA", "fB", 10_000L) } returns
                AppResult.Failure(AppError.Validation.SameFundTransfer)
            val vm = buildVm()

            vm.onTransferClick(fundA)
            vm.onTransferDestinationChange("fB")
            vm.onDialogAmountChange("100")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // fire repo

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_same_fund_transfer
        }

        test("transfer server NegativeBalance: after confirm, screen-level insufficient-transfer error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.transfer("p1", "fA", "fB", 99_999_99L) } returns
                AppResult.Failure(AppError.Invariant.NegativeBalance)
            val vm = buildVm()

            vm.onTransferClick(fundA)
            vm.onTransferDestinationChange("fB")
            vm.onDialogAmountChange("99999.99")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // fire repo

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_insufficient_balance_transfer
        }

        test("transfer server NotFound: after confirm, screen-level transfer-not-found error") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.transfer("p1", "fA", "fB", 5_000L) } returns
                AppResult.Failure(AppError.NotFound("fund"))
            val vm = buildVm()

            vm.onTransferClick(fundA)
            vm.onTransferDestinationChange("fB")
            vm.onDialogAmountChange("50")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // fire repo

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_transfer_not_found
        }

        test("dismiss closes a deposit/withdraw/transfer dialog when not submitting") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onWithdrawClick(fundA)
            vm.state.value.dialog.shouldBeInstanceOf<FundDialog.WithdrawInput>()
            vm.onDialogDismiss()
            vm.state.value.dialog shouldBe FundDialog.None
        }

        // ── Revalue portfolio (E6.11) ────────────────────────────────────────

        test("revalue click opens the RevalueInput dialog") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onRevalueClick()

            val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.RevalueInput>()
            dlg.newTotalText shouldBe ""
            dlg.errorMessageRes shouldBe 0
        }

        test("revalue click on empty portfolio sets a screen-level error and opens no dialog") {
            // Mirrors desktop RevaluePortfolioAsync: `if (Funds.Count == 0) ErrorMessage = ...; return.`
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            val vm = buildVm()

            vm.onRevalueClick()

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_no_funds_for_revalue
        }

        test("revalue OK with valid amount stages ConfirmRevalue with old + new totals") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()
            // Old total from the loaded list: 500_00 + 300_00 + 200_00 = 1000_00 agoras.
            val expectedOldTotal = sampleFunds.sumOf { it.balanceAgoras }

            vm.onRevalueClick()
            vm.onRevalueAmountChange("1500.00")
            vm.onDialogConfirm() // stage confirm; must NOT call repo yet

            val confirm = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.ConfirmRevalue>()
            confirm.oldTotalAgoras shouldBe expectedOldTotal
            confirm.newTotalAgoras shouldBe 150_000L
            coVerify(exactly = 0) { fundRepo.revaluePortfolio(any(), any()) }
        }

        test("revalue rejects empty / zero / negative / non-numeric / too-many-decimals") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)

            // Each case opens a fresh dialog, types the bad input, and asserts the
            // expected inline error. We rebuild the VM per case so the dialog state
            // doesn't carry over.
            data class Case(val input: String, val expectedErrorRes: Int)
            val cases = listOf(
                Case("", R.string.fund_error_amount_must_be_positive),       // empty
                Case("0", R.string.fund_error_amount_must_be_positive),      // zero
                Case("-50", R.string.fund_error_amount_must_be_positive),    // negative (parser → Negative)
                Case("abc", R.string.fund_error_invalid_number),             // non-numeric
                Case("100.123", R.string.fund_error_too_many_decimals),      // too many decimals
            )

            for ((input, expectedErrorRes) in cases) {
                val vm = buildVm()
                vm.onRevalueClick()
                vm.onRevalueAmountChange(input)
                vm.onDialogConfirm()

                val dlg = vm.state.value.dialog.shouldBeInstanceOf<FundDialog.RevalueInput>()
                dlg.errorMessageRes shouldBe expectedErrorRes
            }
            coVerify(exactly = 0) { fundRepo.revaluePortfolio(any(), any()) }
        }

        test("revalue fully confirmed calls repository with parsed agoras and refreshes") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.revaluePortfolio("p1", 200_000L) } returns AppResult.Success("op-rev-1")
            val vm = buildVm()

            vm.onRevalueClick()
            vm.onRevalueAmountChange("2000")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogConfirm() // confirm — fires repo

            coVerify(exactly = 1) { fundRepo.revaluePortfolio("p1", 200_000L) }
            vm.state.value.dialog shouldBe FundDialog.None
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation("p1") }
            coVerify(exactly = 2) { fundRepo.getFunds(portfolioId) } // initial + refresh
        }

        test("revalue no-op result (same total) still closes dialog and triggers refresh + sync") {
            // E6.11 step 3: when new total == old total, the repository returns Success(null)
            // and logs nothing. The VM should still close the dialog and fire its post-
            // mutation flow harmlessly — the refresh is cheap and keeps behaviour uniform.
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.revaluePortfolio("p1", 100_000L) } returns AppResult.Success(null)
            val vm = buildVm()

            vm.onRevalueClick()
            vm.onRevalueAmountChange("1000.00") // exactly the current total
            vm.onDialogConfirm()
            vm.onDialogConfirm()

            vm.state.value.dialog shouldBe FundDialog.None
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation("p1") }
        }

        test("revalue server PortfolioTotalIsZero: screen-level message matches desktop") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.revaluePortfolio("p1", 50_000L) } returns
                AppResult.Failure(AppError.Validation.PortfolioTotalIsZero)
            val vm = buildVm()

            vm.onRevalueClick()
            vm.onRevalueAmountChange("500")
            vm.onDialogConfirm()
            vm.onDialogConfirm()

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_portfolio_total_is_zero
        }

        test("revalue server RevalueWouldZeroFund: screen-level message matches desktop") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.revaluePortfolio("p1", 1L) } returns
                AppResult.Failure(AppError.Validation.RevalueWouldZeroFund)
            val vm = buildVm()

            vm.onRevalueClick()
            vm.onRevalueAmountChange("0.01")
            vm.onDialogConfirm()
            vm.onDialogConfirm()

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_revalue_would_zero_fund
        }

        test("revalue server PortfolioClosed: screen-level closed-portfolio message") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.revaluePortfolio("p1", 200_000L) } returns
                AppResult.Failure(AppError.Validation.PortfolioClosed)
            val vm = buildVm()

            vm.onRevalueClick()
            vm.onRevalueAmountChange("2000")
            vm.onDialogConfirm()
            vm.onDialogConfirm()

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_portfolio_closed
        }

        test("revalue server TotalMismatch: screen-level mismatch message") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            coEvery { fundRepo.revaluePortfolio("p1", 200_000L) } returns
                AppResult.Failure(AppError.Invariant.TotalMismatch)
            val vm = buildVm()

            vm.onRevalueClick()
            vm.onRevalueAmountChange("2000")
            vm.onDialogConfirm()
            vm.onDialogConfirm()

            vm.state.value.dialog shouldBe FundDialog.None
            vm.state.value.errorMessageRes shouldBe R.string.fund_error_total_mismatch
        }

        test("revalue dismissing the confirm dialog does not call the repository") {
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(sampleFunds)
            val vm = buildVm()

            vm.onRevalueClick()
            vm.onRevalueAmountChange("2000")
            vm.onDialogConfirm() // stage confirm
            vm.onDialogDismiss() // user backs out before committing

            vm.state.value.dialog shouldBe FundDialog.None
            coVerify(exactly = 0) { fundRepo.revaluePortfolio(any(), any()) }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a [FundListViewModel] using the test mocks.
     *
     * Navigation Compose's `toRoute<Routes.FundList>()` extension reads the route arguments
     * out of [SavedStateHandle] by property name, so we pre-populate the handle with
     * `portfolioId` and `portfolioName` entries.
     */
    private fun buildVm(): FundListViewModel {
        val savedState = SavedStateHandle(
            mapOf(
                "portfolioId" to portfolioId,
                "portfolioName" to portfolioName,
            ),
        )
        return FundListViewModel(fundRepo, syncCoordinator, savedState)
    }
}
