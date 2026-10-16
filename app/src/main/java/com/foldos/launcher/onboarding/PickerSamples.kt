package com.foldos.launcher.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.ColorFamily
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.ColorSchemes
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenMode
import com.foldos.launcher.core.model.ThemeId
import com.foldos.launcher.core.repo.IconShape
import com.foldos.launcher.core.ui.LocalDragController
import com.foldos.launcher.core.ui.rememberDragController
import com.foldos.launcher.theme.FoldOsTheme
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.tokensFor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/*
 * Sample drawings shared by the setup wizard and Settings (Fix round 1.1: Settings is "pick a sample",
 * so both screens render the same previews). Everything here is pure drawing: colors come in as
 * parameters, so the pieces work inside `FoldOsTheme` (wizard) and inside the plain Material window
 * of Settings.
 */

/* ------------------------------------------------------------------------------------------ */
/* Color swatches                                                                              */
/* ------------------------------------------------------------------------------------------ */

/**
 * A rounded square split along its anti-diagonal: the upper-left half is the LIGHT version (pastel
 * wallpaper gradient, deep accent dot), the lower-right half the DARK version (deep wallpaper
 * gradient, light accent dot).
 */
@Composable
internal fun SplitColorSwatch(
    lightWall1: Color,
    lightWall2: Color,
    lightAccent: Color,
    darkWall1: Color,
    darkWall2: Color,
    darkAccent: Color,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 56.dp,
    corner: Dp = 14.dp,
) {
    Canvas(
        modifier = modifier
            .size(swatchSize)
            .clip(RoundedCornerShape(corner)),
    ) {
        val w = this.size.width
        val h = this.size.height
        drawRect(brush = Brush.linearGradient(listOf(lightWall1, lightWall2)))
        val darkHalf = Path().apply {
            moveTo(w, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path = darkHalf, brush = Brush.linearGradient(listOf(darkWall1, darkWall2)))
        drawCircle(color = lightAccent, radius = w * 0.14f, center = Offset(w * 0.30f, h * 0.30f))
        drawCircle(color = darkAccent, radius = w * 0.14f, center = Offset(w * 0.70f, h * 0.70f))
    }
}

/** [SplitColorSwatch] for one primary color: light and dark versions of its three colors. */
@Composable
internal fun SplitColorSwatch(
    family: ColorFamily,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 56.dp,
    corner: Dp = 14.dp,
) {
    SplitColorSwatch(
        lightWall1 = Color(family.lightWall1),
        lightWall2 = Color(family.lightWall2),
        lightAccent = Color(family.lightAccent),
        darkWall1 = Color(family.darkWall1),
        darkWall2 = Color(family.darkWall2),
        darkAccent = Color(family.darkAccent),
        modifier = modifier,
        swatchSize = swatchSize,
        corner = corner,
    )
}

/** The "Theme colors" tile: the theme's own light and dark accents on a plain light / dark ground. */
@Composable
internal fun ThemeColorsSwatch(
    theme: ThemeId,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 56.dp,
    corner: Dp = 14.dp,
) {
    val light = remember(theme) { tokensFor(theme, false, ColorSchemeConfig(), null) }
    val dark = remember(theme) { tokensFor(theme, true, ColorSchemeConfig(), null) }
    val lightGround = Color(0xFFEEF0F5)
    val darkGround = Color(0xFF0C0D12)
    SplitColorSwatch(
        lightWall1 = lightGround,
        lightWall2 = light.accent.copy(alpha = 0.35f).compositeOver(lightGround),
        lightAccent = light.accent,
        darkWall1 = darkGround,
        darkWall2 = dark.accent.copy(alpha = 0.35f).compositeOver(darkGround),
        darkAccent = dark.accent,
        modifier = modifier,
        swatchSize = swatchSize,
        corner = corner,
    )
}

/** The "Custom" tile: a color wheel. */
@Composable
internal fun CustomColorSwatch(
    modifier: Modifier = Modifier,
    swatchSize: Dp = 56.dp,
    corner: Dp = 14.dp,
) {
    val wheel = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFF43F5E), Color(0xFFF59E0B), Color(0xFF22C55E),
                Color(0xFF0EA5E9), Color(0xFF8B5CF6), Color(0xFFF43F5E),
            ),
        )
    }
    Box(
        modifier = modifier
            .size(swatchSize)
            .clip(RoundedCornerShape(corner))
            .background(wheel),
    )
}

