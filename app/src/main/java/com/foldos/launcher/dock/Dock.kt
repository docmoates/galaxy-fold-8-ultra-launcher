package com.foldos.launcher.dock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yDirectionalFocus
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.DockBackground
import com.foldos.launcher.core.model.DockConfig
import com.foldos.launcher.core.model.DockContent
import com.foldos.launcher.core.model.DockPosition
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import kotlin.math.abs

/** Numbers shared with the Home agent so it can size the pinned list it hands to [Dock]. */
object DockDefaults {
    /** Height of the Edge rail's icons; the rail is icons only. */
    val railIconSize: Dp = 46.dp

    /**
     * How many pinned apps the dock shows for [config] on [screen]; mirrors the `n` logic of the
     * mock-up's `dockHTML()`. FLEX counts as the inner screen.
     */
    fun pinnedCount(config: DockConfig, screen: ScreenKind): Int {
        if (config.pos == DockPosition.RAIL) return 4
        val wide = screen != ScreenKind.COVER
        var n = if (wide) 7 else 4
        if (config.two) {
            n = if (wide) 16 else 10
        } else if (config.scroll) {
            n = if (wide) 12 else 6
        }
        if (config.taskbar) n = minOf(n, if (wide) 6 else 2)
        if (config.media) n = maxOf(1, n - (if (wide) 1 else 2))
        if (config.smart) n = maxOf(1, n - 1)
        if (config.folder) n = maxOf(1, n - 1)
        return n
    }

    /** How many apps the folder add-on shows in its tile and pop-up. */
    const val FOLDER_SIZE: Int = 4
}

/**
 * The dock, built from the traits in [config] (PLAN.md 2.6, mock-up `dockHTML()`).
 *
 * @param pinned the user's dock apps; the first [DockDefaults.pinnedCount] are shown, any extra
 *   ones fill the folder add-on.
 * @param recents most recent first. The taskbar add-on shows the first two, the smart add-on
 *   suggests the last two.
 * @param modifier applied to the outermost node. For [DockPosition.BOTTOM] the dock fills the
 *   width it is given and should sit at the bottom of the home page (above the gesture bar).
 *   For [DockPosition.RAIL] it is a vertical pill that should be aligned to the right edge,
 *   vertically centered.
 */
