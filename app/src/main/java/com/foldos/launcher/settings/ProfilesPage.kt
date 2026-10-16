package com.foldos.launcher.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.Profile
import com.foldos.launcher.profiles.FoldOsProfileRepository
import com.foldos.launcher.profiles.ProfileEditor
import com.foldos.launcher.profiles.ProfileRow
import com.foldos.launcher.profiles.ProfileScheduler
import com.foldos.launcher.profiles.ProfileSwitcherSheet
import com.foldos.launcher.profiles.SaveCurrentProfileDialog
import com.foldos.launcher.profiles.rememberProfileGraph
import kotlinx.coroutines.launch

object ProfilesRows {
    val profiles = SettingsRowInfo("Profiles", "Switch by schedule or Do Not Disturb")
    val active = SettingsRowInfo("Active profile", "Tap to switch; the active profile saves your changes")
    val list = SettingsRowInfo("Your profiles", "Work, Home, Night; tap one to apply it, the pencil to edit")
    val saveCurrent = SettingsRowInfo("Save current as…", "Snapshot today's settings into a new profile")
    val exactAlarms = SettingsRowInfo("Exact schedule alarms", "Let profiles switch on the minute (Alarms & reminders)", advanced = true)
    val perProfile = SettingsRowInfo("What a profile changes", "Look, home styles, dock, gestures, search, hidden apps", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.PROFILES,
        title = "Profiles",
        subtitle = "Work, Home, Night; switch by schedule or Do Not Disturb",
        icon = Icons.Outlined.Person,
        rows = listOf(profiles, active, list, saveCurrent, exactAlarms, perProfile),
    )
}

/**
 * Settings > Profiles: the feature switch, the active profile (opens the switcher sheet), the list
 * of saved profiles (tap applies, pencil edits), "Save current as…" and the exact-alarm status for
 * the schedule. Profiles come from `graph.profiles`; the Settings activity does not provide
 * `LocalAppGraph`, so the graph is read from the Application.
 */
@Composable
fun ProfilesPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val on = settings.feature(LauncherSettings.FEATURE_PROFILES)
    val context = LocalContext.current
    val graph = rememberProfileGraph()
    val profiles by graph.profiles.profiles.collectAsState(initial = emptyList())
    val active: Profile? = profiles.firstOrNull { it.id == settings.activeProfile }
    val filter = LocalSettingsRowFilter.current

    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }

    // Exact-alarm permission is granted in system settings; re-check (and re-arm the alarm exactly)
    // when the user comes back from there.
    val scheduler = remember(context) { ProfileScheduler(context) }
    var canExact by remember { mutableStateOf(scheduler.canScheduleExact()) }
    LifecycleResumeEffect(scheduler) {
        canExact = scheduler.canScheduleExact()
        FoldOsProfileRepository.from(context)?.let { repo -> repo.appScope.launch { repo.rescheduleAlarms() } }
        onPauseOrDispose { }
    }

    SettingsPageScaffold(title = ProfilesRows.page.title, onBack = onBack) {
        ToggleRow(
            info = ProfilesRows.profiles,
            checked = on,
            onCheckedChange = { enabled ->
                update { it.withFeature(LauncherSettings.FEATURE_PROFILES, enabled) }
                // Arms or cancels the boundary alarm for the new state.
                FoldOsProfileRepository.from(context)?.let { repo -> repo.appScope.launch { repo.rescheduleAlarms() } }
            },
        )
        StaticRow(
            info = ProfilesRows.active,
            onClick = { switcherOpen = true },
            visible = on,
            trailingText = active?.name ?: "None",
        )

        if (on && filter.shows(ProfilesRows.list)) {
            SectionHeader(text = "Your profiles")
            profiles.forEach { profile ->
                key(profile.id) {
                    ProfileRow(
                        profile = profile,
                        active = profile.id == settings.activeProfile,
                        onActivate = { graph.appScope.launch { graph.profiles.activate(profile.id) } },
                        onEdit = { editing = profile },
                    )
                }
            }
            if (profiles.isEmpty()) {
                Text(
                    text = "No profiles yet. Save the current settings as one to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        StaticRow(info = ProfilesRows.saveCurrent, onClick = { saving = true }, visible = on)

        if (on && (filter.shows(ProfilesRows.exactAlarms) || filter.shows(ProfilesRows.perProfile))) {
            SectionHeader(text = "Automatic switching")
        }
        StaticRow(
            info = ProfilesRows.exactAlarms,
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }
            },
            visible = on,
            trailingText = if (canExact) "Allowed" else "Not allowed",
        )
        StaticRow(info = ProfilesRows.perProfile, visible = on, showChevron = false)
    }

    if (switcherOpen) ProfileSwitcherSheet(onDismiss = { switcherOpen = false })
    if (saving) SaveCurrentProfileDialog(onDismiss = { saving = false })
    editing?.let { profile -> ProfileEditor(profile = profile, onDismiss = { editing = null }) }
}
