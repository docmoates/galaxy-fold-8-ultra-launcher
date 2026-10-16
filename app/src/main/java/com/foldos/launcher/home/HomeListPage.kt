package com.foldos.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale

/**
 * Minimal list (mock-up `HOME.*.list`). Cover: big clock header, large-text favorites and an
 * alphabet scrubber down the right edge. Inner: two panes split at the hinge, favorites (with the
 * scrubber) on the left, a 96sp clock plus the calendar and media built-ins (or the page's own
 * widget items) on the right.
 */
@Composable
internal fun HomeListPage(
    ctx: HomeContext,
    page: HomePage?,
    items: List<HomeItem>,
    modifier: Modifier = Modifier,
) {
    val now = rememberHomeClock()
    val pageId = page?.id ?: 0L
    if (!ctx.isInner) {
        val favorites = remember(items, ctx.sortedApps) { ctx.favorites(items, LIST_COVER_COUNT) }
        Column(modifier.fillMaxSize()) {
            HomeListHeader(now, timeSize = 66)
            HomeScrubbedFavoriteList(ctx, favorites, items, Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        val favorites = remember(items, ctx.sortedApps) { ctx.favorites(items, LIST_INNER_COUNT) }
        val widgetItems = remember(items, pageId) {
            items.filter { it.kind == ItemKind.WIDGET }.take(3)
        }
        val gap = if (ctx.hingeGutter > 0.dp) 32.dp else 24.dp
        Row(modifier.fillMaxSize()) {
            HomeScrubbedFavoriteList(
                ctx,
                favorites,
                items,
                Modifier.weight(1f).fillMaxHeight().padding(top = 30.dp),
            )
            Spacer(Modifier.width(gap))
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(top = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HomeListHeader(now, timeSize = 96, topPadding = 0.dp)
                if (widgetItems.isEmpty()) {
                    // The slots are several rows tall, so the built-ins take their multi-row
                    // agenda / player layouts (spanY == 1 would be the single-line variant).
                    HomeBuiltInWidget(HomeBuiltInIds.CALENDAR, 2, 2, Modifier.weight(1f).fillMaxWidth())
                    HomeBuiltInWidget(HomeBuiltInIds.MEDIA, 2, 2, Modifier.weight(1f).fillMaxWidth())
                } else {
                    widgetItems.forEach { item ->
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            HomeItemTile(ctx, item, widgetPadding = 0.dp)
                        }
                    }
                }
            }
        }
    }
}

private const val LIST_COVER_COUNT: Int = 12
private const val LIST_INNER_COUNT: Int = 14

/** Clock and date header (mock-up `.lhead` / `.lt` / `.ld`). */
@Composable
internal fun HomeListHeader(now: LocalDateTime, timeSize: Int, topPadding: Dp = 16.dp) {
    val tokens = LocalFoldOsTokens.current
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = topPadding, bottom = 4.dp)) {
        Text(
            text = now.homeTimeText(),
            color = tokens.onSurface,
            fontSize = timeSize.sp,
            fontWeight = FontWeight.Light,
            lineHeight = timeSize.sp,
            letterSpacing = (-0.03f * timeSize).sp,
            style = TextStyle(shadow = homeTextShadow()),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = now.homeDateText(long = true),
                color = tokens.onSurface.copy(alpha = 0.8f),
                fontSize = 14.5.sp,
                style = TextStyle(shadow = homeTextShadow()),
            )
            Text(
                text = "·",
                color = tokens.onSurface.copy(alpha = 0.8f),
                fontSize = 14.5.sp,
            )
            Icon(
                imageVector = Icons.Rounded.Event,
                contentDescription = null,
                tint = tokens.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.width(18.dp).height(18.dp),
            )
            Text(
                text = "No events today",
                color = tokens.onSurface.copy(alpha = 0.8f),
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = homeTextShadow()),
            )
        }
    }
}

/** The favorites list with the alphabet scrubber on its right edge (mock-up `.lst` + `.alpha`). */
@Composable
private fun HomeScrubbedFavoriteList(
    ctx: HomeContext,
    favorites: List<AppInfo>,
    items: List<HomeItem>,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letters = remember(favorites) { favorites.map { it.scrubberLetter() }.distinct() }
    Box(modifier) {
        HomeFavoriteList(ctx, favorites, items, listState, Modifier.fillMaxSize().padding(end = 22.dp))
        if (letters.size > 1) {
            HomeAlphabetScrubber(
                letters = letters,
                onLetter = { letter ->
                    val index = favorites.indexOfFirst { it.scrubberLetter() == letter }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 24.dp, bottom = 24.dp),
            )
        }
    }
}

/** First letter of the label in upper case, or '#' when it is not a letter. */
private fun AppInfo.scrubberLetter(): Char {
    val first = label.trim().firstOrNull() ?: return '#'
    return if (first.isLetter()) first.uppercaseChar() else '#'
}

@Composable
private fun HomeFavoriteList(
    ctx: HomeContext,
    favorites: List<AppInfo>,
    items: List<HomeItem>,
    listState: LazyListState,
    modifier: Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        items(favorites, key = { it.key }) { app ->
            val holder = remember { HomeAnchorHolder() }
            Box(Modifier.fillMaxWidth()) {
                // Mock-up `.li`: the name, then an accent dot when the app has notifications.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .homeAnchor(holder)
                        .homeTapGestures(
                            onTap = { ctx.onLaunch(app) },
                            onLongPress = { ctx.longPressApp(app, items, holder.bounds()) },
                        )
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = app.label,
                        color = tokens.onSurface,
                        fontSize = 29.sp,
                        lineHeight = 36.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = homeTextShadow()),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (ctx.badgeFor(app.packageName) > 0) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(tokens.accent, CircleShape),
                        )
                    }
                }
                HomeItemPressOverlay(ctx, ctx.itemFor(app, items))
            }
        }
    }
}

/**
 * Alphabet scrubber (mock-up `.alpha`): the letters spread down the edge; tapping or dragging
 * along them calls [onLetter] with the letter under the finger, which highlights in the accent.
 */
@Composable
internal fun HomeAlphabetScrubber(
    letters: List<Char>,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    var heightPx by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<Char?>(null) }

    fun letterAt(y: Float): Char? {
        if (letters.isEmpty() || heightPx <= 0) return null
        val index = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.size - 1)
        return letters[index]
    }

    Column(
        modifier = modifier
            .width(20.dp)
            .onSizeChanged { heightPx = it.height }
            .pointerInput(letters) {
                detectTapGestures(onTap = { position -> letterAt(position.y)?.let(onLetter) })
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { position ->
                        val letter = letterAt(position.y)
                        if (letter != null) {
                            active = letter
                            onLetter(letter)
                        }
                    },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                    onVerticalDrag = { change, _ ->
                        val letter = letterAt(change.position.y)
                        if (letter != null && letter != active) {
                            active = letter
                            onLetter(letter)
                        }
                    },
                )
            },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString().uppercase(Locale.getDefault()),
                color = if (letter == active) tokens.accent else tokens.onSurface.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
