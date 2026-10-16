package com.foldos.launcher.profiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Fired by [ProfileScheduler] at a profile's start or end boundary: applies the profile the rules
 * select now and arms the alarm for the next boundary. Declared in the manifest with
 * `android:exported="false"` (only our own `PendingIntent` targets it).
 */
class ProfileAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOUNDARY) return
        val repo = FoldOsProfileRepository.from(context) ?: return
        val result = goAsync()
        repo.appScope.launch {
            try {
                repo.applyAutomaticSwitch()
                repo.rescheduleAlarms()
            } catch (e: Exception) {
                Log.w(TAG, "Profile boundary handling failed", e)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ProfileAlarmReceiver"

        /** Intent action of the boundary alarm. */
        const val ACTION_BOUNDARY: String = "com.foldos.launcher.profiles.BOUNDARY"
    }
}
