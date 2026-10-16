package com.foldos.launcher.builtins

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService

/**
 * Notification listener the launcher registers so `MediaSessionManager.getActiveSessions` (the
 * Now Playing widget) and, later, notification badges can work. It holds no state of its own:
 * granting it in Settings > Notifications > Device & app notifications is what unlocks the APIs.
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

    companion object {
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
