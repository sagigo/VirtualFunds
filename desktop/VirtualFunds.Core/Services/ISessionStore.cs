using Supabase.Gotrue;

namespace VirtualFunds.Core.Services;

/// <summary>
/// Persists and restores the Supabase auth session between app launches (E3.3, E3.6).
/// The C# desktop implementation stores the session in local app storage.
/// </summary>
public interface ISessionStore
{
    /// <summary>Persists the session to local storage.</summary>
    Task SaveAsync(Session session);

    /// <summary>
    /// Loads the previously persisted session.
    /// Returns <c>null</c> if no session is stored or the stored data is unreadable.
    /// Never throws.
    /// </summary>
    Task<Session?> LoadAsync();

    /// <summary>Clears any persisted session from local storage.</summary>
    Task ClearAsync();
}
