package com.foldos.launcher.core

import android.app.Application
import android.content.Context
import com.foldos.launcher.backup.RestoredWidgetCleanup
import com.foldos.launcher.data.AppGraphImpl
import com.foldos.launcher.perf.StartupTracker
import com.foldos.launcher.wallpaper.DailyWallpaper
import kotlinx.coroutines.launch

/** Application entry point. Holds the [AppGraph] for the process. */
class FoldOsApplication : Application() {

    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        // Timed so `adb logcat -s FoldOsStartup` and Perfetto show how long graph construction takes.
        graph = StartupTracker.section("graphInit") { AppGraphImpl(this) }
        // Daily wallpaper (Settings > Look): arms or cancels its alarm from the setting; off by default.
        DailyWallpaper.start(this, graph)
        // After an Auto Backup restore the stored appWidgetIds do not belong to this phone: drop those rows once.
        graph.appScope.launch { RestoredWidgetCleanup.runOnce(this@FoldOsApplication, graph) }
    }

    companion object {
        fun from(context: Context): FoldOsApplication =
            context.applicationContext as FoldOsApplication
    }
}
