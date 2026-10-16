package com.foldos.launcher.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.LauncherSettings

/**
 * Launcher typography. Roboto Flex is not bundled, so this is Material 3's default type scale
 * on the platform default font family.
 */
val FoldOsTypography: Typography = Typography()

/** Resolves Settings > Look > Appearance to a boolean; [Appearance.AUTO] follows the system. */
@Composable
fun isDarkAppearance(appearance: Appearance): Boolean = when (appearance) {
    Appearance.DARK -> true
    Appearance.LIGHT -> false
    Appearance.AUTO -> isSystemInDarkTheme()
}

/**
 * Root theme for every launcher screen. Resolves dark/light from [LauncherSettings.appearance],
 * reads Material You dynamic color (API 31+; the app's minSdk is 34) and provides:
 *
 * - [LocalFoldOsTokens] with the [FoldOsTokens] for `settings.theme` + `settings.colors`, with
 *   the corner radius taken from `settings.radiusDp` (Settings > Look > Corner radius) and, with
 *   `settings.highContrast`, the [withHighContrast] boost,
 * - a [MaterialTheme] whose color scheme is derived from those tokens (primary = accent, surface =
 *   surface, ...), [FoldOsTypography], and shapes based on the theme radius,
 * - [LocalContentColor] = `tokens.onSurface` so plain `Text` is readable on the wallpaper.
 */
@Composable
fun FoldOsTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = isDarkAppearance(settings.appearance)
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val dynamic: ColorScheme? = remember(context, dark, inspection) {
        if (inspection) {
            null
        } else if (dark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    }
    val tokens = remember(settings.theme, dark, settings.colors, dynamic, settings.radiusDp, settings.highContrast) {
        val themed = tokensFor(settings.theme, dark, settings.colors, dynamic).copy(radius = settings.radiusDp.dp)
        // Settings > Accessibility > High contrast: opaque panels, strong outlines, black / white text.
        if (settings.highContrast) themed.withHighContrast() else themed
    }
    val colorScheme = remember(tokens) { tokens.toMaterialColorScheme() }
    val shapes = remember(tokens.radius) { shapesFor(tokens.radius) }

    CompositionLocalProvider(
        LocalFoldOsTokens provides tokens,
        LocalContentColor provides tokens.onSurface,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FoldOsTypography,
            shapes = shapes,
            content = content,
        )
    }
}

/** Material 3 shapes scaled from the theme radius (Bold 6dp ... Material 28dp). */
private fun shapesFor(radius: Dp): Shapes = Shapes(
    extraSmall = RoundedCornerShape(radius * 0.25f),
    small = RoundedCornerShape(radius * 0.5f),
    medium = RoundedCornerShape(radius * 0.75f),
    large = RoundedCornerShape(radius),
    extraLarge = RoundedCornerShape(radius * 1.25f),
)

/**
 * A Material 3 [ColorScheme] derived from the tokens so stock components (buttons, switches,
 * sheets, dialogs) follow the launcher theme. Translucent token surfaces are composited onto an
 * opaque base so M3 containers never show through each other.
 */
fun FoldOsTokens.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) Color(0xFF0C0D12) else Color(0xFFEEF0F5)
    val solidSurface = surface.compositeOver(base)
    val solidHighlight = highlight.compositeOver(solidSurface)
    val outlineColor = surfaceBorder.compositeOver(solidSurface)
    val onSurfaceVariantColor = onSurface.tint(solidSurface, 0.72f)
    val surfaceVariantColor = onSurface.tint(solidSurface, 0.08f)

    // Progressive container tones (M3 surfaceContainer*), lighter in dark mode, darker in light mode.
    val step = if (isDark) Color.White else Color.Black
    val containerLowest = if (isDark) base else Color.White
    val containerLow = step.tint(solidSurface, 0.02f)
    val container = step.tint(solidSurface, 0.05f)
    val containerHigh = step.tint(solidSurface, 0.08f)
    val containerHighest = step.tint(solidSurface, 0.12f)

    return if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = solidHighlight,
            onPrimaryContainer = onHighlight,
            inversePrimary = accent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = solidHighlight,
            onSecondaryContainer = onHighlight,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = solidHighlight,
            onTertiaryContainer = onHighlight,
            background = base,
            onBackground = onSurface,
            surface = solidSurface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = onSurfaceVariantColor,
            surfaceTint = accent,
            inverseSurface = onSurface,
            inverseOnSurface = solidSurface,
            outline = outlineColor,
            outlineVariant = outlineColor,
            surfaceBright = containerHighest,
            surfaceDim = base,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest,
            surfaceContainerLow = containerLow,
            surfaceContainerLowest = containerLowest,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = solidHighlight,
            onPrimaryContainer = onHighlight,
            inversePrimary = accent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = solidHighlight,
            onSecondaryContainer = onHighlight,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = solidHighlight,
            onTertiaryContainer = onHighlight,
            background = base,
            onBackground = onSurface,
            surface = solidSurface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = onSurfaceVariantColor,
            surfaceTint = accent,
            inverseSurface = onSurface,
            inverseOnSurface = solidSurface,
            outline = outlineColor,
            outlineVariant = outlineColor,
            surfaceBright = Color.White,
            surfaceDim = containerHighest,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest,
            surfaceContainerLow = containerLow,
            surfaceContainerLowest = containerLowest,
        )
    }
}

/** This color at [amount] opacity composited onto [onto]. */
private fun Color.tint(onto: Color, amount: Float): Color = copy(alpha = amount).compositeOver(onto)
