package com.foldos.launcher.mail

import android.net.Uri
import com.foldos.launcher.core.repo.MailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** A Gmail REST call failed; [statusCode] is the HTTP status (0 for transport errors). */
class GmailApiException(message: String, val statusCode: Int) : IOException(message) {
    /** 401: the access token was rejected; the caller should refresh or sign in again. */
    val unauthorized: Boolean get() = statusCode == 401
}

/** One inbox message with the Gmail-only fields the [MailMessage] contract does not carry. */
data class GmailApiMessage(
    val message: MailMessage,
    val threadId: String,
    /** Gmail label ids: `INBOX`, `UNREAD`, `STARRED`, `CATEGORY_PERSONAL`, `CATEGORY_UPDATES`, ... */
    val labelIds: Set<String>,
) {
    val starred: Boolean get() = LABEL_STARRED in labelIds
    val primary: Boolean get() = LABEL_CATEGORY_PERSONAL in labelIds || labelIds.none { it.startsWith("CATEGORY_") }
    val updates: Boolean get() = LABEL_CATEGORY_UPDATES in labelIds

    companion object {
        const val LABEL_UNREAD: String = "UNREAD"
        const val LABEL_STARRED: String = "STARRED"
        const val LABEL_INBOX: String = "INBOX"
        const val LABEL_CATEGORY_PERSONAL: String = "CATEGORY_PERSONAL"
        const val LABEL_CATEGORY_UPDATES: String = "CATEGORY_UPDATES"
        const val LABEL_CATEGORY_SOCIAL: String = "CATEGORY_SOCIAL"
        const val LABEL_CATEGORY_PROMOTIONS: String = "CATEGORY_PROMOTIONS"
    }
}

/**
 * Minimal Gmail REST client on `HttpsURLConnection` + `org.json` (no Google client libraries):
 * `users.getProfile`, `messages.list` and `messages.get(format=metadata)`. Every call needs a
 * valid OAuth access token with the `gmail.readonly` scope and throws [GmailApiException] on a
 * non-2xx response.
 */
class GmailApiClient {

    /** The signed-in address from `users/me/profile`, or null when the response lacks it. */
    suspend fun profileEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        getJson("$BASE/users/me/profile", accessToken).optString("emailAddress").takeIf { it.isNotBlank() }
    }

    /**
     * The newest [maxResults] inbox messages (`q=in:inbox`), newest first, each fetched with
     * `format=metadata` and only the From / Subject / Date headers.
     */
    suspend fun listInbox(accessToken: String, maxResults: Int = 25): List<GmailApiMessage> =
        withContext(Dispatchers.IO) {
            val listUrl = Uri.parse("$BASE/users/me/messages").buildUpon()
                .appendQueryParameter("maxResults", maxResults.coerceIn(1, 100).toString())
                .appendQueryParameter("q", "in:inbox")
                .build()
                .toString()
            val list = getJson(listUrl, accessToken)
            val ids = ArrayList<String>()
            val array = list.optJSONArray("messages")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val id = array.optJSONObject(i)?.optString("id").orEmpty()
                    if (id.isNotBlank()) ids.add(id)
                }
            }
            if (ids.isEmpty()) return@withContext emptyList()
            val gate = Semaphore(PARALLEL_FETCHES)
            coroutineScope {
                ids.map { id ->
                    async {
                        gate.withPermit { runCatching { fetchMessage(id, accessToken) }.getOrNull() }
                    }
                }.awaitAll()
            }.filterNotNull().sortedByDescending { it.message.date }
        }

    private fun fetchMessage(id: String, accessToken: String): GmailApiMessage {
        val url = Uri.parse("$BASE/users/me/messages/$id").buildUpon()
            .appendQueryParameter("format", "metadata")
            .appendQueryParameter("metadataHeaders", "From")
            .appendQueryParameter("metadataHeaders", "Subject")
            .appendQueryParameter("metadataHeaders", "Date")
            .build()
            .toString()
        val json = getJson(url, accessToken)
        val labels = HashSet<String>()
        json.optJSONArray("labelIds")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(labels::add)
        }
        var from = ""
        var subject = ""
        json.optJSONObject("payload")?.optJSONArray("headers")?.let { headers ->
            for (i in 0 until headers.length()) {
                val header = headers.optJSONObject(i) ?: continue
                when (header.optString("name").lowercase()) {
                    "from" -> from = header.optString("value")
                    "subject" -> subject = header.optString("value")
                }
            }
        }
        val date = json.optString("internalDate").toLongOrNull() ?: 0L
        val snippet = decodeHtmlEntities(json.optString("snippet"))
        return GmailApiMessage(
            message = MailMessage(
                id = json.optString("id").ifBlank { id },
                from = displayNameOf(from),
                subject = subject.ifBlank { "(no subject)" },
                snippet = snippet,
                date = date,
                unread = GmailApiMessage.LABEL_UNREAD in labels,
            ),
            threadId = json.optString("threadId"),
            labelIds = labels,
        )
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        if (connection !is HttpsURLConnection) {
            connection.disconnect()
            throw GmailApiException("Refusing a non-HTTPS Gmail endpoint", 0)
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "Gmail returned HTTP $code" }
                throw GmailApiException(message, code)
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        } catch (e: GmailApiException) {
            throw e
        } catch (e: IOException) {
            throw GmailApiException(e.message ?: "Network error", 0)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://gmail.googleapis.com/gmail/v1"
        private const val TIMEOUT_MS = 15_000
        private const val PARALLEL_FETCHES = 6

        /** `"Sam Rivera" <sam@example.com>` -> `Sam Rivera`; a bare address stays as is. */
        fun displayNameOf(fromHeader: String): String {
            val raw = fromHeader.trim()
            if (raw.isEmpty()) return "Unknown sender"
            val angle = raw.indexOf('<')
            val name = if (angle > 0) raw.substring(0, angle) else if (angle == 0) "" else raw
            val cleaned = name.trim().trim('"', '\'').trim()
            if (cleaned.isNotEmpty()) return cleaned
            return raw.removePrefix("<").removeSuffix(">").trim()
        }

        /** Gmail snippets are HTML-escaped; undo the handful of entities they use. */
        fun decodeHtmlEntities(text: String): String = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value
            }
    }
}
