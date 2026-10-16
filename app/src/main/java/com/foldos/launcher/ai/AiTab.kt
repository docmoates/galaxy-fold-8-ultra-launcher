package com.foldos.launcher.ai

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.builtins.FocusTimerStore
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.AiReply
import com.foldos.launcher.mail.GmailIntents
import com.foldos.launcher.mail.GmailMailRepository
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.settings.SettingsRoutes
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One bubble of the AI tab's conversation. */
data class AiChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    /** True when the assistant's reply confirms a settings change it already applied. */
    val appliedSettings: Boolean = false,
)

/**
 * Process-wide conversation state, so switching hub tabs (or folding) keeps the thread. Replies
 * are produced on the app graph's `appScope`, so a request keeps running when the tab leaves.
 */
object AiConversation {
    val messages: SnapshotStateList<AiChatMessage> = mutableStateListOf()

    var busy: Boolean by mutableStateOf(false)
        private set

    private var nextId: Long = 1L

    fun send(graph: AppGraph, text: String) {
        val request = text.trim()
        if (request.isEmpty() || busy) return
        busy = true
        messages.add(AiChatMessage(nextId++, request, fromUser = true))
        graph.appScope.launch {
            val reply: AiReply = runCatching { graph.ai.handle(request) }
                .getOrElse { AiReply("Something went wrong: ${it.message ?: it.javaClass.simpleName}", appliedSettings = false) }
            withContext(Dispatchers.Main) {
                messages.add(AiChatMessage(nextId++, reply.text, fromUser = false, appliedSettings = reply.appliedSettings))
                busy = false
            }
        }
    }

    /** Adds an assistant-side note (e.g. "Started a focus session") without a request. */
    fun note(text: String) {
        messages.add(AiChatMessage(nextId++, text, fromUser = false))
    }

    fun clear() {
        messages.clear()
    }
}

/** A one-tap action under "Suggested actions" (mock-up `.acts`). */
data class AiSuggestedAction(
    val icon: ImageVector,
    val text: String,
    val onClick: () -> Unit,
)

private val TRY_ASKING: List<String> = listOf(
    "What's next on my calendar?",
    "What's due today?",
    "Make my inner screen a dashboard",
    "Switch to dark mode",
    "Use the ocean colors",
    "Set the bold theme",
    "Hide Reddit",
    "Open Spotify when I unfold",
    "What's in my inbox?",
)

