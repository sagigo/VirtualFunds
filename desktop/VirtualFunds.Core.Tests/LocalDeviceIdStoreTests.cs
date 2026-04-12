using VirtualFunds.Core.Services;
// Explicit IO aliases needed because WPF implicit usings introduce System.Windows.Shapes.Path,
// which would make bare "Path" ambiguous with System.IO.Path.
using IODirectory = System.IO.Directory;
using IOFile = System.IO.File;
using IOPath = System.IO.Path;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="LocalDeviceIdStore"/> (E8.4).
/// Uses a temp file path to avoid touching real app data.
/// </summary>
public class LocalDeviceIdStoreTests : IDisposable
{
    private readonly string _tempDir;
    private readonly string _filePath;

    public LocalDeviceIdStoreTests()
    {
        _tempDir = IOPath.Combine(IOPath.GetTempPath(), "VirtualFundsTests_" + Guid.NewGuid().ToString("N"));
        IODirectory.CreateDirectory(_tempDir);
        _filePath = IOPath.Combine(_tempDir, "device_id.json");
    }

    public void Dispose()
    {
        if (IODirectory.Exists(_tempDir))
            IODirectory.Delete(_tempDir, recursive: true);
    }

    [Fact]
    public async Task GetOrCreate_NoFile_CreatesNewGuid()
    {
        var store = new LocalDeviceIdStore(_filePath);
        var id = await store.GetOrCreateAsync();

        Assert.NotEqual(Guid.Empty, id);
        Assert.True(IOFile.Exists(_filePath));
    }

    [Fact]
    public async Task GetOrCreate_CalledTwice_ReturnsSameGuid()
    {
        var store = new LocalDeviceIdStore(_filePath);
        var first = await store.GetOrCreateAsync();
        var second = await store.GetOrCreateAsync();

        Assert.Equal(first, second);
    }

    [Fact]
    public async Task GetOrCreate_CorruptedFile_GeneratesNew()
    {
        // Write garbage to the file.
        await IOFile.WriteAllTextAsync(_filePath, "not a valid guid");

        var store = new LocalDeviceIdStore(_filePath);
        var id = await store.GetOrCreateAsync();

        Assert.NotEqual(Guid.Empty, id);
    }

    [Fact]
    public async Task GetOrCreate_EmptyFile_GeneratesNew()
    {
        await IOFile.WriteAllTextAsync(_filePath, "");

        var store = new LocalDeviceIdStore(_filePath);
        var id = await store.GetOrCreateAsync();

        Assert.NotEqual(Guid.Empty, id);
    }

    [Fact]
    public async Task GetOrCreate_DifferentPaths_ReturnDifferentGuids()
    {
        var path1 = IOPath.Combine(_tempDir, "device_id_1.json");
        var path2 = IOPath.Combine(_tempDir, "device_id_2.json");

        var store1 = new LocalDeviceIdStore(path1);
        var store2 = new LocalDeviceIdStore(path2);

        var id1 = await store1.GetOrCreateAsync();
        var id2 = await store2.GetOrCreateAsync();

        Assert.NotEqual(id1, id2);
    }
}
