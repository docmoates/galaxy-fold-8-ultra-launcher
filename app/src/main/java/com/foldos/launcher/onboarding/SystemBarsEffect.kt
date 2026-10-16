package com.foldos.launcher.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * System bar styling shared by MainActivity, OnboardingActivity and SettingsActivity (Fix round 1.2).
 *
 * `enableEdgeToEdge()` with its defaults picks light or dark status bar icons from the SYSTEM dark
 * mode, but the launcher's Appearance setting (Dark / Light / Auto) is independent of it: a dark
 * launcher on a light-mode phone got dark clock and icons on a dark wallpaper. The activities now
 * call [enableLauncherEdgeToEdge] once in onCreate and [SystemBarsEffect] with the RESOLVED launcher
 * darkness (`theme.isDarkAppearance(settings.appearance)`) from their content.
 */

/**
 * Edge to edge with transparent bars and a dark-theme icon style (light icons) as the starting
 * point, which suits the default Dark appearance and avoids a flash of dark icons on the first frame.
 * Both bars are transparent without a contrast scrim (the dark style never enforces one), so the
 * wallpaper shows behind the status bar and the gesture / 3-button navigation bar.
 * [SystemBarsEffect] then follows the real appearance.
 */
fun ComponentActivity.enableLauncherEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
}

/**
 * Keeps the status bar and navigation bar icons readable on the launcher's own surface: light icons
 * when [dark] is true (dark launcher), dark icons when it is false (light launcher). Re-applied
 * whenever [dark] changes, so switching Appearance (or the system mode while on Auto) updates the
 * bars at once. Call it once per window from the activity's content, outside any dialog.
 *
 * @param dark the resolved launcher darkness, e.g. `isDarkAppearance(settings.appearance)`.
 */
@Composable
fun SystemBarsEffect(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findHostActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}

private fun Context.findHostActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
