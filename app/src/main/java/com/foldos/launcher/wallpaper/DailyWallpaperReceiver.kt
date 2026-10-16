package com.foldos.launcher.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.foldos.launcher.core.FoldOsApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires once a day ([ACTION_DAILY], from the alarm [DailyWallpaper] arms) and re-arms after a
 * reboot, an app update or a clock / time-zone change. When `LauncherSettings.dailyWallpaper` is on
 * it sets today's wallpaper if that has not happened yet and arms the next alarm; when it is off it
 * cancels the alarm. Declared in the manifest with `android:exported="false"`.
 */
class DailyWallpaperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val graph = FoldOsApplication.from(context).graph
        val result = goAsync()
        graph.appScope.launch {
            try {
                if (graph.settings.settings.first().dailyWallpaper) {
                    DailyWallpaper.applyIfDue(context)
                    DailyWallpaper.schedule(context)
                } else {
                    DailyWallpaper.cancel(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Daily wallpaper after ${intent.action} failed", e)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DailyWallpaperReceiver"

        /** Action of the daily alarm's PendingIntent. */
        const val ACTION_DAILY: String = "com.foldos.launcher.action.DAILY_WALLPAPER"

        /** Every action this receiver handles; the manifest filter lists the system ones. */
        val ACTIONS: Set<String> = setOf(
            ACTION_DAILY,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
