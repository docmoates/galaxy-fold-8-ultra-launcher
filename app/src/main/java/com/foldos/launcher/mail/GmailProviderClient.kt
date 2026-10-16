package com.foldos.launcher.mail

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Unread / total counts of one Gmail label, read from the Gmail app's public content provider.
 *
 * @property account the Google account (e-mail address) the label belongs to.
 * @property canonicalName Gmail's stable label id, e.g. [GmailProviderClient.LABEL_INBOX] "^i".
 * @property name the display name ("Inbox", "Updates", a user label).
 */
data class GmailLabelCount(
    val account: String,
    val canonicalName: String,
    val name: String,
    val unread: Int,
    val total: Int,
) {
    /** True for the system categories / labels the Mail tab lists; user labels are extra. */
    val isSystem: Boolean get() = canonicalName.startsWith("^")
}

/**
 * Reads unread counts from the Gmail app's content provider (`content://com.google.android.gm/`),
 * which works without any Google Cloud project or OAuth client. Needs the manifest permission
 * `com.google.android.gm.permission.READ_CONTENT_PROVIDER` (normal protection level, granted at
 * install) and the Gmail app; every call returns empty results otherwise.
 *
 * Accounts come from [AccountManager] (`getAccountsByType("com.google")`, which on API 26+ lists
 * the Google accounts visible to this app without GET_ACCOUNTS) plus the account the user picked
 * through [chooseAccountIntent], which the system makes visible to the app on selection.
 */
