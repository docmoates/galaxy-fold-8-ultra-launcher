package com.foldos.launcher.settings

import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.MailState
import com.foldos.launcher.mail.GmailIntents
import com.foldos.launcher.mail.GmailMailRepository
import com.foldos.launcher.mail.GmailProviderClient
import com.foldos.launcher.mail.MailAuthActivity
import com.foldos.launcher.mail.findActivity
import kotlinx.coroutines.launch

/** Settings › Mail (Hub-mail-AI). Route [ROUTE]; Integrate registers it in `SettingsRoot` / `SettingsPages`. */
object MailSettingsRows {
    /** Navigation route of this page (not in `SettingsRoutes`, which the Settings owner keeps). */
    const val ROUTE: String = "mail"

    val clientId = SettingsRowInfo("OAuth client id", "Google Cloud client of type Android for com.foldos.launcher")
    val connect = SettingsRowInfo("Gmail sign-in", "Google sign-in with read-only access (gmail.readonly)")
    val account = SettingsRowInfo("Account", "The address FoldOS is signed in as")
    val refresh = SettingsRowInfo("Refresh inbox", "Reload the 25 newest inbox messages")
    val mailTab = SettingsRowInfo("Mail tab in the hub", "Inbox list; list and reading pane on the inner screen")
    val gmailApp = SettingsRowInfo("Gmail app counts", "Unread counts per label from the Gmail app; no sign-in needed")
    val chooseAccount = SettingsRowInfo("Account for counts", "Pick the Google account the Gmail app counts come from")
    val howTo = SettingsRowInfo("How to get a client id", "Google Cloud Console, step by step", advanced = true)
    val storage = SettingsRowInfo("Token storage", "Where the sign-in tokens are kept", advanced = true)

    val page = SettingsPageInfo(
        route = ROUTE,
        title = "Mail",
        subtitle = "Gmail sign-in, OAuth client id, Gmail app counts",
        icon = Icons.Outlined.Mail,
        rows = listOf(clientId, connect, account, refresh, mailTab, gmailApp, chooseAccount, howTo, storage),
    )
}

