package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.LauncherSettings

object HubRows {
    val hubOn = SettingsRowInfo("Hub page", "Today briefing, AI, Mail, Calendar and Contacts on one page")
    val position = SettingsRowInfo("Position", "Which side of home the hub sits on")
    val tabBar = SettingsRowInfo("Tab bar")
    val aiTab = SettingsRowInfo("AI tab", "On-device Gemini Nano")
    val mailTab = SettingsRowInfo("Mail tab", "Gmail API with Google sign-in")
    val calTab = SettingsRowInfo("Calendar tab", "Android Calendar Provider: Google, Samsung, Outlook")
    val peopleTab = SettingsRowInfo("Contacts tab", "Android Contacts Provider: Google contacts")
    val briefing = SettingsRowInfo("Briefing sections", "Choose and order: emails, tasks, events, people, weather, commute")
    val opensOn = SettingsRowInfo("Opens on", "Today, or the last tab used", advanced = true)
    val tabOrder = SettingsRowInfo("Tab order", "Drag to reorder tabs", advanced = true)
    val mailAccounts = SettingsRowInfo("Mail accounts", "Gmail, Outlook, IMAP; unified inbox", advanced = true)
    val calendars = SettingsRowInfo("Calendars shown", "Choose which calendars appear", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.HUB,
        title = "Hub",
        subtitle = "Today briefing, AI, Mail, Calendar, Contacts",
        icon = Icons.Outlined.Hub,
        rows = listOf(hubOn, position, tabBar, aiTab, mailTab, calTab, peopleTab, briefing, opensOn, tabOrder, mailAccounts, calendars),
    )

    val positionOptions: List<Pair<String, String>> = listOf(
        LauncherSettings.HUB_POS_LEFT to "Left of home",
        LauncherSettings.HUB_POS_RIGHT to "Right of home",
    )
    val tabBarOptions: List<Pair<String, String>> = listOf(
        LauncherSettings.HUB_BAR_BOTTOM to "Bottom",
        LauncherSettings.HUB_BAR_TOP to "Top",
    )
}

@Composable
fun HubPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val on = settings.hubOn

    fun setTab(tab: String, enabled: Boolean) {
        update { current ->
            val tabs = if (enabled) current.hubTabs + tab else current.hubTabs - tab
            current.copy(hubTabs = tabs)
        }
    }

    SettingsPageScaffold(title = HubRows.page.title, onBack = onBack) {
        ToggleRow(
            info = HubRows.hubOn,
            checked = on,
            onCheckedChange = { enabled -> update { it.copy(hubOn = enabled) } },
        )
        SelectRow(
            info = HubRows.position,
            value = settings.hubPos,
            options = HubRows.positionOptions,
            onSelect = { pos -> update { it.copy(hubPos = pos) } },
            visible = on,
        )
        SelectRow(
            info = HubRows.tabBar,
            value = settings.hubBar,
            options = HubRows.tabBarOptions,
            onSelect = { bar -> update { it.copy(hubBar = bar) } },
            visible = on,
        )
        if (on) SectionHeader(text = "Tabs")
        ToggleRow(
            info = HubRows.aiTab,
            checked = LauncherSettings.HUB_TAB_AI in settings.hubTabs,
            onCheckedChange = { enabled -> setTab(LauncherSettings.HUB_TAB_AI, enabled) },
            visible = on,
        )
        ToggleRow(
            info = HubRows.mailTab,
            checked = LauncherSettings.HUB_TAB_MAIL in settings.hubTabs,
            onCheckedChange = { enabled -> setTab(LauncherSettings.HUB_TAB_MAIL, enabled) },
            visible = on,
        )
        ToggleRow(
            info = HubRows.calTab,
            checked = LauncherSettings.HUB_TAB_CAL in settings.hubTabs,
            onCheckedChange = { enabled -> setTab(LauncherSettings.HUB_TAB_CAL, enabled) },
            visible = on,
        )
        ToggleRow(
            info = HubRows.peopleTab,
            checked = LauncherSettings.HUB_TAB_PEOPLE in settings.hubTabs,
            onCheckedChange = { enabled -> setTab(LauncherSettings.HUB_TAB_PEOPLE, enabled) },
            visible = on,
        )
        StaticRow(info = HubRows.briefing)
        StaticRow(info = HubRows.opensOn, trailingText = "Today")
        StaticRow(info = HubRows.tabOrder)
        StaticRow(info = HubRows.mailAccounts)
        StaticRow(info = HubRows.calendars)
    }
}
