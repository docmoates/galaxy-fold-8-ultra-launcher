package com.foldos.launcher.builtins

import android.Manifest
import android.content.Context
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One calendar instance happening today, read from `CalendarContract.Instances`. */
data class AgendaEvent(
    val id: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
    /** Calendar / event colour as ARGB; 0 when the provider has none. */
    val colorArgb: Int,
) {
    val begin: LocalDateTime get() = Instant.ofEpochMilli(beginMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val end: LocalDateTime get() = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    /** "10:00 – 10:15" or "All day". */
    fun timeRangeText(): String = if (allDay) "All day" else "${begin.format(eventTime)} – ${end.format(eventTime)}"

    /** "10:00 AM" or "All day". */
    fun startText(): String = if (allDay) "All day" else begin.format(eventTimeAmPm)

    /** "10:00 – 10:15 · Teams" (location dropped when blank). */
    fun subtitle(): String {
        val range = timeRangeText()
        val where = location?.trim().orEmpty()
        return if (where.isEmpty()) range else "$range · $where"
    }

    fun color(fallback: Color): Color = if (colorArgb == 0) fallback else Color(colorArgb)

    private companion object {
        val eventTime: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
        val eventTimeAmPm: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    }
}

/** The READ_CALENDAR permission string the agenda widgets request. */
internal const val CALENDAR_PERMISSION: String = Manifest.permission.READ_CALENDAR

/**
 * Today's instances (midnight to midnight, local zone) sorted by start, timed events after the
 * all-day ones. Returns an empty list when READ_CALENDAR is not granted or the provider fails.
 */
internal suspend fun loadTodayEvents(context: Context, day: LocalDate = LocalDate.now()): List<AgendaEvent> =
    withContext(Dispatchers.IO) {
        if (!context.isPermissionGranted(CALENDAR_PERMISSION)) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val begin = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val result = ArrayList<AgendaEvent>()
        runCatching {
            CalendarContract.Instances.query(context.contentResolver, projection, begin, end)?.use { cursor ->
                while (cursor.moveToNext()) {
                    result += AgendaEvent(
                        id = cursor.getLong(0),
                        title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        beginMillis = cursor.getLong(2),
                        endMillis = cursor.getLong(3),
                        allDay = cursor.getInt(4) == 1,
                        location = cursor.getString(5),
                        colorArgb = if (cursor.isNull(6)) 0 else cursor.getInt(6),
                    )
                }
            }
        }
        result.sortedWith(compareBy<AgendaEvent> { !it.allDay }.thenBy { it.beginMillis })
    }

/**
 * Today's events as state. Reloads when [granted] flips to true, when the launcher resumes and
 * once a minute (so "in 19 min" and the "next event" pick stay current). Empty until loaded.
 */
@Composable
internal fun rememberTodayEvents(granted: Boolean): State<List<AgendaEvent>> {
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    val minute = rememberWidgetClock().minute
    return produceState(initialValue = emptyList(), granted, resumeTick, minute) {
        value = if (granted) loadTodayEvents(context) else emptyList()
    }
}

/** The first timed event that has not ended yet; all-day events never count as "next". */
internal fun List<AgendaEvent>.nextEvent(nowMillis: Long = System.currentTimeMillis()): AgendaEvent? =
    firstOrNull { !it.allDay && it.endMillis > nowMillis }
