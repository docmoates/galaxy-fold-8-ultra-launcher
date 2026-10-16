package com.foldos.launcher.builtins

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kind of device shown in the Devices widget; picks the glyph and the display name. */
enum class DeviceKind(val label: String, val icon: ImageVector) {
    PHONE("Fold", Icons.Outlined.Smartphone),
    WATCH("Watch", Icons.Outlined.Watch),
    BUDS("Buds", Icons.Outlined.Headphones),
    LAPTOP("Book", Icons.Outlined.Laptop),
    KEYBOARD("Keys", Icons.Outlined.Keyboard),
}

/** One row of the Devices widget. [percent] is null when the level is unknown. */
data class DeviceBattery(
    val kind: DeviceKind,
    val name: String,
    val percent: Int?,
    val charging: Boolean = false,
) {
    val fraction: Float? get() = percent?.let { it / 100f }
    val percentText: String get() = percent?.let { "$it%" } ?: "–"
}

/** The BLUETOOTH_CONNECT permission string the Devices widget requests for bonded devices. */
internal const val BLUETOOTH_PERMISSION: String = Manifest.permission.BLUETOOTH_CONNECT

/** Reads the phone's own level from the sticky `ACTION_BATTERY_CHANGED` broadcast. */
internal fun readPhoneBattery(context: Context, sticky: Intent? = null): DeviceBattery {
    val intent = sticky ?: runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_EXPORTED)
    }.getOrNull()
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return DeviceBattery(DeviceKind.PHONE, DeviceKind.PHONE.label, percent, charging)
}

/** Phone battery as state; updates on every `ACTION_BATTERY_CHANGED`. */
@Composable
internal fun rememberPhoneBattery(): DeviceBattery {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(readPhoneBattery(context)) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                battery = readPhoneBattery(ctx, intent)
            }
        }
        runCatching {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_EXPORTED)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return battery
}

/**
 * Bonded Bluetooth wearables (watches, buds/headphones, laptops, keyboards) with their battery
 * level where the stack exposes one. Battery comes from the hidden
 * `BluetoothDevice.getBatteryLevel()` via reflection, wrapped in `runCatching`; -1 / failure means
 * unknown. Empty when BLUETOOTH_CONNECT is not granted or Bluetooth is off.
 */
internal suspend fun loadBluetoothDevices(context: Context): List<DeviceBattery> = withContext(Dispatchers.IO) {
    if (!context.isPermissionGranted(BLUETOOTH_PERMISSION)) return@withContext emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return@withContext emptyList()
    val bonded: Set<BluetoothDevice> = runCatching { adapter.bondedDevices }.getOrNull() ?: return@withContext emptyList()
    bonded.mapNotNull { device ->
        val kind = runCatching { device.kind() }.getOrNull() ?: return@mapNotNull null
        val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: kind.label
        DeviceBattery(kind, shortDeviceName(name, kind), device.batteryLevelOrNull())
    }.sortedBy { it.kind.ordinal }
}

/** Phone first, then bonded wearables; the Bluetooth part reloads on resume. */
@Composable
internal fun rememberDeviceBatteries(bluetoothGranted: Boolean): State<List<DeviceBattery>> {
    val context = LocalContext.current
    val phone = rememberPhoneBattery()
    val resumeTick = rememberResumeTick()
    return produceState(initialValue = listOf(phone), phone, bluetoothGranted, resumeTick) {
        val others = if (bluetoothGranted) loadBluetoothDevices(context) else emptyList()
        value = listOf(phone) + others
    }
}

private fun BluetoothDevice.kind(): DeviceKind? {
    val btClass: BluetoothClass = bluetoothClass ?: return null
    return when (btClass.deviceClass) {
        BluetoothClass.Device.WEARABLE_WRIST_WATCH -> DeviceKind.WATCH
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
        -> DeviceKind.BUDS
        BluetoothClass.Device.COMPUTER_LAPTOP -> DeviceKind.LAPTOP
        BluetoothClass.Device.PERIPHERAL_KEYBOARD,
        BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING,
        -> DeviceKind.KEYBOARD
        else -> when (btClass.majorDeviceClass) {
            BluetoothClass.Device.Major.WEARABLE -> DeviceKind.WATCH
            BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceKind.BUDS
            BluetoothClass.Device.Major.COMPUTER -> DeviceKind.LAPTOP
            else -> null
        }
    }
}

/** Hidden API `int getBatteryLevel()`; returns null when unknown (-1) or inaccessible. */
private fun BluetoothDevice.batteryLevelOrNull(): Int? = runCatching {
    val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
    (method.invoke(this) as? Int)?.takeIf { it in 0..100 }
}.getOrNull()

/** "Galaxy Watch7 (ABCD)" -> "Watch7"; falls back to the kind's label when the name is unwieldy. */
private fun shortDeviceName(name: String, kind: DeviceKind): String {
    val cleaned = name.replace(Regex("\\s*\\([^)]*\\)"), "").removePrefix("Galaxy ").trim()
    return if (cleaned.isEmpty() || cleaned.length > 12) kind.label else cleaned
}
