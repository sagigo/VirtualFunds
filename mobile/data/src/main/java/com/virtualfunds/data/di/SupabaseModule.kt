package com.virtualfunds.data.di

import com.virtualfunds.data.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

// @Module tells Hilt this object contains provider functions — recipes for building things.
// @InstallIn(SingletonComponent::class) scopes everything here to the application lifetime,
// meaning the SupabaseClient is created exactly once when the app starts.
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    // @Provides tells Hilt: "when something asks for a SupabaseClient, call this function".
    // @Singleton ensures Hilt calls it only once and reuses the same instance everywhere.
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        // Postgrest is the REST API plugin — used for all table reads and RPC calls.
        install(Postgrest)

        // Auth is the authentication plugin — used for sign in/up/out and session management.
        // Session persistence (EncryptedSharedPreferences) is configured in Phase 1.
        // For now, Auth uses its default in-memory session storage.
        install(Auth)
    }
}
