package com.foldos.launcher.builtins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pomodoro-style 25:00 countdown (mock-up `wgRender` kind `timer`): start / pause / reset, and
 * at four rows duration chips (5, 15, 25 min, +1 min). Transient state lives in a remembered
 * [FocusTimerController]; the end time and paused remainder persist through [FocusTimerStore],
 * so the countdown survives leaving the home screen and process death.
 */
@Composable
fun FocusTimerWidget(spanX: Int, spanY: Int, modifier: Modifier, store: FocusTimerStore) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val iw = maxWidth.value
        val ih = maxHeight.value
        val snapshot by store.snapshot.collectAsStateWithLifecycle(initialValue = FocusTimerSnapshot())
        val scope = rememberCoroutineScope()
        val controller = remember(store) { FocusTimerController(store, scope) }
        controller.snapshot = snapshot

        // Tick 4x a second while running so the seconds stay smooth; stop when paused.
        LaunchedEffect(snapshot.running, snapshot.endTimeMillis) {
            controller.now = System.currentTimeMillis()
            while (snapshot.running) {
                delay(250L)
                controller.now = System.currentTimeMillis()
                if (controller.remainingMillis <= 0L) {
                    controller.complete()
                    break
                }
            }
        }

        val remaining = controller.remainingMillis
        val timeText = formatCountdown(remaining)
        val fraction = 1f - (remaining.toFloat() / FocusTimerStore.SESSION_MILLIS).coerceIn(0f, 1f)
        val round = snapshot.roundsDone % FocusTimerStore.ROUNDS_PER_SET + 1
        val roundText = "Pomodoro · round $round of ${FocusTimerStore.ROUNDS_PER_SET}"

        if (one) {
            val fs = minDp(ih * 0.72f, 44f)
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BigNumber(timeText, dpToSp(fs))
                if (spanX >= 2) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        WidgetDimText(if (spanX >= 4) roundText else "Round $round of ${FocusTimerStore.ROUNDS_PER_SET}", size = 12.sp)
                        WidgetBar(fraction)
                    }
                }
                if (spanX >= 4) {
                    ResetButton(onClick = controller::reset, size = 28.dp)
                }
                StartPauseButton(snapshot.running, onClick = controller::toggle, size = 34.dp)
            }
        } else {
            val ch = ih - WidgetHeaderHeight.value
            val fs = minDp(ch * 0.36f, iw * 0.36f)
            Column(Modifier.fillMaxSize()) {
                WidgetHeader(Icons.Outlined.Timer, "Focus")
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BigNumber(timeText, dpToSp(fs), textAlign = TextAlign.Center)
                    WidgetBar(fraction)
                    WidgetRow(start = roundText, end = if (snapshot.running) "Running" else "Paused")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (spanX < 3) 10.dp else 14.dp),
                    ) {
                        ResetButton(onClick = controller::reset, size = if (spanX < 3) 24.dp else 28.dp)
                        StartPauseButton(snapshot.running, onClick = controller::toggle, size = if (spanX < 3) 36.dp else 44.dp)
                    }
                    if (spanY >= 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DurationChip("5 min") { controller.setMinutes(5) }
                            DurationChip("15 min") { controller.setMinutes(15) }
                            DurationChip("25 min") { controller.setMinutes(25) }
                            DurationChip("+1 min") { controller.addMinutes(1) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ViewModel-free holder for the timer's transient state. [now] is bumped by the widget's ticker;
 * everything durable is written straight to the [FocusTimerStore].
 */
internal class FocusTimerController(private val store: FocusTimerStore, private val scope: CoroutineScope) {
    var snapshot: FocusTimerSnapshot = FocusTimerSnapshot()
    var now: Long by mutableLongStateOf(System.currentTimeMillis())

    val remainingMillis: Long get() = snapshot.remainingAt(now)

    fun toggle() {
        val current = snapshot
        val remaining = remainingMillis
        scope.launch {
            if (current.running) store.pause(remaining)
            else store.start(if (remaining <= 0L) FocusTimerStore.SESSION_MILLIS else remaining)
        }
    }

    fun reset() {
        scope.launch { store.reset() }
    }

    fun complete() {
        scope.launch { store.complete() }
    }

    fun setMinutes(minutes: Int) {
        val millis = minutes * 60_000L
        val running = snapshot.running
        scope.launch { if (running) store.start(millis) else store.pause(millis) }
    }

    fun addMinutes(minutes: Int) {
        val millis = remainingMillis + minutes * 60_000L
        val running = snapshot.running
        scope.launch { if (running) store.start(millis) else store.pause(millis) }
    }
}

/** "24:59"; hours roll into the minutes column ("90:00") since sessions stay short. */
internal fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis + 999L) / 1000L
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun StartPauseButton(running: Boolean, onClick: () -> Unit, size: Dp) {
    val tokens = LocalFoldOsTokens.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(tokens.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (running) "Pause" else "Start",
            tint = tokens.onAccent,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit, size: Dp) {
    Icon(
        Icons.Outlined.Replay,
        contentDescription = "Reset",
        tint = LocalContentColor.current.copy(alpha = 0.8f),
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
    )
}

/** Mock-up `.chipz span`: small pill chip. */
@Composable
private fun DurationChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 11.5.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(LocalContentColor.current.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
