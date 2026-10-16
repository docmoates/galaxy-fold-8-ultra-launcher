package com.foldos.launcher.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.picker.FoldOsWidgetCatalog
import kotlinx.coroutines.launch

object WidgetsRows {
    val wpGroup = SettingsRowInfo("Widget picker groups by", "How the widget picker organizes its app list")
    val resetRecent = SettingsRowInfo("Reset recent widgets", "Clears the \"Recently used\" section of the widget picker")

    val page = SettingsPageInfo(
        route = SettingsRoutes.WIDGETS,
        title = "Widgets",
        subtitle = "How the widget picker is organized",
        icon = Icons.Outlined.Widgets,
        rows = listOf(wpGroup, resetRecent),
    )
}

/** One choice of the picker grouping: stored value, card label, one-line explanation, card glyph. */
private class GroupChoice(
    val value: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
)

private val GROUP_CHOICES = listOf(
    GroupChoice(
        value = LauncherSettings.WP_GROUP_CATEGORY,
        label = "Category",
        description = "Filter chips: Clock, Weather, Calendar and more",
        icon = Icons.Rounded.Category,
    ),
    GroupChoice(
        value = LauncherSettings.WP_GROUP_APP,
        label = "App",
        description = "A plain list of apps, no chips",
        icon = Icons.Rounded.Apps,
    ),
    GroupChoice(
        value = LauncherSettings.WP_GROUP_RECENT,
        label = "Recent",
        description = "Recently used widgets first, then chips",
        icon = Icons.Rounded.History,
    ),
)

@Composable
fun WidgetsPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val graph = remember(context) { FoldOsApplication.from(context).graph }
    val colors = MaterialTheme.colorScheme

    SettingsPageScaffold(title = WidgetsRows.page.title, onBack = onBack) {
        SampleSection(
            title = "Widget picker",
            subtitle = "Every group opens the same two-level picker: the apps first, then their widgets.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GROUP_CHOICES.forEach { choice ->
                    SampleCard(
                        label = choice.label,
                        selected = settings.wpGroup == choice.value,
                        onClick = { update { it.copy(wpGroup = choice.value) } },
                        modifier = Modifier.weight(1f),
                        description = choice.description,
                        previewBackground = colors.surface,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = choice.icon,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            }
        }
        StaticRow(
            info = WidgetsRows.resetRecent,
            onClick = {
                val catalog = graph.widgets as? FoldOsWidgetCatalog
                if (catalog != null) {
                    graph.appScope.launch { catalog.clearRecents() }
                    Toast.makeText(context, "Recent widgets cleared", Toast.LENGTH_SHORT).show()
                }
            },
            showChevron = false,
        )
    }
}
