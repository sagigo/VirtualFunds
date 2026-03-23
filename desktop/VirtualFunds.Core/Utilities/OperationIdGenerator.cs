namespace VirtualFunds.Core.Utilities;

/// <summary>
/// Generates unique identifiers required by every RPC mutation call (per E2.3).
/// <para>
/// <c>operation_id</c> groups all history rows for one logical user action.
/// <c>transaction_id</c> uniquely identifies a single history row and serves as
/// the idempotency anchor when used as the summary transaction ID.
/// </para>
/// </summary>
public static class OperationIdGenerator
{
    /// <summary>
    /// Creates a new unique operation ID that groups related transaction rows
    /// for a single logical action (e.g., a deposit that produces one Summary + one Detail row).
    /// </summary>
    public static Guid NewOperationId() => Guid.NewGuid();

    /// <summary>
    /// Creates a new unique transaction ID for a single history row.
    /// When used as the summary transaction ID in an RPC call, it also serves as the idempotency key (E7.4).
    /// </summary>
    public static Guid NewTransactionId() => Guid.NewGuid();
}
