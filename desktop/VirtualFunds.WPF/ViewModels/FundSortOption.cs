namespace VirtualFunds.WPF.ViewModels;

/// <summary>
/// Presentation-only sort modes for the fund list (E5.10).
/// No money algorithm may depend on these values — they affect display order only.
/// </summary>
public enum FundSortMode
{
    /// <summary>Sort by creation date, oldest first (default).</summary>
    CreatedDate,

    /// <summary>Sort alphabetically by fund name (matches server order).</summary>
    Name,

    /// <summary>Sort by balance, highest first.</summary>
    Balance,

    /// <summary>Sort by derived allocation percentage, highest first.</summary>
    AllocationPercent,

    /// <summary>User-defined order, maintained in memory via up/down buttons.</summary>
    Custom,
}

/// <summary>
/// A single entry in the sort mode dropdown.
/// Pairs a user-facing Hebrew label with the corresponding <see cref="FundSortMode"/> value.
/// </summary>
/// <param name="Label">Hebrew label shown in the ComboBox.</param>
/// <param name="Mode">The sort mode this option represents.</param>
public record FundSortOption(string Label, FundSortMode Mode);
