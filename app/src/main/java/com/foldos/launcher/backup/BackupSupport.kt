package com.foldos.launcher.backup

import android.app.backup.BackupManager
import android.appwidget.AppWidgetHost
import android.content.Context
import android.net.Uri
import android.util.Log
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Small Android helpers shared by the backup repository, the UI actions and the layout reset. */
internal object BackupSupport {

    private const val TAG = "FoldOsBackup"

    /** Reads at most [maxBytes] from [uri]; throws [IOException] when it cannot be opened or is larger. */
    fun readBytes(context: Context, uri: Uri, maxBytes: Int): ByteArray {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the file")
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("The file is larger than ${maxBytes / (1024 * 1024)} MB")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    /** Writes [text] as UTF-8 to [uri], truncating whatever was there. */
    fun writeText(context: Context, uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the file")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Gives hosted app widget ids back to the system after the layout rows that held them are gone.
     * `deleteAppWidgetId` is per package, so a throw-away host with the app's host id is enough.
     */
    fun releaseAppWidgetIds(context: Context, ids: Collection<Int>) {
        if (ids.isEmpty()) return
        val host = AppWidgetHost(context.applicationContext, AppGraph.APP_WIDGET_HOST_ID)
        for (id in ids) {
            try {
                host.deleteAppWidgetId(id)
            } catch (e: Exception) {
                Log.w(TAG, "deleteAppWidgetId($id) failed", e)
            }
        }
    }

    /** Asks Android to schedule an Auto Backup pass; the system decides when it really runs. */
    fun requestAutoBackup(context: Context) {
        try {
            BackupManager(context.applicationContext).dataChanged()
        } catch (e: Exception) {
            Log.w(TAG, "BackupManager.dataChanged failed", e)
        }
    }
}

/**
 * Resets the home layout to the defaults (Settings > Backup > Reset layout, Advanced). Settings
 * and icon choices are kept.
 */
object LayoutReset {

    /**
     * Deletes every page and item, gives the hosted app widget ids back, then seeds the default
     * layout for each screen with the current column count and style, so the home is not left empty
     * until the next screen change.
     */
    suspend fun reset(context: Context, graph: AppGraph) {
        val widgetIds = graph.layouts.allPagesWithItems().flatMap { it.second }.mapNotNull { it.appWidgetId }
        graph.layouts.replaceAll(emptyList())
        BackupSupport.releaseAppWidgetIds(context, widgetIds)
        val current = graph.settings.settings.first()
        val apps = graph.apps.apps.first()
        // FLEX is never displayed (effectiveScreen only returns COVER or INNER), so it gets no pages.
        for (screen in listOf(ScreenKind.COVER, ScreenKind.INNER)) {
            graph.layouts.ensureDefaultLayout(screen, apps, current.colsFor(screen), current.styleFor(screen))
        }
        BackupSupport.requestAutoBackup(context)
    }
}

/**
 * Lets a whole-settings write (backup restore, reset all settings) change `styleCover` /
 * `styleInner` without LauncherViewModel's style watcher rewriting every page of that screen:
 * the page styles were just restored (or deliberately kept), so the global restyle must not run.
 *
 * [arm] records the style pair the write will produce; the watcher calls [consume] with each new
 * pair and skips the restyle when it matches. An armed value expires after [WINDOW_MS] so a stale
 * one cannot swallow a later manual change.
 */
internal object StyleRestoreGate {
    private const val WINDOW_MS = 15_000L

    @Volatile private var expected: Pair<HomeStyle, HomeStyle>? = null
    @Volatile private var armedAt = 0L

    /** Arms the gate when [after] changes a style compared with [before]; otherwise nothing is emitted. */
    fun arm(before: LauncherSettings, after: LauncherSettings) {
        val next = after.styleCover to after.styleInner
        if (next == (before.styleCover to before.styleInner)) return
        armedAt = System.currentTimeMillis()
        expected = next
    }

    /** True (once) when [next] is the pair a restore armed, i.e. the watcher must not restyle pages. */
    fun consume(next: Pair<HomeStyle, HomeStyle>): Boolean {
        val wanted = expected ?: return false
        if (System.currentTimeMillis() - armedAt > WINDOW_MS) {
            expected = null
            return false
        }
        if (wanted != next) return false
        expected = null
        return true
    }
}
