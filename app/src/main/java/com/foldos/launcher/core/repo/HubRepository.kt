package com.foldos.launcher.core.repo

import kotlinx.coroutines.flow.Flow

/** A calendar event from `CalendarContract`; [begin] / [end] are epoch millis, [color] is ARGB. */
data class HubEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int,
)

/** A missed call from `CallLog`; [name] is the matched contact name or null. */
data class HubCall(
    val id: Long,
    val number: String,
    val name: String?,
    val time: Long,
)

/**
 * A contact from `ContactsContract`. [phone] / [email] are the primary entries or null;
 * [lookupKey] is `ContactsContract.Contacts.LOOKUP_KEY`, for opening the contact card.
 */
data class HubContact(
    val id: Long,
    val name: String,
    val phone: String?,
    val email: String?,
    val photoUri: String?,
    val starred: Boolean,
    val lookupKey: String,
)

/** A to-do item; [dueMillis] is null when it has no due date. */
data class HubTask(
    val id: Long,
    val title: String,
    val dueMillis: Long?,
    val done: Boolean,
)

/**
 * Data behind the Hub page's Today, Calendar and Contacts tabs. Implemented by the Hub-core agent on
 * the Android calendar, call log and contacts providers; `data.EmptyHubRepository` is the
 * placeholder. Every flow emits an empty list while the matching permission is missing.
 */
interface HubRepository {
    /** Today's events (local time), ordered by [HubEvent.begin]. */
    fun todayEvents(): Flow<List<HubEvent>>

    /** Events overlapping `[startMillis, endMillis)`, ordered by [HubEvent.begin]. */
    fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<HubEvent>>

    /** Recent missed calls, newest first. */
    fun missedCalls(): Flow<List<HubCall>>

    /** Every visible contact, sorted by name. */
    fun contacts(): Flow<List<HubContact>>

    /** Starred contacts, sorted by name. */
    fun favorites(): Flow<List<HubContact>>

    /** Open tasks first (by due date), then done ones. */
    fun tasks(): Flow<List<HubTask>>

    suspend fun setTaskDone(id: Long, done: Boolean)

    suspend fun addTask(title: String, dueMillis: Long?)
}