class GmailProviderClient(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True when the Gmail app is installed. */
    fun gmailInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(GMAIL_PACKAGE, 0)
    }.isSuccess

    /**
     * Google accounts known to this app: the ones [AccountManager] reports plus the one picked
     * with [chooseAccountIntent] (persisted). Never throws.
     */
    fun accounts(): List<String> {
        val fromManager = runCatching {
            AccountManager.get(appContext).getAccountsByType(GOOGLE_ACCOUNT_TYPE).map { it.name }
        }.getOrDefault(emptyList())
        val chosen = chosenAccount()
        return (fromManager + listOfNotNull(chosen)).distinct()
    }

    /** The account picked through [chooseAccountIntent], or null. */
    fun chosenAccount(): String? = prefs.getString(KEY_CHOSEN_ACCOUNT, null)?.takeIf { it.isNotBlank() }

    /** Remembers the account picked from the system chooser ([AccountManager.KEY_ACCOUNT_NAME]). */
    fun setChosenAccount(name: String?) {
        prefs.edit().apply {
            if (name.isNullOrBlank()) remove(KEY_CHOSEN_ACCOUNT) else putString(KEY_CHOSEN_ACCOUNT, name)
        }.apply()
    }

    /**
     * The system account chooser limited to Google accounts. Launch it with
     * `ActivityResultContracts.StartActivityForResult()`; the result's
     * `getStringExtra(AccountManager.KEY_ACCOUNT_NAME)` is the picked account, to pass to
     * [setChosenAccount].
     */
    fun chooseAccountIntent(): Intent = AccountManager.newChooseAccountIntent(
        null,
        null,
        arrayOf(GOOGLE_ACCOUNT_TYPE),
        null,
        null,
        null,
        null,
    )

    /**
     * Label counts for [account], system labels first in [SYSTEM_LABEL_ORDER] order, then user
     * labels A-Z. Empty when the provider is unavailable (no Gmail, permission missing, unknown
     * account).
     */
    suspend fun labelCounts(account: String): List<GmailLabelCount> = withContext(Dispatchers.IO) {
        queryLabels(account)
    }

    /** Label counts for every account from [accounts]. */
    suspend fun allLabelCounts(): List<GmailLabelCount> = withContext(Dispatchers.IO) {
        accounts().flatMap { queryLabels(it) }
    }

    /** Unread inbox count per account (`^i`); accounts without a readable provider are absent. */
    suspend fun unreadInbox(): Map<String, Int> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, Int>()
        accounts().forEach { account ->
            queryLabels(account).firstOrNull { it.canonicalName == LABEL_INBOX }?.let { result[account] = it.unread }
        }
        result
    }

    /**
     * [allLabelCounts] re-read whenever Gmail's label table changes (a `ContentObserver` per
     * account) and on every collection start. Emits an empty list when nothing is readable.
     */
    fun observeLabelCounts(): Flow<List<GmailLabelCount>> = callbackFlow<Unit> {
        val resolver = appContext.contentResolver
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        accounts().forEach { account ->
            runCatching { resolver.registerContentObserver(labelsUri(account), true, observer) }
        }
        trySend(Unit)
        awaitClose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
        .map { accounts().flatMap { account -> queryLabels(account) } }
        .flowOn(Dispatchers.IO)

    private fun queryLabels(account: String): List<GmailLabelCount> {
        val result = ArrayList<GmailLabelCount>()
        runCatching {
            appContext.contentResolver.query(labelsUri(account), PROJECTION, null, null, null)?.use { cursor ->
                val canonicalIdx = cursor.getColumnIndex(COLUMN_CANONICAL_NAME)
                val nameIdx = cursor.getColumnIndex(COLUMN_NAME)
                val unreadIdx = cursor.getColumnIndex(COLUMN_NUM_UNREAD)
                val totalIdx = cursor.getColumnIndex(COLUMN_NUM_CONVERSATIONS)
                while (cursor.moveToNext()) {
                    val canonical = if (canonicalIdx >= 0) cursor.getString(canonicalIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: canonical else canonical
                    val unread = if (unreadIdx >= 0) cursor.getInt(unreadIdx) else 0
                    val total = if (totalIdx >= 0) cursor.getInt(totalIdx) else 0
                    result.add(GmailLabelCount(account, canonical, displayNameFor(canonical, name), unread, total))
                }
            }
        }
        return result.sortedWith(
            compareBy<GmailLabelCount> { label ->
                val idx = SYSTEM_LABEL_ORDER.indexOf(label.canonicalName)
                if (idx >= 0) idx else if (label.isSystem) SYSTEM_LABEL_ORDER.size else SYSTEM_LABEL_ORDER.size + 1
            }.thenBy { it.name.lowercase() },
        )
    }

    private fun displayNameFor(canonical: String, name: String): String = when (canonical) {
        LABEL_INBOX -> "Inbox"
        LABEL_PRIMARY -> "Primary"
        LABEL_SOCIAL -> "Social"
        LABEL_PROMOTIONS -> "Promotions"
        LABEL_UPDATES -> "Updates"
        LABEL_FORUMS -> "Forums"
        LABEL_STARRED -> "Starred"
        LABEL_IMPORTANT -> "Important"
        LABEL_UNREAD -> "Unread"
        LABEL_PRIORITY_INBOX -> "Priority inbox"
        LABEL_ALL_MAIL -> "All mail"
        LABEL_SENT -> "Sent"
        LABEL_DRAFTS -> "Drafts"
        LABEL_SPAM -> "Spam"
        LABEL_TRASH -> "Trash"
        else -> name.ifBlank { canonical }
    }

    companion object {
        const val GMAIL_PACKAGE: String = "com.google.android.gm"
        const val AUTHORITY: String = "com.google.android.gm"

        /** Manifest permission that unlocks the provider (normal level; declare it, no runtime request). */
        const val PERMISSION: String = "com.google.android.gm.permission.READ_CONTENT_PROVIDER"
        const val GOOGLE_ACCOUNT_TYPE: String = "com.google"

        // Canonical label names of Gmail's public provider (GmailContract.Labels).
        const val LABEL_INBOX: String = "^i"
        const val LABEL_PRIMARY: String = "^sq_ig_i_personal"
        const val LABEL_SOCIAL: String = "^sq_ig_i_social"
        const val LABEL_PROMOTIONS: String = "^sq_ig_i_promo"
        const val LABEL_UPDATES: String = "^sq_ig_i_notification"
        const val LABEL_FORUMS: String = "^sq_ig_i_group"
        const val LABEL_STARRED: String = "^t"
        const val LABEL_IMPORTANT: String = "^io_im"
        const val LABEL_UNREAD: String = "^u"
        const val LABEL_PRIORITY_INBOX: String = "^iim"
        const val LABEL_ALL_MAIL: String = "^all"
        const val LABEL_SENT: String = "^f"
        const val LABEL_DRAFTS: String = "^r"
        const val LABEL_SPAM: String = "^s"
        const val LABEL_TRASH: String = "^k"

        /** Display order of the system labels the Mail tab shows. */
        val SYSTEM_LABEL_ORDER: List<String> = listOf(
            LABEL_INBOX, LABEL_PRIMARY, LABEL_UPDATES, LABEL_SOCIAL, LABEL_PROMOTIONS, LABEL_FORUMS,
            LABEL_STARRED, LABEL_IMPORTANT, LABEL_DRAFTS, LABEL_SENT,
        )

        private const val COLUMN_CANONICAL_NAME = "canonicalName"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_NUM_CONVERSATIONS = "numConversations"
        private const val COLUMN_NUM_UNREAD = "numUnreadConversations"
        private val PROJECTION = arrayOf(COLUMN_CANONICAL_NAME, COLUMN_NAME, COLUMN_NUM_CONVERSATIONS, COLUMN_NUM_UNREAD)

        private const val PREFS_NAME = "foldos_mail"
        private const val KEY_CHOSEN_ACCOUNT = "provider_account"

        /** `content://com.google.android.gm/<account>/labels`. */
        fun labelsUri(account: String): Uri =
            Uri.parse("content://$AUTHORITY/").buildUpon().appendPath(account).appendPath("labels").build()
    }
}
