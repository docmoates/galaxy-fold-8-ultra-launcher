package com.foldos.launcher.dock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.home.HomeIconCache
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The subset of theme tokens the dock needs, copied out of [LocalFoldOsTokens] once per
 * composition so the building blocks below do not depend on the token type directly.
 */
internal data class DockPalette(
    val surface: Color,
    val surfaceBorder: Color,
    val onSurface: Color,
    val accent: Color,
    val onAccent: Color,
    val radius: Dp,
    val iconCornerFraction: Float,
    /** Sheet background (mock-up `--drawer`) for pop-ups, callouts and the center button ring. */
    val drawer: Color,
)

@Composable
internal fun dockPalette(): DockPalette {
    val t = LocalFoldOsTokens.current
    return DockPalette(
        surface = t.surface,
        surfaceBorder = t.surfaceBorder,
        onSurface = t.onSurface,
        accent = t.accent,
        onAccent = t.onAccent,
        radius = t.radius,
        iconCornerFraction = t.iconCornerFraction,
        drawer = t.drawerScrim,
    )
}

/** Mock-up `.surf`: translucent surface with a hairline border. */
internal fun Modifier.dockSurface(palette: DockPalette, shape: Shape): Modifier =
    this.background(palette.surface, shape).border(1.dp, palette.surfaceBorder, shape)

/** Mock-up `--tsh`: a soft drop shadow for text drawn straight on the wallpaper. */
internal fun textShadow(): Shadow =
    Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1f), blurRadius = 6f)

/* ------------------------------------------------------------------------------------------ */
/* Icons and labels                                                                            */
/* ------------------------------------------------------------------------------------------ */

/**
 * One app icon, loaded lazily off the main thread through [AppInfo.iconLoader] and painted as an
 * [ImageBitmap]. While loading (or if loading fails) a tinted placeholder of the same shape shows.
 */
@Composable
internal fun AppIcon(
    app: AppInfo,
    size: Dp,
    palette: DockPalette,
    modifier: Modifier = Modifier,
    cornerFraction: Float = palette.iconCornerFraction,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    // HomeIconCache.generation is bumped after an icon pack / shape / override change, so the
    // always-visible dock re-rasterises together with the home grid.
    // It shares the home grid's icon cache (same key format) so the pack / shape work is done once.
    val cacheKey = "${app.key}@$sizePx#${HomeIconCache.generation}"
    val bitmap by produceState<ImageBitmap?>(HomeIconCache.get(cacheKey), cacheKey) {
        val hit = HomeIconCache.get(cacheKey)
        if (hit != null) {
            value = hit
            return@produceState
        }
        val rendered = withContext(Dispatchers.Default) {
            runCatching { app.iconLoader().toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
        }
        if (rendered != null) HomeIconCache.put(cacheKey, rendered)
        value = rendered
    }
    val shape = RoundedCornerShape(size * cornerFraction.coerceIn(0f, 0.5f))
    Box(modifier = modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        val painted = bitmap
        if (painted != null) {
            Image(bitmap = painted, contentDescription = app.label, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(palette.accent.copy(alpha = 0.35f)))
        }
    }
}

/** Mock-up `.al`: a single-line label under a dock icon. */
@Composable
internal fun DockLabel(
    text: String,
    palette: DockPalette,
    shadow: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.widthIn(max = 76.dp),
        color = palette.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = TextStyle(
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            shadow = if (shadow) textShadow() else null,
        ),
    )
}

/** Mock-up `.app`: an icon slot with an optional label, clickable as one unit. */
@Composable
internal fun DockItem(
    label: String?,
    palette: DockPalette,
    shadowLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        icon()
        if (label != null) DockLabel(label, palette, shadowLabel)
    }
}

/** Mock-up `.dt`: an app name instead of an icon (Text dock). */
@Composable
internal fun DockTextItem(
    app: AppInfo,
    palette: DockPalette,
    shadow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = app.label,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        color = palette.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            shadow = if (shadow) textShadow() else null,
        ),
    )
}

/** Mock-up `.bb`: a 68dp circular surface around one icon (Bubbles background). */
@Composable
internal fun BubbleSlot(palette: DockPalette, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(68.dp).dockSurface(palette, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Mock-up `-webkit-box-reflect` (3D shelf): the icon plus a vertically flipped copy below it at
 * 35% alpha, fading out over the top 45% of the icon height.
 */
@Composable
internal fun ReflectedIcon(size: Dp, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon()
        Box(
            modifier = Modifier
                .width(size)
                .height(size * 0.45f)
                .clipToBounds()
                .graphicsLayer {
                    alpha = 0.35f
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .size(size)
                    .graphicsLayer { scaleY = -1f },
            ) {
                icon()
            }
        }
    }
}

/** Mock-up `.sg::after`: accent sparkle badge for a suggested app. */
@Composable
internal fun SuggestionIcon(app: AppInfo, size: Dp, palette: DockPalette) {
    Box {
        AppIcon(app, size, palette)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-5).dp)
                .size(20.dp)
                .background(palette.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = palette.onAccent,
            )
        }
    }
}

/** Mock-up `.ic.btn`: the all-apps button drawn as a surface-colored icon tile. */
@Composable
internal fun AllAppsIcon(size: Dp, palette: DockPalette) {
    val shape = RoundedCornerShape(size * palette.iconCornerFraction.coerceIn(0f, 0.5f))
    Box(
        modifier = Modifier.size(size).dockSurface(palette, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = "All apps",
            modifier = Modifier.size(size * 0.5f),
            tint = palette.onSurface,
        )
    }
}

