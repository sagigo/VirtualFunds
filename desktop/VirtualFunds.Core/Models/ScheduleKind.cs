namespace VirtualFunds.Core.Models;

/// <summary>
/// The supported schedule kinds for a scheduled deposit (E8.2).
/// Values match the <c>schedule_kind</c> text column in the database.
/// Serialized as strings by <c>StringEnumConverter</c>.
/// </summary>
public enum ScheduleKind
{
    /// <summary>Executes once at a specific date and time, then disables itself.</summary>
    OneTime,

    /// <summary>Executes every day at a specified time of day (Israel time).</summary>
    Daily,

    /// <summary>Executes on selected weekdays at a specified time of day (Israel time).</summary>
    Weekly,

    /// <summary>Executes on a specific day of the month at a specified time of day (Israel time).</summary>
    Monthly,
}
