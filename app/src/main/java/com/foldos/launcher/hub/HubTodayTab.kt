package com.foldos.launcher.hub

import android.Manifest
import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.builtins.FakeWeatherProvider
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.HubCall
import com.foldos.launcher.core.repo.HubContact
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

/**
 * The Hub's Today tab (mock-up `todayPage`): greeting and weather line, briefing sentence, next
 * event with Join, tasks with add / complete, today's schedule, e-mails that need you (when the Mail
 * tab is connected), the "Reach out" row of missed calls and favourites, and permission cards for
 * calendar, call log and contacts while those are missing. Cover: one column; inner: two panes.
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
    val now = rememberHubMinute()
    val today = now.toLocalDate()
    val nowMillis = remember(now) { now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }

    val calendarGranted = rememberHubPermission(Manifest.permission.READ_CALENDAR)
    val callsGranted = rememberHubPermission(Manifest.permission.READ_CALL_LOG)
    val contactsGranted = rememberHubPermission(Manifest.permission.READ_CONTACTS)

    val events by remember(graph) { graph.hub.todayEvents() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by remember(graph) { graph.hub.tasks() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val missed by remember(graph) { graph.hub.missedCalls() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by remember(graph) { graph.hub.favorites() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val mailState by graph.mail.state.collectAsStateWithLifecycle(initialValue = MailState.NotConfigured)
    val weatherProvider = remember { FakeWeatherProvider() }
    val weather by weatherProvider.weather.collectAsStateWithLifecycle()

    val mailOn = LauncherSettings.HUB_TAB_MAIL in settings.hubTabs
    val connectedMail = (mailState as? MailState.Connected)?.takeIf { mailOn }
    val needMail = remember(connectedMail) { connectedMail?.messages?.filter { it.unread }.orEmpty() }
    val briefingOn = settings.feature(LauncherSettings.FEATURE_AI) && settings.aiMode != LauncherSettings.AI_MODE_OFF
    val nextEvent = remember(events, nowMillis) { events.firstOrNull { !it.allDay && it.end > nowMillis } }
    val dueTasks = remember(tasks, today) { tasks.filter { !it.done && HubFormat.isDueNow(it.dueMillis, today) } }
    val people = remember(missed, favorites) { reachOutPeople(missed, favorites) }

    val dateLine = HubFormat.weekdayDate(today)
    val subtitle = weather?.let { "$dateLine · ${it.tempText} ${it.location}" } ?: dateLine

    val header: @Composable () -> Unit = {
        HubPageHeader(
            title = HubFormat.greeting(now.hour),
            subtitle = subtitle,
            subtitleIcon = weather?.condition?.icon,
            trailingIcon = Icons.Rounded.AccountCircle,
            onTrailingClick = { context.startActivity(SettingsActivity.intent(context, SettingsRoutes.HUB)) },
        )
    }
    val briefing: @Composable () -> Unit = {
        if (briefingOn) {
            HubBriefingCard(
                title = briefingTitle(now.hour),
                text = buildHubBriefing(events, dueTasks, nextEvent, missed, needMail.size, calendarGranted, nowMillis),
            )
        }
    }
    val next: @Composable () -> Unit = {
        if (!calendarGranted) {
            HubPermissionCard(
                title = "Calendar",
                body = "Shows your next event, today's schedule and the briefing.",
                permission = Manifest.permission.READ_CALENDAR,
            )
        } else {
            HubNextEventCard(event = nextEvent, allDay = events.firstOrNull { it.allDay }, nowMillis = nowMillis)
        }
    }
    val tasksCard: @Composable (Modifier) -> Unit = { m ->
        HubTasksCard(
            tasks = tasks,
            dueCount = dueTasks.size,
            today = today,
            onToggle = { task -> scope.launch { graph.hub.setTaskDone(task.id, !task.done) } },
            onAdd = { title -> scope.launch { graph.hub.addTask(title, null) } },
            onDelete = { task ->
                val repo = graph.hub as? FoldOsHubRepository
                if (repo != null) scope.launch { repo.deleteTask(task.id) }
            },
            modifier = m,
        )
    }
    val schedule: @Composable (Int, Modifier) -> Unit = { limit, m ->
        if (calendarGranted) {
            HubScheduleCard(events = events, limit = limit, onMore = { onOpenTab(HubTab.CALENDAR) }, modifier = m)
        }
    }
    val mails: @Composable () -> Unit = {
        if (connectedMail != null && needMail.isNotEmpty()) {
            HubMailCard(messages = needMail.take(2), unread = connectedMail.unread, onOpen = { onOpenTab(HubTab.MAIL) })
        }
    }
    val reach: @Composable () -> Unit = {
        if (!callsGranted) {
            HubPermissionCard(
                title = "Call log",
                body = "Lists missed calls to return in \"Reach out\".",
                permission = Manifest.permission.READ_CALL_LOG,
            )
        } else if (people.isNotEmpty()) {
            HubReachOutCard(people = people, today = today, onOpenContacts = { onOpenTab(HubTab.CONTACTS) })
        }
        if (!contactsGranted) {
            HubPermissionCard(
                title = "Contacts",
                body = "Names and photos for calls, plus your favourites.",
                permission = Manifest.permission.READ_CONTACTS,
            )
        }
    }

    if (screen == ScreenKind.COVER) {
        HubScrollColumn(contentPadding = contentPadding, modifier = modifier.padding(horizontal = HubPagePadding)) {
            header()
            briefing()
            next()
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(HubCardGap),
            ) {
                tasksCard(Modifier.weight(1f).fillMaxHeight())
                if (calendarGranted) schedule(3, Modifier.weight(1f).fillMaxHeight())
            }
            mails()
            reach()
        }
    } else {
        HubTwoPane(
            modifier = modifier.padding(horizontal = HubPagePadding),
            left = {
                HubScrollColumn(contentPadding = contentPadding) {
                    header()
                    briefing()
                    next()
                    schedule(4, Modifier.fillMaxWidth())
                }
            },
            right = {
                HubScrollColumn(contentPadding = contentPadding) {
                    HubGap(12.dp)
                    mails()
                    tasksCard(Modifier.fillMaxWidth())
                    reach()
                }
            },
        )
    }
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
 * The template briefing (mock-up `.brf`): counts of today's events and due tasks, the next event,
 * missed calls and unread mail, each as one plain sentence with the key facts in bold.
 */
