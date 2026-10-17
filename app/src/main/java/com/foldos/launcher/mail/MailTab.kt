package com.foldos.launcher.mail

import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.foldos.launcher.core.repo.MailAccount
import com.foldos.launcher.core.repo.MailRepository
import kotlinx.coroutines.delay
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.MailMessage
import com.foldos.launcher.core.repo.MailState
import com.foldos.launcher.settings.MailSettingsRows
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Which inbox slice the chip row shows (mock-up `.dtabs`: Primary, Updates, Starred). */
private enum class MailFilter(val label: String) { ALL("All"), PRIMARY("Primary"), UPDATES("Updates"), STARRED("Starred") }

/**
 * The Hub's Mail tab (mock-up `mailPage`). Mail comes from the Crosspoint server: sign in with a Crosspoint
 * account, link Gmail once in the browser, then read the inbox. Cover screen: header, search, category chips,
 * optional AI summary and the inbox list with a Compose FAB. Inner screen: the list on the left and a reading
 * pane with suggested replies on the right. Reads `LocalAppGraph.current.mail`:
 *
 * - [MailState.SignedOut]: a compact Crosspoint sign-in form (plus the Gmail app's unread counts).
 * - [MailState.NotLinked]: a "Connect Gmail" button (read-only approval, once, in the browser).
 * - [MailState.NotConfigured]: the server's administrator has not set up mail yet.
 * - [MailState.Connected]: the Gmail inbox; "Reply in Gmail" deep-links into the conversation.
 *
 * The inbox refreshes when the tab opens and every few minutes while it is visible.
 * Must be composed inside `FoldOsTheme` with `LocalAppGraph` provided (the launcher activity).
 */
@Composable
fun MailTab(screen: ScreenKind, settings: LauncherSettings, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val repo = graph.mail
    val gmail = repo as? GmailMailRepository
    val context = LocalContext.current
    val tokens = LocalFoldOsTokens.current
    val scope = rememberCoroutineScope()
    // Inference gives T = MailState, R = MailState?, so `state` is MailState? until the first emission.
    val state by repo.state.collectAsStateWithLifecycle(initialValue = null)
    val account by repo.account.collectAsStateWithLifecycle(initialValue = MailAccount())
    val provider = remember(gmail) { gmail?.provider ?: GmailProviderClient(context) }
    val accountEmail: String? = account.gmail
    val inner = screen != ScreenKind.COVER
    var notice by remember { mutableStateOf<String?>(null) }

    // Refresh when the tab opens (or the app comes back from the browser) and every few minutes while visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(repo, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                repo.refresh()
                delay(MailAutoRefreshMs)
            }
        }
    }

    val openMailSettings = { context.startActivity(SettingsActivity.intent(context, MailSettingsRows.ROUTE)) }
    val connect: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null) {
            notice = null
            scope.launch {
                val result = repo.startLink(activity)
                notice = if (result.ok) null else result.message
            }
        }
    }
    val signOut: () -> Unit = { scope.launch { repo.signOut() }; Unit }

    val current = state
    val subtitle = when (current) {
        null -> "Gmail"
        MailState.SignedOut -> "Sign in to see your inbox"
        MailState.NotLinked -> "Signed in · Gmail not linked yet"
        MailState.NotConfigured -> "Mail is not set up yet"
        MailState.Connecting -> "Gmail · loading"
        // Connected shows no title block at all: the list starts at the top.
        is MailState.Connected -> accountEmail ?: "Gmail"
        is MailState.Error -> "Gmail · problem loading"
    }
    // Gmail was linked with the old read-only permission (or the server said so mid-session): reconnect first.
    val needsRelink = account.needsRelink && account.signedInAs != null && current != MailState.SignedOut
    val menuItems = buildList<Pair<String, () -> Unit>> {
        add("Mail settings" to { openMailSettings() })
        if (account.linked) add("Disconnect Gmail" to { scope.launch { repo.unlink() }; Unit })
        if (account.signedInAs != null) add("Sign out" to signOut)
    }

    val showList = current is MailState.Connected && !needsRelink
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!showList) MailPageHeader(title = "Inbox", subtitle = subtitle, menuItems = menuItems)
            when {
                needsRelink -> MailSetupContent(provider = provider, inner = inner, modifier = Modifier.weight(1f)) {
                    MailRelinkCard(
                        waiting = account.waitingForLink,
                        notice = notice,
                        onConnect = connect,
                        onSignOut = signOut,
                    )
                }
                else -> when (current) {
                null, MailState.Connecting -> MailCentered {
                    CircularProgressIndicator(color = tokens.accent)
                    Text(
                        text = "Loading your mail…",
                        color = tokens.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                MailState.SignedOut -> MailSetupContent(provider = provider, inner = inner, modifier = Modifier.weight(1f)) {
                    MailSignInCard(repo)
                }
                MailState.NotLinked -> MailSetupContent(provider = provider, inner = inner, modifier = Modifier.weight(1f)) {
                    MailConnectCard(
                        signedInAs = account.signedInAs,
                        waiting = account.waitingForLink,
                        notice = notice,
                        onConnect = connect,
                        onSignOut = signOut,
                    )
                }
                MailState.NotConfigured -> MailSetupContent(provider = provider, inner = inner, modifier = Modifier.weight(1f)) {
                    MailMessageCard(
                        title = "Mail is not set up yet",
                        body = "Your administrator has not set up mail yet. Check back later.",
                        primary = "Check again" to { scope.launch { repo.refresh() }; Unit },
                        secondary = "Sign out" to signOut,
                    )
                }
                is MailState.Error -> MailSetupContent(provider = provider, inner = inner, modifier = Modifier.weight(1f)) {
                    MailMessageCard(
                        title = "Gmail could not be loaded",
                        body = current.message,
                        primary = "Try again" to { scope.launch { repo.refresh() }; Unit },
                        secondary = "Mail settings" to { openMailSettings() },
                    )
                }
                is MailState.Connected -> MailInboxContent(
                    state = current,
                    repo = repo,
                    gmail = gmail,
                    accountEmail = accountEmail,
                    inner = inner,
                    offline = settings.noNetwork,
                    menuItems = menuItems,
                    onRefresh = { scope.launch { repo.refresh() } },
                    modifier = Modifier.weight(1f),
                )
                }
            }
        }
    }
}

