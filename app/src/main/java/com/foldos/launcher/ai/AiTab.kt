package com.foldos.launcher.ai

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.builtins.FocusTimerStore
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.AiReply
import com.foldos.launcher.hub.HubIntents
import com.foldos.launcher.hub.fitCount
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.settings.SettingsRoutes
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil

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

    fun send(graph: com.foldos.launcher.core.AppGraph, text: String) {
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

    /** Adds a user line and an assistant reply that the tab produced locally (task added, directions opened). */
    fun local(userText: String, reply: String) {
        messages.add(AiChatMessage(nextId++, userText, fromUser = true))
        messages.add(AiChatMessage(nextId++, reply, fromUser = false))
    }

    fun clear() {
        messages.clear()
    }
}

/** What the next line typed in the ask bar means instead of a normal question. */
private const val PENDING_TASK = "task"
private const val PENDING_REMINDER = "reminder"
private const val PENDING_DIRECTIONS = "directions"

private val QuickCellMinHeight: Dp = 46.dp
/** Tiles stretch to at most 1.25x the base height when rows are left over; the rest goes into the row gaps. */
private const val QuickStretchMax = 1.25f
private val QuickGap: Dp = 8.dp

/**
 * The Hub's AI tab (1.6.0, "Action grid"): a grid of compact quick-action tiles (2 columns on the cover,
 * 3 on the inner screen) that fills the available height without scrolling, and the Ask bar (text field and
 * mic) pinned at the bottom. Tapping an action or sending text opens the conversation in a panel that replaces
 * the grid (back arrow returns); the engine chip lives in that panel's header. Only as many actions as fit are
 * shown. Reads `LocalAppGraph.current.ai`; must be composed inside `FoldOsTheme`.
 */
