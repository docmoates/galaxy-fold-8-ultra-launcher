package com.foldos.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import com.foldos.launcher.builtins.FoldOsBuiltInWidgets
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.core.repo.BuiltInWidgetRegistry
import com.foldos.launcher.core.repo.IconResolver
import com.foldos.launcher.core.repo.LayoutRepository
import com.foldos.launcher.core.repo.SettingsRepository
import com.foldos.launcher.core.repo.WidgetCatalog
import com.foldos.launcher.data.db.LayoutDatabase
import com.foldos.launcher.icons.FoldOsIconResolver
import com.foldos.launcher.picker.FoldOsWidgetCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-scoped [AppGraph]. Each repository is created on first use and then kept for the
 * life of the process; the [appWidgetHost] is created and started right away, because the graph
 * is constructed exactly once, in `FoldOsApplication.onCreate`:
 *
 * ```
 * graph = AppGraphImpl(this)
 * ```
 *
 * Phase 2+3 wiring: [builtIns] is the FoldOS widget library, [widgets] the catalog merging it with
 * the installed AppWidget providers, [icons] the icon-pack resolver, and [apps] routes every
 * `AppInfo.iconLoader` through that resolver.
 */
class AppGraphImpl(context: Context) : AppGraph {

    private val appContext: Context = context.applicationContext

    /** The Room database behind [layouts]; exposed for callers that need direct DAO access. */
    val database: LayoutDatabase by lazy { LayoutDatabase.create(appContext) }

    override val settings: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }

    override val layouts: LayoutRepository by lazy { LayoutRepositoryImpl(database) }

    /** Icons go through [icons] (packs, overrides, shape); the lambda keeps the two lazies acyclic. */
    override val apps: AppsRepository by lazy { AppsRepositoryImpl(appContext) { icons } }

    override val widgets: WidgetCatalog by lazy {
        FoldOsWidgetCatalog(appContext, AppWidgetManager.getInstance(appContext), builtIns, icons, appScope)
    }

    override val builtIns: BuiltInWidgetRegistry by lazy { FoldOsBuiltInWidgets(appContext) }

    override val icons: IconResolver by lazy { FoldOsIconResolver(appContext, settings) }

    /**
     * Started in the constructor, i.e. in `Application.onCreate`, so bound widgets receive updates
     * from the first frame. `MainActivity` additionally stops listening in `onStop` and starts
     * again in `onStart`, the way Launcher3 does, so a backgrounded launcher does not wake up for
     * widget updates.
     */
    override val appWidgetHost: AppWidgetHost = AppWidgetHost(appContext, AppGraph.APP_WIDGET_HOST_ID)

    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            Log.w(TAG, "AppWidgetHost.startListening failed", e)
        }
    }

    private companion object {
        const val TAG = "AppGraphImpl"
    }
}
