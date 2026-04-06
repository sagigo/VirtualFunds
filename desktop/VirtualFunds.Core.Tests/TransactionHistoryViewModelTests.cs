using NSubstitute;
using NSubstitute.ExceptionExtensions;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.WPF.ViewModels;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="TransactionHistoryViewModel"/>, focusing on the history-based
/// undo feature (E6.12) and the <c>ComputeUndoability</c> logic.
/// </summary>
public class TransactionHistoryViewModelTests
{
    private static readonly Guid PortfolioId = Guid.NewGuid();

    private readonly ITransactionService _transactionService = Substitute.For<ITransactionService>();
    private readonly IFundService _fundService = Substitute.For<IFundService>();

    private TransactionHistoryViewModel MakeVm()
    {
        return new TransactionHistoryViewModel(_transactionService, _fundService, PortfolioId);
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /// <summary>Wires ConfirmationRequested to return a predetermined answer.</summary>
    private static void StubConfirmation(TransactionHistoryViewModel vm, bool confirmed)
    {
        vm.ConfirmationRequested += _ => Task.FromResult(confirmed);
    }

    /// <summary>Creates a <see cref="TransactionGroup"/> for testing.</summary>
    private static TransactionGroup MakeGroup(
        string type = "FundDeposit",
        Guid? operationId = null,
        Guid? undoOfOperationId = null,
        bool isUndoable = false) => new()
    {
        OperationId = operationId ?? Guid.NewGuid(),
        CommittedAtUtc = DateTime.UtcNow,
        TransactionType = type,
        UndoOfOperationId = undoOfOperationId,
        IsUndoable = isUndoable,
        SummaryText = $"Test {type}",
        AmountAgoras = 10000,
        Details = Array.Empty<TransactionDetailItem>(),
    };

    // -----------------------------------------------------------------------------------------
    // UndoOperation command
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task UndoOperation_CallsServiceWithCorrectIds()
    {
        var group = MakeGroup(isUndoable: true);
        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.UndoOperationCommand.ExecuteAsync(group);

        await _fundService.Received(1).UndoOperationAsync(PortfolioId, group.OperationId);
    }

    [Fact]
    public async Task UndoOperation_NotUndoable_DoesNotCallService()
    {
        var group = MakeGroup(isUndoable: false);
        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.UndoOperationCommand.ExecuteAsync(group);

        await _fundService.DidNotReceive().UndoOperationAsync(Arg.Any<Guid>(), Arg.Any<Guid>());
    }

    [Fact]
    public async Task UndoOperation_UserCancelled_DoesNotCallService()
    {
        var group = MakeGroup(isUndoable: true);
        var vm = MakeVm();
        StubConfirmation(vm, false);

        await vm.UndoOperationCommand.ExecuteAsync(group);

        await _fundService.DidNotReceive().UndoOperationAsync(Arg.Any<Guid>(), Arg.Any<Guid>());
    }

    [Fact]
    public async Task UndoOperation_NegativeBalance_SetsHebrewError()
    {
        var group = MakeGroup(isUndoable: true);
        _fundService.UndoOperationAsync(PortfolioId, group.OperationId)
            .ThrowsAsync(new InsufficientFundBalanceException());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.UndoOperationCommand.ExecuteAsync(group);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task UndoOperation_ClosedPortfolio_SetsHebrewError()
    {
        var group = MakeGroup(isUndoable: true);
        _fundService.UndoOperationAsync(PortfolioId, group.OperationId)
            .ThrowsAsync(new PortfolioClosedException());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.UndoOperationCommand.ExecuteAsync(group);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task UndoOperation_FundNotFound_SetsHebrewError()
    {
        var group = MakeGroup(isUndoable: true);
        _fundService.UndoOperationAsync(PortfolioId, group.OperationId)
            .ThrowsAsync(new FundNotFoundException());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.UndoOperationCommand.ExecuteAsync(group);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task UndoOperation_Success_RaisesUndoCompletedEvent()
    {
        var group = MakeGroup(isUndoable: true);
        var vm = MakeVm();
        StubConfirmation(vm, true);

        var eventRaised = false;
        vm.UndoCompleted += () =>
        {
            eventRaised = true;
            return Task.CompletedTask;
        };

        await vm.UndoOperationCommand.ExecuteAsync(group);

        Assert.True(eventRaised);
    }

    // -----------------------------------------------------------------------------------------
    // ComputeUndoability (tested indirectly through LoadHistoryAsync)
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task LoadHistory_UndoableTypes_MarkedTrue()
    {
        var deposit = MakeGroup("FundDeposit");
        var withdrawal = MakeGroup("FundWithdrawal");
        var transfer = MakeGroup("Transfer");
        var revalue = MakeGroup("PortfolioRevalued");

        _transactionService.GetHistoryAsync(PortfolioId)
            .Returns(new List<TransactionGroup> { deposit, withdrawal, transfer, revalue });
        _transactionService.GetFundFilterOptionsAsync(PortfolioId)
            .Returns(new List<FundFilterOption>());

        var vm = MakeVm();
        await vm.LoadHistoryCommand.ExecuteAsync(null);

        Assert.True(deposit.IsUndoable);
        Assert.True(withdrawal.IsUndoable);
        Assert.True(transfer.IsUndoable);
        Assert.True(revalue.IsUndoable);
    }

    [Fact]
    public async Task LoadHistory_AlreadyUndone_MarkedFalse()
    {
        var originalOp = Guid.NewGuid();
        var deposit = MakeGroup("FundDeposit", operationId: originalOp);
        var undo = MakeGroup("Undo", undoOfOperationId: originalOp);

        _transactionService.GetHistoryAsync(PortfolioId)
            .Returns(new List<TransactionGroup> { undo, deposit });
        _transactionService.GetFundFilterOptionsAsync(PortfolioId)
            .Returns(new List<FundFilterOption>());

        var vm = MakeVm();
        await vm.LoadHistoryCommand.ExecuteAsync(null);

        Assert.False(deposit.IsUndoable);
    }

    [Theory]
    [InlineData("FundCreated")]
    [InlineData("FundRenamed")]
    [InlineData("FundDeleted")]
    [InlineData("PortfolioCreated")]
    [InlineData("PortfolioRenamed")]
    [InlineData("PortfolioClosed")]
    public async Task LoadHistory_StructuralOps_MarkedFalse(string transactionType)
    {
        var group = MakeGroup(transactionType);

        _transactionService.GetHistoryAsync(PortfolioId)
            .Returns(new List<TransactionGroup> { group });
        _transactionService.GetFundFilterOptionsAsync(PortfolioId)
            .Returns(new List<FundFilterOption>());

        var vm = MakeVm();
        await vm.LoadHistoryCommand.ExecuteAsync(null);

        Assert.False(group.IsUndoable);
    }

    [Fact]
    public async Task LoadHistory_UndoType_MarkedFalse()
    {
        var group = MakeGroup("Undo");

        _transactionService.GetHistoryAsync(PortfolioId)
            .Returns(new List<TransactionGroup> { group });
        _transactionService.GetFundFilterOptionsAsync(PortfolioId)
            .Returns(new List<FundFilterOption>());

        var vm = MakeVm();
        await vm.LoadHistoryCommand.ExecuteAsync(null);

        Assert.False(group.IsUndoable);
    }

    [Fact]
    public async Task LoadHistory_ScheduledDeposit_MarkedFalse()
    {
        var group = MakeGroup("ScheduledDepositExecuted");

        _transactionService.GetHistoryAsync(PortfolioId)
            .Returns(new List<TransactionGroup> { group });
        _transactionService.GetFundFilterOptionsAsync(PortfolioId)
            .Returns(new List<FundFilterOption>());

        var vm = MakeVm();
        await vm.LoadHistoryCommand.ExecuteAsync(null);

        Assert.False(group.IsUndoable);
    }
}
