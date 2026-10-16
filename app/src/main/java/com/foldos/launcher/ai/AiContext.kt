package com.foldos.launcher.ai

import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.core.repo.BadgeRepository
import com.foldos.launcher.core.repo.HubCall
import com.foldos.launcher.core.repo.HubEvent
import com.foldos.launcher.core.repo.HubRepository
import com.foldos.launcher.core.repo.HubTask
import com.foldos.launcher.core.repo.MailMessage
import com.foldos.launcher.core.repo.MailRepository
import com.foldos.launcher.core.repo.MailState
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the assistant may talk about, captured at one instant and entirely on-device:
 * today's and the coming week's calendar, tasks, missed calls, notification badge counts, the
 * mail state, the launcher settings and the installed apps. Built by [AiContextBuilder]; rendered
 * to plain text with [render] for an [com.foldos.launcher.core.repo.AiProvider].
 */
data class AiContextSnapshot(
    val now: LocalDateTime,
    val settings: LauncherSettings,
    /** Today's events ordered by start. */
    val todayEvents: List<HubEvent>,
    /** Events after today within the next seven days, ordered by start. */
    val upcomingEvents: List<HubEvent>,
    val tasks: List<HubTask>,
    val missedCalls: List<HubCall>,
    /** Package name -> unread notification count. */
    val badgeCounts: Map<String, Int>,
    val mail: MailState,
    /** Installed launchable apps (hidden ones included), for naming packages. */
    val apps: List<AppInfo>,
) {
    val nowMillis: Long get() = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** The timed event running right now, if any. */
    fun currentEvent(): HubEvent? = todayEvents.firstOrNull { !it.allDay && it.begin <= nowMillis && it.end > nowMillis }

    /** The next timed event that has not started yet (today first, then the coming days). */
    fun nextEvent(): HubEvent? =
        todayEvents.filter { !it.allDay && it.begin > nowMillis }.minByOrNull { it.begin }
            ?: upcomingEvents.filter { !it.allDay }.minByOrNull { it.begin }

    val openTasks: List<HubTask> get() = tasks.filter { !it.done }

    /** Open tasks due before tomorrow midnight (overdue included). */
    fun tasksDueToday(): List<HubTask> {
        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return openTasks.filter { it.dueMillis != null && it.dueMillis < endOfDay }
    }

    val unreadMail: Int get() = (mail as? MailState.Connected)?.unread ?: 0

    val unreadMessages: List<MailMessage>
        get() = (mail as? MailState.Connected)?.messages?.filter { it.unread } ?: emptyList()

    val totalBadges: Int get() = badgeCounts.values.sum()

    /** App label for a package, falling back to the package name. */
    fun appLabel(packageName: String): String =
        apps.firstOrNull { it.packageName == packageName }?.label ?: packageName.substringAfterLast('.')

    /** Events on [day] (today or one of the coming days). */
    fun eventsOn(day: LocalDate): List<HubEvent> {
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return (todayEvents + upcomingEvents).filter { it.begin < end && it.end > start }.sortedBy { it.begin }
    }

    /** Plain-text rendering for a text model; nothing leaves the device unless a provider sends it. */
    fun render(): String = buildString {
        appendLine("Now: ${now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, h:mm a", Locale.getDefault()))}")
        appendLine("Today's events (${todayEvents.size}):")
        todayEvents.forEach { event ->
            appendLine("  - ${aiEventTime(event)} ${event.title}${event.location?.let { " at $it" } ?: ""}")
        }
        if (upcomingEvents.isNotEmpty()) {
            appendLine("Coming days:")
            upcomingEvents.take(10).forEach { event ->
                appendLine("  - ${aiDayName(event.begin, now)} ${aiEventTime(event)} ${event.title}")
            }
        }
        appendLine("Open tasks (${openTasks.size}):")
        openTasks.take(10).forEach { task ->
            appendLine("  - ${task.title}${task.dueMillis?.let { " (due ${aiDayName(it, now)} ${aiClock(it)})" } ?: ""}")
        }
        if (missedCalls.isNotEmpty()) {
            appendLine("Missed calls (${missedCalls.size}):")
            missedCalls.take(5).forEach { call -> appendLine("  - ${call.name ?: call.number} at ${aiClock(call.time)}") }
        }
        appendLine(
            when (val m = mail) {
                is MailState.Connected -> "Mail: ${m.unread} unread of ${m.messages.size} inbox messages"
                MailState.NotConfigured -> "Mail: not connected"
                MailState.Disconnected -> "Mail: signed out"
                MailState.Connecting -> "Mail: connecting"
                is MailState.Error -> "Mail: error (${m.message})"
            },
        )
        unreadMessages.take(5).forEach { message -> appendLine("  - ${message.from}: ${message.subject}") }
        if (badgeCounts.isNotEmpty()) {
            appendLine("Notifications: ${badgeCounts.entries.sortedByDescending { it.value }.take(6).joinToString { "${appLabel(it.key)} ${it.value}" }}")
        }
        appendLine(
            "Launcher: theme ${settings.theme.name.lowercase()}, ${settings.appearance.name.lowercase()} appearance, " +
                "colors ${settings.colors.scheme}, cover ${settings.styleCover.name.lowercase()} (${settings.colsCover} columns), " +
                "inner ${settings.styleInner.name.lowercase()} (${settings.colsInner} columns), hub ${if (settings.hubOn) settings.hubPos else "off"}, " +
                "${settings.hiddenApps.size} hidden apps, AI mode ${settings.aiMode}",
        )
    }
}

