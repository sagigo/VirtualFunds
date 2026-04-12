namespace VirtualFunds.Core.Models;

/// <summary>
/// Discriminates transaction rows into per-operation summaries and per-fund detail lines.
/// See E7.2 for the summary/detail model.
/// </summary>
public enum RecordKind
{
    /// <summary>One summary row per operation, holding user-facing text. fund_id is NULL.</summary>
    Summary,

    /// <summary>One detail row per affected fund per operation. fund_id is NOT NULL.</summary>
    Detail,
}
