package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.foldos.launcher.onboarding.SampleDockPreview
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

/** One on / off trait shown as a chip. */
private class DockTrait(val label: String, val on: Boolean, val onToggle: (Boolean) -> Unit)

/**
 * The dock trait editor ("Customize" on the Settings home): a live preview of the dock being edited,
 * then chips for the background, position, items, layout traits and add-ons. Picking a whole style is
 * done on the Settings home and in [DockGalleryPage]; this page only mixes traits.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.widthIn(max = 480.dp).clip(RoundedCornerShape(16.dp))) {
                SampleDockPreview(
                    dock = dock,
                    modifier = Modifier.fillMaxWidth().aspectRatio(DockStripRatio),
                    base = settings,
                )
            }
        }
        val description = DockPresets.byId(presetId)?.description
            ?: "Custom mix: your own combination of traits."
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ToggleRow(
            info = DockRows.dock,
            checked = settings.feature(LauncherSettings.FEATURE_DOCK),
            onCheckedChange = { on -> update { it.withFeature(LauncherSettings.FEATURE_DOCK, on) } },
        )
        ChipsRow(
            info = DockRows.editFor,
            value = screen,
            options = DockRows.editForOptions,
            onSelect = { kind -> editInner = kind == ScreenKind.INNER },
            visible = !sameLayout,
        )
        ChipsRow(
            info = DockRows.preset,
            value = presetId,
            options = DockRows.presetOptions,
            onSelect = { id ->
                if (id != DockPresets.CUSTOM_ID) setDock(DockPresets.configFor(id))
            },
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

        TraitChips(
            title = "Layout",
            traits = listOf(
                DockTrait("Labels", dock.labels) { on -> setDock(dock.copy(labels = on)) },
                DockTrait("Two rows", dock.two) { on -> setDock(dock.copy(two = on, scroll = if (on) false else dock.scroll)) },
                DockTrait("Scrolling", dock.scroll) { on -> setDock(dock.copy(scroll = on, two = if (on) false else dock.two)) },
                DockTrait("Magnify", dock.magnify) { on -> setDock(dock.copy(magnify = on)) },
            ),
        )
        if (!isRail) {
            TraitChips(
                title = "Add-ons",
                traits = listOf(
                    DockTrait("Search bar", dock.search) { on -> setDock(dock.copy(search = on)) },
                    DockTrait("Recent apps", dock.taskbar) { on -> setDock(dock.copy(taskbar = on)) },
                    DockTrait("AI suggestions", dock.smart) { on -> setDock(dock.copy(smart = on)) },
                    DockTrait("Folder", dock.folder) { on -> setDock(dock.copy(folder = on)) },
                    DockTrait("Media player", dock.media) { on -> setDock(dock.copy(media = on)) },
                    DockTrait("Notification preview", dock.notify) { on -> setDock(dock.copy(notify = on)) },
                    DockTrait("Center button", dock.center) { on -> setDock(dock.copy(center = on)) },
                    DockTrait("Swipe handle", dock.handle) { on -> setDock(dock.copy(handle = on)) },
                ),
            )
        }

        StaticRow(info = DockRows.more)
        StaticRow(info = DockRows.searchProvider, trailingText = "Launcher")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TraitChips(title: String, traits: List<DockTrait>) {
    SectionHeader(text = title)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        traits.forEach { trait ->
            FilterChip(
                selected = trait.on,
                onClick = { trait.onToggle(!trait.on) },
                label = { Text(text = trait.label) },
                leadingIcon = if (trait.on) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