/**
 * The Hub's AI tab (mock-up `aiPage`): header, ask bar with mic, engine chips, the conversation
 * and "Try asking" prompts. The inner screen adds a right pane with suggested actions (navigate
 * to the next event, reply hints, call back, focus session) and a "For you" briefing card. Reads
 * `LocalAppGraph.current.ai`; must be composed inside `FoldOsTheme`.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AiTab(screen: ScreenKind, settings: LauncherSettings, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val tokens = LocalFoldOsTokens.current
    val scope = rememberCoroutineScope()
    val inner = screen != ScreenKind.COVER
    val aiOn = settings.feature(LauncherSettings.FEATURE_AI)

    val builder = remember(graph) { AiContextBuilder(graph.settings, graph.apps, graph.hub, graph.badges, graph.mail) }
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            refreshTick++
        }
    }
    val replies = AiConversation.messages.count { !it.fromUser }
    val snapshot by produceState<AiContextSnapshot?>(initialValue = null, builder, refreshTick, replies) {
        value = runCatching { builder.snapshot() }.getOrNull()
    }
    val onDeviceReady by produceState(initialValue = false, settings.aiMode, context) {
        value = settings.aiMode == LauncherSettings.AI_MODE_ONDEVICE &&
            runCatching { AiCoreProvider(context).available() }.getOrDefault(false)
    }
    val engineLabel = when {
        !aiOn -> "AI features off"
        settings.aiMode == LauncherSettings.AI_MODE_OFF -> "Assistant off · commands still work"
        settings.aiMode == LauncherSettings.AI_MODE_ONDEVICE ->
            if (onDeviceReady) "On-device · Gemini Nano" else "On-device · Template (Nano unavailable)"
        else -> "On-device · Template rules"
    }

    var draft by rememberSaveable { mutableStateOf("") }
    val send: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            AiConversation.send(graph, text)
            draft = ""
        }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) send(heard)
    }
    val startSpeech: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask anything or tell the launcher")
        runCatching { speech.launch(intent) }.onFailure { AiConversation.note("Voice input isn't available on this device.") }
    }
    val openAiSettings = { context.startActivity(SettingsActivity.intent(context, SettingsRoutes.AI)) }

    val conversation: @Composable (Modifier) -> Unit = { columnModifier ->
        Column(modifier = columnModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiAskBar(
                value = draft,
                onValueChange = { draft = it },
                onSend = { send(draft) },
                onMic = startSpeech,
                enabled = !AiConversation.busy,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiChip(text = engineLabel, icon = Icons.Rounded.Lock, onClick = openAiSettings)
                AiChip(text = "Calendar, Mail, Tasks, Calls", icon = Icons.Rounded.Link, onClick = null)
            }
            AiSectionLabel("Conversation")
            AiConversationList(messages = AiConversation.messages, busy = AiConversation.busy, modifier = Modifier.weight(1f))
            AiSectionLabel("Try asking")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TRY_ASKING.forEach { prompt -> AiChip(text = prompt, icon = null, onClick = { send(prompt) }) }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AiPageHeader(title = "Assistant", subtitle = "Ask, plan, or change the launcher")
        if (!aiOn) {
            FoldOsSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI features are off", color = tokens.onSurface, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "The assistant, briefing and suggested actions are switched off. Everything runs on this phone; turn it back on under Settings › AI.",
                        color = tokens.onSurface.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Button(
                        onClick = openAiSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                    ) { Text("Open Settings › AI") }
                }
            }
            return@Column
        }
        if (!inner) {
            conversation(Modifier.weight(1f).fillMaxWidth())
        } else {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                conversation(Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AiSectionLabel("Suggested actions")
                    val actions = suggestedActions(snapshot, graph, scope)
                    if (actions.isEmpty()) {
                        AiActionRow(
                            action = AiSuggestedAction(Icons.Rounded.Settings, "Nothing to suggest yet; connect calendar, mail or tasks") { openAiSettings() },
                        )
                    } else {
                        actions.forEach { action -> AiActionRow(action) }
                    }
                    AiSectionLabel("For you")
                    AiBriefingCard(snapshot = snapshot)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Suggested actions                                                                          */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun suggestedActions(
    snapshot: AiContextSnapshot?,
    graph: AppGraph,
    scope: kotlinx.coroutines.CoroutineScope,
): List<AiSuggestedAction> {
    val context = LocalContext.current
    if (snapshot == null) return emptyList()
    val actions = ArrayList<AiSuggestedAction>()
    val next = snapshot.nextEvent() ?: snapshot.currentEvent()
    if (next != null) {
        val location = next.location?.trim().orEmpty()
        if (location.isNotEmpty()) {
            actions.add(
                AiSuggestedAction(Icons.Rounded.DirectionsCar, "Navigate to $location") {
                    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(location)}"))
                    runCatching { context.startActivity(geo) }
                },
            )
        } else {
            actions.add(
                AiSuggestedAction(Icons.Rounded.Event, "Open ${next.title} (${aiClock(next.begin)})") {
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, next.id)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
            )
        }
    }
    val unread = snapshot.unreadMessages.firstOrNull()
    if (unread != null) {
        val hint = replyHintFor(unread.subject + " " + unread.snippet)
        actions.add(
            AiSuggestedAction(Icons.AutoMirrored.Rounded.Reply, "Reply to ${unread.from.substringBefore(' ')}: “$hint”") {
                val gmail = graph.mail as? GmailMailRepository
                val threadId = gmail?.threadIdFor(unread.id)
                if (threadId != null) GmailIntents.openThread(context, threadId, null) else GmailIntents.openGmail(context)
            },
        )
    }
    val call = snapshot.missedCalls.firstOrNull()
    if (call != null) {
        actions.add(
            AiSuggestedAction(Icons.Rounded.Call, "Call back ${call.name ?: call.number}") {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(call.number)}"))) }
            },
        )
    }
    if (snapshot.openTasks.isNotEmpty() || actions.size < 2) {
        actions.add(
            AiSuggestedAction(Icons.Rounded.Timer, "Start a 25-minute focus session") {
                scope.launch {
                    runCatching { FocusTimerStore(context).start(FocusTimerStore.SESSION_MILLIS) }
                    AiConversation.note("Started a 25-minute focus session. The Focus timer widget shows the countdown.")
                }
            },
        )
    }
    return actions
}

