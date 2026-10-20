package com.foldos.widgetsplus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Draws the widget page: a header, one of five sections (Today, Calendar, Contacts, Mail, AI) and a tab bar.
 * The sections are fixed rows inside one layout; [build] fills the rows that fit the widget's height and hides
 * the rest, so a bigger widget shows more.
 */
object WidgetRenderer {

    const val ACTION_TAB: String = "com.foldos.widgetsplus.TAB"
    const val ACTION_REFRESH: String = "com.foldos.widgetsplus.REFRESH"
    const val EXTRA_TAB: String = "tab"

    private const val HUB_PACKAGE = "com.foldos.launcher"
    private const val HUB_ACTIVITY = "com.foldos.launcher.hub.HubActivity"
    private const val HUB_TAB_EXTRA = "com.foldos.launcher.hub.TAB"

    private const val EVENT_ROWS = 8
    private const val TODAY_ROWS = 4
    private const val CONTACT_ROWS = 8
    private const val ROW_DP = 46
    private const val TILE_DP = 72

    private val tabViewIds = intArrayOf(R.id.tab_today, R.id.tab_calendar, R.id.tab_contacts, R.id.tab_mail, R.id.tab_ai)
    private val sectionIds = intArrayOf(R.id.section_today, R.id.section_calendar, R.id.section_contacts, R.id.section_mail, R.id.section_ai)

    fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val options = manager.getAppWidgetOptions(widgetId)
        val height = maxOf(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
        ).takeIf { it > 0 } ?: 420
        manager.updateAppWidget(widgetId, build(context, widgetId, height))
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, WidgetsPlusProvider::class.java)).forEach { updateWidget(context, manager, it) }
    }

    fun build(context: Context, widgetId: Int, heightDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_page)
        val tab = WidgetPrefs.tab(context, widgetId)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // Height left for the section: padding 28, header 40, gaps 14, tab bar 48.
        val area = (heightDp - 130).coerceAtLeast(ROW_DP)

        // Tab bar: highlight the selected tab and wire every tab to its broadcast.
        PageTab.entries.forEachIndexed { index, page ->
            val id = tabViewIds[index]
            views.setInt(id, "setBackgroundResource", if (page == tab) R.drawable.tab_selected else R.drawable.tab_idle)
            views.setOnClickPendingIntent(id, tabIntent(context, widgetId, page))
        }
        sectionIds.forEachIndexed { index, id ->
            views.setViewVisibility(id, if (index == tab.ordinal) View.VISIBLE else View.GONE)
        }
        views.setViewVisibility(R.id.perm_prompt, View.GONE)
        views.setOnClickPendingIntent(R.id.header_title, refreshIntent(context))
        views.setTextViewText(R.id.header_sub, today.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())))

        when (tab) {
            PageTab.TODAY -> drawToday(context, views, widgetId, zone, today, area)
            PageTab.CALENDAR -> drawCalendar(context, views, widgetId, zone, today, area)
            PageTab.CONTACTS -> drawContacts(context, views, widgetId, area)
            PageTab.MAIL -> drawMail(context, views, widgetId, area)
            PageTab.AI -> drawAi(context, views, widgetId, area)
        }
        return views
    }

    // ---- Today ---------------------------------------------------------------------------------------

    private fun drawToday(context: Context, views: RemoteViews, widgetId: Int, zone: ZoneId, today: LocalDate, area: Int) {
        views.setTextViewText(R.id.header_title, greeting())
        if (!WidgetData.hasCalendar(context)) {
            showPermission(context, views, R.id.section_today, "Allow calendar access to see your day.\nTap to open Widgets +")
            return
        }
        val events = WidgetData.upcomingEvents(context)
        val todays = events.filter { it.day(zone) == today }
        views.setTextViewText(
            R.id.today_brief,
            when (todays.size) {
                0 -> "Nothing on the calendar today."
                1 -> "1 event today."
                else -> "${todays.size} events today."
            },
        )
        views.setOnClickPendingIntent(R.id.today_brief, hubOr(context, widgetId * 10 + 1, "today", calendarApp()))
        val next = events.firstOrNull { !it.allDay } ?: events.firstOrNull()
        if (next == null) {
            views.setViewVisibility(R.id.next_card, View.GONE)
            repeat(TODAY_ROWS) { views.setViewVisibility(rowId("td_row_", it), View.GONE) }
            views.setViewVisibility(R.id.today_empty, View.VISIBLE)
            views.setTextViewText(R.id.today_empty, "Your next 7 days are clear.")
            return
        }
        views.setViewVisibility(R.id.next_card, View.VISIBLE)
        views.setViewVisibility(R.id.today_empty, View.GONE)
        views.setTextViewText(R.id.next_title, next.title)
        views.setTextViewText(R.id.next_sub, listOfNotNull(whenText(next, today, zone, oneLine = true), next.location).joinToString("  ·  "))
        views.setOnClickPendingIntent(R.id.next_card, eventIntent(context, widgetId * 10 + 2, next))

        val rest = events.filter { it != next }
        val rows = ((area - 40 - 100) / ROW_DP).coerceIn(0, TODAY_ROWS)
        for (i in 0 until TODAY_ROWS) {
            val event = rest.getOrNull(i)?.takeIf { i < rows }
            val row = rowId("td_row_", i)
            if (event == null) {
                views.setViewVisibility(row, View.GONE)
                continue
            }
            views.setViewVisibility(row, View.VISIBLE)
            fillEventRow(context, views, widgetId, event, today, zone, "td_time_", "td_title_", "td_sub_", "td_row_", i, 20)
        }
    }

    // ---- Calendar ------------------------------------------------------------------------------------

    private fun drawCalendar(context: Context, views: RemoteViews, widgetId: Int, zone: ZoneId, today: LocalDate, area: Int) {
        views.setTextViewText(R.id.header_title, "Calendar")
        if (!WidgetData.hasCalendar(context)) {
            showPermission(context, views, R.id.section_calendar, "Allow calendar access to see your events.\nTap to open Widgets +")
            return
        }
        val events = WidgetData.upcomingEvents(context)
        val rows = (area / ROW_DP).coerceIn(1, EVENT_ROWS)
        views.setViewVisibility(R.id.cal_empty, if (events.isEmpty()) View.VISIBLE else View.GONE)
        for (i in 0 until EVENT_ROWS) {
            val event = events.getOrNull(i)?.takeIf { i < rows }
            if (event == null) {
                views.setViewVisibility(rowId("ev_row_", i), View.GONE)
                continue
            }
            views.setViewVisibility(rowId("ev_row_", i), View.VISIBLE)
            fillEventRow(context, views, widgetId, event, today, zone, "ev_time_", "ev_title_", "ev_sub_", "ev_row_", i, 30)
        }
    }

    private fun fillEventRow(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        event: EventItem,
        today: LocalDate,
        zone: ZoneId,
        timePrefix: String,
        titlePrefix: String,
        subPrefix: String,
        rowPrefix: String,
        index: Int,
        requestBase: Int,
    ) {
        views.setTextViewText(rowId(timePrefix, index), whenText(event, today, zone, oneLine = false))
        views.setTextViewText(rowId(titlePrefix, index), event.title)
        val sub = listOfNotNull(durationText(event), event.location).joinToString("  ·  ")
        views.setTextViewText(rowId(subPrefix, index), sub)
        views.setViewVisibility(rowId(subPrefix, index), if (sub.isEmpty()) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(rowId(rowPrefix, index), eventIntent(context, widgetId * 100 + requestBase + index, event))
    }

    // ---- Contacts ------------------------------------------------------------------------------------

    private fun drawContacts(context: Context, views: RemoteViews, widgetId: Int, area: Int) {
        views.setTextViewText(R.id.header_title, "Favorites")
        if (!WidgetData.hasContacts(context)) {
            showPermission(context, views, R.id.section_contacts, "Allow contacts access to see your favorites.\nTap to open Widgets +")
            return
        }
        val rows = (area / ROW_DP).coerceIn(1, CONTACT_ROWS)
        val contacts = WidgetData.favorites(context, CONTACT_ROWS)
        views.setViewVisibility(R.id.ct_empty, if (contacts.isEmpty()) View.VISIBLE else View.GONE)
        for (i in 0 until CONTACT_ROWS) {
            val contact = contacts.getOrNull(i)?.takeIf { i < rows }
            val row = rowId("ct_row_", i)
            if (contact == null) {
                views.setViewVisibility(row, View.GONE)
                continue
            }
            views.setViewVisibility(row, View.VISIBLE)
            views.setTextViewText(rowId("ct_initial_", i), contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?")
            views.setTextViewText(rowId("ct_name_", i), contact.name)
            views.setOnClickPendingIntent(row, activity(context, widgetId * 100 + 50 + i, Intent(Intent.ACTION_VIEW, contact.lookupUri)))
            // ACTION_DIAL only opens the dialer with the number filled in; the widget never places a call itself.
            views.setOnClickPendingIntent(
                rowId("ct_call_", i),
                activity(context, widgetId * 100 + 70 + i, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.number, null))),
            )
        }
    }

    // ---- Mail and AI ---------------------------------------------------------------------------------

    private fun drawMail(context: Context, views: RemoteViews, widgetId: Int, area: Int) {
        views.setTextViewText(R.id.header_title, "Mail")
        val tiles = (area / TILE_DP).coerceIn(1, 3)
        val ids = intArrayOf(R.id.mail_open, R.id.mail_compose, R.id.mail_hub)
        ids.forEachIndexed { i, id -> views.setViewVisibility(id, if (i < tiles) View.VISIBLE else View.GONE) }
        views.setOnClickPendingIntent(R.id.mail_open, activity(context, widgetId * 10 + 3, mailApp()))
        views.setOnClickPendingIntent(R.id.mail_compose, activity(context, widgetId * 10 + 4, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))))
        views.setOnClickPendingIntent(R.id.mail_hub, hubOr(context, widgetId * 10 + 5, "mail", mailApp()))
    }

    private fun drawAi(context: Context, views: RemoteViews, widgetId: Int, area: Int) {
        views.setTextViewText(R.id.header_title, "AI")
        val tiles = (area / TILE_DP).coerceIn(1, 4)
        val ids = intArrayOf(R.id.ai_assist, R.id.ai_voice, R.id.ai_hub, R.id.ai_event)
        ids.forEachIndexed { i, id -> views.setViewVisibility(id, if (i < tiles) View.VISIBLE else View.GONE) }
        views.setOnClickPendingIntent(R.id.ai_assist, activity(context, widgetId * 10 + 6, Intent(Intent.ACTION_ASSIST)))
        views.setOnClickPendingIntent(R.id.ai_voice, activity(context, widgetId * 10 + 7, Intent(Intent.ACTION_VOICE_COMMAND)))
        views.setOnClickPendingIntent(R.id.ai_hub, hubOr(context, widgetId * 10 + 8, "ai", Intent(Intent.ACTION_ASSIST)))
        views.setOnClickPendingIntent(R.id.ai_event, activity(context, widgetId * 10 + 9, Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)))
    }

    // ---- Shared pieces -------------------------------------------------------------------------------

    private fun showPermission(context: Context, views: RemoteViews, sectionId: Int, message: String) {
        views.setViewVisibility(sectionId, View.GONE)
        views.setViewVisibility(R.id.perm_prompt, View.VISIBLE)
        views.setTextViewText(R.id.perm_prompt, message)
        views.setOnClickPendingIntent(R.id.perm_prompt, activity(context, 9001, Intent(context, SetupActivity::class.java)))
    }

    private fun greeting(): String {
        val hour = LocalTime.now().hour
        return when {
            hour < 5 -> "Good evening"
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    /** "9:30 AM" for today, "Wed 9:30 AM" (or "Wed\n9:30 AM" in the narrow time column) for other days. */
    private fun whenText(event: EventItem, today: LocalDate, zone: ZoneId, oneLine: Boolean): String {
        val day = event.day(zone)
        val time = if (event.allDay) {
            "All day"
        } else {
            Instant.ofEpochMilli(event.begin).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        }
        if (day == today) return if (oneLine) "Today  ·  $time" else time
        val label = if (day == today.plusDays(1)) "Tomorrow" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return if (oneLine) "$label  ·  $time" else "$label\n$time"
    }

    private fun durationText(event: EventItem): String? {
        if (event.allDay) return null
        val minutes = ((event.end - event.begin) / 60_000L).toInt()
        if (minutes <= 0) return null
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    private fun rowId(prefix: String, index: Int): Int = idCache.getOrPut(prefix + index) {
        R.id::class.java.getField(prefix + index).getInt(null)
    }

    private val idCache = HashMap<String, Int>()

    // ---- Intents -------------------------------------------------------------------------------------

    private fun tabIntent(context: Context, widgetId: Int, tab: PageTab): PendingIntent {
        val intent = Intent(context, WidgetsPlusProvider::class.java)
            .setAction(ACTION_TAB)
            .setData(Uri.parse("widgetsplus://tab/$widgetId/${tab.ordinal}"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(EXTRA_TAB, tab.ordinal)
        return PendingIntent.getBroadcast(context, widgetId * 10 + tab.ordinal + 100000, intent, FLAGS)
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetsPlusProvider::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 9002, intent, FLAGS)
    }

    private fun activity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS)

    private fun eventIntent(context: Context, requestCode: Int, event: EventItem): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
        return activity(context, requestCode, intent)
    }

    /** Opens FoldOS Hub at [hubTab] when FoldOS is installed, otherwise the [fallback] system app. */
    private fun hubOr(context: Context, requestCode: Int, hubTab: String, fallback: Intent): PendingIntent {
        val intent = if (hubInstalled(context)) {
            Intent().setClassName(HUB_PACKAGE, HUB_ACTIVITY).putExtra(HUB_TAB_EXTRA, hubTab)
        } else {
            fallback
        }
        return activity(context, requestCode, intent)
    }

    fun hubInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(HUB_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun calendarApp(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)

    private fun mailApp(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}
