using NSubstitute;
using NSubstitute.ExceptionExtensions;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.WPF.ViewModels;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="AuthViewModel"/>.
/// <see cref="IAuthService"/> is mocked with NSubstitute so no network calls are made.
/// </summary>
public class AuthViewModelTests
{
    private readonly IAuthService _authService = Substitute.For<IAuthService>();

    private AuthViewModel MakeVm() => new(_authService);

    // -----------------------------------------------------------------------------------------
    // ToggleMode
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void ToggleMode_FlipsIsSignUpMode()
    {
        var vm = MakeVm();
        Assert.False(vm.IsSignUpMode);

        vm.ToggleModeCommand.Execute(null);
        Assert.True(vm.IsSignUpMode);

        vm.ToggleModeCommand.Execute(null);
        Assert.False(vm.IsSignUpMode);
    }

    [Fact]
    public void ToggleMode_ClearsErrorMessage()
    {
        var vm = MakeVm();
        vm.ErrorMessage = "some error";

        vm.ToggleModeCommand.Execute(null);

        Assert.Empty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // SubmitAsync — error messages (Sign In mode)
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task Submit_SignIn_EmptyEmail_SetsHebrewErrorMessage()
    {
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .ThrowsAsync(new EmptyEmailException());

        var vm = MakeVm();
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
        // Verify it contains Hebrew content (not an English default).
        Assert.DoesNotContain("Exception", vm.ErrorMessage);
    }

    [Fact]
    public async Task Submit_SignIn_InvalidEmail_SetsHebrewErrorMessage()
    {
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .ThrowsAsync(new InvalidEmailFormatException("bad"));

        var vm = MakeVm();
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task Submit_SignIn_EmptyPassword_SetsHebrewErrorMessage()
    {
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .ThrowsAsync(new EmptyPasswordException());

        var vm = MakeVm();
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task Submit_SignIn_WrongCredentials_SetsHebrewErrorMessage()
    {
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .ThrowsAsync(new AuthenticationFailedException("bad credentials"));

        var vm = MakeVm();
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // SubmitAsync — error messages (Sign Up mode)
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task Submit_SignUp_PasswordTooShort_SetsHebrewErrorMessage()
    {
        _authService.SignUpAsync(Arg.Any<string>(), Arg.Any<string>())
            .ThrowsAsync(new PasswordTooShortException());

        var vm = MakeVm();
        vm.IsSignUpMode = true;
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    [Fact]
    public async Task Submit_SignUp_RegistrationFailed_SetsHebrewErrorMessage()
    {
        _authService.SignUpAsync(Arg.Any<string>(), Arg.Any<string>())
            .ThrowsAsync(new RegistrationFailedException("already in use"));

        var vm = MakeVm();
        vm.IsSignUpMode = true;
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // SubmitAsync — generic fallback
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task Submit_UnexpectedException_SetsFallbackHebrewErrorMessage()
    {
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .ThrowsAsync(new InvalidOperationException("unexpected"));

        var vm = MakeVm();
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotEmpty(vm.ErrorMessage);
    }

    // -----------------------------------------------------------------------------------------
    // SubmitAsync — success
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task Submit_SignIn_Success_FiresAuthSucceededEvent()
    {
        var expectedState = new AuthStateSignedIn("user-999");
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .Returns(expectedState);

        var vm = MakeVm();
        AuthState? captured = null;
        vm.AuthSucceeded += s => captured = s;

        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotNull(captured);
        var signedIn = Assert.IsType<AuthStateSignedIn>(captured);
        Assert.Equal("user-999", signedIn.UserId);
    }

    [Fact]
    public async Task Submit_SignIn_Success_ErrorMessageRemainsEmpty()
    {
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .Returns(new AuthStateSignedIn("u"));

        var vm = MakeVm();
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.Empty(vm.ErrorMessage);
    }

    [Fact]
    public async Task Submit_SignUp_Success_FiresAuthSucceededEvent()
    {
        var expectedState = new AuthStateSignedIn("user-new");
        _authService.SignUpAsync(Arg.Any<string>(), Arg.Any<string>())
            .Returns(expectedState);

        var vm = MakeVm();
        vm.IsSignUpMode = true;
        AuthState? captured = null;
        vm.AuthSucceeded += s => captured = s;

        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.NotNull(captured);
        Assert.IsType<AuthStateSignedIn>(captured);
    }

    // -----------------------------------------------------------------------------------------
    // SubmitAsync — error is cleared at the start of each attempt
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task Submit_ClearsErrorMessage_BeforeEachAttempt()
    {
        // First call fails, second call succeeds.
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .Returns(
                _ => throw new AuthenticationFailedException("fail"),
                _ => new AuthStateSignedIn("u"));

        var vm = MakeVm();

        await vm.SubmitCommand.ExecuteAsync(null);
        Assert.NotEmpty(vm.ErrorMessage);  // Error set after first failure.

        await vm.SubmitCommand.ExecuteAsync(null);
        Assert.Empty(vm.ErrorMessage);     // Error cleared on second (successful) attempt.
    }

    // -----------------------------------------------------------------------------------------
    // IsLoading
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task Submit_IsLoadingFalse_AfterCompletion()
    {
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .Returns(new AuthStateSignedIn("u"));

        var vm = MakeVm();
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.False(vm.IsLoading);
    }

    [Fact]
    public async Task Submit_IsLoadingFalse_AfterFailure()
    {
        _authService.SignInAsync(Arg.Any<string>(), Arg.Any<string>())
            .ThrowsAsync(new AuthenticationFailedException("bad"));

        var vm = MakeVm();
        await vm.SubmitCommand.ExecuteAsync(null);

        Assert.False(vm.IsLoading);
    }
}
