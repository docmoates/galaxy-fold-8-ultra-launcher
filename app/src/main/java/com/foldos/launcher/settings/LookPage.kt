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
import com.foldos.launcher.onboarding.colorsForFamily
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foldos.launcher.theme.SpaceBlackPreviewTile
import com.foldos.launcher.theme.isDarkAppearance

object LookRows {
    val theme = SettingsRowInfo("Theme")
    val spaceBlack = SettingsRowInfo("Space black", "Dock, cards and widgets solid black, all text white")
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
        rows = listOf(theme, spaceBlack, scheme, accent, wall1, wall2, radius, iconPack, iconShape, wallpaper, dailyWallpaper, presetGallery),
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

    /** Default (the theme's own colors), one entry per primary color (it has a light and a dark version), Custom. Keys are family ids. */
    val schemeOptions: List<Pair<String, String>> =
        listOf(ColorSchemeConfig.SCHEME_THEME to "Default") +
            ColorSchemes.families.map { family -> family.id to family.name } +
            (ColorSchemeConfig.SCHEME_CUSTOM to "Custom")
}

/** Look > Space black: a labeled switch with a live preview tile; also used by the main Settings page. */
@Composable
internal fun SpaceBlackToggleRow(settings: LauncherSettings, update: SettingsUpdater) {
    ToggleRow(
        info = LookRows.spaceBlack,
        checked = settings.spaceBlack,
        onCheckedChange = { on -> update { it.copy(spaceBlack = on) } },
        leadingContent = {
            SpaceBlackPreviewTile(base = settings, modifier = Modifier.size(width = 48.dp, height = 64.dp))
        },
    )
}

@Composable
fun LookPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = settings.colors
    val isDark = isDarkAppearance(settings.appearance)
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
        SpaceBlackToggleRow(settings = settings, update = update)
        ChipsRow(
            info = LookRows.scheme,
            value = ColorSchemes.familyOfScheme(colors.scheme)?.id ?: colors.scheme,
            options = LookRows.schemeOptions,
            onSelect = { id ->
                update { current ->
                    val family = ColorSchemes.familyById(id)
                    val next = if (family != null) {
                        // The light or dark version is picked by the Appearance on its own.
                        colorsForFamily(current.colors, family, isDark)
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