/**
 * Settings › Mail: the OAuth client id field, Connect / Disconnect / Refresh for the Gmail API,
 * the Mail tab switch, the Gmail app's label counts with an account picker, and the how-to.
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

    val state by repo.state.collectAsStateWithLifecycle(initialValue = MailState.NotConfigured)
    val accountEmail: String? = if (gmail != null) gmail.accountEmail.collectAsStateWithLifecycle().value else null
    val tokensEncrypted: Boolean? = if (gmail != null) gmail.tokensEncrypted.collectAsStateWithLifecycle().value else null
    val serverId: String = if (gmail != null) gmail.serverClientId.collectAsStateWithLifecycle().value else ""
    var setupUrlText by rememberSaveable(settings.mailSetupUrl) { mutableStateOf(settings.mailSetupUrl.orEmpty()) }
    var clientIdText by rememberSaveable(settings.mailClientId) { mutableStateOf(settings.mailClientId.orEmpty()) }
    var howToOpen by rememberSaveable { mutableStateOf(false) }
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

    val saved = settings.mailClientId?.trim().orEmpty()
    val dirty = clientIdText.trim() != saved
    val connectLabel = when (state) {
        MailState.NotConfigured -> "Connect"
        MailState.Disconnected -> "Connect"
        MailState.Connecting -> "Connecting…"
        is MailState.Connected -> "Disconnect"
        is MailState.Error -> "Connect again"
    }
    val statusText = when (val s = state) {
        MailState.NotConfigured -> "Not configured: save a client id, or ask your admin to set the shared one"
        MailState.Disconnected -> "Signed out"
        MailState.Connecting -> "Signing in and loading the inbox"
        is MailState.Connected -> "Connected · ${s.messages.size} messages, ${s.unread} unread"
        is MailState.Error -> "Problem: ${s.message}"
    }

    SettingsPageScaffold(title = MailSettingsRows.page.title, onBack = onBack) {
        SectionHeader(text = "Gmail API")
        if (filter.shows(MailSettingsRows.clientId)) {
            ListItem(
                headlineContent = { Text(MailSettingsRows.clientId.title) },
                supportingContent = { Text(MailSettingsRows.clientId.subtitle.orEmpty()) },
            )
            OutlinedTextField(
                value = clientIdText,
                onValueChange = { clientIdText = it },
                singleLine = true,
                placeholder = { Text("1234567890-abc.apps.googleusercontent.com") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val next = clientIdText.trim().ifEmpty { null }
                        update { it.copy(mailClientId = next) }
                        if (next == null) scope.launch { repo.disconnect() }
                    },
                    enabled = dirty,
                ) { Text("Save") }
                if (saved.isNotEmpty()) {
                    TextButton(onClick = {
                        clientIdText = ""
                        update { it.copy(mailClientId = null) }
                        scope.launch { repo.disconnect() }
                    }) { Text("Clear") }
                }
            }
        }
        if (saved.isEmpty() && serverId.isNotEmpty()) {
            ListItem(
                headlineContent = { Text("Shared sign-in is set up") },
                supportingContent = { Text("Your organization provides the Google sign-in. Just tap Connect below; you can still paste your own id above to override it.") },
            )
        }
        ListItem(
            headlineContent = { Text("Setup server") },
            supportingContent = { Text("Where the shared Google sign-in id is downloaded from. Leave empty for ${LauncherSettings.DEFAULT_MAIL_SETUP_URL}.") },
        )
        OutlinedTextField(
            value = setupUrlText,
            onValueChange = { setupUrlText = it },
            singleLine = true,
            placeholder = { Text(LauncherSettings.DEFAULT_MAIL_SETUP_URL) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { update { it.copy(mailSetupUrl = setupUrlText.trim().ifEmpty { null }) } },
                enabled = setupUrlText.trim() != settings.mailSetupUrl.orEmpty(),
            ) { Text("Save") }
            TextButton(onClick = { scope.launch { gmail?.refreshServerConfig() } }) { Text("Check now") }
        }
        if (filter.shows(MailSettingsRows.connect)) {
            ListItem(
                headlineContent = { Text(MailSettingsRows.connect.title) },
                supportingContent = { Text(statusText) },
                trailingContent = {
                    val connected = state is MailState.Connected
                    val enabled = (saved.isNotEmpty() || serverId.isNotEmpty()) && state != MailState.Connecting
                    if (connected) {
                        OutlinedButton(onClick = { scope.launch { repo.disconnect() } }) { Text(connectLabel) }
                    } else {
                        Button(
                            onClick = {
                                val activity = context.findActivity()
                                if (activity != null) scope.launch { repo.connect(activity) }
                            },
                            enabled = enabled,
                        ) { Text(connectLabel) }
                    }
                },
            )
        }
        StaticRow(
            info = MailSettingsRows.account,
            trailingText = accountEmail ?: "Not signed in",
            showChevron = false,
        )
        StaticRow(
            info = MailSettingsRows.refresh,
            onClick = { scope.launch { repo.refresh() } },
            visible = state is MailState.Connected,
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
                .forEach { (account, labels) ->
                    ListItem(
                        headlineContent = { Text(account, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
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
        if (filter.shows(MailSettingsRows.howTo)) {
            StaticRow(info = MailSettingsRows.howTo, onClick = { howToOpen = !howToOpen }, trailingText = if (howToOpen) "Hide" else "Show")
            if (howToOpen) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "1. Open console.cloud.google.com and create a project (any name).",
                        "2. APIs & Services › Library: enable the Gmail API.",
                        "3. APIs & Services › OAuth consent screen: External, add your Google account as a test user, add the scope …/auth/gmail.readonly.",
                        "4. Credentials › Create credentials › OAuth client ID › Android. Package name com.foldos.launcher; SHA-1 of the certificate that signed this APK (debug keystore for a debug build).",
                        "5. Copy the client id and paste it above. The redirect is ${MailAuthActivity.REDIRECT_URI}; Android clients need no secret.",
                        "6. Tap Connect, pick the account, allow read-only access. Testing mode tokens expire after 7 days unless the app is published for verification.",
                    ).forEach { line -> Text(text = line, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { GmailIntents.openGmail(context) }) { Text("Open Gmail") }
                }
            }
        }
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
