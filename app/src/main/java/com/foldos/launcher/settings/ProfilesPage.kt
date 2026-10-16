package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.LauncherSettings

object ProfilesRows {
    val profiles = SettingsRowInfo("Profiles", "Switch by time, place, Wi-Fi or Routines")
    val active = SettingsRowInfo("Active profile")
    val triggers = SettingsRowInfo("Auto-switch triggers", "Time, place, Wi-Fi network, Samsung Routines")
    val perProfile = SettingsRowInfo("What a profile changes", "Home pages, dock, theme, hidden apps", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.PROFILES,
        title = "Profiles",
        subtitle = "Work, Home, Travel, Night; auto-switch triggers",
        icon = Icons.Outlined.Person,
        rows = listOf(profiles, active, triggers, perProfile),
    )
}

@Composable
fun ProfilesPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val on = settings.feature(LauncherSettings.FEATURE_PROFILES)
    SettingsPageScaffold(title = ProfilesRows.page.title, onBack = onBack) {
        ToggleRow(
            info = ProfilesRows.profiles,
            checked = on,
            onCheckedChange = { enabled -> update { it.withFeature(LauncherSettings.FEATURE_PROFILES, enabled) } },
        )
        SelectRow(
            info = ProfilesRows.active,
            value = settings.profile,
            options = ScreensRows.profileOptions,
            onSelect = { name -> update { it.copy(profile = name) } },
            visible = on,
        )
        StaticRow(info = ProfilesRows.triggers, visible = on)
        StaticRow(info = ProfilesRows.perProfile, visible = on)
    }
}
