package com.foldos.launcher.mail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.repo.MailDetail
import com.foldos.launcher.core.repo.MailDraft
import com.foldos.launcher.core.repo.MailMessage
import com.foldos.launcher.core.repo.MailOpenResult
import com.foldos.launcher.core.repo.MailRepository
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ------------------------------------------------------------------------------------------ */
/* Draft model (kept in memory while the app process is alive)                                */
/* ------------------------------------------------------------------------------------------ */

/**
 * An unsent message being written in the Mail tab. The fields are Compose state so the compose pane edits them
 * directly; the seed values remember what was prefilled so "is there anything to lose" can be answered.
 */
internal class MailComposeState(
    val title: String,
    private val seedTo: String = "",
    private val seedCc: String = "",
    private val seedSubject: String = "",
    private val seedBody: String = "",
    val replyToMessageId: String? = null,
    val threadId: String? = null,
) {
    var to by mutableStateOf(seedTo)
    var cc by mutableStateOf(seedCc)
    var bcc by mutableStateOf("")
    var subject by mutableStateOf(seedSubject)
    var body by mutableStateOf(seedBody)
    var showCc by mutableStateOf(seedCc.isNotBlank())

    /** True when the person has typed or changed something compared with the prefill. */
    fun isDirty(): Boolean =
        to != seedTo || cc != seedCc || bcc.isNotBlank() || subject != seedSubject || body != seedBody

    fun toMailDraft(): MailDraft = MailDraft(
        to = splitRecipients(to),
        cc = splitRecipients(cc),
        bcc = splitRecipients(bcc),
        subject = subject.trim(),
        body = body,
        replyToMessageId = replyToMessageId,
        threadId = threadId,
    )

    /** What is wrong with the recipients, as friendly text, or null when the message can be sent. */
    fun validate(): String? {
        val all = splitRecipients(to) + splitRecipients(cc) + splitRecipients(bcc)
        if (splitRecipients(to).isEmpty()) return "Add at least one recipient."
        val bad = all.firstOrNull { !AddressRegex.matches(addressPart(it)) }
        return bad?.let { "\"$it\" is not a valid email address." }
    }
}

/** The one unsent draft; survives the Mail tab leaving the screen, gone when the app process ends. */
internal object MailDraftStore {
    var current by mutableStateOf<MailComposeState?>(null)
}

private val AddressRegex = Regex("^[^\\s@<>,;]+@[^\\s@<>,;]+\\.[^\\s@<>,;]+$")

private fun addressPart(entry: String): String =
    Regex("<([^>]+)>").find(entry)?.groupValues?.get(1)?.trim() ?: entry.trim()

internal fun splitRecipients(text: String): List<String> =
    text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

internal fun newMessageDraft(): MailComposeState = MailComposeState(title = "New message")

private fun displayName(name: String, email: String): String = name.ifBlank { email }

/** "On Fri, Oct 16, 2026 at 9:12 AM, Jane wrote:" followed by the original with "> " in front of each line. */
private fun quotedOriginal(detail: MailDetail): String {
    val head = "On ${formatFullDate(detail.date)}, ${displayName(detail.fromName, detail.fromEmail)} wrote:"
    val text = detail.bodyText.trimEnd().take(MaxQuoteChars)
    val lines = text.lines().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" }
    return "\n\n$head\n$lines\n"
}

private fun prefixed(prefix: String, subject: String): String {
    val clean = subject.trim()
    return if (clean.startsWith(prefix, ignoreCase = true)) clean else "$prefix $clean".trim()
}

