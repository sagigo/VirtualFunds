using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Services;

/// <summary>
/// Service for scheduled deposit management and execution (PR-8, E8).
/// CRUD operations call Supabase RPCs. Execution is device-triggered via <c>rpc_execute_due_scheduled_deposits</c>.
/// </summary>
public interface IScheduledDepositService
{
    /// <summary>
    /// Loads all scheduled deposits for the given portfolio, ordered by creation date descending.
    /// Resolves fund names for display by cross-referencing the fund list.
    /// </summary>
    /// <param name="portfolioId">The portfolio whose scheduled deposits to load.</param>
    /// <returns>A read-only list of display items. Empty list if none exist.</returns>
    Task<IReadOnlyList<ScheduledDepositListItem>> GetScheduledDepositsAsync(Guid portfolioId);

    /// <summary>
    /// Creates or updates a scheduled deposit via <c>rpc_upsert_scheduled_deposit</c> (E8.7).
    /// Also used to enable/disable by passing the desired <paramref name="isEnabled"/> value.
    /// </summary>
    /// <param name="portfolioId">The portfolio that owns this deposit.</param>
    /// <param name="fundId">The target fund to deposit into.</param>
    /// <param name="name">Display name for the scheduled deposit.</param>
    /// <param name="amountAgoras">Amount per execution in agoras (must be &gt; 0).</param>
    /// <param name="scheduleKind">One of: "OneTime", "Daily", "Weekly", "Monthly".</param>
    /// <param name="isEnabled">Whether the deposit should be active.</param>
    /// <param name="note">Optional note attached to each execution.</param>
    /// <param name="timeOfDayMinutes">Minutes since midnight Israel time (0–1439). Required for Daily/Weekly/Monthly.</param>
    /// <param name="weekdayMask">Bitmask of weekdays (1–127). Required for Weekly.</param>
    /// <param name="dayOfMonth">Day of the month (1–28). Required for Monthly.</param>
    /// <param name="nextRunAtUtc">Desired execution time in UTC. Required for OneTime only.</param>
    /// <param name="scheduledDepositId">Null to create, non-null to update an existing deposit.</param>
    /// <returns>The scheduled deposit ID (existing or newly created).</returns>
    /// <exception cref="Exceptions.EmptyFundNameException">Name is empty.</exception>
    /// <exception cref="Exceptions.NegativeFundAmountException">Amount is not positive.</exception>
    /// <exception cref="Exceptions.InvalidScheduleKindException">Unsupported schedule kind.</exception>
    /// <exception cref="Exceptions.InvalidScheduleFieldsException">Schedule fields don't match the kind.</exception>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    Task<Guid> UpsertScheduledDepositAsync(
        Guid portfolioId,
        Guid fundId,
        string name,
        long amountAgoras,
        string scheduleKind,
        bool isEnabled,
        string? note,
        int? timeOfDayMinutes,
        int? weekdayMask,
        int? dayOfMonth,
        DateTime? nextRunAtUtc,
        Guid? scheduledDepositId);

    /// <summary>
    /// Deletes a scheduled deposit via <c>rpc_delete_scheduled_deposit</c> (E8.8).
    /// Past occurrence rows may remain for audit; the real audit trail is in transactions.
    /// </summary>
    /// <param name="scheduledDepositId">The deposit to delete.</param>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    Task DeleteScheduledDepositAsync(Guid scheduledDepositId);

    /// <summary>
    /// Triggers execution of all due scheduled deposits for a portfolio (E8.9).
    /// Calls <c>rpc_execute_due_scheduled_deposits</c> which handles exactly-once coordination,
    /// catch-up processing, and balance mutations.
    /// </summary>
    /// <param name="portfolioId">The portfolio to execute deposits for.</param>
    /// <param name="deviceId">The stable device ID of this installation.</param>
    /// <returns>The number of deposits that were executed.</returns>
    Task<int> ExecuteDueDepositsAsync(Guid portfolioId, Guid deviceId);
}
