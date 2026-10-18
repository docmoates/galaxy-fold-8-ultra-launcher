package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.DrawerTint
import com.foldos.launcher.theme.DrawerTintPreviewChip
import com.foldos.launcher.core.model.GridMode
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.home.StarterLayouts

object HomeRows {
    val starterCover = SettingsRowInfo("Cover starter layout", "Seed the cover's first page or a new page with a layout")
    val starterInner = SettingsRowInfo("Inner starter layout", "Seed the inner screen's first page or a new page with a layout")
    val gridMode = SettingsRowInfo("Placement", "Snap to grid or place freely")
    val cardBackground = SettingsRowInfo("Card background", "Rounded surface behind app cards")
    val cardTitles = SettingsRowInfo("Card titles", "Show the title row on cards that have one")
    val drawerStyle = SettingsRowInfo("App drawer", "Pixel-style pop-up or full screen")
    val popBlur = SettingsRowInfo("Blur behind pop-up", "Blur home while the drawer is open")
    val drawerTint = SettingsRowInfo("Pop-up color", "Tint the app pop-up and drawer with your theme color, or keep it neutral")
    val labels = SettingsRowInfo("App labels")
    val folderStyle = SettingsRowInfo("Folder style", "Grid, list or big folders; each folder can override it")
    val colsCover = SettingsRowInfo("Cover columns", advanced = true)
    val colsInner = SettingsRowInfo("Inner columns", advanced = true)
    val iconSize = SettingsRowInfo("Icon size", advanced = true)
    val more = SettingsRowInfo("Page indicator, scroll physics, haptics", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.HOME,
        title = "Home",
        subtitle = "Starter layouts, placement, cards, app drawer, labels",
        icon = Icons.Outlined.Home,
        rows = listOf(
            starterCover, starterInner, gridMode, cardBackground, cardTitles, drawerStyle, popBlur, drawerTint, labels,
            folderStyle, colsCover, colsInner, iconSize, more,
        ),
    )

    val folderStyleOptions: List<Pair<String, String>> = listOf(
        LauncherSettings.FOLDER_GRID to "Grid",
        LauncherSettings.FOLDER_LIST to "List",
        LauncherSettings.FOLDER_BIG to "Big",
    )

    /** The four starter layouts (Round 2.0); seeding only, no rendering style. */
    val starterOptions: List<Pair<HomeStyle, String>> get() = StarterLayouts.all

    val drawerTintOptions: List<Pair<DrawerTint, String>> = listOf(
        DrawerTint.THEME to "Theme color",
        DrawerTint.NEUTRAL to "Neutral",
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
    // Starter layouts (Round 2.0): pick one, then "Replace first page" or "Add as new page".
    var chooserFor by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStarter by rememberSaveable { mutableStateOf<String?>(null) }
    val applyStarter = rememberStarterApplier()
    val chooserScreen = chooserFor?.let { name -> ScreenKind.entries.firstOrNull { it.name == name } }
    val starter = pendingStarter?.let { name -> HomeStyle.entries.firstOrNull { it.name == name } }

    SettingsPageScaffold(title = HomeRows.page.title, onBack = onBack) {
        StaticRow(
            info = HomeRows.starterCover,
            onClick = { chooserFor = ScreenKind.COVER.name },
            trailingText = StarterLayouts.name(settings.styleCover),
        )
        StaticRow(
            info = HomeRows.starterInner,
            onClick = { chooserFor = ScreenKind.INNER.name },
            trailingText = StarterLayouts.name(settings.styleInner),
            visible = settings.innerHasOwnLayout(),
        )
        SelectRow(
            info = HomeRows.gridMode,
            value = settings.gridMode,
            options = HomeRows.gridModeOptions,
            onSelect = { mode -> update { it.copy(gridMode = mode) } },
        )
        ToggleRow(
            info = HomeRows.cardBackground,
            checked = settings.cardBackground,
            onCheckedChange = { on -> update { it.copy(cardBackground = on) } },
        )
        ToggleRow(
            info = HomeRows.cardTitles,
            checked = settings.cardTitles,
            onCheckedChange = { on -> update { it.copy(cardTitles = on) } },
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
        SelectRow(
            info = HomeRows.drawerTint,
            value = settings.drawerTint,
            options = HomeRows.drawerTintOptions,
            onSelect = { tint -> update { it.copy(drawerTint = tint) } },
            leadingContent = {
                DrawerTintPreviewChip(
                    base = settings,
                    tint = settings.drawerTint,
                    modifier = Modifier.size(width = 48.dp, height = 64.dp),
                )
            },
        )
        ToggleRow(
            info = HomeRows.labels,
            checked = settings.labels,
            onCheckedChange = { on -> update { it.copy(labels = on) } },
        )
        SelectRow(
            info = HomeRows.folderStyle,
            value = settings.folderStyle,
            options = HomeRows.folderStyleOptions,
            onSelect = { style -> update { it.copy(folderStyle = style) } },
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

    if (chooserScreen != null && starter == null) {
        StarterChooserDialog(
            screen = chooserScreen,
            onPick = { picked -> pendingStarter = picked.name },
            onDismiss = { chooserFor = null },
        )
    }
    if (chooserScreen != null && starter != null) {
        StarterApplyDialog(
            starter = starter,
            screen = chooserScreen,
            onReplaceFirst = { applyStarter(chooserScreen, starter, true) },
            onAddPage = { applyStarter(chooserScreen, starter, false) },
            onDismiss = {
                pendingStarter = null
                chooserFor = null
            },
        )
    }
}
