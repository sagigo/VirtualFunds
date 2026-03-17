using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Services;

/// <summary>
/// Authentication operations for the Virtual Funds application (E3.4).
/// All mutation methods return the resulting <see cref="AuthState"/> so callers
/// do not need a separate property query after each operation.
/// </summary>
public interface IAuthService
{
    /// <summary>The last known authentication state.</summary>
    AuthState CurrentState { get; }

    /// <summary>
    /// Attempts to load and restore a previously persisted session (E3.4 — App launch).
    /// Returns <see cref="AuthStateSignedIn"/> if a valid/refreshable session is found,
    /// or <see cref="AuthStateSignedOut"/> if there is no usable session.
    /// </summary>
    Task<AuthState> RestoreSessionAsync();

    /// <summary>
    /// Signs in with email and password (E3.4 — Sign in).
    /// </summary>
    /// <exception cref="Exceptions.EmptyEmailException"/>
    /// <exception cref="Exceptions.EmptyPasswordException"/>
    /// <exception cref="Exceptions.InvalidEmailFormatException"/>
    /// <exception cref="Exceptions.AuthenticationFailedException"/>
    Task<AuthState> SignInAsync(string email, string password);

    /// <summary>
    /// Registers a new account and immediately signs in (E3.4 — Sign up).
    /// </summary>
    /// <exception cref="Exceptions.EmptyEmailException"/>
    /// <exception cref="Exceptions.EmptyPasswordException"/>
    /// <exception cref="Exceptions.InvalidEmailFormatException"/>
    /// <exception cref="Exceptions.PasswordTooShortException"/>
    /// <exception cref="Exceptions.RegistrationFailedException"/>
    Task<AuthState> SignUpAsync(string email, string password);

    /// <summary>
    /// Signs out the current user and clears the persisted session (E3.4 — Sign out).
    /// Always returns <see cref="AuthStateSignedOut"/>.
    /// </summary>
    Task<AuthState> SignOutAsync();
}