/** Gmail was linked with the old read-only permission: one button re-runs the link flow. */
@Composable
private fun MailRelinkCard(
    waiting: Boolean,
    notice: String?,
    onConnect: () -> Unit,
    onSignOut: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(
        modifier = Modifier.fillMaxWidth(),
        color = tokens.highlight,
        borderColor = Color.Transparent,
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Mail, contentDescription = null, tint = tokens.onHighlight, modifier = Modifier.size(18.dp))
                Text(
                    text = "RECONNECT GMAIL",
                    color = tokens.onHighlight.copy(alpha = 0.8f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = "Reconnect Gmail to allow reading and sending",
                color = tokens.onHighlight,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            )
            Text(
                text = "Your Gmail was linked before FoldOS could open and send mail. Approve it once more in your browser. " +
                    "Any draft you started is kept.",
                color = tokens.onHighlight,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            if (waiting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = tokens.onHighlight, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Waiting for you to finish in the browser…",
                        color = tokens.onHighlight,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!notice.isNullOrBlank()) {
                Text(text = notice, color = tokens.onHighlight, fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (waiting) "Open the page again" else "Reconnect") }
            TextButton(onClick = onSignOut) { Text("Sign out", color = tokens.onHighlight) }
        }
    }
}

/** How often the inbox reloads while the Mail tab is visible. */
private const val MailAutoRefreshMs = 3L * 60L * 1000L

/* ------------------------------------------------------------------------------------------ */
/* Signed out / not linked cards                                                              */
/* ------------------------------------------------------------------------------------------ */

@Composable
internal fun mailFieldColors(): TextFieldColors {
    val tokens = LocalFoldOsTokens.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = tokens.onSurface,
        unfocusedTextColor = tokens.onSurface,
        cursorColor = tokens.accent,
        focusedBorderColor = tokens.accent,
        unfocusedBorderColor = tokens.surfaceBorder,
        focusedLabelColor = tokens.accent,
        unfocusedLabelColor = tokens.onSurface.copy(alpha = 0.7f),
    )
}

