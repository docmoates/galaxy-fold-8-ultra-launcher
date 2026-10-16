package com.foldos.launcher.mail

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the Gmail API sign-in: the AppAuth `AuthState` JSON (access + refresh tokens) and the
 * signed-in address. Backed by `EncryptedSharedPreferences` (androidx.security-crypto, AES256-GCM
 * values under an Android Keystore master key); when the Keystore is unusable (some emulators,
 * a restored backup whose key no longer exists) it falls back to plain app-private
 * `SharedPreferences`, which [encrypted] reports so Settings can say so.
 *
 * Exclude `foldos_mail_auth*.xml` from Auto Backup: encrypted values cannot be decrypted on
 * another device and would only produce a failed restore.
 */
class GmailAuthStore(context: Context) {

    private val appContext: Context = context.applicationContext

    /** True when tokens live in `EncryptedSharedPreferences`; false on the plain-prefs fallback. */
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
        }.onFailure { Log.w(TAG, "EncryptedSharedPreferences unavailable; using plain prefs", it) }.getOrNull()
        encrypted = secure != null
        prefs = secure ?: appContext.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Serialized `net.openid.appauth.AuthState`, or null when signed out. */
    var authStateJson: String?
        get() = prefs.getString(KEY_AUTH_STATE, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_AUTH_STATE) else putString(KEY_AUTH_STATE, value)
            }.apply()
        }

    /** The signed-in Gmail address (`users/me/profile`), or null until the first inbox load. */
    var accountEmail: String?
        get() = prefs.getString(KEY_ACCOUNT_EMAIL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_ACCOUNT_EMAIL) else putString(KEY_ACCOUNT_EMAIL, value)
            }.apply()
        }

    /** The OAuth client id the stored tokens were issued for; a different id invalidates them. */
    var clientId: String?
        get() = prefs.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_CLIENT_ID) else putString(KEY_CLIENT_ID, value)
            }.apply()
        }

    val signedIn: Boolean get() = authStateJson != null

    /** Forgets tokens and the account; the plain-prefs file is cleared too. */
    fun clear() {
        prefs.edit().remove(KEY_AUTH_STATE).remove(KEY_ACCOUNT_EMAIL).remove(KEY_CLIENT_ID).apply()
    }

    private companion object {
        const val TAG = "GmailAuthStore"
        const val ENCRYPTED_PREFS_NAME = "foldos_mail_auth"
        const val PLAIN_PREFS_NAME = "foldos_mail_auth_plain"
        const val KEY_AUTH_STATE = "auth_state"
        const val KEY_ACCOUNT_EMAIL = "account_email"
        const val KEY_CLIENT_ID = "client_id"
    }
}
