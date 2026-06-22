package com.virtualfunds.ui.history

import androidx.lifecycle.SavedStateHandle
import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.FundFilterOption
import com.virtualfunds.domain.model.TransactionDetailItem
import com.virtualfunds.domain.model.TransactionGroup
import com.virtualfunds.domain.model.TransactionType
import com.virtualfunds.domain.repository.FundRepository
import com.virtualfunds.domain.repository.TransactionRepository
import com.virtualfunds.sync.SyncCoordinator
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * Unit tests for [TransactionHistoryViewModel] (PR-7, E7.6).
 *
 * # Test strategy
 *
 * Same shape as [com.virtualfunds.ui.fund.FundListViewModelTest]:
 *   - `UnconfinedTestDispatcher` set as Main so the `init { load() }` coroutine runs to
 *     completion synchronously during construction (the mocked repo suspends-then-returns
 *     immediately), letting us assert on `state.value` right after `buildVm()`.
 *   - `IsolationMode.InstancePerTest` so every test gets fresh mocks.
 *
 * Focus: client-side filtering (date / fund / type, E7.6), clear-filters, empty states,
 * display paging, and load-failure handling. Undoability (E6.12) is computed in the repository
 * and covered by its own tests, so it isn't re-tested here.
 */
class TransactionHistoryViewModelTest : FunSpec() {

    private val repo = mockk<TransactionRepository>()
    private val fundRepo = mockk<FundRepository>()
    private val syncCoordinator = mockk<SyncCoordinator>()

    private val portfolioId = "p1"
    private val portfolioName = "פנסיה"

    // Three operations on distinct days (noon UTC). We derive the filter bounds from these same
    // instants through the device zone, so the date assertions hold on any machine timezone.
    private val day10 = Instant.parse("2026-01-10T12:00:00Z")
    private val day20 = Instant.parse("2026-01-20T12:00:00Z")
    private val day30 = Instant.parse("2026-01-30T12:00:00Z")

    init {
        isolationMode = IsolationMode.InstancePerTest

        beforeTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            // Sensible defaults; individual tests override getHistory as needed.
            coEvery { repo.getHistory(any()) } returns AppResult.Success(emptyList())
            coEvery { repo.getFundFilterOptions(any()) } returns AppResult.Success(emptyList())
            justRun { syncCoordinator.triggerPostMutation(any()) }
        }

        afterTest {
            Dispatchers.resetMain()
            clearAllMocks()
        }

        // ── Initial load ──────────────────────────────────────────────────────

        test("successful load populates groups and fund options, clears loading") {
            val groups = listOf(makeGroup(operationId = "op1", type = TransactionType.FundDeposit))
            val options = listOf(FundFilterOption("fA", "מניות"))
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(groups)
            coEvery { repo.getFundFilterOptions(portfolioId) } returns AppResult.Success(options)

            val vm = buildVm()

            vm.state.value.groups.map { it.operationId } shouldBe listOf("op1")
            vm.state.value.fundFilterOptions shouldBe options
            vm.state.value.isLoading shouldBe false
            vm.state.value.isEmpty shouldBe false
            vm.state.value.errorMessageRes shouldBe 0
            vm.state.value.portfolioName shouldBe portfolioName
        }

        test("failed history load sets error message") {
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Failure(AppError.Network("boom"))

            val vm = buildVm()

            vm.state.value.groups shouldBe emptyList()
            vm.state.value.errorMessageRes shouldBe R.string.history_error_load
        }

        test("fund-options failure still shows history with empty fund dropdown") {
            val groups = listOf(makeGroup(operationId = "op1"))
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(groups)
            coEvery { repo.getFundFilterOptions(portfolioId) } returns
                AppResult.Failure(AppError.Network("boom"))

            val vm = buildVm()

            vm.state.value.groups.map { it.operationId } shouldBe listOf("op1")
            vm.state.value.fundFilterOptions shouldBe emptyList()
            vm.state.value.errorMessageRes shouldBe 0
        }

        test("retry after failure clears error and reloads") {
            coEvery { repo.getHistory(portfolioId) } returnsMany listOf(
                AppResult.Failure(AppError.Network("first")),
                AppResult.Success(listOf(makeGroup(operationId = "op1"))),
            )
            val vm = buildVm()
            vm.state.value.errorMessageRes shouldBe R.string.history_error_load

            vm.onRetry()

            vm.state.value.errorMessageRes shouldBe 0
            vm.state.value.groups.size shouldBe 1
        }

