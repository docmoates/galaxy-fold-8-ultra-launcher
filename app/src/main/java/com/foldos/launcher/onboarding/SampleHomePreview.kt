package com.foldos.launcher.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.DockBackground
import com.foldos.launcher.core.model.DockConfig
import com.foldos.launcher.core.model.DockPosition
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.ThemeId
import com.foldos.launcher.core.ui.LocalDragController
import com.foldos.launcher.core.ui.rememberDragController
import com.foldos.launcher.dock.Dock
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.FoldOsTheme
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.Wallpaper

/**
 * A live cover-screen home drawn with real launcher composables and theme tokens for a candidate
 * [style] / [theme] / [colors], scaled down to fit the box given by [modifier] (PLAN.md 2.9: every
 * wizard question is answered by tapping a live preview).
 *
 * The preview is laid out at the Fold's cover width ([PREVIEW_COVER_WIDTH]) inside
 * `FoldOsTheme(candidate)` where `candidate` is [base] with the three choices applied, so the
 * wallpaper, surfaces, radius, icon shape and dock all follow the candidate settings. The dock is
 * the real `Dock` composable fed with [SampleApps].
 *
 * @param base every other setting (appearance, dock, icon size, labels, columns, radius). Pass
 *   the user's current settings so the preview differs from their home only in the choice shown.
 * @param dock the cover dock drawn at the bottom; defaults to `base.dockCover`.
 * @param radiusDp corner radius for the candidate; defaults to `base.radiusDp`.
 */
@Composable
fun SampleHomePreview(
    style: HomeStyle,
    theme: ThemeId,
    colors: ColorSchemeConfig,
    modifier: Modifier = Modifier,
    base: LauncherSettings = LauncherSettings(),
    dock: DockConfig = base.dockCover,
    radiusDp: Int = base.radiusDp,
) {
    val candidate = remember(style, theme, colors, base, dock, radiusDp) {
        base.copy(styleCover = style, theme = theme, colors = colors, dockCover = dock, radiusDp = radiusDp)
    }
    ScaledPreview(
        virtualWidth = PREVIEW_COVER_WIDTH,
        virtualHeight = PREVIEW_COVER_HEIGHT,
        modifier = modifier,
    ) {
        // The real Dock may register as a drag target; previews get their own throw-away controller.
        CompositionLocalProvider(LocalDragController provides rememberDragController()) {
            FoldOsTheme(settings = candidate) {
                SampleCoverHome(settings = candidate)
            }
        }
    }
}

/**
 * The bottom strip of a cover screen showing only the wallpaper and [dock] (mock-up `miniDock` /
 * `stripScr`), scaled to fit the width of the box given by [modifier].
 */
