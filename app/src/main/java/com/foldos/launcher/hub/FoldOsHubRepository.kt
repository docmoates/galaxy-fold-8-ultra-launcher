package com.foldos.launcher.hub

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import com.foldos.launcher.core.repo.HubCall
import com.foldos.launcher.core.repo.HubContact
import com.foldos.launcher.core.repo.HubEvent
import com.foldos.launcher.core.repo.HubRepository
import com.foldos.launcher.core.repo.HubTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.regex.Pattern

/**
 * One call-log row of any type (incoming, outgoing, missed, ...); [type] is a `CallLog.Calls.*_TYPE`
 * constant and [durationSeconds] the call length. Used by the Contacts tab's "Recent calls".
 */
data class HubCallLogEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val time: Long,
    val type: Int,
    val durationSeconds: Long,
)

/**
 * [HubRepository] on the Android providers (PLAN.md 2.5):
 *
 * - events from `CalendarContract.Instances` (READ_CALENDAR), all-day instances normalised to local
 *   midnight so a day query never leaks the neighbouring day's all-day events; when an event has no
 *   location but its description holds a URL, that URL is returned as [HubEvent.location] so the
 *   Today tab can offer "Join",
 * - missed calls from `CallLog.Calls` (READ_CALL_LOG),
 * - contacts and starred favourites from `ContactsContract` (READ_CONTACTS) with their primary phone,
 *   e-mail and thumbnail photo,
 * - tasks from the local [HubTaskStore] (`foldos_tasks` DataStore, no permission).
 *
 * Every provider flow emits an empty list while its permission is missing and re-checks the grant
 * whenever [permissionsVersion] is bumped ([notifyPermissionsChanged]: the permission dialog's
 * result in `MainActivity`, the Hub page resuming); once granted it emits the first query and
 * re-queries on every `ContentObserver` change. Nothing polls. All queries run on [Dispatchers.IO].
 * The `hub/` package's name for this class is [FoldOsHubRepository]; [AndroidHubRepository] is the
 * alias CONTRACTS.md uses.
 */
class FoldOsHubRepository(context: Context) : HubRepository {

    private val appContext: Context = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val taskStore = HubTaskStore(appContext)

    private val _permissionsVersion = MutableStateFlow(0)

    /** Bumped by [notifyPermissionsChanged]; every provider flow re-checks its permission on a change. */
    val permissionsVersion: StateFlow<Int> = _permissionsVersion.asStateFlow()

    /** Call after a runtime permission may have changed (permission dialog result, activity resume). */
    fun notifyPermissionsChanged() {
        _permissionsVersion.update { it + 1 }
    }

    /** The runtime permissions this repository reads with, in the order the Today tab asks for them. */
    val requiredPermissions: List<String> = REQUIRED_PERMISSIONS

    /** Permissions from [requiredPermissions] that are not granted right now. */
    fun missingPermissions(): List<String> = REQUIRED_PERMISSIONS.filterNot(::hasPermission)

    fun hasPermission(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------------------------------
    // Calendar
    // ------------------------------------------------------------------------------------------

    override fun todayEvents(): Flow<List<HubEvent>> =
        observed(CalendarContract.CONTENT_URI, Manifest.permission.READ_CALENDAR) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val begin = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            queryEvents(begin, end)
        }

    override fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<HubEvent>> =
        observed(CalendarContract.CONTENT_URI, Manifest.permission.READ_CALENDAR) {
            queryEvents(startMillis, endMillis)
        }

    /**
     * Labels of the visible calendar accounts ("Google", "Samsung", "Outlook", "Device", or the
     * account name), distinct, for the Calendar tab's subtitle. Empty without READ_CALENDAR.
     */
    fun calendarAccounts(): Flow<List<String>> =
        observed(CalendarContract.CONTENT_URI, Manifest.permission.READ_CALENDAR) { queryCalendarAccounts() }

