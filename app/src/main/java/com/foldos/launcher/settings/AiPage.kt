package com.foldos.launcher.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.ai.AiCoreProvider
import com.foldos.launcher.ai.AiUsageEntry
import com.foldos.launcher.ai.AiUsageLog
import com.foldos.launcher.core.model.LauncherSettings
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AiRows {
    val ai = SettingsRowInfo("AI features", "Summaries, suggested replies and actions")
    val engine = SettingsRowInfo("Engine", "Template rules, on-device Gemini Nano (AICore), or off")
    val engineStatus = SettingsRowInfo("Engine status", "Whether Gemini Nano can run on this device")
    val suggestions = SettingsRowInfo("Suggestions", "Smart dock apps, drawer categories, briefing")
    val hubTab = SettingsRowInfo("AI tab in the hub", "Ask bar, conversation, suggested actions")
    val usageLog = SettingsRowInfo("AI usage log", "What ran, on which data, with which engine")
    val clearLog = SettingsRowInfo("Clear usage log")
    val permissions = SettingsRowInfo("Permissions", "Calendar, Gmail and Todoist access for the briefing", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.AI,
        title = "AI",
        subtitle = "On-device engine, suggestions, usage log",
        icon = Icons.Outlined.AutoAwesome,
        rows = listOf(ai, engine, engineStatus, suggestions, hubTab, usageLog, clearLog, permissions),
    )

    val engineOptions: List<Pair<String, String>> = listOf(
        LauncherSettings.AI_MODE_TEMPLATE to "Template (on-device rules)",
        LauncherSettings.AI_MODE_ONDEVICE to "Gemini Nano (on-device)",
        LauncherSettings.AI_MODE_OFF to "Off",
    )
}

@Composable
fun AiPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val on = settings.feature(LauncherSettings.FEATURE_AI)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val usageLog = remember(context) { AiUsageLog(context) }
    val entries by usageLog.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val aiCore = remember(context) { AiCoreProvider(context) }
    val nanoStatus by produceState(initialValue = "Checking…", aiCore) {
        value = if (runCatching { aiCore.available() }.getOrDefault(false)) "Ready" else aiCore.unavailableReason()
    }
    var showAllLog by rememberSaveable { mutableStateOf(false) }
    val filter = LocalSettingsRowFilter.current

    SettingsPageScaffold(title = AiRows.page.title, onBack = onBack) {
        ToggleRow(
            info = AiRows.ai,
            checked = on,
            onCheckedChange = { enabled -> update { it.withFeature(LauncherSettings.FEATURE_AI, enabled) } },
        )
        SelectRow(
            info = AiRows.engine,
            value = settings.aiMode,
            options = AiRows.engineOptions,
            onSelect = { mode -> update { it.copy(aiMode = mode) } },
            visible = on,
        )
        StaticRow(
            info = AiRows.engineStatus,
            visible = on,
            showChevron = false,
            trailingText = when (settings.aiMode) {
                LauncherSettings.AI_MODE_OFF -> "Off"
                LauncherSettings.AI_MODE_ONDEVICE -> nanoStatus
                else -> "Template ready"
            },
        )
        if (on && settings.aiMode == LauncherSettings.AI_MODE_ONDEVICE && nanoStatus != "Ready" && filter.shows(AiRows.engineStatus)) {
            Text(
                text = "Questions fall back to the template engine until Gemini Nano is available. Launcher commands always run locally.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
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

        if (filter.shows(AiRows.usageLog)) {
            SectionHeader(text = "AI usage log")
            ListItem(
                headlineContent = { Text(AiRows.usageLog.title) },
                supportingContent = {
                    Text(
                        if (entries.isEmpty()) "Nothing has run yet. Every answer and applied command is listed here with the data it read."
                        else "${entries.size} entries, newest first. Kept on this phone only."
                    )
                },
            )
            val shown = if (showAllLog) entries else entries.take(LOG_PREVIEW)
            shown.forEach { entry -> AiUsageRow(entry) }
            if (entries.size > LOG_PREVIEW) {
                StaticRow(
                    info = SettingsRowInfo(if (showAllLog) "Show fewer" else "Show all ${entries.size}"),
                    onClick = { showAllLog = !showAllLog },
                    showChevron = false,
                )
            }
        }
        StaticRow(
            info = AiRows.clearLog,
            onClick = { scope.launch { usageLog.clear() } },
            visible = entries.isNotEmpty(),
            showChevron = false,
        )
    }
}

private const val LOG_PREVIEW = 8

@Composable
private fun AiUsageRow(entry: AiUsageEntry) {
    val time = remember(entry.time) {
        Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault()))
    }
    ListItem(
        headlineContent = { Text(entry.action, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Text(
                text = "$time · ${entry.engine} · read: ${entry.data}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
