package com.foldos.launcher.onboarding

import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.DockConfig
import com.foldos.launcher.core.model.DockPresets
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.GridMode
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ThemeId

/**
 * A whole-launcher starting point (PLAN.md 5.3 "Preset gallery"). [apply] rewrites the look and
 * layout settings of the given [LauncherSettings] and records [id] in `LauncherSettings.preset`;
 * it leaves appearance (dark / light / auto), hub tabs and feature switches alone unless
 * the preset is about them.
 */
data class Preset(
    val id: String,
    val name: String,
    val description: String,
    val apply: (LauncherSettings) -> LauncherSettings,
)

object Presets {
    /** Corner radius each theme ships with (mock-up `RAD`). */
    fun radiusFor(theme: ThemeId): Int = when (theme) {
        ThemeId.MATERIAL -> 28
        ThemeId.GLASS -> 22
        ThemeId.ONEUI -> 26
        ThemeId.BOLD -> 6
    }

    val minimal: Preset = Preset(
        id = "minimal",
        name = "Minimal",
        description = "Text list of favorites on both screens, no icons, text dock, Dark Glass in mono.",
        apply = { s ->
            s.copy(
                styleCover = HomeStyle.LIST,
                styleInner = HomeStyle.LIST,
                theme = ThemeId.GLASS,
                radiusDp = radiusFor(ThemeId.GLASS),
                labels = false,
                dockCover = DockPresets.configFor("text"),
                dockInner = DockPresets.configFor("text"),
                colors = ColorSchemeConfig(scheme = "mono"),
                gridMode = GridMode.GRID,
                preset = "minimal",
            )
        },
    )

    val dashboard: Preset = Preset(
        id = "dashboard",
        name = "Dashboard",
        description = "Widgets fill every page, apps live in the dock and drawer. Material You with a floating pill and taskbar.",
        apply = { s ->
            s.copy(
                styleCover = HomeStyle.DASHBOARD,
                styleInner = HomeStyle.DASHBOARD,
                theme = ThemeId.MATERIAL,
                radiusDp = radiusFor(ThemeId.MATERIAL),
                labels = true,
                dockCover = DockPresets.configFor("pill"),
                dockInner = DockPresets.configFor("taskbar"),
                colors = ColorSchemeConfig(),
                gridMode = GridMode.GRID,
                preset = "dashboard",
            )
        },
    )

    val productivity: Preset = Preset(
        id = "productivity",
        name = "Productivity",
        description = "Grid outside, hybrid panels with calendar and tasks inside. One UI look, search dock, hub with every tab.",
        apply = { s ->
            s.copy(
                styleCover = HomeStyle.GRID,
                styleInner = HomeStyle.HYBRID,
                theme = ThemeId.ONEUI,
                radiusDp = radiusFor(ThemeId.ONEUI),
                labels = true,
                dockCover = DockPresets.configFor("search"),
                dockInner = DockPresets.configFor("taskbar"),
                colors = ColorSchemeConfig(scheme = "ocean"),
                hubOn = true,
                hubTabs = setOf(
                    LauncherSettings.HUB_TAB_AI,
                    LauncherSettings.HUB_TAB_MAIL,
                    LauncherSettings.HUB_TAB_CAL,
                    LauncherSettings.HUB_TAB_PEOPLE,
                ),
                gridMode = GridMode.GRID,
                preset = "productivity",
            )
        },
    )

    val media: Preset = Preset(
        id = "media",
        name = "Media",
        description = "Now-playing and photo widgets on a dashboard, media dock on both screens, Dark Glass in sunset colors.",
        apply = { s ->
            s.copy(
                styleCover = HomeStyle.DASHBOARD,
                styleInner = HomeStyle.DASHBOARD,
                theme = ThemeId.GLASS,
                radiusDp = radiusFor(ThemeId.GLASS),
                dockCover = DockPresets.configFor("media"),
                dockInner = DockPresets.configFor("media"),
                colors = ColorSchemeConfig(scheme = "sunset"),
                gridMode = GridMode.GRID,
                preset = "media",
            )
        },
    )

    val powerUser: Preset = Preset(
        id = "power",
        name = "Power user",
        description = "Hybrid panels, free placement, dense grids, Bold theme, center-action dock outside and a smart search taskbar inside. Every feature on.",
        apply = { s ->
            var next = s.copy(
                styleCover = HomeStyle.HYBRID,
                styleInner = HomeStyle.HYBRID,
                theme = ThemeId.BOLD,
                radiusDp = radiusFor(ThemeId.BOLD),
                labels = true,
                gridMode = GridMode.FREE,
                colsCover = 5,
                colsInner = 8,
                iconSizeDp = 52,
                dockCover = DockPresets.configFor("center"),
                dockInner = DockConfig(taskbar = true, search = true, smart = true),
                colors = ColorSchemeConfig(),
                hubOn = true,
                preset = "power",
            )
            LauncherSettings.ALL_FEATURES.forEach { key -> next = next.withFeature(key, true) }
            next
        },
    )

    val oneUiClassic: Preset = Preset(
        id = "oneui",
        name = "One UI Classic",
        description = "Looks like Samsung's launcher: icon grid with labels, icons-only dock, taskbar inside, full-screen drawer, One UI theme colors.",
        apply = { s ->
            s.copy(
                styleCover = HomeStyle.GRID,
                styleInner = HomeStyle.GRID,
                theme = ThemeId.ONEUI,
                radiusDp = radiusFor(ThemeId.ONEUI),
                labels = true,
                gridMode = GridMode.GRID,
                colsCover = 4,
                colsInner = 6,
                iconSizeDp = 58,
                dockCover = DockPresets.configFor("none"),
                dockInner = DockPresets.configFor("taskbar"),
                drawerStyle = DrawerStyle.FULL,
                colors = ColorSchemeConfig(),
                preset = "oneui",
            )
        },
    )

    val all: List<Preset> = listOf(minimal, dashboard, productivity, media, powerUser, oneUiClassic)

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