@Composable
fun AiTab(screen: ScreenKind, settings: LauncherSettings, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val tokens = LocalFoldOsTokens.current
    val scope = rememberCoroutineScope()
    val inner = screen != ScreenKind.COVER
    val aiOn = settings.feature(LauncherSettings.FEATURE_AI)

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

    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    val openAiSettings = { context.startActivity(SettingsActivity.intent(context, SettingsRoutes.AI)) }

    val handlePending: (String, String) -> Unit = { kind, text ->
        scope.launch {
            when (kind) {
                PENDING_TASK -> {
                    runCatching { graph.hub.addTask(text, null) }
                    AiConversation.local(text, "Added the task “$text”. It shows in Tasks on the Today tab.")
                }
                PENDING_REMINDER -> {
                    val due = nextHourMillis()
                    runCatching { graph.hub.addTask(text, due) }
                    AiConversation.local(text, "Reminder added for ${aiClock(due)}. It shows in Tasks on the Today tab.")
                }
                else -> {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(text)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    AiConversation.local(text, "Opening directions to $text.")
                }
            }
        }
    }
    val submit: (String) -> Unit = { text ->
        val line = text.trim()
        if (line.isNotEmpty()) {
            draft = ""
            panelOpen = true
            val kind = pending
            if (kind == null) {
                AiConversation.send(graph, line)
            } else {
                pending = null
                handlePending(kind, line)
            }
        }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) submit(heard)
    }
    val startSpeech: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask anything or tell the launcher")
        runCatching { speech.launch(intent) }.onFailure {
            panelOpen = true
            AiConversation.note("Voice input isn't available on this device.")
        }
    }

    val runAction: (AiQuickAction) -> Unit = { action ->
        when (action.id) {
            "call_back" -> scope.launch {
                val call = runCatching { graph.hub.missedCalls().first().firstOrNull() }.getOrNull()
                if (call != null) {
                    HubIntents.dial(context, call.number)
                } else {
                    panelOpen = true
                    AiConversation.local(action.label, "No missed calls to return.")
                }
            }
            "find_contact" -> runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "add_event" -> HubIntents.newEvent(context, nextHourMillis())
            "add_task" -> {
                panelOpen = true
                pending = PENDING_TASK
                AiConversation.note("What's the task? Type it below.")
            }
            "set_reminder" -> {
                panelOpen = true
                pending = PENDING_REMINDER
                AiConversation.note("What should I remind you about? I'll add it to Tasks, due in about an hour.")
            }
            "directions" -> {
                panelOpen = true
                pending = PENDING_DIRECTIONS
                AiConversation.note("Where to? Type a place or address below.")
            }
            "compose_email" -> launchSafely(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
            "message_someone" -> launchSafely(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            "timer" -> launchSafely(context, Intent(android.provider.AlarmClock.ACTION_SET_TIMER))
            "set_alarm" -> launchSafely(context, Intent(android.provider.AlarmClock.ACTION_SET_ALARM))
            "open_gmail" -> {
                val gmail = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                launchSafely(context, gmail ?: Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
            }
            "focus_25" -> scope.launch {
                runCatching { FocusTimerStore(context).start(FocusTimerStore.SESSION_MILLIS) }
                panelOpen = true
                AiConversation.local(action.label, "Started a 25-minute focus session. The Focus timer widget shows the countdown.")
            }
            else -> action.prompt?.let { prompt ->
                panelOpen = true
                AiConversation.send(graph, prompt)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!aiOn) {
            AiTopBar(title = "Assistant", onSettings = openAiSettings)
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

        if (panelOpen) {
            AiPanelHeader(
                engineLabel = engineLabel,
                onBack = {
                    panelOpen = false
                    pending = null
                },
                onEngine = openAiSettings,
            )
            AiConversationList(
                messages = AiConversation.messages,
                busy = AiConversation.busy,
                modifier = Modifier.weight(1f),
            )
        } else {
            AiTopBar(title = "Assistant", onSettings = openAiSettings)
            val actions = remember(settings.aiQuickActions) { AiQuickActions.resolve(settings.aiQuickActions) }
            AiActionGrid(
                actions = actions,
                columns = if (inner) 3 else 2,
                onAction = runAction,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        AiAskBar(
            value = draft,
            onValueChange = { draft = it },
            onSend = { submit(draft) },
            onMic = startSpeech,
            enabled = !AiConversation.busy,
            placeholder = when (pending) {
                PENDING_TASK -> "Type the task"
                PENDING_REMINDER -> "Remind me to…"
                PENDING_DIRECTIONS -> "Type a place or address"
                else -> "Ask anything or tell the launcher"
            },
        )
    }
}

private fun launchSafely(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun nextHourMillis(): Long =
    LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/* ------------------------------------------------------------------------------------------ */
/* Grid                                                                                       */
/* ------------------------------------------------------------------------------------------ */

/**
 * The quick-action tiles: [columns] per row, as many rows as [fitCount] says the height holds, tiles stretched
 * (up to 64dp) to fill the height. Actions that do not fit are dropped, never scrolled.
 */
@Composable
private fun AiActionGrid(
    actions: List<AiQuickAction>,
    columns: Int,
    onAction: (AiQuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    BoxWithConstraints(modifier = modifier) {
        if (actions.isEmpty()) {
            Text(
                text = "No quick actions are shown. Ask below, or pick some under Settings › AI › Quick actions.",
                color = tokens.onSurface.copy(alpha = 0.75f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(4.dp),
            )
            return@BoxWithConstraints
        }
        val rowsFit = fitCount(maxHeight, QuickCellMinHeight, QuickGap)
        val rows = minOf(ceil(actions.size / columns.toFloat()).toInt(), rowsFit)
        if (rows <= 0) return@BoxWithConstraints
        val cellHeight = ((maxHeight - QuickGap * (rows - 1)) / rows).coerceIn(QuickCellMinHeight, QuickCellMinHeight * QuickStretchMax)
        val gap = if (rows > 1) ((maxHeight - cellHeight * rows) / (rows - 1)).coerceAtLeast(QuickGap) else QuickGap
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            actions.take(rows * columns).chunked(columns).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QuickGap)) {
                    line.forEach { action ->
                        AiActionTile(action = action, height = cellHeight, onClick = { onAction(action) }, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - line.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A single-line compact tile: accent icon and label. */
@Composable
private fun AiActionTile(action: AiQuickAction, height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(
        modifier = modifier
            .height(height)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(action.icon, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(20.dp))
            Text(
                text = action.label,
                color = tokens.onSurface,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Pieces                                                                                     */
/* ------------------------------------------------------------------------------------------ */

/** Slim title row of the grid view: 24sp title with the AI settings shortcut. */
@Composable
private fun AiTopBar(title: String, onSettings: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tokens.accent, modifier = Modifier.padding(start = 4.dp).size(22.dp))
        Text(
            text = title,
            color = tokens.onSurface,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            maxLines = 1,
        )
        IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Settings, contentDescription = "AI settings", tint = tokens.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
        }
    }
}

/** Panel header: back arrow, title and the engine chip (moved here from the grid). */
@Composable
private fun AiPanelHeader(engineLabel: String, onBack: () -> Unit, onEngine: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to quick actions", tint = tokens.onSurface, modifier = Modifier.size(24.dp))
        }
        Text(
            text = "Assistant",
            color = tokens.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            AiChip(text = engineLabel, icon = Icons.Rounded.Lock, onClick = onEngine)
        }
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
    placeholder: String,
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
                                text = if (enabled) placeholder else "Thinking…",
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
private fun AiChip(text: String, icon: ImageVector?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(999.dp)
    val base = modifier
        .clip(shape)
        .background(tokens.surface)
        .border(1.dp, tokens.surfaceBorder, shape)
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tokens.onSurface, modifier = Modifier.size(14.dp))
        Text(text = text, color = tokens.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The conversation inside the panel; this list may scroll (it is a chat, not the tab). */
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
