package com.foldos.launcher.builtins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.min

/**
 * Battery of the phone and bonded wearables (mock-up `wgRender` kind `battery`): the phone level
 * comes from the sticky `ACTION_BATTERY_CHANGED` broadcast, watch / buds rows appear only when
 * bonded Bluetooth devices exist (level via the hidden `getBatteryLevel`, unknown shows "–").
 * Single row: one ring (1 wide) or bars; taller: a grid of rings sized to fit.
 */
@Composable
fun DevicesBatteryWidget(spanX: Int, spanY: Int, modifier: Modifier) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val iw = maxWidth.value
        val ih = maxHeight.value
        val bluetoothGranted = rememberPermissionGranted(BLUETOOTH_PERMISSION)
        val requester = LocalPermissionRequester.current
        val devices by rememberDeviceBatteries(bluetoothGranted)

        if (one) {
            val d = (ih - 4f).coerceAtLeast(20f)
            if (spanX == 1) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DeviceRing(devices.first(), d.dp, iconSize = 20.dp)
                }
            } else {
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    devices.take(if (spanX >= 4) 2 else 1).forEach { device ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(device.kind.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                WidgetDimText(device.name, size = 13.sp, modifier = Modifier.weight(1f))
                                WidgetTitleText(device.percentText, size = 13.sp)
                            }
                            WidgetBar(device.fraction ?: 0f)
                            WidgetDimText(device.statusText(), size = 11.5.sp)
                        }
                    }
                    if (spanX == 2) {
                        val second = devices.getOrNull(1)
                        if (second != null) {
                            DeviceRing(second, d.dp, iconSize = 18.dp)
                        } else if (!bluetoothGranted) {
                            WidgetActionButton("Allow", onClick = { requester(BLUETOOTH_PERMISSION) }, compact = true)
                        }
                    } else if (spanX >= 4 && devices.size < 2 && !bluetoothGranted) {
                        WidgetActionButton("Allow Bluetooth", onClick = { requester(BLUETOOTH_PERMISSION) }, compact = true)
                    }
                }
            }
        } else {
            val ch = ih - WidgetHeaderHeight.value
            val showButton = !bluetoothGranted
            val gridHeight = if (showButton) ch - 40f else ch
            val (n, r) = gridFit(iw, gridHeight, if (spanX >= 4) 66f else 60f, min(4, spanX * 2))
            val k = min(devices.size, n * r)
            val ringD = minDp(60f, floor(gridHeight / r) - 22f, floor(iw / n) - 8f).coerceAtLeast(22f)
            Column(Modifier.fillMaxSize()) {
                WidgetHeader(Icons.Outlined.BatteryFull, "Devices")
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    devices.take(k).chunked(n).forEach { rowDevices ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowDevices.forEach { device ->
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    DeviceRing(device, ringD.dp, iconSize = if (ringD >= 44f) 22.dp else 14.dp)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        WidgetDimText(device.name, size = 12.sp)
                                        WidgetTitleText(device.percentText, size = 12.sp)
                                    }
                                }
                            }
                            // keep the last row's tiles the same width as the full rows
                            repeat(n - rowDevices.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (showButton) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        WidgetActionButton(
                            "Allow Bluetooth for Watch & Buds",
                            onClick = { requester(BLUETOOTH_PERMISSION) },
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

/** A device's ring with its glyph in the middle (mock-up `.ring`). */
@Composable
private fun DeviceRing(device: DeviceBattery, size: Dp, iconSize: Dp) {
    val stroke = if (size >= 40.dp) 5.dp else 3.5.dp
    ProgressRing(fraction = device.fraction, size = size, stroke = stroke) {
        Icon(device.kind.icon, contentDescription = device.name, modifier = Modifier.size(iconSize))
    }
}

private fun DeviceBattery.statusText(): String = when {
    charging -> "Charging"
    percent == null -> "Level unknown"
    kind == DeviceKind.PHONE -> if (percent > 20) "On battery" else "Charge soon"
    else -> "Connected"
}
