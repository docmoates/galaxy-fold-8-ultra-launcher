package com.foldos.launcher.picker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.core.repo.BuiltInWidgetRegistry
import com.foldos.launcher.core.repo.IconResolver
import com.foldos.launcher.core.repo.WidgetCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlin.math.ceil

/**
 * Process-wide Preferences DataStore holding the recently used widget ids. File-level so there is
 * exactly one instance per file name, as DataStore requires.
 */
private val Context.widgetRecentsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FoldOsWidgetCatalog.DATA_STORE_NAME,
)

/**
 * The FoldOS [WidgetCatalog]: every FoldOS built-in from [builtIns] plus every home-screen
 * AppWidget provider from [appWidgetManager], each app provider filed under a [WidgetCategory]
 * by [WidgetCategoryMap] (bundled asset -> keywords -> `ApplicationInfo.category` -> Utilities).
 *
 * [entries] re-emits when a package is installed, removed, replaced or changed. Recently used
 * ids (`markUsed`) persist in a Preferences DataStore so the "Recently used" group survives
 * restarts.
 *
 * Wire it in `AppGraphImpl`:
 * ```
 * override val widgets: WidgetCatalog by lazy {
 *     FoldOsWidgetCatalog(appContext, AppWidgetManager.getInstance(appContext), builtIns, icons, appScope)
 * }
 * ```
 *
 * @param icons used for the fallback preview of an app widget whose provider ships no preview
 *   image: the app's icon, drawn through the active icon pack.
 * @param scope scope that keeps the shared provider list alive between collectors; pass
 *   `AppGraph.appScope`.
 */
class FoldOsWidgetCatalog(
    context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val builtIns: BuiltInWidgetRegistry,
    private val icons: IconResolver,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : WidgetCatalog {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val categories = WidgetCategoryMap(appContext)
    private val dataStore: DataStore<Preferences> = appContext.widgetRecentsDataStore

    /** Emits once on collection and again on every package change, conflated. */
    private val providerChanges: Flow<Unit> = callbackFlow {
        trySend(Unit)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        // Package broadcasts are protected system broadcasts, which a NOT_EXPORTED receiver
        // still receives on API 34+; nothing else may trigger a refresh.
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        awaitClose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // already unregistered
            }
        }
    }.conflate()

    override val entries: Flow<List<WidgetEntry>> = providerChanges
        .map { loadAll() }
        .flowOn(Dispatchers.IO)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    /** Recently used ids, most recent first. */
    private val recentIds: Flow<List<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decodeRecents(prefs[RECENT_KEY]) }

    override fun grouped(by: String): Flow<List<Pair<String, List<WidgetEntry>>>> =
        combine(entries, recentIds) { all, used -> group(all, used, by) }

    override suspend fun markUsed(id: String) {
        try {
            dataStore.edit { prefs ->
                val current = decodeRecents(prefs[RECENT_KEY])
                val next = (listOf(id) + current.filter { it != id }).take(MAX_RECENT)
                prefs[RECENT_KEY] = next.joinToString(SEPARATOR)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist recent widget $id", e)
        }
    }

    /** Forgets every recently used id (Settings > Widgets > Reset recent widgets). */
    suspend fun clearRecents() {
        try {
            dataStore.edit { prefs -> prefs.remove(RECENT_KEY) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear recent widgets", e)
        }
    }

    // ---- grouping -------------------------------------------------------------------------

    private fun group(
        all: List<WidgetEntry>,
        used: List<String>,
        by: String,
    ): List<Pair<String, List<WidgetEntry>>> {
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

    // ---- loading --------------------------------------------------------------------------

    private fun loadAll(): List<WidgetEntry> {
        val builtIn = builtIns.all.map { widget ->
            WidgetEntry(
                id = widget.id,
                name = widget.name,
                source = BUILT_IN_SOURCE,
                category = widget.category,
                minSpanX = widget.minSpanX,
                minSpanY = widget.minSpanY,
                defaultSpanX = widget.defaultSize.first,
                defaultSpanY = widget.defaultSize.second,
                isBuiltIn = true,
            )
        }
        val providers: List<AppWidgetProviderInfo> = try {
            appWidgetManager.installedProviders
        } catch (e: Exception) {
            Log.w(TAG, "Could not list widget providers", e)
            emptyList()
        }
        val hosted = providers
            .filter { it.widgetCategory == 0 || (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
            .map { toEntry(it) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return (builtIn + hosted).distinctBy { it.id }
    }

    private fun toEntry(info: AppWidgetProviderInfo): WidgetEntry {
        val packageName = info.provider.packageName
        val source = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
        val label: String = try {
            info.loadLabel(packageManager)
        } catch (e: Exception) {
            null
        } ?: packageName

        val defaultX = if (info.targetCellWidth > 0) info.targetCellWidth else cellsFor(info.minWidth)
        val defaultY = if (info.targetCellHeight > 0) info.targetCellHeight else cellsFor(info.minHeight)
        val minX = if (info.minResizeWidth > 0) cellsFor(info.minResizeWidth).coerceAtMost(defaultX) else defaultX
        val minY = if (info.minResizeHeight > 0) cellsFor(info.minResizeHeight).coerceAtMost(defaultY) else defaultY

        return WidgetEntry(
            id = info.provider.flattenToString(),
            name = label,
            source = source,
            category = categories.categoryFor(packageName, label),
            minSpanX = minX,
            minSpanY = minY,
            defaultSpanX = defaultX,
            defaultSpanY = defaultY,
            isBuiltIn = false,
            provider = info.provider,
            previewLoader = { loadPreview(info, label) },
        )
    }

    /** Provider preview image, else the app icon through the icon pack, else the provider icon. */
    private fun loadPreview(info: AppWidgetProviderInfo, label: String): Drawable? {
        val preview = try {
            info.loadPreviewImage(appContext, 0)
        } catch (e: Exception) {
            null
        }
        if (preview != null) return preview
        val appInfo = AppInfo(
            packageName = info.provider.packageName,
            activityName = info.provider.className,
            label = label,
            user = Process.myUserHandle(),
            iconLoader = {
                try {
                    info.loadIcon(appContext, 0)
                } catch (e: Exception) {
                    null
                } ?: packageManager.getApplicationIcon(info.provider.packageName)
            },
        )
        return try {
            icons.icon(appInfo)
        } catch (e: Exception) {
            try {
                appInfo.iconLoader()
            } catch (inner: Exception) {
                null
            }
        }
    }

    private fun decodeRecents(raw: String?): List<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

    companion object {
        /** `WidgetEntry.source` for every FoldOS built-in (mock-up "FoldOS built-in"). */
        const val BUILT_IN_SOURCE: String = "FoldOS built-in"
        const val DATA_STORE_NAME: String = "foldos_widget_recents"

        private const val TAG = "FoldOsWidgetCatalog"
        private const val MAX_RECENT = 20
        private const val SEPARATOR = "\n"
        private val RECENT_KEY: Preferences.Key<String> = stringPreferencesKey("recent_ids")

        /** Cells needed for a dp size using the standard 70 * n - 30 AppWidget convention. */
        fun cellsFor(dp: Int): Int = ceil((dp + 30) / 70f).toInt().coerceAtLeast(1)
    }
}
