package com.foldos.launcher.perf

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The one process-wide cache of rasterised app icons, shared by the home grid, the dock, the
 * drawer, folders, pairs and the pickers (`home.HomeIconCache` is a thin facade over it).
 *
 * - Keyed by app key + pixel size + icon generation ([key]); the generation is bumped by
 *   `HomeIconCache.clear()` whenever the icon pack, shape or a per-app override changes, so stale
 *   bitmaps are never served and old entries simply age out of the LRU.
 * - Bounded by memory rather than entry count: about 1/16 of the Java heap, between 4 and 32 MB.
 * - Decoding ([load]) always runs on `Dispatchers.IO`, at most [MAX_PARALLEL_DECODES] at a time, so a
 *   fast fling through the drawer cannot starve the thread pool or the UI thread; a decode that is
 *   still waiting for a permit is cancelled together with the composable that asked for it.
 */
object IconBitmapCache {

    /** Icon decodes allowed to run at the same time. */
    const val MAX_PARALLEL_DECODES: Int = 4

    private val maxKilobytes: Int =
        ((Runtime.getRuntime().maxMemory() / 1024L) / 16L).toInt().coerceIn(4 * 1024, 32 * 1024)

    private val cache: LruCache<String, ImageBitmap> = object : LruCache<String, ImageBitmap>(maxKilobytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * BYTES_PER_PIXEL) / 1024L).toInt().coerceAtLeast(1)
    }

    private val decodePermits = Semaphore(MAX_PARALLEL_DECODES)

    /** Cache key for [app] rendered at [sizePx] under icon generation [generation]. */
    fun key(app: AppInfo, sizePx: Int, generation: Int): String = "${app.key}@$sizePx#$generation"

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }

    /** Drops every bitmap (the caller bumps its generation so composables re-request them). */
    fun clear() {
        cache.evictAll()
    }

    /**
     * Returns the cached bitmap for [key] or rasterises [AppInfo.iconLoader] at [sizePx] x [sizePx]
     * off the main thread, stores it and returns it. Null when the drawable could not be rendered.
     */
    suspend fun load(app: AppInfo, sizePx: Int, key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        val size = sizePx.coerceAtLeast(1)
        val rendered: ImageBitmap? = withContext(Dispatchers.IO) {
            decodePermits.withPermit {
                // Another composable may have rendered the same icon while this one waited.
                val raced = cache.get(key)
                if (raced != null) {
                    raced
                } else {
                    runCatching {
                        app.iconLoader().toBitmap(size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
                    }.getOrNull()
                }
            }
        }
        if (rendered != null) cache.put(key, rendered)
        return rendered
    }

    private const val BYTES_PER_PIXEL: Long = 4L
}

/** Result of one [IconBitmapCache.load], tagged with the key it answered so stale results are ignored. */
private class LoadedIcon(
    val key: String,
    val bitmap: ImageBitmap?,
    val packageName: String,
    val activityName: String?,
)

/**
 * The rasterised icon of [app] at [sizePx] for icon generation [generation], or null while it loads
 * (or if it cannot be rendered).
 *
 * A cache hit is returned in the same composition (no placeholder frame); a miss is decoded on
 * `Dispatchers.IO` through [IconBitmapCache.load]. The result for an older key is never returned
 * after the app changed, so a recycled cell cannot flash the previous app's icon; when only the key
 * changes for the same app (icon pack, shape, size) the previous bitmap stays until the new one is ready.
 */
@Composable
fun rememberAppIconBitmap(app: AppInfo, sizePx: Int, generation: Int): ImageBitmap? {
    val packageName = app.packageName
    val activityName = app.activityName
    val key = remember(packageName, activityName, sizePx, generation) {
        IconBitmapCache.key(app, sizePx, generation)
    }
    val cached = remember(key) { IconBitmapCache.get(key) }
    var loaded by remember { mutableStateOf<LoadedIcon?>(null) }
    if (cached == null) {
        LaunchedEffect(key) {
            loaded = LoadedIcon(key, IconBitmapCache.load(app, sizePx, key), packageName, activityName)
        }
    }
    // While a new key (icon pack / shape / size change) loads, keep showing the previous bitmap of the
    // same app instead of dropping to the placeholder; a different app never inherits it.
    val lastShown = remember { arrayOfNulls<LoadedIcon>(1) }
    val exact = cached ?: loaded?.takeIf { it.key == key }?.bitmap
    if (exact != null) {
        if (lastShown[0]?.bitmap !== exact || lastShown[0]?.key != key) {
            lastShown[0] = LoadedIcon(key, exact, packageName, activityName)
        }
        return exact
    }
    return lastShown[0]?.takeIf { it.packageName == packageName && it.activityName == activityName }?.bitmap
}
