package com.foldos.launcher.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.repo.NotificationSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Shared notification views: one notification row (mock-up `.npv`: 14dp radius, drawer background,
 * 13sp "<b>Sender</b> · text"), the per-app preview card shown on long-press, and small helpers for
 * app icons / labels by package name. Everything uses MaterialTheme only, so it renders both under
 * FoldOsTheme (home) and the Settings activity's plain Material theme.
 */

/** The app graph from the Application, so these views work from both activities without a composition local. */
@Composable
internal fun rememberNotificationGraph(): AppGraph {
    val context = LocalContext.current
    return remember(context) { FoldOsApplication.from(context).graph }
}

/** The application's label for [packageName], or the last package segment when it cannot be resolved. */
fun appLabelOf(context: Context, packageName: String): String {
    val pm: PackageManager = context.packageManager
    return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
        .getOrDefault(packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() })
}

/** The application icon of [packageName] rasterised off the main thread; null while loading or on failure. */
@Composable
fun rememberAppIconBitmap(packageName: String, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName, key2 = sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val px = sizePx.coerceAtLeast(1)
                context.packageManager.getApplicationIcon(packageName).toBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

/** "now", "5 min. ago", "Yesterday"... for a notification's post time. */
fun relativePostTime(postTime: Long, now: Long = System.currentTimeMillis()): String {
    if (now - postTime < DateUtils.MINUTE_IN_MILLIS) return "now"
    return DateUtils.getRelativeTimeSpanString(postTime, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString()
}

/** Rounded app icon tile with a surface placeholder while the bitmap loads. */
@Composable
fun NotificationAppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = rememberAppIconBitmap(packageName, sizePx)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 24))
            .background(if (bitmap == null) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
        }
    }
}

/**
 * One notification: optional app icon, bold title, dimmed text, relative time and a dismiss button.
 * Tapping the row calls [onOpen] (normally `ActiveNotifications.open`), the X calls [onDismiss]
 * (normally `ActiveNotifications.dismiss(summary.key)`).
 */
@Composable
fun NotificationRow(
    summary: NotificationSummary,
    onOpen: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showAppIcon: Boolean = true,
) {
    val context = LocalContext.current
    val title = summary.title.ifBlank { appLabelOf(context, summary.packageName) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showAppIcon) {
            NotificationAppIcon(packageName = summary.packageName, size = 36.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = relativePostTime(summary.postTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (summary.text.isNotBlank()) {
                Text(
                    text = summary.text,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

/**
 * The long-press notification preview for one app (mock-up `.npv`): a floating card listing
 * `graph.badges.notificationsFor(packageName)` newest first, with "Clear all". Tapping a row opens
 * the notification and calls [onDismiss]; the Home agent positions the card (e.g. in a `Popup`
 * anchored above the icon) and only shows it when `settings.notificationPreview` is on.
 */
@Composable
fun NotificationPreviewCard(
    packageName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = 5,
) {
    val context = LocalContext.current
    val graph = rememberNotificationGraph()
    val flow = remember(graph, packageName) { graph.badges.notificationsFor(packageName) }
    val notifications by flow.collectAsState(initial = emptyList())
    val label = remember(packageName) { appLabelOf(context, packageName) }

    Surface(
        modifier = modifier.widthIn(min = 240.dp, max = 360.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (notifications.isEmpty()) label else "$label · ${notifications.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = {
                        ActiveNotifications.dismissAll(packageName)
                        onDismiss()
                    }) { Text(text = "Clear all") }
                }
            }
            if (notifications.isEmpty()) {
                Text(
                    text = "No notifications",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else {
                notifications.take(maxItems).forEach { summary ->
                    NotificationRow(
                        summary = summary,
                        onOpen = {
                            ActiveNotifications.open(context, summary)
                            onDismiss()
                        },
                        onDismiss = { ActiveNotifications.dismiss(summary.key) },
                        showAppIcon = false,
                    )
                }
                if (notifications.size > maxItems) {
                    Text(
                        text = "+${notifications.size - maxItems} more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
