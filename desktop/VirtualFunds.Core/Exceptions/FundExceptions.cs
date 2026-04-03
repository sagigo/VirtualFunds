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

/// <summary>
/// A fund operation would result in a negative balance.
/// Maps to RPC error token <c>ERR_INVARIANT:NEGATIVE_BALANCE</c>.
/// </summary>
public sealed class InsufficientFundBalanceException()
    : Exception("Insufficient fund balance for this operation.");

/// <summary>
/// A transfer was attempted where source and destination are the same fund.
/// This is a client-side validation — the RPC is never called.
/// </summary>
public sealed class SameFundTransferException()
    : Exception("Source and destination funds must be different.");

/// <summary>
/// The server detected that fund balance totals are inconsistent after an operation.
/// This indicates a bug in client-side delta computation and should never happen in practice.
/// Maps to RPC error token <c>ERR_INVARIANT:TOTAL_MISMATCH</c>.
/// </summary>
public sealed class TotalMismatchException()
    : Exception("Fund total invariant check failed — possible bug in delta computation.");
