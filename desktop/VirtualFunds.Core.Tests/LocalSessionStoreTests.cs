using Newtonsoft.Json;
using Supabase.Gotrue;
using VirtualFunds.Core.Services;
// Explicit IO aliases needed because WPF implicit usings introduce System.Windows.Shapes.Path,
// which would make bare "Path" ambiguous with System.IO.Path.
using IOFile = System.IO.File;
using IOPath = System.IO.Path;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="LocalSessionStore"/> using a temp file so production app data is never touched.
/// Each test instance gets its own isolated file path via the constructor.
/// </summary>
public class LocalSessionStoreTests : IDisposable
{
    private readonly string _tempFile;
    private readonly LocalSessionStore _store;

    public LocalSessionStoreTests()
    {
        _tempFile = IOPath.Combine(IOPath.GetTempPath(), $"vf_test_session_{Guid.NewGuid()}.json");
        // Uses the internal constructor that accepts a custom path.
        _store = new LocalSessionStore(_tempFile);
    }

    public void Dispose()
    {
        if (IOFile.Exists(_tempFile))
            IOFile.Delete(_tempFile);
    }

    // -----------------------------------------------------------------------------------------

    [Fact]
    public async Task LoadAsync_ReturnsNull_WhenFileDoesNotExist()
    {
        var result = await _store.LoadAsync();

        Assert.Null(result);
    }

    [Fact]
    public async Task SaveAndLoad_RoundTripsAccessAndRefreshTokens()
    {
        var session = MakeSession("access-tok", "refresh-tok", "user-42");

        await _store.SaveAsync(session);
        var loaded = await _store.LoadAsync();

        Assert.NotNull(loaded);
        Assert.Equal("access-tok", loaded.AccessToken);
        Assert.Equal("refresh-tok", loaded.RefreshToken);
    }

    [Fact]
    public async Task SaveAndLoad_RoundTripsUserId()
    {
        var session = MakeSession("a", "r", "user-xyz");

        await _store.SaveAsync(session);
        var loaded = await _store.LoadAsync();

        Assert.NotNull(loaded);
        Assert.Equal("user-xyz", loaded.User?.Id);
    }

    [Fact]
    public async Task ClearAsync_RemovesFile()
    {
        await _store.SaveAsync(MakeSession("a", "r", "u"));
        Assert.True(IOFile.Exists(_tempFile));

        await _store.ClearAsync();

        Assert.False(IOFile.Exists(_tempFile));
    }

    [Fact]
    public async Task LoadAsync_ReturnsNull_AfterClear()
    {
        await _store.SaveAsync(MakeSession("a", "r", "u"));
        await _store.ClearAsync();

        var result = await _store.LoadAsync();

        Assert.Null(result);
    }

    [Fact]
    public async Task LoadAsync_ReturnsNull_WhenFileIsCorrupt()
    {
        await IOFile.WriteAllTextAsync(_tempFile, "this is not valid json {{{");

        var result = await _store.LoadAsync();

        Assert.Null(result);
    }

    [Fact]
    public async Task ClearAsync_DoesNotThrow_WhenFileDoesNotExist()
    {
        // File never existed — Clear should be a no-op.
        var exception = await Record.ExceptionAsync(() => _store.ClearAsync());

        Assert.Null(exception);
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Constructs a <see cref="Session"/> via JSON deserialization (same path the SDK uses).
    /// </summary>
    private static Session MakeSession(string accessToken, string refreshToken, string userId)
    {
        var json = JsonConvert.SerializeObject(new
        {
            access_token = accessToken,
            refresh_token = refreshToken,
            token_type = "bearer",
            expires_in = 3600,
            user = new { id = userId }
        });

        return JsonConvert.DeserializeObject<Session>(json)!;
    }
}
