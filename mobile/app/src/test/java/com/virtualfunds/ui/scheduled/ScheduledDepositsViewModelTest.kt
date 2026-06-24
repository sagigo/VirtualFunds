package com.virtualfunds.ui.scheduled

import androidx.lifecycle.SavedStateHandle
import com.virtualfunds.R
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.domain.model.ScheduleKind
import com.virtualfunds.domain.model.ScheduledDepositListItem
import com.virtualfunds.domain.repository.FundRepository
import com.virtualfunds.domain.repository.ScheduledDepositRepository
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
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Unit tests for [ScheduledDepositsViewModel] (PR-8, E8).
 *
 * Same harness as [com.virtualfunds.ui.fund.FundListViewModelTest]: an `UnconfinedTestDispatcher`
 * runs init/coroutine work synchronously, and `IsolationMode.InstancePerTest` gives each test a
 * fresh VM + sync-events flow.
 *
 * Coverage focuses on the Phase-9-specific logic: schedule-field mapping per kind (E8.2), the
 * client-side OneTime `next_run_at_utc` computation, enable/disable as a flag-flipped upsert
 * (E8.7), and the create/edit/delete flows + their post-mutation refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledDepositsViewModelTest : FunSpec() {

    private val scheduledRepo = mockk<ScheduledDepositRepository>()
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
        balanceAgoras = 500_00L,
        allocationPercent = 50.0,
        createdAtUtc = Instant.parse("2026-02-01T00:00:00Z"),
    )
    private val funds = listOf(fundA, fundB)

    private val dailyItem = ScheduledDepositListItem(
        scheduledDepositId = "sd1",
        portfolioId = portfolioId,
        fundId = "fB",
        fundName = "אג״ח",
        name = "חיסכון",
        note = "הערה",
        isEnabled = true,
        amountAgoras = 500_00L,
        scheduleKind = ScheduleKind.Daily,
        timeOfDayMinutes = 600,
        weekdayMask = null,
        dayOfMonth = null,
        nextRunAtUtc = Instant.parse("2026-06-28T06:00:00Z"),
    )
    private val oneTimeItem = ScheduledDepositListItem(
        scheduledDepositId = "sd2",
        portfolioId = portfolioId,
        fundId = "fA",
        fundName = "מניות",
        name = "בונוס",
        note = null,
        isEnabled = true,
        amountAgoras = 1_000_00L,
        scheduleKind = ScheduleKind.OneTime,
        timeOfDayMinutes = null,
        weekdayMask = null,
        dayOfMonth = null,
        nextRunAtUtc = Instant.parse("2026-07-01T11:30:00Z"),
    )

    init {
        isolationMode = IsolationMode.InstancePerTest

        beforeTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            every { syncCoordinator.events } returns syncEvents
            justRun { syncCoordinator.triggerPostMutation(any()) }
        }

        afterTest {
            Dispatchers.resetMain()
            clearAllMocks()
        }

        // ── Initial load ──────────────────────────────────────────────────────

        test("successful initial load populates items and clears loading") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns
                AppResult.Success(listOf(dailyItem, oneTimeItem))

            val vm = buildVm()

            vm.state.value.items.map { it.scheduledDepositId } shouldBe listOf("sd1", "sd2")
            vm.state.value.isLoading shouldBe false
            vm.state.value.errorMessageRes shouldBe 0
            vm.state.value.portfolioName shouldBe portfolioName
        }

        test("failed initial load sets the load error") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns
                AppResult.Failure(AppError.Network("boom"))

            val vm = buildVm()

            vm.state.value.items shouldBe emptyList()
            vm.state.value.errorMessageRes shouldBe R.string.sd_error_load
        }

        test("retry after failure reloads") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returnsMany listOf(
                AppResult.Failure(AppError.Network("first")),
                AppResult.Success(listOf(dailyItem)),
            )
            val vm = buildVm()
            vm.state.value.errorMessageRes shouldBe R.string.sd_error_load

            vm.onRetry()

            vm.state.value.errorMessageRes shouldBe 0
            vm.state.value.items.size shouldBe 1
        }

        // ── Opening the form ──────────────────────────────────────────────────

        test("add click opens a create form with the first fund preselected and Daily default") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            val vm = buildVm()

            vm.onAddClick()

            val form = vm.state.value.form
            form.shouldBeInstanceOf<ScheduledDepositFormState>()
            form.isCreate shouldBe true
            form.fundId shouldBe "fA"
            form.scheduleKind shouldBe ScheduleKind.Daily
        }

        test("add click with no funds does not open the form and calls no upsert") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(emptyList())
            val vm = buildVm()

            vm.onAddClick()

            vm.state.value.form shouldBe null
            coVerify(exactly = 0) {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            }
        }

        test("edit click opens a form pre-filled from the deposit") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(listOf(dailyItem))
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            val vm = buildVm()

            vm.onEditClick(dailyItem)

            val form = vm.state.value.form
            form.shouldBeInstanceOf<ScheduledDepositFormState>()
            form.editingId shouldBe "sd1"
            form.name shouldBe "חיסכון"
            form.fundId shouldBe "fB"
            form.amountText shouldBe "500.00"
            form.scheduleKind shouldBe ScheduleKind.Daily
            form.timeOfDayMinutes shouldBe 600
            form.note shouldBe "הערה"
        }

        // ── Validation ────────────────────────────────────────────────────────

        test("save with blank name shows empty-name error and calls no upsert") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            val vm = buildVm()
            vm.onAddClick()
            vm.onFormAmountChange("100")

            vm.onFormSave()

            vm.state.value.form?.errorMessageRes shouldBe R.string.sd_error_empty_name
            coVerify(exactly = 0) {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            }
        }

        test("save with invalid amount shows invalid-number error") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            val vm = buildVm()
            vm.onAddClick()
            vm.onFormNameChange("חיסכון")
            vm.onFormAmountChange("abc")

            vm.onFormSave()

            vm.state.value.form?.errorMessageRes shouldBe R.string.fund_error_invalid_number
        }

        test("editing a form field clears a pending error") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            val vm = buildVm()
            vm.onAddClick()
            vm.onFormSave() // blank name → error
            vm.state.value.form?.errorMessageRes shouldBe R.string.sd_error_empty_name

            vm.onFormNameChange("חיסכון")

            vm.state.value.form?.errorMessageRes shouldBe 0
        }

        test("save Weekly with no weekday selected shows weekday error") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            val vm = buildVm()
            vm.onAddClick()
            vm.onFormNameChange("חיסכון")
            vm.onFormAmountChange("100")
            vm.onFormKindChange(ScheduleKind.Weekly)

            vm.onFormSave()

            vm.state.value.form?.errorMessageRes shouldBe R.string.sd_error_no_weekday
        }

        // ── Create per schedule kind (E8.2 field mapping) ─────────────────────

        test("create Daily sends time-of-day only, others null") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            coEvery {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AppResult.Success("new-id")
            val vm = buildVm()

            vm.onAddClick()
            vm.onFormNameChange("חיסכון")
            vm.onFormAmountChange("500")
            vm.onFormSave()

            coVerify(exactly = 1) {
                scheduledRepo.upsertScheduledDeposit(
                    portfolioId = portfolioId,
                    fundId = "fA",
                    name = "חיסכון",
                    amountAgoras = 500_00L,
                    scheduleKind = ScheduleKind.Daily,
                    isEnabled = true,
                    note = null,
                    timeOfDayMinutes = 9 * 60,
                    weekdayMask = null,
                    dayOfMonth = null,
                    nextRunAtUtc = null,
                    scheduledDepositId = null,
                )
            }
            vm.state.value.form shouldBe null
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation(portfolioId) }
            coVerify(exactly = 2) { scheduledRepo.getScheduledDeposits(portfolioId) } // initial + reload
        }

        test("create Weekly sends weekday mask + time, day-of-month and next-run null") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            coEvery {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AppResult.Success("new-id")
            val vm = buildVm()

            vm.onAddClick()
            vm.onFormNameChange("חיסכון")
            vm.onFormAmountChange("100")
            vm.onFormKindChange(ScheduleKind.Weekly)
            vm.onFormWeekdayToggle(1) // Monday → bit 1 = 2
            vm.onFormWeekdayToggle(3) // Wednesday → bit 3 = 8
            vm.onFormSave()

            coVerify(exactly = 1) {
                scheduledRepo.upsertScheduledDeposit(
                    portfolioId = portfolioId,
                    fundId = "fA",
                    name = "חיסכון",
                    amountAgoras = 100_00L,
                    scheduleKind = ScheduleKind.Weekly,
                    isEnabled = true,
                    note = null,
                    timeOfDayMinutes = 9 * 60,
                    weekdayMask = 10,
                    dayOfMonth = null,
                    nextRunAtUtc = null,
                    scheduledDepositId = null,
                )
            }
        }

        test("create Monthly sends day-of-month + time, weekday and next-run null") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            coEvery {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AppResult.Success("new-id")
            val vm = buildVm()

            vm.onAddClick()
            vm.onFormNameChange("חיסכון")
            vm.onFormAmountChange("100")
            vm.onFormKindChange(ScheduleKind.Monthly)
            vm.onFormDayOfMonthChange(15)
            vm.onFormSave()

            coVerify(exactly = 1) {
                scheduledRepo.upsertScheduledDeposit(
                    portfolioId = portfolioId,
                    fundId = "fA",
                    name = "חיסכון",
                    amountAgoras = 100_00L,
                    scheduleKind = ScheduleKind.Monthly,
                    isEnabled = true,
                    note = null,
                    timeOfDayMinutes = 9 * 60,
                    weekdayMask = null,
                    dayOfMonth = 15,
                    nextRunAtUtc = null,
                    scheduledDepositId = null,
                )
            }
        }

        test("create OneTime sends a computed next-run and null recurring fields") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            coEvery {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AppResult.Success("new-id")
            val vm = buildVm()

            vm.onAddClick()
            vm.onFormNameChange("בונוס")
            vm.onFormAmountChange("1000")
            vm.onFormKindChange(ScheduleKind.OneTime)
            vm.onFormSave()

            // OneTime: recurring fields must be null and next_run_at_utc computed client-side
            // (E8.12) and therefore non-null. captureNullable matches the nullable arg and lets
            // us assert a value was actually supplied.
            val nextRunSlot = slot<Instant?>()
            coVerify(exactly = 1) {
                scheduledRepo.upsertScheduledDeposit(
                    portfolioId = portfolioId,
                    fundId = "fA",
                    name = "בונוס",
                    amountAgoras = 1_000_00L,
                    scheduleKind = ScheduleKind.OneTime,
                    isEnabled = true,
                    note = null,
                    timeOfDayMinutes = null,
                    weekdayMask = null,
                    dayOfMonth = null,
                    nextRunAtUtc = captureNullable(nextRunSlot),
                    scheduledDepositId = null,
                )
            }
            nextRunSlot.isCaptured shouldBe true
            vm.state.value.form shouldBe null
        }

        test("create surfaces server INVALID_SCHEDULE_FIELDS in the form") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(emptyList())
            coEvery { fundRepo.getFunds(portfolioId) } returns AppResult.Success(funds)
            coEvery {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AppResult.Failure(AppError.Validation.InvalidScheduleFields)
            val vm = buildVm()

            vm.onAddClick()
            vm.onFormNameChange("חיסכון")
            vm.onFormAmountChange("100")
            vm.onFormSave()

            vm.state.value.form?.errorMessageRes shouldBe R.string.sd_error_invalid_schedule_fields
        }

        // ── Enable / disable (E8.7 upsert with flipped flag) ──────────────────

        test("toggle on a recurring deposit flips isEnabled and clears next-run for server recompute") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(listOf(dailyItem))
            coEvery {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AppResult.Success("sd1")
            val vm = buildVm()

            vm.onToggleEnabled(dailyItem)

            coVerify(exactly = 1) {
                scheduledRepo.upsertScheduledDeposit(
                    portfolioId = portfolioId,
                    fundId = "fB",
                    name = "חיסכון",
                    amountAgoras = 500_00L,
                    scheduleKind = ScheduleKind.Daily,
                    isEnabled = false, // was true
                    note = "הערה",
                    timeOfDayMinutes = 600,
                    weekdayMask = null,
                    dayOfMonth = null,
                    nextRunAtUtc = null, // recurring → server recomputes
                    scheduledDepositId = "sd1",
                )
            }
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation(portfolioId) }
        }

        test("toggle on a OneTime deposit preserves its next-run") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(listOf(oneTimeItem))
            coEvery {
                scheduledRepo.upsertScheduledDeposit(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AppResult.Success("sd2")
            val vm = buildVm()

            vm.onToggleEnabled(oneTimeItem)

            coVerify(exactly = 1) {
                scheduledRepo.upsertScheduledDeposit(
                    portfolioId = portfolioId,
                    fundId = "fA",
                    name = "בונוס",
                    amountAgoras = 1_000_00L,
                    scheduleKind = ScheduleKind.OneTime,
                    isEnabled = false,
                    note = null,
                    timeOfDayMinutes = null,
                    weekdayMask = null,
                    dayOfMonth = null,
                    nextRunAtUtc = oneTimeItem.nextRunAtUtc, // preserved for OneTime
                    scheduledDepositId = "sd2",
                )
            }
        }

        // ── Delete (section 4.2 confirmation) ─────────────────────────────────

        test("delete click opens the confirmation; confirming deletes and refreshes") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(listOf(dailyItem))
            coEvery { scheduledRepo.deleteScheduledDeposit("sd1") } returns AppResult.Success(Unit)
            val vm = buildVm()

            vm.onDeleteClick(dailyItem)
            vm.state.value.confirmDelete?.scheduledDepositId shouldBe "sd1"

            vm.onConfirmDelete()

            coVerify(exactly = 1) { scheduledRepo.deleteScheduledDeposit("sd1") }
            vm.state.value.confirmDelete shouldBe null
            coVerify(exactly = 1) { syncCoordinator.triggerPostMutation(portfolioId) }
        }

        test("dismissing the delete confirmation does not call the repository") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(listOf(dailyItem))
            val vm = buildVm()

            vm.onDeleteClick(dailyItem)
            vm.onDismissDelete()

            vm.state.value.confirmDelete shouldBe null
            coVerify(exactly = 0) { scheduledRepo.deleteScheduledDeposit(any()) }
        }

        // ── Sync events ───────────────────────────────────────────────────────

        test("PostMutation for this portfolio refreshes the list") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returnsMany listOf(
                AppResult.Success(emptyList()),
                AppResult.Success(listOf(dailyItem)),
            )
            val vm = buildVm()

            syncEvents.emit(SyncTrigger.PostMutation(portfolioId))

            vm.state.value.items.size shouldBe 1
        }

        test("PostMutation for a different portfolio is ignored") {
            coEvery { scheduledRepo.getScheduledDeposits(portfolioId) } returns AppResult.Success(listOf(dailyItem))
            val vm = buildVm()

            syncEvents.emit(SyncTrigger.PostMutation("other"))

            coVerify(exactly = 1) { scheduledRepo.getScheduledDeposits(portfolioId) }
        }
    }

    private fun buildVm(): ScheduledDepositsViewModel {
        val savedState = SavedStateHandle(
            mapOf(
                "portfolioId" to portfolioId,
                "portfolioName" to portfolioName,
            ),
        )
        return ScheduledDepositsViewModel(scheduledRepo, fundRepo, syncCoordinator, savedState)
    }
}
