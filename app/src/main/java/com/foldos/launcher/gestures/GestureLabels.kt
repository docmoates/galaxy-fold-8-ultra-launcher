package com.foldos.launcher.gestures

import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.GestureAction
import com.foldos.launcher.core.model.GestureKind

/** User-facing names for gestures and actions (Settings > Gestures, edit-mode hints). */
object GestureLabels {

    /** Section order for the Settings page: touch, two fingers, hinge and keys. */
    val touchKinds: List<GestureKind> = listOf(
        GestureKind.SWIPE_UP, GestureKind.SWIPE_DOWN, GestureKind.DOUBLE_TAP,
        GestureKind.PINCH_IN, GestureKind.PINCH_OUT,
    )
    val twoFingerKinds: List<GestureKind> = listOf(
        GestureKind.SWIPE_UP_2F, GestureKind.SWIPE_DOWN_2F,
        GestureKind.SWIPE_LEFT_2F, GestureKind.SWIPE_RIGHT_2F,
    )
    val hingeKinds: List<GestureKind> = listOf(GestureKind.UNFOLD, GestureKind.FOLD, GestureKind.HOME_DOUBLE)

    /** Gestures only shown under the Advanced switch. */
    val advancedKinds: Set<GestureKind> = setOf(
        GestureKind.PINCH_OUT, GestureKind.SWIPE_UP_2F, GestureKind.SWIPE_DOWN_2F,
        GestureKind.SWIPE_LEFT_2F, GestureKind.SWIPE_RIGHT_2F, GestureKind.HOME_DOUBLE,
    )

    fun kindTitle(kind: GestureKind): String = when (kind) {
        GestureKind.SWIPE_UP -> "Swipe up"
        GestureKind.SWIPE_DOWN -> "Swipe down"
        GestureKind.SWIPE_LEFT_2F -> "Two-finger swipe left"
        GestureKind.SWIPE_RIGHT_2F -> "Two-finger swipe right"
        GestureKind.SWIPE_UP_2F -> "Two-finger swipe up"
        GestureKind.SWIPE_DOWN_2F -> "Two-finger swipe down"
        GestureKind.DOUBLE_TAP -> "Double tap"
        GestureKind.PINCH_IN -> "Pinch in"
        GestureKind.PINCH_OUT -> "Pinch out"
        GestureKind.HOME_DOUBLE -> "Home key twice"
        GestureKind.FOLD -> "Fold"
        GestureKind.UNFOLD -> "Unfold"
    }

    fun kindDescription(kind: GestureKind): String = when (kind) {
        GestureKind.SWIPE_UP -> "One finger up on a home page"
        GestureKind.SWIPE_DOWN -> "One finger down on a home page"
        GestureKind.SWIPE_LEFT_2F -> "Two fingers left"
        GestureKind.SWIPE_RIGHT_2F -> "Two fingers right"
        GestureKind.SWIPE_UP_2F -> "Two fingers up"
        GestureKind.SWIPE_DOWN_2F -> "Two fingers down"
        GestureKind.DOUBLE_TAP -> "Two quick taps on empty space"
        GestureKind.PINCH_IN -> "Two fingers closing"
        GestureKind.PINCH_OUT -> "Two fingers spreading"
        GestureKind.HOME_DOUBLE -> "Press the home key twice while on the home screen"
        GestureKind.FOLD -> "Closing the phone while the launcher is showing"
        GestureKind.UNFOLD -> "Opening the phone while the launcher is showing"
    }

    fun actionTitle(type: String): String = when (type) {
        GestureAction.TYPE_NONE -> "Nothing"
        GestureAction.TYPE_DRAWER -> "App drawer"
        GestureAction.TYPE_SEARCH -> "Search"
        GestureAction.TYPE_HUB -> "Hub page"
        GestureAction.TYPE_SETTINGS -> "Launcher settings"
        GestureAction.TYPE_NOTIFICATIONS -> "Notifications"
        GestureAction.TYPE_QUICK_SETTINGS -> "Quick settings"
        GestureAction.TYPE_APP -> "Open an app"
        GestureAction.TYPE_EDIT -> "Edit home screen"
        GestureAction.TYPE_LOCK_HINT -> "Lock screen (hint)"
        GestureAction.TYPE_RECENTS_HINT -> "Recent apps (hint)"
        else -> type
    }

    /** Every assignable type with its title, in [GestureAction.ALL_TYPES] order (dropdown options). */
    val actionOptions: List<Pair<String, String>> = GestureAction.ALL_TYPES.map { it to actionTitle(it) }

    /**
     * Label for a bound action: the app's name ("Open Spotify") for [GestureAction.TYPE_APP] when
     * the app is in [apps], the package name when it is not installed, otherwise [actionTitle].
     */
    fun actionLabel(action: GestureAction, apps: List<AppInfo>): String {
        if (action.type != GestureAction.TYPE_APP) return actionTitle(action.type)
        val target = action.target ?: return actionTitle(action.type)
        val app = apps.firstOrNull { it.key == target }
            ?: apps.firstOrNull { it.packageName == GestureActions.splitAppKey(target).first }
        return if (app != null) "Open ${app.label}" else "Open ${GestureActions.splitAppKey(target).first}"
    }
}
