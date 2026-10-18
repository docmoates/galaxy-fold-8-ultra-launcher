package com.foldos.launcher.hub

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.HubEvent
import com.foldos.launcher.theme.LocalFoldOsTokens
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The Hub's Calendar tab (1.6.0, "Agenda by day"): a grouped list of the next days that have events
 * ("Today", "Tomorrow", then weekday and date), each event as time, title and duration. It fills the
 * height it is given with as many groups and rows as fit and never scrolls; an "Open calendar" link
 * closes the list only when there is room left. The inner screen uses two columns (first two days on the
 * left, the following days on the right).
 */
@Composable
internal fun HubCalendarTab(
    screen: ScreenKind,
    settings: LauncherSettings,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val now = rememberHubMinute()
    val today = now.toLocalDate()
    val granted = rememberHubPermission(Manifest.permission.READ_CALENDAR)

    if (!granted) {
        // Permission missing: only the compact centred panel.
        HubPermissionPanel(
            icon = Icons.Rounded.CalendarMonth,
            title = "Calendar access",
            reason = "Allow access to show events from every calendar on this phone.",
            permission = Manifest.permission.READ_CALENDAR,
            modifier = modifier.padding(horizontal = HubPagePadding).padding(contentPadding),
        )
        return
    }

    val rangeStart = HubFormat.startOfDay(today)
    val rangeEnd = HubFormat.startOfDay(today.plusDays(AGENDA_DAYS.toLong()))
    val events by remember(graph, rangeStart) { graph.hub.eventsBetween(rangeStart, rangeEnd) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val nowMillis = remember(now) { now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val days = remember(events, today, nowMillis) { agendaDays(events, today, nowMillis) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = HubPagePadding),
    ) {
        val gap = HubFitGap
        val barHeight = HubSlimBarHeight.scaledBy(fontScale)
        val headerH = AgendaHeaderHeight.scaledBy(fontScale)
        val rowH = AgendaRowHeight.scaledBy(fontScale)
        val footerH = AgendaFooterHeight.scaledBy(fontScale)
        val listHeight = maxHeight - barHeight - gap
        val counts = days.map { it.events.size }

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            HubSlimBar(title = "Calendar") {
                HubIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "New event",
                    onClick = { HubIntents.newEvent(context, newEventStart(now)) },
                    size = 40.dp,
                )
            }
            if (days.isEmpty()) {
                HubListCard(Modifier.fillMaxWidth()) {
                    HubEmptyRow("No events in the next ${AGENDA_DAYS} days")
                }
            } else if (screen == ScreenKind.COVER) {
                val plan = planAgenda(counts, listHeight.value, (headerH + 2.dp).value, (rowH + 1.dp).value, gap.value, footerH.value)
                AgendaColumn(days, plan, 0, Modifier.fillMaxWidth(), gap, headerH, rowH, footerH, today) {
                    HubIntents.openCalendar(context, nowMillis)
                }
            } else {
                val leftDays = days.take(2)
                val rightDays = days.drop(2)
                val leftPlan = planAgenda(leftDays.map { it.events.size }, listHeight.value, (headerH + 2.dp).value, (rowH + 1.dp).value, gap.value, footerH.value)
                val rightPlan = planAgenda(rightDays.map { it.events.size }, listHeight.value, (headerH + 2.dp).value, (rowH + 1.dp).value, gap.value, footerH.value)
                // One footer only: the right column's when it has groups, else the left one's.
                val rightHasGroups = rightPlan.groups.isNotEmpty()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    AgendaColumn(
                        leftDays, leftPlan.copy(footer = leftPlan.footer && !rightHasGroups), 0,
                        Modifier.weight(1f), gap, headerH, rowH, footerH, today,
                    ) { HubIntents.openCalendar(context, nowMillis) }
                    AgendaColumn(
                        rightDays, rightPlan, 0,
                        Modifier.weight(1f), gap, headerH, rowH, footerH, today,
                    ) { HubIntents.openCalendar(context, nowMillis) }
                }
            }
        }
    }
}

