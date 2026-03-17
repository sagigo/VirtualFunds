using Supabase.Gotrue.Exceptions;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;

namespace VirtualFunds.Core.Supabase;

/// <summary>
/// Supabase-backed implementation of <see cref="IAuthService"/> (E3.4, E3.6).
/// Wraps <see cref="global::Supabase.Client"/> and coordinates with <see cref="ISessionStore"/>
/// for local session persistence.
/// </summary>
public sealed class SupabaseAuthService : IAuthService
{
    private readonly global::Supabase.Client _client;
    private readonly ISessionStore _sessionStore;

    /// <inheritdoc />
    public AuthState CurrentState { get; private set; } = new AuthStateUnknown();

    /// <summary>Minimum password length enforced by Supabase.</summary>
    private const int MinPasswordLength = 6;

    /// <summary>
    /// Initializes the service with the Supabase client and local session store.
    /// </summary>
    public SupabaseAuthService(global::Supabase.Client client, ISessionStore sessionStore)
    {
        _client = client;
        _sessionStore = sessionStore;
    }

    /// <inheritdoc />
    public async Task<AuthState> RestoreSessionAsync()
    {
        var session = await _sessionStore.LoadAsync().ConfigureAwait(false);

        if (session is null || session.AccessToken is null || session.RefreshToken is null)
        {
            CurrentState = new AuthStateSignedOut();
            return CurrentState;
        }

        try
        {
            // SetSession re-validates the tokens and triggers automatic refresh if needed.
            var restored = await _client.Auth.SetSession(session.AccessToken, session.RefreshToken)
                .ConfigureAwait(false);

            if (restored?.User?.Id is null)
            {
                await _sessionStore.ClearAsync().ConfigureAwait(false);
                CurrentState = new AuthStateSignedOut();
                return CurrentState;
            }

            // Persist the potentially-refreshed session.
            await _sessionStore.SaveAsync(restored).ConfigureAwait(false);
            CurrentState = new AuthStateSignedIn(restored.User.Id);
            return CurrentState;
        }
        catch
        {
            // Any failure during restore means we have no usable session.
            await _sessionStore.ClearAsync().ConfigureAwait(false);
            CurrentState = new AuthStateSignedOut();
            return CurrentState;
        }
    }

    /// <inheritdoc />
    public async Task<AuthState> SignInAsync(string email, string password)
    {
        ValidateEmailAndPassword(email, password, validateMinLength: false);

        try
        {
            var session = await _client.Auth.SignIn(email, password).ConfigureAwait(false);

            if (session?.User?.Id is null)
                throw new AuthenticationFailedException("Sign-in failed: no session returned.");

            await _sessionStore.SaveAsync(session).ConfigureAwait(false);
            CurrentState = new AuthStateSignedIn(session.User.Id);
            return CurrentState;
        }
        catch (GotrueException ex)
        {
            throw new AuthenticationFailedException(
                "Invalid email or password. Please try again.", ex);
        }
    }

    /// <inheritdoc />
    public async Task<AuthState> SignUpAsync(string email, string password)
    {
        ValidateEmailAndPassword(email, password, validateMinLength: true);

        try
        {
            var session = await _client.Auth.SignUp(email, password).ConfigureAwait(false);

            if (session?.User?.Id is null)
                throw new RegistrationFailedException("Sign-up failed: no session returned.");

            await _sessionStore.SaveAsync(session).ConfigureAwait(false);
            CurrentState = new AuthStateSignedIn(session.User.Id);
            return CurrentState;
        }
        catch (GotrueException ex)
        {
            throw new RegistrationFailedException(
                "Registration failed. The email may already be in use.", ex);
        }
    }

    /// <inheritdoc />
    public async Task<AuthState> SignOutAsync()
    {
        try
        {
            await _client.Auth.SignOut().ConfigureAwait(false);
        }
        catch
        {
            // Best-effort sign-out: even if the server call fails, we clear the local session.
        }

        await _sessionStore.ClearAsync().ConfigureAwait(false);
        CurrentState = new AuthStateSignedOut();
        return CurrentState;
    }

    // -----------------------------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Validates email format and password requirements.
    /// Throws typed exceptions on failure so the ViewModel can map them to Hebrew error messages.
    /// </summary>
    private static void ValidateEmailAndPassword(string email, string password, bool validateMinLength)
    {
        if (string.IsNullOrWhiteSpace(email))
            throw new EmptyEmailException();

        if (!email.Contains('@'))
            throw new InvalidEmailFormatException(email);

        if (string.IsNullOrWhiteSpace(password))
            throw new EmptyPasswordException();

        if (validateMinLength && password.Length < MinPasswordLength)
            throw new PasswordTooShortException();
    }
}
