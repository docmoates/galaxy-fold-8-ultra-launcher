package com.foldos.launcher.items

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.home.rememberHomeAppIconPainter
import com.foldos.launcher.theme.FoldOsTokens
import com.foldos.launcher.theme.LocalFoldOsTokens

/*
 * Helpers shared by the folder / pair / context-menu composables of this package. Everything here
 * is internal; the public surface of the package is FolderIcon, FolderSheet, PairIcon, AppPairs,
 * PairEditorSheet, ItemContextMenu / ContextAction and Folders.
 */

/**
 * Looks an app up the way the home grid does: by "package/activity" key first, then any activity
 * of the package (the map is keyed by `AppInfo.key`).
 */
internal fun Map<String, AppInfo>.appFor(packageName: String?, activityName: String?): AppInfo? {
    val pkg = packageName ?: return null
    if (activityName != null) {
        this["$pkg/$activityName"]?.let { return it }
    }
    return values.firstOrNull { it.packageName == pkg }
}

/**
 * An [AppInfo] built from the package manager for an app that is not (or no longer) in the apps
 * list (a hidden app, an app without a launcher activity, a work-profile app): its application icon
 * and label. Null when the package is not installed at all.
 *
 * [distinctKey] gives the result an empty activity name (key `"package/"`) so its raw, unshaped
 * icon can never be cached under a real app's `package/activity` key in the icon bitmap cache.
 */
internal fun fallbackApp(
    context: Context,
    packageName: String,
    activityName: String?,
    distinctKey: Boolean = false,
): AppInfo? {
    val pm = context.packageManager
    val info = try {
        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
    } catch (e: PackageManager.NameNotFoundException) {
        return null
    }
    val label = try {
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        packageName
    }
    return AppInfo(
        packageName = packageName,
        activityName = if (distinctKey) "" else (activityName ?: ""),
        label = label,
        user = Process.myUserHandle(),
        iconLoader = { pm.getApplicationIcon(info) },
    )
}

/**
 * The apps a folder's preview draws: its children in folder order (`row`, then `col`), each looked up
 * in [apps] (package/activity key, then any activity of the package) and, when that finds nothing,
 * built from the package manager through [fallbackApp], so an installed child always has an icon.
 * Children whose package is not installed are skipped. At most [limit] apps.
 *
 * The package-manager fallback only runs once [apps] has loaded (is non-empty): before that every
 * child would miss the map and cost a main-thread binder call, and its raw icon would be drawn
 * instead of the themed one. Fallback apps carry a distinct key and are memoised per package.
 */
internal fun previewAppsOf(
    children: List<HomeItem>,
    apps: Map<String, AppInfo>,
    context: Context,
    limit: Int = Folders.PREVIEW_COUNT,
): List<AppInfo> =
    children
        .sortedWith(compareBy<HomeItem> { it.row }.thenBy { it.col })
        .asSequence()
        .mapNotNull { child ->
            apps.firstAppOf(child) ?: if (apps.isEmpty()) {
                null
            } else {
                child.packageName?.let { cachedFallbackApp(context, it, child.activityName) }
            }
        }
        .take(limit)
        .toList()

private val fallbackPreviewCache = java.util.concurrent.ConcurrentHashMap<String, AppInfo>()

/** [fallbackApp] (distinct key) memoised per package; a missing package is not cached, it may install later. */
private fun cachedFallbackApp(context: Context, packageName: String, activityName: String?): AppInfo? {
    fallbackPreviewCache[packageName]?.let { return it }
    // The cache is process-wide and each AppInfo's icon loader captures the package manager, so build
    // it from the application context: an Activity context would be kept alive after it is destroyed.
    val built = fallbackApp(context.applicationContext, packageName, activityName, distinctKey = true) ?: return null
    fallbackPreviewCache[packageName] = built
    return built
}

/** The app of an APP item, or app A of a PAIR item. */
internal fun Map<String, AppInfo>.firstAppOf(item: HomeItem): AppInfo? = appFor(item.packageName, item.activityName)

/** App B of a PAIR item; null for every other kind. */
internal fun Map<String, AppInfo>.secondAppOf(item: HomeItem): AppInfo? = appFor(item.packageName2, item.activityName2)

/** The theme's icon shape for an icon of [size] (mock-up `border-radius: calc(var(--is) * var(--icr))`). */
internal fun itemCornerShape(size: Dp, tokens: FoldOsTokens): RoundedCornerShape =
    RoundedCornerShape(size * tokens.iconCornerFraction.coerceIn(0f, 0.5f))

/** Soft shadow under labels drawn straight on the wallpaper (mock-up `--tsh`). */
internal fun itemTextShadow(): Shadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1f),
    blurRadius = 3f,
)

/** The title a PAIR item shows when it has none stored. */
internal fun pairTitle(item: HomeItem, a: AppInfo?, b: AppInfo?): String =
    item.title ?: when {
        a != null && b != null -> "${a.label} & ${b.label}"
        a != null -> a.label
        else -> "App pair"
    }

/**
 * One app icon rasterised through the home icon cache, clipped to [cornerFraction] of its size
 * (null = the theme's icon shape). A surface-colored square shows while the bitmap loads.
 */
@Composable
internal fun ItemAppIcon(
    app: AppInfo,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerFraction: Float? = null,
) {
    val tokens = LocalFoldOsTokens.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val fraction = (cornerFraction ?: tokens.iconCornerFraction).coerceIn(0f, 0.5f)
    val shape = RoundedCornerShape(size * fraction)
    val painter = rememberHomeAppIconPainter(app, sizePx)
    Box(
        modifier = modifier
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

/**
 * Places a pop-up card "near the icon": centred horizontally on the anchor (the layout node the
 * `Popup` is composed in, normally the item tile), below it when there is room, else above it,
 * else centred on the anchor; always kept [marginPx] inside the window. When the anchor is the
 * whole window (the pop-up was composed at the root) this degenerates to a centred card.
 */
internal class NearAnchorPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val maxX = (windowSize.width - w - marginPx).coerceAtLeast(marginPx)
        val maxY = (windowSize.height - h - marginPx).coerceAtLeast(marginPx)
        val centeredX = anchorBounds.left + (anchorBounds.width - w) / 2
        val x = centeredX.coerceIn(minOf(marginPx, maxX), maxX)
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - gapPx - h
        val y = when {
            below + h + marginPx <= windowSize.height -> below
            above >= marginPx -> above
            else -> anchorBounds.top + (anchorBounds.height - h) / 2
        }
        return IntOffset(x, y.coerceIn(minOf(marginPx, maxY), maxY))
    }
}
