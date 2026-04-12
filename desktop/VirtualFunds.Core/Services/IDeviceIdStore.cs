namespace VirtualFunds.Core.Services;

/// <summary>
/// Provides a stable device identifier for this installation (E2.3, E8.4).
/// <para>
/// Each app installation must create and persist a stable <c>device_id</c> UUID.
/// This ID is passed to <c>rpc_execute_due_scheduled_deposits</c> for claim coordination.
/// </para>
/// </summary>
public interface IDeviceIdStore
{
    /// <summary>
    /// Returns the persisted device ID, creating and saving a new one if none exists.
    /// </summary>
    Task<Guid> GetOrCreateAsync();
}
