package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.LauncherSettings

object AiRows {
    val ai = SettingsRowInfo("AI features", "Summaries, suggested replies and actions")
    val engine = SettingsRowInfo("Engine", "On-device Gemini Nano (AICore)")
    val suggestions = SettingsRowInfo("Suggestions", "Smart dock apps, drawer categories, briefing")
    val hubTab = SettingsRowInfo("AI tab in the hub", "Ask bar, conversation, suggested actions")
    val permissions = SettingsRowInfo("Permissions", "Calendar, Gmail and Todoist access for the briefing", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.AI,
        title = "AI",
        subtitle = "On-device engine, suggestions, permissions",
        icon = Icons.Outlined.AutoAwesome,
        rows = listOf(ai, engine, suggestions, hubTab, permissions),
    )
}

@Composable
fun AiPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val on = settings.feature(LauncherSettings.FEATURE_AI)
    SettingsPageScaffold(title = AiRows.page.title, onBack = onBack) {
        ToggleRow(
            info = AiRows.ai,
            checked = on,
            onCheckedChange = { enabled -> update { it.withFeature(LauncherSettings.FEATURE_AI, enabled) } },
        )
        StaticRow(info = AiRows.engine, visible = on, trailingText = "On-device")
        StaticRow(info = AiRows.suggestions, visible = on)
        ToggleRow(
            info = AiRows.hubTab,
            checked = LauncherSettings.HUB_TAB_AI in settings.hubTabs,
            onCheckedChange = { enabled ->
                update { current ->
                    val tabs = if (enabled) {
                        current.hubTabs + LauncherSettings.HUB_TAB_AI
                    } else {
                        current.hubTabs - LauncherSettings.HUB_TAB_AI
                    }
                    current.copy(hubTabs = tabs)
                }
            },
            visible = on && settings.hubOn,
        )
        StaticRow(info = AiRows.permissions, visible = on)
    }
}
