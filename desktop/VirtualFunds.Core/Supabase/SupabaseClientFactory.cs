namespace VirtualFunds.Core.Supabase;

/// <summary>
/// Builds and initializes a <see cref="global::Supabase.Client"/> from the configured URL and anon key.
/// <para>
/// <see cref="global::Supabase.Client"/> requires an async <c>InitializeAsync()</c> call before use,
/// which cannot be done inside a DI constructor. Call this factory in application startup
/// before building the DI container, then register the returned (already-initialized) instance
/// as a singleton.
/// </para>
/// </summary>
public static class SupabaseClientFactory
{
    /// <summary>
    /// Creates a fully initialized <see cref="global::Supabase.Client"/>.
    /// </summary>
    /// <param name="supabaseUrl">The Supabase project URL (e.g. https://xyz.supabase.co).</param>
    /// <param name="supabaseAnonKey">The Supabase project anon/public API key.</param>
    /// <returns>An initialized client ready to use.</returns>
    public static async Task<global::Supabase.Client> CreateAsync(string supabaseUrl, string supabaseAnonKey)
    {
        var options = new global::Supabase.SupabaseOptions
        {
            // AutoRefreshToken handles token renewal automatically.
            // AutoConnectRealtime is off — realtime is out of scope for version 1.
            AutoRefreshToken = true,
            AutoConnectRealtime = false,
        };

        var client = new global::Supabase.Client(supabaseUrl, supabaseAnonKey, options);
        await client.InitializeAsync().ConfigureAwait(false);
        return client;
    }
}
