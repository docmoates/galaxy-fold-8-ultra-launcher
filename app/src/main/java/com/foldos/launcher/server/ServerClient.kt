package com.foldos.launcher.server

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A non-2xx answer from the Crosspoint server. [code] is the JSON `error` field, [message] is safe to show. */
class ServerException(val status: Int, val code: String?, message: String) : Exception(message) {
    /** The token is dead (or the credentials are wrong on login). */
    val unauthorized: Boolean get() = status == 401
}

/** The `mail` block the server returns from login, `/me` and unlink. */
data class ServerMailInfo(val available: Boolean, val linked: Boolean, val account: String?)

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
