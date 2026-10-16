package com.foldos.launcher.search

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.HubEvent
import com.foldos.launcher.core.repo.HubRepository
import com.foldos.launcher.settings.SearchRows
import com.foldos.launcher.settings.SettingsPageInfo
import com.foldos.launcher.settings.SettingsPages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "FoldOsSearch"

/** A result with the match score its provider gave it (0..100); [SearchIndex] adds the usage boost. */
internal data class Scored(val result: SearchResult, val score: Int)

internal fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/* ------------------------------------------------------------------------------------------ */
/* Apps                                                                                        */
/* ------------------------------------------------------------------------------------------ */

/** Installed apps by label (fuzzy prefix) or package name. [apps] is already minus hidden apps. */
internal object AppSearchProvider {
    fun search(query: String, apps: List<AppInfo>, limit: Int): List<Scored> {
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<Scored>()
        for (app in apps) {
            var score = SearchMatch.score(query, app.label, app.packageName)
            if (score == 0 && query.contains(' ') && SearchMatch.allWordsMatch(query, app.label)) score = 70
            if (score > 0) out += Scored(SearchResult.App(app), score)
        }
        return out.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.result.title.lowercase() }).take(limit)
    }
}

/* ------------------------------------------------------------------------------------------ */
/* App shortcuts                                                                               */
/* ------------------------------------------------------------------------------------------ */

/**
 * Deep-link shortcuts (PLAN.md 4.1 "Deep-link shortcut search") from `LauncherApps.getShortcuts`
 * with dynamic, manifest and pinned shortcuts. The system only answers the default home app
 * (`hasShortcutHostPermission`); otherwise the provider is silent. The full list is cached for
 * [CACHE_MILLIS] because one query walks every package.
 */
internal class ShortcutSearchProvider(context: Context) {
    private val appContext: Context = context.applicationContext
    private val launcherApps: LauncherApps? = appContext.getSystemService(LauncherApps::class.java)
    private val densityDpi: Int = appContext.resources.displayMetrics.densityDpi

    private val cacheLock = Any()
    private var cached: List<ShortcutInfo> = emptyList()
    private var cachedAt: Long = 0L

    fun search(query: String, settings: LauncherSettings, apps: List<AppInfo>, limit: Int): List<Scored> {
        val launcherApps = launcherApps ?: return emptyList()
        if (query.length < 2) return emptyList()
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val all = allShortcuts(launcherApps, apps)
        val labelsByPackage: Map<String, String> = apps.groupBy { it.packageName }.mapValues { (_, list) -> list.first().label }
        val matches = ArrayList<Scored>()
        for (info in all) {
            if (!info.isEnabled) continue
            val pkg = info.`package`
            if (settings.isHidden(pkg)) continue
            val label = (info.shortLabel ?: info.longLabel)?.toString()?.takeIf { it.isNotBlank() } ?: continue
            val appLabel = labelsByPackage[pkg]
            var score = SearchMatch.score(query, label)
            if (score == 0 && info.longLabel != null) score = SearchMatch.score(query, info.longLabel.toString()) - 5
            if (score <= 0) continue
            // Shortcuts rank below the app with the same name: "Maps" should open Maps, not a route.
            matches += Scored(
                SearchResult.Shortcut(
                    packageName = pkg,
                    shortcutId = info.id,
                    user = info.userHandle,
                    label = label,
                    appLabel = appLabel,
                    icon = null,
                ),
                score - 8,
            )
        }
        val top = matches.sortedByDescending { it.score }.take(limit)
        // Icons only for the few rows that will show.
        val byKey = all.associateBy { "${it.`package`}/${it.id}" }
        return top.map { scored ->
            val shortcut = scored.result as SearchResult.Shortcut
            val info = byKey["${shortcut.packageName}/${shortcut.shortcutId}"]
            val icon = if (info == null) null else runCatching { launcherApps.getShortcutIconDrawable(info, densityDpi) }.getOrNull()
            Scored(shortcut.copy(icon = icon), scored.score)
        }
    }