/* ------------------------------------------------------------------------------------------ */
/* Appearance (dark / light / auto)                                                            */
/* ------------------------------------------------------------------------------------------ */

/**
 * A live cover-home preview for an Appearance choice: dark, light, or (auto) a diagonal split of both.
 * [base] supplies theme, colors, style and dock; only the appearance differs. Give it a size with [modifier].
 */
@Composable
internal fun AppearanceSample(
    appearance: Appearance,
    base: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    when (appearance) {
        Appearance.DARK, Appearance.LIGHT -> SampleHomePreview(
            style = base.styleCover,
            theme = base.theme,
            colors = base.colors,
            modifier = modifier,
            base = base.copy(appearance = appearance),
        )
        Appearance.AUTO -> Box(modifier = modifier) {
            SampleHomePreview(
                style = base.styleCover,
                theme = base.theme,
                colors = base.colors,
                modifier = Modifier.fillMaxSize(),
                base = base.copy(appearance = Appearance.DARK),
            )
            Box(modifier = Modifier.fillMaxSize().clip(DiagonalRightShape)) {
                SampleHomePreview(
                    style = base.styleCover,
                    theme = base.theme,
                    colors = base.colors,
                    modifier = Modifier.fillMaxSize(),
                    base = base.copy(appearance = Appearance.LIGHT),
                )
            }
        }
    }
}

