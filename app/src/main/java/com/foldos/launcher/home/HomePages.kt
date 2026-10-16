package com.foldos.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.theme.LocalFoldOsTokens
import java.time.LocalDateTime

/**
 * Renders one home page by the [HomeStyle] configured for the screen (Settings > Home > Cover /
 * Inner screen style). [HomePage.style] is not consulted: every page is persisted with a non-null
 * style at creation, so it can not express "follow the setting" yet.
 */
@Composable
internal fun HomePageBody(
    ctx: HomeContext,
    page: HomePage?,
    items: List<HomeItem>,
    modifier: Modifier = Modifier,
) {
    val style = ctx.settings.styleFor(ctx.screen)
    when (style) {
        HomeStyle.GRID -> HomeGridPage(ctx, page, items, modifier)
        HomeStyle.DASHBOARD -> HomeDashboardPage(ctx, modifier)
        HomeStyle.LIST -> HomeListPage(ctx, items, modifier)
        HomeStyle.HYBRID -> HomeHybridPage(ctx, items, modifier)
    }
}

/* ---------------- Classic grid ---------------- */

@Composable
internal fun HomeGridPage(
    ctx: HomeContext,
    page: HomePage?,
    items: List<HomeItem>,
    modifier: Modifier = Modifier,
) {
    val cols = ctx.cols
    val pageId = page?.id ?: 0L
    // Only the first page (or the placeholder shown before any page exists) is auto-filled while
    // the repository has nothing for it; other pages render empty when they are empty.
    val firstPage = page == null || page.index == 0
    val gridItems = remember(items, cols, pageId, firstPage, ctx.sortedApps) {
        val source = if (items.isEmpty() && firstPage) ctx.autoGridItems(pageId, cols, maxRows = 8) else items
        reflowGridItems(source, cols)
    }
    val cells = remember(gridItems) {
        gridItems.map { HomeCell(it.col, it.row, it.spanX, it.spanY) }
    }
    val labelHeight = if (ctx.labels) 24.dp else 0.dp
    HomeCellGrid(
        cols = cols,
        cells = cells,
        modifier = modifier.fillMaxSize(),
        minCellHeight = ctx.iconSize + labelHeight + 20.dp,
        gutter = ctx.hingeGutter,
    ) { index ->
        HomeItemTile(ctx, gridItems[index])
    }
}

/**
 * Moves every item that does not fit the current column count (negative coordinates, `col >= cols`,
 * or a span that runs past the last column) into the first free cell in reading order, so lowering
 * "Cover columns" / "Inner columns" in Settings below the seeded count never hides items. Items that
 * fit keep their stored position; nothing is written back to the repository.
 */
internal fun reflowGridItems(items: List<HomeItem>, cols: Int): List<HomeItem> {
    val columns = cols.coerceAtLeast(1)
    fun fits(item: HomeItem): Boolean =
        item.col >= 0 && item.row >= 0 && item.col + item.spanX.coerceAtLeast(1) <= columns
    if (items.all(::fits)) return items

    val occupied = HashSet<Pair<Int, Int>>()
    fun mark(col: Int, row: Int, spanX: Int, spanY: Int) {
        for (r in row until row + spanY) {
            for (c in col until col + spanX) occupied.add(c to r)
        }
    }
    fun free(col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        for (r in row until row + spanY) {
            for (c in col until col + spanX) if ((c to r) in occupied) return false
        }
        return true
    }

    val kept = items.filter(::fits)
    kept.forEach { mark(it.col, it.row, it.spanX.coerceAtLeast(1), it.spanY.coerceAtLeast(1)) }

    val moved = items
        .filterNot(::fits)
        .sortedWith(compareBy<HomeItem> { it.row }.thenBy { it.col })
        .map { item ->
            val spanX = item.spanX.coerceIn(1, columns)
            val spanY = item.spanY.coerceAtLeast(1)
            var row = 0
            var col = 0
            var placed = false
            while (!placed) {
                col = 0
                while (col + spanX <= columns) {
                    if (free(col, row, spanX, spanY)) {
                        placed = true
                        break
                    }
                    col++
                }
                if (!placed) row++
            }
            mark(col, row, spanX, spanY)
            item.copy(col = col, row = row, spanX = spanX, spanY = spanY)
        }
    return kept + moved
}

@Composable
internal fun HomeItemTile(ctx: HomeContext, item: HomeItem) {
    when (item.kind) {
        ItemKind.APP -> {
            val app = ctx.appFor(item)
            if (app != null) {
                HomeAppTile(
                    app = app,
                    iconSize = ctx.iconSize,
                    showLabel = ctx.labels,
                    onClick = { ctx.onLaunch(app) },
                    onLongClick = ctx.onLongPressHome,
                )
            } else {
                HomeMissingTile(ctx.iconSize)
            }
        }
        ItemKind.FOLDER -> HomeFolderTile(ctx, item)
        ItemKind.WIDGET -> HomeDashCard(
            title = item.title ?: "Widget",
            icon = Icons.Rounded.Widgets,
            body = item.title ?: "Widget",
            modifier = Modifier.fillMaxSize().padding(6.dp),
            caption = "Widget hosting arrives in phase 2",
            bodySize = 18,
        )
        ItemKind.PAIR -> HomePairTile(ctx, item)
    }
}

