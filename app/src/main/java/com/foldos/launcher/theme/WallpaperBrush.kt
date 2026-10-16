package com.foldos.launcher.theme

import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One gradient color stop; [position] is 0..1 along the gradient. */
@Immutable
data class WallStop(val color: Color, val position: Float)

/**
 * One layer of a CSS-like multi-layer background. Layers are listed bottom-first in
 * [LayeredWallpaperBrush] (the reverse of CSS, where the first layer is on top).
 */
@Immutable
sealed class WallLayer {
    /** Flat fill. */
    data class Solid(val color: Color) : WallLayer()

    /**
     * CSS-style linear gradient. [angleDeg] follows CSS: 0 = towards top, 90 = towards right,
     * 180 = towards bottom. The gradient line spans the box like CSS does.
     */
    data class Linear(val angleDeg: Float, val stops: List<WallStop>) : WallLayer()

    /**
     * CSS-style elliptical radial gradient. [cx]/[cy] are the center as fractions of width/height;
     * [rx]/[ry] are the radii as fractions of width/height (CSS `radial-gradient(rx ry at cx cy, ...)`).
     */
    data class Radial(
        val cx: Float,
        val cy: Float,
        val rx: Float,
        val ry: Float,
        val stops: List<WallStop>,
    ) : WallLayer()

    /** Thin repeating grid lines (Bold theme). [cells] is roughly how many cells span the shorter side. */
    data class GridLines(val color: Color, val cells: Int = 12) : WallLayer()
}

/**
 * A [ShaderBrush] that composites several gradient layers (bottom-first) into one shader using
 * [ComposeShader], so a multi-layer CSS background can be carried around as a single Compose [Brush]
 * and drawn with `Modifier.background(brush)` or `drawRect(brush)`.
 */
@Immutable
data class LayeredWallpaperBrush(val layers: List<WallLayer>) : ShaderBrush() {

    override fun createShader(size: Size): Shader {
        val w = max(size.width, 1f)
        val h = max(size.height, 1f)
        var result: Shader? = null
        for (layer in layers) {
            val next = layer.toShader(w, h)
            result = if (result == null) next else ComposeShader(result, next, PorterDuff.Mode.SRC_OVER)
        }
        return result ?: solidShader(Color.Black, w, h)
    }
}

/** Convenience: a two-stop fade from [color] at 0 to fully transparent at [fadeAt] (CSS `color 0%, transparent N%`). */
internal fun fade(color: Color, fadeAt: Float): List<WallStop> =
    listOf(WallStop(color, 0f), WallStop(color.copy(alpha = 0f), fadeAt))

/** Convenience: a hard-edged fill (CSS `color N%, transparent N+1%`), used for One UI's hills. */
internal fun hardEdge(color: Color, edge: Float): List<WallStop> =
    listOf(
        WallStop(color, 0f),
        WallStop(color, edge),
        WallStop(color.copy(alpha = 0f), min(edge + 0.01f, 1f)),
        WallStop(color.copy(alpha = 0f), 1f),
    )

private fun solidShader(color: Color, w: Float, h: Float): Shader {
    val c = color.toArgb()
    return LinearGradient(0f, 0f, w, h, intArrayOf(c, c), null, Shader.TileMode.CLAMP)
}

/** Android gradients need at least two stops; pad a 0/1-stop list (empty -> fully transparent). */
private fun List<WallStop>.atLeastTwo(): List<WallStop> = when (size) {
    0 -> listOf(WallStop(Color.Transparent, 0f), WallStop(Color.Transparent, 1f))
    1 -> listOf(this[0], this[0].copy(position = 1f))
    else -> this
}

private fun List<WallStop>.colorsArray(): IntArray = IntArray(size) { this[it].color.toArgb() }

private fun List<WallStop>.positionsArray(): FloatArray = FloatArray(size) { this[it].position.coerceIn(0f, 1f) }

private fun WallLayer.toShader(w: Float, h: Float): Shader = when (this) {
    is WallLayer.Solid -> solidShader(color, w, h)

    is WallLayer.Linear -> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        // CSS gradient line length: |w sin a| + |h cos a|, centered in the box.
        val len = abs(w * sin(rad)).toFloat() + abs(h * cos(rad)).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val safe = stops.atLeastTwo()
        LinearGradient(
            cx - dx * len / 2f, cy - dy * len / 2f,
            cx + dx * len / 2f, cy + dy * len / 2f,
            safe.colorsArray(), safe.positionsArray(), Shader.TileMode.CLAMP,
        )
    }

    is WallLayer.Radial -> {
        val cxp = cx * w
        val cyp = cy * h
        val rxp = max(rx * w, 1f)
        val ryp = max(ry * h, 1f)
        val safe = stops.atLeastTwo()
        RadialGradient(cxp, cyp, rxp, safe.colorsArray(), safe.positionsArray(), Shader.TileMode.CLAMP).apply {
            // Stretch the circle into the CSS ellipse.
            setLocalMatrix(Matrix().apply { setScale(1f, ryp / rxp, cxp, cyp) })
        }
    }

    is WallLayer.GridLines -> {
        val cell = max(min(w, h) / max(cells, 1).toFloat(), 4f)
        val t = (1.5f / cell).coerceIn(0.01f, 0.5f)
        val c = color.toArgb()
        val clear = color.copy(alpha = 0f).toArgb()
        val colors = intArrayOf(c, c, clear, clear)
        val positions = floatArrayOf(0f, t, t, 1f)
        val horizontalLines = LinearGradient(0f, 0f, 0f, cell, colors, positions, Shader.TileMode.REPEAT)
        val verticalLines = LinearGradient(0f, 0f, cell, 0f, colors, positions, Shader.TileMode.REPEAT)
        ComposeShader(horizontalLines, verticalLines, PorterDuff.Mode.SRC_OVER)
    }
}
