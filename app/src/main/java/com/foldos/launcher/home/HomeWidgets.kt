package com.foldos.launcher.home

import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.a11y.rememberA11yPagerScroll
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.widgets.WidgetSlot
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * `BuiltInWidget.id`s the home styles seed (mock-up `WG` templates). They equal the ids of
 * `com.foldos.launcher.builtins.FoldOsBuiltInWidgets` (`"foldos.<name>"`), so a seeded item resolves
 * to the real Compose widget through `graph.builtIns.byId`. For an id the registry does not know
 * ([NOTES], [SMART], or a registry without that widget) [HomeBuiltInWidget] draws a themed
 * placeholder card instead, so the dashboard, list and hybrid styles always look complete.
 */
object HomeBuiltInIds {
    const val GLANCE: String = "foldos.glance"
    const val CLOCK: String = "foldos.clock"
    const val WEATHER: String = "foldos.weather"
    const val CALENDAR: String = "foldos.calendar"
    const val DEVICES: String = "foldos.devices"
    const val MEDIA: String = "foldos.media"
    const val TODO: String = "foldos.todo"
    const val FOCUS: String = "foldos.focus"
    const val NEWS: String = "foldos.news"
    const val NOTES: String = "foldos.notes"
    const val SMART: String = "foldos.smart"

    val ALL: List<String> = listOf(GLANCE, CLOCK, WEATHER, CALENDAR, DEVICES, MEDIA, TODO, FOCUS, NEWS, NOTES, SMART)

