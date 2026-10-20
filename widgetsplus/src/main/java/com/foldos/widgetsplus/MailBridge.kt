package com.foldos.widgetsplus

import android.content.Context
import android.net.Uri

/** One inbox row from FoldOS Hub; [date] is epoch millis. */
data class MailRow(val id: String, val sender: String, val subject: String, val snippet: String, val date: Long, val unread: Boolean)

/** What the FoldOS Hub mail bridge answered. */
sealed interface MailResult {
    /** FoldOS is signed in to the Crosspoint server and Gmail is linked. */
    data class Connected(val unread: Int, val rows: List<MailRow>) : MailResult

    /** FoldOS is not installed, or this build of it has no bridge. */
    data object NotInstalled : MailResult

    /** The signature permission was refused (the two apps are signed with different keys). */
    data object NoAccess : MailResult

    data object SignedOut : MailResult
    data object NotLinked : MailResult
    data object NotConfigured : MailResult
    data object Connecting : MailResult
    data class Error(val message: String) : MailResult
}

/**
 * Reads the Hub's inbox from FoldOS (`content://com.foldos.launcher.mailbridge/inbox`). FoldOS holds the
 * Crosspoint sign-in; this app never sees the token. Call from a worker thread: FoldOS may fetch from the
 * server before it answers (up to about eight seconds).
 */
object MailBridge {
    private val INBOX: Uri = Uri.parse("content://com.foldos.launcher.mailbridge/inbox")

    fun read(context: Context, limit: Int): MailResult {
        return try {
            val cursor = context.contentResolver.query(INBOX.buildUpon().appendQueryParameter("limit", limit.toString()).build(), null, null, null, null)
                ?: return MailResult.NotInstalled
            cursor.use { c ->
                val extras = c.extras
                val rows = ArrayList<MailRow>()
                val iId = c.getColumnIndex("id")
                val iFrom = c.getColumnIndex("sender")
                val iSubject = c.getColumnIndex("subject")
                val iSnippet = c.getColumnIndex("snippet")
                val iDate = c.getColumnIndex("date")
                val iUnread = c.getColumnIndex("unread")
                while (c.moveToNext()) {
                    rows += MailRow(
                        id = c.getString(iId).orEmpty(),
                        sender = c.getString(iFrom).orEmpty(),
                        subject = c.getString(iSubject).orEmpty(),
                        snippet = c.getString(iSnippet).orEmpty(),
                        date = c.getLong(iDate),
                        unread = c.getInt(iUnread) == 1,
                    )
                }
                when (extras?.getString("state")) {
                    "connected" -> MailResult.Connected(extras.getInt("unread"), rows)
                    "signed_out" -> MailResult.SignedOut
                    "not_linked" -> MailResult.NotLinked
                    "not_configured" -> MailResult.NotConfigured
                    "connecting" -> MailResult.Connecting
                    "error" -> MailResult.Error(extras.getString("message") ?: "Mail is unavailable.")
                    else -> MailResult.Error("FoldOS did not answer.")
                }
            }
        } catch (_: SecurityException) {
            MailResult.NoAccess
        } catch (_: IllegalArgumentException) {
            MailResult.NotInstalled
        } catch (e: Exception) {
            MailResult.Error("Could not read mail from FoldOS.")
        }
    }
}