    private fun queryEvents(startMillis: Long, endMillis: Long): List<HubEvent> {
        if (endMillis <= startMillis) return emptyList()
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        // The query window as whole local days, for the all-day overlap check below.
        val endDateExclusive = Instant.ofEpochMilli(endMillis - 1).atZone(zone).toLocalDate().plusDays(1)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.DESCRIPTION,
        )
        val result = ArrayList<HubEvent>()
        // All-day instances are stored at UTC midnight; widen the window by a day on each side so they
        // are returned for every zone, then filter them by local date.
        val queryBegin = startMillis - DAY_MILLIS
        val queryEnd = endMillis + DAY_MILLIS
        runCatching {
            CalendarContract.Instances.query(resolver, projection, queryBegin, queryEnd)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val allDay = cursor.getInt(4) == 1
                    var begin = cursor.getLong(2)
                    var end = cursor.getLong(3)
                    if (allDay) {
                        val firstDay: LocalDate = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                        val rawLastDay: LocalDate = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                        val minLastDay: LocalDate = firstDay.plusDays(1)
                        val lastDayExclusive: LocalDate = if (rawLastDay.isBefore(minLastDay)) minLastDay else rawLastDay
                        if (!firstDay.isBefore(endDateExclusive) || !lastDayExclusive.isAfter(startDate)) continue
                        begin = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
                        end = lastDayExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
                    } else {
                        if (begin >= endMillis || end <= startMillis) continue
                    }
                    val location = cursor.getString(5)?.trim()?.takeIf { it.isNotEmpty() }
                    val description = if (cursor.isNull(7)) null else cursor.getString(7)
                    result += HubEvent(
                        id = cursor.getLong(0),
                        title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        begin = begin,
                        end = end,
                        allDay = allDay,
                        location = location ?: findUrl(description),
                        color = if (cursor.isNull(6)) 0 else cursor.getInt(6),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Calendar query failed", it) }
        return result.sortedWith(compareBy<HubEvent> { !it.allDay }.thenBy { it.begin }.thenBy { it.end })
    }

    private fun queryCalendarAccounts(): List<String> {
        val projection = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val labels = LinkedHashSet<String>()
        runCatching {
            resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0).orEmpty()
                    val type = cursor.getString(1).orEmpty()
                    labels += accountLabel(name, type)
                }
            }
        }.onFailure { Log.w(TAG, "Calendars query failed", it) }
        return labels.toList()
    }

    // ------------------------------------------------------------------------------------------
    // Call log
    // ------------------------------------------------------------------------------------------

    override fun missedCalls(): Flow<List<HubCall>> =
        observed(CallLog.Calls.CONTENT_URI, Manifest.permission.READ_CALL_LOG) {
            queryCalls(missedOnly = true, limit = MISSED_CALL_LIMIT)
                .map { HubCall(id = it.id, number = it.number, name = it.name, time = it.time) }
        }

    /** The newest [limit] call-log rows of every type, newest first. Empty without READ_CALL_LOG. */
    fun recentCalls(limit: Int = RECENT_CALL_LIMIT): Flow<List<HubCallLogEntry>> =
        observed(CallLog.Calls.CONTENT_URI, Manifest.permission.READ_CALL_LOG) {
            queryCalls(missedOnly = false, limit = limit)
        }

    private fun queryCalls(missedOnly: Boolean, limit: Int): List<HubCallLogEntry> {
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.coerceAtLeast(1).toString())
            .build()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
        )
        val selection = if (missedOnly) "${CallLog.Calls.TYPE} = ?" else null
        val args = if (missedOnly) arrayOf(CallLog.Calls.MISSED_TYPE.toString()) else null
        val result = ArrayList<HubCallLogEntry>()
        runCatching {
            resolver.query(uri, projection, selection, args, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    result += HubCallLogEntry(
                        id = cursor.getLong(0),
                        number = cursor.getString(1).orEmpty(),
                        name = cursor.getString(2)?.takeIf { it.isNotBlank() },
                        time = cursor.getLong(3),
                        type = cursor.getInt(4),
                        durationSeconds = cursor.getLong(5),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Call log query failed", it) }
        return result
    }

    // ------------------------------------------------------------------------------------------
    // Contacts
    // ------------------------------------------------------------------------------------------

    override fun contacts(): Flow<List<HubContact>> =
        observed(ContactsContract.Contacts.CONTENT_URI, Manifest.permission.READ_CONTACTS) {
            queryContacts(starredOnly = false)
        }

    override fun favorites(): Flow<List<HubContact>> =
        observed(ContactsContract.Contacts.CONTENT_URI, Manifest.permission.READ_CONTACTS) {
            queryContacts(starredOnly = true)
        }

    private fun queryContacts(starredOnly: Boolean): List<HubContact> {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.STARRED,
            ContactsContract.Contacts.LOOKUP_KEY,
        )
        val selection = buildString {
            append(ContactsContract.Contacts.IN_VISIBLE_GROUP).append(" = 1 AND ")
            append(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY).append(" IS NOT NULL AND ")
            append(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY).append(" != ''")
            if (starredOnly) append(" AND ").append(ContactsContract.Contacts.STARRED).append(" = 1")
        }
        val rows = ArrayList<HubContact>()
        runCatching {
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                null,
                ContactsContract.Contacts.SORT_KEY_PRIMARY,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val lookupKey = cursor.getString(4) ?: continue
                    rows += HubContact(
                        id = cursor.getLong(0),
                        name = cursor.getString(1).orEmpty(),
                        phone = null,
                        email = null,
                        photoUri = cursor.getString(2),
                        starred = cursor.getInt(3) == 1,
                        lookupKey = lookupKey,
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Contacts query failed", it) }
        if (rows.isEmpty()) return rows
        val phones = primaryValues(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
        )
        val emails = primaryValues(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY,
        )
        return rows.map { it.copy(phone = phones[it.id], email = emails[it.id]) }
    }

    /** contactId -> the super-primary value if any, else the first row's value. */
    private fun primaryValues(uri: Uri, idColumn: String, valueColumn: String, superPrimaryColumn: String): Map<Long, String> {
        val values = HashMap<Long, String>()
        val superPrimary = HashSet<Long>()
        runCatching {
            resolver.query(uri, arrayOf(idColumn, valueColumn, superPrimaryColumn), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val value = cursor.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val isSuper = cursor.getInt(2) != 0
                    if (isSuper && superPrimary.add(id)) {
                        values[id] = value
                    } else if (id !in superPrimary && id !in values) {
                        values[id] = value
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Contact data query failed for $uri", it) }
        return values
    }

    /**
     * An intent that starts a video call with [contact] through whichever app (Meet / Duo, WhatsApp,
     * Telegram, Samsung) has registered a video-call row for it in the contacts provider, or null when
     * none has (callers fall back to the contact card). Runs on IO.
     */
    suspend fun videoCallIntent(contact: HubContact): Intent? = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return@withContext null
        var found: Intent? = null
        runCatching {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE),
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(contact.id.toString()),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext() && found == null) {
                    val mimeType = cursor.getString(1) ?: continue
                    if (VIDEO_MIME_HINTS.any { mimeType.contains(it, ignoreCase = true) }) {
                        val dataUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, cursor.getLong(0))
                        found = Intent(Intent.ACTION_VIEW).setDataAndType(dataUri, mimeType)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Video call lookup failed", it) }
        found
    }

    // ------------------------------------------------------------------------------------------
    // Tasks
    // ------------------------------------------------------------------------------------------

    override fun tasks(): Flow<List<HubTask>> = taskStore.tasks

    override suspend fun setTaskDone(id: Long, done: Boolean) = taskStore.setDone(id, done)

    override suspend fun addTask(title: String, dueMillis: Long?) = taskStore.add(title, dueMillis)

    suspend fun deleteTask(id: Long) = taskStore.remove(id)

    suspend fun clearCompletedTasks() = taskStore.clearCompleted()

    // ------------------------------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------------------------------

    /**
     * Empty while [permission] is missing (re-checked on every [permissionsVersion] bump, never by
     * polling); afterwards [query] once and again on every change under [uri]. Runs on IO;
     * identical results are dropped.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observed(uri: Uri, permission: String, query: () -> List<T>): Flow<List<T>> =
        _permissionsVersion
            .map { hasPermission(permission) }
            .distinctUntilChanged()
            .flatMapLatest { granted ->
                if (!granted) {
                    flowOf(emptyList())
                } else {
                    contentChanges(uri).map { runCatching(query).getOrElse { emptyList() } }
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Emits once on collection and again whenever the content under [uri] changes. */
    private fun contentChanges(uri: Uri): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = runCatching { resolver.registerContentObserver(uri, true, observer) }.isSuccess
        awaitClose { if (registered) runCatching { resolver.unregisterContentObserver(observer) } }
    }.conflate()

    private fun accountLabel(name: String, type: String): String {
        val lower = type.lowercase()
        return when {
            lower == "com.google" -> "Google"
            lower.contains("samsung") || lower.contains("osp.app.signin") -> "Samsung"
            lower.contains("outlook") || lower.contains("microsoft") || lower.contains("exchange") -> "Outlook"
            lower == CalendarContract.ACCOUNT_TYPE_LOCAL.lowercase() || lower.isEmpty() -> "Device"
            name.isNotBlank() -> name
            else -> type
        }
    }

    companion object {
        private const val TAG = "FoldOsHubRepository"
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private const val MISSED_CALL_LIMIT = 25
        private const val RECENT_CALL_LIMIT = 60

        /** Runtime permissions the Hub reads with; each must also be declared in the manifest. */
        val REQUIRED_PERMISSIONS: List<String> = listOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
        )

        private val VIDEO_MIME_HINTS: List<String> = listOf("video", "tachyon")

        private val URL_PATTERN: Pattern = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE)

        /** The first http(s) URL inside [text], or null. Also used by the Today tab's Join button. */
        fun findUrl(text: String?): String? {
            if (text.isNullOrBlank()) return null
            val matcher = URL_PATTERN.matcher(text)
            return if (matcher.find()) matcher.group().trimEnd('.', ',', ')', ';') else null
        }
    }
}

/** CONTRACTS.md names the Hub-core implementation `AndroidHubRepository`; it is [FoldOsHubRepository]. */
typealias AndroidHubRepository = FoldOsHubRepository
