package com.foldos.launcher.server

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A non-2xx answer from the Crosspoint server. [code] is the JSON `error` field, [message] is safe to show. */
class ServerException(val status: Int, val code: String?, message: String) : Exception(message) {
    /** The token is dead (or the credentials are wrong on login). */
    val unauthorized: Boolean get() = status == 401
}

/** The `mail` block the server returns from login, `/me` and unlink. */
data class ServerMailInfo(val available: Boolean, val linked: Boolean, val account: String?, val needsRelink: Boolean = false)

/** The `subscriber` block plus its [mail] state; [token] is only set by [ServerClient.login]. */
data class ServerSession(
    val token: String?,
    val email: String,
    val name: String?,
    val mail: ServerMailInfo,
)

data class ServerInboxMessage(
    val id: String,
    val threadId: String,
    val fromName: String,
    val fromEmail: String,
    val subject: String,
    val snippet: String,
    val receivedAt: Long,
    val unread: Boolean,
    val starred: Boolean,
    val labels: Set<String>,
)

data class ServerAddress(val name: String, val email: String)

data class ServerAttachment(val name: String, val mime: String, val size: Long)

data class ServerMessage(
    val id: String,
    val threadId: String,
    val fromName: String,
    val fromEmail: String,
    val to: List<ServerAddress>,
    val cc: List<ServerAddress>,
    val subject: String,
    val date: Long,
    val unread: Boolean,
    val starred: Boolean,
    val labels: Set<String>,
    val bodyText: String,
    val hasHtml: Boolean,
    /** Server-sanitized HTML body (strict allow-list), null when the message has none. Never logged. */
    val bodyHtml: String? = null,
    /** True when [bodyHtml] references remote https images. */
    val remoteContent: Boolean = false,
    val attachments: List<ServerAttachment>,
    val messageIdHeader: String?,
)

data class ServerInbox(val account: String?, val unread: Int, val messages: List<ServerInboxMessage>)

/**
 * Small client for the Crosspoint server's `/api/v1/foldos` endpoints (HttpURLConnection + org.json, 10 s
 * timeouts, always on Dispatchers.IO). Every call throws [ServerException] for a non-2xx answer and
 * `java.io.IOException` when the server cannot be reached. Never logs tokens, passwords or bodies. More
 * server-backed features can add their calls here.
 *
 * [baseUrl] is read on every call so a changed server address in Settings applies immediately.
 */
class ServerClient(private val baseUrl: suspend () -> String?) {

    suspend fun login(email: String, password: String): ServerSession {
        val body = JSONObject().put("email", email).put("password", password).put("device", deviceName())
        return session(call("POST", "/api/v1/foldos/login", null, body))
    }

    suspend fun logout(token: String) {
        call("POST", "/api/v1/foldos/logout", token, JSONObject())
    }

    suspend fun me(token: String): ServerSession = session(call("GET", "/api/v1/foldos/me", token, null))

    /** Returns the browser URL that links Gmail once. */
    suspend fun mailLink(token: String): String {
        val reply = call("POST", "/api/v1/foldos/mail/link", token, JSONObject())
        return reply.optString("url").ifBlank { throw ServerException(502, null, "The server did not return a link page.") }
    }

    suspend fun mailUnlink(token: String): ServerMailInfo =
        mailInfo(call("POST", "/api/v1/foldos/mail/unlink", token, JSONObject()).optJSONObject("mail"))

    suspend fun inbox(token: String, limit: Int = 25, fresh: Boolean = false): ServerInbox {
        val path = "/api/v1/foldos/mail/inbox?limit=$limit" + if (fresh) "&fresh=1" else ""
        val reply = call("GET", path, token, null)
        val array = reply.optJSONArray("messages")
        val messages = buildList {
            if (array != null) for (i in 0 until array.length()) {
                val m = array.optJSONObject(i) ?: continue
                val labelsArray = m.optJSONArray("labels")
                val labels = buildSet {
                    if (labelsArray != null) for (j in 0 until labelsArray.length()) add(labelsArray.optString(j))
                }
                val fromEmail = m.optString("from_email")
                add(
                    ServerInboxMessage(
                        id = m.optString("id"),
                        threadId = m.optString("thread_id"),
                        fromName = m.optString("from_name").ifBlank { fromEmail },
                        fromEmail = fromEmail,
                        subject = m.optString("subject").ifBlank { "(no subject)" },
                        snippet = m.optString("snippet"),
                        receivedAt = m.optLong("received_at", 0L),
                        unread = m.optBoolean("unread", false),
                        starred = m.optBoolean("starred", false),
                        labels = labels,
                    ),
                )
            }
        }
        return ServerInbox(
            account = reply.optString("account").ifBlank { null },
            unread = reply.optInt("unread", messages.count { it.unread }),
            messages = messages.filter { it.id.isNotEmpty() },
        )
    }