    private fun allShortcuts(launcherApps: LauncherApps, apps: List<AppInfo>): List<ShortcutInfo> {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            if (cached.isNotEmpty() && now - cachedAt < CACHE_MILLIS) return cached
        }
        val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        val user = Process.myUserHandle()
        val global: List<ShortcutInfo>? = try {
            launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(flags), user)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not the shortcut host; shortcut search off", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Shortcut query failed (user locked?)", e)
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Shortcut query failed", e)
            null
        }
        val list: List<ShortcutInfo> = global ?: perPackage(launcherApps, apps, flags)
        synchronized(cacheLock) {
            cached = list
            cachedAt = now
        }
        return list
    }

    /** Fallback when the global query is refused: one query per installed package. */
    private fun perPackage(launcherApps: LauncherApps, apps: List<AppInfo>, flags: Int): List<ShortcutInfo> {
        val out = ArrayList<ShortcutInfo>()
        val packages = apps.map { it.packageName }.distinct()
        for (pkg in packages) {
            val query = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(flags)
            val found = try {
                launcherApps.getShortcuts(query, Process.myUserHandle())
            } catch (e: RuntimeException) {
                null
            }
            if (found != null) out += found
        }
        return out
    }

    /** Drops the cache, e.g. after a package change. */
    fun invalidate() {
        synchronized(cacheLock) {
            cached = emptyList()
            cachedAt = 0L
        }
    }

    companion object {
        private const val CACHE_MILLIS = 20_000L
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Contacts                                                                                    */
/* ------------------------------------------------------------------------------------------ */

/** Contacts through `ContactsContract.Contacts.CONTENT_FILTER_URI`; needs READ_CONTACTS. */
internal object ContactSearchProvider {
    const val PERMISSION: String = Manifest.permission.READ_CONTACTS

    fun search(context: Context, query: String, limit: Int): List<Scored> {
        if (query.length < 2 || !context.hasPermission(PERMISSION)) return emptyList()
        val resolver = context.contentResolver
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ContactsContract.Contacts.STARRED,
        )
        val sort = "${ContactsContract.Contacts.STARRED} DESC, ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        val found = ArrayList<SearchResult.Contact>()
        try {
            resolver.query(uri, projection, null, null, sort)?.use { cursor ->
                while (cursor.moveToNext() && found.size < limit) {
                    val id = cursor.getLong(0)
                    val lookupKey = cursor.getString(1) ?: continue
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val photo = cursor.getString(3)
                    val hasPhone = cursor.getInt(4) == 1
                    val starred = cursor.getInt(5) == 1
                    val phone = if (hasPhone) firstPhone(resolver, id) else null
                    val email = firstEmail(resolver, id)
                    found += SearchResult.Contact(id, lookupKey, name, phone, email, photo, starred)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Contacts query refused", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Contacts query failed", e)
        }
        val normalized = SearchMatch.normalize(query)
        return found.map { contact ->
            // The provider already matched name, nickname, phone or email; keep a floor so a
            // number match still shows, and bump starred contacts.
            val score = maxOf(SearchMatch.score(normalized, contact.name), 45) + (if (contact.starred) 5 else 0)
            Scored(contact, score)
        }
    }

    private fun firstPhone(resolver: ContentResolver, contactId: Long): String? = firstString(
        resolver,
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        contactId,
        "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
    )

    private fun firstEmail(resolver: ContentResolver, contactId: Long): String? = firstString(
        resolver,
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        ContactsContract.CommonDataKinds.Email.ADDRESS,
        "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
        contactId,
        "${ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Email.IS_PRIMARY} DESC",
    )

    private fun firstString(
        resolver: ContentResolver,
        uri: Uri,
        column: String,
        selection: String,
        contactId: Long,
        sort: String,
    ): String? = try {
        resolver.query(uri, arrayOf(column), selection, arrayOf(contactId.toString()), sort)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    } catch (e: RuntimeException) {
        null
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Settings                                                                                    */
/* ------------------------------------------------------------------------------------------ */

/**
 * Every FoldOS settings row (title / subtitle -> page route), built from the settings package's
 * page objects (`SettingsPages.all`) plus the Search page itself.
 */
object SettingsSearchIndex {
    val pages: List<SettingsPageInfo> by lazy {
        (SettingsPages.all + SearchRows.page).distinctBy { it.route }
    }

    val entries: List<SearchResult.Setting> by lazy {
        pages.flatMap { page -> page.rows.map { row -> SearchResult.Setting(page.route, page.title, row) } }
    }

    internal fun search(query: String, limit: Int): List<Scored> {
        if (query.length < 2) return emptyList()
        val out = ArrayList<Scored>()
        for (entry in entries) {
            val row = entry.row
            var score = SearchMatch.score(query, row.title)
            val subtitle = row.subtitle
            if (subtitle != null) score = maxOf(score, SearchMatch.score(query, subtitle) - 12)
            score = maxOf(score, SearchMatch.score(query, entry.pageTitle) - 25)
            if (score <= 0 && query.contains(' ')) {
                val haystack = listOfNotNull(row.title, subtitle, entry.pageTitle).joinToString(" ")
                if (SearchMatch.allWordsMatch(query, haystack)) score = 55
            }
            if (row.advanced && score > 0) score -= 3
            if (score > 0) out += Scored(entry, score)
        }
        return out.sortedByDescending { it.score }.take(limit)
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Files                                                                                       */
/* ------------------------------------------------------------------------------------------ */

/**
 * Recent files by name from `MediaStore.Files`, newest first. Scoped storage only returns the
 * media types the app may read, so the sheet asks for [PERMISSION] when none is granted. Only the
 * two visual-media permissions are declared in the manifest (READ_MEDIA_VIDEO / AUDIO are not), so
 * those are the only grants that can ever be present.
 */
internal object FileSearchProvider {
    const val PERMISSION: String = Manifest.permission.READ_MEDIA_IMAGES

    private val anyMediaPermission = listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    fun granted(context: Context): Boolean = anyMediaPermission.any { context.hasPermission(it) }

    fun search(context: Context, query: String, limit: Int): List<Scored> {
        if (query.length < 2 || !granted(context)) return emptyList()
        val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%${escapeLike(query)}%"))
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit * 3)
        }
        val out = ArrayList<Scored>()
        val normalized = SearchMatch.normalize(query)
        try {
            context.contentResolver.query(filesUri, projection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val mime = cursor.getString(2)
                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    val modified = if (cursor.isNull(4)) 0L else cursor.getLong(4) * 1000L
                    val score = maxOf(SearchMatch.score(normalized, name), 35)
                    out += Scored(SearchResult.MediaFile(id, ContentUris.withAppendedId(filesUri, id), name, mime, size, modified), score)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "MediaStore query refused", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "MediaStore query failed", e)
        }
        // Newest first within equal scores: the cursor is already date-ordered, so a stable sort keeps it.
        return out.sortedByDescending { it.score }.take(limit)
    }

    private fun escapeLike(text: String): String = text.replace("%", "").replace("_", " ")
}

/* ------------------------------------------------------------------------------------------ */
/* Calendar                                                                                    */
/* ------------------------------------------------------------------------------------------ */

/**
 * Events in the next 30 days whose title or location matches. Asks the Hub repository first
 * (`HubRepository.eventsBetween`), then `CalendarContract.Instances` with its search URI when the
 * hub has nothing (placeholder hub, or a repository that filters calendars).
 */
internal object CalendarSearchProvider {
    const val PERMISSION: String = Manifest.permission.READ_CALENDAR
    private const val WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

    suspend fun search(context: Context, hub: HubRepository?, query: String, limit: Int): List<Scored> {
        if (query.length < 2 || !context.hasPermission(PERMISSION)) return emptyList()
        val begin = System.currentTimeMillis() - 60 * 60 * 1000L
        val end = begin + WINDOW_MILLIS
        val fromHub: List<HubEvent> = if (hub == null) {
            emptyList()
        } else {
            runCatching { withTimeoutOrNull(1_200L) { hub.eventsBetween(begin, end).first() } }.getOrNull().orEmpty()
        }
        val events: List<SearchResult.CalendarEvent> = if (fromHub.isNotEmpty()) {
            fromHub.map { SearchResult.CalendarEvent(it.id, it.title, it.begin, it.end, it.allDay, it.location) }
        } else {
            queryProvider(context, begin, end, query)
        }
        val out = ArrayList<Scored>()
        for (event in events) {
            var score = SearchMatch.score(query, event.title)
            val location = event.location
            if (location != null) score = maxOf(score, SearchMatch.score(query, location) - 10)
            if (score <= 0 && query.contains(' ') && SearchMatch.allWordsMatch(query, event.title)) score = 55
            if (score > 0) out += Scored(event, score)
        }
        return out.sortedWith(compareByDescending<Scored> { it.score }.thenBy { (it.result as SearchResult.CalendarEvent).begin }).take(limit)
    }

    private fun queryProvider(context: Context, begin: Long, end: Long, query: String): List<SearchResult.CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = ArrayList<SearchResult.CalendarEvent>()
        try {
            CalendarContract.Instances.query(context.contentResolver, projection, begin, end, query)?.use { cursor ->
                while (cursor.moveToNext()) {
                    out += SearchResult.CalendarEvent(
                        eventId = cursor.getLong(0),
                        title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        begin = cursor.getLong(2),
                        end = cursor.getLong(3),
                        allDay = cursor.getInt(4) == 1,
                        location = cursor.getString(5),
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Calendar query refused", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Calendar query failed", e)
        }
        return out.sortedBy { it.begin }
    }
}