/** Folder: a surface tile with four mini squares (mock-up `.fic`). */
@Composable
private fun HomeFolderTile(ctx: HomeContext, item: HomeItem) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50))
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Opening a folder arrives with the folder UI; a plain tap does nothing for now.
            .homeTapGestures(onTap = {}, onLongPress = ctx.onLongPressHome)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(ctx.iconSize)
                .clip(shape)
                .background(tokens.surface, shape)
                .border(1.dp, tokens.surfaceBorder, shape)
                .padding(ctx.iconSize / 6),
        ) {
            val dot = ctx.iconSize / 4
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(dot).background(tokens.accent, RoundedCornerShape(30)))
                    Box(Modifier.size(dot).background(tokens.accent.copy(alpha = 0.7f), RoundedCornerShape(30)))
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(dot).background(tokens.accent.copy(alpha = 0.5f), RoundedCornerShape(30)))
                    Box(Modifier.size(dot).background(tokens.accent.copy(alpha = 0.35f), RoundedCornerShape(30)))
                }
            }
        }
        if (ctx.labels) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title ?: "Folder",
                color = tokens.onSurface,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = homeTextShadow()),
            )
        }
    }
}

/** App pair: the primary app's icon with a second half tinted in the accent (mock-up `.ic.pair`). */
@Composable
private fun HomePairTile(ctx: HomeContext, item: HomeItem) {
    val tokens = LocalFoldOsTokens.current
    val app = ctx.appFor(item)
    val shape = RoundedCornerShape(percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .homeTapGestures(onTap = { if (app != null) ctx.onLaunch(app) }, onLongPress = ctx.onLongPressHome)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.size(ctx.iconSize), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(shape)) {
                if (app != null) {
                    HomeAppIcon(app = app, size = ctx.iconSize, modifier = Modifier.fillMaxHeight())
                } else {
                    Box(Modifier.fillMaxSize().background(tokens.surface))
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(tokens.highlight),
            )
        }
        if (ctx.labels) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title ?: "Pair",
                color = tokens.onSurface,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = homeTextShadow()),
            )
        }
    }
}

/* ---------------- Widget dashboard ---------------- */

/**
 * Placeholder dashboard. Cover: 2 columns x 3 rows; inner: 4 columns x 2 rows with the middle gap
 * over the hinge. Cards: Clock (highlighted), Weather, Calendar, Media (+ Tasks, Focus on inner).
 */
@Composable
internal fun HomeDashboardPage(ctx: HomeContext, modifier: Modifier = Modifier) {
    val now = rememberHomeClock()
    val inner = ctx.isInner
    val cols = if (inner) 4 else 2
    val rows = if (inner) 2 else 3
    val cells = remember(inner) {
        if (inner) {
            listOf(
                HomeCell(0, 0, 2, 1), HomeCell(2, 0), HomeCell(3, 0),
                HomeCell(0, 1, 2, 1), HomeCell(2, 1), HomeCell(3, 1),
            )
        } else {
            listOf(
                HomeCell(0, 0, 2, 1),
                HomeCell(0, 1), HomeCell(1, 1),
                HomeCell(0, 2, 2, 1),
            )
        }
    }
    HomeCellGrid(
        cols = cols,
        cells = cells,
        modifier = modifier.fillMaxSize(),
        fixedRows = rows,
        gutter = ctx.hingeGutter,
    ) { index ->
        val cardModifier = Modifier.fillMaxSize().padding(6.dp)
        when (index) {
            0 -> HomeClockCard(now, cardModifier)
            1 -> HomeDashCard("Weather", Icons.Rounded.WbSunny, "--°", cardModifier, caption = "Connect a weather source")
            2 -> HomeDashCard("Calendar", Icons.Rounded.DateRange, now.dayOfMonth.toString(), cardModifier, caption = "No events yet")
            3 -> HomeMediaCard(cardModifier)
            4 -> HomeDashCard("Tasks", Icons.Rounded.CheckCircle, "0", cardModifier, caption = "Due today")
            else -> HomeDashCard("Focus", Icons.Rounded.Timer, "25:00", cardModifier, caption = "Deep work")
        }
    }
}

@Composable
private fun HomeClockCard(now: LocalDateTime, modifier: Modifier) {
    HomeDashCard(
        title = "Clock",
        icon = Icons.Rounded.Schedule,
        body = now.homeTimeText(),
        modifier = modifier,
        caption = now.homeDateText(long = true),
        highlight = true,
        bodySize = 44,
    )
}

@Composable
private fun HomeCalendarCard(now: LocalDateTime, modifier: Modifier) {
    HomeDashCard(
        title = "Calendar",
        icon = Icons.Rounded.DateRange,
        body = now.homeDateText(long = false),
        modifier = modifier,
        caption = "No events yet",
        bodySize = 24,
    )
}

