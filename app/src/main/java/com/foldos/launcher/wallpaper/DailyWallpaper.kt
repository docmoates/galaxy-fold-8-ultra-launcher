package com.foldos.launcher.wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.foldos.launcher.core.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

/**
 * Settings > Look > Daily wallpaper (`LauncherSettings.dailyWallpaper`, off by default), without
 * WorkManager: one inexact `AlarmManager` alarm shortly after local midnight fires
 * [DailyWallpaperReceiver], which draws the day's gradient ([DailyWallpaperGenerator]) and sets it
 * with `WallpaperManager.setBitmap` (home screen; permission `SET_WALLPAPER`), then arms the next
 * alarm. [start] (called from `FoldOsApplication.onCreate`) watches the setting: switching it on
 * applies today's wallpaper once and arms the alarm, switching it off cancels the alarm. The
 * receiver for boot, app update and clock changes re-arms it, and applies a missed day.
 *
 * "Applied" is remembered per local date in a private preference file, so the wallpaper is
 * redrawn at most once a day however often the process starts.
 */
object DailyWallpaper {

    private const val TAG = "DailyWallpaper"
    private const val PREFS = "foldos_daily_wallpaper"
    private const val KEY_LAST_DAY = "last_day"
    private const val KEY_ARMED = "armed"
    private const val REQUEST_CODE = 6101

    /** Minutes past local midnight at which the daily alarm is aimed. */
    private const val ALARM_MINUTE_OF_DAY = 5

    /** Starts watching `LauncherSettings.dailyWallpaper` on the app scope. Call once per process. */
    fun start(context: Context, graph: AppGraph) {
        val appContext = context.applicationContext
        graph.appScope.launch {
            graph.settings.settings.map { it.dailyWallpaper }.distinctUntilChanged().collect { enabled ->
                try {
                    if (enabled) {
                        applyIfDue(appContext)
                        schedule(appContext)
                    } else {
                        cancel(appContext)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Daily wallpaper sync failed", e)
                }
            }
        }
    }

    /**
     * Draws and sets today's wallpaper unless it was already set today (or [force]). Returns true
     * when a wallpaper was set. Never throws: a device that does not allow wallpaper changes just
     * returns false.
     */
    suspend fun applyIfDue(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val today = LocalDate.now().toEpochDay()
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!force && prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE) == today) return@withContext false
        val manager = WallpaperManager.getInstance(appContext)
        try {
            if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return@withContext false
            val metrics = appContext.resources.displayMetrics
            val width = manager.desiredMinimumWidth.takeIf { it > 0 } ?: metrics.widthPixels
            val height = manager.desiredMinimumHeight.takeIf { it > 0 } ?: metrics.heightPixels
            val bitmap = DailyWallpaperGenerator.generate(today, width, height)
            try {
                manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            } finally {
                bitmap.recycle()
            }
            prefs.edit().putLong(KEY_LAST_DAY, today).apply()
            true
        } catch (e: Exception) {
            // IOException from setBitmap, SecurityException when the permission is missing.
            Log.w(TAG, "Could not set the daily wallpaper", e)
            false
        }
    }

    /** Arms the alarm for the next local midnight plus [ALARM_MINUTE_OF_DAY] minutes. */
    fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis(now), pendingIntent(appContext))
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ARMED, true).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Scheduling the daily wallpaper failed", e)
        }
    }

    /** Cancels the daily alarm (a no-op when none was armed by this process or an earlier one). */
    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ARMED, false)) return
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(pendingIntent(appContext)) }
        prefs.edit().putBoolean(KEY_ARMED, false).apply()
    }

    /** Local midnight after [now] plus [ALARM_MINUTE_OF_DAY] minutes, in epoch millis. */
    fun nextTriggerMillis(now: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, ALARM_MINUTE_OF_DAY)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyWallpaperReceiver::class.java).setAction(DailyWallpaperReceiver.ACTION_DAILY)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
