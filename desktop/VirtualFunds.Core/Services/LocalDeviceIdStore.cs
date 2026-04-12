using Newtonsoft.Json;

namespace VirtualFunds.Core.Services;

/// <summary>
/// File-based implementation of <see cref="IDeviceIdStore"/>.
/// Persists a stable device UUID to <c>%LOCALAPPDATA%\VirtualFunds\device_id.json</c> (E8.4).
/// <para>
/// Follows the same pattern as <see cref="LocalSessionStore"/>: file-based persistence
/// with silent recovery from corrupted files.
/// </para>
/// </summary>
public sealed class LocalDeviceIdStore : IDeviceIdStore
{
    private static readonly string DefaultFilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VirtualFunds",
        "device_id.json");

    private readonly string _filePath;

    /// <summary>Production constructor — stores the device ID at the standard app data path.</summary>
    public LocalDeviceIdStore() : this(DefaultFilePath) { }

    /// <summary>
    /// Test constructor — stores the device ID at a custom path.
    /// Use this in unit tests to avoid touching real app data.
    /// </summary>
    internal LocalDeviceIdStore(string filePath) => _filePath = filePath;

    /// <inheritdoc />
    public async Task<Guid> GetOrCreateAsync()
    {
        // Try to read existing device ID.
        try
        {
            if (File.Exists(_filePath))
            {
                var json = await File.ReadAllTextAsync(_filePath).ConfigureAwait(false);
                var id = JsonConvert.DeserializeObject<Guid>(json);

                // Guard against an empty/default GUID from a corrupted file.
                if (id != Guid.Empty)
                    return id;
            }
        }
        catch
        {
            // Corrupted file — fall through to generate a new ID.
        }

        // Generate and persist a new device ID.
        var newId = Guid.NewGuid();

        var directory = Path.GetDirectoryName(_filePath)!;
        Directory.CreateDirectory(directory);

        var newJson = JsonConvert.SerializeObject(newId, Formatting.Indented);
        await File.WriteAllTextAsync(_filePath, newJson).ConfigureAwait(false);

        return newId;
    }
}
