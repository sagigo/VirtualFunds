// androidx.security:security-crypto (EncryptedSharedPreferences + MasterKey) is in maintenance
// mode and the Kotlin compiler flags every reference as deprecated. We deliberately accept this
// for v1 — see the kdoc on SecureSessionManager and mobile/CLAUDE.md §Auth Token Storage. The
// suppression is scoped to this file because the file's sole job is to use that API.
@file:Suppress("DEPRECATION")

package com.virtualfunds.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Persists the Supabase auth session in [EncryptedSharedPreferences] (mobile/CLAUDE.md §Auth Token Storage).
 *
 * This class implements supabase-kt's [SessionManager] interface. Once registered on the `Auth`
 * plugin (see `SupabaseModule`), the SDK calls these methods automatically:
 *   - on sign-in / sign-up         → [saveSession] is called with the new tokens
 *   - on sign-out                  → [deleteSession] is called
 *   - on app start (auto-restore)  → [loadSession] is called and the SDK validates / refreshes
 *                                    the returned session
 *
 * Why EncryptedSharedPreferences:
 *   - Backed by Android Keystore (hardware-backed on most devices), so the session tokens are
 *     encrypted at rest and tied to this app + this device.
 *   - Note: this AndroidX library is in maintenance mode. For v1 we accept it; we may migrate
 *     to a DataStore-based scheme later (mobile/CLAUDE.md §Auth Token Storage).
 *
 * Storage format: a single JSON-encoded UserSession under the key [SESSION_KEY]. UserSession is
 * already `@Serializable` in supabase-kt, so we only need a [Json] runtime to encode/decode.
 */
class SecureSessionManager(context: Context) : SessionManager {

    private val prefs: SharedPreferences = createEncryptedPrefs(context.applicationContext)

    // `ignoreUnknownKeys` insulates us from minor SDK schema additions across upgrades —
    // an old persisted session won't fail to load just because supabase-kt added a new field.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Encodes [session] as JSON and writes it to the encrypted prefs file.
     * Called by supabase-kt's Auth plugin after every successful sign-in/up and after a
     * background refresh produces fresh tokens.
     */
    override suspend fun saveSession(session: UserSession) {
        val encoded = json.encodeToString(UserSession.serializer(), session)
        prefs.edit().putString(SESSION_KEY, encoded).apply()
    }

    /**
     * Reads the persisted session and decodes it. Returns `null` when no session is stored
     * **or** when the stored bytes fail to decode (corrupt prefs, schema mismatch, etc.).
     *
     * Returning `null` on decode failure is deliberate — a corrupt persisted session must
     * NEVER crash the app on startup. The user simply lands on the sign-in screen and can
     * authenticate again. Called by the Auth plugin once during initialization.
     */
    override suspend fun loadSession(): UserSession? {
        val encoded = prefs.getString(SESSION_KEY, null) ?: return null
        return runCatching { json.decodeFromString(UserSession.serializer(), encoded) }.getOrNull()
    }

    /**
     * Removes the persisted session entirely. Called by the Auth plugin on `signOut()` and
     * also when a refresh failure renders the stored tokens unusable.
     */
    override suspend fun deleteSession() {
        prefs.edit().remove(SESSION_KEY).apply()
    }

    private companion object {
        /** Name of the encrypted SharedPreferences file. Created on first use. */
        const val PREFS_FILE_NAME = "vf_secure_session_prefs"
        /** Single key under which the JSON-encoded UserSession is stored. */
        const val SESSION_KEY = "supabase_user_session"

        fun createEncryptedPrefs(appContext: Context): SharedPreferences {
            // MasterKey wraps an Android Keystore key with the AES256_GCM scheme. The key is
            // generated lazily on first use and persists for the lifetime of the app install.
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
