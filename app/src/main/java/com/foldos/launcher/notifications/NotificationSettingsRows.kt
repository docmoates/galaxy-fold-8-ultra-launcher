package com.foldos.launcher.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.foldos.launcher.a11y.a11yGreyscale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.settings.ChipsRow
import com.foldos.launcher.settings.SettingsRowInfo
import com.foldos.launcher.settings.SettingsUpdater
import com.foldos.launcher.settings.StaticRow
import com.foldos.launcher.settings.ToggleRow
import kotlinx.coroutines.launch

/*
 * Settings rows the Integrate agent drops into existing pages. They use the `settings` package's
 * row composables, so they filter with the page's search / Advanced state like every other row.
 */

/** Row metadata (also feeds settings search when added to a `SettingsPageInfo.rows` list). */
object NotificationSettingsRowInfos {
    val quietDrawer = SettingsRowInfo(
        "Quiet notification drawer",
        "Route low-value alerts to a quiet list instead of badges",
    )
    val quietApps = SettingsRowInfo("Quiet apps", "Notifications from these apps never badge")
    val badgeStyle = SettingsRowInfo("Badge style", "Counts, dots or none on app icons")
    val preview = SettingsRowInfo("Notification previews", "Long-press a badged app to read its notifications")
    val access = SettingsRowInfo("Notification access", "Required for badges, previews and the quiet drawer")

    /** Everything above, for a page's searchable row list. */
    val all: List<SettingsRowInfo> = listOf(quietDrawer, quietApps, badgeStyle, preview, access)
}

/**
 * Settings > Features: the "Quiet notification drawer" toggle, the "Quiet apps" multi-select row
 * (opens [QuietAppsPickerSheet]) and, while the drawer is on but access is missing, the access
 * card. State lives in [QuietDrawerStore]; writes run on `graph.appScope`.
 */
@Composable
fun QuietDrawerSettingsRows() {
    val context = LocalContext.current
    val graph = rememberNotificationGraph()
    val store = remember(context) { QuietDrawerStore(context) }
    val config by store.config.collectAsState(initial = QuietDrawerConfig())
    val accessGranted = rememberNotificationAccessGranted()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    ToggleRow(
        info = NotificationSettingsRowInfos.quietDrawer,
        checked = config.enabled,
        onCheckedChange = { on -> graph.appScope.launch { store.setEnabled(on) } },
    )
    StaticRow(
        info = NotificationSettingsRowInfos.quietApps,
        onClick = { pickerOpen = true },
        trailingText = when (config.packages.size) {
            0 -> "None"
            1 -> "1 app"
            else -> "${config.packages.size} apps"
        },
    )
    if (config.enabled && !accessGranted) {
        NotificationAccessCard(
            onOpenSettings = { openNotificationAccessSettings(context) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    if (pickerOpen) {
        QuietAppsPickerSheet(onClose = { pickerOpen = false })
    }
}

/**
 * Settings > Notifications (or Features): badge style chips (`LauncherSettings.badgeStyle`), the
 * long-press preview toggle (`notificationPreview`) and the access card while access is missing.
 * [settings] / [update] are the page's current settings and updater.
 */
@Composable
fun NotificationBadgeSettingsRows(settings: LauncherSettings, update: SettingsUpdater) {
    val context = LocalContext.current
    ChipsRow(
        info = NotificationSettingsRowInfos.badgeStyle,
        value = settings.badgeStyle,
        options = listOf(
            LauncherSettings.BADGE_COUNT to "Count",
            LauncherSettings.BADGE_DOT to "Dot",
            LauncherSettings.BADGE_OFF to "Off",
        ),
        onSelect = { style -> update { it.copy(badgeStyle = style) } },
    )
    ToggleRow(
        info = NotificationSettingsRowInfos.preview,
        checked = settings.notificationPreview,
        onCheckedChange = { on -> update { it.copy(notificationPreview = on) } },
    )
    NotificationAccessCard(
        onOpenSettings = { openNotificationAccessSettings(context) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Multi-select of installed apps (one row per package, from `graph.apps.allApps` so hidden apps
 * are listed too) whose notifications go to the quiet drawer. Checking writes
 * `QuietDrawerStore.setQuiet` on `graph.appScope`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietAppsPickerSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val graph = rememberNotificationGraph()
    val store = remember(context) { QuietDrawerStore(context) }
    val config by store.config.collectAsState(initial = QuietDrawerConfig())
    val allApps by graph.apps.allApps.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }

    val packages: List<AppInfo> = remember(allApps, query, config.packages) {
        allApps
            .distinctBy { it.packageName }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            .sortedWith(compareBy<AppInfo> { it.packageName !in config.packages }.thenBy { it.label.lowercase() })
    }

    ModalBottomSheet(onDismissRequest = onClose, modifier = Modifier.a11yGreyscale()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(text = "Quiet apps", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Their notifications skip badges and previews and wait in the quiet drawer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(text = "Search apps") },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(packages, key = { it.packageName }) { app ->
                val quiet = app.packageName in config.packages
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { graph.appScope.launch { store.setQuiet(app.packageName, !quiet) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { NotificationAppIcon(packageName = app.packageName, size = 40.dp) },
                    headlineContent = { Text(text = app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(text = app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Checkbox(
                            checked = quiet,
                            onCheckedChange = { on -> graph.appScope.launch { store.setQuiet(app.packageName, on) } },
                        )
                    },
                )
            }
        }
        if (packages.isEmpty()) {
            Text(
                text = if (allApps.isEmpty()) "Loading apps..." else "No apps match \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
