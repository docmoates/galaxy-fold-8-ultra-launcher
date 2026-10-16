package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.foldos.launcher.core.model.DockBackground
import com.foldos.launcher.core.model.DockConfig
import com.foldos.launcher.core.model.DockContent
import com.foldos.launcher.core.model.DockPosition
import com.foldos.launcher.core.model.DockPresets
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.ScreenMode
import com.foldos.launcher.core.model.matchingPresetId

object DockRows {
    val dock = SettingsRowInfo("Dock")
    val editFor = SettingsRowInfo("Edit the dock for")
    val preset = SettingsRowInfo("Start from a style", "20 styles; switch any trait on or off afterwards")
    val background = SettingsRowInfo("Background")
    val position = SettingsRowInfo("Position")
    val items = SettingsRowInfo("Items")
    val labels = SettingsRowInfo("Labels")
    val two = SettingsRowInfo("Two rows")
    val scroll = SettingsRowInfo("Scrolling")
    val magnify = SettingsRowInfo("Magnify")
    val search = SettingsRowInfo("Search bar", "Add-on")
    val taskbar = SettingsRowInfo("Recent apps", "Add-on")
    val smart = SettingsRowInfo("AI suggestions", "Add-on")
    val folder = SettingsRowInfo("Folder", "Add-on")
    val media = SettingsRowInfo("Media player", "Add-on")
    val notify = SettingsRowInfo("Notification preview", "Add-on")
    val center = SettingsRowInfo("Center button", "Add-on")
    val handle = SettingsRowInfo("Swipe handle", "Add-on")
    val more = SettingsRowInfo("Dock icon count, background opacity, blur", advanced = true)
    val searchProvider = SettingsRowInfo("Dock search provider", "Launcher search, Google, or Perplexity", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.DOCK,
        title = "Dock",
        subtitle = "20 styles per screen, every trait mixable",
        icon = Icons.Outlined.Dock,
        rows = listOf(
            dock, editFor, preset, background, position, items,
            labels, two, scroll, magnify,
            search, taskbar, smart, folder, media, notify, center, handle,
            more, searchProvider,
        ),
    )

    val editForOptions: List<Pair<ScreenKind, String>> = listOf(
        ScreenKind.COVER to "Cover screen",
        ScreenKind.INNER to "Inner screen",
    )
    val presetOptions: List<Pair<String, String>> =
        DockPresets.all.mapIndexed { index, preset -> preset.id to "${index + 1}. ${preset.name}" } +
            (DockPresets.CUSTOM_ID to "Custom mix")
    val backgroundOptions: List<Pair<DockBackground, String>> = listOf(
        DockBackground.PILL to "Pill",
        DockBackground.SHELF to "Edge shelf",
        DockBackground.NONE to "None",
        DockBackground.BUBBLE to "Bubbles",
        DockBackground.LINE to "Hairline",
        DockBackground.REFLECT to "3D shelf",
    )
    val positionOptions: List<Pair<DockPosition, String>> = listOf(
        DockPosition.BOTTOM to "Bottom",
        DockPosition.RAIL to "Edge rail",
    )
    val contentOptions: List<Pair<DockContent, String>> = listOf(
        DockContent.ICONS to "Icons",
        DockContent.TEXT to "App names",
    )
}

