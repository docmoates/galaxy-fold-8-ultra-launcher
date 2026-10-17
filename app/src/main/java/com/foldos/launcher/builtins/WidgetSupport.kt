package com.foldos.launcher.builtins

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Shared building blocks for the built-in widgets. They mirror the mock-up's `.w.wc` CSS:
 * header `.wh`, kicker `.k`, big number `.bn`, row `.r`, progress `.bar`, ring `.ring`,
 * transport `.ctl`, week strip `.wkm`. Everything here is internal to the builtins package.
 */

/** Height the mock-up reserves for the `.wh` header row (content height = box height - 23). */
internal val WidgetHeaderHeight: Dp = 23.dp

/** Tabular numerals for times and percentages (mock-up `font-variant-numeric: tabular-nums`). */
internal val TabularNumbers: TextStyle = TextStyle(fontFeatureSettings = "tnum")

/**
 * The themed card every built-in widget draws into (mock-up `.w.wc`, padding 12x14, or `.one`
 * padding 8x14 for single-row sizes). The content lambda receives the inner box constraints, so
 * widgets can scale their type to the real space like `wgRender` does with `iw` / `ih`.
 */
@Composable
internal fun BuiltInCard(
    modifier: Modifier,
    one: Boolean,
    highlight: Boolean = false,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val fill = if (highlight) tokens.highlight else tokens.surface
    val onFill = if (highlight) tokens.onHighlight else tokens.onSurface
    FoldOsSurface(
        modifier = modifier,
        color = fill,
        borderColor = if (highlight) Color.Transparent else tokens.surfaceBorder,
        contentPadding = if (one) PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        else PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides onFill) {
            BoxWithConstraints(Modifier.fillMaxSize(), content = content)
        }
    }
}

/** Header row (mock-up `.wh`): 15dp accent icon, 11.5sp semibold title, optional right-aligned count. */
@Composable
internal fun WidgetHeader(icon: ImageVector, title: String, trailingText: String? = null) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().height(WidgetHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(15.dp))
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalContentColor.current.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

/** Small uppercase label (mock-up `.k`). */
@Composable
internal fun WidgetKicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.07.em,
        color = LocalContentColor.current.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Single-line text that shrinks (down to [minScale] of [fontSize]) until it fits the width and height it is
 * given, then falls back to an ellipsis. No subcomposition, so it is safe inside intrinsic-size layouts.
 * It also grows back (up to the requested size) when it is given more room.
 */
@Composable
internal fun FitText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = TextStyle.Default,
    lineHeightEqualsSize: Boolean = false,
    minScale: Float = 0.45f,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    var scale by remember(text, fontSize) { mutableFloatStateOf(1f) }
    val size = fontSize * scale
    Text(
        text = text,
        fontSize = size,
        lineHeight = if (lineHeightEqualsSize) size else TextUnit.Unspecified,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = overflow,
        textAlign = textAlign,
        style = style,
        modifier = modifier,
        onTextLayout = { r ->
            val limitW = r.layoutInput.constraints.maxWidth
            val limitH = r.layoutInput.constraints.maxHeight
            val natW = r.multiParagraph.maxIntrinsicWidth
            val natH = r.multiParagraph.height
            val wr = if (limitW in 1 until 100_000 && natW > 0f) limitW / natW else Float.MAX_VALUE
            val hr = if (limitH in 1 until 100_000 && natH > 0f) limitH / natH else Float.MAX_VALUE
            val ratio = min(wr, hr)
            if (ratio < 1f) {
                val next = (scale * ratio * 0.98f).coerceAtLeast(minScale)
                if (next < scale - 0.005f) scale = next
            } else if (scale < 1f && ratio > 1.12f) {
                val next = min(1f, scale * ratio * 0.98f)
                if (next > scale + 0.005f) scale = next
            }
        },
    )
}

/** Large light numeral (mock-up `.bn`): weight 350, tight tracking, tabular digits, one line, scaled to fit. */
@Composable
internal fun BigNumber(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    textAlign: TextAlign? = null,
) {
    FitText(
        text = text,
        fontSize = size,
        modifier = modifier,
        color = color,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.03).em,
        textAlign = textAlign,
        style = TabularNumbers,
        lineHeightEqualsSize = true,
        minScale = 0.3f,
        overflow = TextOverflow.Clip,
    )
}

