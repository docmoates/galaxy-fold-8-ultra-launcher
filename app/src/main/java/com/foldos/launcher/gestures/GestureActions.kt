package com.foldos.launcher.gestures

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.foldos.launcher.core.model.GestureAction

/**
 * What the launcher UI lets a gesture do. Implemented by whoever owns the home state (the
 * `LauncherViewModel` / `LauncherRoot` wiring): every method is a plain state change or an intent.
 */
interface GestureHost {
    /** Opens the app drawer. */
    fun openDrawer()

    /** Opens universal search (the drawer with the keyboard up until `search/` lands). */
    fun openSearch()

    /** Scrolls the home pager to the Hub page. */
    fun openHub()

    /** Starts the Settings activity at its root. */
    fun openSettings()

    /** Enters live-edit mode. */
    fun enterEditMode()

    /**
     * Launches an app. [activityName] is null when the stored target only names a package; the host
     * then starts the package's first launchable activity.
     */
    fun launch(packageName: String, activityName: String?)
}

/**
 * Turns a [GestureAction] into its effect. UI-side actions go through [GestureHost]; the system
 * shades go through `StatusBarManager` (hidden API, reflection, `EXPAND_STATUS_BAR` permission) with
 * a toast when the call is refused; the lock / recents hints are toasts, because a third-party
 * launcher can neither lock the screen nor restyle One UI's recents.
 */
object GestureActions {

    private const val TAG = "GestureActions"

    /** Message shown for [GestureAction.TYPE_LOCK_HINT]. */
    const val LOCK_HINT_MESSAGE: String =
        "FoldOS can't lock the screen: Android only lets accessibility services do that. Press the side key instead."

    /** Message shown for [GestureAction.TYPE_RECENTS_HINT]. */
    const val RECENTS_HINT_MESSAGE: String =
        "Recent apps stay with One UI: swipe up from the bottom edge and hold."

    /**
     * Performs [action]. Unknown or [GestureAction.TYPE_NONE] actions do nothing. Never throws:
     * failures are logged and, where the user would otherwise see nothing happen, toasted.
     */
    fun perform(context: Context, action: GestureAction, host: GestureHost) {
        when (action.type) {
            GestureAction.TYPE_NONE -> Unit
            GestureAction.TYPE_DRAWER -> host.openDrawer()
            GestureAction.TYPE_SEARCH -> host.openSearch()
            GestureAction.TYPE_HUB -> host.openHub()
            GestureAction.TYPE_SETTINGS -> host.openSettings()
            GestureAction.TYPE_EDIT -> host.enterEditMode()
            GestureAction.TYPE_NOTIFICATIONS -> {
                if (!expandNotifications(context)) toast(context, "Couldn't open the notification shade")
            }
            GestureAction.TYPE_QUICK_SETTINGS -> {
                if (!expandQuickSettings(context)) toast(context, "Couldn't open quick settings")
            }
            GestureAction.TYPE_APP -> {
                val target = action.target
                if (target.isNullOrBlank()) {
                    toast(context, "No app chosen for this gesture")
                } else {
                    val (packageName, activityName) = splitAppKey(target)
                    runCatching { host.launch(packageName, activityName) }
                        .onFailure { Log.w(TAG, "Could not launch $target", it) }
                }
            }
            GestureAction.TYPE_LOCK_HINT -> toast(context, LOCK_HINT_MESSAGE)
            GestureAction.TYPE_RECENTS_HINT -> toast(context, RECENTS_HINT_MESSAGE)
            else -> Log.w(TAG, "Unknown gesture action type ${action.type}")
        }
    }

    /**
     * Splits an `AppInfo.key` ("package/activity") into package and activity; a bare package name
     * yields a null activity.
     */
    fun splitAppKey(key: String): Pair<String, String?> {
        val slash = key.indexOf('/')
        if (slash <= 0) return key to null
        val activity = key.substring(slash + 1).takeIf { it.isNotBlank() }
        return key.substring(0, slash) to activity
    }

    /**
     * Pulls down the notification shade through `StatusBarManager.expandNotificationsPanel()`.
     * Needs `android.permission.EXPAND_STATUS_BAR` in the manifest. Returns false when the call is
     * unavailable or refused.
     */
    fun expandNotifications(context: Context): Boolean = invokeStatusBar(context, "expandNotificationsPanel")

    /**
     * Pulls down the quick settings through `StatusBarManager.expandSettingsPanel()`. Needs
     * `android.permission.EXPAND_STATUS_BAR`. Returns false when the call is unavailable or refused.
     */
    fun expandQuickSettings(context: Context): Boolean = invokeStatusBar(context, "expandSettingsPanel")

    private fun invokeStatusBar(context: Context, methodName: String): Boolean = runCatching {
        // Context.STATUS_BAR_SERVICE is hidden; "statusbar" is its value on every API level.
        val service = context.getSystemService("statusbar") ?: return false
        val method = service.javaClass.getMethod(methodName)
        method.invoke(service)
        true
    }.onFailure { Log.w(TAG, "StatusBarManager.$methodName failed", it) }.getOrDefault(false)

    private fun toast(context: Context, message: String) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
