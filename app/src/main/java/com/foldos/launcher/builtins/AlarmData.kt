package com.foldos.launcher.builtins

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The next alarm the system will fire, from `AlarmManager.nextAlarmClock`; null when none is set. */
internal fun readNextAlarm(context: Context): LocalDateTime? {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return null
    val info: AlarmManager.AlarmClockInfo = runCatching { manager.nextAlarmClock }.getOrNull() ?: return null
    return Instant.ofEpochMilli(info.triggerTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

/** Next alarm as state; refreshes on `ACTION_NEXT_ALARM_CLOCK_CHANGED`. */
@Composable
internal fun rememberNextAlarm(): LocalDateTime? {
    val context = LocalContext.current
    var alarm by remember { mutableStateOf(readNextAlarm(context)) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                alarm = readNextAlarm(ctx)
            }
        }
        runCatching {
            context.registerReceiver(
                receiver,
                IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
                Context.RECEIVER_EXPORTED,
            )
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return alarm
}

/** "6:30 AM" for an alarm today, "Mon 6:30 AM" for a later day. */
internal fun LocalDateTime.alarmText(now: LocalDateTime): String =
    if (toLocalDate() == now.toLocalDate()) clockAmPmText() else "${weekdayText()} ${clockAmPmText()}"
