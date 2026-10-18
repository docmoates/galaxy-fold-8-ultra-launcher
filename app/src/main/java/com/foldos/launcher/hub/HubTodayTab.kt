package com.foldos.launcher.hub

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.a11yMinTouchTarget
import com.foldos.launcher.builtins.FakeWeatherProvider
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.HubEvent
import com.foldos.launcher.core.repo.HubTask
import com.foldos.launcher.core.repo.MailMessage
import com.foldos.launcher.core.repo.MailState
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.settings.SettingsRoutes
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * The Hub's Today tab (1.6.0, "Briefing and mini cards"), laid out to fit one screen with no scrolling:
 * greeting header (date and weather), a one to three line briefing, the "Next up" hero card, a two-up row of
 * Tasks and Schedule mini cards, then as many top e-mail rows as the remaining height holds. The inner screen
 * uses two columns: greeting, briefing and next up on the left; tasks, schedule and e-mails on the right.
 * Sections that do not fit are dropped, never scrolled.
 */
@Composable
internal fun HubTodayTab(
    screen: ScreenKind,
    settings: LauncherSettings,
    contentPadding: PaddingValues,
    onOpenTab: (HubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontScale = LocalDensity.current.fontScale
    val now = rememberHubMinute()
    val today = now.toLocalDate()
    val nowMillis = remember(now) { now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }

    val calendarGranted = rememberHubPermission(Manifest.permission.READ_CALENDAR)
    val callsGranted = rememberHubPermission(Manifest.permission.READ_CALL_LOG)

    val events by remember(graph) { graph.hub.todayEvents() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by remember(graph) { graph.hub.tasks() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val missed by remember(graph) { graph.hub.missedCalls() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val mailState by graph.mail.state.collectAsStateWithLifecycle(initialValue = MailState.NotConfigured)
    val weatherProvider = remember { FakeWeatherProvider() }
    val weather by weatherProvider.weather.collectAsStateWithLifecycle()

    val mailOn = LauncherSettings.HUB_TAB_MAIL in settings.hubTabs
    val connectedMail = (mailState as? MailState.Connected)?.takeIf { mailOn }
    val needMail = remember(connectedMail) { connectedMail?.messages?.filter { it.unread }.orEmpty() }
    val briefingOn = settings.feature(LauncherSettings.FEATURE_AI) && settings.aiMode != LauncherSettings.AI_MODE_OFF
    val nextEvent = remember(events, nowMillis) { events.firstOrNull { !it.allDay && it.end > nowMillis } }
    val dueTasks = remember(tasks, today) { tasks.filter { !it.done && HubFormat.isDueNow(it.dueMillis, today) } }
    val upcoming = remember(events, nowMillis) { events.filter { it.allDay || it.end > nowMillis } }

    val dateLine = HubFormat.weekdayDate(today)
    val subtitle = weather?.let { "$dateLine · ${it.tempText} ${it.location}" } ?: dateLine
    val briefing = remember(events.size, dueTasks.size, missed.size, needMail.size, calendarGranted, callsGranted) {
        hubBriefingLine(
            events = if (calendarGranted) events.size else null,
            dueTasks = dueTasks.size,
            missedCalls = if (callsGranted) missed.size else 0,
            unreadMail = needMail.size,
        )
    }

    val header: @Composable () -> Unit = {
        HubPageHeader(
            title = HubFormat.greeting(now.hour),
            subtitle = subtitle,
            subtitleIcon = weather?.condition?.icon,
            trailingIcon = Icons.Rounded.AccountCircle,
            onTrailingClick = { context.startActivity(SettingsActivity.intent(context, SettingsRoutes.HUB)) },
        )
    }
    val toggleTask: (HubTask) -> Unit = { task -> scope.launch { graph.hub.setTaskDone(task.id, !task.done) } }
    val addTask: (String) -> Unit = { title -> scope.launch { graph.hub.addTask(title, null) } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = HubPagePadding),
    ) {
        val gap = HubFitGap
        val columnHeight = maxHeight

        if (screen == ScreenKind.COVER) {
            val plan = remember(columnHeight, fontScale, briefing.length, briefingOn, calendarGranted, needMail.size, maxWidth) {
                planTodayCover(
                    height = columnHeight,
                    width = maxWidth,
                    fontScale = fontScale,
                    gap = gap,
                    briefingOn = briefingOn,
                    briefingLength = briefing.length,
                    calendarGranted = calendarGranted,
                    mailCount = needMail.size,
                )
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                if (plan.header) header()
                if (plan.briefing) HubBriefingCard(title = briefingTitle(now.hour), text = briefing)
                if (plan.next) {
                    if (calendarGranted) {
                        HubNextEventCard(event = nextEvent, allDay = events.firstOrNull { it.allDay }, nowMillis = nowMillis)
                    } else {
                        HubPermissionCard(
                            title = "Calendar",
                            body = "Shows your next event, today's schedule and the briefing.",
                            permission = Manifest.permission.READ_CALENDAR,
                        )
                    }
                }
                if (plan.mini) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        HubTasksMini(
                            tasks = tasks,
                            dueCount = dueTasks.size,
                            today = today,
                            rows = 0,
                            height = HubMiniHeight.scaledBy(fontScale),
                            onToggle = toggleTask,
                            onAdd = addTask,
                            modifier = Modifier.weight(1f),
                        )
                        if (calendarGranted) {
                            HubScheduleMini(
                                events = upcoming,
                                total = events.size,
                                lines = 2,
                                height = HubMiniHeight.scaledBy(fontScale),
                                onOpen = { onOpenTab(HubTab.CALENDAR) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (plan.mailRows > 0 && connectedMail != null) {
                    HubMailRows(needMail.take(plan.mailRows), connectedMail.unread, onOpen = { onOpenTab(HubTab.MAIL) })
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                // Left: greeting, briefing, next up.
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    var left = columnHeight
                    fun fits(h: Dp): Boolean {
                        val need = h + gap
                        return if (need <= left + gap) { left -= need; true } else false
                    }
                    if (fits(HubHeaderHeight.scaledBy(fontScale))) header()
                    if (briefingOn) {
                        val h = hubBriefingHeight(briefing.length, maxWidth / 2, fontScale)
                        if (fits(h)) HubBriefingCard(title = briefingTitle(now.hour), text = briefing)
                    }
                    val nextH = if (calendarGranted) HubNextHeight.scaledBy(fontScale) else HubPermissionHeight.scaledBy(fontScale)
                    if (fits(nextH)) {
                        if (calendarGranted) {
                            HubNextEventCard(event = nextEvent, allDay = events.firstOrNull { it.allDay }, nowMillis = nowMillis)
                        } else {
                            HubPermissionCard(
                                title = "Calendar",
                                body = "Shows your next event, today's schedule and the briefing.",
                                permission = Manifest.permission.READ_CALENDAR,
                            )
                        }
                    }
                }
                // Right: tasks, schedule, e-mails.
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    var left = columnHeight - 8.dp
                    Box(Modifier.height(8.dp))
                    val taskRows = 3
                    val tasksH = HubMiniChrome.scaledBy(fontScale) + HubMiniRowHeight.scaledBy(fontScale) * taskRows
                    if (tasksH <= left) {
                        left -= tasksH + gap
                        HubTasksMini(
                            tasks = tasks,
                            dueCount = dueTasks.size,
                            today = today,
                            rows = taskRows,
                            height = tasksH,
                            onToggle = toggleTask,
                            onAdd = addTask,
                        )
                    }
                    if (calendarGranted) {
                        val lines = 3
                        val schedH = HubMiniChrome.scaledBy(fontScale) + HubScheduleLineHeight.scaledBy(fontScale) * lines
                        if (schedH <= left) {
                            left -= schedH + gap
                            HubScheduleMini(
                                events = upcoming,
                                total = events.size,
                                lines = lines,
                                height = schedH,
                                onOpen = { onOpenTab(HubTab.CALENDAR) },
                            )
                        }
                    }
                    if (connectedMail != null && needMail.isNotEmpty()) {
                        val rows = minOf(
                            needMail.size,
                            fitCount(left - HubMailKicker.scaledBy(fontScale) - gap - 8.dp, HubMailRowHeight.scaledBy(fontScale)),
                        )
                        if (rows > 0) HubMailRows(needMail.take(rows), connectedMail.unread, onOpen = { onOpenTab(HubTab.MAIL) })
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Fit planning (cover)                                                                        */
/* ------------------------------------------------------------------------------------------ */

private val HubHeaderHeight: Dp = 66.dp
private val HubNextHeight: Dp = 92.dp
private val HubPermissionHeight: Dp = 76.dp
private val HubMiniHeight: Dp = 104.dp
private val HubMiniChrome: Dp = 52.dp
private val HubMiniRowHeight: Dp = 36.dp
private val HubScheduleLineHeight: Dp = 26.dp
private val HubMailKicker: Dp = 22.dp
private val HubMailRowHeight: Dp = 56.dp

internal data class TodayCoverPlan(
    val header: Boolean,
    val briefing: Boolean,
    val next: Boolean,
    val mini: Boolean,
    val mailRows: Int,
)

/** Greedy top-down fit of the cover Today stack; the e-mail rows take whatever height is left. */
internal fun planTodayCover(
    height: Dp,
    width: Dp,
    fontScale: Float,
    gap: Dp,
    briefingOn: Boolean,
    briefingLength: Int,
    calendarGranted: Boolean,
    mailCount: Int,
): TodayCoverPlan {
    var left = height
    var shown = 0
    fun take(h: Dp): Boolean {
        val need = if (shown == 0) h else h + gap
        if (need > left) return false
        left -= need
        shown++
        return true
    }
    val header = take(HubHeaderHeight.scaledBy(fontScale))
    val briefing = briefingOn && take(hubBriefingHeight(briefingLength, width, fontScale))
    val next = take((if (calendarGranted) HubNextHeight else HubPermissionHeight).scaledBy(fontScale))
    val mini = take(HubMiniHeight.scaledBy(fontScale))
    var mailRows = 0
    if (mailCount > 0) {
        val room = left - gap - HubMailKicker.scaledBy(fontScale) - 8.dp
        mailRows = minOf(mailCount, fitCount(room, HubMailRowHeight.scaledBy(fontScale)))
    }
    return TodayCoverPlan(header, briefing, next, mini, mailRows)
}

/** Height of the briefing card for a text of [length] characters at [width]: 1 to 3 lines. */
internal fun hubBriefingHeight(length: Int, width: Dp, fontScale: Float): Dp {
    val lines = briefingLineCount(length, width)
    return (56.dp + 20.dp * lines).scaledBy(fontScale)
}

internal fun briefingLineCount(length: Int, width: Dp): Int {
    val charsPerLine = ((width.value - 32f) / 7.2f).coerceAtLeast(12f)
    return ceil(length / charsPerLine).toInt().coerceIn(1, 3)
}

/* ------------------------------------------------------------------------------------------ */
/* Briefing                                                                                    */
/* ------------------------------------------------------------------------------------------ */

private fun briefingTitle(hour: Int): String = when {
    hour < 12 -> "Morning briefing"
    hour < 17 -> "Afternoon briefing"
    else -> "Evening briefing"
}

/**
 * The 1.6.0 template briefing: one short line of counts such as "2 events today, 25 missed calls,
 * 9 emails need you", the numbers in bold. [events] is null while calendar access is missing.
 */
internal fun hubBriefingLine(events: Int?, dueTasks: Int, missedCalls: Int, unreadMail: Int): AnnotatedString =
    buildAnnotatedString {
        val bold = SpanStyle(fontWeight = FontWeight.Bold)
        var first = true
        fun part(count: Int, singular: String, plural: String, tail: String = "") {
            if (!first) append(", ")
            first = false
            withStyle(bold) { append(if (count == 1) "1 $singular" else "$count $plural") }
            append(tail)
        }
        if (events != null && events > 0) part(events, "event", "events", " today")
        if (dueTasks > 0) part(dueTasks, "task", "tasks", " due")
        if (missedCalls > 0) part(missedCalls, "missed call", "missed calls")
        if (unreadMail > 0) {
            if (!first) append(", ")
            first = false
            withStyle(bold) { append(if (unreadMail == 1) "1 email" else "$unreadMail emails") }
            append(if (unreadMail == 1) " needs you" else " need you")
        }
        if (first) append(if (events == null) "Allow calendar access to see your day." else "Your day is clear.")
    }

@Composable
private fun HubBriefingCard(title: String, text: AnnotatedString, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    HubListCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tokens.accent,
            )
            Text(
                text = title.uppercase(),
                color = tokens.onSurface.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
            )
        }
        Text(
            text = text,
            modifier = Modifier.padding(top = 6.dp),
            color = tokens.onSurface,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Next up                                                                                     */
/* ------------------------------------------------------------------------------------------ */

/** "Next up" hero: the start time large, the countdown under it, then title, details and Join. */
@Composable
private fun HubNextEventCard(event: HubEvent?, allDay: HubEvent?, nowMillis: Long, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val shown = event ?: allDay
    val joinUrl = shown?.let(::hubJoinUrl)
    HubListCard(
        modifier = modifier.clickable(enabled = shown != null) { shown?.let { HubIntents.openEvent(context, it) } },
        color = tokens.highlight,
        borderColor = Color.Transparent,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "NEXT UP",
            color = tokens.onHighlight.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (shown == null) {
                Column(Modifier.weight(1f)) {
                    Text(text = "No more events today", color = tokens.onHighlight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(text = "Nothing scheduled", color = tokens.onHighlight.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (shown.allDay) "All day" else HubFormat.clock(shown.begin),
                        color = tokens.onHighlight,
                        fontSize = 30.sp,
                        lineHeight = 33.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = HubFormat.relativeToNow(shown, nowMillis),
                        color = tokens.onHighlight.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = shown.title,
                        color = tokens.onHighlight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hubEventSubtitle(shown),
                        color = tokens.onHighlight.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (joinUrl != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.5.dp, tokens.onHighlight, RoundedCornerShape(999.dp))
                            .clickable { HubIntents.openUrl(context, joinUrl) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp), tint = tokens.onHighlight)
                        Text(text = "Join", color = tokens.onHighlight, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Mini cards                                                                                  */
/* ------------------------------------------------------------------------------------------ */

/**
 * Tasks mini card. [rows] = 0 shows the count and the first open task (tap the card to add one);
 * [rows] > 0 lists that many open tasks, each with a tick circle. Adding happens inline.
 */
@Composable
private fun HubTasksMini(
    tasks: List<HubTask>,
    dueCount: Int,
    today: LocalDate,
    rows: Int,
    height: Dp,
    onToggle: (HubTask) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val open = remember(tasks) { tasks.filter { !it.done } }
    var adding by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(adding) { if (adding) runCatching { focus.requestFocus() } }
    val submit = {
        if (draft.isNotBlank()) onAdd(draft.trim())
        draft = ""
        adding = false
    }
    HubListCard(
        modifier = modifier
            .height(height)
            .clickable(role = Role.Button, onClickLabel = "Add a task") { adding = true },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tasks",
                color = tokens.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Add task",
                modifier = Modifier.size(22.dp).clip(CircleShape).background(tokens.accent.copy(alpha = 0.18f)).padding(2.dp),
                tint = tokens.accent,
            )
        }
        when {
            adding -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(text = "New task", color = tokens.onSurface.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = TextStyle(color = tokens.onSurface, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(tokens.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                }
                Text(
                    text = "Add",
                    color = tokens.accent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(role = Role.Button) { submit() }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            rows == 0 -> {
                Text(
                    text = if (open.isEmpty()) "All done" else "${open.size} open",
                    color = tokens.onSurface,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                )
                Text(
                    text = when {
                        dueCount > 0 -> "$dueCount due today"
                        open.isNotEmpty() -> open.first().title
                        else -> "Tap to add a task"
                    },
                    color = tokens.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            open.isEmpty() -> {
                Text(
                    text = "All done",
                    color = tokens.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 1,
                )
                Text(text = "Tap to add a task", color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 12.5.sp, maxLines = 1)
            }
            else -> open.take(rows).forEach { task ->
                val due = HubFormat.dueText(task.dueMillis, today)
                val dueNow = HubFormat.isDueNow(task.dueMillis, today)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HubMiniRowHeight)
                        .clickable(role = Role.Checkbox) { onToggle(task) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (task.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = tokens.onSurface.copy(alpha = 0.65f),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            color = tokens.onSurface,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (due != null) {
                            Text(
                                text = due,
                                color = if (dueNow) HubDueRed else tokens.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val HubDueRed: Color = Color(0xFFFF6B7A)

/** Schedule mini card: the next [lines] events as "10:00  Standup"; tap opens the Calendar tab. */
@Composable
private fun HubScheduleMini(
    events: List<HubEvent>,
    total: Int,
    lines: Int,
    height: Dp,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    HubListCard(
        modifier = modifier.height(height).clickable(role = Role.Button, onClick = onOpen),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Schedule",
                color = tokens.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(text = "$total today", color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
        }
        if (events.isEmpty()) {
            Text(
                text = "Nothing scheduled",
                color = tokens.onSurface.copy(alpha = 0.7f),
                fontSize = 13.5.sp,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 1,
            )
        } else {
            events.take(lines).forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable { HubIntents.openEvent(context, event) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (event.allDay) "All day" else HubFormat.clock(event.begin),
                        color = tokens.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = event.title,
                        color = tokens.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Mail                                                                                        */
/* ------------------------------------------------------------------------------------------ */

/** "Emails that need you": avatar, sender and subject rows (the caller passes only as many as fit). */
@Composable
private fun HubMailRows(messages: List<MailMessage>, unread: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { HubKicker("Emails that need you", Modifier.padding(0.dp)) }
            Text(text = "$unread unread", color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 11.5.sp, maxLines = 1)
        }
        HubListCard(modifier = Modifier.clickable(role = Role.Button, onClick = onOpen)) {
            messages.forEachIndexed { index, message ->
                if (index > 0) HubRowDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().height(HubMailRowHeight - 1.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HubAvatar(name = message.from, photoUri = null, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = message.from,
                            color = tokens.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = message.subject,
                            color = tokens.onSurface.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
