using Newtonsoft.Json.Linq;
using Postgrest;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;

namespace VirtualFunds.Core.Supabase;

/// <summary>
/// Supabase-backed implementation of <see cref="IScheduledDepositService"/> (PR-8, E8).
/// Reads scheduled deposits via Postgrest. Mutations use dedicated RPCs.
/// Execution triggers <c>rpc_execute_due_scheduled_deposits</c>.
/// </summary>
public sealed class SupabaseScheduledDepositService : IScheduledDepositService
{
    private readonly global::Supabase.Client _client;
    private readonly IFundService _fundService;

    /// <summary>
    /// Initializes the service with the Supabase client and a fund service for name resolution.
    /// </summary>
    public SupabaseScheduledDepositService(global::Supabase.Client client, IFundService fundService)
    {
        _client = client;
        _fundService = fundService;
    }

    /// <inheritdoc />
    public async Task<IReadOnlyList<ScheduledDepositListItem>> GetScheduledDepositsAsync(Guid portfolioId)
    {
        // Load scheduled deposits from Postgrest (RLS ensures user ownership).
        var response = await _client.From<ScheduledDeposit>()
            .Filter("portfolio_id", Constants.Operator.Equals, portfolioId.ToString())
            .Order("created_at_utc", Constants.Ordering.Descending)
            .Get()
            .ConfigureAwait(false);

        var deposits = response.Models;

        if (deposits.Count == 0)
            return Array.Empty<ScheduledDepositListItem>();

        // Resolve fund names for display.
        var funds = await _fundService.GetFundsAsync(portfolioId).ConfigureAwait(false);
        var fundNameMap = funds.ToDictionary(f => f.FundId, f => f.Name);

        return deposits
            .Select(d => new ScheduledDepositListItem
            {
                ScheduledDepositId = d.ScheduledDepositId,
                PortfolioId = d.PortfolioId,
                FundId = d.FundId,
                Name = d.Name,
                Note = d.Note,
                IsEnabled = d.IsEnabled,
                AmountAgoras = d.AmountAgoras,
                ScheduleKind = d.ScheduleKind,
                TimeOfDayMinutes = d.TimeOfDayMinutes,
                WeekdayMask = d.WeekdayMask,
                DayOfMonth = d.DayOfMonth,
                NextRunAtUtc = d.NextRunAtUtc,
                FundName = fundNameMap.GetValueOrDefault(d.FundId, "—"),
            })
            .ToList();
    }

    /// <inheritdoc />
    public async Task<Guid> UpsertScheduledDepositAsync(
        Guid portfolioId,
        Guid fundId,
        string name,
        long amountAgoras,
        ScheduleKind scheduleKind,
        bool isEnabled,
        string? note,
        int? timeOfDayMinutes,
        int? weekdayMask,
        int? dayOfMonth,
        DateTime? nextRunAtUtc,
        Guid? scheduledDepositId)
    {
        try
        {
            var response = await _client.Rpc(
                "rpc_upsert_scheduled_deposit",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_fund_id = fundId,
                    p_name = name,
                    p_amount_agoras = amountAgoras,
                    p_schedule_kind = scheduleKind.ToString(), // RPC expects a text value
                    p_is_enabled = isEnabled,
                    p_note = note,
                    p_time_of_day_minutes = timeOfDayMinutes,
                    p_weekday_mask = weekdayMask,
                    p_day_of_month = dayOfMonth,
                    p_next_run_at_utc = nextRunAtUtc,
                    p_scheduled_deposit_id = scheduledDepositId,
                }).ConfigureAwait(false);

            // The RPC returns jsonb: { "scheduled_deposit_id": "...", "next_run_at_utc": "..." }
            var json = JObject.Parse(response.Content!);
            return json["scheduled_deposit_id"]!.ToObject<Guid>();
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw; // Unreachable, but satisfies the compiler.
        }
    }

    /// <inheritdoc />
    public async Task DeleteScheduledDepositAsync(Guid scheduledDepositId)
    {
        try
        {
            await _client.Rpc(
                "rpc_delete_scheduled_deposit",
                new
                {
                    p_scheduled_deposit_id = scheduledDepositId,
                }).ConfigureAwait(false);
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw;
        }
    }

    /// <inheritdoc />
    public async Task<int> ExecuteDueDepositsAsync(Guid portfolioId, Guid deviceId)
    {
        try
        {
            var response = await _client.Rpc(
                "rpc_execute_due_scheduled_deposits",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_now_utc = DateTime.UtcNow,
                    p_device_id = deviceId,
                }).ConfigureAwait(false);

            // The RPC returns a jsonb array of executed occurrences.
            var array = JArray.Parse(response.Content ?? "[]");
            return array.Count;
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw;
        }
    }

    // -----------------------------------------------------------------------------------------
    // Error mapping
    // -----------------------------------------------------------------------------------------

    private static bool IsRpcException(Exception ex)
    {
        var msg = ex.Message;
        var inner = ex.InnerException?.Message;
        return msg.Contains("ERR_") || inner?.Contains("ERR_") == true;
    }

    /// <summary>
    /// Maps RPC error tokens to typed exceptions. Includes scheduled-deposit-specific tokens
    /// plus shared tokens that can be raised by these RPCs.
    /// </summary>
    private static void ThrowForRpcError(Exception ex)
    {
        var message = ex.Message + (ex.InnerException?.Message ?? string.Empty);

        // Scheduled-deposit-specific tokens
        if (message.Contains("ERR_VALIDATION:INVALID_SCHEDULE_FIELDS"))
            throw new InvalidScheduleFieldsException();

        if (message.Contains("ERR_VALIDATION:INVALID_SCHEDULE_KIND"))
            throw new InvalidScheduleKindException();

        // Shared tokens (reused from fund/portfolio exceptions)
        if (message.Contains("ERR_VALIDATION:EMPTY_NAME"))
            throw new EmptyFundNameException();

        if (message.Contains("ERR_VALIDATION:NEGATIVE_AMOUNT"))
            throw new NegativeFundAmountException();

        if (message.Contains("ERR_VALIDATION:PORTFOLIO_CLOSED"))
            throw new PortfolioClosedException();

        if (message.Contains("ERR_NOT_FOUND"))
            throw new FundNotFoundException();

        // Unknown RPC error — re-throw the original.
        throw ex;
    }
}