@Composable
fun DockPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    var editInner by rememberSaveable { mutableStateOf(false) }
    val sameLayout = settings.screenMode == ScreenMode.SAME
    val screen = if (!sameLayout && editInner) ScreenKind.INNER else ScreenKind.COVER
    val dock = settings.dockFor(screen)

    fun setDock(next: DockConfig) {
        update { current ->
            if (screen == ScreenKind.COVER) current.copy(dockCover = next) else current.copy(dockInner = next)
        }
    }

    val presetId = dock.matchingPresetId() ?: DockPresets.CUSTOM_ID
    val isRail = dock.pos == DockPosition.RAIL

    SettingsPageScaffold(title = DockRows.page.title, onBack = onBack) {
        ToggleRow(
            info = DockRows.dock,
            checked = settings.feature(LauncherSettings.FEATURE_DOCK),
            onCheckedChange = { on -> update { it.withFeature(LauncherSettings.FEATURE_DOCK, on) } },
        )
        SelectRow(
            info = DockRows.editFor,
            value = screen,
            options = DockRows.editForOptions,
            onSelect = { kind -> editInner = kind == ScreenKind.INNER },
            visible = !sameLayout,
        )
        SelectRow(
            info = DockRows.preset,
            value = presetId,
            options = DockRows.presetOptions,
            onSelect = { id ->
                if (id != DockPresets.CUSTOM_ID) setDock(DockPresets.configFor(id))
            },
        )
        val description = DockPresets.byId(presetId)?.description
            ?: "Custom mix: your own combination of traits."
        StaticRow(
            info = SettingsRowInfo("Current style", description),
            showChevron = false,
        )
        ChipsRow(
            info = DockRows.background,
            value = dock.bg,
            options = DockRows.backgroundOptions,
            onSelect = { bg -> setDock(dock.copy(bg = bg)) },
        )
        ChipsRow(
            info = DockRows.position,
            value = dock.pos,
            options = DockRows.positionOptions,
            onSelect = { pos -> setDock(dock.copy(pos = pos)) },
        )
        ChipsRow(
            info = DockRows.items,
            value = dock.content,
            options = DockRows.contentOptions,
            onSelect = { content -> setDock(dock.copy(content = content)) },
        )

        SectionHeader(text = "Layout")
        ToggleRow(
            info = DockRows.labels,
            checked = dock.labels,
            onCheckedChange = { on -> setDock(dock.copy(labels = on)) },
        )
        ToggleRow(
            info = DockRows.two,
            checked = dock.two,
            onCheckedChange = { on -> setDock(dock.copy(two = on, scroll = if (on) false else dock.scroll)) },
        )
        ToggleRow(
            info = DockRows.scroll,
            checked = dock.scroll,
            onCheckedChange = { on -> setDock(dock.copy(scroll = on, two = if (on) false else dock.two)) },
        )
        ToggleRow(
            info = DockRows.magnify,
            checked = dock.magnify,
            onCheckedChange = { on -> setDock(dock.copy(magnify = on)) },
        )

        if (!isRail) SectionHeader(text = "Add-ons")
        ToggleRow(
            info = DockRows.search,
            checked = dock.search,
            onCheckedChange = { on -> setDock(dock.copy(search = on)) },
            visible = !isRail,
        )
        ToggleRow(
            info = DockRows.taskbar,
            checked = dock.taskbar,
            onCheckedChange = { on -> setDock(dock.copy(taskbar = on)) },
            visible = !isRail,
        )
        ToggleRow(
            info = DockRows.smart,
            checked = dock.smart,
            onCheckedChange = { on -> setDock(dock.copy(smart = on)) },
            visible = !isRail,
        )
        ToggleRow(
            info = DockRows.folder,
            checked = dock.folder,
            onCheckedChange = { on -> setDock(dock.copy(folder = on)) },
            visible = !isRail,
        )
        ToggleRow(
            info = DockRows.media,
            checked = dock.media,
            onCheckedChange = { on -> setDock(dock.copy(media = on)) },
            visible = !isRail,
        )
        ToggleRow(
            info = DockRows.notify,
            checked = dock.notify,
            onCheckedChange = { on -> setDock(dock.copy(notify = on)) },
            visible = !isRail,
        )
        ToggleRow(
            info = DockRows.center,
            checked = dock.center,
            onCheckedChange = { on -> setDock(dock.copy(center = on)) },
            visible = !isRail,
        )
        ToggleRow(
            info = DockRows.handle,
            checked = dock.handle,
            onCheckedChange = { on -> setDock(dock.copy(handle = on)) },
            visible = !isRail,
        )

        StaticRow(info = DockRows.more)
        StaticRow(info = DockRows.searchProvider, trailingText = "Launcher")
    }
}