internal fun buildHubBriefing(
    events: List<HubEvent>,
    dueTasks: List<HubTask>,
    nextEvent: HubEvent?,
    missed: List<HubCall>,
    unreadMail: Int,
    calendarGranted: Boolean,
    nowMillis: Long,
): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    fun b(text: String) = withStyle(bold) { append(text) }
    val eventCount = events.size
    val taskCount = dueTasks.size

    when {
        !calendarGranted && taskCount > 0 -> {
            append("Allow calendar access to see your day; ")
            b(plural(taskCount, "task"))
            append(if (taskCount == 1) " is due today." else " are due today.")
        }
        !calendarGranted -> append("Allow calendar access to see your day at a glance.")
        eventCount > 0 && taskCount > 0 -> {
            append("You have "); b(plural(eventCount, "event")); append(" and "); b(plural(taskCount, "task")); append(" due today.")
        }
        eventCount > 0 -> {
            append("You have "); b(plural(eventCount, "event")); append(" today and nothing else due.")
        }
        taskCount > 0 -> {
            append("Nothing on the calendar, but "); b(plural(taskCount, "task"))
            append(if (taskCount == 1) " is due today." else " are due today.")
        }
        else -> append("Your calendar is clear and nothing is due today.")
    }

    if (nextEvent != null) {
        append(" ")
        if (nextEvent.begin > nowMillis) {
            append("Next up: "); b(nextEvent.title); append(" at "); b(HubFormat.clockAmPm(nextEvent.begin))
            val where = nextEvent.location?.takeUnless { FoldOsHubRepository.findUrl(it) == it }?.trim().orEmpty()
            if (where.isNotEmpty()) { append(" at "); append(where) }
            append(".")
        } else {
            b(nextEvent.title); append(" is on now until "); b(HubFormat.clockAmPm(nextEvent.end)); append(".")
        }
    }

    if (missed.isNotEmpty()) {
        append(" ")
        if (missed.size == 1) {
            b(missed.first().name ?: missed.first().number); append(" called while you were away.")
        } else {
            b("${missed.size} missed calls"); append(" to return.")
        }
    }

    if (unreadMail > 0) {
        append(" "); b(plural(unreadMail, "email")); append(if (unreadMail == 1) " needs you." else " need you.")
    }
}

private fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

@Composable
private fun HubBriefingCard(title: String, text: AnnotatedString, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    HubListCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
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
            )
        }
        Text(
            text = text,
            modifier = Modifier.padding(top = 6.dp),
            color = tokens.onSurface,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Next event                                                                                  */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up `.w.hi.nxt`: highlight card with the start time, countdown, title, details and Join. */
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (shown == null) {
                Column(Modifier.weight(1f)) {
                    Text(text = "No more events today", color = tokens.onHighlight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Nothing scheduled", color = tokens.onHighlight.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (shown.allDay) "All" else HubFormat.clock(shown.begin),
                        color = tokens.onHighlight,
                        fontSize = 24.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (shown.allDay) "day" else HubFormat.relativeToNow(shown, nowMillis),
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
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp), tint = tokens.onHighlight)
                        Text(text = "Join", color = tokens.onHighlight, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Tasks                                                                                       */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up tasks `.lcard` with `.trow`s: tap the circle to complete, long-press a row to delete, add at the bottom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HubTasksCard(
    tasks: List<HubTask>,
    dueCount: Int,
    today: LocalDate,
    onToggle: (HubTask) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (HubTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val open = tasks.filter { !it.done }
    val doneCount = tasks.size - open.size
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (draft.isNotBlank()) {
            onAdd(draft)
            draft = ""
        }
    }
    HubListCard(modifier = modifier) {
        HubCardHeader(title = "Tasks", trailingText = if (dueCount > 0) "$dueCount due" else if (open.isEmpty()) "All done" else "${open.size} open")
        Column(Modifier.padding(bottom = 8.dp)) {
            open.take(5).forEach { task ->
                HubTaskRow(task = task, today = today, onToggle = { onToggle(task) }, onDelete = { onDelete(task) })
            }
            if (open.size > 5) {
                Text(
                    text = "+${open.size - 5} more",
                    color = tokens.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp, bottom = 4.dp),
                )
            }
            tasks.filter { it.done }.take(2).forEach { task ->
                HubTaskRow(task = task, today = today, onToggle = { onToggle(task) }, onDelete = { onDelete(task) })
            }
            if (doneCount > 2) {
                Text(
                    text = "$doneCount done",
                    color = tokens.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp, bottom = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add task",
                    modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = submit),
                    tint = tokens.accent,
                )
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(text = "Add a task", color = tokens.onSurface.copy(alpha = 0.55f), fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = tokens.onSurface, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(tokens.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HubTaskRow(task: HubTask, today: LocalDate, onToggle: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val due = HubFormat.dueText(task.dueMillis, today)
    val dueNow = !task.done && HubFormat.isDueNow(task.dueMillis, today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onDelete)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (task.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (task.done) "Mark not done" else "Mark done",
            modifier = Modifier.size(22.dp),
            tint = if (task.done) tokens.accent else tokens.onSurface.copy(alpha = 0.65f),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                color = tokens.onSurface.copy(alpha = if (task.done) 0.55f else 1f),
                fontSize = 14.sp,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (due != null) {
                Text(
                    text = due,
                    color = if (dueNow) HubDueRed else tokens.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private val HubDueRed: Color = Color(0xFFFF6B7A)

/* ------------------------------------------------------------------------------------------ */
/* Schedule                                                                                    */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun HubScheduleCard(events: List<HubEvent>, limit: Int, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    HubListCard(modifier = modifier) {
        HubCardHeader(title = "Schedule", trailingText = "${events.size} today")
        if (events.isEmpty()) {
            HubEmptyRow("Nothing scheduled")
        } else {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                events.take(limit).forEach { event ->
                    HubEventRow(event = event, onClick = { HubIntents.openEvent(context, event) })
                }
                if (events.size > limit) {
                    Text(
                        text = "+${events.size - limit} more",
                        color = tokens.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onMore),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Mail                                                                                        */
/* ------------------------------------------------------------------------------------------ */

/** "Emails that need you" (mock-up `.mrow`s) from the connected Mail tab's unread messages. */
@Composable
private fun HubMailCard(messages: List<MailMessage>, unread: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val today = LocalDate.now()
    HubListCard(modifier = modifier.clickable(onClick = onOpen)) {
        HubCardHeader(title = "Emails that need you", trailingText = "$unread unread")
        Column(Modifier.padding(bottom = 8.dp)) {
            messages.forEach { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HubAvatar(name = message.from, photoUri = null, size = 42.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = message.from,
                                color = tokens.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = HubFormat.callTime(message.date, today),
                                color = tokens.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                            )
                        }
                        Text(text = message.subject, color = tokens.onSurface, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = message.snippet,
                            color = tokens.onSurface.copy(alpha = 0.65f),
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Reach out                                                                                   */
/* ------------------------------------------------------------------------------------------ */

/** One "Reach out" cell: a missed call ([call] set) or a favourite ([contact] set). */
internal data class HubReachOutPerson(
    val name: String,
    val number: String?,
    val photoUri: String?,
    val call: HubCall?,
    val contact: HubContact?,
)

/** Missed calls (newest first, one per number) followed by favourites not already listed, up to six. */
internal fun reachOutPeople(missed: List<HubCall>, favorites: List<HubContact>, limit: Int = 6): List<HubReachOutPerson> {
    val result = ArrayList<HubReachOutPerson>(limit)
    val numbers = ArrayList<String>()
    for (call in missed) {
        if (result.size >= limit) break
        if (call.number.isBlank() || numbers.any { PhoneNumberUtils.compare(it, call.number) }) continue
        numbers += call.number
        val match = favorites.firstOrNull { it.phone != null && PhoneNumberUtils.compare(it.phone, call.number) }
        result += HubReachOutPerson(
            name = call.name ?: match?.name ?: call.number,
            number = call.number,
            photoUri = match?.photoUri,
            call = call,
            contact = match,
        )
    }
    for (contact in favorites) {
        if (result.size >= limit) break
        if (contact.phone != null && numbers.any { PhoneNumberUtils.compare(it, contact.phone) }) continue
        result += HubReachOutPerson(name = contact.name, number = contact.phone, photoUri = contact.photoUri, call = null, contact = contact)
    }
    return result
}

/** Mock-up `.ppl`: avatar, first name and a status per person, with call / message actions. */
@Composable
private fun HubReachOutCard(
    people: List<HubReachOutPerson>,
    today: LocalDate,
    onOpenContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    HubListCard(modifier = modifier) {
        HubCardHeader(title = "Reach out", trailingText = people.size.toString(), onTrailingClick = onOpenContacts)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            people.forEach { person ->
                Column(
                    modifier = Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val contact = person.contact
                            if (contact != null) HubIntents.openContact(context, contact) else person.number?.let { HubIntents.dial(context, it) }
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HubAvatar(name = person.name, photoUri = person.photoUri, size = 42.dp)
                    Text(
                        text = hubFirstName(person.name),
                        color = tokens.onSurface,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = person.call?.let { "Missed call ${HubFormat.callTime(it.time, today)}" } ?: "Favourite",
                        color = tokens.onSurface.copy(alpha = 0.65f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val number = person.number
                    if (number != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HubIconButton(Icons.Rounded.Call, "Call", { HubIntents.dial(context, number) }, size = 30.dp)
                            HubIconButton(Icons.Rounded.Chat, "Message", { HubIntents.message(context, number) }, size = 30.dp)
                        }
                    }
                }
            }
        }
    }
}
