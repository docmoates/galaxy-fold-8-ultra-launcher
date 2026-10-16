package com.foldos.launcher.core

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.core.repo.LayoutRepository
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Hand-rolled dependency graph. The Integrate agent builds the concrete instance in
 * [FoldOsApplication.onCreate]; UI reads it through [LocalAppGraph].
 */
interface AppGraph {
    val settings: SettingsRepository
    val layouts: LayoutRepository
    val apps: AppsRepository

    /**
     * Process-wide scope for writes that must finish even when the UI that started them leaves the
     * composition (e.g. a settings change right before the Settings activity finishes). Never
     * cancelled; uses a SupervisorJob so one failed write does not affect the others.
     */
    val appScope: CoroutineScope
}

/** Provided at the root of each activity's composition; reading it without a provider throws. */
val LocalAppGraph: ProvidableCompositionLocal<AppGraph> = staticCompositionLocalOf {
    error("No AppGraph provided. Wrap the content in CompositionLocalProvider(LocalAppGraph provides graph).")
}
