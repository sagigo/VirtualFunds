namespace VirtualFunds.Core.Utilities;

/// <summary>
/// Formats money amounts stored as integer agoras for display (CLAUDE.md spec).
/// <para>
/// All money is stored as <c>long</c> agoras (1 NIS = 100 agoras). This formatter
/// converts to a human-readable string: <c>"1,234.56 ₪"</c>.
/// </para>
/// <list type="bullet">
///   <item>Shekel symbol (<c>₪</c>) placed <b>after</b> the number.</item>
///   <item>Two decimal places always shown.</item>
///   <item>Thousands separator: comma.</item>
///   <item>Display only — stored value stays <c>long</c> agoras.</item>
/// </list>
/// </summary>
public static class MoneyFormatter
{
    /// <summary>
    /// Formats an agora amount as a display string.
    /// </summary>
    /// <param name="agoras">The amount in agoras (100 agoras = 1 NIS). May be negative.</param>
    /// <returns>Formatted string, e.g. <c>"1,234.56 ₪"</c> or <c>"-50.00 ₪"</c>.</returns>
    public static string FormatAgoras(long agoras)
    {
        // Integer division and modulo to split into whole NIS and agora remainder.
        // Math.DivRem guarantees the remainder has the same sign as the dividend,
        // so we work with absolute values and prepend the sign.
        var isNegative = agoras < 0;
        var absolute = Math.Abs(agoras);
        var wholePart = absolute / 100;
        var agoraPart = absolute % 100;

        var sign = isNegative ? "-" : "";
        return $"{sign}{wholePart:N0}.{agoraPart:D2} ₪";
    }
}
