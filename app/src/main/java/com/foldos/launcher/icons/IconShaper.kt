package com.foldos.launcher.icons

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.repo.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** Mask geometry for every [IconShape]. Paths are in pixels for a `size` x `size` square. */
object IconShapes {

    /** Human-readable name for Settings. */
    fun displayName(shape: IconShape): String = when (shape) {
        IconShape.SYSTEM -> "System"
        IconShape.CIRCLE -> "Circle"
        IconShape.SQUIRCLE -> "Squircle"
        IconShape.ROUNDED -> "Rounded"
        IconShape.TEARDROP -> "Teardrop"
    }

    /** One-line description for Settings. */
    fun description(shape: IconShape): String = when (shape) {
        IconShape.SYSTEM -> "The device's own icon shape"
        IconShape.CIRCLE -> "Round icons"
        IconShape.SQUIRCLE -> "Superellipse, between a circle and a square"
        IconShape.ROUNDED -> "Square with 22% corners"
        IconShape.TEARDROP -> "Round with one sharper corner"
    }

    /** The mask for [shape], or null for [IconShape.SYSTEM] (no masking). */
    fun maskPath(shape: IconShape, size: Float): Path? {
        if (size <= 0f) return null
        val path = Path()
        when (shape) {
            IconShape.SYSTEM -> return null
            IconShape.CIRCLE -> path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
            IconShape.SQUIRCLE -> addSuperellipse(path, size, SQUIRCLE_EXPONENT)
            IconShape.ROUNDED -> {
                val r = size * ROUNDED_CORNER_FRACTION
                path.addRoundRect(RectF(0f, 0f, size, size), r, r, Path.Direction.CW)
            }
            IconShape.TEARDROP -> {
                val big = size / 2f
                val small = size * TEARDROP_SMALL_CORNER_FRACTION
                // top-left, top-right, bottom-right, bottom-left (x, y pairs)
                val radii = floatArrayOf(big, big, big, big, small, small, big, big)
                path.addRoundRect(RectF(0f, 0f, size, size), radii, Path.Direction.CW)
            }
        }
        path.close()
        return path
    }

