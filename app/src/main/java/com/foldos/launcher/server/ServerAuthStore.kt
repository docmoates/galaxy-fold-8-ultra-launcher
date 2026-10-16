package com.foldos.launcher.server

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the Crosspoint server sign-in: the bearer token and who it belongs to. Backed by
 * `EncryptedSharedPreferences` (AES256-GCM under an Android Keystore master key); when the Keystore is
 * unusable it falls back to plain app-private `SharedPreferences`, which [encrypted] reports.
 *
 * The preference files keep the names `foldos_mail_auth` / `foldos_mail_auth_plain` that the backup and
 * device-transfer rules already exclude, so the token never leaves the phone through a backup. The token is
 * never written to `LauncherSettings` and never logged.
 */
class ServerAuthStore(context: Context) {

    private val appContext: Context = context.applicationContext

    /** True when the token lives in `EncryptedSharedPreferences`; false on the plain-prefs fallback. */
    val encrypted: Boolean

    private val prefs: SharedPreferences

    init {
        val secure: SharedPreferences? = runCatching {
            val masterKey = MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { Log.w(TAG, "EncryptedSharedPreferences unavailable; using plain prefs") }.getOrNull()
        encrypted = secure != null
        prefs = secure ?: appContext.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
        // Builds before 1.2.4 kept Google (AppAuth) tokens here; they are useless now.
        if (prefs.contains(LEGACY_AUTH_STATE)) prefs.edit().remove(LEGACY_AUTH_STATE).remove(LEGACY_CLIENT_ID).apply()
    }

    private fun get(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    private fun put(key: String, value: String?) {
        prefs.edit().apply { if (value.isNullOrBlank()) remove(key) else putString(key, value) }.apply()
    }

    var token: String?
        get() = get(KEY_TOKEN)
        set(value) = put(KEY_TOKEN, value)

    /** Crosspoint account email. */
    var email: String?
        get() = get(KEY_EMAIL)
        set(value) = put(KEY_EMAIL, value)

    var name: String?
        get() = get(KEY_NAME)
        set(value) = put(KEY_NAME, value)

    /** Linked Gmail address as last reported by the server. */
    var gmailAccount: String?
        get() = get(KEY_GMAIL)
        set(value) = put(KEY_GMAIL, value)

    val signedIn: Boolean get() = token != null

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_EMAIL).remove(KEY_NAME).remove(KEY_GMAIL).apply()
    }

    private companion object {
        const val TAG = "ServerAuthStore"
        const val ENCRYPTED_PREFS_NAME = "foldos_mail_auth"
        const val PLAIN_PREFS_NAME = "foldos_mail_auth_plain"
        const val KEY_TOKEN = "server_token"
        const val KEY_EMAIL = "server_email"
        const val KEY_NAME = "server_name"
        const val KEY_GMAIL = "server_gmail"
        const val LEGACY_AUTH_STATE = "auth_state"
        const val LEGACY_CLIENT_ID = "client_id"
    }
}