    /** Display title for a built-in id (used as `HomeItem.title` when seeding). */
    fun title(id: String): String = when (id) {
        GLANCE -> "At a glance"
        CLOCK -> "Clock"
        WEATHER -> "Weather"
        CALENDAR -> "Calendar"
        DEVICES -> "Devices"
        MEDIA -> "Now playing"
        TODO -> "To-do"
        FOCUS -> "Focus"
        NEWS -> "News"
        NOTES -> "Notes"
        SMART -> "Suggested"
        else -> id.substringAfterLast('.')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /** Built-ins drawn with the theme highlight colors (mock-up `.w.hi`) when they lead a dashboard. */
    fun isHero(id: String?): Boolean = id == GLANCE || id == CLOCK
}

/**
 * One widget item on a home page, filling the box it is given. Built-ins come from
 * `graph.builtIns.byId(item.builtinId)` (placeholder card when the registry lacks the id); hosted
 * app widgets go through `widgets/` [WidgetSlot] with the per-cell size derived from the box.
 *
 * @param highlight draw a built-in placeholder with the highlight colors (dashboard hero card).
 */
@Composable
fun HomeWidgetItem(item: HomeItem, modifier: Modifier = Modifier, highlight: Boolean = false) {
    BoxWithConstraints(modifier) {
        val spanX = item.spanX.coerceAtLeast(1)
        val spanY = item.spanY.coerceAtLeast(1)
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val cellWidthDp = if (widthDp.isFinite()) (widthDp / spanX).roundToInt().coerceAtLeast(1) else 70
        val cellHeightDp = if (heightDp.isFinite()) (heightDp / spanY).roundToInt().coerceAtLeast(1) else 70
        when {
            item.isBuiltInWidget -> HomeBuiltInWidget(
                id = item.builtinId ?: HomeBuiltInIds.GLANCE,
                spanX = spanX,
                spanY = spanY,
                modifier = Modifier.fillMaxSize(),
                highlight = highlight,
            )
            item.isAppWidget -> WidgetSlot(item, cellWidthDp, cellHeightDp, Modifier.fillMaxSize())
            else -> HomeDashCard(
                title = item.title ?: "Widget",
                icon = Icons.Rounded.Widgets,
                body = item.title ?: "Widget",
                modifier = Modifier.fillMaxSize(),
                caption = "Choose a widget for this slot",
                bodySize = 18,
            )
        }
    }
}

/**
 * A FoldOS built-in widget by id: the registry's Compose content when it knows the id, otherwise
 * the matching placeholder card from [HomeBuiltInIds] (themed, real clock and battery, everything
 * else labelled as not connected yet).
 */
@Composable
fun HomeBuiltInWidget(
    id: String,
    spanX: Int,
    spanY: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val graph = LocalAppGraph.current
    // Items stored before the ids carried the "foldos." prefix still resolve.
    val widget = remember(graph, id) { graph.builtIns.byId(id) ?: graph.builtIns.byId("foldos.$id") }
    if (widget != null) {
        widget.content(spanX, spanY, modifier)
    } else {
        HomeBuiltInFallback(id, spanX, spanY, modifier, highlight)
    }
}

/**
 * A widget stack: the items sharing a `stackId` in one slot, flipped by a horizontal swipe, with
 * the stack dots top-right (mock-up `.stk`). [content] draws one item filling the slot.
 *
 * @param smartRules when true (the Smart feature is on) and a member carries a time rule
 *   (`rule:HH:MM-HH:MM` in its title or built-in id, see [homeTimeRuleOf]), the stack flips to the
 *   member whose window contains the current time ([homeSmartStackIndex]) and re-evaluates every
 *   minute; a manual swipe holds until the matching member changes.
 */
@Composable
fun HomeStackSlot(
    items: List<HomeItem>,
    modifier: Modifier = Modifier,
    smartRules: Boolean = false,
    content: @Composable (HomeItem) -> Unit,
) {
    val ordered = remember(items) { items.sortedBy { it.stackIndex } }
    if (ordered.isEmpty()) return
    val tokens = LocalFoldOsTokens.current
    val pagerState = rememberPagerState { ordered.size }
    val scrollToPage = rememberA11yPagerScroll(pagerState)
    val hasRules = remember(ordered) { hasHomeSmartRules(ordered) }
    if (smartRules && hasRules) {
        val now = rememberHomeClock()
        val target = remember(ordered, now.hour, now.minute) { homeSmartStackIndex(ordered, now.toLocalTime()) }
        LaunchedEffect(target) {
            if (target != null && target in ordered.indices && target != pagerState.currentPage) {
                scrollToPage(target)
            }
        }
    }
    Box(modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> ordered[index].id },
        ) { page ->
            Box(Modifier.fillMaxSize()) { content(ordered[page]) }
        }
        if (ordered.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(ordered.size) { index ->
                    val alpha = if (index == pagerState.currentPage) 1f else 0.3f
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(tokens.onSurface.copy(alpha = alpha), CircleShape),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Placeholder cards for the built-in ids (mock-up WG templates)                               */
/* ------------------------------------------------------------------------------------------ */

@Suppress("UNUSED_PARAMETER") // spanY: the frames stretch to whatever height the slot gives them.
@Composable
private fun HomeBuiltInFallback(id: String, spanX: Int, spanY: Int, modifier: Modifier, highlight: Boolean) {
    val now = rememberHomeClock()
    // One cell wide gets the narrow variant of each card.
    val compact = spanX <= 1
    when (id) {
        HomeBuiltInIds.GLANCE -> HomeGlanceCard(now, modifier, highlight, compact)
        HomeBuiltInIds.CLOCK -> HomeClockFallback(now, modifier, highlight, compact)
        HomeBuiltInIds.WEATHER -> HomeWeatherFallback(modifier, highlight, compact)
        HomeBuiltInIds.CALENDAR -> HomeCalendarFallback(now, modifier, highlight, compact)
        HomeBuiltInIds.DEVICES -> HomeDevicesFallback(modifier, highlight, compact)
        HomeBuiltInIds.MEDIA -> HomeMediaFallback(modifier, highlight, compact)
        HomeBuiltInIds.TODO -> HomeTodoFallback(modifier, highlight, compact)
        HomeBuiltInIds.FOCUS -> HomeFocusFallback(modifier, highlight, compact)
        HomeBuiltInIds.NEWS -> HomeNewsFallback(modifier, highlight, compact)
        HomeBuiltInIds.NOTES -> HomeCardFrame(modifier, highlight, compact) {
            HomeCardKicker("Notes")
            HomeCardText("Pin a note here. Samsung Notes and Google Keep connect in phase 5.", 14)
        }
        HomeBuiltInIds.SMART -> HomeCardFrame(modifier, highlight, compact) {
            HomeCardKicker("Suggested")
            Spacer(Modifier.weight(1f))
            HomeCardText("App suggestions learn from your day once the AI engine is on.", 14)
        }
        else -> HomeDashCard(
            title = HomeBuiltInIds.title(id),
            icon = Icons.Rounded.Widgets,
            body = HomeBuiltInIds.title(id),
            modifier = modifier,
            caption = "This built-in is not installed yet",
            highlight = highlight,
            bodySize = 18,
        )
    }
}

/**
 * Card frame shared by the placeholders (mock-up `.w`): theme surface or highlight, hairline, the
 * theme radius and 16x18dp padding (14x16 when [compact]). Content color follows the fill.
 */
@Composable
internal fun HomeCardFrame(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(tokens.radius)
    val background = if (highlight) tokens.highlight else tokens.surface
    val foreground = if (highlight) tokens.onHighlight else tokens.onSurface
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Column(
            modifier = modifier
                .clip(shape)
                .background(background, shape)
                .border(1.dp, if (highlight) Color.Transparent else tokens.surfaceBorder, shape)
                .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/** Small uppercase card title (mock-up `.k`). */
@Composable
internal fun HomeCardKicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        color = LocalContentColor.current.copy(alpha = 0.72f),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Big numeral (mock-up `.big`). */
@Composable
private fun HomeCardBig(text: String, size: Int) {
    Text(
        text = text,
        color = LocalContentColor.current,
        fontSize = size.sp,
        lineHeight = (size * 0.98f).sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.03f * size).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Secondary line (mock-up `.s`). */
@Composable
private fun HomeCardText(text: String, size: Int, alpha: Float = 0.72f, bold: Boolean = false, maxLines: Int = 2) {
    Text(
        text = text,
        color = LocalContentColor.current.copy(alpha = alpha),
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Icon + text line (mock-up `.ln`). */
@Composable
private fun HomeCardLine(icon: ImageVector, text: String, alpha: Float = 0.9f) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = LocalContentColor.current.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        Text(
            text = text,
            color = LocalContentColor.current.copy(alpha = alpha),
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Thin progress bar (mock-up `.bar`). */
@Composable
private fun HomeCardBar(fraction: Float, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(LocalContentColor.current.copy(alpha = 0.15f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(tokens.accent, RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun HomeGlanceCard(now: LocalDateTime, modifier: Modifier, highlight: Boolean, compact: Boolean) {
    HomeCardFrame(modifier, highlight, compact) {
        if (compact) {
            HomeCardBig(now.homeTimeText(), 40)
            Spacer(Modifier.weight(1f))
            HomeCardText(now.homeDateText(long = false), 14, alpha = 1f, bold = true, maxLines = 1)
            HomeCardLine(Icons.Rounded.Event, "No events today")
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                HomeCardBig(now.homeTimeText(), 54)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    HomeCardText(now.homeDateText(long = true), 15, alpha = 1f, bold = true, maxLines = 1)
                    HomeCardLine(Icons.Rounded.WbSunny, "Weather not connected", alpha = 0.82f)
                    HomeCardLine(Icons.Rounded.Event, "No events today", alpha = 0.82f)
                }
            }
        }
    }
}

@Composable
private fun HomeClockFallback(now: LocalDateTime, modifier: Modifier, highlight: Boolean, compact: Boolean) {
    HomeCardFrame(modifier, highlight, compact) {
        HomeCardKicker(now.homeDateText(long = false))
        HomeCardBig(now.homeTimeText(), if (compact) 42 else 58)
        Spacer(Modifier.weight(1f))
        HomeCardLine(Icons.Rounded.WbSunny, "Weather not connected")
        if (!compact) HomeCardLine(Icons.Rounded.Event, "No events today")
    }
}

@Composable
private fun HomeWeatherFallback(modifier: Modifier, highlight: Boolean, compact: Boolean) {
    HomeCardFrame(modifier, highlight, compact) {
        HomeCardKicker("Weather")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeCardBig("--°", if (compact) 40 else 46)
            Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.weight(1f))
        HomeCardText("Connect a weather source", 13)
        if (!compact) HomeCardText("H --° · L --°", 13)
    }
}

@Composable
private fun HomeCalendarFallback(now: LocalDateTime, modifier: Modifier, highlight: Boolean, compact: Boolean) {
    val tokens = LocalFoldOsTokens.current
    HomeCardFrame(modifier, highlight, compact) {
        HomeCardKicker("Today · ${now.homeDateText(long = false)}")
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeEventRow(tokens.accent, "Nothing scheduled", "Your calendars appear here")
            if (!compact) HomeEventRow(LocalContentColor.current.copy(alpha = 0.35f), "Calendar connects in phase 5", "Google, Samsung and Outlook")
        }
    }
}

/** One agenda row with a colored bar (mock-up `.ev`). */
@Composable
private fun HomeEventRow(color: Color, title: String, detail: String) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color, RoundedCornerShape(2.dp)),
        )
        Column {
            HomeCardText(title, 13, alpha = 1f, bold = true, maxLines = 1)
            HomeCardText(detail, 12, alpha = 0.7f, maxLines = 1)
        }
    }
}

@Composable
private fun HomeDevicesFallback(modifier: Modifier, highlight: Boolean, compact: Boolean) {
    val battery = rememberHomeBatteryPercent()
    HomeCardFrame(modifier, highlight, compact) {
        HomeCardKicker("Devices")
        val devices = listOf(
            Triple(Icons.Rounded.Smartphone, "Fold", battery),
            Triple(Icons.Rounded.Watch, "Watch", null),
            Triple(Icons.Rounded.Headphones, "Buds", null),
            Triple(Icons.Rounded.RadioButtonChecked, "Ring", null),
        )
        if (compact) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                devices.forEach { (icon, name, percent) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeRing(percent, icon, 23.dp)
                        HomeCardText(name, 12, alpha = 0.7f, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        HomeCardText(percent?.let { "$it%" } ?: "--", 12, alpha = 1f, bold = true, maxLines = 1)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                devices.forEach { (icon, name, percent) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HomeRing(percent, icon, 54.dp)
                        HomeCardText(name, 12, alpha = 0.7f, maxLines = 1)
                        HomeCardText(percent?.let { "$it%" } ?: "--", 12, alpha = 1f, bold = true, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Battery ring (mock-up `.ring`): track circle, accent arc for [percent], icon in the middle. */
@Composable
private fun HomeRing(percent: Int?, icon: ImageVector, size: Dp) {
    val tokens = LocalFoldOsTokens.current
    val track = LocalContentColor.current.copy(alpha = 0.15f)
    val accent = tokens.accent
    val strokeWidth = if (size < 30.dp) 3.dp else 5.dp
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val diameter = this.size.minDimension - stroke.width
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(inset, inset)
            drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (percent != null) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * percent.coerceIn(0, 100) / 100f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.42f))
    }
}

/** The phone's own battery level from [BatteryManager]; null when unavailable. */
@Composable
private fun rememberHomeBatteryPercent(): Int? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()?.takeIf { it in 0..100 }
    }
}

@Composable
private fun HomeMediaFallback(modifier: Modifier, highlight: Boolean, compact: Boolean) {
    val tokens = LocalFoldOsTokens.current
    HomeCardFrame(modifier, highlight, compact) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!compact) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(tokens.radius * 0.6f + 4.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFF6A59), Color(0xFFC1357A), Color(0xFF3B2A8C))),
                        ),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HomeCardKicker("Now playing")
                HomeCardText("Nothing playing", 16, alpha = 1f, bold = true, maxLines = 1)
                HomeCardText("Media controls appear here", 13, maxLines = 1)
                HomeCardBar(0f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(28.dp))
                    Box(
                        Modifier
                            .size(if (compact) 34.dp else 42.dp)
                            .background(tokens.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = "Pause", tint = tokens.onAccent, modifier = Modifier.size(24.dp))
                    }
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeTodoFallback(modifier: Modifier, highlight: Boolean, compact: Boolean) {
    val tokens = LocalFoldOsTokens.current
    HomeCardFrame(modifier, highlight, compact) {
        HomeCardKicker("To-do")
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            HomeTodoRow(Icons.Rounded.CheckBoxOutlineBlank, "Add your first task", done = false, accent = tokens.accent)
            HomeTodoRow(Icons.Rounded.CheckBoxOutlineBlank, "Connect Todoist or Google Tasks", done = false, accent = tokens.accent)
            if (!compact) HomeTodoRow(Icons.Rounded.CheckBox, "Install FoldOS", done = true, accent = tokens.accent)
        }
    }
}

@Composable
private fun HomeTodoRow(icon: ImageVector, text: String, done: Boolean, accent: Color) {
    val color = LocalContentColor.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = if (done) accent else color, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            color = color.copy(alpha = if (done) 0.5f else 1f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeFocusFallback(modifier: Modifier, highlight: Boolean, compact: Boolean) {
    HomeCardFrame(modifier, highlight, compact) {
        HomeCardKicker("Focus")
        HomeCardBig("25:00", if (compact) 40 else 44)
        Spacer(Modifier.weight(1f))
        HomeCardBar(0f)
        HomeCardText("Deep work · tap to start", 13, maxLines = 1)
    }
}

@Composable
private fun HomeNewsFallback(modifier: Modifier, highlight: Boolean, compact: Boolean) {
    HomeCardFrame(modifier, highlight, compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HomeCardKicker("News", Modifier.weight(1f))
            Icon(Icons.Rounded.Article, contentDescription = null, modifier = Modifier.size(18.dp), tint = LocalContentColor.current.copy(alpha = 0.8f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                HomeCardText("Connect a news source", 14, alpha = 1f, maxLines = 2)
                HomeCardText("RSS and Google News · phase 5", 12, alpha = 0.65f, maxLines = 1)
            }
            if (!compact) {
                Column {
                    HomeCardText("Headlines follow your theme", 14, alpha = 1f, maxLines = 2)
                    HomeCardText("FoldOS · now", 12, alpha = 0.65f, maxLines = 1)
                }
            }
        }
    }
}
