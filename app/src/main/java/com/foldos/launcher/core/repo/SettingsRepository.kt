package com.foldos.launcher.core.repo

import com.foldos.launcher.core.model.LauncherSettings
import kotlinx.coroutines.flow.Flow

/** Persisted launcher settings. Implemented by the Settings agent (DataStore + JSON). */
interface SettingsRepository {
    /** Emits the current settings and every change after it. */
    val settings: Flow<LauncherSettings>

    /** Atomically replaces the settings with `transform(current)`. */
    suspend fun update(transform: (LauncherSettings) -> LauncherSettings)
}
