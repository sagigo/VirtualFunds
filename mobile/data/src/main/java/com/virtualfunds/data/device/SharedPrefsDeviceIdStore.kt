package com.virtualfunds.data.device

import android.content.Context
import android.content.SharedPreferences
import com.virtualfunds.domain.repository.DeviceIdStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the device's stable UUID in plain `SharedPreferences` (E2.3, E8.4).
 *
 * Why not [androidx.security.crypto.EncryptedSharedPreferences]: the device_id is not
 * sensitive — it is only used as a claim-coordination key in `rpc_execute_due_scheduled_deposits`
 * (E8.5). The session token *is* sensitive and lives in `EncryptedSharedPreferences`
 * (see [com.virtualfunds.data.auth.SecureSessionManager]). Splitting them keeps each store
 * minimal. (mobile/CLAUDE.md §Local Storage — explicit on this point.)
 *
 * Concurrency: an [internalMutex] serialises read-then-write so two threads racing to
 * call [getOrCreate] on a fresh install can never produce two different IDs. After the ID
 * is written the mutex is essentially uncontended (we just return the cached value).
 *
 * @Inject + @Singleton: Hilt provides the [Context] (Application) and constructs a single
 * shared instance — there's no benefit to multiple instances and the mutex would not be
 * shared if there were.
 */
// @Singleton tells Hilt to keep one shared instance — the in-memory [cached] field and
// the [mutex] would not be shared across multiple instances, defeating both caching and
// the read-then-write serialisation guarantee.
@Singleton
class SharedPrefsDeviceIdStore
    // @Inject on the constructor lets Hilt's generated factory provide [context] from
    // the DI graph.
    @Inject constructor(
    // @ApplicationContext yields the long-lived Application context (not an Activity
    // context). Safe to hold from a @Singleton because both have the same lifetime.
    @ApplicationContext private val context: Context,
) : DeviceIdStore {

    private val prefs: SharedPreferences by lazy {
        // MODE_PRIVATE is the default and means "only this app's UID can read this file".
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val mutex = Mutex()

    @Volatile
    private var cached: String? = null

    override suspend fun getOrCreate(): String {
        // Fast path: already loaded into memory.
        cached?.let { return it }

        return mutex.withLock {
            // Re-check inside the lock in case another coroutine populated [cached]
            // while we waited.
            cached?.let { return@withLock it }

            // SharedPreferences disk I/O is small but not free — hop off the main
            // dispatcher to keep the UI thread free even in the rare cold-start case.
            withContext(Dispatchers.IO) {
                val existing = prefs.getString(KEY_DEVICE_ID, null)
                if (existing != null) {
                    cached = existing
                    return@withContext existing
                }

                val freshId = UUID.randomUUID().toString()
                // commit() is synchronous (we want the value durable before returning).
                // The next call short-circuits on [cached] so we only pay this cost once.
                prefs.edit().putString(KEY_DEVICE_ID, freshId).commit()
                cached = freshId
                freshId
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "virtual_funds_device"
        const val KEY_DEVICE_ID = "device_id"
    }
}