@Composable
fun SampleDockPreview(
    dock: DockConfig,
    modifier: Modifier = Modifier,
    base: LauncherSettings = LauncherSettings(),
) {
    val candidate = remember(dock, base) { base.copy(dockCover = dock) }
    ScaledPreview(
        virtualWidth = PREVIEW_COVER_WIDTH,
        virtualHeight = PREVIEW_DOCK_STRIP_HEIGHT,
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalDragController provides rememberDragController()) {
            FoldOsTheme(settings = candidate) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Wallpaper()
                    if (candidate.dockCover.pos == DockPosition.RAIL) {
                        SampleDock(
                            settings = candidate,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                        )
                    } else {
                        SampleDock(
                            settings = candidate,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* The whole cover home at 411dp                                                                */
/* ------------------------------------------------------------------------------------------ */

/** Must be composed inside `FoldOsTheme(settings)`; fills its parent with a cover home for [settings]. */
@Composable
internal fun SampleCoverHome(settings: LauncherSettings) {
    val rail = settings.dockCover.pos == DockPosition.RAIL
    Box(modifier = Modifier.fillMaxSize()) {
        Wallpaper()
        Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp, bottom = 16.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SampleStatusBar()
                Spacer(Modifier.height(8.dp))
                when (settings.styleCover) {
                    HomeStyle.GRID -> GridHome(settings)
                    HomeStyle.DASHBOARD -> DashboardHome(settings)
                    HomeStyle.LIST -> ListHome(settings)
                    HomeStyle.HYBRID -> HybridHome(settings)
                }
            }
            if (!rail && settings.feature(LauncherSettings.FEATURE_DOCK)) {
                Spacer(Modifier.height(10.dp))
                SampleDock(settings = settings)
            }
        }
        if (rail && settings.feature(LauncherSettings.FEATURE_DOCK)) {
            SampleDock(
                settings = settings,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            )
        }
    }
}

/** The real dock with sample apps; side margins follow `LauncherRoot` (shelf and hairline are full width). */
@Composable
internal fun SampleDock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val config = settings.dockCover
    val side = when (config.bg) {
        DockBackground.SHELF, DockBackground.LINE -> 0.dp
        else -> 16.dp
    }
    Dock(
        config = config,
        screen = ScreenKind.COVER,
        pinned = SampleApps.all,
        recents = SampleApps.recents,
        settings = settings,
        onLaunch = {},
        onOpenDrawer = {},
        onSearch = {},
        modifier = if (config.pos == DockPosition.RAIL) modifier else modifier.padding(start = side, end = side),
    )
}

/* ------------------------------------------------------------------------------------------ */
/* Four home styles (mock-up HOME.cover.*)                                                      */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun ColumnScope.GridHome(settings: LauncherSettings) {
    SampleSearchBar(settings)
    Spacer(Modifier.height(12.dp))
    PreviewGlanceCard(Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    SampleAppGrid(
        cols = settings.colsCover.coerceIn(3, 6),
        rows = 4,
        iconSize = settings.iconSizeDp.dp,
        labels = settings.labels,
    )
    Spacer(Modifier.weight(1f))
    PageDots(active = 0)
}

@Composable
private fun ColumnScope.DashboardHome(settings: LauncherSettings) {
    SampleSearchBar(settings)
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1.25f).fillMaxWidth()) {
            PreviewClockCard(highlight = true, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PreviewWeatherCard(Modifier.weight(1f).fillMaxHeight())
            PreviewDevicesCard(Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(0.85f).fillMaxWidth()) {
            PreviewMediaCard(Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(0.9f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PreviewTodoCard(Modifier.weight(1f).fillMaxHeight())
            if (settings.feature(LauncherSettings.FEATURE_SMART)) {
                SmartWidget(Modifier.weight(1f).fillMaxHeight())
            } else {
                NotesWidget(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    PageDots(active = 1)
}

@Composable
private fun ColumnScope.ListHome(settings: LauncherSettings) {
    val tokens = LocalFoldOsTokens.current
    Spacer(Modifier.height(10.dp))
    Text(
        text = "9:41",
        color = tokens.onSurface,
        style = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, shadow = previewShadow()),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Fri, Oct 16 · ",
            color = tokens.onSurface.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
        Icon(
            imageVector = Icons.Rounded.WbSunny,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = tokens.onSurface.copy(alpha = 0.75f),
        )
        Text(
            text = "64° · Standup 10:00",
            color = tokens.onSurface.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
    }
    Spacer(Modifier.height(14.dp))
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val badges = settings.feature(LauncherSettings.FEATURE_BADGES)
        SampleApps.favorites.take(9).forEachIndexed { index, spec ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = spec.label,
                    color = tokens.onSurface,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, shadow = previewShadow()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badges && (index == 0 || index == 1 || index == 5)) {
                    Box(Modifier.size(8.dp).background(tokens.accent, CircleShape))
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        "A B C D F G H K M N P R S T W Y".split(' ').forEach { letter ->
            Text(text = letter, color = tokens.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    SampleSearchBar(settings)
    Spacer(Modifier.height(10.dp))
    PageDots(active = 0)
}

@Composable
private fun ColumnScope.HybridHome(settings: LauncherSettings) {
    SampleSearchBar(settings)
    Spacer(Modifier.height(12.dp))
    FoldOsSurface(
        modifier = Modifier.weight(1.15f).fillMaxWidth(),
        contentPadding = PaddingValues(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            PreviewClockCard(highlight = true, modifier = Modifier.weight(1.1f).fillMaxWidth())
            PreviewCalendarCard(Modifier.weight(1f).fillMaxWidth())
        }
    }
    Spacer(Modifier.height(10.dp))
    FoldOsSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
    ) {
        SampleAppGrid(cols = 4, rows = 2, iconSize = 50.dp, labels = settings.labels)
    }
    Spacer(Modifier.height(10.dp))
    PageDots(active = 0)
}

/* ------------------------------------------------------------------------------------------ */
/* Pieces                                                                                      */
/* ------------------------------------------------------------------------------------------ */

private fun previewShadow(): Shadow =
    Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 1f), blurRadius = 6f)

@Composable
private fun SampleStatusBar() {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "9:41", color = tokens.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(text = "82%", color = tokens.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Mock-up `.srch`: the pill search bar at the top of a page; hidden when the feature is off. */
@Composable
private fun SampleSearchBar(settings: LauncherSettings) {
    if (!settings.feature(LauncherSettings.FEATURE_SEARCH)) return
    // The bottom dock's search add-on is the search entry: no second search bar (LauncherRoot: topSearchBarOn).
    val dock = settings.dockCover
    if (settings.feature(LauncherSettings.FEATURE_DOCK) && dock.pos != DockPosition.RAIL && dock.search) return
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(25.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = tokens.onSurface,
            )
            Text(
                text = "Search",
                modifier = Modifier.weight(1f),
                color = tokens.onSurface.copy(alpha = 0.7f),
                fontSize = 15.sp,
                maxLines = 1,
            )
            if (settings.feature(LauncherSettings.FEATURE_AI)) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = tokens.accent,
                )
            }
        }
    }
}

/** Mock-up `.dots`: hub marker plus three page dots. */
@Composable
private fun PageDots(active: Int) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .border(1.5.dp, tokens.onSurface.copy(alpha = 0.5f), CircleShape),
        )
        repeat(3) { index ->
            val color = if (index == active) tokens.accent else tokens.onSurface.copy(alpha = 0.35f)
            Box(Modifier.size(if (index == active) 8.dp else 6.dp).background(color, CircleShape))
        }
    }
}

/** Icon grid of [SampleApps.specs] drawn as gradient tiles in the theme's icon shape (mock-up `appGrid`). */
@Composable
private fun SampleAppGrid(cols: Int, rows: Int, iconSize: Dp, labels: Boolean) {
    val tokens = LocalFoldOsTokens.current
    val percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50)
    val shape = RoundedCornerShape(percent = percent)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until cols) {
                    val spec = SampleApps.specs[(row * cols + col) % SampleApps.specs.size]
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SampleIconTile(color = Color(spec.color), size = iconSize, shape = shape)
                        if (labels) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = spec.label,
                                color = tokens.onSurface,
                                style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, shadow = previewShadow()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleIconTile(color: Color, size: Dp, shape: RoundedCornerShape) {
    val light = Color(
        red = (color.red + (1f - color.red) * 0.28f),
        green = (color.green + (1f - color.green) * 0.28f),
        blue = (color.blue + (1f - color.blue) * 0.28f),
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(light, color))),
    )
}

