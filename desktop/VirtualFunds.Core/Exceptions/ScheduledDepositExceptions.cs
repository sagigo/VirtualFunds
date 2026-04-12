namespace VirtualFunds.Core.Exceptions;

/// <summary>
/// The schedule-specific fields are invalid for the given schedule kind.
/// Maps to RPC error token <c>ERR_VALIDATION:INVALID_SCHEDULE_FIELDS</c>.
/// </summary>
public sealed class InvalidScheduleFieldsException()
    : Exception("Schedule fields are invalid for the selected schedule kind.");

/// <summary>
/// The schedule kind is not one of the supported values (OneTime, Daily, Weekly, Monthly).
/// Maps to RPC error token <c>ERR_VALIDATION:INVALID_SCHEDULE_KIND</c>.
/// </summary>
public sealed class InvalidScheduleKindException()
    : Exception("Invalid schedule kind.");
