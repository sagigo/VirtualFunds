using Newtonsoft.Json;
using Supabase.Gotrue;

namespace VirtualFunds.Core.Services;

/// <summary>
/// File-based implementation of <see cref="ISessionStore"/>.
/// Persists the Supabase session as JSON to <c>%LOCALAPPDATA%\VirtualFunds\session.json</c> (E3.6).
/// Uses Newtonsoft.Json for serialization because <see cref="Session"/> uses [JsonProperty] attributes
/// from the Newtonsoft ecosystem (already a transitive dependency via supabase-csharp).
/// </summary>
public sealed class LocalSessionStore : ISessionStore
{
    private static readonly string DefaultFilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VirtualFunds",
        "session.json");

    private readonly string _filePath;

    /// <summary>Production constructor — stores session at the standard app data path.</summary>
    public LocalSessionStore() : this(DefaultFilePath) { }

    /// <summary>
    /// Test constructor — stores session at a custom path.
    /// Use this in unit tests to avoid touching real app data.
    /// </summary>
    internal LocalSessionStore(string filePath) => _filePath = filePath;

    /// <inheritdoc />
    public async Task SaveAsync(Session session)
    {
        var directory = Path.GetDirectoryName(_filePath)!;
        Directory.CreateDirectory(directory);

        var json = JsonConvert.SerializeObject(session, Formatting.Indented);
        await File.WriteAllTextAsync(_filePath, json).ConfigureAwait(false);
    }

    /// <inheritdoc />
    public async Task<Session?> LoadAsync()
    {
        try
        {
            if (!File.Exists(_filePath))
                return null;

            var json = await File.ReadAllTextAsync(_filePath).ConfigureAwait(false);
            return JsonConvert.DeserializeObject<Session>(json);
        }
        catch
        {
            // Treat any read or parse failure as "no session" — never crash the app on startup.
            return null;
        }
    }

    /// <inheritdoc />
    public Task ClearAsync()
    {
        if (File.Exists(_filePath))
            File.Delete(_filePath);

        return Task.CompletedTask;
    }
}
