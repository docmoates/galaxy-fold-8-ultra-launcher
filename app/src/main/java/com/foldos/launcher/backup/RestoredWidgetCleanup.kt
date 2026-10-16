package com.foldos.launcher.backup

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import com.foldos.launcher.core.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Auto Backup / device transfer restores the Room database together with `items.app_widget_id`, but
 * those ids were never allocated on the new phone: a widget the user adds later can be handed the
 * same id as a stale row, and the two rows would then render (and release) one widget.
 *
 * The first launch of an install writes a marker in `noBackupFilesDir` (never backed up or
 * transferred). When the marker is missing, the install is either brand new (no rows, nothing to
 * do) or just restored, so every hosted widget row whose id [AppWidgetManager] does not know is
 * removed once. Rows the system still knows are kept.
 */
internal object RestoredWidgetCleanup {

    private const val TAG = "RestoredWidgetCleanup"
    private const val MARKER = "foldos_widget_ids_checked"

    suspend fun runOnce(context: Context, graph: AppGraph) {
        val appContext = context.applicationContext
        val marker = File(appContext.noBackupFilesDir, MARKER)
        if (marker.exists()) return
        try {
            val manager = AppWidgetManager.getInstance(appContext)
            val rows = graph.layouts.allPagesWithItems().flatMap { it.second }
            for (item in rows) {
                val id = item.appWidgetId ?: continue
                val known = try {
                    manager.getAppWidgetInfo(id) != null
                } catch (e: Exception) {
                    false
                }
                if (!known) graph.layouts.deleteItem(item.id)
            }
            withContext(Dispatchers.IO) { marker.createNewFile() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Try again on the next launch.
            Log.w(TAG, "Cleaning restored widget rows failed", e)
        }
    }
}
