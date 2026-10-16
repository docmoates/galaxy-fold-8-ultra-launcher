package com.foldos.launcher.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.foldos.launcher.builtins.FoldOsNotificationListener
import com.foldos.launcher.core.repo.NotificationSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide mirror of the notifications the launcher's `NotificationListenerService` currently
 * sees. `builtins.FoldOsNotificationListener` feeds it from `onListenerConnected`,
 * `onNotificationPosted` and `onNotificationRemoved`; [FoldOsBadgeRepository] and the quiet drawer
 * read it.
 *
 * Only badge-worthy notifications are kept (see [isBadgeWorthy]): group summaries, ongoing /
 * foreground-service / non-clearable notifications and media sessions are dropped at publish time.
 */
object ActiveNotifications {

    private const val TAG = "ActiveNotifications"

    private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())

    /** Every active, badge-worthy notification, in no particular order. Empty without access. */
    val notifications: StateFlow<List<StatusBarNotification>> = _notifications.asStateFlow()

    private val _connected = MutableStateFlow(false)

    /** True while the listener service is bound by the system (i.e. access is granted and live). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var listener: NotificationListenerService? = null

    // ---- Called by builtins.FoldOsNotificationListener -------------------------------------

    /** The service connected; [active] is its `activeNotifications` snapshot. */
    internal fun onListenerConnected(service: NotificationListenerService, active: List<StatusBarNotification>) {
        listener = service
        _notifications.value = active.filter { it.isBadgeWorthy() }
        _connected.value = true
    }

    /** The service was unbound (access revoked or process teardown). */
    internal fun onListenerDisconnected(service: NotificationListenerService) {
        if (listener === service) listener = null
        _connected.value = false
        _notifications.value = emptyList()
    }

    /** A notification was posted or updated; replaces any entry with the same key. */
    internal fun onPosted(sbn: StatusBarNotification) {
        val worthy = sbn.isBadgeWorthy()
        _notifications.update { list ->
            val without = list.filterNot { it.key == sbn.key }
            if (worthy) without + sbn else without
        }
    }

    /** A notification was removed (dismissed, cancelled or its app uninstalled). */
    internal fun onRemoved(key: String) {
        _notifications.update { list -> list.filterNot { it.key == key } }
    }

    // ---- Public helpers ----------------------------------------------------------------------

    /**
     * Cancels the notification with [key] through the listener service. Returns false when the
     * listener is not connected or the system refused.
     */
    fun dismiss(key: String): Boolean {
        val service = listener ?: return false
        return runCatching { service.cancelNotification(key) }
            .onFailure { Log.w(TAG, "cancelNotification failed for $key", it) }
            .isSuccess
    }

    /** Cancels every active notification of [packageName]; returns how many were requested. */
    fun dismissAll(packageName: String): Int {
        val keys = notifications.value.filter { it.packageName == packageName }.map { it.key }
        keys.forEach { dismiss(it) }
        return keys.size
    }

    /**
     * Fires [summary]'s `contentIntent` (the notification's tap action) from the launcher and, when
     * the notification is auto-cancel, dismisses it the way the shade would. Returns false when
     * there is no intent or sending failed.
     */
    fun open(context: Context, summary: NotificationSummary): Boolean {
        val intent = summary.contentIntent ?: return false
        val sent = runCatching {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
            intent.send(context, 0, null, null, null, null, options)
        }.onFailure { Log.w(TAG, "contentIntent.send failed for ${summary.key}", it) }.isSuccess
        if (sent) {
            val sbn = notifications.value.firstOrNull { it.key == summary.key }
            if (sbn != null && sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) dismiss(summary.key)
        }
        return sent
    }
}

/** The listener component the launcher registers in the manifest. */
fun notificationListenerComponent(context: Context): ComponentName = FoldOsNotificationListener.componentName(context)

/** True once the user granted the launcher notification access (Settings > Notifications > Device & app notifications). */
fun isNotificationAccessGranted(context: Context): Boolean = FoldOsNotificationListener.isEnabled(context)

/**
 * Opens the system page where the user grants notification access: the launcher's own detail page
 * on Android 11+ (falls back to the full listener list if that intent cannot be resolved).
 */
fun openNotificationAccessSettings(context: Context) {
    val component = notificationListenerComponent(context)
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val started = runCatching { context.startActivity(detail) }.isSuccess
    if (!started) runCatching { context.startActivity(list) }
}

/**
 * Whether this notification should count towards a badge: not a group summary, clearable (so not
 * ongoing, foreground-service or no-clear) and not a media session / transport control.
 */
fun StatusBarNotification.isBadgeWorthy(): Boolean {
    val n: Notification = notification ?: return false
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
    if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
    if (n.flags and Notification.FLAG_NO_CLEAR != 0) return false
    if (!isClearable) return false
    if (n.category == Notification.CATEGORY_TRANSPORT) return false
    val extras = n.extras
    if (extras != null) {
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        if (template != null && template.contains("MediaStyle")) return false
    }
    return true
}

/** Title / text from `EXTRA_TITLE` / `EXTRA_TEXT` (with big-text, inbox-lines and sub-text fallbacks). */
fun StatusBarNotification.toSummary(): NotificationSummary {
    val n: Notification = notification
    val extras = n.extras
    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
        ?: ""
    val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        ?: extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { line -> line.isNotEmpty() } }
            ?.lastOrNull()
        ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        ?: ""
    return NotificationSummary(
        key = key,
        packageName = packageName,
        title = title,
        text = text,
        postTime = postTime,
        contentIntent = n.contentIntent,
    )
}
