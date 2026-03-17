namespace VirtualFunds.Core.Exceptions;

/// <summary>The email field was empty or whitespace-only. Maps to ERR_VALIDATION:EMPTY_NAME.</summary>
public sealed class EmptyEmailException() : Exception("Email address cannot be empty.");

/// <summary>The password field was empty or whitespace-only.</summary>
public sealed class EmptyPasswordException() : Exception("Password cannot be empty.");

/// <summary>The email address does not contain a valid format (e.g., missing '@').</summary>
public sealed class InvalidEmailFormatException(string email)
    : Exception($"'{email}' is not a valid email address.");

/// <summary>
/// The password is shorter than the minimum length required by Supabase (6 characters).
/// </summary>
public sealed class PasswordTooShortException()
    : Exception("Password must be at least 6 characters.");

/// <summary>
/// Sign-in failed because of invalid credentials (wrong email or password).
/// Wraps the underlying Supabase/GoTrue exception.
/// </summary>
public sealed class AuthenticationFailedException(string message, Exception? inner = null)
    : Exception(message, inner);

/// <summary>
/// Sign-up failed — the email is already registered, or Supabase rejected the request.
/// Wraps the underlying Supabase/GoTrue exception.
/// </summary>
public sealed class RegistrationFailedException(string message, Exception? inner = null)
    : Exception(message, inner);
