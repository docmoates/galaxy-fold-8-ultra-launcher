package com.foldos.launcher.core.repo

import android.app.PendingIntent
import kotlinx.coroutines.flow.Flow

/**
 * One active notification, as seen by the launcher's `NotificationListenerService`.
 *
 * @property key `StatusBarNotification.key`, used to dismiss or open it.
 * @property contentIntent the notification's tap action; null when it has none.
 */
data class NotificationSummary(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
)

/**
 * Notification badges and previews (Settings > Notifications). Implemented by the Badges agent on
 * top of `builtins.FoldOsNotificationListener`; `data.EmptyBadgeRepository` is the placeholder.
 * Both flows emit nothing but empty values until the user grants notification access.
 */
interface BadgeRepository {
    /** Package name -> number of unread notifications; packages without notifications are absent. */
    val counts: Flow<Map<String, Int>>

    /** Active notifications of one package, newest first (the long-press preview). */
    fun notificationsFor(packageName: String): Flow<List<NotificationSummary>>
}
