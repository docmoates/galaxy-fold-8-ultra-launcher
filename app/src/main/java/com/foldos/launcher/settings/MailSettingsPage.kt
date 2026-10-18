package com.foldos.launcher.settings

import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.MailAccount
import com.foldos.launcher.core.repo.MailState
import com.foldos.launcher.mail.GmailMailRepository
import com.foldos.launcher.mail.GmailProviderClient
import com.foldos.launcher.mail.findActivity
import kotlinx.coroutines.launch

/** Settings > Mail. Route [ROUTE]; registered in `SettingsRoot` / `SettingsRootPage`. */
object MailSettingsRows {
    /** Navigation route of this page (not in `SettingsRoutes`, which the Settings owner keeps). */
    const val ROUTE: String = "mail"

    val account = SettingsRowInfo("Crosspoint account", "Sign in with your Crosspoint email and password")
    val gmailLink = SettingsRowInfo("Gmail link", "Link or unlink your Gmail (approved once in the browser; reconnect when asked to allow reading and sending)")
    val server = SettingsRowInfo("Server address", "Crosspoint server the Mail tab uses; default ${LauncherSettings.DEFAULT_MAIL_SETUP_URL}")
    val refresh = SettingsRowInfo("Refresh now", "Reload the 25 newest inbox messages")
    val loadImages = SettingsRowInfo("Load images in emails", "Always show pictures in messages; turn off to hide them until you tap Load images")
    val mailTab = SettingsRowInfo("Mail tab in the hub", "Inbox list; list and reading pane on the inner screen")
    val gmailApp = SettingsRowInfo("Gmail app counts", "Unread counts per label from the Gmail app; no sign-in needed")
    val chooseAccount = SettingsRowInfo("Account for counts", "Pick the Google account the Gmail app counts come from")
    val storage = SettingsRowInfo("Token storage", "Where the Crosspoint sign-in is kept on this phone", advanced = true)

    val page = SettingsPageInfo(
        route = ROUTE,
        title = "Mail",
        subtitle = "Crosspoint sign-in, Gmail link, server address, Gmail app counts",
        icon = Icons.Outlined.Mail,
        rows = listOf(account, gmailLink, server, refresh, loadImages, mailTab, gmailApp, chooseAccount, storage),
    )
}

/**
 * Settings > Mail: Crosspoint account (sign in / sign out), Gmail link status with Link / Unlink, the server
 * address, Refresh now, the Mail tab switch, and the Gmail app's label counts with an account picker.
 * Uses `FoldOsApplication.graph.mail` directly (Settings runs without `LocalAppGraph`).
 */
