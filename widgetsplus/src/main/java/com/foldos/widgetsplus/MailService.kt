package com.foldos.widgetsplus

import android.content.Context
import com.foldos.widgetsplus.server.ServerAuthStore
import com.foldos.widgetsplus.server.ServerClient
import com.foldos.widgetsplus.server.ServerException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** One inbox row; [date] is epoch millis. */
data class MailRow(val id: String, val threadId: String, val sender: String, val subject: String, val snippet: String, val date: Long, val unread: Boolean)

/** What the Crosspoint server said about the inbox. */
sealed interface MailResult {
    /** Signed in and Gmail linked. [offline] is true when the rows are the last copy because the server could not be reached. */
    data class Connected(val unread: Int, val rows: List<MailRow>, val gmail: String?, val offline: Boolean = false) : MailResult

    data object SignedOut : MailResult
    data object NotLinked : MailResult
    data object NotConfigured : MailResult
    data class Error(val message: String) : MailResult
}

/** Who is signed in, as last reported by the server. */
data class AccountStatus(val signedInAs: String?, val gmail: String?, val linked: Boolean, val available: Boolean, val needsRelink: Boolean)

/**
 * Widgets + signs in to the Crosspoint server on its own, with the same account people use in FoldOS, and reads
 * the inbox from the server. Nothing here depends on FoldOS being installed. The token lives in
 * [ServerAuthStore] (encrypted). Every function blocks on the network: call them from a worker thread.
 */
object MailService {
    private const val UNREACHABLE = "Can't reach the Crosspoint server. Check your connection."
    const val SESSION_ENDED = "Your sign-in ended. Please sign in again."

    @Volatile
    private var cachedStore: ServerAuthStore? = null

    @Synchronized
    fun store(context: Context): ServerAuthStore =
        cachedStore ?: ServerAuthStore(context.applicationContext).also { cachedStore = it }

    private fun client(context: Context): ServerClient = ServerClient { store(context).serverUrl }

    fun isSignedIn(context: Context): Boolean = store(context).signedIn

    /** The account as saved on this phone (no network). */
    fun savedAccount(context: Context): AccountStatus {
        val s = store(context)
        return AccountStatus(s.email.takeIf { s.signedIn }, s.gmailAccount, s.gmailAccount != null, true, false)
    }

    /** Signs in; returns null on success or a friendly error. */
    fun signIn(context: Context, email: String, password: String, serverUrl: String?): String? {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.isEmpty()) return "Enter your email and password."
        val s = store(context)
        s.serverUrl = serverUrl?.trim()?.takeIf { it.isNotEmpty() && ServerClient.normalizeBase(it) != ServerClient.DEFAULT_BASE_URL }
        return try {
            val session = client(context).login(cleanEmail, password)
            val token = session.token ?: return "The server did not return a sign-in. Please try again."
            s.token = token
            s.email = session.email.ifBlank { cleanEmail }
            s.name = session.name
            s.gmailAccount = if (session.mail.linked) session.mail.account else null
            null
        } catch (e: ServerException) {
            when {
                e.status == 401 -> "That email and password did not match an account."
                e.status == 429 -> e.message?.takeIf { it.isNotBlank() } ?: "Too many tries. Please wait 15 minutes."
                e.status >= 500 -> "The server had a problem. Please try again in a moment."
                else -> e.message ?: "Could not sign in."
            }
        } catch (_: IOException) {
            UNREACHABLE
        } catch (_: Exception) {
            "Could not sign in. Please try again."
        }
    }

    fun signOut(context: Context) {
        val s = store(context)
        val token = s.token
        if (token != null) runCatching { client(context).logout(token) }
        s.clear()
    }

    /** Asks the server for the one-time Gmail link page; returns the URL to open, or throws [IllegalStateException] with text to show. */
    fun linkUrl(context: Context): String {
        val token = store(context).token ?: throw IllegalStateException("Sign in first.")
        return try {
            client(context).mailLink(token)
        } catch (e: ServerException) {
            if (e.unauthorized) {
                store(context).clear()
                throw IllegalStateException(SESSION_ENDED)
            }
            throw IllegalStateException(if (e.status == 503) "The server has no mail set up yet." else e.message ?: "Could not start the Gmail link.")
        } catch (_: IOException) {
            throw IllegalStateException(UNREACHABLE)
        }
    }

    /** Asks the server who is signed in and whether Gmail is linked; null when it cannot be reached. */
    fun refreshAccount(context: Context): AccountStatus? {
        val s = store(context)
        val token = s.token ?: return AccountStatus(null, null, false, true, false)
        return try {
            val mail = client(context).me(token).mail
            s.gmailAccount = if (mail.linked) mail.account else null
            AccountStatus(s.email, s.gmailAccount, mail.linked && !mail.needsRelink, mail.available, mail.linked && mail.needsRelink)
        } catch (e: ServerException) {
            if (e.unauthorized) {
                s.clear()
                AccountStatus(null, null, false, true, false)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** The inbox for the widget and the AI: fresh from the server, or the last copy when the server is unreachable. */
    fun inbox(context: Context, limit: Int): MailResult {
        val s = store(context)
        val token = s.token ?: return MailResult.SignedOut
        return try {
            val inbox = client(context).inbox(token, limit = limit.coerceIn(1, 25), fresh = true)
            val rows = inbox.messages.map {
                MailRow(it.id, it.threadId, it.fromName, it.subject, it.snippet, it.receivedAt, it.unread)
            }
            if (inbox.account != null) s.gmailAccount = inbox.account
            s.inboxCache = encode(inbox.unread, rows)
            MailResult.Connected(inbox.unread, rows, s.gmailAccount)
        } catch (e: ServerException) {
            when {
                e.unauthorized -> {
                    s.clear()
                    MailResult.SignedOut
                }
                e.status == 409 || e.code == "not_linked" -> MailResult.NotLinked
                e.status == 503 -> MailResult.NotConfigured
                e.code == "relink_required" -> MailResult.Error("Reconnect Gmail in Widgets + (open the app).")
                else -> MailResult.Error(e.message ?: "Could not load your mail.")
            }
        } catch (_: IOException) {
            decode(s.inboxCache, s.gmailAccount)?.let { return it }
            MailResult.Error(UNREACHABLE)
        } catch (_: Exception) {
            MailResult.Error("Could not load your mail.")
        }
    }

    private fun encode(unread: Int, rows: List<MailRow>): String {
        val array = JSONArray()
        rows.forEach {
            array.put(
                JSONObject().put("id", it.id).put("thread", it.threadId).put("from", it.sender).put("subject", it.subject)
                    .put("snippet", it.snippet).put("date", it.date).put("unread", it.unread),
            )
        }
        return JSONObject().put("unread", unread).put("rows", array).toString()
    }

    private fun decode(raw: String?, gmail: String?): MailResult.Connected? {
        if (raw == null) return null
        return runCatching {
            val json = JSONObject(raw)
            val array = json.getJSONArray("rows")
            val rows = (0 until array.length()).map {
                val o = array.getJSONObject(it)
                MailRow(o.optString("id"), o.optString("thread"), o.optString("from"), o.optString("subject"), o.optString("snippet"), o.optLong("date"), o.optBoolean("unread"))
            }
            MailResult.Connected(json.optInt("unread"), rows, gmail, offline = true)
        }.getOrNull()
    }
}
