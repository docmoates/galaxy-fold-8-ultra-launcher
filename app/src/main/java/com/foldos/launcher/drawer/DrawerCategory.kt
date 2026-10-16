package com.foldos.launcher.drawer

import com.foldos.launcher.core.model.AppInfo

/**
 * Drawer filter chips (mock-up `dtabs`). Only [ALL] filters today; the other chips are stubs
 * that keep their selected look but return the full list, until smart categories exist.
 */
enum class DrawerCategory(val title: String, val locked: Boolean = false) {
    ALL("All"),
    WORK("Work"),
    MEDIA("Media"),
    SOCIAL("Social"),
    TOOLS("Tools"),
    HIDDEN("Hidden", locked = true);

    /** Stub: every category currently shows every app. */
    fun filter(apps: List<AppInfo>): List<AppInfo> = apps
}
