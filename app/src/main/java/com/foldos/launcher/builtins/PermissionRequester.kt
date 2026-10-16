package com.foldos.launcher.builtins

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Asks the hosting activity to request one runtime permission (a `Manifest.permission.*` string).
 *
 * Built-in widgets that need a dangerous permission (calendar, photos, Bluetooth) show an
 * "Allow ..." button and call this with the permission name. The Integrate agent provides it at
 * the activity root, normally backed by
 * `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`:
 *
 * ```
 * val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
 * CompositionLocalProvider(LocalPermissionRequester provides { launcher.launch(it) }) { ... }
 * ```
 *
 * The default only logs, so a widget rendered without a provider (previews, the picker) still
 * draws its permission button and nothing crashes.
 */
val LocalPermissionRequester: ProvidableCompositionLocal<(String) -> Unit> =
    staticCompositionLocalOf {
        { permission: String ->
            Log.w("FoldOsBuiltIns", "No LocalPermissionRequester provided; cannot request $permission")
        }
    }

/**
 * Whether [permission] is granted right now. Re-checked every time the lifecycle resumes, so the
 * widget flips from its "Allow" button to live data as soon as the user returns from the system
 * dialog or from Settings.
 */
@Composable
internal fun rememberPermissionGranted(permission: String): Boolean {
    val context = LocalContext.current
    var granted by remember(permission) { mutableStateOf(context.isPermissionGranted(permission)) }
    LifecycleResumeEffect(permission) {
        granted = context.isPermissionGranted(permission)
        onPauseOrDispose { }
    }
    return granted
}

/**
 * Whether any of [permissions] is granted right now; re-checked on every resume like
 * [rememberPermissionGranted]. Used where the system can grant a narrower alternative (the Android
 * 14 "Select photos" choice grants READ_MEDIA_VISUAL_USER_SELECTED instead of READ_MEDIA_IMAGES).
 */
@Composable
internal fun rememberAnyPermissionGranted(permissions: List<String>): Boolean {
    val context = LocalContext.current
    var granted by remember(permissions) { mutableStateOf(context.isAnyPermissionGranted(permissions)) }
    LifecycleResumeEffect(permissions) {
        granted = context.isAnyPermissionGranted(permissions)
        onPauseOrDispose { }
    }
    return granted
}

/**
 * A counter that increments each time the lifecycle resumes. Use it as a key for data loads that
 * should refresh when the launcher comes back to the foreground.
 */
@Composable
internal fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return tick
}

internal fun Context.isPermissionGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.isAnyPermissionGranted(permissions: List<String>): Boolean =
    permissions.any { isPermissionGranted(it) }
