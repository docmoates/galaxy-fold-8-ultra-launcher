package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenMode

object ScreensRows {
    val screenMode = SettingsRowInfo("Cover and inner screens", "Same, different, or continuity")
    val appearance = SettingsRowInfo("Appearance", "Light, dark, or switch at sunrise and sunset")
    val foldTransition = SettingsRowInfo("Fold transition", "Morph animation or instant", advanced = true)
    val flexLayout = SettingsRowInfo("Flex mode layout", "Own layout or reuse the inner screen", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.SCREENS,
        title = "Screens",
        subtitle = "Same, different or continuity; dark, light, auto",
        icon = Icons.Outlined.PhoneAndroid,
        rows = listOf(screenMode, appearance, foldTransition, flexLayout),
    )

    val screenModeOptions: List<Pair<ScreenMode, String>> = listOf(
        ScreenMode.SAME to "Same layout",
        ScreenMode.DIFFERENT to "Different layouts",
        ScreenMode.CONTINUITY to "Continuity",
    )
    val appearanceOptions: List<Pair<Appearance, String>> = listOf(
        Appearance.DARK to "Dark",
        Appearance.LIGHT to "Light",
        Appearance.AUTO to "Auto (sunset)",
    )
}

@Composable
fun ScreensPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(title = ScreensRows.page.title, onBack = onBack) {
        SelectRow(
            info = ScreensRows.screenMode,
            value = settings.screenMode,
            options = ScreensRows.screenModeOptions,
            onSelect = { mode -> update { it.copy(screenMode = mode) } },
        )
        SelectRow(
            info = ScreensRows.appearance,
            value = settings.appearance,
            options = ScreensRows.appearanceOptions,
            onSelect = { mode -> update { it.copy(appearance = mode) } },
        )
        StaticRow(info = ScreensRows.foldTransition, trailingText = "Morph")
        StaticRow(info = ScreensRows.flexLayout, trailingText = "Own layout")
    }
}