/** A widget card: surface (or highlight) fill with 14dp padding. */
@Composable
private fun PreviewCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(
        modifier = modifier,
        color = if (highlight) tokens.highlight else tokens.surface,
        borderColor = if (highlight) Color.Transparent else tokens.surfaceBorder,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/** Mock-up `.k`: the small uppercase kicker at the top of a widget. */
@Composable
private fun Kicker(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        color = color.copy(alpha = 0.72f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = color)
        Text(text = text, color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PreviewGlanceCard(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "9:41",
                color = tokens.onSurface,
                style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "Friday, Oct 16", color = tokens.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                InfoLine(Icons.Rounded.WbSunny, "64° · Austin", tokens.onSurface.copy(alpha = 0.75f))
                InfoLine(Icons.Rounded.Event, "Standup at 10:00", tokens.onSurface.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun PreviewClockCard(highlight: Boolean, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val color = if (highlight) tokens.onHighlight else tokens.onSurface
    PreviewCard(modifier = modifier, highlight = highlight) {
        Kicker("Fri, Oct 16 · Austin", color)
        Text(
            text = "9:41",
            color = color,
            style = TextStyle(fontSize = 54.sp, fontWeight = FontWeight.Light),
        )
        Spacer(Modifier.weight(1f))
        InfoLine(Icons.Rounded.WbSunny, "64° partly cloudy · H 71° L 55°", color)
        Spacer(Modifier.height(3.dp))
        InfoLine(Icons.Rounded.Event, "Standup in 19 min", color)
    }
}

@Composable
private fun PreviewWeatherCard(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    PreviewCard(modifier = modifier) {
        Kicker("Weather", tokens.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "64°", color = tokens.onSurface, style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Light))
            Icon(imageVector = Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(26.dp), tint = tokens.accent)
        }
        Spacer(Modifier.weight(1f))
        Text(text = "Partly cloudy", color = tokens.onSurface.copy(alpha = 0.8f), fontSize = 12.5.sp, maxLines = 1)
        Text(text = "H 71° · L 55° · Rain 10%", color = tokens.onSurface.copy(alpha = 0.8f), fontSize = 12.5.sp, maxLines = 1)
    }
}

@Composable
private fun PreviewDevicesCard(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    PreviewCard(modifier = modifier) {
        Kicker("Devices", tokens.onSurface)
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DeviceRing("Fold", 82)
            DeviceRing("Watch", 54)
            DeviceRing("Buds", 91)
            DeviceRing("Ring", 70)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RowScope.DeviceRing(name: String, percent: Int) {
    val tokens = LocalFoldOsTokens.current
    val accent = tokens.accent
    val track = tokens.onSurface.copy(alpha = 0.18f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = accent, startAngle = -90f, sweepAngle = 360f * percent / 100f, useCenter = false, style = stroke)
        }
        Text(text = name, color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 10.5.sp, maxLines = 1)
        Text(text = "$percent%", color = tokens.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PreviewMediaCard(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    PreviewCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape((tokens.radius - 6.dp).coerceAtLeast(4.dp)))
                    .background(Brush.linearGradient(listOf(tokens.accent, tokens.accent.copy(alpha = 0.45f)))),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Kicker("Now playing · Spotify", tokens.onSurface)
                Text(text = "Midnight City", color = tokens.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = "M83", color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 12.5.sp, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(tokens.onSurface.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(0.38f).fillMaxHeight().background(tokens.accent))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(20.dp), tint = tokens.onSurface)
                    Box(Modifier.size(26.dp).background(tokens.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.onAccent)
                    }
                    Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp), tint = tokens.onSurface)
                }
            }
        }
    }
}

