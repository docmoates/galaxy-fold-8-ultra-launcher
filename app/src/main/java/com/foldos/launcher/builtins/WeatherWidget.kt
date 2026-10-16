package com.foldos.launcher.builtins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min

/**
 * Current conditions (mock-up `wgRender` kind `weather`): temperature, condition, high / low;
 * wider sizes add the hourly strip, taller ones the daily list. Data comes from [WeatherProvider].
 */
@Composable
fun WeatherWidget(spanX: Int, spanY: Int, modifier: Modifier, weatherProvider: WeatherProvider) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val iw = maxWidth.value
        val ih = maxHeight.value
        val weather by weatherProvider.weather.collectAsStateWithLifecycle(initialValue = null)
        val w = weather
        if (w == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WidgetDimText("No weather data", textAlign = TextAlign.Center)
            }
            return@BuiltInCard
        }

        if (one) {
            val fs = minDp(ih * 0.78f, if (spanX == 1) 30f else 46f)
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BigNumber(w.tempText, dpToSp(fs))
                Icon(w.condition.icon, contentDescription = w.condition.label, modifier = Modifier.size(24.dp))
                if (spanX >= 2) {
                    Column(Modifier.weight(1f)) {
                        WidgetTitleText(w.location, size = 12.5.sp)
                        WidgetDimText(w.condition.label)
                    }
                }
                if (spanX >= 4) {
                    Column(horizontalAlignment = Alignment.End) {
                        WidgetTitleText("H ${w.highF}°", size = 12.5.sp)
                        WidgetDimText("L ${w.lowF}°")
                    }
                }
            }
        } else {
            val ch = ih
            val fs = minDp(ch * (if (spanY >= 3) 0.26f else 0.42f), iw * 0.3f)
            val narrow = spanX <= 2
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BigNumber(w.tempText, dpToSp(fs))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(w.condition.icon, contentDescription = w.condition.label, modifier = Modifier.size(24.dp))
                        WidgetTitleText(w.location, size = 13.sp)
                    }
                }
                if (narrow) {
                    WidgetRow(start = w.condition.label)
                    WidgetRow(start = "H ${w.highF}°", end = "L ${w.lowF}°")
                } else {
                    WidgetRow(start = w.condition.label, end = "H ${w.highF}° · L ${w.lowF}°")
                }
                if (spanY >= 2 && spanX >= 3) {
                    HourlyStrip(cycle(w.hourly, if (spanX >= 4) 6 else 4))
                }
                if (spanY >= 3) {
                    val count = min(w.daily.size, if (spanY == 3) (if (spanX >= 3) 3 else 4) else 6)
                    DailyList(w.daily.take(count))
                }
                if (spanY == 2 && narrow) {
                    WidgetRow(start = "Rain ${w.rainChancePercent}%", end = "Wind ${w.windMph}")
                }
            }
        }
    }
}

/** Mock-up `.hourly`: evenly spaced columns of label / icon / temperature. */
@Composable
private fun HourlyStrip(hours: List<HourlyForecast>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        hours.forEach { h ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                WidgetDimText(h.label, size = 12.sp, alpha = 0.6f)
                Icon(h.condition.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                WidgetTitleText("${h.tempF}°", size = 12.sp)
            }
        }
    }
}

/** Mock-up `.daily`: day, icon, high, dimmed low per row. */
@Composable
private fun DailyList(days: List<DailyForecast>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { d ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WidgetTitleText(d.day, size = 13.sp, modifier = Modifier.width(34.dp))
                Icon(
                    d.condition.icon,
                    contentDescription = d.condition.label,
                    tint = LocalContentColor.current.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.weight(1f))
                WidgetTitleText("${d.highF}°", size = 13.sp)
                WidgetDimText("${d.lowF}°", size = 13.sp, alpha = 0.55f, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            }
        }
    }
}
