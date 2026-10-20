package com.foldos.widgetsplus

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One calendar event occurrence. All-day events carry UTC midnight times, so [allDay] decides how they are read. */
data class EventItem(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val location: String?,
    val allDay: Boolean,
) {
    /** The local calendar day the event starts on (UTC date for all-day events). */
    fun day(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
}

/** A starred contact with one phone number. */
data class ContactItem(val name: String, val number: String, val lookupUri: Uri)

/** Reads calendar events and starred contacts from the Android providers; every call returns an empty list without its permission. */
object WidgetData {

    fun hasCalendar(context: Context): Boolean = granted(context, Manifest.permission.READ_CALENDAR)

    fun hasContacts(context: Context): Boolean = granted(context, Manifest.permission.READ_CONTACTS)

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Events that have not ended yet and start within [days] days, soonest first, at most [limit]. */
    fun upcomingEvents(context: Context, days: Int = 7, limit: Int = 30): List<EventItem> {
        if (!hasCalendar(context)) return emptyList()
        val now = System.currentTimeMillis()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now - ONE_DAY)
        ContentUris.appendId(builder, now + days * ONE_DAY)
        val out = ArrayList<EventItem>()
        runCatching {
            context.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.ALL_DAY,
                ),
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && out.size < limit * 2) {
                    val allDay = cursor.getInt(5) == 1
                    val begin = cursor.getLong(1)
                    val end = cursor.getLong(2)
                    // All-day events are stored in UTC; compare them against the local day so today's still show.
                    val stillOn = if (allDay) end > startOfTodayUtcAligned(now) else end > now
                    if (!stillOn) continue
                    out += EventItem(
                        eventId = cursor.getLong(0),
                        begin = begin,
                        end = end,
                        title = cursor.getString(3)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        location = cursor.getString(4)?.takeIf { it.isNotBlank() },
                        allDay = allDay,
                    )
                }
            }
        }
        return out.sortedBy { it.begin }.take(limit)
    }

    /** Starred contacts that have a phone number, alphabetical, one row per person. */
    fun favorites(context: Context, limit: Int = 12): List<ContactItem> {
        if (!hasContacts(context)) return emptyList()
        val seen = HashSet<Long>()
        val out = ArrayList<ContactItem>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                "${ContactsContract.Contacts.STARRED}=1",
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && out.size < limit) {
                    val id = cursor.getLong(0)
                    val lookup = cursor.getString(1) ?: continue
                    val number = cursor.getString(3) ?: continue
                    if (!seen.add(id)) continue
                    out += ContactItem(
                        name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: number,
                        number = number,
                        lookupUri = ContactsContract.Contacts.getLookupUri(id, lookup),
                    )
                }
            }
        }
        return out
    }

    private fun startOfTodayUtcAligned(now: Long): Long {
        // Local midnight expressed in UTC wall-clock terms, matching how all-day events are stored.
        val localDate = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private const val ONE_DAY: Long = 24L * 60 * 60 * 1000
}
