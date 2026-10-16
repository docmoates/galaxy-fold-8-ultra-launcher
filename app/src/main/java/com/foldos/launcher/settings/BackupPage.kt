package com.foldos.launcher.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foldos.launcher.backup.BackupActions
import com.foldos.launcher.backup.rememberBackupActions
import com.foldos.launcher.core.model.LauncherSettings

/**
 * Settings > Backup (Backup agent). Route [ROUTE]; Integrate registers it in `SettingsRoot`
 * (`composable(BackupRows.ROUTE) { BackupPage(...) }`, plus the `known` start-route check) and in
 * `SettingsPages.all`. The route is not in `SettingsRoutes`, which the Settings owner keeps.
 */
object BackupRows {
    /** Navigation route of this page. */
    const val ROUTE: String = "backup"

    val export = SettingsRowInfo("Back up to file", "Save layout, settings, profiles and icon choices as foldos-backup.json")
    val restore = SettingsRowInfo("Restore from file", "Replace this phone's setup with a FoldOS backup file")
    val google = SettingsRowInfo("Google device backup", "Android Auto Backup keeps FoldOS data in your Google account")
    val googleNow = SettingsRowInfo("Request a backup now", "Asks Android to run an Auto Backup pass soon")
    val googleSettings = SettingsRowInfo("Open Android backup settings", "Turn Google backup on or check the last backup")
    val otherLaunchers = SettingsRowInfo("Import from another launcher", "Nova, Lawnchair and Niagara")
    val nova = SettingsRowInfo("Nova Launcher", "Settings or .novabackup file: grid size and dock icons")
    val lawnchair = SettingsRowInfo("Lawnchair", "Settings or .lawnchairbackup file: grid size and dock icons")
    val niagara = SettingsRowInfo("Niagara Launcher", "Has no backup file to read; set up the look in FoldOS directly")
    val settingsFile = SettingsRowInfo("Read a launcher settings file", "Pick a Lawnchair or Nova file; only the grid size and dock icon count are used")
    val resetLayout = SettingsRowInfo("Reset layout", "Rebuild the home pages from the defaults; settings are kept", advanced = true)

    val page = SettingsPageInfo(
        route = ROUTE,
        title = "Backup",
        subtitle = "Back up and restore, Google backup, import from another launcher",
        icon = Icons.Outlined.Backup,
        rows = listOf(export, restore, google, googleNow, googleSettings, nova, lawnchair, niagara, settingsFile, resetLayout),
    )
}

/**
 * Settings > Backup: save / restore `foldos-backup.json` through the system file pickers, the
 * Google Auto Backup status, stubs for importing from Nova / Lawnchair / Niagara with a best-effort
 * grid reader, and (Advanced) Reset layout. Uses `FoldOsApplication.graph.backup` through
 * [rememberBackupActions].
 */
@Composable
fun BackupPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val actions: BackupActions = rememberBackupActions()
    val filter = LocalSettingsRowFilter.current
    val autoBackupStatus = remember(context) { autoBackupStatus(context) }
    val googleRow = remember(autoBackupStatus) { BackupRows.google.copy(subtitle = autoBackupStatus) }

    SettingsPageScaffold(title = BackupRows.page.title, onBack = onBack) {
        if (actions.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        SectionHeader(text = "Back up to a file")
        StaticRow(info = BackupRows.export, onClick = actions.exportToFile)
        StaticRow(info = BackupRows.restore, onClick = actions.restoreFromFile)
        NoteText(
            text = "The file holds your home pages, folders, stacks, app pairs, built-in widgets, all settings, " +
                "profiles and per-app icon choices. Hosted app widgets, search history and Gmail sign-in are not included.",
        )

        SectionHeader(text = "Google device backup")
        StaticRow(info = googleRow, showChevron = false)
        StaticRow(
            info = BackupRows.googleNow,
            onClick = {
                runCatching { android.app.backup.BackupManager(context.applicationContext).dataChanged() }
                Toast.makeText(context, "Backup requested", Toast.LENGTH_SHORT).show()
            },
        )
        StaticRow(
            info = BackupRows.googleSettings,
            onClick = {
                // Google backup is not on the Privacy screen on most phones: try the backup screen
                // first, then the main Settings list, then Privacy.
                val screens = listOf("android.settings.BACKUP_AND_RESET_SETTINGS", Settings.ACTION_SETTINGS, Settings.ACTION_PRIVACY_SETTINGS)
                for (action in screens) {
                    val opened = runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    if (opened.isSuccess) break
                }
            },
        )

        SectionHeader(text = BackupRows.otherLaunchers.title)
        StaticRow(info = BackupRows.nova, showChevron = false)
        StaticRow(info = BackupRows.lawnchair, showChevron = false)
        StaticRow(info = BackupRows.niagara, showChevron = false)
        StaticRow(info = BackupRows.settingsFile, onClick = actions.importLauncherFile)
        NoteText(
            text = "Full file import will parse these launchers' backup formats when they are available. For now " +
                "FoldOS reads only the grid size and dock icon count; the cover grid takes the column count.",
        )

        if (filter.advanced) SectionHeader(text = "Advanced")
        StaticRow(info = BackupRows.resetLayout, onClick = actions.resetLayout)
    }
}

@Composable
private fun NoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** One sentence on whether Android's Auto Backup can currently save FoldOS data. */
private fun autoBackupStatus(context: Context): String {
    val allowed = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
    if (!allowed) return "Auto Backup is switched off for FoldOS."
    // "backup_enabled" is the system's Google backup switch; not every ROM exposes it.
    val systemSwitch = runCatching { Settings.Secure.getInt(context.contentResolver, "backup_enabled") }.getOrNull()
    return when (systemSwitch) {
        1 -> "On. Android saves FoldOS layouts and settings to your Google account about once a day while charging on Wi-Fi, and restores them on a new phone."
        0 -> "Google backup is off on this phone, so FoldOS data is not saved. Turn it on in Android settings."
        else -> "Allowed for FoldOS. Android decides when to save; check Settings > Google > Backup that it is on."
    }
}
