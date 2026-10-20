package com.foldos.widgetsplus

import android.content.Context

/** The tabs of the widget page, in tab-bar order. */
enum class PageTab { TODAY, CALENDAR, CONTACTS, MAIL, AI }

/** Which tab each widget instance shows, kept in SharedPreferences. */
object WidgetPrefs {
    private const val NAME = "widgetsplus"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun tab(context: Context, widgetId: Int): PageTab =
        PageTab.entries.getOrElse(prefs(context).getInt("tab_$widgetId", 0)) { PageTab.TODAY }

    fun setTab(context: Context, widgetId: Int, tab: PageTab) {
        prefs(context).edit().putInt("tab_$widgetId", tab.ordinal).apply()
    }

    fun forget(context: Context, widgetIds: IntArray) {
        val edit = prefs(context).edit()
        widgetIds.forEach { edit.remove("tab_$it") }
        edit.apply()
    }
}
