package com.foldos.launcher.builtins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Digital clock (mock-up `wgRender` kind `clock`, digital branch): big time, date, next alarm
 * from `AlarmManager.nextAlarmClock`; 3 rows add the weather and next-event lines, 4 rows the
 * week strip. Single-row sizes lay everything out horizontally.
 */
@Composable
fun ClockWidget(spanX: Int, spanY: Int, modifier: Modifier, weatherProvider: WeatherProvider) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val iw = maxWidth.value
        val ih = maxHeight.value
        val now = rememberWidgetClock()
        val alarm = rememberNextAlarm()
        val weather by weatherProvider.weather.collectAsStateWithLifecycle(initialValue = null)
        val alarmText = alarm?.alarmText(now) ?: "No alarm"

        if (one) {
            val fs = minDp(ih * 0.78f, iw * (if (spanX >= 4) 0.2f else 0.34f))
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BigNumber(now.clockText(), dpToSp(fs))
                when {
                    spanX >= 4 -> {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            WidgetTitleText(now.longDateText(), size = 13.sp)
                            val w = weather
                            if (w != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(w.condition.icon, null, Modifier.size(14.dp), tint = LocalContentColor.current.copy(alpha = 0.7f))
                                    WidgetDimText("${w.tempText} · ${w.location}")
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Alarm, null, Modifier.size(14.dp))
                                WidgetTitleText(alarmText, size = 12.5.sp)
                            }
                            WidgetDimText(if (alarm != null) "Next alarm" else "None set", size = 12.sp)
                        }
                    }
                    spanX >= 2 -> {
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            WidgetTitleText(now.monthDayText(), size = 12.sp)
                            WidgetDimText(now.weekdayText(), size = 12.sp, alpha = 0.75f)
                        }
                    }
                }
            }
        } else {
            val ch = ih
            val fs = minDp(ch * (if (spanY >= 3) 0.32f else 0.5f), iw * (if (spanX >= 3) 0.3f else 0.44f))
            val calendarGranted = rememberPermissionGranted(CALENDAR_PERMISSION)
            val events by rememberTodayEvents(calendarGranted && spanY >= 3)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(now.clockText(), dpToSp(fs))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        now.shortDateText(),
                        fontSize = 13.sp,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.Alarm, null, Modifier.size(18.dp), tint = LocalContentColor.current.copy(alpha = 0.7f))
                    Spacer(Modifier.size(4.dp))
                    WidgetDimText(alarmText, size = 13.sp, textAlign = TextAlign.End)
                }
                if (spanY >= 3) {
                    val w = weather
                    if (w != null) {
                        WidgetRow(
                            start = "${w.tempText} ${w.condition.label.lowercase()} · ${w.location}",
                            icon = w.condition.icon,
                        )
                    }
                    val next = events.nextEvent()
                    val eventText = when {
                        !calendarGranted -> "Calendar access off"
                        next == null -> "No more events today"
                        else -> {
                            val rel = relativeFromNow(next.beginMillis)
                            if (rel.isEmpty()) "${next.title} · now" else "${next.title} $rel"
                        }
                    }
                    WidgetRow(start = eventText, icon = Icons.Outlined.Event)
                }
                if (spanY >= 4) {
                    WeekStrip(now.toLocalDate())
                }
            }
        }
    }
}
