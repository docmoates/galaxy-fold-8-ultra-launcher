package com.foldos.launcher.core

import android.appwidget.AppWidgetHost
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.core.repo.BuiltInWidgetRegistry
import com.foldos.launcher.core.repo.IconResolver
import com.foldos.launcher.core.repo.LayoutRepository
import com.foldos.launcher.core.repo.SettingsRepository
import com.foldos.launcher.core.repo.WidgetCatalog
import kotlinx.coroutines.CoroutineScope

/**
 * Hand-rolled dependency graph. The Integrate agent builds the concrete instance in
 * [FoldOsApplication.onCreate]; UI reads it through [LocalAppGraph].
 */
interface AppGraph {
    val settings: SettingsRepository
    val layouts: LayoutRepository
    val apps: AppsRepository

    /** The widget gallery (app widget providers + built-ins). */
    val widgets: WidgetCatalog

    /** FoldOS built-in widgets, looked up by `HomeItem.builtinId`. */
    val builtIns: BuiltInWidgetRegistry

    /** Icon packs, per-app overrides and shape masking. */
    val icons: IconResolver

    /**
     * The process-wide host for third-party app widgets, host id [APP_WIDGET_HOST_ID]. Created and
     * started (`startListening()`) when the graph is built in `Application.onCreate`; activities
     * call `createView` / `allocateAppWidgetId` / `deleteAppWidgetId` on it.
     */
    val appWidgetHost: AppWidgetHost

    /**
     * Process-wide scope for writes that must finish even when the UI that started them leaves the
     * composition (e.g. a settings change right before the Settings activity finishes). Never
     * cancelled; uses a SupervisorJob so one failed write does not affect the others.
     */
    val appScope: CoroutineScope

    companion object {
        /** Host id passed to [AppWidgetHost]; stable across process restarts. */
        const val APP_WIDGET_HOST_ID: Int = 1024
    }
}

/** Provided at the root of each activity's composition; reading it without a provider throws. */
val LocalAppGraph: ProvidableCompositionLocal<AppGraph> = staticCompositionLocalOf {
    error("No AppGraph provided. Wrap the content in CompositionLocalProvider(LocalAppGraph provides graph).")
}
