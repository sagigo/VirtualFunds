using NSubstitute;
using NSubstitute.ExceptionExtensions;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.WPF.ViewModels;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="ScheduledDepositsViewModel"/> (PR-8, E8).
/// Services are mocked with NSubstitute.
/// </summary>
public class ScheduledDepositsViewModelTests
{
    private static readonly Guid PortfolioId = Guid.NewGuid();

    private readonly IScheduledDepositService _sdService = Substitute.For<IScheduledDepositService>();
    private readonly IFundService _fundService = Substitute.For<IFundService>();

    private ScheduledDepositsViewModel MakeVm() =>
        new(_sdService, _fundService, PortfolioId);

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private static ScheduledDepositListItem MakeDeposit(
        string name = "הפקדה א",
        bool isEnabled = true,
        string scheduleKind = "Daily") => new()
    {
        ScheduledDepositId = Guid.NewGuid(),
        PortfolioId = PortfolioId,
        FundId = Guid.NewGuid(),
        Name = name,
        IsEnabled = isEnabled,
        AmountAgoras = 10000,
        ScheduleKind = scheduleKind,
        TimeOfDayMinutes = scheduleKind == "OneTime" ? null : 540,
        NextRunAtUtc = DateTime.UtcNow.AddHours(1),
        FundName = "קרן א",
    };

    private static FundListItem MakeFund(string name = "קרן א") => new()
    {
        FundId = Guid.NewGuid(),
        Name = name,
        BalanceAgoras = 10000,
        AllocationPercent = 100.0,
        CreatedAtUtc = DateTime.UtcNow,
    };

    private static void StubForm(ScheduledDepositsViewModel vm, ScheduledDepositFormResult? result)
    {
        vm.DepositFormRequested += (_, _) => Task.FromResult(result);
    }

    private static void StubConfirmation(ScheduledDepositsViewModel vm, bool confirmed)
    {
        vm.ConfirmationRequested += _ => Task.FromResult(confirmed);
    }

    // -----------------------------------------------------------------------------------------
    // LoadScheduledDeposits
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task LoadScheduledDeposits_Success_PopulatesList()
    {
        var items = new List<ScheduledDepositListItem> { MakeDeposit("א"), MakeDeposit("ב") };
        _sdService.GetScheduledDepositsAsync(PortfolioId).Returns(items);

        var vm = MakeVm();
        await vm.LoadScheduledDepositsCommand.ExecuteAsync(null);

        Assert.Equal(2, vm.ScheduledDeposits.Count);
        Assert.False(vm.IsEmpty);
        Assert.Empty(vm.ErrorMessage);
    }

    [Fact]
    public async Task LoadScheduledDeposits_EmptyResult_SetsIsEmpty()
    {
        _sdService.GetScheduledDepositsAsync(PortfolioId)
            .Returns(Array.Empty<ScheduledDepositListItem>());

        var vm = MakeVm();
        await vm.LoadScheduledDepositsCommand.ExecuteAsync(null);

        Assert.Empty(vm.ScheduledDeposits);
        Assert.True(vm.IsEmpty);
    }

    [Fact]
    public async Task LoadScheduledDeposits_ServiceThrows_SetsErrorMessage()
    {
        _sdService.GetScheduledDepositsAsync(PortfolioId)
            .ThrowsAsync(new Exception("network error"));

        var vm = MakeVm();
        await vm.LoadScheduledDepositsCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // CreateScheduledDeposit
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task CreateScheduledDeposit_UserCancels_NoServiceCall()
    {
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem> { MakeFund() });
        _sdService.GetScheduledDepositsAsync(PortfolioId)
            .Returns(Array.Empty<ScheduledDepositListItem>());

        var vm = MakeVm();
        StubForm(vm, null); // User cancels.

        await vm.CreateScheduledDepositCommand.ExecuteAsync(null);

        await _sdService.DidNotReceive().UpsertScheduledDepositAsync(
            Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>(),
            Arg.Any<string>(), Arg.Any<bool>(), Arg.Any<string?>(),
            Arg.Any<int?>(), Arg.Any<int?>(), Arg.Any<int?>(),
            Arg.Any<DateTime?>(), Arg.Any<Guid?>());
    }

    [Fact]
    public async Task CreateScheduledDeposit_Success_CallsServiceAndReloads()
    {
        var fund = MakeFund();
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem> { fund });
        _sdService.GetScheduledDepositsAsync(PortfolioId)
            .Returns(Array.Empty<ScheduledDepositListItem>());
        _sdService.UpsertScheduledDepositAsync(
            Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>(),
            Arg.Any<string>(), Arg.Any<bool>(), Arg.Any<string?>(),
            Arg.Any<int?>(), Arg.Any<int?>(), Arg.Any<int?>(),
            Arg.Any<DateTime?>(), Arg.Any<Guid?>())
            .Returns(Guid.NewGuid());

