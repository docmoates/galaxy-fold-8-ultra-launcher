package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.ColorSchemes
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ThemeId

object LookRows {
    val theme = SettingsRowInfo("Theme")
    val scheme = SettingsRowInfo("Color scheme", "Recolors accent, highlights and wallpaper on any theme")
    val accent = SettingsRowInfo("Accent color", "Buttons, highlights, active tabs")
    val wall1 = SettingsRowInfo("Wallpaper color 1")
    val wall2 = SettingsRowInfo("Wallpaper color 2")
    val radius = SettingsRowInfo("Corner radius", advanced = true)
    val iconPack = SettingsRowInfo("Icon pack and icon shape", "Any installed icon pack, per-app edits", advanced = true)
    val wallpaper = SettingsRowInfo("Wallpaper", "Separate wallpaper per screen and per mode")
    val presetGallery = SettingsRowInfo("Preset gallery", "Minimal, Dashboard, Productivity, Media, Power user")

    val page = SettingsPageInfo(
        route = SettingsRoutes.LOOK,
        title = "Look",
        subtitle = "Theme, color scheme, accent, corner radius",
        icon = Icons.Outlined.Palette,
        rows = listOf(theme, scheme, accent, wall1, wall2, radius, iconPack, wallpaper, presetGallery),
    )

    val themeOptions: List<Pair<ThemeId, String>> = listOf(
        ThemeId.MATERIAL to "Material You",
        ThemeId.GLASS to "Glass",
        ThemeId.ONEUI to "One UI",
        ThemeId.BOLD to "Bold",
    )

    /** Default corner radius per theme (mock-up `RAD`). */
    fun defaultRadiusDp(theme: ThemeId): Int = when (theme) {
        ThemeId.MATERIAL -> 28
        ThemeId.GLASS -> 22
        ThemeId.ONEUI -> 26
        ThemeId.BOLD -> 6
    }

    val schemeOptions: List<Pair<String, String>> =
        listOf(ColorSchemeConfig.SCHEME_THEME to "Theme colors") +
            ColorSchemes.presets.map { preset -> preset.id to preset.name } +
            (ColorSchemeConfig.SCHEME_CUSTOM to "Custom")
}

@Composable
fun LookPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val colors = settings.colors

    fun setCustomColor(transform: (ColorSchemeConfig) -> ColorSchemeConfig) {
        update { current ->
            current.copy(colors = transform(current.colors).copy(scheme = ColorSchemeConfig.SCHEME_CUSTOM))
        }
    }

    SettingsPageScaffold(title = LookRows.page.title, onBack = onBack) {
        ChipsRow(
            info = LookRows.theme,
            value = settings.theme,
            options = LookRows.themeOptions,
            onSelect = { theme ->
                update { it.copy(theme = theme, radiusDp = LookRows.defaultRadiusDp(theme)) }
            },
        )
        ChipsRow(
            info = LookRows.scheme,
            value = colors.scheme,
            options = LookRows.schemeOptions,
            onSelect = { id ->
                update { current ->
                    val preset = ColorSchemes.byId(id)
                    val next = if (preset != null) {
                        current.colors.copy(
                            scheme = id,
                            accent = preset.accent,
                            wall1 = preset.wall1,
                            wall2 = preset.wall2,
                        )
                    } else {
                        current.colors.copy(scheme = id)
                    }
                    current.copy(colors = next)
                }
            },
        )
        ColorRow(
            info = LookRows.accent,
            value = colors.accent,
            onValueChange = { argb -> setCustomColor { it.copy(accent = argb) } },
        )
        ColorRow(
            info = LookRows.wall1,
            value = colors.wall1,
            onValueChange = { argb -> setCustomColor { it.copy(wall1 = argb) } },
        )
        ColorRow(
            info = LookRows.wall2,
            value = colors.wall2,
            onValueChange = { argb -> setCustomColor { it.copy(wall2 = argb) } },
        )
        SliderRow(
            info = LookRows.radius,
            value = settings.radiusDp,
            range = 0..32,
            onValueChange = { radius -> update { it.copy(radiusDp = radius) } },
            unit = " dp",
        )
        StaticRow(info = LookRows.iconPack)
        StaticRow(info = LookRows.wallpaper)
        StaticRow(info = LookRows.presetGallery)
    }
}
