package com.foldos.launcher.data

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.core.repo.WidgetCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlin.math.ceil

/**
 * Placeholder [WidgetCatalog] until the Picker agent's catalog lands: lists only the installed
 * AppWidget providers (`AppWidgetManager.getInstalledProviders()`), all filed under
 * [WidgetCategory.UTILITIES], and keeps the recently-used list in memory for this process.
 */
class PlaceholderWidgetCatalog(context: Context) : WidgetCatalog {

    private val appContext: Context = context.applicationContext
    private val recent = MutableStateFlow<List<String>>(emptyList())

    override val entries: Flow<List<WidgetEntry>> = flow { emit(loadProviders()) }.flowOn(Dispatchers.IO)

    override fun grouped(by: String): Flow<List<Pair<String, List<WidgetEntry>>>> =
        combine(entries, recent) { all, used -> group(all, used, by) }

    override suspend fun markUsed(id: String) {
        recent.update { current -> (listOf(id) + current.filter { it != id }).take(MAX_RECENT) }
    }

    private fun group(all: List<WidgetEntry>, used: List<String>, by: String): List<Pair<String, List<WidgetEntry>>> {
        val byCategory: List<Pair<String, List<WidgetEntry>>> = WidgetCategory.entries.mapNotNull { category ->
            val inCategory = all.filter { it.category == category }
            if (inCategory.isEmpty()) null else category.displayName to inCategory
        }
        return when (by) {
            WidgetCatalog.GROUP_APP -> all.groupBy { it.source }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (source, list) -> source to list }
            WidgetCatalog.GROUP_RECENT -> {
                val byId = all.associateBy { it.id }
                val recentEntries = used.mapNotNull { byId[it] }
                if (recentEntries.isEmpty()) byCategory
                else listOf(WidgetCatalog.RECENT_GROUP_TITLE to recentEntries) + byCategory
            }
            else -> byCategory
        }
    }

    private fun loadProviders(): List<WidgetEntry> {
        val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext) ?: return emptyList()
        val pm = appContext.packageManager
        val providers: List<AppWidgetProviderInfo> = try {
            manager.installedProviders
        } catch (e: Exception) {
            Log.w(TAG, "Could not list widget providers", e)
            return emptyList()
        }
        return providers
            .map { info -> toEntry(info, pm) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun toEntry(info: AppWidgetProviderInfo, pm: PackageManager): WidgetEntry {
        val packageName = info.provider.packageName
        val source = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
        val defaultX = if (info.targetCellWidth > 0) info.targetCellWidth else cellsFor(info.minWidth)
        val defaultY = if (info.targetCellHeight > 0) info.targetCellHeight else cellsFor(info.minHeight)
        val minX = if (info.minResizeWidth > 0) cellsFor(info.minResizeWidth).coerceAtMost(defaultX) else defaultX
        val minY = if (info.minResizeHeight > 0) cellsFor(info.minResizeHeight).coerceAtMost(defaultY) else defaultY
        val context = appContext
        return WidgetEntry(
            id = info.provider.flattenToString(),
            name = info.loadLabel(pm) ?: packageName,
            source = source,
            category = WidgetCategory.UTILITIES,
            minSpanX = minX,
            minSpanY = minY,
            defaultSpanX = defaultX,
            defaultSpanY = defaultY,
            isBuiltIn = false,
            provider = info.provider,
            previewLoader = {
                try {
                    info.loadPreviewImage(context, 0)
                } catch (e: Exception) {
                    null
                }
            },
        )
    }

    companion object {
        private const val TAG = "WidgetCatalog"
        private const val MAX_RECENT = 20

        /** Cells needed for a dp size using the standard 70 * n - 30 AppWidget convention. */
        fun cellsFor(dp: Int): Int = ceil((dp + 30) / 70f).toInt().coerceAtLeast(1)
    }
}