    /** |x/r|^n + |y/r|^n = 1, sampled around the circle. n = 5 matches the Pixel squircle closely. */
    private fun addSuperellipse(path: Path, size: Float, exponent: Float) {
        val r = size / 2f
        val cx = r
        val cy = r
        val power = 2f / exponent
        val steps = SUPERELLIPSE_SEGMENTS
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (2.0 * Math.PI)
            val c = cos(t)
            val s = sin(t)
            val x = cx + r * sign(c).toFloat() * abs(c).pow(power.toDouble()).toFloat()
            val y = cy + r * sign(s).toFloat() * abs(s).pow(power.toDouble()).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private const val SQUIRCLE_EXPONENT = 5f
    private const val ROUNDED_CORNER_FRACTION = 0.22f
    private const val TEARDROP_SMALL_CORNER_FRACTION = 0.15f
    private const val SUPERELLIPSE_SEGMENTS = 128
}

/**
 * Rasterises app icons into a chosen [IconShape] (adaptive icons: background + foreground layers
 * drawn at their full 108/72 canvas and clipped; legacy icons: normalised onto a tinted plate), and
 * composes uncovered icons with an icon pack's back / mask / upon layers.
 */
class IconShaper(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Pixel size of rendered icons: the system's large launcher icon size (e.g. 192 px at xxhdpi). */
    val iconSizePx: Int = run {
        val am = appContext.getSystemService(ActivityManager::class.java)
        val size = am?.launcherLargeIconSize ?: 0
        if (size > 0) size else (appContext.resources.displayMetrics.density * 72f).roundToInt().coerceAtLeast(48)
    }

    /** [raw] masked to [shape]; returns [raw] itself for [IconShape.SYSTEM]. */
    fun shape(raw: Drawable, shape: IconShape, sizePx: Int = iconSizePx): Drawable {
        if (shape == IconShape.SYSTEM) return raw
        val size = sizePx.coerceAtLeast(1)
        val path = IconShapes.maskPath(shape, size.toFloat()) ?: return raw
        val content = normalized(raw, size)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        return BitmapDrawable(appContext.resources, out)
    }

    /**
     * Composes [raw] the way the pack asks for icons it does not cover: iconback (picked by [seed]
     * when the pack ships several), the icon scaled by the pack's factor, iconmask punched out,
     * iconupon on top. Null when the pack defines none of these layers.
     */
    fun composeWithPack(raw: Drawable, pack: IconPackData, loader: IconPackLoader, seed: Int, sizePx: Int = iconSizePx): Drawable? {
        if (!pack.composesUncovered) return null
        val size = sizePx.coerceAtLeast(1)
        val back = pick(pack.iconBacks, seed)?.let { loader.drawable(pack.packageName, it) }
        val mask = pick(pack.iconMasks, seed)?.let { loader.drawable(pack.packageName, it) }
        val upon = pick(pack.iconUpons, seed)?.let { loader.drawable(pack.packageName, it) }
        if (back == null && mask == null && upon == null) return null

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // The icon on its own layer so the mask only cuts the icon, never the back.
        val iconLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        run {
            val canvas = Canvas(iconLayer)
            val icon = raw.toBitmap(size, size, Bitmap.Config.ARGB_8888)
            val scaled = (size * pack.scale).coerceIn(1f, size.toFloat())
            val offset = (size - scaled) / 2f
            canvas.drawBitmap(icon, null, RectF(offset, offset, offset + scaled, offset + scaled), bitmapPaint)
            if (mask != null) {
                val maskBitmap = mask.toBitmap(size, size, Bitmap.Config.ARGB_8888)
                val cut = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                cut.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                canvas.drawBitmap(maskBitmap, 0f, 0f, cut)
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (back != null) canvas.drawBitmap(back.toBitmap(size, size, Bitmap.Config.ARGB_8888), 0f, 0f, bitmapPaint)
        canvas.drawBitmap(iconLayer, 0f, 0f, bitmapPaint)
        if (upon != null) canvas.drawBitmap(upon.toBitmap(size, size, Bitmap.Config.ARGB_8888), 0f, 0f, bitmapPaint)
        return BitmapDrawable(appContext.resources, out)
    }

    private fun pick(names: List<String>, seed: Int): String? {
        if (names.isEmpty()) return null
        val index = (seed % names.size + names.size) % names.size
        return names[index]
    }

    /**
     * A `size` x `size` bitmap holding the icon's content edge to edge, as it would appear under a
     * full-bleed mask: adaptive layers drawn at 1.5x and centred (the 108dp canvas around the 72dp
     * safe zone); legacy icons either kept as-is when they already fill their square, or shrunk onto a
     * plate tinted from their dominant colour.
     */
    private fun normalized(raw: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (raw is AdaptiveIconDrawable) {
            val inset = (size * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
            val bounds = Rect(-inset, -inset, size + inset, size + inset)
            val background: Drawable? = raw.background
            val foreground: Drawable? = raw.foreground
            if (background != null) {
                background.bounds = bounds
                background.draw(canvas)
            }
            if (foreground != null) {
                foreground.bounds = bounds
                foreground.draw(canvas)
            }
            if (background == null && foreground == null) {
                raw.bounds = Rect(0, 0, size, size)
                raw.draw(canvas)
            }
            return bitmap
        }

        val legacy = raw.toBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (fillsCorners(legacy)) {
            canvas.drawBitmap(legacy, 0f, 0f, paint)
            return bitmap
        }
        canvas.drawColor(plateColor(legacy))
        val inner = (size * LEGACY_SCALE).roundToInt().coerceAtLeast(1)
        val offset = (size - inner) / 2f
        canvas.drawBitmap(legacy, null, RectF(offset, offset, offset + inner, offset + inner), paint)
        return bitmap
    }

    /** True when all four corners (2 px in) are opaque, i.e. the legacy icon is already a full square. */
    private fun fillsCorners(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 6 || h < 6) return false
        val inset = 2
        val corners = intArrayOf(
            bitmap.getPixel(inset, inset),
            bitmap.getPixel(w - 1 - inset, inset),
            bitmap.getPixel(inset, h - 1 - inset),
            bitmap.getPixel(w - 1 - inset, h - 1 - inset),
        )
        return corners.all { Color.alpha(it) >= OPAQUE_THRESHOLD }
    }

    /** Average opaque colour of a downscaled copy, lifted towards white so the icon stays readable on it. */
    private fun plateColor(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val c = small.getPixel(x, y)
                if (Color.alpha(c) < 128) continue
                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                n++
            }
        }
        if (small !== bitmap) small.recycle()
        if (n == 0L) return DEFAULT_PLATE
        val avg = Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        return ColorUtils.blendARGB(avg, Color.WHITE, PLATE_LIGHTEN)
    }

    private companion object {
        const val LEGACY_SCALE = 0.72f
        const val OPAQUE_THRESHOLD = 200
        const val SAMPLE_SIZE = 8
        const val PLATE_LIGHTEN = 0.62f
        const val DEFAULT_PLATE = 0xFFEFEFEF.toInt()
    }
}
