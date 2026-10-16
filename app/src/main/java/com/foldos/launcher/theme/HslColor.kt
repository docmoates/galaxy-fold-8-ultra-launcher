package com.foldos.launcher.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * HSL triple in the mock-up's units: [h] in degrees (0..360), [s] and [l] in percent (0..100).
 * Values are rounded to whole numbers exactly like `hexHsl()` in the mock-up so derived colors match.
 */
internal data class Hsl(val h: Float, val s: Float, val l: Float)

/** Port of the mock-up's `hexHsl(hex)`; alpha is ignored. */
internal fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    var h = 0f
    var s = 0f
    val l = (mx + mn) / 2f
    if (mx != mn) {
        val d = mx - mn
        s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        h = when (mx) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        h *= 60f
    }
    return Hsl(
        h = h.roundToInt().toFloat(),
        s = (s * 100f).roundToInt().toFloat(),
        l = (l * 100f).roundToInt().toFloat(),
    )
}

/**
 * Port of the mock-up's `hsl(h, s, l, a)`: [h] in degrees, [s] and [l] in percent, [alpha] 0..1.
 * Out-of-range inputs are clamped.
 */
internal fun hsl(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = (s / 100f).coerceIn(0f, 1f)
    val lig = (l / 100f).coerceIn(0f, 1f)
    val c = (1f - abs(2f * lig - 1f)) * sat
    val hp = hue / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lig - c / 2f
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/** `Color(r, g, b)` with a fractional alpha; mirrors CSS `rgba(r, g, b, a)`. */
internal fun rgba(r: Int, g: Int, b: Int, a: Float): Color = Color(r, g, b).copy(alpha = a)
