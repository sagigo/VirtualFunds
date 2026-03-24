namespace VirtualFunds.Core.Exceptions;

/// <summary>
/// The fund name was empty or whitespace-only.
/// Maps to RPC error token <c>ERR_VALIDATION:EMPTY_NAME</c>.
/// </summary>
public sealed class EmptyFundNameException()
    : Exception("Fund name cannot be empty.");

/// <summary>
/// Another fund with the same name (case-insensitive) already exists in this portfolio.
/// Maps to RPC error token <c>ERR_VALIDATION:DUPLICATE_NAME</c>.
/// </summary>
public sealed class DuplicateFundNameException()
    : Exception("A fund with this name already exists in this portfolio.");

/// <summary>
/// The initial amount for the fund was negative.
/// Maps to RPC error token <c>ERR_VALIDATION:NEGATIVE_AMOUNT</c>.
/// </summary>
public sealed class NegativeFundAmountException()
    : Exception("Fund amount cannot be negative.");

/// <summary>
/// The fund cannot be deleted because its balance is not zero.
/// Maps to RPC error token <c>ERR_VALIDATION:FUND_NOT_EMPTY</c>.
/// </summary>
public sealed class FundNotEmptyException()
    : Exception("Cannot delete a fund with a non-zero balance.");

/// <summary>
/// The fund cannot be deleted because it has an enabled scheduled deposit.
/// Maps to RPC error token <c>ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT</c>.
/// </summary>
public sealed class FundHasScheduledDepositException()
    : Exception("Cannot delete a fund with an enabled scheduled deposit.");

/// <summary>
/// The requested fund was not found or does not belong to the current user's portfolio.
/// Maps to RPC error token <c>ERR_NOT_FOUND</c>.
/// </summary>
public sealed class FundNotFoundException()
    : Exception("Fund not found.");
