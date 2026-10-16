package com.foldos.launcher.core.repo

import com.foldos.launcher.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

/** Installed launchable apps. Implemented by the Apps agent on top of LauncherApps. */
interface AppsRepository {
    /**
     * Launchable activities minus `LauncherSettings.hiddenApps`, sorted by label; re-emits on
     * install, uninstall, update and when the hidden set changes. What the drawer, search and
     * home grid show.
     */
    val apps: Flow<List<AppInfo>>

    /** Every launchable activity including hidden ones (Settings > App drawer > Hidden apps). */
    val allApps: Flow<List<AppInfo>>

    /** Starts the app's main activity for its user. */
    fun launch(app: AppInfo)
}
