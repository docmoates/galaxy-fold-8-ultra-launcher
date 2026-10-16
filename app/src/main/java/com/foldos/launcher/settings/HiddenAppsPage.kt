package com.foldos.launcher.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.notifications.NotificationAppIcon

object HiddenAppsRows {
    /** Route of the page (Settings > More settings > Hidden apps). */
    const val ROUTE: String = "hidden_apps"

    val hidden = SettingsRowInfo("Hidden apps", "Keep apps out of the drawer, search and home screens")

    val page = SettingsPageInfo(
        route = ROUTE,
        title = "Hidden apps",
        subtitle = "Choose apps to hide from the drawer and search",
        icon = Icons.Outlined.VisibilityOff,
        rows = listOf(hidden),
    )
}

/**
 * Settings > More settings > Hidden apps: every installed app with a checkbox; a ticked app is added to
 * `LauncherSettings.hiddenApps` (and disappears from the drawer, search and home pages), an unticked one
 * is removed again (including a single-activity "package/activity" entry). Apps that were hidden when the
 * page opened are listed first (the order stays put while ticking).
 * The app list comes from `graph.apps.allApps` (read from the Application, because the Settings
 * activity provides no `LocalAppGraph`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val graph = remember(context) { FoldOsApplication.from(context).graph }
    val allApps by graph.apps.allApps.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }

    // Live set: drives the checkbox state.
    val hiddenApps = settings.hiddenApps
    // Snapshot from when the page opened: drives the "hidden first" order, so a row does not jump to
    // the top under the finger when it is ticked.
    val initiallyHidden = remember { settings.hiddenApps }
    val rows: List<AppInfo> = remember(allApps, query, initiallyHidden) {
        allApps
            .distinctBy { it.packageName }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareBy<AppInfo> { !isPackageHidden(initiallyHidden, it.packageName) }.thenBy { it.label.lowercase() },
            )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = HiddenAppsRows.page.title, modifier = Modifier.a11yHeading()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = "Tick an app to hide it from the app drawer, search and your home screens. " +
                    "Untick it to bring it back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(text = "Search apps") },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (rows.isEmpty()) {
                Text(
                    text = if (allApps.isEmpty()) "Loading apps..." else "No apps match \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                items(rows, key = { it.packageName }) { app ->
                    val hidden = isPackageHidden(hiddenApps, app.packageName)
                    val setHidden: (Boolean) -> Unit = { hide ->
                        update { current ->
                            current.copy(hiddenApps = withPackageHidden(current.hiddenApps, app.packageName, hide))
                        }
                    }
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) { setHidden(!hidden) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { NotificationAppIcon(packageName = app.packageName, size = 40.dp) },
                        headlineContent = { Text(text = app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (hidden) {
                            { Text(text = "Hidden") }
                        } else {
                            null
                        },
                        trailingContent = {
                            Checkbox(checked = hidden, onCheckedChange = null)
                        },
                    )
                }
            }
        }
    }
}

/** True when [packageName], or any "[packageName]/activity" key, is in [hiddenApps]. */
private fun isPackageHidden(hiddenApps: Set<String>, packageName: String): Boolean =
    packageName in hiddenApps || hiddenApps.any { it.startsWith("$packageName/") }

/** [hiddenApps] with [packageName] (and its single-activity keys) added or removed. */
private fun withPackageHidden(hiddenApps: Set<String>, packageName: String, hide: Boolean): Set<String> =
    if (hide) {
        hiddenApps + packageName
    } else {
        hiddenApps.filterNot { it == packageName || it.startsWith("$packageName/") }.toSet()
    }
