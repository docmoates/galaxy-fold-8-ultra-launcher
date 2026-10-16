package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.GridMode
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenMode

object HomeRows {
    val styleCover = SettingsRowInfo("Cover screen style", "Each page can use a different style")
    val styleInner = SettingsRowInfo("Inner screen style")
    val gridMode = SettingsRowInfo("Placement", "Snap to grid or place freely")
    val drawerStyle = SettingsRowInfo("App drawer", "Pixel-style pop-up or full screen")
    val popBlur = SettingsRowInfo("Blur behind pop-up", "Blur home while the drawer is open")
    val labels = SettingsRowInfo("App labels")
    val colsCover = SettingsRowInfo("Cover columns", advanced = true)
    val colsInner = SettingsRowInfo("Inner columns", advanced = true)
    val iconSize = SettingsRowInfo("Icon size", advanced = true)
    val more = SettingsRowInfo("Page indicator, scroll physics, haptics", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.HOME,
        title = "Home",
        subtitle = "Style per screen, placement, app drawer, labels",
        icon = Icons.Outlined.Home,
        rows = listOf(styleCover, styleInner, gridMode, drawerStyle, popBlur, labels, colsCover, colsInner, iconSize, more),
    )

    val coverStyleOptions: List<Pair<HomeStyle, String>> = listOf(
        HomeStyle.GRID to "Classic grid",
        HomeStyle.DASHBOARD to "Widget dashboard",
        HomeStyle.LIST to "Minimal list",
        HomeStyle.HYBRID to "Hybrid panels",
    )
    val innerStyleOptions: List<Pair<HomeStyle, String>> = listOf(
        HomeStyle.DASHBOARD to "Widget dashboard",
        HomeStyle.GRID to "Classic grid",
        HomeStyle.LIST to "Minimal list",
        HomeStyle.HYBRID to "Hybrid panels",
    )
    val gridModeOptions: List<Pair<GridMode, String>> = listOf(
        GridMode.GRID to "Grid",
        GridMode.FREE to "Free placement",
    )
    val drawerStyleOptions: List<Pair<DrawerStyle, String>> = listOf(
        DrawerStyle.POPUP to "Pop-up sheet",
        DrawerStyle.FULL to "Full screen",
    )
}

@Composable
fun HomeSettingsPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(title = HomeRows.page.title, onBack = onBack) {
        SelectRow(
            info = HomeRows.styleCover,
            value = settings.styleCover,
            options = HomeRows.coverStyleOptions,
            onSelect = { style -> update { it.copy(styleCover = style) } },
        )
        SelectRow(
            info = HomeRows.styleInner,
            value = settings.styleInner,
            options = HomeRows.innerStyleOptions,
            onSelect = { style -> update { it.copy(styleInner = style) } },
            visible = settings.screenMode != ScreenMode.SAME,
        )
        SelectRow(
            info = HomeRows.gridMode,
            value = settings.gridMode,
            options = HomeRows.gridModeOptions,
            onSelect = { mode -> update { it.copy(gridMode = mode) } },
        )
        SelectRow(
            info = HomeRows.drawerStyle,
            value = settings.drawerStyle,
            options = HomeRows.drawerStyleOptions,
            onSelect = { style -> update { it.copy(drawerStyle = style) } },
        )
        ToggleRow(
            info = HomeRows.popBlur,
            checked = settings.popBlur,
            onCheckedChange = { on -> update { it.copy(popBlur = on) } },
            visible = settings.drawerStyle == DrawerStyle.POPUP,
        )
        ToggleRow(
            info = HomeRows.labels,
            checked = settings.labels,
            onCheckedChange = { on -> update { it.copy(labels = on) } },
        )
        SliderRow(
            info = HomeRows.colsCover,
            value = settings.colsCover,
            range = 3..5,
            onValueChange = { cols -> update { it.copy(colsCover = cols) } },
        )
        SliderRow(
            info = HomeRows.colsInner,
            value = settings.colsInner,
            range = 5..8,
            onValueChange = { cols -> update { it.copy(colsInner = cols) } },
        )
        SliderRow(
            info = HomeRows.iconSize,
            value = settings.iconSizeDp,
            range = 46..66,
            onValueChange = { size -> update { it.copy(iconSizeDp = size) } },
            unit = " dp",
        )
        StaticRow(info = HomeRows.more)
    }
}
