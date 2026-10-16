package com.foldos.launcher.items

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem

/**
 * Launches an app pair in split screen (PLAN.md 4 "App pairs & multitasking"): app A starts
 * normally through [LauncherApps.startMainActivity]; [SECOND_APP_DELAY_MS] later app B starts with
 * `FLAG_ACTIVITY_LAUNCH_ADJACENT | FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK`, which
 * asks the system to open it in the other half of the screen next to A. The delay gives A's task
 * time to come to the front so the "adjacent" hint has something to be adjacent to.
 *
 * `LAUNCH_ADJACENT` is a hint: in a single-window situation (cover screen with multi-window off,
 * an app that forbids resizing) Android simply opens B normally, which is the expected fallback.
 * An app of another user profile (work profile) cannot be started with intent flags from here, so
 * B then falls back to a plain [LauncherApps.startMainActivity].
 */
object AppPairs {
    /** Delay before app B is launched adjacent to app A. */
    const val SECOND_APP_DELAY_MS: Long = 350L

    private const val TAG = "AppPairs"
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The flags app B is started with. */
    const val ADJACENT_FLAGS: Int =
        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK

    /** Starts [a], then [b] adjacent to it after [SECOND_APP_DELAY_MS]. Safe to call from any thread. */
    fun launch(context: Context, a: AppInfo, b: AppInfo) {
        val appContext = context.applicationContext
        val launcherApps = appContext.getSystemService(LauncherApps::class.java)
        if (!startMain(launcherApps, a)) return
        mainHandler.postDelayed({ launchAdjacent(appContext, launcherApps, b) }, SECOND_APP_DELAY_MS)
    }

    /**
     * Resolves both apps of [pair] from [apps] (keyed by `AppInfo.key`) and launches them. Returns
     * false, launching nothing, when the item is not a pair or either app is no longer installed.
     */
    fun launch(context: Context, pair: HomeItem, apps: Map<String, AppInfo>): Boolean {
        if (!pair.isPair) return false
        val a = apps.firstAppOf(pair) ?: return false
        val b = apps.secondAppOf(pair) ?: return false
        launch(context, a, b)
        return true
    }

    /** The intent that opens [app] in the adjacent split-screen slot. */
    fun adjacentIntent(app: AppInfo): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(ADJACENT_FLAGS)

    private fun launchAdjacent(context: Context, launcherApps: LauncherApps?, b: AppInfo) {
        if (b.user != Process.myUserHandle()) {
            // Cross-profile launches go through LauncherApps, which takes no intent flags.
            startMain(launcherApps, b)
            return
        }
        try {
            context.startActivity(adjacentIntent(b))
        } catch (e: Exception) {
            Log.w(TAG, "Adjacent launch of ${b.key} failed; starting it normally", e)
            startMain(launcherApps, b)
        }
    }

    private fun startMain(launcherApps: LauncherApps?, app: AppInfo): Boolean {
        if (launcherApps == null) {
            Log.w(TAG, "LauncherApps unavailable; cannot start ${app.key}")
            return false
        }
        return try {
            launcherApps.startMainActivity(ComponentName(app.packageName, app.activityName), app.user, null, null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start ${app.key}", e)
            false
        }
    }
}
