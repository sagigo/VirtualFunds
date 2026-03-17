using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Services;
using VirtualFunds.Core.Supabase;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for the local validation logic in <see cref="SupabaseAuthService"/>.
/// All tested paths throw before any network call is made, so no Supabase
/// connection is required — both <c>client</c> and <c>sessionStore</c> are <c>null</c>.
/// </summary>
public class SupabaseAuthServiceValidationTests
{
    // Validation throws before the client or store are accessed, so nulls are safe here.
    private static SupabaseAuthService MakeService()
        => new(null!, null!);

    // -----------------------------------------------------------------------------------------
    // SignInAsync — validation (min-length not checked for sign-in)
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task SignIn_EmptyEmail_ThrowsEmptyEmailException()
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<EmptyEmailException>(() => svc.SignInAsync("", "password1"));
    }

    [Fact]
    public async Task SignIn_WhitespaceEmail_ThrowsEmptyEmailException()
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<EmptyEmailException>(() => svc.SignInAsync("   ", "password1"));
    }

    [Fact]
    public async Task SignIn_EmailWithoutAt_ThrowsInvalidEmailFormatException()
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<InvalidEmailFormatException>(() => svc.SignInAsync("notanemail", "password1"));
    }

    [Fact]
    public async Task SignIn_EmptyPassword_ThrowsEmptyPasswordException()
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<EmptyPasswordException>(() => svc.SignInAsync("user@example.com", ""));
    }

    [Fact]
    public async Task SignIn_WhitespacePassword_ThrowsEmptyPasswordException()
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<EmptyPasswordException>(() => svc.SignInAsync("user@example.com", "   "));
    }

    // Sign-in does NOT enforce minimum password length (user may have a short existing password).
    [Fact]
    public async Task SignIn_ShortPassword_DoesNotThrowPasswordTooShortException()
    {
        var svc = MakeService();

        // Validation passes; the exception that fires will be from the null client, not our logic.
        var ex = await Record.ExceptionAsync(() => svc.SignInAsync("user@example.com", "abc"));

        Assert.IsNotType<PasswordTooShortException>(ex);
    }

    // -----------------------------------------------------------------------------------------
    // SignUpAsync — same email rules + minimum password length enforced
    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task SignUp_EmptyEmail_ThrowsEmptyEmailException()
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<EmptyEmailException>(() => svc.SignUpAsync("", "password1"));
    }

    [Fact]
    public async Task SignUp_EmailWithoutAt_ThrowsInvalidEmailFormatException()
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<InvalidEmailFormatException>(() => svc.SignUpAsync("bademail", "password1"));
    }

    [Fact]
    public async Task SignUp_EmptyPassword_ThrowsEmptyPasswordException()
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<EmptyPasswordException>(() => svc.SignUpAsync("user@example.com", ""));
    }

    [Theory]
    [InlineData("a")]
    [InlineData("ab")]
    [InlineData("12345")]   // 5 chars — one short
    public async Task SignUp_PasswordTooShort_ThrowsPasswordTooShortException(string shortPassword)
    {
        var svc = MakeService();
        await Assert.ThrowsAsync<PasswordTooShortException>(
            () => svc.SignUpAsync("user@example.com", shortPassword));
    }

    [Fact]
    public async Task SignUp_SixCharPassword_PassesValidation()
    {
        var svc = MakeService();

        // 6 chars passes validation; failure will come from null client, not our rules.
        var ex = await Record.ExceptionAsync(() => svc.SignUpAsync("user@example.com", "123456"));

        Assert.IsNotType<PasswordTooShortException>(ex);
        Assert.IsNotType<EmptyPasswordException>(ex);
    }
}