@Composable
private fun PreviewTodoCard(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    PreviewCard(modifier = modifier) {
        Kicker("To-do · Todoist", tokens.onSurface)
        Spacer(Modifier.height(6.dp))
        TodoLine("Send plan v0.3", done = false)
        TodoLine("Call the installer", done = false)
        TodoLine("Renew passport", done = true)
    }
}

@Composable
private fun TodoLine(text: String, done: Boolean) {
    val tokens = LocalFoldOsTokens.current
    val color = if (done) tokens.onSurface.copy(alpha = 0.5f) else tokens.onSurface
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .then(
                    if (done) Modifier.background(tokens.accent) else Modifier.border(1.5.dp, color, RoundedCornerShape(3.dp)),
                ),
        )
        Text(text = text, color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NotesWidget(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    PreviewCard(modifier = modifier) {
        Kicker("Samsung Notes", tokens.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Widget rule idea: show Health when connected to gym Wi-Fi.",
            color = tokens.onSurface,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Mock-up `WG.smart`: suggested apps picked by the on-device model. */
@Composable
private fun SmartWidget(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50))
    PreviewCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp), tint = tokens.accent)
            Kicker("Suggested", tokens.onSurface)
        }
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(12, 5, 8, 10).forEach { index ->
                val spec = SampleApps.specs[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SampleIconTile(color = Color(spec.color), size = 34.dp, shape = shape)
                    Text(text = spec.label, color = tokens.onSurface.copy(alpha = 0.8f), fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PreviewCalendarCard(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    PreviewCard(modifier = modifier) {
        Kicker("Today · Google + Outlook", tokens.onSurface)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CalendarEvent("Standup", "10:00 – 10:15 · Teams", Color(0xFF5B8CFF))
            CalendarEvent("Dentist", "11:30 – 12:30 · Lamar Blvd", Color(0xFFF5A524))
            CalendarEvent("1:1 with Sam", "2:00 – 2:30 PM", Color(0xFF22C55E))
        }
    }
}

@Composable
private fun CalendarEvent(title: String, time: String, color: Color) {
    val tokens = LocalFoldOsTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(3.dp).height(30.dp).clip(CircleShape).background(color))
        Column {
            Text(text = title, color = tokens.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(text = time, color = tokens.onSurface.copy(alpha = 0.7f), fontSize = 11.5.sp, maxLines = 1)
        }
    }
}
