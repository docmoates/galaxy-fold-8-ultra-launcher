package com.foldos.launcher.builtins

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.foldos.launcher.notifications.ActiveNotifications

/**
 * Notification listener the launcher registers so `MediaSessionManager.getActiveSessions` (the
 * Now Playing widget) and notification badges can work. Granting it in Settings > Notifications >
 * Device & app notifications is what unlocks the APIs.
 *
 * Every posted / removed notification is forwarded to `notifications.ActiveNotifications`, the
 * process-wide state the badge repository and the quiet drawer read; the service itself keeps no
 * state. Filtering (group summaries, ongoing, media) happens there.
 *
 * Manifest entry (Integrate agent):
 * ```
 * <service android:name=".builtins.FoldOsNotificationListener"
 *     android:exported="true"
 *     android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
 *     <intent-filter>
 *         <action android:name="android.service.notification.NotificationListenerService" />
 *     </intent-filter>
 * </service>
 * ```
 */
class FoldOsNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        val active: List<StatusBarNotification> = runCatching { activeNotifications?.toList() }
            .onFailure { Log.w(TAG, "activeNotifications unavailable on connect", it) }
            .getOrNull() ?: emptyList()
        ActiveNotifications.onListenerConnected(this, active)
    }

    override fun onListenerDisconnected() {
        ActiveNotifications.onListenerDisconnected(this)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        ActiveNotifications.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        ActiveNotifications.onRemoved(sbn.key)
    }

    override fun onDestroy() {
        ActiveNotifications.onListenerDisconnected(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FoldOsNotifListener"

        /** The component the media widget passes to `MediaSessionManager`. */
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, FoldOsNotificationListener::class.java)

        /** True once the user has granted notification access to the launcher. */
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return runCatching { manager.isNotificationListenerAccessGranted(componentName(context)) }.getOrDefault(false)
        }
    }
}
