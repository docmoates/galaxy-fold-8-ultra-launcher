package com.foldos.launcher.edit

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Edit mode > Wallpaper. A stub until per-screen / per-mode wallpapers arrive: explains that the
 * themed wallpaper comes from Settings > Look, and hands off to the system wallpaper picker
 * ([Intent.ACTION_SET_WALLPAPER]) or the live wallpaper chooser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Wallpaper",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "FoldOS paints the theme wallpaper itself; recolor it under Themes. " +
                    "To use a photo or a live wallpaper behind the launcher, pick it with the system picker. " +
                    "Separate wallpapers per screen and per dark/light mode arrive in a later phase.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        openSystemWallpaperPicker(context)
                        onDismiss()
                    },
                leadingContent = { Icon(imageVector = Icons.Rounded.Wallpaper, contentDescription = null) },
                headlineContent = { Text(text = "System wallpaper picker") },
                supportingContent = { Text(text = "Photos, Gallery, Samsung Themes and other wallpaper apps") },
                trailingContent = { Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null) },
            )
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        openLiveWallpaperChooser(context)
                        onDismiss()
                    },
                leadingContent = { Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null) },
                headlineContent = { Text(text = "Live wallpapers") },
                supportingContent = { Text(text = "Animated wallpapers installed on this device") },
                trailingContent = { Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null) },
            )
        }
    }
}

/**
 * Starts the system "Set wallpaper" chooser. Returns false when no activity could handle it (the
 * chooser itself always resolves, so this only fails on locked-down devices).
 */
fun openSystemWallpaperPicker(context: Context): Boolean {
    val pick = Intent(Intent.ACTION_SET_WALLPAPER)
    val chooser = Intent.createChooser(pick, "Choose wallpaper").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(chooser) }.isSuccess
}

/** Starts the live wallpaper chooser; falls back to [openSystemWallpaperPicker] when it is missing. */
fun openLiveWallpaperChooser(context: Context): Boolean {
    val live = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val started = runCatching { context.startActivity(live) }.isSuccess
    return started || openSystemWallpaperPicker(context)
}