        test("empty history reports isEmpty with no active filters") {
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(emptyList())

            val vm = buildVm()

            vm.state.value.isEmpty shouldBe true
            vm.state.value.hasActiveFilters shouldBe false
        }

        // ── Type filter (E7.6) ────────────────────────────────────────────────

        test("type filter keeps only operations whose summary type matches") {
            val deposit = makeGroup(operationId = "dep", type = TransactionType.FundDeposit)
            val withdraw = makeGroup(operationId = "wd", type = TransactionType.FundWithdrawal)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(deposit, withdraw))
            val vm = buildVm()

            vm.onTypeFilterChange(TransactionType.FundDeposit)

            vm.state.value.groups.map { it.operationId } shouldBe listOf("dep")
        }

        // ── Fund filter (E7.6) ────────────────────────────────────────────────

        test("fund filter keeps operations with a detail row for that fund") {
            val opA = makeGroup(operationId = "a", details = listOf(detail("fA")))
            val opB = makeGroup(operationId = "b", details = listOf(detail("fB")))
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(opA, opB))
            val vm = buildVm()

            vm.onFundFilterChange("fA")

            vm.state.value.groups.map { it.operationId } shouldBe listOf("a")
        }

        // ── Date range filter (E7.6) ──────────────────────────────────────────

        test("from-date filter keeps operations on or after the bound") {
            val g30 = makeGroup(operationId = "g30", committedAtUtc = day30)
            val g20 = makeGroup(operationId = "g20", committedAtUtc = day20)
            val g10 = makeGroup(operationId = "g10", committedAtUtc = day10)
            // Newest-first, as the repository returns them.
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(g30, g20, g10))
            val vm = buildVm()

            vm.onFromDateChange(localDate(day20))

            vm.state.value.groups.map { it.operationId } shouldBe listOf("g30", "g20")
        }

        test("to-date filter keeps operations on or before the bound") {
            val g30 = makeGroup(operationId = "g30", committedAtUtc = day30)
            val g20 = makeGroup(operationId = "g20", committedAtUtc = day20)
            val g10 = makeGroup(operationId = "g10", committedAtUtc = day10)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(g30, g20, g10))
            val vm = buildVm()

            vm.onToDateChange(localDate(day20))

            vm.state.value.groups.map { it.operationId } shouldBe listOf("g20", "g10")
        }

        // ── Clear filters ─────────────────────────────────────────────────────

        test("clear filters restores the full list and reports no active filters") {
            val deposit = makeGroup(operationId = "dep", type = TransactionType.FundDeposit)
            val withdraw = makeGroup(operationId = "wd", type = TransactionType.FundWithdrawal)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(deposit, withdraw))
            val vm = buildVm()
            vm.onTypeFilterChange(TransactionType.FundDeposit)
            vm.state.value.groups.size shouldBe 1

            vm.onClearFilters()

            vm.state.value.groups.map { it.operationId } shouldBe listOf("dep", "wd")
            vm.state.value.hasActiveFilters shouldBe false
        }

        test("filtering to nothing reports isEmpty with active filters") {
            val deposit = makeGroup(operationId = "dep", type = TransactionType.FundDeposit)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(deposit))
            val vm = buildVm()

            // No FundWithdrawal operations exist → result set becomes empty.
            vm.onTypeFilterChange(TransactionType.FundWithdrawal)

            vm.state.value.isEmpty shouldBe true
            vm.state.value.hasActiveFilters shouldBe true
        }

        // ── Display paging ────────────────────────────────────────────────────

        test("history longer than one page is revealed incrementally") {
            // PAGE_SIZE is 50 (private). 60 groups → first page shows 50, hasMore true.
            val groups = (1..60).map { makeGroup(operationId = "op$it") }
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(groups)
            val vm = buildVm()

            vm.state.value.groups.size shouldBe 50
            vm.state.value.hasMore shouldBe true

            vm.onLoadMore()

            vm.state.value.groups.size shouldBe 60
            vm.state.value.hasMore shouldBe false
        }

        test("changing a filter resets paging back to the first page") {
            val deposits = (1..60).map {
                makeGroup(operationId = "dep$it", type = TransactionType.FundDeposit)
            }
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(deposits)
            val vm = buildVm()
            vm.onLoadMore()
            vm.state.value.groups.size shouldBe 60

            // Re-applying a filter should drop us back to a single page over the filtered set.
            vm.onTypeFilterChange(TransactionType.FundDeposit)

            vm.state.value.groups.size shouldBe 50
            vm.state.value.hasMore shouldBe true
        }

        // ── Undo (E6.12) ──────────────────────────────────────────────────────

        test("onUndoClick on an undoable operation opens the confirm dialog") {
            val group = makeGroup(operationId = "op1", isUndoable = true)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(group))
            val vm = buildVm()

            vm.onUndoClick(group)

            vm.state.value.undoConfirm?.operationId shouldBe "op1"
        }

        test("onUndoClick on a non-undoable operation is a no-op") {
            val group = makeGroup(operationId = "op1", isUndoable = false)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(group))
            val vm = buildVm()

            vm.onUndoClick(group)

            vm.state.value.undoConfirm shouldBe null
        }

        test("confirming undo commits, broadcasts a mutation, and reloads history") {
            val group = makeGroup(operationId = "op1", isUndoable = true)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(group))
            coEvery { fundRepo.undoOperation(portfolioId, "op1") } returns AppResult.Success(Unit)
            val vm = buildVm()
            vm.onUndoClick(group)

            vm.onUndoConfirm()

            coVerify(exactly = 1) { fundRepo.undoOperation(portfolioId, "op1") }
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation(portfolioId) }
            // getHistory called twice: initial load + reload after undo (E6.12 step 7).
            coVerify(exactly = 2) { repo.getHistory(portfolioId) }
            vm.state.value.undoConfirm shouldBe null
            vm.state.value.isUndoing shouldBe false
        }

        test("undo failure surfaces a one-shot error and closes the dialog") {
            val group = makeGroup(operationId = "op1", isUndoable = true)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(group))
            coEvery { fundRepo.undoOperation(portfolioId, "op1") } returns
                AppResult.Failure(AppError.Invariant.NegativeBalance)
            val vm = buildVm()
            vm.onUndoClick(group)

            vm.onUndoConfirm()

            vm.state.value.undoErrorRes shouldBe R.string.history_error_undo_negative_balance
            vm.state.value.undoConfirm shouldBe null
            vm.state.value.isUndoing shouldBe false
            // A failed undo must NOT broadcast a mutation.
            coVerify(exactly = 0) { syncCoordinator.triggerPostMutation(any()) }
        }

        test("onUndoErrorShown clears the one-shot error") {
            val group = makeGroup(operationId = "op1", isUndoable = true)
            coEvery { repo.getHistory(portfolioId) } returns AppResult.Success(listOf(group))
            coEvery { fundRepo.undoOperation(portfolioId, "op1") } returns
                AppResult.Failure(AppError.Validation.PortfolioClosed)
            val vm = buildVm()
            vm.onUndoClick(group)
            vm.onUndoConfirm()
            vm.state.value.undoErrorRes shouldBe R.string.history_error_undo_portfolio_closed

            vm.onUndoErrorShown()

            vm.state.value.undoErrorRes shouldBe 0
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildVm(): TransactionHistoryViewModel {
        val savedState = SavedStateHandle(
            mapOf(
                "portfolioId" to portfolioId,
                "portfolioName" to portfolioName,
            ),
        )
        return TransactionHistoryViewModel(repo, fundRepo, syncCoordinator, savedState)
    }

    private fun makeGroup(
        operationId: String,
        type: TransactionType = TransactionType.FundDeposit,
        committedAtUtc: Instant = day20,
        details: List<TransactionDetailItem> = emptyList(),
        isUndoable: Boolean = false,
    ): TransactionGroup = TransactionGroup(
        operationId = operationId,
        committedAtUtc = committedAtUtc,
        transactionType = type,
        summaryText = "summary $operationId",
        undoOfOperationId = null,
        amountAgoras = 100_00L,
        details = details,
        isUndoable = isUndoable,
    )

    private fun detail(fundId: String): TransactionDetailItem = TransactionDetailItem(
        fundId = fundId,
        fundName = "fund $fundId",
        transactionType = TransactionType.FundDeposit,
        amountAgoras = 100_00L,
        beforeBalanceAgoras = 0L,
        afterBalanceAgoras = 100_00L,
    )

    /** Same UTC-instant → device-local date conversion the VM uses for date filtering. */
    private fun localDate(instant: Instant): LocalDate =
        instant.atZone(ZoneId.systemDefault()).toLocalDate()
}
