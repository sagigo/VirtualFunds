package com.virtualfunds.data.di

import com.virtualfunds.data.auth.SupabaseAuthRepository
import com.virtualfunds.domain.auth.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the auth layer.
 *
 * @Binds is the lightweight cousin of @Provides — it tells Hilt "when something asks for
 * [AuthRepository], hand them the [SupabaseAuthRepository] instance you already know how to
 * build (via its @Inject constructor)". The module must be `abstract` for @Binds to work.
 *
 * This pattern keeps the DI graph clean: the :app layer depends on the [AuthRepository]
 * interface from :domain and never sees the Supabase-specific implementation type.
 */
// @Module marks this as a Hilt module — a class whose abstract/provider functions tell Hilt
// how to satisfy specific types in the DI graph.
// @InstallIn(SingletonComponent::class) places these bindings in the application-wide scope,
// so they live for the entire process lifetime.
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    // @Binds tells Hilt: "when something asks for AuthRepository, give them the
    // SupabaseAuthRepository instance you already know how to construct via its @Inject
    // constructor". This is the cheapest possible interface→impl mapping (no factory body).
    // @Singleton enforces a single shared instance so the authState StateFlow has one source.
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository
}
