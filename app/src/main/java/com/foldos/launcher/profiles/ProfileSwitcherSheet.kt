package com.foldos.launcher.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.Profile
import kotlinx.coroutines.launch

/**
 * Bottom sheet listing every saved profile with a tick on the active one. Tapping a row applies
 * that profile (`ProfileRepository.activate`) and closes the sheet; the pencil opens
 * [ProfileEditor]; "Keep current settings" clears the active profile; "Save current as…" captures a
 * new one. Works from the launcher (e.g. a gesture or dock action) and from Settings > Profiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherSheet(onDismiss: () -> Unit) {
    val graph = rememberProfileGraph()
    val settings by graph.settings.settings.collectAsState(initial = null)
    val profiles by graph.profiles.profiles.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Profile?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    val activeId = settings?.activeProfile
    val featureOn = settings?.feature(LauncherSettings.FEATURE_PROFILES) ?: true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(text = "Profiles", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (featureOn) {
                    "Each profile restores its own look, home styles, dock, gestures and hidden apps."
                } else {
                    "Profiles are switched off in Settings › Profiles; you can still apply one by hand."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        profiles.forEach { profile ->
            key(profile.id) {
                ProfileRow(
                    profile = profile,
                    active = profile.id == activeId,
                    onActivate = {
                        graph.appScope.launch { graph.profiles.activate(profile.id) }
                        onDismiss()
                    },
                    onEdit = { editing = profile },
                    containerColor = Color.Transparent,
                )
            }
        }
        if (profiles.isEmpty()) {
            Text(
                text = "No profiles yet. Save the current settings as one to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    graph.appScope.launch { graph.profiles.activate(null) }
                    onDismiss()
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(imageVector = Icons.Outlined.Block, contentDescription = null) },
            headlineContent = { Text(text = "Keep current settings") },
            supportingContent = { Text(text = "No profile applied; changes are not saved into one") },
            trailingContent = if (activeId == null) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        )
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { saving = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
            headlineContent = { Text(text = "Save current as…") },
            supportingContent = { Text(text = "Snapshot today's settings into a new profile") },
        )

        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    editing?.let { profile ->
        ProfileEditor(profile = profile, onDismiss = { editing = null })
    }
    if (saving) SaveCurrentProfileDialog(onDismiss = { saving = false })
}
