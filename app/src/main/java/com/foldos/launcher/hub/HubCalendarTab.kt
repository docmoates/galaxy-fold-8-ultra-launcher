package com.foldos.launcher.hub

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The Hub's Calendar tab (mock-up `calPage`): week strip, the selected day's timeline with a now
 * line and side-by-side overlapping events, "Coming up", and a "New event" FAB that opens the
 * `CalendarContract` insert intent. The inner screen adds a month grid in the left pane.
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
    val now = rememberHubMinute()
    val today = now.toLocalDate()
    val granted = rememberHubPermission(Manifest.permission.READ_CALENDAR)

    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }
    val firstDayOfWeek: DayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weekStart = remember(selected, firstDayOfWeek) { startOfWeek(selected, firstDayOfWeek) }
    val month = remember(selected) { YearMonth.from(selected) }

    val dayStart = HubFormat.startOfDay(selected)
    val dayEnd = HubFormat.startOfDay(selected.plusDays(1))
    val dayEvents by remember(graph, dayStart) { graph.hub.eventsBetween(dayStart, dayEnd) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val weekEvents by remember(graph, weekStart) {
        graph.hub.eventsBetween(HubFormat.startOfDay(weekStart), HubFormat.startOfDay(weekStart.plusDays(7)))
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val monthEvents by remember(graph, month, screen) {
        if (screen == ScreenKind.COVER) {
            flowOf(emptyList<HubEvent>())
        } else {
            graph.hub.eventsBetween(HubFormat.startOfDay(month.atDay(1)), HubFormat.startOfDay(month.plusMonths(1).atDay(1)))
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tomorrow = HubFormat.startOfDay(today.plusDays(1))
    val upcoming by remember(graph, tomorrow) { graph.hub.eventsBetween(tomorrow, HubFormat.startOfDay(today.plusDays(15))) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by remember(graph) { (graph.hub as? FoldOsHubRepository)?.calendarAccounts() ?: flowOf(emptyList<String>()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val weekDots = remember(weekEvents) { weekEvents.flatMap { it.dates() }.toSet() }
    val monthDots = remember(monthEvents) { monthEvents.flatMap { it.dates() }.toSet() }

    val subtitle = when {
        !granted -> "Calendar access needed"
        accounts.isEmpty() -> "No calendars"
        else -> accounts.joinToString(" · ")
    }
    val header: @Composable () -> Unit = {
        HubPageHeader(
            title = HubFormat.monthYear(selected),
            subtitle = subtitle,
            subtitleIcon = Icons.Rounded.CalendarMonth,
            trailingIcon = Icons.Rounded.Today,
            onTrailingClick = { selectedEpochDay = today.toEpochDay() },
        )
    }
    val dayTitle = if (selected == today) "Today, ${HubFormat.weekdayShort(selected)} ${selected.dayOfMonth}" else HubFormat.weekdayLongDate(selected)
    val dayCard: @Composable (Modifier, Dp) -> Unit = { m, hourHeight ->
        if (!granted) {
            HubPermissionCard(
                title = "Calendar",
                body = "Shows events from every account on this phone: Google, Samsung, Outlook.",
                permission = Manifest.permission.READ_CALENDAR,
                modifier = m,
            )
        } else {
            HubListCard(modifier = m) {
                HubCardHeader(
                    title = dayTitle,
                    trailingIcon = if (screen == ScreenKind.COVER) Icons.Rounded.ViewDay else Icons.Rounded.ViewWeek,
                )
                HubAllDayRow(dayEvents.filter { it.allDay })
                HubDayTimeline(
                    events = dayEvents,
                    dayStartMillis = dayStart,
                    now = if (selected == today) now else null,
                    hourHeight = hourHeight,
                    onEventClick = { HubIntents.openEvent(context, it) },
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
                )
            }
        }
    }
    val upcomingCard: @Composable () -> Unit = {
        if (granted) HubUpcomingCard(upcoming.take(4))
    }
    val fab: @Composable (Modifier) -> Unit = { m ->
        HubFab(
            icon = Icons.Rounded.Add,
            label = if (screen == ScreenKind.COVER) "New" else "New event",
            onClick = { HubIntents.newEvent(context, newEventStart(selected, now)) },
            modifier = m,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (screen == ScreenKind.COVER) {
            HubScrollColumn(contentPadding = contentPadding, modifier = Modifier.padding(horizontal = HubPagePadding)) {
                header()
                HubListCard(contentPadding = PaddingValues(8.dp)) {
                    HubWeekStrip(weekStart = weekStart, selected = selected, today = today, dots = weekDots) {
                        selectedEpochDay = it.toEpochDay()
                    }
                }
                dayCard(Modifier.fillMaxWidth(), 52.dp)
                upcomingCard()
                HubGap(64.dp)
            }
        } else {
            HubTwoPane(
                modifier = Modifier.padding(horizontal = HubPagePadding),
                left = {
                    HubScrollColumn(contentPadding = contentPadding) {
                        header()
                        HubListCard {
                            HubMonthGrid(
                                month = month,
                                selected = selected,
                                today = today,
                                dots = monthDots,
                                firstDayOfWeek = firstDayOfWeek,
                                onSelect = { selectedEpochDay = it.toEpochDay() },
                                onMonthChange = { delta -> selectedEpochDay = selected.plusMonths(delta.toLong()).toEpochDay() },
                            )
                        }
                        upcomingCard()
                    }
                },
                right = {
                    HubScrollColumn(contentPadding = contentPadding) {
                        HubGap(12.dp)
                        dayCard(Modifier.fillMaxWidth(), 60.dp)
                        HubGap(64.dp)
                    }
                },
            )
        }
        fab(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding()),
        )
    }
}

/** Every local date an event touches (multi-day all-day events mark each day). */
private fun HubEvent.dates(): List<LocalDate> {
    val first = HubFormat.toDate(begin)
    val lastExclusive = HubFormat.toDate(end - 1)
    if (!lastExclusive.isAfter(first)) return listOf(first)
    val days = ArrayList<LocalDate>()
    var day = first
    while (!day.isAfter(lastExclusive) && days.size < 62) {
        days += day
        day = day.plusDays(1)
    }
    return days
}

private fun startOfWeek(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
    val diff = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    return date.minusDays(diff.toLong())
}

/** A new event starts at the next full hour today, or 9:00 on another selected day. */
private fun newEventStart(selected: LocalDate, now: LocalDateTime): Long {
    val start = if (selected == now.toLocalDate()) {
        now.withMinute(0).withSecond(0).withNano(0).plusHours(1)
    } else {
        selected.atTime(9, 0)
    }
    return start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/* ------------------------------------------------------------------------------------------ */
/* Week strip and month grid                                                                   */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up `.wk`: seven day cells; the selected one is an accent pill, a dot marks days with events. */
@Composable
private fun HubWeekStrip(
    weekStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    dots: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 7) {
            val day = weekStart.plusDays(i.toLong())
            val isSelected = day == selected
            val fg = if (isSelected) tokens.onAccent else tokens.onSurface
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) tokens.accent else Color.Transparent)
                    .clickable { onSelect(day) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = HubFormat.weekdayShort(day),
                    color = fg.copy(alpha = if (isSelected) 0.85f else 0.6f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    color = fg,
                    fontSize = 19.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (day == today && !isSelected) FontWeight.Bold else FontWeight.Medium,
                )
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (day in dots) fg.copy(alpha = 0.6f) else Color.Transparent),
                )
            }
        }
    }
}

/** Mock-up `.mgrid`: weekday header, muted leading days, accent circle on the selected day, event dots. */
@Composable
private fun HubMonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    dots: Set<LocalDate>,
    firstDayOfWeek: DayOfWeek,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (Int) -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = HubFormat.monthYear(month.atDay(1)),
            color = tokens.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        HubIconButton(Icons.Rounded.ChevronLeft, "Previous month", { onMonthChange(-1) }, size = 34.dp, tinted = false)
        HubIconButton(Icons.Rounded.ChevronRight, "Next month", { onMonthChange(1) }, size = 34.dp, tinted = false)
    }
    val gridStart = startOfWeek(month.atDay(1), firstDayOfWeek)
    val totalCells = run {
        val lead = ((month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7)
        val cells = lead + month.lengthOfMonth()
        ((cells + 6) / 7) * 7
    }
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val dow = firstDayOfWeek.plus(i.toLong())
                Text(
                    text = dow.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                    color = tokens.onSurface.copy(alpha = 0.55f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }
        for (row in 0 until totalCells / 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val day = gridStart.plusDays((row * 7 + col).toLong())
                    val inMonth = YearMonth.from(day) == month
                    val isSelected = day == selected
                    val fg = if (isSelected) tokens.onAccent else tokens.onSurface.copy(alpha = if (inMonth) 1f else 0.3f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (isSelected) tokens.accent else Color.Transparent)
                            .clickable { onSelect(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.dayOfMonth.toString(),
                            color = fg,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected || day == today) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (day in dots) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(fg.copy(alpha = 0.6f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Day timeline                                                                                */
/* ------------------------------------------------------------------------------------------ */

/** All-day events as small coloured chips above the timeline. */
@Composable
private fun HubAllDayRow(events: List<HubEvent>) {
    if (events.isEmpty()) return
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        events.forEach { event ->
            val color = hubEventColor(event)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.26f))
                    .clickable { HubIntents.openEvent(context, event) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(
                    text = event.title,
                    color = tokens.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val HubNowRed: Color = Color(0xFFFF4D5E)
private val TimelineLabelWidth: Dp = 44.dp
private val TimelineBlockStart: Dp = 52.dp

/**
 * Mock-up `.tl`: hour lines with labels, event blocks positioned by time (overlapping events share
 * the width through [layoutHubTimeline]) and a red now line when [now] is given.
 */
@Composable
internal fun HubDayTimeline(
    events: List<HubEvent>,
    dayStartMillis: Long,
    now: LocalDateTime?,
    onEventClick: (HubEvent) -> Unit,
    modifier: Modifier = Modifier,
    hourHeight: Dp = 56.dp,
) {
    val tokens = LocalFoldOsTokens.current
    val hours = remember(events, dayStartMillis) { hubTimelineHours(events, dayStartMillis) }
    val slots = remember(events) { layoutHubTimeline(events) }
    val startHour = hours.first
    val endHour = hours.last
    val totalHeight = hourHeight * (endHour - startHour)
    val lineColor = tokens.onSurface.copy(alpha = 0.12f)

    fun yFor(millis: Long): Dp {
        val hoursFromStart = (millis - dayStartMillis) / 3_600_000f - startHour
        return hourHeight * hoursFromStart.coerceIn(0f, (endHour - startHour).toFloat())
    }

    BoxWithConstraints(modifier = modifier.height(totalHeight)) {
        val blockArea = (maxWidth - TimelineBlockStart - 4.dp).coerceAtLeast(40.dp)

        Canvas(Modifier.fillMaxSize()) {
            val labelPx = TimelineLabelWidth.toPx()
            for (h in startHour..endHour) {
                val y = (hourHeight * (h - startHour)).toPx()
                drawLine(lineColor, Offset(labelPx, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
        }
        for (h in startHour..endHour) {
            Text(
                text = hourLabel(h),
                color = tokens.onSurface.copy(alpha = 0.6f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.offset(y = hourHeight * (h - startHour) - 8.dp),
            )
        }
        slots.forEach { slot ->
            val event = slot.event
            val top = yFor(event.begin) + 1.dp
            val bottom = yFor(event.end)
            val height = (bottom - top - 1.dp).coerceAtLeast(26.dp)
            val columnWidth = blockArea / slot.columns
            val color = hubEventColor(event)
            Row(
                modifier = Modifier
                    .offset(x = TimelineBlockStart + columnWidth * slot.column, y = top)
                    .width((columnWidth - 2.dp).coerceAtLeast(24.dp))
                    .height(height)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.26f))
                    .clickable { onEventClick(event) },
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(color))
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        text = event.title,
                        color = tokens.onSurface,
                        fontSize = 13.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (height >= 44.dp) {
                        Text(
                            text = hubEventSubtitle(event),
                            color = tokens.onSurface.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (now != null) {
            val nowHours = now.hour + now.minute / 60f
            if (nowHours >= startHour && nowHours <= endHour) {
                val y = hourHeight * (nowHours - startHour)
                Box(
                    Modifier
                        .offset(x = TimelineLabelWidth + 2.dp, y = y - 1.dp)
                        .width(maxWidth - TimelineLabelWidth - 2.dp)
                        .height(2.dp)
                        .background(HubNowRed),
                )
                Box(
                    Modifier
                        .offset(x = TimelineLabelWidth - 3.dp, y = y - 5.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(HubNowRed),
                )
            }
        }
    }
}

private fun hourLabel(hour: Int): String = when {
    hour == 0 || hour == 24 -> "12 AM"
    hour == 12 -> "12 PM"
    hour > 12 -> "${hour - 12} PM"
    else -> "$hour AM"
}

/* ------------------------------------------------------------------------------------------ */
/* Coming up                                                                                   */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up `UPCOMING`: the next events after today with their day, time and place. */
@Composable
private fun HubUpcomingCard(events: List<HubEvent>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    HubListCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        HubKicker("Coming up", modifier = Modifier.padding(horizontal = 0.dp))
        if (events.isEmpty()) {
            Text(
                text = "Nothing in the next two weeks",
                color = LocalFoldOsTokens.current.onSurface.copy(alpha = 0.65f),
                fontSize = 13.5.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                events.forEach { event ->
                    val day = HubFormat.shortDayMonth(HubFormat.toDate(event.begin))
                    val time = if (event.allDay) "All day" else HubFormat.clockAmPm(event.begin)
                    val where = event.location?.takeUnless { FoldOsHubRepository.findUrl(it) == it }?.trim().orEmpty()
                    val subtitle = if (where.isEmpty()) "$day · $time" else "$day · $time · $where"
                    HubUpcomingRow(event = event, subtitle = subtitle, onClick = { HubIntents.openEvent(context, event) })
                }
            }
        }
    }
}

@Composable
private fun HubUpcomingRow(event: HubEvent, subtitle: String, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val color = hubEventColor(event)
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text(text = event.title, color = tokens.onSurface, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
