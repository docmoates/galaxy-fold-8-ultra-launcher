package com.foldos.launcher.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.a11y.LocalLargeIcons
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlin.math.ceil
import kotlin.math.min

/**
 * The one text style for app names under icons (home grid, folders, pairs, dock, drawer and the
 * folder / pair pop-ups). Constants and size helpers; the composables are [appLabelTextStyle] and
 * [AppLabel].
 *
 * Rules (Fix round 1.3): 12sp medium weight by default (never bold), centered, up to
 * [MAX_LINES] lines with word wrapping and an ellipsis only after the second line, line height
 * [LINE_HEIGHT_FACTOR] times the size, a soft shadow on wallpaper surfaces, and a fixed reserved
 * height of two lines so the icons of a row stay aligned whether a name takes one line or two.
 * The system font scale only counts up to [MAX_FONT_SCALE] for these labels.
 */
object AppLabelStyle {

    /** `LauncherSettings.labelSize` values. */
    const val SIZE_SMALL: String = "small"
    const val SIZE_MEDIUM: String = "medium"
    const val SIZE_LARGE: String = "large"

    /** All sizes in display order. */
    val SIZES: List<String> = listOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE)

    const val SMALL_SP: Float = 11f
    const val MEDIUM_SP: Float = 12f
    const val LARGE_SP: Float = 14f

    /** Extra sp added in Easy mode (`LauncherSettings.largeIcons`): medium becomes 15sp. */
    const val EASY_EXTRA_SP: Float = 3f

    const val MAX_LINES: Int = 2
    const val LINE_HEIGHT_FACTOR: Float = 1.15f

    /** The system (and accessibility) font scale counts only up to this for app labels. */
    const val MAX_FONT_SCALE: Float = 1.15f

    /** Soft shadow behind labels drawn straight on the wallpaper. */
    const val SHADOW_ALPHA: Float = 0.55f
    val SHADOW_BLUR: Dp = 4.dp

    /** Gap between an icon and its label block, and slack so a rounded-up second line never clips. */
    val ICON_GAP: Dp = 6.dp
    val HEIGHT_SLACK: Dp = 1.dp

    /** The label size in sp for a `LauncherSettings.labelSize` value; unknown values mean medium. */
    fun sizeSpFor(labelSize: String): Float = when (labelSize) {
        SIZE_SMALL -> SMALL_SP
        SIZE_LARGE -> LARGE_SP
        else -> MEDIUM_SP
    }

    /** Size in sp the labels of [settings] start from (before Easy mode and the font-scale cap). */
    fun sizeSpFor(settings: LauncherSettings): Float = sizeSpFor(settings.labelSize)

    /** [sizeSp] after Easy mode ([large]) has added its extra size. */
    fun effectiveSizeSp(sizeSp: Float, large: Boolean): Float = if (large) sizeSp + EASY_EXTRA_SP else sizeSp

    /**
     * Height in dp of the two reserved label lines. The cap makes the layout size
     * `size * min(fontScale, MAX_FONT_SCALE)` dp per em, regardless of how large the system font is.
     */
    fun reservedLinesHeightDp(sizeSp: Float, large: Boolean, fontScale: Float = 1f): Float =
        MAX_LINES * effectiveSizeSp(sizeSp, large) * min(fontScale, MAX_FONT_SCALE) * LINE_HEIGHT_FACTOR

    /**
     * Height in dp an icon cell needs below its icon for a label block: the gap, two lines and the
     * slack; zero when the settings hide labels. Used by the home grid to size its rows.
     */
    fun blockHeightDp(settings: LauncherSettings, fontScale: Float = 1f): Float {
        if (!settings.effectiveLabels) return 0f
        return ICON_GAP.value + ceil(reservedLinesHeightDp(sizeSpFor(settings), settings.largeIcons, fontScale)) + HEIGHT_SLACK.value
    }

    /** [blockHeightDp] as a [Dp]. */
    fun blockHeight(settings: LauncherSettings, fontScale: Float = 1f): Dp = blockHeightDp(settings, fontScale).dp
}

/**
 * The label size (sp, before Easy mode and the font-scale cap) that [AppLabel] and
 * [appLabelTextStyle] use when no size is passed. Provide it with [ProvideLabelSize]; the home
 * screen, drawer and dock do. Default is medium (12sp).
 */
