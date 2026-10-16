package com.foldos.launcher.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.repo.NotificationSummary
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.settings.SettingsRoutes

/**
 * The quiet notification drawer (PLAN.md 4.1): a bottom sheet listing, grouped by app and newest
 * first, every notification of the packages the user routed away from badges in
 * Settings > Features > Quiet notification drawer. Tapping a notification opens it; the X dismisses
 * it; each app group has "Clear". Shows the access prompt when notification access is missing and
 * a shortcut to Settings > Features when the drawer is switched off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietDrawerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { QuietDrawerStore(context) }
    val config by store.config.collectAsState(initial = QuietDrawerConfig())
    val flow = remember(store) { quietNotificationsFlow(store) }
    val notifications by flow.collectAsState(initial = emptyList())
    val accessGranted = rememberNotificationAccessGranted()

    val groups: List<Pair<String, List<NotificationSummary>>> = remember(notifications) {
        notifications.groupBy { it.packageName }.entries
            .sortedByDescending { (_, list) -> list.maxOf { it.postTime } }
            .map { (pkg, list) -> pkg to list }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(text = "Quiet notifications", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    !config.enabled -> "The quiet drawer is off"
                    notifications.isEmpty() -> "Nothing waiting from ${config.packages.size} quiet ${plural(config.packages.size, "app")}"
                    else -> "${notifications.size} from ${groups.size} ${plural(groups.size, "app")} · kept here instead of badges"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            !accessGranted -> {
                NotificationAccessCard(
                    onOpenSettings = { openNotificationAccessSettings(context) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            !config.enabled -> {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = "Turn it on in Settings > Features and pick the apps whose alerts should wait here quietly.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        context.startActivity(SettingsActivity.intent(context, SettingsRoutes.FEATURES))
                        onDismiss()
                    }) { Text(text = "Open Features settings") }
                }
            }
            notifications.isEmpty() -> {
                Text(
                    text = "All quiet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    groups.forEachIndexed { index, (packageName, list) ->
                        item(key = "header:$packageName") {
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            QuietGroupHeader(packageName = packageName, count = list.size)
                        }
                        items(list, key = { it.key }) { summary ->
                            NotificationRow(
                                summary = summary,
                                onOpen = {
                                    ActiveNotifications.open(context, summary)
                                    onDismiss()
                                },
                                onDismiss = { ActiveNotifications.dismiss(summary.key) },
                                showAppIcon = false,
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun QuietGroupHeader(packageName: String, count: Int) {
    val context = LocalContext.current
    val label = remember(packageName) { appLabelOf(context, packageName) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NotificationAppIcon(packageName = packageName, size = 28.dp)
        Text(
            text = "$label · $count",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { ActiveNotifications.dismissAll(packageName) }) { Text(text = "Clear") }
    }
}

private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"