private const val AGENDA_DAYS = 14

private val AgendaHeaderHeight: Dp = 34.dp
private val AgendaRowHeight: Dp = 54.dp
private val AgendaFooterHeight: Dp = 36.dp

/** One non-empty day of the agenda. */
internal data class AgendaDay(val date: LocalDate, val events: List<HubEvent>)

/**
 * Groups [events] by local day (a multi-day event appears on each day it touches), keeps only non-empty
 * days from [today] on, and hides today's events that have already ended. All-day events sort first.
 */
internal fun agendaDays(events: List<HubEvent>, today: LocalDate, nowMillis: Long): List<AgendaDay> {
    val byDay = java.util.TreeMap<LocalDate, MutableList<HubEvent>>()
    for (event in events) {
        val first = HubFormat.toDate(event.begin)
        val last = HubFormat.toDate((event.end - 1).coerceAtLeast(event.begin))
        var day = if (first.isBefore(today)) today else first
        var guard = 0
        while (!day.isAfter(last) && guard++ < AGENDA_DAYS) {
            if (day == today && !event.allDay && event.end <= nowMillis) {
                day = day.plusDays(1)
                continue
            }
            byDay.getOrPut(day) { ArrayList() } += event
            day = day.plusDays(1)
        }
    }
    return byDay.entries
        .map { (date, list) -> AgendaDay(date, list.sortedWith(compareBy<HubEvent>({ !it.allDay }, { it.begin }))) }
        .filter { it.events.isNotEmpty() }
}

/** "Today", "Tomorrow", otherwise "Wednesday". */
internal fun agendaDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> HubFormat.weekdayLongDate(date).substringBefore(",")
}

/** A new event starts at the next full hour. */
private fun newEventStart(now: LocalDateTime): Long =
    now.withMinute(0).withSecond(0).withNano(0).plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun AgendaColumn(
    days: List<AgendaDay>,
    plan: AgendaPlan,
    offset: Int,
    modifier: Modifier,
    gap: Dp,
    headerH: Dp,
    rowH: Dp,
    footerH: Dp,
    today: LocalDate,
    onOpenCalendar: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        plan.groups.forEach { group ->
            val day = days[group.index + offset]
            AgendaGroup(day = day, rows = group.rows, today = today, headerH = headerH, rowH = rowH)
        }
        if (plan.footer) AgendaFooter(height = footerH, onClick = onOpenCalendar)
    }
}

@Composable
private fun AgendaGroup(day: AgendaDay, rows: Int, today: LocalDate, headerH: Dp, rowH: Dp) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    HubListCard {
        Row(
            modifier = Modifier.fillMaxWidth().height(headerH).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = agendaDayLabel(day.date, today),
                color = tokens.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = HubFormat.shortDayMonth(day.date),
                color = tokens.onSurface.copy(alpha = 0.7f),
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (day.events.size == 1) "1 event" else "${day.events.size} events",
                color = tokens.onSurface.copy(alpha = 0.6f),
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }
        day.events.take(rows).forEach { event ->
            HubRowDivider()
            AgendaEventRow(event = event, height = rowH, onClick = { HubIntents.openEvent(context, event) })
        }
    }
}

@Composable
private fun AgendaEventRow(event: HubEvent, height: Dp, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val color = hubEventColor(event)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(4.dp).height(height - 22.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Column(Modifier.width(62.dp)) {
            Text(
                text = if (event.allDay) "All day" else HubFormat.clock(event.begin),
                color = tokens.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (!event.allDay) {
                Text(
                    text = HubFormat.duration(event.begin, event.end),
                    color = tokens.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = event.title,
            color = tokens.onSurface,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AgendaFooter(height: Dp, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Open calendar",
            color = tokens.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
