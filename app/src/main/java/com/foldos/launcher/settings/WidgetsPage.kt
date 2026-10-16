package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.LauncherSettings

object WidgetsRows {
    val wpGroup = SettingsRowInfo("Widget picker groups by", "How \"Add a widget\" is organized")
    val stacks = SettingsRowInfo("Widget stacks", "Swipe between widgets in one slot")
    val smart = SettingsRowInfo("Smart widgets", "Rules by time, place, fold state, device")
    val androidWidgets = SettingsRowInfo("Android widgets", "Host any app widget")
    val integrations = SettingsRowInfo("Integrations", "Google, Samsung, Microsoft, Spotify, Todoist, Notion, Slack and more")
    val widgetTheme = SettingsRowInfo("Widget theme", "Follow launcher or override per widget", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.WIDGETS,
        title = "Widgets",
        subtitle = "Picker grouping, stacks, smart rules, integrations",
        icon = Icons.Outlined.Widgets,
        rows = listOf(wpGroup, stacks, smart, androidWidgets, integrations, widgetTheme),
    )

    val groupOptions: List<Pair<String, String>> = listOf(
        LauncherSettings.WP_GROUP_CATEGORY to "Category",
        LauncherSettings.WP_GROUP_APP to "App",
        LauncherSettings.WP_GROUP_RECENT to "Recently used, then category",
    )
}

@Composable
fun WidgetsPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(title = WidgetsRows.page.title, onBack = onBack) {
        SelectRow(
            info = WidgetsRows.wpGroup,
            value = settings.wpGroup,
            options = WidgetsRows.groupOptions,
            onSelect = { group -> update { it.copy(wpGroup = group) } },
        )
        ToggleRow(
            info = WidgetsRows.stacks,
            checked = settings.feature(LauncherSettings.FEATURE_STACKS),
            onCheckedChange = { on -> update { it.withFeature(LauncherSettings.FEATURE_STACKS, on) } },
        )
        ToggleRow(
            info = WidgetsRows.smart,
            checked = settings.feature(LauncherSettings.FEATURE_SMART),
            onCheckedChange = { on -> update { it.withFeature(LauncherSettings.FEATURE_SMART, on) } },
        )
        StaticRow(info = WidgetsRows.androidWidgets)
        StaticRow(info = WidgetsRows.integrations)
        StaticRow(info = WidgetsRows.widgetTheme, trailingText = "Follow launcher")
    }
}
