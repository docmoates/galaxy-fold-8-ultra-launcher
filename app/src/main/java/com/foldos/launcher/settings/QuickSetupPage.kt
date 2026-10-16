package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.foldos.launcher.core.model.LauncherSettings

object QuickSetupRows {
    val wizard = SettingsRowInfo("Run setup wizard", "Screens, home style, look, colors, dock; every step picked from a live preview")
    val gallery = SettingsRowInfo("Preset gallery", "Minimal, Dashboard, Productivity, Media, Power user, One UI Classic")
    val importLayout = SettingsRowInfo("Import from current launcher", "Reads Samsung or Pixel layout where permitted")
    val reset = SettingsRowInfo("Reset all settings", "Back to the FoldOS defaults", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.QUICK_SETUP,
        title = "Quick setup",
        subtitle = "Re-run the wizard, preset gallery, reset",
        icon = Icons.Outlined.Bolt,
        rows = listOf(wizard, gallery, importLayout, reset),
    )
}

@Composable
fun QuickSetupPage(
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    SettingsPageScaffold(title = QuickSetupRows.page.title, onBack = onBack) {
        StaticRow(info = QuickSetupRows.wizard)
        StaticRow(info = QuickSetupRows.gallery)
        StaticRow(info = QuickSetupRows.importLayout)
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
                        update { LauncherSettings() }
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