/**
 * Gathers an [AiContextSnapshot] from the app graph's repositories. Every flow is read once with a
 * short timeout, so a provider that never emits (a missing permission) just leaves its section
 * empty instead of blocking the answer.
 */
class AiContextBuilder(
    private val settings: SettingsRepository,
    private val apps: AppsRepository,
    private val hub: HubRepository,
    private val badges: BadgeRepository,
    private val mail: MailRepository,
) {
    suspend fun snapshot(): AiContextSnapshot {
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val tomorrowStart = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEnd = now.toLocalDate().plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
        val current = firstOrDefault(settings.settings, LauncherSettings())
        return AiContextSnapshot(
            now = now,
            settings = current,
            todayEvents = firstOrDefault(hub.todayEvents(), emptyList()).sortedBy { it.begin },
            upcomingEvents = firstOrDefault(hub.eventsBetween(tomorrowStart, weekEnd), emptyList()).sortedBy { it.begin },
            tasks = firstOrDefault(hub.tasks(), emptyList()),
            missedCalls = firstOrDefault(hub.missedCalls(), emptyList()),
            badgeCounts = firstOrDefault(badges.counts, emptyMap()),
            mail = firstOrDefault(mail.state, MailState.NotConfigured),
            apps = firstOrDefault(apps.allApps, emptyList()),
        )
    }

    private suspend fun <T> firstOrDefault(flow: Flow<T>, default: T, timeoutMs: Long = FLOW_TIMEOUT_MS): T =
        try {
            withTimeoutOrNull(timeoutMs) { flow.first() } ?: default
        } catch (e: Exception) {
            default
        }

    private companion object {
        const val FLOW_TIMEOUT_MS = 1_500L
    }
}

/* ---- Shared formatting helpers (package-internal) ---- */

internal fun aiClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

internal fun aiEventTime(event: HubEvent): String = if (event.allDay) "All day" else aiClock(event.begin)

/** "today", "tomorrow", or the weekday name for a time relative to [now]. */
internal fun aiDayName(millis: Long, now: LocalDateTime): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = now.toLocalDate()
    return when {
        day == today -> "today"
        day == today.minusDays(1) -> "yesterday"
        day == today.plusDays(1) -> "tomorrow"
        day.isAfter(today) && day.isBefore(today.plusDays(7)) -> day.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        else -> day.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

/** "in 19 min", "in 2 h 5 min", "now", or "5 min ago". */
internal fun aiRelative(millis: Long, nowMillis: Long): String {
    val delta = millis - nowMillis
    val minutes = (kotlin.math.abs(delta) / 60_000L).toInt()
    val text = when {
        minutes < 1 -> return "now"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h${if (minutes % 60 != 0) " ${minutes % 60} min" else ""}"
    }
    return if (delta >= 0) "in $text" else "$text ago"
}

internal fun aiPlural(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1) "1 $singular" else "$count $plural"