        var formResult = new ScheduledDepositFormResult(
            Name: "הפקדה חדשה",
            FundId: fund.FundId,
            AmountAgoras: 5000,
            ScheduleKind: "Daily",
            IsEnabled: true,
            Note: null,
            TimeOfDayMinutes: 540,
            WeekdayMask: null,
            DayOfMonth: null,
            NextRunAtUtc: null);

        var vm = MakeVm();
        StubForm(vm, formResult);

        await vm.CreateScheduledDepositCommand.ExecuteAsync(null);

        await _sdService.Received(1).UpsertScheduledDepositAsync(
            PortfolioId, fund.FundId, "הפקדה חדשה", 5000,
            "Daily", true, null, 540, null, null, null, null);
        Assert.Empty(vm.ErrorMessage);
    }

    [Fact]
    public async Task CreateScheduledDeposit_InvalidScheduleFields_SetsHebrewError()
    {
        var fund = MakeFund();
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem> { fund });
        _sdService.UpsertScheduledDepositAsync(
            Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>(),
            Arg.Any<string>(), Arg.Any<bool>(), Arg.Any<string?>(),
            Arg.Any<int?>(), Arg.Any<int?>(), Arg.Any<int?>(),
            Arg.Any<DateTime?>(), Arg.Any<Guid?>())
            .ThrowsAsync(new InvalidScheduleFieldsException());

        var formResult = new ScheduledDepositFormResult(
            "test", fund.FundId, 5000, "Daily", true, null, 540, null, null, null);

        var vm = MakeVm();
        StubForm(vm, formResult);

        await vm.CreateScheduledDepositCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // DeleteScheduledDeposit
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task DeleteScheduledDeposit_UserDeclines_NoServiceCall()
    {
        var deposit = MakeDeposit();
        var vm = MakeVm();
        StubConfirmation(vm, false);

        await vm.DeleteScheduledDepositCommand.ExecuteAsync(deposit);

        await _sdService.DidNotReceive().DeleteScheduledDepositAsync(Arg.Any<Guid>());
    }

    [Fact]
    public async Task DeleteScheduledDeposit_Success_CallsServiceAndReloads()
    {
        var deposit = MakeDeposit();
        _sdService.GetScheduledDepositsAsync(PortfolioId)
            .Returns(Array.Empty<ScheduledDepositListItem>());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.DeleteScheduledDepositCommand.ExecuteAsync(deposit);

        await _sdService.Received(1).DeleteScheduledDepositAsync(deposit.ScheduledDepositId);
        Assert.Empty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // ToggleEnabled
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task ToggleEnabled_CallsUpsertWithFlippedEnabled()
    {
        var deposit = MakeDeposit(isEnabled: true);
        _sdService.GetScheduledDepositsAsync(PortfolioId)
            .Returns(Array.Empty<ScheduledDepositListItem>());
        _sdService.UpsertScheduledDepositAsync(
            Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>(),
            Arg.Any<string>(), Arg.Any<bool>(), Arg.Any<string?>(),
            Arg.Any<int?>(), Arg.Any<int?>(), Arg.Any<int?>(),
            Arg.Any<DateTime?>(), Arg.Any<Guid?>())
            .Returns(deposit.ScheduledDepositId);

        var vm = MakeVm();
        await vm.ToggleEnabledCommand.ExecuteAsync(deposit);

        // Verify the enabled flag was flipped (true → false).
        await _sdService.Received(1).UpsertScheduledDepositAsync(
            PortfolioId, deposit.FundId, deposit.Name, deposit.AmountAgoras,
            deposit.ScheduleKind, false, // flipped from true to false
            deposit.Note, deposit.TimeOfDayMinutes, deposit.WeekdayMask,
            deposit.DayOfMonth, Arg.Any<DateTime?>(), deposit.ScheduledDepositId);
    }

    [Fact]
    public async Task ToggleEnabled_DisabledToEnabled_FlipsCorrectly()
    {
        var deposit = MakeDeposit(isEnabled: false);
        _sdService.GetScheduledDepositsAsync(PortfolioId)
            .Returns(Array.Empty<ScheduledDepositListItem>());
        _sdService.UpsertScheduledDepositAsync(
            Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>(),
            Arg.Any<string>(), Arg.Any<bool>(), Arg.Any<string?>(),
            Arg.Any<int?>(), Arg.Any<int?>(), Arg.Any<int?>(),
            Arg.Any<DateTime?>(), Arg.Any<Guid?>())
            .Returns(deposit.ScheduledDepositId);

        var vm = MakeVm();
        await vm.ToggleEnabledCommand.ExecuteAsync(deposit);

        // Verify the enabled flag was flipped (false → true).
        await _sdService.Received(1).UpsertScheduledDepositAsync(
            PortfolioId, deposit.FundId, deposit.Name, deposit.AmountAgoras,
            deposit.ScheduleKind, true, // flipped from false to true
            deposit.Note, deposit.TimeOfDayMinutes, deposit.WeekdayMask,
            deposit.DayOfMonth, Arg.Any<DateTime?>(), deposit.ScheduledDepositId);
    }
}
