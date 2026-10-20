package com.foldos.widgetsplus

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlin.concurrent.thread

/**
 * The "Widgets + page" home-screen widget. Drawing reads the calendar and contacts providers, so every
 * update runs on a worker thread behind `goAsync()` rather than on the receiver's main thread.
 */
class WidgetsPlusProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        async { appWidgetIds.forEach { WidgetRenderer.updateWidget(context, appWidgetManager, it) } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        async { WidgetRenderer.updateWidget(context, appWidgetManager, appWidgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.forget(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetRenderer.ACTION_TAB -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val tab = PageTab.entries.getOrNull(intent.getIntExtra(WidgetRenderer.EXTRA_TAB, 0)) ?: PageTab.TODAY
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WidgetPrefs.setTab(context, id, tab)
                    pendingAsync { WidgetRenderer.updateWidget(context, AppWidgetManager.getInstance(context), id) }
                }
            }
            WidgetRenderer.ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> pendingAsync { WidgetRenderer.updateAll(context) }
            else -> super.onReceive(context, intent)
        }
    }

    /** Runs [block] on a worker thread, keeping the receiver alive (call from `onReceive` or a callback it makes). */
    private fun async(block: () -> Unit) = pendingAsync(block)

    private fun pendingAsync(block: () -> Unit) {
        val result = goAsync()
        thread(name = "widgetsplus-update") {
            try {
                block()
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        /** Asks every placed widget to redraw (the setup screen calls this after a permission changes). */
        fun requestRefresh(context: Context) {
            context.sendBroadcast(
                Intent(context, WidgetsPlusProvider::class.java).setAction(WidgetRenderer.ACTION_REFRESH),
            )
        }

        fun component(context: Context): ComponentName = ComponentName(context, WidgetsPlusProvider::class.java)
    }
}