val LocalLabelSizeSp: ProvidableCompositionLocal<Float> = compositionLocalOf { AppLabelStyle.MEDIUM_SP }

/** Provides [LocalLabelSizeSp] from `settings.labelSize` to [content]. */
@Composable
fun ProvideLabelSize(settings: LauncherSettings, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLabelSizeSp provides AppLabelStyle.sizeSpFor(settings),
        content = content,
    )
}

/**
 * The shared app-label [TextStyle]: [sizeSp] (plus 3sp in Easy mode) medium weight, centered unless
 * [textAlign] says otherwise, line height 1.15 and, with [onWallpaper], a black 55% shadow with a 4dp
 * blur. The size is kept from growing with the system font: it is converted so that the label's
 * pixel size is `size * min(fontScale, 1.15)`, i.e. `sp = size * min(fontScale, 1.15) / fontScale`.
 * [color] is applied when specified.
 */
@Composable
fun appLabelTextStyle(
    sizeSp: Float = LocalLabelSizeSp.current,
    onWallpaper: Boolean = true,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Center,
): TextStyle {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val size = AppLabelStyle.effectiveSizeSp(sizeSp, LocalLargeIcons.current)
    val cap = if (fontScale > AppLabelStyle.MAX_FONT_SCALE) AppLabelStyle.MAX_FONT_SCALE / fontScale else 1f
    val shadow = if (onWallpaper) {
        Shadow(
            color = Color.Black.copy(alpha = AppLabelStyle.SHADOW_ALPHA),
            offset = Offset(0f, with(density) { 1.dp.toPx() }),
            blurRadius = with(density) { AppLabelStyle.SHADOW_BLUR.toPx() },
        )
    } else {
        null
    }
    return TextStyle(
        color = color,
        fontSize = (size * cap).sp,
        fontWeight = FontWeight.Medium,
        lineHeight = (size * cap * AppLabelStyle.LINE_HEIGHT_FACTOR).sp,
        textAlign = textAlign,
        shadow = shadow,
    )
}

/**
 * An app, folder or pair name under an icon: [appLabelTextStyle], up to two lines wrapped at word
 * boundaries (an ellipsis only after the second line), filling the width it is given. With
 * [reserveLines] (the default) the label always occupies the height of two lines, so a row of icons
 * stays aligned whether a name takes one line or two; pass false for a label in a row.
 *
 * Give the parent cell a horizontal padding of at most 1dp per side so the label is the cell width
 * minus 2dp.
 *
 * @param sizeSp base size in sp; defaults to the [LocalLabelSizeSp] chosen in Settings.
 * @param onWallpaper true when the text sits straight on the wallpaper (adds the soft shadow).
 * @param fillWidth true (default) fills the width it is given; false wraps to the text, up to the incoming maximum.
 */
@Composable
fun AppLabel(
    text: String,
    modifier: Modifier = Modifier,
    sizeSp: Float = LocalLabelSizeSp.current,
    color: Color = LocalFoldOsTokens.current.onSurface,
    onWallpaper: Boolean = true,
    textAlign: TextAlign = TextAlign.Center,
    reserveLines: Boolean = true,
    fillWidth: Boolean = true,
) {
    val density = LocalDensity.current
    val style = appLabelTextStyle(sizeSp = sizeSp, onWallpaper = onWallpaper, color = color, textAlign = textAlign)
    val reserved: Modifier = if (reserveLines) {
        val lineDp = with(density) { style.lineHeight.toDp() }
        Modifier.height(lineDp * AppLabelStyle.MAX_LINES + AppLabelStyle.HEIGHT_SLACK)
    } else {
        Modifier
    }
    Text(
        text = text,
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier).then(reserved),
        color = color,
        style = style,
        maxLines = AppLabelStyle.MAX_LINES,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Height below an icon for the label block of [settings] at the current font scale (zero when labels
 * are hidden): gap + two reserved lines. The home grid adds it to the icon size to size its rows.
 */
@Composable
fun appLabelBlockHeight(settings: LauncherSettings): Dp =
    AppLabelStyle.blockHeight(settings, LocalDensity.current.fontScale)