/** The right-hand side of a box, cut along a steep diagonal (used by the "auto" appearance sample). */
private object DiagonalRightShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(size.width * 0.62f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width * 0.38f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Screens: cover / inner diagrams                                                              */
/* ------------------------------------------------------------------------------------------ */

/**
 * A small diagram of the cover and inner screens for a [ScreenMode]: Same (one layout, scaled),
 * Different (a tall grid outside, widgets inside) or Continuity (the open page grows when unfolding).
 * [ink] draws outlines, [panel] fills the screens and [accent] the tiles.
 */
@Composable
internal fun ScreenModeDiagram(
    mode: ScreenMode,
    ink: Color,
    panel: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(120f / 78f)) {
        val u = this.size.width / 120f
        val stroke = Stroke(width = 1.6f * u)

        fun frame(x: Float, y: Float, w: Float, h: Float) {
            val topLeft = Offset(x * u, y * u)
            val sz = Size(w * u, h * u)
            drawRoundRect(color = panel, topLeft = topLeft, size = sz, cornerRadius = CornerRadius(7f * u))
            drawRoundRect(color = ink, topLeft = topLeft, size = sz, cornerRadius = CornerRadius(7f * u), style = stroke)
        }

        fun tiles(x: Float, y: Float, cols: Int, rows: Int, cell: Float, gap: Float, alpha: Float = 1f) {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    drawRoundRect(
                        color = accent.copy(alpha = alpha),
                        topLeft = Offset((x + c * (cell + gap)) * u, (y + r * (cell + gap)) * u),
                        size = Size(cell * u, cell * u),
                        cornerRadius = CornerRadius(cell * 0.3f * u),
                    )
                }
            }
        }

        fun block(x: Float, y: Float, w: Float, h: Float, alpha: Float) {
            drawRoundRect(
                color = accent.copy(alpha = alpha),
                topLeft = Offset(x * u, y * u),
                size = Size(w * u, h * u),
                cornerRadius = CornerRadius(3f * u),
            )
        }

        // Cover screen (tall, narrow) on the left, inner screen (nearly square) on the right.
        frame(8f, 6f, 38f, 66f)
        frame(62f, 12f, 52f, 54f)
        when (mode) {
            ScreenMode.SAME -> {
                tiles(14f, 16f, cols = 2, rows = 3, cell = 10f, gap = 4f)
                tiles(69f, 20f, cols = 3, rows = 3, cell = 10f, gap = 5f)
                // "=" between the screens.
                drawLine(ink, Offset(50f * u, 36f * u), Offset(58f * u, 36f * u), strokeWidth = 1.8f * u)
                drawLine(ink, Offset(50f * u, 42f * u), Offset(58f * u, 42f * u), strokeWidth = 1.8f * u)
            }
            ScreenMode.DIFFERENT -> {
                tiles(14f, 16f, cols = 2, rows = 3, cell = 10f, gap = 4f)
                block(68f, 18f, 40f, 16f, 0.95f)
                block(68f, 38f, 18f, 20f, 0.6f)
                block(90f, 38f, 18f, 20f, 0.6f)
                // "!=" between the screens.
                drawLine(ink, Offset(50f * u, 34f * u), Offset(58f * u, 44f * u), strokeWidth = 1.8f * u)
            }
            ScreenMode.CONTINUITY -> {
                tiles(14f, 16f, cols = 2, rows = 3, cell = 10f, gap = 4f)
                // The same page, opened up: the first two columns are the cover page, the rest is new space.
                tiles(69f, 20f, cols = 2, rows = 3, cell = 10f, gap = 5f)
                tiles(99f, 20f, cols = 1, rows = 3, cell = 10f, gap = 5f, alpha = 0.4f)
                // Arrow from the cover to the inner screen.
                drawLine(ink, Offset(48f * u, 39f * u), Offset(60f * u, 39f * u), strokeWidth = 1.8f * u)
                drawLine(ink, Offset(55f * u, 34f * u), Offset(60f * u, 39f * u), strokeWidth = 1.8f * u)
                drawLine(ink, Offset(55f * u, 44f * u), Offset(60f * u, 39f * u), strokeWidth = 1.8f * u)
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* App drawer: pop-up sheet / full screen                                                       */
/* ------------------------------------------------------------------------------------------ */

/**
 * A live cover-home preview with the app drawer open in the given style: a pop-up sheet over the
 * dimmed home, or the full-screen drawer. Both show the group chips and a grid of apps.
 */
@Composable
internal fun DrawerSample(
    popup: Boolean,
    base: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    val candidate = remember(base, popup) {
        base.copy(drawerStyle = if (popup) DrawerStyle.POPUP else DrawerStyle.FULL)
    }
    ScaledPreview(
        virtualWidth = PREVIEW_COVER_WIDTH,
        virtualHeight = PREVIEW_COVER_HEIGHT,
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalDragController provides rememberDragController()) {
            FoldOsTheme(settings = candidate) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SampleCoverHome(settings = candidate)
                    DrawerSheetSample(popup = popup)
                }
            }
        }
    }
}

@Composable
private fun DrawerSheetSample(popup: Boolean) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier = Modifier.fillMaxSize()) {
        if (popup) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
        }
        val sheetModifier = if (popup) {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
        } else {
            Modifier.fillMaxSize()
        }
        Column(
            modifier = sheetModifier
                .background(tokens.drawerScrim)
                .padding(start = 18.dp, end = 18.dp, top = if (popup) 14.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (popup) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(tokens.onSurface.copy(alpha = 0.3f)),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(tokens.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "Search apps",
                    modifier = Modifier.padding(start = 20.dp),
                    color = tokens.onSurface.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Work", "Social", "Media").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (index == 0) tokens.accent else tokens.onSurface.copy(alpha = 0.1f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (index == 0) tokens.onAccent else tokens.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            SampleDrawerGrid(rows = 5)
        }
    }
}

