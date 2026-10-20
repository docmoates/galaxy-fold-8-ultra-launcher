package com.foldos.launcher.mail

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.repo.MailState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lets the Widgets + app (same repository, same signing key) show the Hub's inbox without its own sign-in.
 * Widgets + asks this provider; the Crosspoint token stays in [com.foldos.launcher.server.ServerAuthStore]
 * and never leaves FoldOS. Only apps holding the signature permission `com.foldos.launcher.permission.MAIL_BRIDGE`
 * (granted to apps signed with the same key) can query it, and it is read only.
 *
 * `content://com.foldos.launcher.mailbridge/inbox?limit=5` returns up to `limit` inbox rows (columns [COLUMNS],
 * newest first, only when the state is connected). The cursor extras carry [EXTRA_STATE] (one of the `STATE_*`
 * values), [EXTRA_UNREAD] and, for problems, [EXTRA_MESSAGE].
 */
class MailBridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, 20) ?: 5
        val mail = FoldOsApplication.from(context).graph.mail
        // Pull a fresh inbox (the widget asks at most every few minutes), then wait for a settled state.
        val state: MailState = runBlocking {
            withTimeoutOrNull(WAIT_MILLIS) {
                mail.refresh()
                mail.state.first { it !is MailState.Connecting }
            }
        } ?: MailState.Connecting

        val cursor = MatrixCursor(COLUMNS)
        val extras = Bundle()
        when (state) {
            is MailState.Connected -> {
                extras.putString(EXTRA_STATE, STATE_CONNECTED)
                extras.putInt(EXTRA_UNREAD, state.unread)
                state.messages.take(limit).forEach { m ->
                    cursor.addRow(arrayOf(m.id, m.from, m.subject, m.snippet, m.date, if (m.unread) 1 else 0))
                }
            }
            MailState.SignedOut -> extras.putString(EXTRA_STATE, STATE_SIGNED_OUT)
            MailState.NotLinked -> extras.putString(EXTRA_STATE, STATE_NOT_LINKED)
            MailState.NotConfigured -> extras.putString(EXTRA_STATE, STATE_NOT_CONFIGURED)
            MailState.Connecting -> extras.putString(EXTRA_STATE, STATE_CONNECTING)
            is MailState.Error -> {
                extras.putString(EXTRA_STATE, STATE_ERROR)
                extras.putString(EXTRA_MESSAGE, state.message)
            }
        }
        cursor.extras = extras
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.com.foldos.launcher.mail"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY: String = "com.foldos.launcher.mailbridge"
        val COLUMNS = arrayOf("id", "sender", "subject", "snippet", "date", "unread")

        const val EXTRA_STATE: String = "state"
        const val EXTRA_UNREAD: String = "unread"
        const val EXTRA_MESSAGE: String = "message"

        const val STATE_CONNECTED: String = "connected"
        const val STATE_SIGNED_OUT: String = "signed_out"
        const val STATE_NOT_LINKED: String = "not_linked"
        const val STATE_NOT_CONFIGURED: String = "not_configured"
        const val STATE_CONNECTING: String = "connecting"
        const val STATE_ERROR: String = "error"

        private const val WAIT_MILLIS: Long = 8_000
    }
}
