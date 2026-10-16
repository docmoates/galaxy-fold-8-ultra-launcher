package com.foldos.launcher.data

import android.content.Context
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.core.repo.LayoutRepository
import com.foldos.launcher.core.repo.SettingsRepository
import com.foldos.launcher.data.db.LayoutDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-scoped [AppGraph]. Each repository is created on first use and then kept for the
 * life of the process. Construct exactly one instance, in `FoldOsApplication.onCreate`:
 *
 * ```
 * graph = AppGraphImpl(this)
 * ```
 */
class AppGraphImpl(context: Context) : AppGraph {

    private val appContext: Context = context.applicationContext

    /** The Room database behind [layouts]; exposed for callers that need direct DAO access. */
    val database: LayoutDatabase by lazy { LayoutDatabase.create(appContext) }

    override val settings: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }

    override val layouts: LayoutRepository by lazy { LayoutRepositoryImpl(database) }

    override val apps: AppsRepository by lazy { AppsRepositoryImpl(appContext) }

    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
