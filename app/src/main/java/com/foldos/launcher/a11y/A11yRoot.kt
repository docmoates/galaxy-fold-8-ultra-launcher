package com.foldos.launcher.a11y

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.foldos.launcher.core.model.LauncherSettings

/** Range the text scale slider and [A11yRoot] clamp `LauncherSettings.textScale` to. */
const val A11Y_MIN_TEXT_SCALE: Float = 0.85f
const val A11Y_MAX_TEXT_SCALE: Float = 1.6f

/**
 * Applies the accessibility settings to everything inside [content]. Compose it ONCE per window,
 * directly inside `FoldOsTheme` (launcher) or the settings theme (`SettingsActivity`):
 *
 * - provides [LocalReduceMotion], [LocalHighContrast], [LocalGreyscale] and [LocalLargeIcons];
 * - text scale: replaces `LocalDensity` with a `Density` whose `fontScale` is the system font scale
 *   times `settings.textScale`, so every `sp` value follows while `dp` sizes stay put (nesting a
 *   second [A11yRoot] would multiply the factor twice);
 * - greyscale: renders the whole content box through a saturation-0 color matrix ([a11yGreyscale]).
 *   Content drawn in separate windows (`Popup`, `Dialog`, bottom sheets) is not covered; give its
 *   root `Modifier.a11yGreyscale()` too.
 *
 * `highContrast` is applied by `FoldOsTheme` (theme tokens), not here.
 *
 * @param applyGreyscale pass false to read [LocalGreyscale] but leave the render effect to the caller.
 */
@Composable
fun A11yRoot(
    settings: LauncherSettings,
    modifier: Modifier = Modifier,
    applyGreyscale: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    val scale = settings.textScale.coerceIn(A11Y_MIN_TEXT_SCALE, A11Y_MAX_TEXT_SCALE)
    val density = remember(base.density, base.fontScale, scale) {
        Density(density = base.density, fontScale = base.fontScale * scale)
    }
    CompositionLocalProvider(
        LocalReduceMotion provides settings.reduceMotion,
        LocalHighContrast provides settings.highContrast,
        LocalGreyscale provides settings.greyscale,
        LocalLargeIcons provides settings.largeIcons,
        LocalDensity provides density,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .a11yGreyscale(enabled = applyGreyscale && settings.greyscale),
        ) {
            content()
        }
    }
}

/** The shared saturation-0 effect; built on first use (API 31+, the app's minSdk is 34). */
private val GreyscaleEffect: androidx.compose.ui.graphics.RenderEffect by lazy(LazyThreadSafetyMode.NONE) {
    val matrix = ColorMatrix().apply { setSaturation(0f) }
    RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix)).asComposeRenderEffect()
}

/**
 * Draws this node and its children in greyscale (color matrix saturation 0) while [enabled]; the
 * unchanged modifier otherwise. Use it on the root of anything that lives in its own window.
 */
fun Modifier.a11yGreyscale(enabled: Boolean): Modifier =
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        this
    } else {
        this.graphicsLayer { renderEffect = GreyscaleEffect }
    }

/** [a11yGreyscale] driven by [LocalGreyscale]; call from a composable (e.g. a `Popup` root). */
@Composable
fun Modifier.a11yGreyscale(): Modifier = a11yGreyscale(enabled = LocalGreyscale.current)