private fun replyHintFor(text: String): String {
    val lower = text.lowercase()
    return when {
        Regex("\\b(meet|meeting|1:1|call|sync|invite|reschedul|move|push)").containsMatchIn(lower) -> "That works for me"
        lower.contains('?') -> "Yes, let me check"
        else -> "Thanks, got it"
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Pieces                                                                                     */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun AiPageHeader(title: String, subtitle: String) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = tokens.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Medium, lineHeight = 33.sp)
            Text(text = subtitle, color = tokens.onSurface.copy(alpha = 0.75f), fontSize = 13.5.sp)
        }
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = tokens.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.size(38.dp),
        )
    }
}

/** The ask bar (mock-up `.ask`): accent ring, sparkle, text field, mic and send. */
@Composable
private fun AiAskBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    enabled: Boolean,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(29.dp)
    FoldOsSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .border(2.dp, tokens.accent.copy(alpha = 0.45f), shape),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(24.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(color = tokens.onSurface, fontSize = 15.5.sp),
                cursorBrush = SolidColor(tokens.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = if (enabled) "Ask anything or tell the launcher" else "Thinking…",
                                color = tokens.onSurface.copy(alpha = 0.65f),
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerField()
                    }
                },
            )
            if (value.isBlank()) {
                IconButton(onClick = onMic, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Speak", tint = tokens.onSurface, modifier = Modifier.size(24.dp))
                }
            } else {
                IconButton(onClick = onSend, enabled = enabled, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = tokens.accent, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AiChip(text: String, icon: ImageVector?, onClick: (() -> Unit)?) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(999.dp)
    val base = Modifier
        .clip(shape)
        .background(tokens.surface)
        .border(1.dp, tokens.surfaceBorder, shape)
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tokens.onSurface, modifier = Modifier.size(14.dp))
        Text(text = text, color = tokens.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun AiSectionLabel(text: String) {
    val tokens = LocalFoldOsTokens.current
    Text(
        text = text.uppercase(),
        color = tokens.onSurface.copy(alpha = 0.72f),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun AiConversationList(messages: List<AiChatMessage>, busy: Boolean, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) {
        val count = messages.size + (if (busy) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    if (messages.isEmpty() && !busy) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            AiBubble(
                text = "Hi. Everything I do stays on this phone. Ask what's next, what's due, who wrote or who called, " +
                    "or tell the launcher what to change.",
                fromUser = false,
            )
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(messages, key = { it.id }) { message ->
            AiBubble(text = message.text, fromUser = message.fromUser, applied = message.appliedSettings)
        }
        if (busy) {
            item(key = "busy") {
                Text(
                    text = "Thinking…",
                    color = tokens.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** A chat bubble (mock-up `.m` / `.m.me`), at most 90% wide, right-aligned for the user. */
@Composable
private fun AiBubble(text: String, fromUser: Boolean, applied: Boolean = false) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.9f), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (fromUser) tokens.accent else tokens.surface)
                    .border(1.dp, if (fromUser) Color.Transparent else tokens.surfaceBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = text,
                    color = if (fromUser) tokens.onAccent else tokens.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                if (applied) {
                    Text(
                        text = "Setting applied",
                        color = tokens.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiActionRow(action: AiSuggestedAction) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = action.onClick),
        shape = RoundedCornerShape((tokens.radius - 4.dp).coerceAtLeast(8.dp)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(action.icon, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(22.dp))
            Text(
                text = action.text,
                color = tokens.onSurface,
                fontSize = 14.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = tokens.onSurface.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
        }
    }
}

/** "For you": the template briefing from the current snapshot (mock-up's feed card slot). */
@Composable
private fun AiBriefingCard(snapshot: AiContextSnapshot?) {
    val tokens = LocalFoldOsTokens.current
    val text = remember(snapshot) {
        if (snapshot == null) "Gathering today's context…" else FoldOsTemplateAiProvider().briefing(snapshot)
    }
    FoldOsSurface(
        modifier = Modifier.fillMaxWidth(),
        color = tokens.highlight,
        borderColor = Color.Transparent,
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tokens.onHighlight, modifier = Modifier.size(18.dp))
                Text(
                    text = "TODAY, IN SHORT",
                    color = tokens.onHighlight.copy(alpha = 0.8f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
            Text(text = text, color = tokens.onHighlight, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Written on-device from your calendar, tasks, mail headers and call log.",
                color = tokens.onHighlight.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}
