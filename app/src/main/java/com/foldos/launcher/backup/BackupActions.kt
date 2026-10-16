package com.foldos.launcher.backup

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.foldos.launcher.core.FoldOsApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry points for the backup UI, shared by Settings > Backup and Settings > Quick setup. Each
 * function opens the system file picker or a confirmation dialog; results are shown in a dialog
 * that [rememberBackupActions] composes itself.
 */
@Stable
class BackupActions internal constructor(
    private val busyState: State<Boolean>,
    /** Save a `foldos-backup.json` through the system "create document" picker. */
    val exportToFile: () -> Unit,
    /** Pick a backup file, confirm, then restore it. */
    val restoreFromFile: () -> Unit,
    /** Pick a Lawnchair / Nova settings file and read its grid size and dock count. */
    val importLauncherFile: () -> Unit,
    /** Confirm, then rebuild the home pages from the defaults. */
    val resetLayout: () -> Unit,
) {
    /** True while an export, restore, import or reset is running. */
    val busy: Boolean get() = busyState.value
}

private const val TAG = "BackupActions"
private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024

/**
 * Creates the [BackupActions] for the current screen. It also composes the dialogs it needs (result
 * message, restore confirmation, reset confirmation), so the caller only wires the rows to the
 * returned functions. Work runs on the app graph's `appScope`, so a restore that has started is not
 * cut short by leaving the screen.
 */
@Composable
fun rememberBackupActions(): BackupActions {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val graph = remember(context) { FoldOsApplication.from(context).graph }
    val busy = remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null && !busy.value) {
            busy.value = true
            graph.appScope.launch {
                val outcome = try {
                    val json = graph.backup.exportJson()
                    withContext(Dispatchers.IO) { BackupSupport.writeText(appContext, uri, json) }
                    "Backup saved. It holds your home layout, settings and icon choices; hosted app widgets are not included."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Export failed", e)
                    "Backup failed: ${e.message ?: e.javaClass.simpleName}"
                }
                busy.value = false
                message = outcome
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !busy.value) {
            busy.value = true
            graph.appScope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { BackupSupport.readBytes(appContext, uri, MAX_BACKUP_BYTES) }
                    pendingRestore = String(bytes, Charsets.UTF_8)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Reading the backup failed", e)
                    message = "Could not read the file: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    busy.value = false
                }
            }
        }
    }

    val launcherFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !busy.value) {
            busy.value = true
            graph.appScope.launch {
                val outcome = try {
                    val bytes = withContext(Dispatchers.IO) { BackupSupport.readBytes(appContext, uri, MAX_BACKUP_BYTES) }
                    val grid = LauncherImport.parse(uri.lastPathSegment, bytes)
                    if (grid == null) {
                        "No grid size or dock icon count found in that file. Pick a Lawnchair or Nova settings or backup file."
                    } else {
                        graph.settings.update { LauncherImport.apply(it, grid) }
                        val applied = if (grid.columns != null) {
                            " The cover grid now has ${grid.columns.coerceIn(3, 5)} columns."
                        } else {
                            ""
                        }
                        grid.describe() + "." + applied + " FoldOS sizes its dock automatically."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Launcher import failed", e)
                    "Could not read the file: ${e.message ?: e.javaClass.simpleName}"
                }
                busy.value = false
                message = outcome
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(text = "Backup") },
            text = { Text(text = text) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text(text = "OK") }
            },
        )
    }

    val restoreJson = pendingRestore
    if (restoreJson != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(text = "Restore this backup?") },
            text = {
                Text(
                    text = "Your home layout, settings and icon choices on this phone are replaced by the " +
                        "backup. Apps that are not installed here are skipped, and hosted app widgets are not restored.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        busy.value = true
                        graph.appScope.launch {
                            val outcome = try {
                                val result = graph.backup.importJson(restoreJson)
                                if (result.ok) result.message else "Restore failed: ${result.message}"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Restore failed", e)
                                "Restore failed: ${e.message ?: e.javaClass.simpleName}"
                            }
                            busy.value = false
                            message = outcome
                        }
                    },
                ) { Text(text = "Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(text = "Cancel") }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(text = "Reset the home layout?") },
            text = {
                Text(
                    text = "Pages, folders, stacks and widgets go back to the defaults. Settings and " +
                        "icon choices are kept. This cannot be undone; back up to a file first if unsure.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        busy.value = true
                        graph.appScope.launch {
                            val outcome = try {
                                LayoutReset.reset(appContext, graph)
                                "The home layout was reset to the defaults."
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Layout reset failed", e)
                                "Reset failed: ${e.message ?: e.javaClass.simpleName}"
                            }
                            busy.value = false
                            message = outcome
                        }
                    },
                ) { Text(text = "Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(text = "Cancel") }
            },
        )
    }

    return BackupActions(
        busyState = busy,
        exportToFile = { runCatching { exportLauncher.launch("foldos-backup.json") } },
        restoreFromFile = { runCatching { restoreLauncher.launch(arrayOf("*/*")) } },
        importLauncherFile = { runCatching { launcherFileLauncher.launch(arrayOf("*/*")) } },
        resetLayout = { confirmReset = true },
    )
}
