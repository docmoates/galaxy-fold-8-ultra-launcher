package com.foldos.launcher.builtins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlin.math.floor

/**
 * The "Glance" header (mock-up `WG.glance` / `.glance`): big time beside a column with the long
 * date, the weather line and the next calendar event. Two rows or more add today's agenda below.
 */
@Composable
fun GlanceWidget(spanX: Int, spanY: Int, modifier: Modifier, weatherProvider: WeatherProvider) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val iw = maxWidth.value
        val ih = maxHeight.value
        val now = rememberWidgetClock()
        val weather by weatherProvider.weather.collectAsStateWithLifecycle(initialValue = null)
        val calendarGranted = rememberPermissionGranted(CALENDAR_PERMISSION)
        val events by rememberTodayEvents(calendarGranted)
        val requester = LocalPermissionRequester.current
        val tokens = LocalFoldOsTokens.current
        val next = events.nextEvent()

        val headerHeight = if (one) ih else minDp(ih * 0.45f, 64f)
        val fs = minDp(headerHeight * 0.9f, iw * 0.3f, 54f)
        val lineSize: TextUnit = if (one) 12.5.sp else 13.5.sp
        val showWeather = spanX >= 3 || !one
        val showEvent = spanX >= 4 || !one

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (spanX >= 3) 18.dp else 12.dp),
            ) {
                BigNumber(now.clockText(), dpToSp(fs))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FitText(
                        text = if (spanX >= 3) now.longDateText() else now.shortDateText(),
                        fontSize = if (one) 13.5.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val w = weather
                    if (showWeather && w != null) {
                        GlanceLine(w.condition.icon, "${w.tempText} · ${w.location}", lineSize)
                    }
                    if (showEvent) {
                        when {
                            !calendarGranted -> GlanceLine(
                                Icons.Outlined.Event,
                                "Allow calendar access",
                                lineSize,
                                color = tokens.accent,
                                modifier = Modifier.clickable { requester(CALENDAR_PERMISSION) },
                            )
                            next != null -> GlanceLine(
                                Icons.Outlined.Event,
                                "${next.title} at ${next.startText()}",
                                lineSize,
                            )
                            else -> GlanceLine(Icons.Outlined.Event, "No more events today", lineSize)
                        }
                    }
                }
            }
            if (!one) {
                val remaining = ih - headerHeight - 8f
                val n = floor((remaining + 9f) / 47f).toInt().coerceAtLeast(0)
                if (n > 0 && calendarGranted) {
                    val upcoming = events.filter { it.endMillis > System.currentTimeMillis() }.take(n)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (upcoming.isEmpty()) {
                            WidgetDimText("Nothing else on today's calendar", size = 13.sp)
                        } else {
                            upcoming.forEach { AgendaEventRow(it) }
                        }
                    }
                }
            }
        }
    }
}

/** One `.gd span` line: 17dp icon + text at 82% opacity. */
@Composable
private fun GlanceLine(
    icon: ImageVector,
    text: String,
    size: TextUnit,
    color: Color = LocalContentColor.current.copy(alpha = 0.82f),
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        FitText(text, fontSize = size, color = color, modifier = Modifier.weight(1f, fill = false))
    }
}
