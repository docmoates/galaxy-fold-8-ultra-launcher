package com.foldos.launcher.core.repo

import com.foldos.launcher.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

/** Installed launchable apps. Implemented by the Apps agent on top of LauncherApps. */
interface AppsRepository {
    /** All launchable activities, sorted by label; re-emits on install, uninstall and update. */
    val apps: Flow<List<AppInfo>>

    /** Starts the app's main activity for its user. */
    fun launch(app: AppInfo)
}
