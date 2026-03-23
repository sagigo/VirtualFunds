namespace VirtualFunds.Core.Exceptions;

/// <summary>
/// The portfolio name was empty or whitespace-only.
/// Maps to RPC error token <c>ERR_VALIDATION:EMPTY_NAME</c>.
/// </summary>
public sealed class EmptyPortfolioNameException()
    : Exception("Portfolio name cannot be empty.");

/// <summary>
/// Another active portfolio with the same name (case-insensitive) already exists.
/// Maps to RPC error token <c>ERR_VALIDATION:DUPLICATE_NAME</c>.
/// </summary>
public sealed class DuplicatePortfolioNameException()
    : Exception("A portfolio with this name already exists.");

/// <summary>
/// The target portfolio has been closed (soft-deleted) and cannot be mutated.
/// Maps to RPC error token <c>ERR_VALIDATION:PORTFOLIO_CLOSED</c>.
/// </summary>
public sealed class PortfolioClosedException()
    : Exception("This portfolio has been closed.");

/// <summary>
/// The requested portfolio was not found or does not belong to the current user.
/// Maps to RPC error token <c>ERR_NOT_FOUND</c>.
/// </summary>
public sealed class PortfolioNotFoundException()
    : Exception("Portfolio not found.");
