package com.virtualfunds.data.di

import com.virtualfunds.data.device.SharedPrefsDeviceIdStore
import com.virtualfunds.data.repository.SupabaseFundRepository
import com.virtualfunds.data.repository.SupabasePortfolioRepository
import com.virtualfunds.data.repository.SupabaseScheduledDepositRepository
import com.virtualfunds.data.repository.SupabaseTransactionRepository
import com.virtualfunds.domain.repository.DeviceIdStore
import com.virtualfunds.domain.repository.FundRepository
import com.virtualfunds.domain.repository.PortfolioRepository
import com.virtualfunds.domain.repository.ScheduledDepositRepository
import com.virtualfunds.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the Phase 2 repository layer.
 *
 * Every binding is `@Binds`: it tells Hilt "when something asks for repository interface
 * X, hand them the Supabase implementation Y that you already know how to construct via
 * its `@Inject` constructor". This is the cheapest possible interface→impl mapping.
 *
 * `@Singleton` on each binding ensures one shared instance per process — important because
 * each repository holds a reference to the shared [io.github.jan.supabase.SupabaseClient]
 * and we want that connection pool reused.
 *
 * Why a separate module from [AuthModule]: keeping each module focused on one cohesive
 * layer means a single repository can be replaced (e.g. for an instrumented test) by
 * swapping just this module rather than rebinding unrelated dependencies.
 */
// @Module marks this as a Hilt module — a class whose abstract/provider functions tell
// Hilt how to satisfy specific types in the DI graph.
@Module
// @InstallIn(SingletonComponent::class) places these bindings in the application-wide
// scope so they live for the entire process lifetime, matching the @Singleton bindings below.
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // @Binds tells Hilt: "when something asks for PortfolioRepository, give them the
    // SupabasePortfolioRepository instance you already know how to construct via its
    // @Inject constructor". Cheaper than @Provides (no factory body to call).
    @Binds
    // @Singleton makes Hilt cache and reuse the one bound instance, so every collaborator
    // shares the same repository (and the SupabaseClient connection pool inside it).
    @Singleton
    abstract fun bindPortfolioRepository(
        impl: SupabasePortfolioRepository,
    ): PortfolioRepository

    // @Binds: maps the FundRepository interface to the Supabase implementation. UI code
    // (ViewModels in Phase 3+) depends only on the interface and never sees the impl.
    @Binds
    // @Singleton: shared across the app — see note on bindPortfolioRepository.
    @Singleton
    abstract fun bindFundRepository(
        impl: SupabaseFundRepository,
    ): FundRepository

    // @Binds: maps the TransactionRepository interface to the Supabase implementation.
    @Binds
    // @Singleton: shared across the app — see note on bindPortfolioRepository.
    @Singleton
    abstract fun bindTransactionRepository(
        impl: SupabaseTransactionRepository,
    ): TransactionRepository

    // @Binds: maps the ScheduledDepositRepository interface to the Supabase implementation.
    @Binds
    // @Singleton: shared across the app — see note on bindPortfolioRepository.
    @Singleton
    abstract fun bindScheduledDepositRepository(
        impl: SupabaseScheduledDepositRepository,
    ): ScheduledDepositRepository

    // @Binds: maps the DeviceIdStore interface to the SharedPreferences-backed impl.
    @Binds
    // @Singleton: the impl caches the device_id in memory after the first read; multiple
    // instances would each do their own disk I/O and defeat that caching.
    @Singleton
    abstract fun bindDeviceIdStore(
        impl: SharedPrefsDeviceIdStore,
    ): DeviceIdStore
}