@Composable
fun MailSettingsPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val graph = remember(context) { FoldOsApplication.from(context).graph }
    val repo = graph.mail
    val gmail = repo as? GmailMailRepository
    val provider = remember(gmail, context) { gmail?.provider ?: GmailProviderClient(context) }
    val scope = rememberCoroutineScope()
    val filter = LocalSettingsRowFilter.current

    val state by repo.state.collectAsStateWithLifecycle(initialValue = MailState.Connecting)
    val account by repo.account.collectAsStateWithLifecycle(initialValue = MailAccount())
    val tokensEncrypted: Boolean? = if (gmail != null) gmail.tokensEncrypted.collectAsStateWithLifecycle().value else null
    var serverText by rememberSaveable(settings.mailSetupUrl) { mutableStateOf(settings.mailSetupUrl.orEmpty()) }
    var emailText by rememberSaveable { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
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

    val signedIn = account.signedInAs != null
    val statusText = when (val s = state) {
        MailState.SignedOut -> "Not signed in"
        MailState.NotLinked -> "Signed in, Gmail not linked yet"
        MailState.NotConfigured -> "Your administrator has not set up mail yet"
        MailState.Connecting -> "Loading"
        is MailState.Connected -> "Connected, ${s.messages.size} messages, ${s.unread} unread"
        is MailState.Error -> "Problem: ${s.message}"
    }
    val submit: () -> Unit = {
        if (!busy && emailText.isNotBlank() && passwordText.isNotEmpty()) {
            busy = true
            message = null
            scope.launch {
                val result = repo.signIn(emailText, passwordText)
                busy = false
                if (result.ok) passwordText = "" else message = result.message
            }
        }
    }

    SettingsPageScaffold(title = MailSettingsRows.page.title, onBack = onBack) {
        SectionHeader(text = "Crosspoint account")
        if (filter.shows(MailSettingsRows.account)) {
            if (signedIn) {
                ListItem(
                    headlineContent = { Text("Signed in as ${account.signedInAs}") },
                    supportingContent = { Text(statusText) },
                    trailingContent = {
                        OutlinedButton(onClick = { scope.launch { repo.signOut() } }) { Text("Sign out") }
                    },
                )
            } else {
                ListItem(
                    headlineContent = { Text(MailSettingsRows.account.title) },
                    supportingContent = { Text("Use your account from sub.crosspointhealth.org") },
                )
                OutlinedTextField(
                    value = emailText,
                    onValueChange = { emailText = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                OutlinedTextField(
                    value = passwordText,
                    onValueChange = { passwordText = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(onClick = submit, enabled = !busy && emailText.isNotBlank() && passwordText.isNotEmpty()) {
                        Text(if (busy) "Signing in…" else "Sign in")
                    }
                }
            }
        }
        if (filter.shows(MailSettingsRows.gmailLink)) {
            ListItem(
                headlineContent = { Text(MailSettingsRows.gmailLink.title) },
                supportingContent = {
                    Text(
                        when {
                            !signedIn -> "Sign in first"
                            !account.available -> "Your administrator has not set up mail yet"
                            account.waitingForLink -> "Waiting for you to finish in the browser…"
                            account.linked -> "Linked: ${account.gmail ?: "Gmail"}"
                            else -> "Not linked yet"
                        },
                    )
                },
                trailingContent = {
                    if (signedIn && account.available) {
                        if (account.linked) {
                            OutlinedButton(onClick = {
                                scope.launch { message = repo.unlink().message }
                            }) { Text("Unlink") }
                        } else {
                            Button(onClick = {
                                val activity = context.findActivity()
                                if (activity != null) scope.launch { message = repo.startLink(activity).message }
                            }) { Text("Link") }
                        }
                    }
                },
            )
            if (signedIn) {
                message?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        if (filter.shows(MailSettingsRows.server)) {
            ListItem(
                headlineContent = { Text(MailSettingsRows.server.title) },
                supportingContent = { Text("Leave empty for ${LauncherSettings.DEFAULT_MAIL_SETUP_URL}.") },
            )
            OutlinedTextField(
                value = serverText,
                onValueChange = { serverText = it },
                singleLine = true,
                placeholder = { Text(LauncherSettings.DEFAULT_MAIL_SETUP_URL) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        update { it.copy(mailSetupUrl = serverText.trim().ifEmpty { null }) }
                        scope.launch { repo.refresh() }
                    },
                    enabled = serverText.trim() != settings.mailSetupUrl.orEmpty(),
                ) { Text("Save") }
                TextButton(
                    onClick = {
                        serverText = ""
                        update { it.copy(mailSetupUrl = null) }
                        scope.launch { repo.refresh() }
                    },
                    enabled = serverText.isNotEmpty() || settings.mailSetupUrl != null,
                ) { Text("Reset") }
            }
        }
        StaticRow(
            info = MailSettingsRows.refresh,
            onClick = { scope.launch { repo.refresh() } },
            visible = signedIn,
        )
        ToggleRow(
            info = MailSettingsRows.loadImages,
            checked = settings.mailLoadImages,
            onCheckedChange = { on -> update { it.copy(mailLoadImages = on) } },
        )
        ToggleRow(
            info = MailSettingsRows.mailTab,
            checked = LauncherSettings.HUB_TAB_MAIL in settings.hubTabs,
            onCheckedChange = { enabled ->
                update { current ->
                    val tabs = if (enabled) current.hubTabs + LauncherSettings.HUB_TAB_MAIL else current.hubTabs - LauncherSettings.HUB_TAB_MAIL
                    current.copy(hubTabs = tabs)
                }
            },
            visible = settings.hubOn,
        )

        SectionHeader(text = "Gmail app")
        if (filter.shows(MailSettingsRows.gmailApp)) {
            ListItem(
                headlineContent = { Text(MailSettingsRows.gmailApp.title) },
                supportingContent = {
                    Text(
                        when {
                            !provider.gmailInstalled() -> "The Gmail app is not installed."
                            counts.isEmpty() -> "No account is visible yet. Pick one below."
                            else -> MailSettingsRows.gmailApp.subtitle.orEmpty()
                        },
                    )
                },
            )
            counts.filter { it.isSystem && it.canonicalName in GmailProviderClient.SYSTEM_LABEL_ORDER }
                .groupBy { it.account }
                .forEach { (accountName, labels) ->
                    ListItem(
                        headlineContent = { Text(accountName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
                        supportingContent = {
                            Text(labels.joinToString(" · ") { "${it.name} ${it.unread}" })
                        },
                    )
                }
        }
        StaticRow(
            info = MailSettingsRows.chooseAccount,
            onClick = { runCatching { accountPicker.launch(provider.chooseAccountIntent()) } },
            trailingText = provider.chosenAccount() ?: "Auto",
        )
        StaticRow(
            info = MailSettingsRows.storage,
            trailingText = when {
                gmail == null -> "n/a"
                tokensEncrypted == null -> "Checking"
                tokensEncrypted -> "Encrypted (Android Keystore)"
                else -> "Plain app storage"
            },
            showChevron = false,
        )
    }
}