/** Compact Crosspoint sign-in form: email, password with show/hide, error text and a short note. */
@Composable
private fun MailSignInCard(repo: MailRepository) {
    val tokens = LocalFoldOsTokens.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canSubmit = !busy && email.isNotBlank() && password.isNotEmpty()
    val submit: () -> Unit = {
        if (canSubmit) {
            busy = true
            error = null
            scope.launch {
                val result = repo.signIn(email, password)
                busy = false
                if (result.ok) password = "" else error = result.message
            }
        }
    }
    FoldOsSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Mail, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(18.dp))
                Text(
                    text = "Sign in to read your mail",
                    color = tokens.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                colors = mailFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
                },
                colors = mailFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            val message = error
            if (message != null) {
                Text(text = message, color = MaterialTheme.colorScheme.error, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
            Button(
                onClick = submit,
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(color = tokens.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in…")
                } else {
                    Text("Sign in")
                }
            }
            Text(
                text = "Use your Crosspoint account from sub.crosspointhealth.org",
                color = tokens.onSurface.copy(alpha = 0.7f),
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

/** Signed in, Gmail not linked: one button that opens the server's link page in the browser. */
@Composable
private fun MailConnectCard(
    signedInAs: String?,
    waiting: Boolean,
    notice: String?,
    onConnect: () -> Unit,
    onSignOut: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(
        modifier = Modifier.fillMaxWidth(),
        color = tokens.highlight,
        borderColor = Color.Transparent,
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Mail, contentDescription = null, tint = tokens.onHighlight, modifier = Modifier.size(18.dp))
                Text(
                    text = "CONNECT GMAIL",
                    color = tokens.onHighlight.copy(alpha = 0.8f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
            if (!signedInAs.isNullOrBlank()) {
                Text(text = "Signed in as $signedInAs", color = tokens.onHighlight.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp)
            }
            Text(
                text = "You will approve access to your Gmail once, in your browser. " +
                    "FoldOS can read your inbox, open messages and send mail you write here.",
                color = tokens.onHighlight,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            if (waiting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = tokens.onHighlight, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Waiting for you to finish in the browser…",
                        color = tokens.onHighlight,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!notice.isNullOrBlank()) {
                Text(text = notice, color = tokens.onHighlight, fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (waiting) "Open the page again" else "Connect Gmail") }
            TextButton(onClick = onSignOut) { Text("Sign out", color = tokens.onHighlight) }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Connected: inbox list, reader and compose                                                  */
/* ------------------------------------------------------------------------------------------ */

/**
 * The connected Mail tab. Cover screen: one pane at a time (list, then reader or compose; system Back goes up one
 * level). Inner screen: the list on the left (about 40%) and the reader or compose view on the right.
 */
@Composable
private fun MailInboxContent(
    state: MailState.Connected,
    repo: MailRepository,
    gmail: GmailMailRepository?,
    accountEmail: String?,
    inner: Boolean,
    offline: Boolean,
    menuItems: List<Pair<String, () -> Unit>>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = LocalFoldOsTokens.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MailFilter.ALL) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var pendingDraft by remember { mutableStateOf<MailComposeState?>(null) }
    val listState = rememberLazyListState()
    val draft = MailDraftStore.current

    val filtered = remember(state.messages, query, filter, gmail) {
        state.messages.filter { message ->
            val labels = gmail?.labelsFor(message.id) ?: emptySet()
            val passesFilter = when (filter) {
                MailFilter.ALL -> true
                MailFilter.PRIMARY -> gmail == null ||
                    MailLabels.CATEGORY_PERSONAL in labels || labels.none { it.startsWith("CATEGORY_") }
                MailFilter.UPDATES -> MailLabels.CATEGORY_UPDATES in labels
                MailFilter.STARRED -> MailLabels.STARRED in labels
            }
            val q = query.trim()
            val passesQuery = q.isEmpty() ||
                message.from.contains(q, ignoreCase = true) ||
                message.subject.contains(q, ignoreCase = true) ||
                message.snippet.contains(q, ignoreCase = true)
            passesFilter && passesQuery
        }
    }
    val openInGmail: (String?) -> Unit = { threadId ->
        if (threadId != null) GmailIntents.openThread(context, threadId, accountEmail) else GmailIntents.openGmail(context)
    }

    // A restored "composing" flag without a draft (the app process was recreated) gets a blank draft.
    LaunchedEffect(composing, draft) {
        if (composing && draft == null) MailDraftStore.current = newMessageDraft()
    }

    // null = resume the draft in memory (or start a blank one); otherwise a reply / forward to start.
    val startDraft: (MailComposeState?) -> Unit = { fresh ->
        val existing = MailDraftStore.current
        when {
            fresh == null -> {
                if (existing == null) MailDraftStore.current = newMessageDraft()
                composing = true
            }
            existing != null && existing.isDirty() -> pendingDraft = fresh
            else -> {
                MailDraftStore.current = fresh
                composing = true
            }
        }
    }

    BackHandler(enabled = composing || selectedId != null) {
        // Back keeps an unsent draft in memory; it only closes the compose view.
        if (composing) composing = false else selectedId = null
    }

    val listColumn: @Composable (Modifier) -> Unit = { columnModifier ->
        Column(modifier = columnModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MailTopBar(
                query = query,
                onQueryChange = { query = it },
                onRefresh = onRefresh,
                onCompose = { startDraft(null) },
                menuItems = menuItems,
            )
            MailFilterChips(selected = filter, onSelect = { filter = it }, starredAvailable = gmail != null)
            if (draft != null && draft.isDirty() && !composing) {
                MailDraftBanner(draft = draft, onResume = { startDraft(null) })
            }
            MailListCard(
                messages = filtered,
                selectedId = if (inner) selectedId else null,
                listState = listState,
                onClick = { message -> selectedId = message.id },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val detailPane: @Composable (Modifier) -> Unit = { paneModifier ->
        val activeDraft = MailDraftStore.current
        val openId = selectedId
        when {
            composing -> {
                if (activeDraft != null) {
                    MailComposePane(
                        draft = activeDraft,
                        repo = repo,
                        offline = offline,
                        onClose = { composing = false },
                        onDiscarded = {
                            MailDraftStore.current = null
                            composing = false
                        },
                        onSent = {
                            MailDraftStore.current = null
                            composing = false
                            selectedId = null
                            scope.launch { repo.refresh() }
                        },
                        modifier = paneModifier,
                    )
                }
            }
            openId != null -> MailReaderPane(
                messageId = openId,
                summary = state.messages.firstOrNull { it.id == openId },
                repo = repo,
                onBack = if (inner) null else ({ selectedId = null }),
                onReply = { detail, all -> startDraft(replyDraft(detail, all, accountEmail)) },
                onForward = { detail -> startDraft(forwardDraft(detail)) },
                onOpenInGmail = openInGmail,
                onMarkedUnread = { if (!inner) selectedId = null },
                modifier = paneModifier,
            )
            else -> FoldOsSurface(modifier = paneModifier) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a message", color = tokens.onSurface.copy(alpha = 0.65f))
                }
            }
        }
    }

    if (!inner) {
        if (composing || selectedId != null) {
            detailPane(modifier.fillMaxSize())
        } else {
            listColumn(modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listColumn(Modifier.weight(0.4f).fillMaxHeight())
            detailPane(Modifier.weight(0.6f).fillMaxHeight())
        }
    }

    pendingDraft?.let { fresh ->
        AlertDialog(
            onDismissRequest = { pendingDraft = null },
            title = { Text("Replace your unsent draft?") },
            text = { Text("You have a message that has not been sent. Starting this one replaces it.") },
            confirmButton = {
                TextButton(onClick = {
                    MailDraftStore.current = fresh
                    composing = true
                    pendingDraft = null
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = {
                    composing = true
                    pendingDraft = null
                }) { Text("Open my draft") }
            },
        )
    }
}

/** Slim top bar of the list: search, Compose and the account menu. No title, no counts. */
@Composable
private fun MailTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onCompose: () -> Unit,
    menuItems: List<Pair<String, () -> Unit>>,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MailSearchPill(query = query, onQueryChange = onQueryChange, onRefresh = onRefresh, modifier = Modifier.weight(1f))
        IconButton(
            onClick = onCompose,
            modifier = Modifier.size(46.dp).clip(CircleShape).background(tokens.accent),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = "Compose", tint = tokens.onAccent, modifier = Modifier.size(22.dp))
        }
        MailAccountMenu(menuItems)
    }
}

/** "Draft: subject · Resume" row shown over the list while an unsent draft is waiting. */
@Composable
private fun MailDraftBanner(draft: MailComposeState, onResume: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val label = draft.subject.ifBlank { draft.to.ifBlank { "(no subject)" } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.accent.copy(alpha = 0.14f))
            .clickable(onClick = onResume)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Edit, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(18.dp))
        Text(
            text = "Unsent draft: $label",
            color = tokens.onSurface,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = "Resume", color = tokens.accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MailListCard(
    messages: List<MailMessage>,
    selectedId: String?,
    listState: LazyListState,
    onClick: (MailMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(modifier = modifier.fillMaxWidth()) {
        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing here",
                    color = tokens.onSurface.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(messages, key = { it.id }) { message ->
                    MailRow(
                        message = message,
                        selected = message.id == selectedId,
                        onClick = { onClick(message) },
                    )
                    HorizontalDivider(color = tokens.surfaceBorder.copy(alpha = 0.6f), thickness = 1.dp)
                }
            }
        }
    }
}

/** One inbox row (mock-up `.mrow`): avatar, sender + time, subject, snippet. */
@Composable
private fun MailRow(message: MailMessage, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val weight = if (message.unread) FontWeight.Bold else FontWeight.Medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) tokens.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MailAvatar(name = message.from, size = 42.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.from,
                    color = tokens.onSurface,
                    fontSize = 14.5.sp,
                    fontWeight = weight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatMailTime(message.date),
                    color = if (message.unread) tokens.accent else tokens.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = if (message.unread) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Text(
                text = message.subject,
                color = tokens.onSurface,
                fontSize = 13.5.sp,
                fontWeight = weight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = message.snippet,
                color = tokens.onSurface.copy(alpha = 0.6f),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Setup states (signed out, not linked, not set up, error) + Gmail app counts                */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun MailSetupContent(
    provider: GmailProviderClient,
    inner: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalFoldOsTokens.current
    var pickTick by remember { mutableIntStateOf(0) }
    val countsFlow = remember(provider, pickTick) { provider.observeLabelCounts() }
    val counts by countsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val accountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!name.isNullOrBlank()) {
            provider.setChosenAccount(name)
            pickTick++
        }
    }
    val gmailInstalled = remember(provider) { provider.gmailInstalled() }

    val countsCard: @Composable () -> Unit = {
        FoldOsSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Gmail app",
                        color = tokens.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    val totalUnread = counts.filter { it.canonicalName == GmailProviderClient.LABEL_INBOX }.sumOf { it.unread }
                    MailCountPill(text = if (totalUnread == 1) "1 unread" else "$totalUnread unread")
                }
                when {
                    !gmailInstalled -> MailHint("The Gmail app is not installed, so there are no counts to show.")
                    counts.isEmpty() -> MailHint(
                        "No Gmail account is visible to FoldOS yet. Choose an account below to see its label counts.",
                    )
                    else -> counts.groupBy { it.account }.forEach { (account, labels) ->
                        Text(
                            text = account,
                            color = tokens.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                        )
                        labels.filter { it.isSystem && it.canonicalName in GmailProviderClient.SYSTEM_LABEL_ORDER }
                            .forEach { label ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { GmailIntents.openGmail(context) }
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label.name,
                                        color = tokens.onSurface,
                                        fontSize = 14.5.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${label.total}",
                                        color = tokens.onSurface.copy(alpha = 0.55f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 10.dp),
                                    )
                                    if (label.unread > 0) MailCountPill(text = "${label.unread}")
                                }
                            }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { runCatching { accountPicker.launch(provider.chooseAccountIntent()) } }) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (counts.isEmpty()) "Choose account" else "Add account")
                    }
                    OutlinedButton(onClick = { GmailIntents.openGmail(context) }) { Text("Open Gmail") }
                    if (inner) OutlinedButton(onClick = { GmailIntents.compose(context) }) { Text("Compose") }
                }
            }
        }
    }

    if (inner) {
        // One centred column: the two cards have very different heights, and a second column left an
        // empty rounded block beside the Gmail app card on the wide screen.
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                header()
                countsCard()
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header()
            countsCard()
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Pieces                                                                                     */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun MailPageHeader(title: String, subtitle: String, menuItems: List<Pair<String, () -> Unit>>) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = tokens.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 33.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Mail, contentDescription = null, tint = tokens.onSurface.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                Text(
                    text = subtitle,
                    color = tokens.onSurface.copy(alpha = 0.75f),
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MailAccountMenu(menuItems)
    }
}

