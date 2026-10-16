package com.foldos.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.core.repo.IconResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AppsRepository] on top of [LauncherApps] for the current user. The list is loaded on first
 * collection and refreshed whenever LauncherApps reports a package change; the callback is
 * registered exactly once for the lifetime of the process.
 *
 * @param iconResolver supplies the graph's [IconResolver] (icon packs, overrides, shape) lazily,
 *   so every `AppInfo.iconLoader` returns the themed icon; null (the default) keeps the raw system
 *   icon. The resolver receives an [AppInfo] whose own `iconLoader` is the raw icon, so a resolver
 *   that falls back to `app.iconLoader()` cannot recurse into itself.
 */
class AppsRepositoryImpl(
    context: Context,
    private val iconResolver: () -> IconResolver? = { null },
) : AppsRepository {

    private val appContext: Context = context.applicationContext
    private val launcherApps: LauncherApps = appContext.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val densityDpi: Int = appContext.resources.displayMetrics.densityDpi

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private val state = MutableStateFlow<List<AppInfo>>(emptyList())

    private val startLock = Any()
    @Volatile
    private var started = false

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            refresh()
        }

        override fun onPackageAdded(packageName: String?, user: UserHandle?) {
            refresh()
        }

        override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            refresh()
        }

        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            refresh()
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            refresh()
        }
    }

    override val apps: Flow<List<AppInfo>> = state.onStart { ensureStarted() }

    override fun launch(app: AppInfo) {
        val component = ComponentName(app.packageName, app.activityName)
        try {
            launcherApps.startMainActivity(component, app.user, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch ${app.key}", e)
        }
    }

    /** Forces a reload of the installed apps list on a background thread. */
    fun refresh() {
        scope.launch {
            refreshLock.withLock {
                state.value = loadApps()
            }
        }
    }

    private fun ensureStarted() {
        if (started) return
        synchronized(startLock) {
            if (started) return
            started = true
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            refresh()
        }
    }

    private fun loadApps(): List<AppInfo> {
        val activities: List<LauncherActivityInfo> = try {
            launcherApps.getActivityList(null, user)
        } catch (e: Exception) {
            Log.w(TAG, "Could not list activities", e)
            return state.value
        }
        return activities
            .filter { it.componentName.packageName != appContext.packageName }
            .map { info -> toAppInfo(info) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun toAppInfo(info: LauncherActivityInfo): AppInfo {
        val component = info.componentName
        val dpi = densityDpi
        val rawIcon: () -> Drawable = { info.getIcon(dpi) }
        val packageName = component.packageName
        val activityName = component.className
        val label = info.label?.toString() ?: component.packageName
        val user = info.user
        // The resolver sees this instance, whose iconLoader is the raw icon.
        val raw = AppInfo(packageName, activityName, label, user, rawIcon)
        return AppInfo(
            packageName = packageName,
            activityName = activityName,
            label = label,
            user = user,
            iconLoader = { resolvedIcon(raw, rawIcon) },
        )
    }

    private fun resolvedIcon(raw: AppInfo, rawIcon: () -> Drawable): Drawable {
        val resolver = iconResolver() ?: return rawIcon()
        return try {
            resolver.icon(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Icon resolver failed for ${raw.key}; using the system icon", e)
            rawIcon()
        }
    }

    private companion object {
        const val TAG = "AppsRepository"
    }
}
