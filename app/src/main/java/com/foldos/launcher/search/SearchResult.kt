package com.foldos.launcher.search

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserHandle
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.settings.SettingsRowInfo

/** Result sections, in the order the sheet lists them (PLAN.md section 4, "Universal search"). */
enum class SearchSection(val title: String) {
    ANSWER("Answer"),
    APPS("Apps"),
    SHORTCUTS("Shortcuts"),
    CONTACTS("Contacts"),
    SETTINGS("Settings"),
    CALENDAR("Calendar"),
    FILES("Files"),
    WEB("Web"),
    PERMISSION("Needs permission"),
}

/**
 * One universal-search hit. [key] is stable across queries (it is what the usage ranking counts),
 * [section] decides where the sheet lists it and [title] / [subtitle] are what the row shows.
 * Activation lives in [SearchActions].
 */
sealed class SearchResult {
    abstract val key: String
    abstract val section: SearchSection
    abstract val title: String
    abstract val subtitle: String?

    /** An installed app (already filtered by `LauncherSettings.hiddenApps`). */
    data class App(val app: AppInfo) : SearchResult() {
        override val key: String get() = "app:${app.key}"
        override val section: SearchSection get() = SearchSection.APPS
        override val title: String get() = app.label
        override val subtitle: String? get() = null
    }

    /** A dynamic, manifest or pinned app shortcut from `LauncherApps.getShortcuts`. */
    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val user: UserHandle,
        val label: String,
        val appLabel: String?,
        val icon: Drawable?,
    ) : SearchResult() {
        override val key: String get() = "shortcut:$packageName/$shortcutId"
        override val section: SearchSection get() = SearchSection.SHORTCUTS
        override val title: String get() = label
        override val subtitle: String? get() = appLabel
    }

    /** A contact from `ContactsContract`; [phone] / [email] are the first entries or null. */
    data class Contact(
        val id: Long,
        val lookupKey: String,
        val name: String,
        val phone: String?,
        val email: String?,
        val photoUri: String?,
        val starred: Boolean,
    ) : SearchResult() {
        override val key: String get() = "contact:$lookupKey"
        override val section: SearchSection get() = SearchSection.CONTACTS
        override val title: String get() = name
        override val subtitle: String? get() = phone ?: email
    }

    /** One FoldOS settings row; activating it opens the page at [route]. */
    data class Setting(
        val route: String,
        val pageTitle: String,
        val row: SettingsRowInfo,
    ) : SearchResult() {
        override val key: String get() = "setting:$route/${row.title}"
        override val section: SearchSection get() = SearchSection.SETTINGS
        override val title: String get() = row.title
        override val subtitle: String get() = row.subtitle?.let { "$pageTitle · $it" } ?: pageTitle
    }

    /** A recent file from `MediaStore.Files`, opened with `ACTION_VIEW`. */
    data class MediaFile(
        val id: Long,
        val uri: Uri,
        val name: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val modifiedMillis: Long,
    ) : SearchResult() {
        override val key: String get() = "file:$id"
        override val section: SearchSection get() = SearchSection.FILES
        override val title: String get() = name
        override val subtitle: String get() = SearchFormat.fileSubtitle(mimeType, sizeBytes, modifiedMillis)
    }

    /** A calendar instance in the next 30 days (`CalendarContract.Instances`). */
    data class CalendarEvent(
        val eventId: Long,
        override val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val location: String?,
    ) : SearchResult() {
        override val key: String get() = "event:$eventId"
        override val section: SearchSection get() = SearchSection.CALENDAR
        override val subtitle: String get() = SearchFormat.eventSubtitle(begin, end, allDay, location)
    }

    /** The value of an arithmetic expression; activating copies [value]. */
    data class Calculation(val expression: String, val value: String) : SearchResult() {
        override val key: String get() = "calc"
        override val section: SearchSection get() = SearchSection.ANSWER
        override val title: String get() = "= $value"
        override val subtitle: String get() = expression
    }

    /** A unit / currency conversion; activating copies [output]. */
    data class Conversion(
        val input: String,
        val output: String,
        val category: String,
        val note: String? = null,
    ) : SearchResult() {
        override val key: String get() = "units:$category"
        override val section: SearchSection get() = SearchSection.ANSWER
        override val title: String get() = output
        override val subtitle: String get() = if (note == null) "$input · $category" else "$input · $category · $note"
    }

    /** A web search on [engineName] (the configured engine or the one a !bang picked). */
    data class Web(
        val query: String,
        val engineId: String,
        val engineName: String,
        val url: String,
        val bang: String?,
    ) : SearchResult() {
        override val key: String get() = "web:$engineId"
        override val section: SearchSection get() = SearchSection.WEB
        override val title: String get() = if (bang == null) "Search $engineName for \"$query\"" else "$engineName: \"$query\""
        override val subtitle: String? get() = bang?.let { "!$it" }
    }

    /** The typed text is a URL; activating opens it in the browser. */
    data class Url(val url: String) : SearchResult() {
        override val key: String get() = "url"
        override val section: SearchSection get() = SearchSection.WEB
        override val title: String get() = "Open $url"
        override val subtitle: String get() = "Website"
    }

    /** Google Lens (or a web image search when Lens is not installed). */
    data class Visual(val query: String) : SearchResult() {
        override val key: String get() = "visual"
        override val section: SearchSection get() = SearchSection.WEB
        override val title: String get() = if (query.isBlank()) "Visual search" else "Image search for \"$query\""
        override val subtitle: String get() = if (query.isBlank()) "Google Lens" else "Lens · images"
    }

    /** A provider that cannot answer until [permission] is granted; activating asks for it. */
    data class Permission(
        val permission: String,
        override val title: String,
        override val subtitle: String?,
    ) : SearchResult() {
        override val key: String get() = "permission:$permission"
        override val section: SearchSection get() = SearchSection.PERMISSION
    }
}
