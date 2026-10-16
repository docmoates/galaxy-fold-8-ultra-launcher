package com.foldos.launcher.builtins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.theme.LocalFoldOsTokens
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

/**
 * Today's agenda from `CalendarContract.Instances` (mock-up `wgRender` kind `calendar`). Without
 * READ_CALENDAR it shows an "Allow calendar access" button that calls [LocalPermissionRequester].
 * Single row: date box + next event; taller: header with the count, optional week strip, and as
 * many colour-barred event rows as fit (41dp each).
 */
@Composable
fun CalendarAgendaWidget(spanX: Int, spanY: Int, modifier: Modifier) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val ih = maxHeight.value
        val granted = rememberPermissionGranted(CALENDAR_PERMISSION)
        val requester = LocalPermissionRequester.current
        if (!granted) {
            WidgetPermissionPrompt(
                message = "See today's events here.",
                buttonText = "Allow calendar access",
                onClick = { requester(CALENDAR_PERMISSION) },
                one = one,
            )
            return@BuiltInCard
        }

        val now = rememberWidgetClock()
        val events by rememberTodayEvents(true)
        val nowMillis = System.currentTimeMillis()
        val upcoming = events.filter { it.allDay || it.endMillis > nowMillis }

        if (one) {
            val next = events.nextEvent(nowMillis) ?: upcoming.firstOrNull()
            val after = upcoming.firstOrNull { it !== next && !it.allDay && it.beginMillis > (next?.beginMillis ?: 0L) }
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DateBox(month = now.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), day = now.dayOfMonth, height = ih.dp)
                if (spanX >= 2) {
                    Column(Modifier.weight(1f)) {
                        if (next == null) {
                            WidgetTitleText("No events today", size = 13.sp)
                            WidgetDimText("Enjoy the free time")
                        } else {
                            WidgetTitleText(next.title, size = 13.sp)
                            val rel = if (next.allDay) "" else relativeFromNow(next.beginMillis, nowMillis)
                            val where = next.location?.trim().orEmpty()
                            val parts = buildList {
                                add(next.startText())
                                if (where.isNotEmpty()) add(where)
                                if (rel.isNotEmpty()) add(rel)
                            }
                            WidgetDimText(parts.joinToString(" · "))
                        }
                    }
                }
                if (spanX >= 4 && after != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        WidgetDimText("then ${after.title}", alpha = 0.75f, textAlign = TextAlign.End)
                        WidgetDimText(after.startText(), alpha = 0.75f, textAlign = TextAlign.End)
                    }
                }
            }
        } else {
            val ch = ih - WidgetHeaderHeight.value
            val strip = spanX >= 3 && spanY >= 3
            val n = max(1, floor((ch - (if (strip) 48f else 0f) + 9f) / 41f).toInt())
            val shown = (if (upcoming.isNotEmpty()) upcoming else events).take(n)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WidgetHeader(Icons.Outlined.Event, "Today", trailingText = "${events.size} today")
                if (strip) {
                    WeekStrip(now.toLocalDate(), Modifier.height(44.dp))
                }
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        WidgetDimText("No events today", size = 13.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        shown.forEach { AgendaEventRow(it) }
                    }
                }
            }
        }
    }
}

/** Mock-up `.ev`: 4dp colour bar, bold title, dimmed "10:00 – 10:15 · Teams" line. */
@Composable
internal fun AgendaEventRow(event: AgendaEvent, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Row(modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(event.color(tokens.accent)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            WidgetTitleText(event.title, size = 13.5.sp)
            WidgetDimText(event.subtitle())
        }
    }
}

/** Mock-up `.dbox`: accent-tinted month / day block for single-row sizes. */
@Composable
private fun DateBox(month: String, day: Int, height: Dp) {
    val tokens = LocalFoldOsTokens.current
    Column(
        Modifier
            .width(46.dp)
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.accent.copy(alpha = 0.2f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            month.uppercase(Locale.getDefault()),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = tokens.accent,
            maxLines = 1,
        )
        Text(
            day.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = tokens.accent,
            maxLines = 1,
            style = TabularNumbers,
        )
    }
}