/** Bold 13-15sp label used for titles inside rows (mock-up `b`); shrinks to fit before it ellipsizes. */
@Composable
internal fun WidgetTitleText(text: String, modifier: Modifier = Modifier, size: TextUnit = 13.sp) {
    FitText(text = text, fontSize = size, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

/** Dimmed 12.5-13sp secondary text (mock-up `span { opacity: .7 }`). */
@Composable
internal fun WidgetDimText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 12.5.sp,
    alpha: Float = 0.7f,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    if (maxLines == 1) {
        FitText(
            text = text,
            fontSize = size,
            color = LocalContentColor.current.copy(alpha = alpha),
            textAlign = textAlign,
            style = TabularNumbers,
            modifier = modifier,
        )
    } else {
        Text(
            text = text,
            fontSize = size,
            color = LocalContentColor.current.copy(alpha = alpha),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            style = TabularNumbers,
            modifier = modifier,
        )
    }
}

/**
 * Space-between row of two short texts with an optional leading icon (mock-up `.r`). Both texts
 * are dimmed unless marked as emphasis.
 */
@Composable
internal fun WidgetRow(
    start: String,
    end: String? = null,
    icon: ImageVector? = null,
    startEmphasis: Boolean = false,
    endEmphasis: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        FitText(
            text = start,
            fontSize = 13.sp,
            fontWeight = if (startEmphasis) FontWeight.SemiBold else FontWeight.Normal,
            color = LocalContentColor.current.copy(alpha = if (startEmphasis) 1f else 0.7f),
            style = TabularNumbers,
            modifier = Modifier.weight(1f),
        )
        if (end != null) {
            Text(
                text = end,
                fontSize = 13.sp,
                fontWeight = if (endEmphasis) FontWeight.SemiBold else FontWeight.Normal,
                color = LocalContentColor.current.copy(alpha = if (endEmphasis) 1f else 0.7f),
                maxLines = 1,
                style = TabularNumbers,
            )
        }
    }
}

/** 5dp progress bar (mock-up `.bar`): track = 16% content color, fill = accent. */
@Composable
internal fun WidgetBar(fraction: Float, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(shape)
            .background(LocalContentColor.current.copy(alpha = 0.16f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .background(tokens.accent, shape),
        )
    }
}

/**
 * Conic progress ring (mock-up `.ring`): accent arc over a dim track with [content] (usually an
 * icon) centred inside. [fraction] in 0..1; null draws the track only.
 */
@Composable
internal fun ProgressRing(
    fraction: Float?,
    size: Dp,
    stroke: Dp = 5.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val track = LocalContentColor.current.copy(alpha = 0.16f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (fraction != null && fraction > 0f) {
                drawArc(
                    color = tokens.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * Accent pill button used for "Allow calendar access", "Allow notification access" and the like.
 * [compact] fits a single-row widget.
 */
@Composable
internal fun WidgetActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tokens.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tokens.onAccent, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            color = tokens.onAccent,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Centred message with an action button, for the "needs permission" state of a widget. */
@Composable
internal fun WidgetPermissionPrompt(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
    one: Boolean,
    modifier: Modifier = Modifier,
) {
    if (one) {
        Row(
            modifier = modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WidgetDimText(message, modifier = Modifier.weight(1f), maxLines = 2)
            WidgetActionButton(buttonText, onClick, compact = true)
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetDimText(message, maxLines = 3, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            WidgetActionButton(buttonText, onClick)
        }
    }
}

/** Previous / play-pause / next (mock-up `.ctl`, `.ctl.sm`). */
@Composable
internal fun TransportControls(
    playing: Boolean,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    small: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val iconSize = if (small) 22.dp else 28.dp
    val ppSize = if (small) 34.dp else 42.dp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (small) 8.dp else 14.dp),
    ) {
        Icon(
            Icons.Filled.SkipPrevious,
            contentDescription = "Previous",
            modifier = Modifier.size(iconSize).clip(CircleShape).clickable(onClick = onPrevious),
        )
        Box(
            Modifier
                .size(ppSize)
                .clip(CircleShape)
                .background(tokens.accent)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = tokens.onAccent,
                modifier = Modifier.size(iconSize),
            )
        }
        Icon(
            Icons.Filled.SkipNext,
            contentDescription = "Next",
            modifier = Modifier.size(iconSize).clip(CircleShape).clickable(onClick = onNext),
        )
    }
}

/** Seven-day strip with today highlighted (mock-up `.wkm`). The week starts on Sunday. */
@Composable
internal fun WeekStrip(today: LocalDate, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    // java.time: Monday = 1 ... Sunday = 7; the strip is Sunday-first.
    val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) {
            val day = sunday.plusDays(i.toLong())
            val on = day == today
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                    fontSize = 11.sp,
                    color = LocalContentColor.current.copy(alpha = if (on) 1f else 0.7f),
                )
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (on) tokens.accent else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) tokens.onAccent else LocalContentColor.current.copy(alpha = 0.7f),
                        style = TabularNumbers,
                    )
                }
            }
        }
    }
}

