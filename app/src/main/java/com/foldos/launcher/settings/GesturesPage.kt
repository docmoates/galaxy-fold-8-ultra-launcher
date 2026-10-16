package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.LauncherSettings

object GesturesRows {
    val gestures = SettingsRowInfo("Home gestures", "Swipes, pinch, double-tap, hinge actions")
    val swipeUp = SettingsRowInfo("Swipe up", "Open the app drawer")
    val swipeDown = SettingsRowInfo("Swipe down", "Open notifications")
    val doubleTap = SettingsRowInfo("Double tap", "Lock the screen")
    val pinch = SettingsRowInfo("Pinch in", "Enter edit mode")
    val hinge = SettingsRowInfo("Half-fold", "Flex mode panel")
    val twoFinger = SettingsRowInfo("Two-finger swipe", "Switch profile", advanced = true)
    val haptics = SettingsRowInfo("Gesture haptics", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.GESTURES,
        title = "Gestures",
        subtitle = "Every gesture mapped to an action",
        icon = Icons.Outlined.Gesture,
        rows = listOf(gestures, swipeUp, swipeDown, doubleTap, pinch, hinge, twoFinger, haptics),
    )
}

@Composable
fun GesturesPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val on = settings.feature(LauncherSettings.FEATURE_GESTURES)
    SettingsPageScaffold(title = GesturesRows.page.title, onBack = onBack) {
        ToggleRow(
            info = GesturesRows.gestures,
            checked = on,
            onCheckedChange = { enabled -> update { it.withFeature(LauncherSettings.FEATURE_GESTURES, enabled) } },
        )
        if (on) SectionHeader(text = "Actions")
        StaticRow(info = GesturesRows.swipeUp, visible = on)
        StaticRow(info = GesturesRows.swipeDown, visible = on)
        StaticRow(info = GesturesRows.doubleTap, visible = on)
        StaticRow(info = GesturesRows.pinch, visible = on)
        StaticRow(info = GesturesRows.hinge, visible = on)
        StaticRow(info = GesturesRows.twoFinger, visible = on)
        StaticRow(info = GesturesRows.haptics, visible = on, trailingText = "On")
    }
}
