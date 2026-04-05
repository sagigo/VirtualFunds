using NSubstitute;
using NSubstitute.ExceptionExtensions;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.WPF.ViewModels;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="PortfolioViewModel"/>.
/// <see cref="IFundService"/> is mocked with NSubstitute.
/// </summary>
public class PortfolioViewModelTests
{
    private static readonly Guid PortfolioId = Guid.NewGuid();
    private const string PortfolioName = "תיק חיסכון";

    private readonly IFundService _fundService = Substitute.For<IFundService>();
    private readonly IPortfolioService _portfolioService = Substitute.For<IPortfolioService>();
    private readonly ITransactionService _transactionService = Substitute.For<ITransactionService>();

    private PortfolioViewModel MakeVm()
    {
        var historyVm = new TransactionHistoryViewModel(_transactionService, PortfolioId);
        return new PortfolioViewModel(_fundService, _portfolioService, PortfolioId, PortfolioName, historyVm);
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /// <summary>Wires the NameInputRequested event to return a predetermined name.</summary>
    private static void StubNameInput(PortfolioViewModel vm, string? nameToReturn)
    {
        vm.NameInputRequested += (_, _) => Task.FromResult(nameToReturn);
    }

    /// <summary>Wires the FundCreateRequested event to return a predetermined result.</summary>
    private static void StubFundCreate(PortfolioViewModel vm, (string Name, long AmountAgoras)? result)
    {
        vm.FundCreateRequested += () => Task.FromResult(result);
    }

    /// <summary>Wires the ConfirmationRequested event to return a predetermined answer.</summary>
    private static void StubConfirmation(PortfolioViewModel vm, bool confirmed)
    {
        vm.ConfirmationRequested += _ => Task.FromResult(confirmed);
    }

    /// <summary>Wires the AmountInputRequested event to return a predetermined amount.</summary>
    private static void StubAmountInput(PortfolioViewModel vm, long? amountAgoras)
    {
        vm.AmountInputRequested += (_, _) => Task.FromResult(amountAgoras);
    }

    /// <summary>Creates a sample fund list item for testing.</summary>
    private static FundListItem MakeItem(string name = "קרן א", long balance = 10000, double allocation = 50.0) => new()
    {
        FundId = Guid.NewGuid(),
        Name = name,
        BalanceAgoras = balance,
        AllocationPercent = allocation,
        CreatedAtUtc = DateTime.UtcNow,
    };

    // -----------------------------------------------------------------------------------------
    // LoadFunds
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task LoadFunds_Success_PopulatesFundsList()
    {
        var items = new List<FundListItem> { MakeItem("א"), MakeItem("ב") };
        _fundService.GetFundsAsync(PortfolioId).Returns(items);

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        Assert.Equal(2, vm.Funds.Count);
    }

    [Fact]
    public async Task LoadFunds_EmptyList_SetsIsEmptyTrue()
    {
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem>());

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        Assert.True(vm.IsEmpty);
    }