@Composable
fun Dock(
    config: DockConfig,
    screen: ScreenKind,
    pinned: List<AppInfo>,
    recents: List<AppInfo>,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onNotifications: (() -> Unit)? = null,
) {
    if (!settings.feature(LauncherSettings.FEATURE_DOCK)) return
    val palette = dockPalette()
    if (config.pos == DockPosition.RAIL) {
        RailDock(config, pinned, palette, onLaunch, modifier, settings.largeIcons)
    } else {
        BottomDock(config, screen, pinned, recents, settings, palette, onLaunch, onOpenDrawer, onSearch, modifier, onNotifications)
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Entries: everything that can sit in the icon row                                            */
/* ------------------------------------------------------------------------------------------ */

private sealed interface DockEntry {
    data class App(val app: AppInfo, val scale: Float) : DockEntry
    data class TextApp(val app: AppInfo) : DockEntry
    object Folder : DockEntry
    object SmartDivider : DockEntry
    data class Suggestion(val app: AppInfo) : DockEntry
    object TaskbarDivider : DockEntry
    data class Recent(val app: AppInfo) : DockEntry
    object AllApps : DockEntry
    object Center : DockEntry
    object Media : DockEntry
}

/** Everything an entry needs to draw itself. */
private class DockContext(
    val config: DockConfig,
    val wide: Boolean,
    val iconSize: Dp,
    val shadowLabels: Boolean,
    val palette: DockPalette,
    val aiOn: Boolean,
    val folderApps: List<AppInfo>,
    val onLaunch: (AppInfo) -> Unit,
    val onOpenDrawer: () -> Unit,
    val onSearch: () -> Unit,
    val onToggleFolder: () -> Unit,
    /** Easy mode (`LauncherSettings.largeIcons`): labels are always shown. */
    val largeIcons: Boolean = false,
) {
    val labels: Boolean get() = config.labels || largeIcons
}

private fun buildEntries(
    config: DockConfig,
    apps: List<AppInfo>,
    recents: List<AppInfo>,
): List<DockEntry> {
    val text = config.content == DockContent.TEXT
    val magnify = config.magnify && !config.two
    val peak = apps.size / 2
    val entries = ArrayList<DockEntry>()
    apps.forEachIndexed { k, app ->
        if (text) {
            entries.add(DockEntry.TextApp(app))
        } else {
            val scale = when {
                !magnify -> 1f
                k == peak -> 1.36f
                abs(k - peak) == 1 -> 1.12f
                else -> 1f
            }
            entries.add(DockEntry.App(app, scale))
        }
    }
    if (config.folder) entries.add(DockEntry.Folder)
    if (config.smart) {
        entries.add(DockEntry.SmartDivider)
        recents.takeLast(2).forEach { entries.add(DockEntry.Suggestion(it)) }
    }
    if (config.taskbar) {
        entries.add(DockEntry.TaskbarDivider)
        recents.take(2).forEach { entries.add(DockEntry.Recent(it)) }
        entries.add(DockEntry.AllApps)
    }
    if (config.center) entries.add((apps.size + 1) / 2, DockEntry.Center)
    if (config.media) entries.add(0, DockEntry.Media)
    return entries
}

/**
 * Mock-up `isz` logic: base 56dp (cover) / 52dp (inner), smaller for labeled, hairline, bubble
 * and two-row docks, and shrunk further when many add-ons share one row.
 */
private fun dockIconSize(config: DockConfig, wide: Boolean, slots: Float): Dp {
    var size = if (wide) 52 else 56
    if (config.labels) size = 50
    if (config.bg == DockBackground.LINE) size = if (wide) 48 else 46
    if (config.bg == DockBackground.BUBBLE) size = 44
    if (config.two) size = 48
    if (!config.two && !config.scroll) {
        if (!wide && slots > 5.5f) size = if (slots > 6.5f) 40 else 46
        if (wide && slots > 11f) size = 44
    }
    return size.dp
}

/* ------------------------------------------------------------------------------------------ */
/* Bottom dock                                                                                 */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun BottomDock(
    config: DockConfig,
    screen: ScreenKind,
    pinned: List<AppInfo>,
    recents: List<AppInfo>,
    settings: LauncherSettings,
    palette: DockPalette,
    onLaunch: (AppInfo) -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier,
    onNotifications: (() -> Unit)? = null,
) {
    val wide = screen != ScreenKind.COVER
    val count = DockDefaults.pinnedCount(config, screen)
    val apps = pinned.take(count)
    val folderApps = remember(pinned, recents, count) {
        (pinned.drop(count) + recents).distinctBy { it.key }.take(DockDefaults.FOLDER_SIZE)
    }
    val entries = buildEntries(config, apps, recents)
    val slots = entries.size + (if (config.media) 2f else 0f) - (if (config.taskbar) 0.7f else 0f)
    // Easy mode (settings.largeIcons): 20% bigger icons and always-on labels.
    val iconSize = dockIconSize(config, wide, slots).let { if (settings.largeIcons) it * 1.2f else it }
    val onSurfaceBg = config.bg == DockBackground.PILL || config.bg == DockBackground.SHELF
    var folderOpen by remember { mutableStateOf(false) }

    val ctx = DockContext(
        config = config,
        wide = wide,
        iconSize = iconSize,
        shadowLabels = !onSurfaceBg,
        palette = palette,
        aiOn = settings.feature(LauncherSettings.FEATURE_AI),
        folderApps = folderApps,
        onLaunch = onLaunch,
        onOpenDrawer = onOpenDrawer,
        onSearch = onSearch,
        onToggleFolder = { folderOpen = !folderOpen },
        largeIcons = settings.largeIcons,
    )

    Column(
        // Hardware keyboard: arrow keys move focus between the dock items.
        modifier = modifier.fillMaxWidth().a11yDirectionalFocus(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (config.handle) {
            DockHandle(palette = palette, onClick = onOpenDrawer)
            Spacer(Modifier.height(6.dp))
        }
        if (config.notify) {
            NotifyRow(wide, palette, onNotifications)
            Spacer(Modifier.height(12.dp))
        }
        Box {
            DockBody(ctx, entries)
            if (config.folder && folderOpen) {
                FolderPopup(
                    name = settings.profile,
                    apps = folderApps,
                    palette = palette,
                    onLaunch = onLaunch,
                    onDismiss = { folderOpen = false },
                )
            }
        }
    }
}

/** The notification add-on's callout; tapping it opens the quiet notification drawer when [onClick] is set. */
@Composable
private fun NotifyRow(wide: Boolean, palette: DockPalette, onClick: (() -> Unit)?) {
    val callout = if (onClick != null) Modifier.clickable(onClickLabel = "Open notifications", role = Role.Button, onClick = onClick) else Modifier
    if (wide) {
        Box(Modifier.fillMaxWidth().padding(start = 30.dp)) {
            NotifyCallout(palette, callout)
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            NotifyCallout(palette, callout)
            Spacer(Modifier.weight(3f))
        }
    }
}

/** The dock panel itself: background trait, icon row(s), optional scroll indicator and search bar. */
@Composable
private fun DockBody(ctx: DockContext, entries: List<DockEntry>) {
    val config = ctx.config
    val palette = ctx.palette
    val wide = ctx.wide
    val fillWidth = !wide ||
        config.bg == DockBackground.SHELF ||
        config.bg == DockBackground.LINE

    val widthModifier = when {
        fillWidth -> Modifier.fillMaxWidth()
        config.search -> Modifier.wrapContentWidth().widthIn(min = 520.dp)
        config.scroll && !config.two -> Modifier.wrapContentWidth().widthIn(max = 560.dp)
        else -> Modifier.wrapContentWidth()
    }

    val pillRadius = palette.radius + (if (config.search) 10.dp else 6.dp)
    val backgroundModifier: Modifier = when (config.bg) {
        DockBackground.PILL -> Modifier.dockSurface(palette, RoundedCornerShape(pillRadius))
        DockBackground.SHELF -> {
            val shape: Shape = RoundedCornerShape(
                topStart = pillRadius,
                topEnd = pillRadius,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            )
            Modifier.dockSurface(palette, shape)
        }
        DockBackground.LINE -> Modifier.drawBehind {
            drawLine(
                color = palette.onSurface.copy(alpha = 0.3f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
            )
        }
        DockBackground.REFLECT -> Modifier.reflectShelf(palette, ctx.iconSize)
        DockBackground.NONE, DockBackground.BUBBLE -> Modifier
    }

    val paddingModifier = when (config.bg) {
        DockBackground.PILL -> when {
            config.search -> Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
            wide -> Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            else -> Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
        }
        DockBackground.SHELF -> Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 16.dp)
        DockBackground.LINE -> Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)
        DockBackground.REFLECT -> Modifier.padding(start = 14.dp, end = 14.dp, bottom = 22.dp)
        DockBackground.NONE, DockBackground.BUBBLE -> Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    }

    Column(
        modifier = Modifier
            .then(widthModifier)
            .then(backgroundModifier)
            .then(paddingModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            config.two -> TwoRowGrid(ctx, entries)
            config.scroll -> ScrollRow(ctx, entries)
            else -> SingleRow(ctx, entries, fillWidth)
        }
        if (config.search) {
            DockSearchBar(palette = palette, showAi = ctx.aiOn, onSearch = ctx.onSearch)
        }
    }
}

/** Mock-up `.d-reflect::before`: a glass shelf drawn behind the bottom of the icon row. */
private fun Modifier.reflectShelf(palette: DockPalette, iconSize: Dp): Modifier = drawBehind {
    val inset = size.width * 0.03f
    val shelfHeight = 22.dp.toPx() + iconSize.toPx() * 0.45f + 6.dp.toPx()
    val bottom = size.height + 4.dp.toPx()
    val top = bottom - shelfHeight
    val skew = 12.dp.toPx()
    val path = Path().apply {
        moveTo(inset + skew, top)
        lineTo(size.width - inset - skew, top)
        lineTo(size.width - inset, bottom)
        lineTo(inset, bottom)
        close()
    }
    drawPath(path = path, color = palette.surface)
    drawPath(path = path, color = palette.surfaceBorder, style = Stroke(width = 1.dp.toPx()))
}

/* ------------------------------------------------------------------------------------------ */
/* Row variants                                                                                */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun SingleRow(ctx: DockContext, entries: List<DockEntry>, fillWidth: Boolean) {
    val config = ctx.config
    val magnify = config.magnify && !config.two
    val text = config.content == DockContent.TEXT
    val centeredWide = ctx.wide &&
        (config.bg == DockBackground.SHELF || config.bg == DockBackground.LINE || text)
    val arrangement = when {
        magnify -> Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        text && !ctx.wide -> Arrangement.SpaceBetween
        centeredWide -> Arrangement.spacedBy(if (text) 28.dp else 36.dp, Alignment.CenterHorizontally)
        ctx.wide -> Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
        else -> Arrangement.SpaceAround
    }
    Row(
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = if (magnify) Alignment.Bottom else Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            val mediaModifier = if (fillWidth) Modifier.weight(1f) else Modifier.width(250.dp)
            EntryView(entry, ctx, mediaModifier)
        }
    }
}

/** Mock-up `.drow.two`: 5 columns on the cover, 8 on the inner screen. */
@Composable
private fun TwoRowGrid(ctx: DockContext, entries: List<DockEntry>) {
    val cols = if (ctx.wide) 8 else 5
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.chunked(cols).forEach { rowEntries ->
            if (ctx.wide) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowEntries.forEach { EntryView(it, ctx, Modifier.width(250.dp)) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until cols) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            val entry = rowEntries.getOrNull(i)
                            if (entry != null) EntryView(entry, ctx, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/** Mock-up `.drow.scroll` + `.sind`: horizontally scrolling row with a fade and an indicator. */
@Composable
private fun ScrollRow(ctx: DockContext, entries: List<DockEntry>) {
    val state = rememberLazyListState()
    Column(
        modifier = if (ctx.wide) Modifier.widthIn(max = 560.dp) else Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.82f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(entries.size) { index ->
                EntryView(entries[index], ctx, Modifier.width(200.dp))
            }
        }
        ScrollIndicator(state, ctx.palette)
    }
}

@Composable
private fun ScrollIndicator(state: LazyListState, palette: DockPalette) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    val fraction = if (total > visible && total > 0) {
        (state.firstVisibleItemIndex.toFloat() / (total - visible).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val color = palette.onSurface
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(3.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(color = color.copy(alpha = 0.2f), cornerRadius = radius)
                val thumbWidth = size.width * 0.45f
                val x = (size.width - thumbWidth) * fraction
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(thumbWidth, size.height),
                    cornerRadius = radius,
                )
            },
    )
}

/* ------------------------------------------------------------------------------------------ */
/* One entry                                                                                   */
/* ------------------------------------------------------------------------------------------ */

/**
 * Draws one dock entry. [mediaModifier] sizes the media card for the current container (weight
 * in a filled row, a fixed width elsewhere); everything else wraps its content.
 */
@Composable
private fun EntryView(entry: DockEntry, ctx: DockContext, mediaModifier: Modifier) {
    val palette = ctx.palette
    val label: (String) -> String? = { if (ctx.labels) it else null }
    when (entry) {
        is DockEntry.App -> MagnifyBox(entry.scale) {
            DockItem(
                label = label(entry.app.label),
                palette = palette,
                shadowLabel = ctx.shadowLabels,
                onClick = { ctx.onLaunch(entry.app) },
                contentLabel = entry.app.label,
            ) {
                PinnedIcon(entry.app, ctx)
            }
        }
        is DockEntry.TextApp -> DockTextItem(
            app = entry.app,
            palette = palette,
            shadow = ctx.shadowLabels,
            onClick = { ctx.onLaunch(entry.app) },
        )
        DockEntry.Folder -> DockItem(
            label = label("Folder"),
            palette = palette,
            shadowLabel = ctx.shadowLabels,
            onClick = ctx.onToggleFolder,
            contentLabel = "Dock folder, ${ctx.folderApps.size} apps",
        ) {
            FolderTile(ctx.folderApps, ctx.iconSize, palette)
        }
        DockEntry.SmartDivider -> SmartDivider(palette)
        is DockEntry.Suggestion -> DockItem(
            label = label(entry.app.label),
            palette = palette,
            shadowLabel = ctx.shadowLabels,
            onClick = { ctx.onLaunch(entry.app) },
            contentLabel = "${entry.app.label}, suggested",
        ) {
            SuggestionIcon(entry.app, ctx.iconSize, palette)
        }
        DockEntry.TaskbarDivider -> DockDivider(palette)
        is DockEntry.Recent -> DockItem(
            label = label(entry.app.label),
            palette = palette,
            shadowLabel = ctx.shadowLabels,
            onClick = { ctx.onLaunch(entry.app) },
            contentLabel = "${entry.app.label}, recent",
        ) {
            AppIcon(entry.app, ctx.iconSize, palette)
        }
        DockEntry.AllApps -> DockItem(
            label = label("Apps"),
            palette = palette,
            shadowLabel = ctx.shadowLabels,
            onClick = ctx.onOpenDrawer,
            contentLabel = "All apps",
        ) {
            AllAppsIcon(ctx.iconSize, palette)
        }
        DockEntry.Center -> CenterActionButton(
            palette = palette,
            ai = ctx.aiOn,
            onClick = ctx.onSearch,
        )
        DockEntry.Media -> MediaCard(palette = palette, modifier = mediaModifier)
    }
}

/** A pinned app's icon with the background trait applied (bubble ring or shelf reflection). */
@Composable
private fun PinnedIcon(app: AppInfo, ctx: DockContext) {
    when (ctx.config.bg) {
        DockBackground.BUBBLE -> BubbleSlot(ctx.palette) { AppIcon(app, ctx.iconSize, ctx.palette) }
        DockBackground.REFLECT -> ReflectedIcon(ctx.iconSize) { AppIcon(app, ctx.iconSize, ctx.palette) }
        else -> AppIcon(app, ctx.iconSize, ctx.palette)
    }
}

/** Mock-up `.mag .ic`: scale about the bottom center so magnified icons grow upward. */
@Composable
private fun MagnifyBox(scale: Float, content: @Composable () -> Unit) {
    if (scale == 1f) {
        content()
    } else {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        ) {
            content()
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Edge rail                                                                                   */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up `.dock.rail`: vertical pill on the right edge, icons only, add-ons ignored. */
@Composable
private fun RailDock(
    config: DockConfig,
    pinned: List<AppInfo>,
    palette: DockPalette,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier,
    largeIcons: Boolean = false,
) {
    val apps = pinned.take(DockDefaults.pinnedCount(config, ScreenKind.COVER))
    val railIconSize = if (largeIcons) DockDefaults.railIconSize * 1.2f else DockDefaults.railIconSize
    val bubble = config.bg == DockBackground.BUBBLE
    val surface = !bubble && config.bg != DockBackground.NONE
    Column(
        modifier = modifier
            .then(if (surface) Modifier.dockSurface(palette, CircleShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        apps.forEach { app ->
            DockItem(
                label = null,
                palette = palette,
                shadowLabel = false,
                onClick = { onLaunch(app) },
                contentLabel = app.label,
            ) {
                if (bubble) {
                    BubbleSlot(palette) { AppIcon(app, railIconSize, palette) }
                } else {
                    AppIcon(app, railIconSize, palette)
                }
            }
        }
    }
}