/** Reply (or reply all) to [detail]; [self] is the signed-in Gmail address, dropped from the recipients. */
internal fun replyDraft(detail: MailDetail, all: Boolean, self: String?): MailComposeState {
    val me = self?.trim()?.lowercase()
    val sender = if (detail.fromEmail.isBlank()) "" else MailDisplay.of(detail.fromName, detail.fromEmail)
    val toList = mutableListOf<String>()
    val ccList = mutableListOf<String>()
    if (sender.isNotBlank()) toList += sender
    if (all) {
        val taken = mutableSetOf(detail.fromEmail.lowercase())
        if (me != null) taken += me
        detail.to.forEach { if (taken.add(it.email.lowercase())) toList += it.display }
        detail.cc.forEach { if (taken.add(it.email.lowercase())) ccList += it.display }
    }
    return MailComposeState(
        title = if (all) "Reply all" else "Reply",
        seedTo = toList.joinToString(", "),
        seedCc = ccList.joinToString(", "),
        seedSubject = prefixed("Re:", detail.subject),
        seedBody = quotedOriginal(detail),
        replyToMessageId = detail.id,
        threadId = detail.threadId.ifBlank { null },
    )
}

internal fun forwardDraft(detail: MailDetail): MailComposeState = MailComposeState(
    title = "Forward",
    seedSubject = prefixed("Fwd:", detail.subject),
    seedBody = quotedOriginal(detail),
)

private object MailDisplay {
    fun of(name: String, email: String): String = if (name.isBlank() || name == email) email else "$name <$email>"
}

private const val MaxQuoteChars = 20_000
private const val MaxBodyChars = 60_000

/* ------------------------------------------------------------------------------------------ */
/* Formatting                                                                                 */
/* ------------------------------------------------------------------------------------------ */

/** "Fri, Oct 16, 2026 at 9:12 AM". */
internal fun formatFullDate(millis: Long): String {
    if (millis <= 0L) return "an unknown date"
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault()))
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}

private val UrlRegex = Regex("https?://[^\\s<>\"]+")

/** [text] with every http(s) link made tappable (opens through the default URI handler). */
internal fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in UrlRegex.findAll(text)) {
        val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '>', '\'')
        if (url.length < 8) continue
        val start = match.range.first
        append(text.substring(last, start))
        pushLink(
            LinkAnnotation.Url(
                url,
                TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            ),
        )
        append(url)
        pop()
        last = start + url.length
    }
    append(text.substring(last))
}

/* ------------------------------------------------------------------------------------------ */
/* Shared pieces                                                                              */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun PaneBar(
    title: String?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = tokens.onSurface)
            }
        }
        Text(
            text = title.orEmpty(),
            color = tokens.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = if (onBack != null) 4.dp else 0.dp),
        )
        actions()
    }
}

@Composable
private fun outlinedButtonColors() = ButtonDefaults.outlinedButtonColors(contentColor = LocalFoldOsTokens.current.onSurface)

@Composable
private fun outlineStroke() = BorderStroke(1.dp, LocalFoldOsTokens.current.surfaceBorder)

/** A small friendly panel used for load errors inside a pane. */
@Composable
private fun PaneNotice(text: String, actionLabel: String?, onAction: (() -> Unit)?) {
    val tokens = LocalFoldOsTokens.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = text, color = tokens.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
            ) { Text(actionLabel) }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Reader                                                                                     */
/* ------------------------------------------------------------------------------------------ */

