using NSubstitute;
using NSubstitute.ExceptionExtensions;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.WPF.ViewModels;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="MainViewModel"/>.
/// <see cref="IPortfolioService"/> and <see cref="IAuthService"/> are mocked with NSubstitute.
/// </summary>
public class MainViewModelTests
{
    private readonly IPortfolioService _portfolioService = Substitute.For<IPortfolioService>();
    private readonly IAuthService _authService = Substitute.For<IAuthService>();

    private MainViewModel MakeVm() => new(_portfolioService, _authService);

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /// <summary>Wires the NameInputRequested event to return a predetermined name.</summary>
    private static void StubNameInput(MainViewModel vm, string? nameToReturn)
    {
        vm.NameInputRequested += (_, _) => Task.FromResult(nameToReturn);
    }

    /// <summary>Wires the ConfirmationRequested event to return a predetermined answer.</summary>
    private static void StubConfirmation(MainViewModel vm, bool confirmed)
    {
        vm.ConfirmationRequested += _ => Task.FromResult(confirmed);
    }

    /// <summary>Creates a sample portfolio list item for testing.</summary>
    private static PortfolioListItem MakeItem(string name = "תיק חיסכון", long total = 0) => new()
    {
        PortfolioId = Guid.NewGuid(),
        Name = name,
        TotalBalanceAgoras = total,
    };

    // -----------------------------------------------------------------------------------------
    // LoadPortfolios
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task LoadPortfolios_Success_PopulatesPortfoliosList()
    {
        var items = new List<PortfolioListItem> { MakeItem("א"), MakeItem("ב") };
        _portfolioService.GetActivePortfoliosAsync().Returns(items);

        var vm = MakeVm();
        await vm.LoadPortfoliosCommand.ExecuteAsync(null);

        Assert.Equal(2, vm.Portfolios.Count);
    }

    [Fact]
    public async Task LoadPortfolios_EmptyList_SetsIsEmptyTrue()
    {
        _portfolioService.GetActivePortfoliosAsync().Returns(new List<PortfolioListItem>());

        var vm = MakeVm();
        await vm.LoadPortfoliosCommand.ExecuteAsync(null);

        Assert.True(vm.IsEmpty);
    }

    [Fact]
    public async Task LoadPortfolios_NonEmptyList_SetsIsEmptyFalse()
    {
        _portfolioService.GetActivePortfoliosAsync().Returns(new List<PortfolioListItem> { MakeItem() });

        var vm = MakeVm();
        await vm.LoadPortfoliosCommand.ExecuteAsync(null);

        Assert.False(vm.IsEmpty);
    }