    /** The full message; 409 `relink_required` means Gmail has to be linked again with the wider permission. */
    suspend fun getMessage(token: String, id: String): ServerMessage {
        val m = call("GET", "/api/v1/foldos/mail/messages/" + URLEncoder.encode(id, "UTF-8"), token, null)
        fun addresses(key: String): List<ServerAddress> {
            val array = m.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val a = array.optJSONObject(i) ?: continue
                    val email = a.optString("email")
                    if (email.isNotBlank()) add(ServerAddress(a.optString("name").ifBlank { email }, email))
                }
            }
        }
        val labelsArray = m.optJSONArray("labels")
        val labels = buildSet {
            if (labelsArray != null) for (j in 0 until labelsArray.length()) add(labelsArray.optString(j))
        }
        val attachmentsArray = m.optJSONArray("attachments")
        val attachments = buildList {
            if (attachmentsArray != null) for (k in 0 until attachmentsArray.length()) {
                val a = attachmentsArray.optJSONObject(k) ?: continue
                add(ServerAttachment(a.optString("name").ifBlank { "attachment" }, a.optString("mime"), a.optLong("size", 0L)))
            }
        }
        val fromEmail = m.optString("from_email")
        return ServerMessage(
            id = m.optString("id").ifBlank { id },
            threadId = m.optString("thread_id"),
            fromName = m.optString("from_name").ifBlank { fromEmail },
            fromEmail = fromEmail,
            to = addresses("to"),
            cc = addresses("cc"),
            subject = m.optString("subject").ifBlank { "(no subject)" },
            date = m.optLong("date", 0L),
            unread = m.optBoolean("unread", false),
            starred = m.optBoolean("starred", false),
            labels = labels,
            bodyText = m.optString("body_text"),
            hasHtml = m.optBoolean("has_html", false),
            bodyHtml = if (m.isNull("body_html")) null else m.optString("body_html").ifBlank { null },
            remoteContent = m.optBoolean("remote_content", false),
            attachments = attachments,
            messageIdHeader = m.optString("message_id_header").ifBlank { null },
        )
    }

    /** Sends a message; throws [ServerException] (400 `send_failed` or a validation message, 409 `relink_required`). */
    suspend fun send(
        token: String,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        body: String,
        replyToMessageId: String?,
        threadId: String?,
    ) {
        val json = JSONObject()
            .put("to", JSONArray(to))
            .put("cc", JSONArray(cc))
            .put("bcc", JSONArray(bcc))
            .put("subject", subject)
            .put("body", body)
            .put("reply_to_message_id", replyToMessageId ?: JSONObject.NULL)
            .put("thread_id", threadId ?: JSONObject.NULL)
        call("POST", "/api/v1/foldos/mail/send", token, json)
    }

    suspend fun markRead(token: String, id: String, read: Boolean) {
        val path = "/api/v1/foldos/mail/messages/" + URLEncoder.encode(id, "UTF-8") + "/read"
        call("POST", path, token, JSONObject().put("read", read))
    }

    private fun session(json: JSONObject): ServerSession {
        val sub = json.optJSONObject("subscriber")
        return ServerSession(
            token = json.optString("token").ifBlank { null },
            email = sub?.optString("email").orEmpty(),
            name = sub?.optString("name")?.ifBlank { null },
            mail = mailInfo(json.optJSONObject("mail")),
        )
    }

    private fun mailInfo(json: JSONObject?): ServerMailInfo = ServerMailInfo(
        available = json?.optBoolean("available", false) ?: false,
        linked = json?.optBoolean("linked", false) ?: false,
        account = if (json == null || json.isNull("account")) null else json.optString("account").ifBlank { null },
        needsRelink = json?.optBoolean("needs_relink", false) ?: false,
    )

    private fun deviceName(): String = "FoldOS on ${Build.MANUFACTURER} ${Build.MODEL}".take(80)

    private suspend fun call(method: String, path: String, token: String?, body: JSONObject?): JSONObject {
        val base = normalizeBase(baseUrl())
        return withContext(Dispatchers.IO) {
            val conn = URL(base + path).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.requestMethod = method
                conn.setRequestProperty("Accept", "application/json")
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull() ?: JSONObject()
                if (status !in 200..299) {
                    val code = json.optString("error").ifBlank { null }
                    val message = json.optString("message").ifBlank { null }
                        ?: code?.takeIf { it.contains(' ') }
                        ?: "The server answered with an error ($status)."
                    throw ServerException(status, code, message)
                }
                json
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://sub.crosspointhealth.org"
        private const val TIMEOUT_MS = 10_000

        /** Trims, adds `https://` when no scheme is given, drops a trailing slash; blank gives the default. */
        fun normalizeBase(raw: String?): String {
            val trimmed = raw?.trim().orEmpty().trimEnd('/')
            if (trimmed.isEmpty()) return DEFAULT_BASE_URL
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
