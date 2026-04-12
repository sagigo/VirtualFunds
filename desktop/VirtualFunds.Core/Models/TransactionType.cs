namespace VirtualFunds.Core.Models;

/// <summary>
/// The canonical registry of all transaction types.
/// See E5.2 for the full specification.
/// </summary>
public enum TransactionType
{
    // ── Portfolio lifecycle ──────────────────────────────────────────────────
    /// <summary>Portfolio was created (Summary only).</summary>
    PortfolioCreated,

    /// <summary>Portfolio was renamed (Summary only).</summary>
    PortfolioRenamed,

    /// <summary>Portfolio was closed (Summary only).</summary>
    PortfolioClosed,

    // ── Fund lifecycle ───────────────────────────────────────────────────────
    /// <summary>Fund was created (Summary + Detail).</summary>
    FundCreated,

    /// <summary>Fund was renamed (Summary + Detail).</summary>
    FundRenamed,

    /// <summary>Fund was deleted (Summary + Detail).</summary>
    FundDeleted,

    // ── Deposits / Withdrawals ───────────────────────────────────────────────
    /// <summary>Money added to a fund (Summary + Detail).</summary>
    FundDeposit,

    /// <summary>Money withdrawn from a fund (Summary + Detail).</summary>
    FundWithdrawal,

    // ── Transfers ────────────────────────────────────────────────────────────
    /// <summary>Transfer between funds — umbrella summary row (Summary only).</summary>
    Transfer,

    /// <summary>Receiving side of a transfer (Detail only).</summary>
    TransferCredit,

    /// <summary>Sending side of a transfer (Detail only).</summary>
    TransferDebit,

    // ── Revaluation ──────────────────────────────────────────────────────────
    /// <summary>Portfolio total proportionally scaled (Summary only).</summary>
    PortfolioRevalued,

    /// <summary>Fund increased during a revaluation (Detail only).</summary>
    RevaluationCredit,

    /// <summary>Fund decreased during a revaluation (Detail only).</summary>
    RevaluationDebit,

    // ── Automation ───────────────────────────────────────────────────────────
    /// <summary>Scheduled deposit auto-execution (Summary + Detail).</summary>
    ScheduledDepositExecuted,

    // ── Undo ─────────────────────────────────────────────────────────────────
    /// <summary>Compensating operation that reverses a prior operation (Summary + Detail).</summary>
    Undo,
}