/**
 * Reads one message inside the Mail tab. Loads the full message from the server, marks it read (the list updates at
 * once through the repository) and offers Reply, Reply all, Forward and Mark unread. [onBack] is null on the inner
 * screen where the list stays visible beside it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MailReaderPane(
    messageId: String,
    summary: MailMessage?,
    repo: MailRepository,
    onBack: (() -> Unit)?,
    onReply: (MailDetail, Boolean) -> Unit,
    onForward: (MailDetail) -> Unit,
    onOpenInGmail: (threadId: String?) -> Unit,
    onMarkedUnread: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val scope = rememberCoroutineScope()
    var attempt by remember(messageId) { mutableIntStateOf(0) }
    var result by remember(messageId) { mutableStateOf<MailOpenResult?>(null) }
    var note by remember(messageId) { mutableStateOf<String?>(null) }
    var menuOpen by remember(messageId) { mutableStateOf(false) }
    val scroll = remember(messageId) { ScrollState(0) }

    LaunchedEffect(messageId, attempt) {
        result = null
        result = repo.openMessage(messageId)
    }
    val detail = result?.detail
    // Opening marks it read; the repository flips the list row and the unread count before the server answers.
    LaunchedEffect(detail?.id) {
        if (detail != null && (summary?.unread ?: detail.unread)) repo.setRead(detail.id, true)
    }

    FoldOsSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneBar(
                title = null,
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = tokens.onSurface)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Open in Gmail") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenInGmail(detail?.threadId)
                                },
                            )
                        }
                    }
                },
            )
            val failure = result?.error
            when {
                result == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = tokens.accent)
                }
                detail == null -> Column(Modifier.weight(1f)) {
                    PaneNotice(
                        text = failure ?: "Could not open that message.",
                        actionLabel = "Try again",
                        onAction = { attempt++ },
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = detail.subject,
                            color = tokens.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 26.sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MailAvatar(name = displayName(detail.fromName, detail.fromEmail), size = 42.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName(detail.fromName, detail.fromEmail),
                                    color = tokens.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (detail.fromEmail.isNotBlank() && detail.fromEmail != detail.fromName) {
                                    Text(
                                        text = detail.fromEmail,
                                        color = tokens.onSurface.copy(alpha = 0.75f),
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (MailLabels.STARRED in detail.labels) {
                                Icon(Icons.Rounded.Star, contentDescription = "Starred", tint = tokens.accent, modifier = Modifier.size(22.dp))
                            }
                        }
                        Text(
                            text = formatFullDate(detail.date),
                            color = tokens.onSurface.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                        )
                        if (detail.to.isNotEmpty()) {
                            Text(
                                text = "To: " + detail.to.joinToString(", ") { it.display },
                                color = tokens.onSurface.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        if (detail.cc.isNotEmpty()) {
                            Text(
                                text = "Cc: " + detail.cc.joinToString(", ") { it.display },
                                color = tokens.onSurface.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        if (detail.attachments.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                detail.attachments.forEach { attachment ->
                                    val size = formatSize(attachment.size)
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(tokens.onSurface.copy(alpha = 0.08f))
                                            .border(1.dp, tokens.surfaceBorder, RoundedCornerShape(999.dp))
                                            .clickable {
                                                note = "Attachments open in the Gmail app."
                                                onOpenInGmail(detail.threadId)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = tokens.onSurface, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = if (size.isEmpty()) attachment.name else "${attachment.name} · $size",
                                            color = tokens.onSurface,
                                            fontSize = 12.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 260.dp),
                                        )
                                    }
                                }
                            }
                            note?.let {
                                Text(text = it, color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 12.5.sp)
                            }
                        }
                        HorizontalDivider(color = tokens.surfaceBorder.copy(alpha = 0.7f))
                        val text = detail.bodyText.trim()
                        val shown = if (text.length > MaxBodyChars) text.take(MaxBodyChars) else text
                        SelectionContainer {
                            Text(
                                text = if (shown.isEmpty()) AnnotatedString("(no text in this message)") else linkify(shown, tokens.accent),
                                color = tokens.onSurface.copy(alpha = if (shown.isEmpty()) 0.65f else 1f),
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                            )
                        }
                        if (text.length > MaxBodyChars) {
                            Text(
                                text = "This message is long. Open it in Gmail to read the rest.",
                                color = tokens.onSurface.copy(alpha = 0.7f),
                                fontSize = 12.5.sp,
                            )
                        }
                        if (detail.hasHtml) {
                            Text(
                                text = "Shown as plain text. Open in Gmail for the formatted version.",
                                color = tokens.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                    HorizontalDivider(color = tokens.surfaceBorder.copy(alpha = 0.7f))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onReply(detail, false) },
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                        ) { Text("Reply") }
                        OutlinedButton(onClick = { onReply(detail, true) }, colors = outlinedButtonColors(), border = outlineStroke()) {
                            Text("Reply all")
                        }
                        OutlinedButton(onClick = { onForward(detail) }, colors = outlinedButtonColors(), border = outlineStroke()) {
                            Text("Forward")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch { repo.setRead(detail.id, false) }
                                onMarkedUnread()
                            },
                            colors = outlinedButtonColors(),
                            border = outlineStroke(),
                        ) { Text("Mark unread") }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Compose                                                                                    */