/** The account icon with the Mail menu (settings, disconnect, sign out). */
@Composable
private fun MailAccountMenu(menuItems: List<Pair<String, () -> Unit>>) {
    val tokens = LocalFoldOsTokens.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Rounded.AccountCircle,
                contentDescription = "Mail menu",
                tint = tokens.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.size(38.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menuItems.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        menuOpen = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun MailSearchPill(query: String, onQueryChange: (String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.onSurface, modifier = Modifier.size(22.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = tokens.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(tokens.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { innerField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Search mail", color = tokens.onSurface.copy(alpha = 0.65f), fontSize = 15.sp)
                        }
                        innerField()
                    }
                },
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = tokens.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MailFilterChips(selected: MailFilter, onSelect: (MailFilter) -> Unit, starredAvailable: Boolean) {
    val tokens = LocalFoldOsTokens.current
    val options = MailFilter.entries.filter { it != MailFilter.STARRED || starredAvailable }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) tokens.accent else tokens.surface)
                    .border(1.dp, if (on) Color.Transparent else tokens.surfaceBorder, RoundedCornerShape(999.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (option == MailFilter.STARRED) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = if (on) tokens.onAccent else tokens.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = option.label,
                    color = if (on) tokens.onAccent else tokens.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


@Composable
private fun MailCountPill(text: String) {
    val tokens = LocalFoldOsTokens.current
    Text(
        text = text,
        color = tokens.accent,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tokens.accent.copy(alpha = 0.18f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun MailHint(text: String) {
    val tokens = LocalFoldOsTokens.current
    Text(
        text = text,
        color = tokens.onSurface.copy(alpha = 0.7f),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun MailMessageCard(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>?,
) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, color = tokens.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(text = body, color = tokens.onSurface.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = primary.second,
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                ) { Text(primary.first) }
                if (secondary != null) OutlinedButton(onClick = secondary.second) { Text(secondary.first) }
            }
        }
    }
}

@Composable
private fun MailCentered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Initials in a colored circle (mock-up `.av`); the color is stable per name. */
@Composable
fun MailAvatar(name: String, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(avatarColorFor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            color = Color.White,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val AVATAR_COLORS: List<Color> = listOf(
    Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFFE44332), Color(0xFF10B981),
    Color(0xFF1E40AF), Color(0xFF8B5CF6), Color(0xFFF97316), Color(0xFF3B6FE0),
)

internal fun avatarColorFor(name: String): Color = AVATAR_COLORS[abs(name.lowercase().hashCode()) % AVATAR_COLORS.size]

internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> name.trim().take(1).uppercase()
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

internal fun firstNameOf(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull()?.trim(',', '"') ?: name

/**
 * "9:12 AM" today, "Yesterday", the weekday within the last week, else "Oct 3" (with the year when
 * it is not this year). [long] adds the time to the day forms.
 */
internal fun formatMailTime(millis: Long, long: Boolean = false): String {
    if (millis <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val time: LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    val today = LocalDate.now(zone)
    val date = time.toLocalDate()
    val locale = Locale.getDefault()
    val clock = time.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    val day = when {
        date == today -> return clock
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> time.format(DateTimeFormatter.ofPattern("EEE", locale))
        date.year == today.year -> time.format(DateTimeFormatter.ofPattern("MMM d", locale))
        else -> time.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    }
    return if (long) "$day · $clock" else day
}