/** Mock-up `.fic`: a 2x2 grid of mini icons on a surface tile. */
@Composable
internal fun FolderTile(apps: List<AppInfo>, size: Dp, palette: DockPalette) {
    val shape = RoundedCornerShape(size * palette.iconCornerFraction.coerceIn(0f, 0.5f))
    val mini = ((size - 22.dp) / 2f).coerceAtLeast(8.dp)
    Column(
        modifier = Modifier.size(size).dockSurface(palette, shape).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 2) {
                    val app = apps.getOrNull(row * 2 + col)
                    if (app != null) {
                        AppIcon(app, mini, palette, cornerFraction = 0.3f)
                    } else {
                        Box(
                            Modifier
                                .size(mini)
                                .background(palette.accent.copy(alpha = 0.35f), RoundedCornerShape(30)),
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Add-ons                                                                                     */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up `.dsep` / `.sdiv`: thin vertical rule, or an accent sparkle for the smart section. */
@Composable
internal fun DockDivider(palette: DockPalette) {
    Box(
        Modifier
            .width(1.dp)
            .height(36.dp)
            .background(palette.onSurface.copy(alpha = 0.25f)),
    )
}

@Composable
internal fun SmartDivider(palette: DockPalette) {
    Icon(
        imageVector = Icons.Rounded.AutoAwesome,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = palette.accent,
    )
}

/** Mock-up `.dock .srch`: search bar below the icon row. */
@Composable
internal fun DockSearchBar(
    palette: DockPalette,
    showAi: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Color.Gray.copy(alpha = 0.14f))
            .clickable(onClick = onSearch)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            modifier = Modifier.size(22.dp),
            tint = palette.onSurface,
        )
        Text(
            text = "Search",
            modifier = Modifier.weight(1f),
            color = palette.onSurface.copy(alpha = 0.7f),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showAi) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = palette.accent,
            )
        }
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = palette.onSurface,
        )
    }
}

/** Mock-up `.mini`: now-playing card. Placeholder until media sessions are wired. */
@Composable
internal fun MediaCard(palette: DockPalette, modifier: Modifier = Modifier) {
    val radius = (palette.radius - 2.dp).coerceAtLeast(6.dp)
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(radius))
            .background(Color.Gray.copy(alpha = 0.14f))
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(palette.accent, palette.accent.copy(alpha = 0.45f))),
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nothing playing",
                color = palette.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Media controls appear here",
                color = palette.onSurface.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = palette.onSurface,
        )
    }
}

/** Mock-up `.cbtn`: raised 68dp accent circle in the middle of the dock. */
@Composable
internal fun CenterActionButton(
    palette: DockPalette,
    ai: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(68.dp)
            .drawBehind {
                drawCircle(color = palette.drawer, radius = size.minDimension / 2f + 6.dp.toPx())
            }
            .shadow(10.dp, CircleShape)
            .background(palette.accent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (ai) Icons.Rounded.AutoAwesome else Icons.Rounded.Search,
            contentDescription = if (ai) "Assistant" else "Search",
            modifier = Modifier.size(32.dp),
            tint = palette.onAccent,
        )
    }
}

/** Mock-up `.hnd`: chevron and hint above the dock. */
@Composable
internal fun DockHandle(
    palette: DockPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = palette.onSurface.copy(alpha = 0.8f),
        )
        Text(
            text = "Swipe up for apps",
            modifier = Modifier.offset(y = (-4).dp),
            color = palette.onSurface.copy(alpha = 0.8f),
            style = TextStyle(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = textShadow(),
            ),
        )
    }
}

/** Mock-up `.npv`: small callout bubble with a pointer, shown above the first dock icon. */
@Composable
internal fun NotifyCallout(palette: DockPalette, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier = modifier.padding(bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .shadow(10.dp, shape)
                .background(palette.drawer, shape)
                .border(1.dp, palette.surfaceBorder, shape)
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("FoldOS") }
                    append(" · Newest notification shows here")
                },
                color = palette.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 32.dp, y = 6.dp)
                .size(12.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(palette.drawer),
        )
    }
}

/** Mock-up `.fpop`: folder contents in a card floating above the dock, right aligned. */
@Composable
internal fun FolderPopup(
    name: String,
    apps: List<AppInfo>,
    palette: DockPalette,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 12.dp.roundToPx() }
    val insetPx = with(density) { 4.dp.roundToPx() }
    val positionProvider = remember(gapPx, insetPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val x = (anchorBounds.right - insetPx - popupContentSize.width).coerceIn(0, maxX)
                val y = (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }
    val shape = RoundedCornerShape(palette.radius + 4.dp)
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(230.dp)
                .shadow(14.dp, shape)
                .background(palette.drawer, shape)
                .border(1.dp, palette.surfaceBorder, shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = name,
                modifier = Modifier.padding(start = 4.dp),
                color = palette.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                apps.take(4).forEach { app ->
                    DockItem(
                        label = app.label,
                        palette = palette,
                        shadowLabel = false,
                        onClick = {
                            onLaunch(app)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        AppIcon(app, 46.dp, palette)
                    }
                }
            }
        }
    }
}
