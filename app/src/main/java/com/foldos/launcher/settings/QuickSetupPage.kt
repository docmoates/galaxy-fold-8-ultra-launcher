package com.foldos.launcher.settings

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.foldos.launcher.backup.StyleRestoreGate
import com.foldos.launcher.backup.rememberBackupActions
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.onboarding.OnboardingActivity

object QuickSetupRows {
    val defaultHome = SettingsRowInfo("Set as default home", "Make FoldOS the launcher that opens when you press Home")
    val wizard = SettingsRowInfo("Run setup wizard", "Screens, home style, look, colors, dock; every step picked from a live preview")
    val gallery = SettingsRowInfo("Preset gallery", "Minimal, Dashboard, Productivity, Media, Power user, One UI Classic")
    val importLayout = SettingsRowInfo("Import from current launcher", "Reads Samsung or Pixel layout where permitted")
    val exportBackup = SettingsRowInfo("Export setup to file", "Save layout, settings, profiles and icon choices as foldos-backup.json")
    val importBackup = SettingsRowInfo("Import setup from file", "Restore a FoldOS backup file; replaces this phone's setup")
    val moreBackup = SettingsRowInfo("More backup options", "Google backup status, import from Nova or Lawnchair, reset layout")
    val reset = SettingsRowInfo("Reset all settings", "Back to the FoldOS defaults", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.QUICK_SETUP,
        title = "Quick setup",
        subtitle = "Re-run the wizard, preset gallery, export / import, reset",
        icon = Icons.Outlined.Bolt,
        rows = listOf(defaultHome, wizard, gallery, importLayout, exportBackup, importBackup, moreBackup, reset),
    )
}

@Composable
fun QuickSetupPage(
    update: SettingsUpdater,
    onBack: () -> Unit,
    onOpenBackup: (() -> Unit)? = null,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val backup = rememberBackupActions()
    val context = LocalContext.current
    val roleManager = remember(context) { context.getSystemService(RoleManager::class.java) }
    // Bumped after the role request returns so the row re-reads whether FoldOS holds the role.
    var roleVersion by remember { mutableIntStateOf(0) }
    val isDefaultHome = remember(roleManager, roleVersion) {
        roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleVersion++
    }
    val defaultHomeRow = remember(isDefaultHome) {
        QuickSetupRows.defaultHome.copy(
            subtitle = if (isDefaultHome) "FoldOS is your default home" else "Another launcher is the default; tap to switch to FoldOS",
        )
    }

    SettingsPageScaffold(title = QuickSetupRows.page.title, onBack = onBack) {
        StaticRow(
            info = defaultHomeRow,
            onClick = {
                val rm = roleManager
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    runCatching { roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                } else {
                    runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                }
            },
        )
        StaticRow(
            info = QuickSetupRows.wizard,
            onClick = { runCatching { context.startActivity(OnboardingActivity.wizardIntent(context)) } },
        )
        StaticRow(
            info = QuickSetupRows.gallery,
            onClick = { runCatching { context.startActivity(OnboardingActivity.galleryIntent(context)) } },
        )
        StaticRow(info = QuickSetupRows.importLayout)
        StaticRow(info = QuickSetupRows.exportBackup, onClick = backup.exportToFile)
        StaticRow(info = QuickSetupRows.importBackup, onClick = backup.restoreFromFile)
        StaticRow(info = QuickSetupRows.moreBackup, onClick = onOpenBackup)
        StaticRow(info = QuickSetupRows.reset, onClick = { confirmReset = true })
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(text = "Reset all settings?") },
            text = { Text(text = "Every FoldOS setting returns to its default. Home layouts are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        update { current ->
                            val defaults = LauncherSettings(wizardDone = current.wizardDone)
                            // Layouts are kept, so the style watcher must not restyle their pages.
                            StyleRestoreGate.arm(current, defaults)
                            defaults
                        }
                    },
                ) {
                    Text(text = "Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}