@Composable
private fun SampleDrawerGrid(rows: Int) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 4) {
                    val spec = SampleApps.specs[(row * 4 + col) % SampleApps.specs.size]
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(shape)
                                .background(Color(spec.color)),
                        )
                        Text(
                            text = spec.label,
                            color = tokens.onSurface,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Icon shapes                                                                                  */
/* ------------------------------------------------------------------------------------------ */

/** A superellipse (n = 4) outline, the "squircle". */
private val SquircleShape: Shape = GenericShape { size, _ ->
    val rx = size.width / 2f
    val ry = size.height / 2f
    val steps = 72
    for (i in 0..steps) {
        val t = i.toDouble() / steps * 2.0 * PI
        val c = cos(t)
        val s = sin(t)
        val x = rx + rx * (sign(c) * abs(c).pow(0.5)).toFloat()
        val y = ry + ry * (sign(s) * abs(s).pow(0.5)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The Compose shape that draws an [IconShape] as a swatch (the system mask is shown as a soft square). */
internal fun iconShapeOf(shape: IconShape): Shape = when (shape) {
    IconShape.SYSTEM -> RoundedCornerShape(percent = 28)
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.ROUNDED -> RoundedCornerShape(percent = 22)
    IconShape.TEARDROP -> RoundedCornerShape(
        topStartPercent = 50,
        topEndPercent = 50,
        bottomEndPercent = 50,
        bottomStartPercent = 8,
    )
}

/** An icon-shaped swatch filled with an app-like gradient. */
@Composable
internal fun IconShapeSwatch(
    shape: IconShape,
    color: Color,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 52.dp,
) {
    val light = remember(color) {
        Color(
            red = color.red + (1f - color.red) * 0.3f,
            green = color.green + (1f - color.green) * 0.3f,
            blue = color.blue + (1f - color.blue) * 0.3f,
        )
    }
    Box(
        modifier = modifier
            .size(swatchSize)
            .clip(iconShapeOf(shape))
            .background(Brush.linearGradient(listOf(light, color))),
    )
}

/* ------------------------------------------------------------------------------------------ */
/* Widget picker grouping                                                                       */
/* ------------------------------------------------------------------------------------------ */

/** Which grouping a [WidgetGroupingSample] draws. */
internal enum class GroupingKind { CATEGORY, APP, RECENT }

/** A tiny picture of the widget picker's list for one grouping: headers with rows of widget thumbnails. */
@Composable
internal fun WidgetGroupingSample(
    kind: GroupingKind,
    ink: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val headers = when (kind) {
        GroupingKind.CATEGORY -> listOf("Clock & Time", "Weather", "Calendar")
        GroupingKind.APP -> listOf("Google", "Spotify", "Samsung")
        GroupingKind.RECENT -> listOf("Recently used", "Clock & Time", "Weather")
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        headers.forEachIndexed { index, title ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (kind == GroupingKind.APP) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                    }
                    Text(
                        text = title,
                        color = ink.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val highlight = kind == GroupingKind.RECENT && index == 0
                    repeat(if (index == 0) 3 else 2) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(if (index == 0) 16.dp else 12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent.copy(alpha = if (highlight) 0.55f else 0.3f)),
                        )
                    }
                    if (index != 0) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Applying a color choice                                                                      */
/* ------------------------------------------------------------------------------------------ */

/**
 * [current] with a primary color picked: the scheme id of [family] (its light / dark version follows the
 * Appearance by itself) and the family's [isDark] values copied into the custom fields, so a later
 * "Custom" starts from the color the user was looking at.
 */
internal fun colorsForFamily(current: ColorSchemeConfig, family: ColorFamily, isDark: Boolean): ColorSchemeConfig {
    val version = family.forMode(isDark)
    return current.copy(
        scheme = family.schemeId,
        accent = version.accent,
        wall1 = version.wall1,
        wall2 = version.wall2,
    )
}

/**
 * [current] switched to "custom": when it was a primary color, its [isDark] version seeds the three custom
 * colors; when it was "theme colors" the custom fields are kept.
 */
internal fun colorsForCustom(current: ColorSchemeConfig, isDark: Boolean): ColorSchemeConfig {
    val version = ColorSchemes.familyOfScheme(current.scheme)?.forMode(isDark)
    return if (version != null) {
        current.copy(
            scheme = ColorSchemeConfig.SCHEME_CUSTOM,
            accent = version.accent,
            wall1 = version.wall1,
            wall2 = version.wall2,
        )
    } else {
        current.copy(scheme = ColorSchemeConfig.SCHEME_CUSTOM)
    }
}
