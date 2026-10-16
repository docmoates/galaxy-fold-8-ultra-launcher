package com.foldos.launcher.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.a11y.LocalLargeIcons
import com.foldos.launcher.a11y.a11yAppLabel
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yFocusTile
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.perf.IconBitmapCache
import com.foldos.launcher.perf.rememberAppIconBitmap
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * Process-wide cache of rendered app icons keyed by "package/activity@sizePx#generation". A thin
 * facade over [IconBitmapCache] (memory-bounded LRU, decoding off the main thread) that adds the
 * Compose-observable [generation].
 */
object HomeIconCache {

    /**
     * Bumped by [clear]; [rememberHomeAppIconPainter] reads it in composition and includes it in
     * its key, so every visible icon re-rasterises after an icon pack, shape or override change.
     */
    var generation: Int by mutableIntStateOf(0)
        private set

    fun get(key: String): ImageBitmap? = IconBitmapCache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        IconBitmapCache.put(key, bitmap)
    }

    /**
     * Drops every rendered icon. Call after the icon pack, icon shape or a per-app override
     * changes so the next composition re-rasterises through the (new) icon loader.
     */
    fun clear() {
        IconBitmapCache.clear()
        generation++
    }
}

/**
 * Loads an app icon off the main thread through [AppInfo.iconLoader] (via the shared
 * [IconBitmapCache]) and returns it as a [Painter]. Null while loading or on failure; a cached icon
 * is returned in the same composition.
 */
@Composable
fun rememberHomeAppIconPainter(app: AppInfo, sizePx: Int): Painter? {
    val bitmap = rememberAppIconBitmap(app, sizePx, HomeIconCache.generation)
    return remember(bitmap) { bitmap?.let { BitmapPainter(it) } }
}

/**
 * One app icon clipped to the theme's icon shape. Shows a surface-colored placeholder while loading.
 * With [badge] > 0 a notification badge in [badgeStyle] (`LauncherSettings.BADGE_*`) is drawn over
 * the top-right corner, outside the clipped icon.
 */
@Composable
fun HomeAppIcon(
    app: AppInfo,
    size: Dp,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    badgeStyle: String = LauncherSettings.BADGE_COUNT,
) {
    val tokens = LocalFoldOsTokens.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50)
    val shape = RoundedCornerShape(percent = percent)
    val painter = rememberHomeAppIconPainter(app, sizePx)
    HomeBadgedBox(count = badge, style = badgeStyle, modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(if (painter == null) tokens.surface else Color.Transparent),
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = app.label,
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/**
 * Icon plus optional label, centered in its cell. Tap launches; a long press calls [onLongPressAt]
 * with the tile's window bounds when given, else [onLongClick]. [badge] / [badgeStyle] draw the
 * notification badge, see [HomeAppIcon].
 *
 * Accessibility: the tile is one TalkBack element named "label" or "label, N notifications" with a
 * click action, the long press as "Open menu" and [a11yActions] in the actions menu; it is also
 * reachable by keyboard (Enter launches, the Menu key opens the long-press menu).
 */
@Composable
fun HomeAppTile(
    app: AppInfo,
    iconSize: Dp,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = LocalFoldOsTokens.current.onSurface,
    onLongClick: (() -> Unit)? = null,
    badge: Int = 0,
    badgeStyle: String = LauncherSettings.BADGE_COUNT,
    onLongPressAt: ((anchor: Rect) -> Unit)? = null,
    a11yActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val holder = remember { HomeAnchorHolder() }
    val longPress: (() -> Unit)? = onLongPressAt?.let { report -> { report(holder.bounds() ?: Rect.Zero) } } ?: onLongClick
    val large = LocalLargeIcons.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .a11yButton(
                label = a11yAppLabel(app.label, badge),
                onActivate = onClick,
                onMenu = longPress,
                customActions = a11yActions,
            )
            .a11yFocusTile(onActivate = onClick, onMenu = longPress)
            .homeAnchor(holder)
            .homeTapGestures(onTap = onClick, onLongPress = longPress)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeAppIcon(app = app, size = iconSize, badge = badge, badgeStyle = badgeStyle)
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium.copy(shadow = homeTextShadow()),
                fontSize = if (large) 15.sp else 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