    [Fact]
    public async Task LoadPortfolios_Failure_SetsHebrewErrorMessage()
    {
        _portfolioService.GetActivePortfoliosAsync()
            .ThrowsAsync(new InvalidOperationException("network error"));

        var vm = MakeVm();
        await vm.LoadPortfoliosCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task LoadPortfolios_IsLoadingFalse_AfterCompletion()
    {
        _portfolioService.GetActivePortfoliosAsync().Returns(new List<PortfolioListItem>());

        var vm = MakeVm();
        await vm.LoadPortfoliosCommand.ExecuteAsync(null);

        Assert.False(vm.IsLoading);
    }

    // -----------------------------------------------------------------------------------------
    // CreatePortfolio
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task CreatePortfolio_Success_ReloadsPortfolioList()
    {
        _portfolioService.CreatePortfolioAsync("חדש").Returns(Guid.NewGuid());
        _portfolioService.GetActivePortfoliosAsync().Returns(new List<PortfolioListItem> { MakeItem("חדש") });

        var vm = MakeVm();
        StubNameInput(vm, "חדש");

        await vm.CreatePortfolioCommand.ExecuteAsync(null);

        await _portfolioService.Received(1).CreatePortfolioAsync("חדש");
        Assert.Single(vm.Portfolios);
    }

    [Fact]
    public async Task CreatePortfolio_Cancelled_DoesNotCallService()
    {
        var vm = MakeVm();
        StubNameInput(vm, null);

        await vm.CreatePortfolioCommand.ExecuteAsync(null);

        await _portfolioService.DidNotReceive().CreatePortfolioAsync(Arg.Any<string>());
    }

    [Fact]
    public async Task CreatePortfolio_EmptyName_SetsHebrewErrorMessage()
    {
        _portfolioService.CreatePortfolioAsync(Arg.Any<string>())
            .ThrowsAsync(new EmptyPortfolioNameException());

        var vm = MakeVm();
        StubNameInput(vm, "");

        await vm.CreatePortfolioCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task CreatePortfolio_DuplicateName_SetsHebrewErrorMessage()
    {
        _portfolioService.CreatePortfolioAsync(Arg.Any<string>())
            .ThrowsAsync(new DuplicatePortfolioNameException());

        var vm = MakeVm();
        StubNameInput(vm, "קיים");

        await vm.CreatePortfolioCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // RenamePortfolio
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task RenamePortfolio_Success_ReloadsPortfolioList()
    {
        var item = MakeItem("ישן");
        _portfolioService.GetActivePortfoliosAsync().Returns(new List<PortfolioListItem> { MakeItem("חדש") });

        var vm = MakeVm();
        StubNameInput(vm, "חדש");

        await vm.RenamePortfolioCommand.ExecuteAsync(item);

        await _portfolioService.Received(1).RenamePortfolioAsync(item.PortfolioId, "חדש");
    }

    [Fact]
    public async Task RenamePortfolio_Cancelled_DoesNotCallService()
    {
        var vm = MakeVm();
        StubNameInput(vm, null);

        await vm.RenamePortfolioCommand.ExecuteAsync(MakeItem());

        await _portfolioService.DidNotReceive()
            .RenamePortfolioAsync(Arg.Any<Guid>(), Arg.Any<string>());
    }

    [Fact]
    public async Task RenamePortfolio_EmptyName_SetsHebrewErrorMessage()
    {
        _portfolioService.RenamePortfolioAsync(Arg.Any<Guid>(), Arg.Any<string>())
            .ThrowsAsync(new EmptyPortfolioNameException());

        var vm = MakeVm();
        StubNameInput(vm, "");

        await vm.RenamePortfolioCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task RenamePortfolio_DuplicateName_SetsHebrewErrorMessage()
    {
        _portfolioService.RenamePortfolioAsync(Arg.Any<Guid>(), Arg.Any<string>())
            .ThrowsAsync(new DuplicatePortfolioNameException());

        var vm = MakeVm();
        StubNameInput(vm, "כפול");

        await vm.RenamePortfolioCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task RenamePortfolio_ClosedPortfolio_SetsHebrewErrorMessage()
    {
        _portfolioService.RenamePortfolioAsync(Arg.Any<Guid>(), Arg.Any<string>())
            .ThrowsAsync(new PortfolioClosedException());

        var vm = MakeVm();
        StubNameInput(vm, "שם חדש");

        await vm.RenamePortfolioCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task RenamePortfolio_NotFound_SetsHebrewErrorMessage()
    {
        _portfolioService.RenamePortfolioAsync(Arg.Any<Guid>(), Arg.Any<string>())
            .ThrowsAsync(new PortfolioNotFoundException());

        var vm = MakeVm();
        StubNameInput(vm, "שם חדש");

        await vm.RenamePortfolioCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // DeletePortfolio (underlying operation: close/soft-delete)
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task DeletePortfolio_Confirmed_CallsServiceAndReloads()
    {
        var item = MakeItem();
        _portfolioService.GetActivePortfoliosAsync().Returns(new List<PortfolioListItem>());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.DeletePortfolioCommand.ExecuteAsync(item);

        await _portfolioService.Received(1).ClosePortfolioAsync(item.PortfolioId);
    }

    [Fact]
    public async Task DeletePortfolio_NotConfirmed_DoesNotCallService()
    {
        var vm = MakeVm();
        StubConfirmation(vm, false);

        await vm.DeletePortfolioCommand.ExecuteAsync(MakeItem());

        await _portfolioService.DidNotReceive().ClosePortfolioAsync(Arg.Any<Guid>());
    }

    [Fact]
    public async Task DeletePortfolio_AlreadyClosed_SetsHebrewErrorMessage()
    {
        _portfolioService.ClosePortfolioAsync(Arg.Any<Guid>())
            .ThrowsAsync(new PortfolioClosedException());

        var vm = MakeVm();
        StubConfirmation(vm, true);

        await vm.DeletePortfolioCommand.ExecuteAsync(MakeItem());

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // OpenPortfolio
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void OpenPortfolio_RaisesPortfolioOpenRequestedEvent()
    {
        var item = MakeItem();
        var vm = MakeVm();
        PortfolioListItem? captured = null;
        vm.PortfolioOpenRequested += p => captured = p;

        vm.OpenPortfolioCommand.Execute(item);

        Assert.Same(item, captured);
    }

    // -----------------------------------------------------------------------------------------
    // SignOut
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task SignOut_RaisesSignOutRequestedEvent()
    {
        var vm = MakeVm();
        var signOutRaised = false;
        vm.SignOutRequested += () => signOutRaised = true;

        await vm.SignOutCommand.ExecuteAsync(null);

        Assert.True(signOutRaised);
    }

    [Fact]
    public async Task SignOut_CallsAuthServiceSignOut()
    {
        var vm = MakeVm();
        vm.SignOutRequested += () => { };

        await vm.SignOutCommand.ExecuteAsync(null);

        await _authService.Received(1).SignOutAsync();
    }

    // -----------------------------------------------------------------------------------------
    // Error clearing
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task LoadPortfolios_ClearsErrorMessage_BeforeLoad()
    {
        _portfolioService.GetActivePortfoliosAsync().Returns(new List<PortfolioListItem>());

        var vm = MakeVm();
        vm.ErrorMessage = "old error";

        await vm.LoadPortfoliosCommand.ExecuteAsync(null);

        Assert.Empty(vm.ErrorMessage);
    }
}
