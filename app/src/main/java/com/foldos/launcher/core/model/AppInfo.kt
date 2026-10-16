package com.foldos.launcher.core.model

import android.graphics.drawable.Drawable
import android.os.UserHandle

/**
 * One launchable activity as reported by LauncherApps. The icon is loaded lazily through
 * [iconLoader] so the apps list stays cheap to build and diff.
 *
 * Equality deliberately ignores [iconLoader]: the repository builds a fresh lambda on every
 * refresh, and comparing it would make every reload look like a change to `remember(apps)` and
 * `MutableStateFlow` de-duplication.
 */
class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val user: UserHandle,
    val iconLoader: () -> Drawable,
) {
    /** Stable key for this activity: "package/activity". */
    val key: String get() = "$packageName/$activityName"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false
        return packageName == other.packageName &&
            activityName == other.activityName &&
            label == other.label &&
            user == other.user
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + activityName.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + user.hashCode()
        return result
    }

    override fun toString(): String = "AppInfo(key=$key, label=$label, user=$user)"
}
