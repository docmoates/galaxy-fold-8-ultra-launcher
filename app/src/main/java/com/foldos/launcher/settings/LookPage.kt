package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.ColorSchemes
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ThemeId
import com.foldos.launcher.icons.IconPackPicker
import com.foldos.launcher.icons.IconShapePicker
import com.foldos.launcher.icons.IconShapes
import com.foldos.launcher.onboarding.OnboardingActivity

object LookRows {
    val theme = SettingsRowInfo("Theme")
    val scheme = SettingsRowInfo("Color scheme", "Recolors accent, highlights and wallpaper on any theme")
    val accent = SettingsRowInfo("Accent color", "Buttons, highlights, active tabs")
    val wall1 = SettingsRowInfo("Wallpaper color 1")
    val wall2 = SettingsRowInfo("Wallpaper color 2")
    val radius = SettingsRowInfo("Corner radius", advanced = true)
    val iconPack = SettingsRowInfo("Icon pack", "Any installed icon pack; long-press an app for a per-app icon")
    val iconShape = SettingsRowInfo("Icon shape", "Circle, squircle, rounded, teardrop or the system mask")
    val wallpaper = SettingsRowInfo("Wallpaper", "Separate wallpaper per screen and per mode")
    val dailyWallpaper = SettingsRowInfo(
        "Daily wallpaper",
        "A new generated gradient every day. Replaces your phone's home wallpaper and shows behind FoldOS; off by default",
    )
    val presetGallery = SettingsRowInfo("Preset gallery", "Minimal, Dashboard, Productivity, Media, Power user, One UI Classic")

    val page = SettingsPageInfo(
        route = SettingsRoutes.LOOK,
        title = "Look",
        subtitle = "Theme, color scheme, accent, icons, corner radius",
        icon = Icons.Outlined.Palette,
        rows = listOf(theme, scheme, accent, wall1, wall2, radius, iconPack, iconShape, wallpaper, dailyWallpaper, presetGallery),
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
    val context = LocalContext.current
    val colors = settings.colors
    var showIconPackPicker by rememberSaveable { mutableStateOf(false) }
    var showIconShapePicker by rememberSaveable { mutableStateOf(false) }

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
        StaticRow(
            info = LookRows.iconPack,
            onClick = { showIconPackPicker = true },
            trailingText = settings.iconPack?.substringAfterLast('.') ?: "System icons",
        )
        StaticRow(
            info = LookRows.iconShape,
            onClick = { showIconShapePicker = true },
            trailingText = IconShapes.displayName(settings.iconShape),
        )
        StaticRow(info = LookRows.wallpaper)
        ToggleRow(
            info = LookRows.dailyWallpaper,
            checked = settings.dailyWallpaper,
            onCheckedChange = { on -> update { it.copy(dailyWallpaper = on) } },
        )
        StaticRow(
            info = LookRows.presetGallery,
            onClick = { runCatching { context.startActivity(OnboardingActivity.galleryIntent(context)) } },
        )
    }

    if (showIconPackPicker) IconPackPicker(onClose = { showIconPackPicker = false })
    if (showIconShapePicker) IconShapePicker(onClose = { showIconShapePicker = false })
}