@Composable
private fun HomeMediaCard(modifier: Modifier) {
    HomeDashCard(
        title = "Media",
        icon = Icons.Rounded.MusicNote,
        body = "Nothing playing",
        modifier = modifier,
        caption = "Media controls appear here",
        bodySize = 20,
    )
}

/* ---------------- Minimal list ---------------- */

/** Large text list of favorites. Inner screen: list on the left, clock and cards on the right. */
@Composable
internal fun HomeListPage(ctx: HomeContext, items: List<HomeItem>, modifier: Modifier = Modifier) {
    val now = rememberHomeClock()
    if (!ctx.isInner) {
        Column(modifier.fillMaxSize()) {
            HomeListHeader(now, timeSize = 66)
            HomeFavoriteList(ctx, ctx.favorites(items, 9), Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        val gap = if (ctx.hingeGutter > 0.dp) 32.dp else 24.dp
        Row(modifier.fillMaxSize()) {
            HomeFavoriteList(
                ctx,
                ctx.favorites(items, 10),
                Modifier.weight(1f).fillMaxHeight().padding(top = 30.dp),
            )
            Spacer(Modifier.width(gap))
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(top = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HomeListHeader(now, timeSize = 88)
                HomeCalendarCard(now, Modifier.weight(1f).fillMaxWidth())
                HomeMediaCard(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HomeListHeader(now: LocalDateTime, timeSize: Int) {
    val tokens = LocalFoldOsTokens.current
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
        Text(
            text = now.homeTimeText(),
            color = tokens.onSurface,
            fontSize = timeSize.sp,
            fontWeight = FontWeight.Light,
            lineHeight = timeSize.sp,
            style = TextStyle(shadow = homeTextShadow()),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = now.homeDateText(long = true),
            color = tokens.onSurface.copy(alpha = 0.8f),
            fontSize = 14.5.sp,
            style = TextStyle(shadow = homeTextShadow()),
        )
    }
}

@Composable
private fun HomeFavoriteList(ctx: HomeContext, favorites: List<AppInfo>, modifier: Modifier) {
    val tokens = LocalFoldOsTokens.current
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
        items(favorites, key = { it.key }) { app ->
            Text(
                text = app.label,
                color = tokens.onSurface,
                fontSize = 29.sp,
                lineHeight = 36.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = homeTextShadow()),
                modifier = Modifier
                    .fillMaxWidth()
                    .homeTapGestures(onTap = { ctx.onLaunch(app) }, onLongPress = ctx.onLongPressHome)
                    .padding(vertical = 5.dp),
            )
        }
    }
}

/* ---------------- Hybrid panels ---------------- */

/** Cover: widget panel above an apps panel. Inner: apps panel left, widget column right. */
@Composable
internal fun HomeHybridPage(ctx: HomeContext, items: List<HomeItem>, modifier: Modifier = Modifier) {
    val now = rememberHomeClock()
    if (!ctx.isInner) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeSurfacePanel(Modifier.weight(1.15f).fillMaxWidth()) {
                HomeCellGrid(
                    cols = 2,
                    cells = listOf(HomeCell(0, 0, 2, 1), HomeCell(0, 1, 2, 1)),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fixedRows = 2,
                ) { index ->
                    val cardModifier = Modifier.fillMaxSize().padding(4.dp)
                    if (index == 0) HomeClockCard(now, cardModifier) else HomeCalendarCard(now, cardModifier)
                }
            }
            HomeSurfacePanel(Modifier.weight(1f).fillMaxWidth()) {
                HomeFavoriteGrid(ctx, ctx.favorites(items, 8), cols = 4, rows = 2, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    } else {
        Row(modifier.fillMaxSize()) {
            HomeSurfacePanel(Modifier.weight(1f).fillMaxHeight()) {
                HomeFavoriteGrid(ctx, ctx.favorites(items, 16), cols = 4, rows = 4, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HomeClockCard(now, Modifier.weight(1f).fillMaxWidth())
                HomeCalendarCard(now, Modifier.weight(1f).fillMaxWidth())
                HomeMediaCard(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** A plain cols x rows grid of app tiles, filled in order. */
@Composable
internal fun HomeFavoriteGrid(
    ctx: HomeContext,
    favorites: List<AppInfo>,
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val shown = favorites.take(cols * rows)
    val cells = remember(shown.size, cols) {
        List(shown.size) { index -> HomeCell(index % cols, index / cols) }
    }
    HomeCellGrid(
        cols = cols,
        cells = cells,
        modifier = modifier,
        fixedRows = rows,
    ) { index ->
        val app = shown[index]
        HomeAppTile(
            app = app,
            iconSize = ctx.iconSize,
            showLabel = ctx.labels,
            onClick = { ctx.onLaunch(app) },
            onLongClick = ctx.onLongPressHome,
        )
    }
}