    [Fact]
    public async Task LoadFunds_NonEmptyList_SetsIsEmptyFalse()
    {
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem> { MakeItem() });

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        Assert.False(vm.IsEmpty);
    }

    [Fact]
    public async Task LoadFunds_Failure_SetsHebrewErrorMessage()
    {
        _fundService.GetFundsAsync(PortfolioId)
            .ThrowsAsync(new InvalidOperationException("network error"));

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task LoadFunds_IsLoadingFalse_AfterCompletion()
    {
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem>());

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        Assert.False(vm.IsLoading);
    }

    [Fact]
    public async Task LoadFunds_ComputesFormattedTotal()
    {
        var items = new List<FundListItem>
        {
            MakeItem(balance: 10000), // 100.00 ₪
            MakeItem(balance: 5000),  // 50.00 ₪
        };
        _fundService.GetFundsAsync(PortfolioId).Returns(items);

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        Assert.Contains("150.00", vm.FormattedTotal);
    }

    // -----------------------------------------------------------------------------------------
    // CreateFund
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task CreateFund_Success_ReloadsFundList()
    {
        _fundService.CreateFundAsync(PortfolioId, "חדשה", 0).Returns(Guid.NewGuid());
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem> { MakeItem("חדשה") });

        var vm = MakeVm();
        StubFundCreate(vm, ("חדשה", 0));
        StubConfirmation(vm, true);

        await vm.CreateFundCommand.ExecuteAsync(null);

        await _fundService.Received(1).CreateFundAsync(PortfolioId, "חדשה", 0);
        Assert.Single(vm.Funds);
    }

    [Fact]
    public async Task CreateFund_WithAmount_PassesAmountToService()
    {
        _fundService.CreateFundAsync(PortfolioId, "חדשה", 15050).Returns(Guid.NewGuid());
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem> { MakeItem("חדשה", 15050) });

        var vm = MakeVm();
        StubFundCreate(vm, ("חדשה", 15050));
        StubConfirmation(vm, true);

        await vm.CreateFundCommand.ExecuteAsync(null);

        await _fundService.Received(1).CreateFundAsync(PortfolioId, "חדשה", 15050);
    }

    [Fact]
    public async Task CreateFund_Cancelled_DoesNotCallService()
    {
        var vm = MakeVm();
        StubFundCreate(vm, null);

        await vm.CreateFundCommand.ExecuteAsync(null);

        await _fundService.DidNotReceive().CreateFundAsync(Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>());
    }

    [Fact]
    public async Task CreateFund_EmptyName_SetsHebrewErrorMessage()
    {
        _fundService.CreateFundAsync(Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>())
            .ThrowsAsync(new EmptyFundNameException());

        var vm = MakeVm();
        StubFundCreate(vm, ("", 0));
        StubConfirmation(vm, true);

        await vm.CreateFundCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task CreateFund_DuplicateName_SetsHebrewErrorMessage()
    {
        _fundService.CreateFundAsync(Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>())
            .ThrowsAsync(new DuplicateFundNameException());

        var vm = MakeVm();
        StubFundCreate(vm, ("כפולה", 0));
        StubConfirmation(vm, true);

        await vm.CreateFundCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task CreateFund_NegativeAmount_SetsHebrewErrorMessage()
    {
        _fundService.CreateFundAsync(Arg.Any<Guid>(), Arg.Any<string>(), Arg.Any<long>())
            .ThrowsAsync(new NegativeFundAmountException());

        var vm = MakeVm();
        StubFundCreate(vm, ("קרן", -100));
        StubConfirmation(vm, true);

        await vm.CreateFundCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // RenameFund
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task RenameFund_Success_ReloadsFundList()
    {
        var item = MakeItem("ישנה");
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem> { MakeItem("חדשה") });

        var vm = MakeVm();
        StubNameInput(vm, "חדשה");
        StubConfirmation(vm, true);

        await vm.RenameFundCommand.ExecuteAsync(item);

        await _fundService.Received(1).RenameFundAsync(PortfolioId, item.FundId, "חדשה");
    }

    [Fact]
    public async Task RenameFund_Cancelled_DoesNotCallService()
    {
        var vm = MakeVm();
        StubNameInput(vm, null);

        await vm.RenameFundCommand.ExecuteAsync(MakeItem());

        await _fundService.DidNotReceive()
            .RenameFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>());
    }

    [Fact]
    public async Task RenameFund_EmptyName_SetsHebrewErrorMessage()
    {
        _fundService.RenameFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>())
            .ThrowsAsync(new EmptyFundNameException());

        var vm = MakeVm();
        StubNameInput(vm, "");
        StubConfirmation(vm, true);

        await vm.RenameFundCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task RenameFund_DuplicateName_SetsHebrewErrorMessage()
    {
        _fundService.RenameFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>())
            .ThrowsAsync(new DuplicateFundNameException());

        var vm = MakeVm();
        StubNameInput(vm, "כפולה");
        StubConfirmation(vm, true);

        await vm.RenameFundCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task RenameFund_ClosedPortfolio_SetsHebrewErrorMessage()
    {
        _fundService.RenameFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>())
            .ThrowsAsync(new PortfolioClosedException());

        var vm = MakeVm();
        StubNameInput(vm, "שם חדש");
        StubConfirmation(vm, true);

        await vm.RenameFundCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task RenameFund_NotFound_SetsHebrewErrorMessage()
    {
        _fundService.RenameFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>(), Arg.Any<string>())
            .ThrowsAsync(new FundNotFoundException());

        var vm = MakeVm();
        StubNameInput(vm, "שם חדש");
        StubConfirmation(vm, true);

        await vm.RenameFundCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // DeleteFund
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task DeleteFund_Confirmed_CallsServiceAndReloads()
    {
        var item = MakeItem();
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem>());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.DeleteFundCommand.ExecuteAsync(item);

        await _fundService.Received(1).DeleteFundAsync(PortfolioId, item.FundId);
    }

    [Fact]
    public async Task DeleteFund_NotConfirmed_DoesNotCallService()
    {
        var vm = MakeVm();
        StubConfirmation(vm, false);

        await vm.DeleteFundCommand.ExecuteAsync(MakeItem());

        await _fundService.DidNotReceive().DeleteFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>());
    }

    [Fact]
    public async Task DeleteFund_FundNotEmpty_SetsHebrewErrorMessage()
    {
        _fundService.DeleteFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>())
            .ThrowsAsync(new FundNotEmptyException());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.DeleteFundCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task DeleteFund_HasScheduledDeposit_SetsHebrewErrorMessage()
    {
        _fundService.DeleteFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>())
            .ThrowsAsync(new FundHasScheduledDepositException());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.DeleteFundCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task DeleteFund_ClosedPortfolio_SetsHebrewErrorMessage()
    {
        _fundService.DeleteFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>())
            .ThrowsAsync(new PortfolioClosedException());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.DeleteFundCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task DeleteFund_NotFound_SetsHebrewErrorMessage()
    {
        _fundService.DeleteFundAsync(Arg.Any<Guid>(), Arg.Any<Guid>())
            .ThrowsAsync(new FundNotFoundException());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.DeleteFundCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // RevaluePortfolio
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task RevaluePortfolio_Success_CallsServiceAndReloads()
    {
        var funds = new List<FundListItem> { MakeItem("א", 10000, 100.0) };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null); // pre-load funds into Funds collection

        StubAmountInput(vm, 20000);
        StubConfirmation(vm, true);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        await _fundService.Received(1).RevaluePortfolioAsync(PortfolioId, 20000);
    }

    [Fact]
    public async Task RevaluePortfolio_NoFunds_SetsHebrewErrorMessage_DoesNotCallService()
    {
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem>());
        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null); // loads empty list

        StubAmountInput(vm, 20000);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
        await _fundService.DidNotReceive().RevaluePortfolioAsync(Arg.Any<Guid>(), Arg.Any<long>());
    }

    [Fact]
    public async Task RevaluePortfolio_Cancelled_DoesNotCallService()
    {
        var funds = new List<FundListItem> { MakeItem() };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        StubAmountInput(vm, null); // user cancelled dialog

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        await _fundService.DidNotReceive().RevaluePortfolioAsync(Arg.Any<Guid>(), Arg.Any<long>());
    }

    [Fact]
    public async Task RevaluePortfolio_ConfirmationDeclined_DoesNotCallService()
    {
        var funds = new List<FundListItem> { MakeItem() };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        StubAmountInput(vm, 20000);
        StubConfirmation(vm, false);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        await _fundService.DidNotReceive().RevaluePortfolioAsync(Arg.Any<Guid>(), Arg.Any<long>());
    }

    [Fact]
    public async Task RevaluePortfolio_PortfolioTotalIsZero_SetsHebrewErrorMessage()
    {
        var funds = new List<FundListItem> { MakeItem() };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        _fundService.RevaluePortfolioAsync(PortfolioId, Arg.Any<long>())
            .ThrowsAsync(new PortfolioTotalIsZeroException());

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);
        StubAmountInput(vm, 20000);
        StubConfirmation(vm, true);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task RevaluePortfolio_NegativeAmount_SetsHebrewErrorMessage()
    {
        var funds = new List<FundListItem> { MakeItem() };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        _fundService.RevaluePortfolioAsync(PortfolioId, Arg.Any<long>())
            .ThrowsAsync(new NegativeFundAmountException());

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);
        StubAmountInput(vm, 20000);
        StubConfirmation(vm, true);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task RevaluePortfolio_ClosedPortfolio_SetsHebrewErrorMessage()
    {
        var funds = new List<FundListItem> { MakeItem() };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        _fundService.RevaluePortfolioAsync(PortfolioId, Arg.Any<long>())
            .ThrowsAsync(new PortfolioClosedException());

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);
        StubAmountInput(vm, 20000);
        StubConfirmation(vm, true);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task RevaluePortfolio_TotalMismatch_SetsHebrewErrorMessage()
    {
        var funds = new List<FundListItem> { MakeItem() };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        _fundService.RevaluePortfolioAsync(PortfolioId, Arg.Any<long>())
            .ThrowsAsync(new TotalMismatchException());

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);
        StubAmountInput(vm, 20000);
        StubConfirmation(vm, true);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task RevaluePortfolio_WouldZeroFund_SetsHebrewErrorMessage()
    {
        var funds = new List<FundListItem> { MakeItem() };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        _fundService.RevaluePortfolioAsync(PortfolioId, Arg.Any<long>())
            .ThrowsAsync(new RevalueWouldZeroFundException());

        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);
        StubAmountInput(vm, 1);
        StubConfirmation(vm, true);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task RevaluePortfolio_IsLoadingFalse_AfterCompletion()
    {
        var funds = new List<FundListItem> { MakeItem() };
        _fundService.GetFundsAsync(PortfolioId).Returns(funds);
        var vm = MakeVm();
        await vm.LoadFundsCommand.ExecuteAsync(null);

        StubAmountInput(vm, 20000);
        StubConfirmation(vm, true);

        await vm.RevaluePortfolioCommand.ExecuteAsync(null);

        Assert.False(vm.IsLoading);
    }

    // -----------------------------------------------------------------------------------------
    // GoBack
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void GoBack_RaisesBackRequestedEvent()
    {
        var vm = MakeVm();
        var backRaised = false;
        vm.BackRequested += () => backRaised = true;

        vm.GoBackCommand.Execute(null);

        Assert.True(backRaised);
    }

    // -----------------------------------------------------------------------------------------
    // Error clearing
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task LoadFunds_ClearsErrorMessage_BeforeLoad()
    {
        _fundService.GetFundsAsync(PortfolioId).Returns(new List<FundListItem>());

        var vm = MakeVm();
        vm.ErrorMessage = "old error";

        await vm.LoadFundsCommand.ExecuteAsync(null);

        Assert.Empty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void Constructor_SetsPortfolioNameAndId()
    {
        var vm = MakeVm();

        Assert.Equal(PortfolioName, vm.PortfolioName);
        Assert.Equal(PortfolioId, vm.PortfolioId);
    }
}