/* ------------------------------------------------------------------------------------------ */

/**
 * Writes and sends a message inside the Mail tab. Back keeps the draft in memory ([MailDraftStore]); Discard asks
 * first when there is something to lose. A successful send shows "Sent" for a moment, then [onSent].
 */
@Composable
internal fun MailComposePane(
    draft: MailComposeState,
    repo: MailRepository,
    offline: Boolean,
    onClose: () -> Unit,
    onDiscarded: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(draft) {
        val tokens = LocalFoldOsTokens.current
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var sent by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var confirmDiscard by remember { mutableStateOf(false) }
        val toFocus = remember { FocusRequester() }
        val bodyFocus = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            delay(120)
            runCatching { if (draft.to.isBlank()) toFocus.requestFocus() else bodyFocus.requestFocus() }
        }

        val send: () -> Unit = {
            if (!busy && !sent && !offline) {
                val problem = draft.validate()
                if (problem != null) {
                    error = problem
                } else {
                    error = null
                    busy = true
                    scope.launch {
                        val outcome = repo.send(draft.toMailDraft())
                        busy = false
                        if (outcome.ok) {
                            sent = true
                            delay(1100)
                            onSent()
                        } else {
                            error = outcome.message ?: "Could not send that message. Please try again."
                        }
                    }
                }
            }
        }
        val discard: () -> Unit = {
            if (draft.isDirty()) confirmDiscard = true else onDiscarded()
        }

        FoldOsSurface(modifier = modifier) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                PaneBar(title = draft.title, onBack = if (sent) null else onClose)
                if (sent) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(48.dp))
                        Text(
                            text = "Sent",
                            color = tokens.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(remember { ScrollState(0) })
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = draft.to,
                            onValueChange = { draft.to = it },
                            label = { Text("To") },
                            minLines = 1,
                            maxLines = 4,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = mailFieldColors(),
                            modifier = Modifier.fillMaxWidth().focusRequester(toFocus),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { draft.showCc = !draft.showCc }) {
                                Text("Cc / Bcc", color = tokens.accent)
                                Icon(
                                    imageVector = if (draft.showCc) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (draft.showCc) "Hide Cc and Bcc" else "Show Cc and Bcc",
                                    tint = tokens.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        AnimatedVisibility(visible = draft.showCc) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = draft.cc,
                                    onValueChange = { draft.cc = it },
                                    label = { Text("Cc") },
                                    minLines = 1,
                                    maxLines = 4,
                                    enabled = !busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    colors = mailFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = draft.bcc,
                                    onValueChange = { draft.bcc = it },
                                    label = { Text("Bcc") },
                                    minLines = 1,
                                    maxLines = 4,
                                    enabled = !busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    colors = mailFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = draft.subject,
                            onValueChange = { draft.subject = it },
                            label = { Text("Subject") },
                            minLines = 1,
                            maxLines = 3,
                            enabled = !busy,
                            colors = mailFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = draft.body,
                            onValueChange = { draft.body = it },
                            label = { Text("Message") },
                            minLines = 8,
                            enabled = !busy,
                            colors = mailFieldColors(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).focusRequester(bodyFocus),
                        )
                    }
                    HorizontalDivider(color = tokens.surfaceBorder.copy(alpha = 0.7f))
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val message = if (offline) "No-network mode is on. Turn it off in Settings > Features to send mail." else error
                        if (message != null) {
                            Text(text = message, color = MaterialTheme.colorScheme.error, fontSize = 13.5.sp, lineHeight = 19.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = send,
                                enabled = !busy && !offline,
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(color = tokens.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sending…")
                                } else {
                                    Text("Send")
                                }
                            }
                            OutlinedButton(
                                onClick = discard,
                                enabled = !busy,
                                colors = outlinedButtonColors(),
                                border = outlineStroke(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Discard") }
                        }
                    }
                }
            }
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text("Discard this draft?") },
                text = { Text("Your message has not been sent and cannot be recovered.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        onDiscarded()
                    }) { Text("Discard") }
                },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
            )
        }
    }
}
