package com.virtualfunds.data.di

import android.content.Context
import com.virtualfunds.data.BuildConfig
import com.virtualfunds.data.auth.SecureSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

// @Module tells Hilt this object contains provider functions — recipes for building things.
// @InstallIn(SingletonComponent::class) scopes everything here to the application lifetime,
// meaning the SupabaseClient is created exactly once when the app starts.
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    /**
     * Provides the [SessionManager] used by supabase-kt's Auth plugin to persist the session.
     *
     * @ApplicationContext gives us a long-lived Context (the Application instance) — safe to
     * hold here because this provider produces a @Singleton bean that lives as long as the app.
     */
    // @Provides tells Hilt: "when something asks for SessionManager, call this function".
    // @Singleton ensures the EncryptedSharedPreferences-backed manager is built once and reused
    // for every read/write — the underlying file handle and master-key access are not free.
    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager =
        SecureSessionManager(context)

    /**
     * The shared [SupabaseClient]. @Singleton + Hilt's SingletonComponent means it is created
     * exactly once and reused everywhere. The Auth plugin uses [sessionManager] to load the
     * persisted session on init (E3.4 "App launch") and to save/clear it on sign-in/-out.
     */
    // @Provides tells Hilt how to build a SupabaseClient. @Singleton means it is built exactly
    // once at app start; every repository receives the same instance, so they all share one
    // HTTP connection pool and one Auth session — required for E3.6 ("avoid unnecessary re-login
    // prompts on resume"). Hilt resolves the SessionManager parameter from the provider above.
    @Provides
    @Singleton
    fun provideSupabaseClient(sessionManager: SessionManager): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        // Postgrest is the REST API plugin — used for all table reads and RPC calls.
        install(Postgrest)

        // Auth plugin — sign in/up/out + session management.
        install(Auth) {
            // Plug our EncryptedSharedPreferences-backed manager in. By default supabase-kt
            // would use a platform-default that on Android is plain SharedPreferences (not
            // encrypted) — we override with [SecureSessionManager] for E3.6 / mobile/CLAUDE.md.
            this.sessionManager = sessionManager
            // autoLoadFromStorage / autoSaveToStorage / alwaysAutoRefresh default to true.
            // Together they implement E3.3 ("refresh tokens automatically") and E3.4 "App launch"
            // (load persisted session before deciding whether to show login UI).
        }
    }
}
