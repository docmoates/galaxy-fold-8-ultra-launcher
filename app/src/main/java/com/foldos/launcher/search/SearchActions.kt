package com.foldos.launcher.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import com.foldos.launcher.core.model.AppInfo

/**
 * What happens when a [SearchResult] is activated. Every launch goes through [start], which adds
 * `FLAG_ACTIVITY_NEW_TASK` for non-activity contexts and turns a missing handler into a toast.
 */
object SearchActions {
    private const val TAG = "FoldOsSearch"

    /** Package of the Google Lens app; falls back to a web image search when it is not installed. */
    const val LENS_PACKAGE: String = "com.google.ar.lens"

    /**
     * Activates [result]. Returns true when the search sheet should close (something was opened),
     * false when it should stay (an answer was copied, a permission was requested).
     */
    fun activate(
        context: Context,
        result: SearchResult,
        onLaunch: (AppInfo) -> Unit,
        onOpenSettingsRoute: (String) -> Unit,
        requestPermission: (String) -> Unit,
    ): Boolean = when (result) {
        is SearchResult.App -> {
            onLaunch(result.app)
            true
        }
        is SearchResult.Shortcut -> startShortcut(context, result)
        is SearchResult.Contact -> openContact(context, result.id, result.lookupKey)
        is SearchResult.Setting -> {
            onOpenSettingsRoute(result.route)
            true
        }
        is SearchResult.MediaFile -> openFile(context, result)
        is SearchResult.CalendarEvent -> openEvent(context, result)
        is SearchResult.Calculation -> {
            copy(context, "Result", result.value)
            false
        }
        is SearchResult.Conversion -> {
            copy(context, "Conversion", result.output)
            false
        }
        is SearchResult.Web -> openUrl(context, result.url)
        is SearchResult.Url -> openUrl(context, result.url)
        is SearchResult.Visual -> visualSearch(context, result.query)
        is SearchResult.Permission -> {
            requestPermission(result.permission)
            false
        }
    }

    fun startShortcut(context: Context, shortcut: SearchResult.Shortcut): Boolean {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        return try {
            launcherApps.startShortcut(shortcut.packageName, shortcut.shortcutId, null, null, shortcut.user)
            true
        } catch (e: ActivityNotFoundException) {
            toast(context, "That shortcut is no longer available")
            false
        } catch (e: SecurityException) {
            toast(context, "FoldOS must be the default home to open shortcuts")
            false
        } catch (e: IllegalStateException) {
            toast(context, "That shortcut is no longer available")
            false
        }
    }

    fun openContact(context: Context, contactId: Long, lookupKey: String): Boolean {
        val uri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
        return start(context, Intent(Intent.ACTION_VIEW, uri))
    }

    /** Opens the dialer with [number] filled in (no CALL_PHONE permission needed). */
    fun call(context: Context, number: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    fun message(context: Context, number: String): Boolean =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))))

    fun email(context: Context, address: String): Boolean =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address))))

    fun openFile(context: Context, file: SearchResult.MediaFile): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (file.mimeType.isNullOrBlank()) setData(file.uri) else setDataAndType(file.uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, intent)
    }

    fun openEvent(context: Context, event: SearchResult.CalendarEvent): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
        return start(context, intent)
    }

    fun openUrl(context: Context, url: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /**
     * Visual search (PLAN.md 4.1 "Visual search button"): Google Lens when installed, otherwise a
     * web image search for [query] (or lens.google.com when the query is blank).
     */
    fun visualSearch(context: Context, query: String): Boolean {
        val lens = runCatching { context.packageManager.getLaunchIntentForPackage(LENS_PACKAGE) }.getOrNull()
        if (lens != null && query.isBlank()) {
            if (start(context, lens, quiet = true)) return true
        }
        return openUrl(context, WebSearch.imageSearchUrl(query))
    }

    /** True when Google Lens is installed (for the Lens button's description). */
    fun hasLens(context: Context): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(LENS_PACKAGE) }.getOrNull() != null

    /** Copies [text] to the clipboard; Android 13+ shows its own confirmation. */
    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(label, text)) }
            .onFailure { Log.w(TAG, "Could not copy to clipboard", it) }
    }

    private fun start(context: Context, intent: Intent, quiet: Boolean = false): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            if (!quiet) toast(context, "No app can open this")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to start $intent", e)
            if (!quiet) toast(context, "Not allowed to open this")
            false
        }
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
