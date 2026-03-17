namespace VirtualFunds.Core.Models;

/// <summary>
/// Discriminated union representing the current authentication state of the application (E3.6).
/// Use pattern matching to handle each state:
/// <code>
/// switch (state)
/// {
///     case AuthStateSignedIn s: Console.WriteLine(s.UserId); break;
///     case AuthStateSignedOut:  ShowLogin(); break;
///     case AuthStateUnknown:    ShowSplash(); break;
/// }
/// </code>
/// </summary>
public abstract class AuthState;

/// <summary>
/// Initial state before the persisted session has been checked.
/// The app has not yet determined whether a valid session exists.
/// </summary>
public sealed class AuthStateUnknown : AuthState;

/// <summary>
/// No valid session exists. The user must sign in or sign up.
/// </summary>
public sealed class AuthStateSignedOut : AuthState;

/// <summary>
/// A valid session exists. The user is authenticated.
/// </summary>
/// <param name="UserId">The Supabase <c>auth.uid()</c> for the current user.</param>
public sealed class AuthStateSignedIn(string userId) : AuthState
{
    /// <summary>The authenticated user's Supabase user ID.</summary>
    public string UserId { get; } = userId;
}
