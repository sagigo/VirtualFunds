package com.virtualfunds.domain.repository

/**
 * Provides a stable device identifier for this installation (E2.3, E8.4).
 *
 * Mirrors `desktop/VirtualFunds.Core/Services/IDeviceIdStore.cs`. Each app install must
 * create and persist a stable UUID — passed to `rpc_execute_due_scheduled_deposits` for
 * exactly-once claim coordination (E8.5).
 *
 * The ID is not sensitive; the implementation may use plain `SharedPreferences`
 * (mobile/CLAUDE.md §Local Storage).
 */
interface DeviceIdStore {
    /**
     * Returns the persisted device ID, generating and saving a new one if none exists.
     * Idempotent across calls: subsequent calls always return the same UUID.
     */
    suspend fun getOrCreate(): String
}
