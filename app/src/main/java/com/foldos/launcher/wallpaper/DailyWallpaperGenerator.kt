package com.foldos.launcher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the gradient wallpaper for one day. The picture depends only on the day number (days since
 * 1970-01-01, local time) and the size, so the same date always gives the same wallpaper and no
 * image has to be bundled or downloaded: three colors on a mostly vertical gradient, two or three
 * soft radial glows and a darkening toward the bottom so dock labels stay readable.
 */
object DailyWallpaperGenerator {

    /** Longest side the generator draws; larger requests are scaled down to keep memory modest. */
    const val MAX_SIDE_PX: Int = 2560

    /** The five colors a day's wallpaper is built from (ARGB). */
    class Palette(
        val top: Int,
        val middle: Int,
        val bottom: Int,
        val glowA: Int,
        val glowB: Int,
    )

    /** Deterministic seed for [epochDay]; spreads consecutive days so they do not look alike. */
    fun seedFor(epochDay: Long): Long = epochDay * -7046029254386353131L + 0x5DEECE66DL

    /** The colors for [epochDay]: two hues 50..160 degrees apart, dark enough for white icons. */
    fun paletteFor(epochDay: Long): Palette {
        val random = Random(seedFor(epochDay))
        val hueA = random.nextFloat() * 360f
        val hueB = (hueA + 50f + random.nextFloat() * 110f) % 360f
        val spread = ((hueB - hueA + 540f) % 360f) - 180f
        val hueMid = (hueA + spread / 2f + 360f) % 360f
        val top = hsv(hueA, 0.60f + random.nextFloat() * 0.25f, 0.25f + random.nextFloat() * 0.20f)
        val middle = hsv(hueMid, 0.60f + random.nextFloat() * 0.20f, 0.38f + random.nextFloat() * 0.20f)
        val bottom = hsv(hueB, 0.55f + random.nextFloat() * 0.25f, 0.45f + random.nextFloat() * 0.25f)
        val glowA = hsv(hueB, 0.50f + random.nextFloat() * 0.30f, 0.80f + random.nextFloat() * 0.20f, alpha = 120)
        val glowB = hsv(hueA, 0.45f + random.nextFloat() * 0.30f, 0.75f + random.nextFloat() * 0.25f, alpha = 100)
        return Palette(top, middle, bottom, glowA, glowB)
    }

    /** [width] x [height] scaled down (never up) so the longest side is at most [MAX_SIDE_PX]. */
    fun fitSize(width: Int, height: Int): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val longest = max(w, h)
        if (longest <= MAX_SIDE_PX) return w to h
        val scale = MAX_SIDE_PX.toFloat() / longest
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    /** Renders the wallpaper for [epochDay]. The caller owns (and should recycle) the bitmap. */
    fun generate(epochDay: Long, widthPx: Int, heightPx: Int): Bitmap {
        val (w, h) = fitSize(widthPx, heightPx)
        // Layout randomness uses its own stream so it never shifts the palette.
        val random = Random(seedFor(epochDay) xor 0x1F3D5B79L)
        val palette = paletteFor(epochDay)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Mostly vertical gradient line through the center, tilted 70..110 degrees from the x axis.
        val angle = Math.toRadians(70.0 + random.nextInt(40))
        val half = hypot(w.toDouble(), h.toDouble()) / 2.0
        val cx = w / 2f
        val cy = h / 2f
        val dx = (cos(angle) * half).toFloat()
        val dy = (sin(angle) * half).toFloat()
        paint.shader = LinearGradient(
            cx - dx, cy - dy, cx + dx, cy + dy,
            intArrayOf(palette.top, palette.middle, palette.bottom),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Soft glows.
        val glows = 2 + random.nextInt(2)
        val longest = max(w, h).toFloat()
        for (i in 0 until glows) {
            val color = if (i % 2 == 0) palette.glowA else palette.glowB
            val radius = longest * (0.45f + random.nextFloat() * 0.40f)
            val gx = random.nextFloat() * w
            val gy = random.nextFloat() * h
            paint.shader = RadialGradient(
                gx, gy, radius,
                intArrayOf(color, color and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }

        // Darker toward the bottom, where the dock sits.
        paint.shader = LinearGradient(
            0f, h * 0.55f, 0f, h.toFloat(),
            0x00000000, 0x66000000,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        return bitmap
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Int = 255): Int =
        Color.HSVToColor(
            alpha,
            floatArrayOf(
                hue.coerceIn(0f, 359.99f),
                min(saturation, 1f).coerceAtLeast(0f),
                min(value, 1f).coerceAtLeast(0f),
            ),
        )
}