/** Fixed-width gap inside a horizontal row. */
@Composable
internal fun RowScope.WidgetGap(width: Dp) {
    Spacer(Modifier.width(width))
}

// ---------------------------------------------------------------------------------------------
// Sizing helpers (ports of wgRender's arithmetic; all in dp)
// ---------------------------------------------------------------------------------------------

/** Converts a dp amount to a font size so text scales with the box the way the mock-up's px does. */
@Composable
internal fun dpToSp(dp: Float): TextUnit = with(LocalDensity.current) { dp.dp.toSp() }

/** Smallest of the given dp amounts (shorthand for the mock-up's `Math.min(...)` font rules). */
internal fun minDp(vararg values: Float): Float = values.minOrNull() ?: 0f

/**
 * `gridFit(cell, maxCols)` from the mock-up: how many [cell]-sized tiles fit across [width] (capped
 * at [maxCols]) and down [height], both at least 1, with a 6dp gap.
 */
internal fun gridFit(width: Float, height: Float, cell: Float, maxCols: Int): Pair<Int, Int> {
    val n = max(1, min(maxCols, floor((width + 6f) / (cell + 6f)).toInt()))
    val r = max(1, floor((height + 6f) / (cell + 6f)).toInt())
    return n to r
}

/** Repeats [items] cyclically to a list of [count] entries (mock-up `cyc`). */
internal fun <T> cycle(items: List<T>, count: Int): List<T> =
    if (items.isEmpty() || count <= 0) emptyList() else List(count) { items[it % items.size] }

// ---------------------------------------------------------------------------------------------
// Time helpers
// ---------------------------------------------------------------------------------------------

/** Wall clock that recomposes at the start of every minute. */
@Composable
internal fun rememberWidgetClock(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            val toNextMinute = (60 - now.second) * 1000L - (now.nano / 1_000_000L)
            delay(toNextMinute.coerceIn(250L, 60_000L))
        }
    }
    return now
}

/** A value that changes every [periodMillis]; read it to recompose on that cadence. */
@Composable
internal fun rememberTicker(periodMillis: Long): Long {
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            delay(periodMillis)
            tick = System.currentTimeMillis()
        }
    }
    return tick
}

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
private val clockAmPmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val longDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val weekdayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

/** "9:41" */
internal fun LocalDateTime.clockText(): String = format(clockFormatter)

/** "9:41 AM" */
internal fun LocalDateTime.clockAmPmText(): String = format(clockAmPmFormatter)

/** "Fri, Oct 16" */
internal fun LocalDateTime.shortDateText(): String = format(shortDateFormatter)

/** "Friday, October 16" */
internal fun LocalDateTime.longDateText(): String = format(longDateFormatter)

/** "Oct 16" */
internal fun LocalDateTime.monthDayText(): String = format(monthDayFormatter)

/** "Fri" */
internal fun LocalDateTime.weekdayText(): String = format(weekdayFormatter)

/** "in 19 min", "in 2 h", "now" or "" when [epochMillis] is in the past. */
internal fun relativeFromNow(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diffMin = (epochMillis - nowMillis) / 60_000L
    return when {
        diffMin < 0 -> ""
        diffMin < 1 -> "now"
        diffMin < 60 -> "in $diffMin min"
        diffMin < 60 * 24 -> "in ${diffMin / 60} h"
        else -> "in ${diffMin / (60 * 24)} d"
    }
}
