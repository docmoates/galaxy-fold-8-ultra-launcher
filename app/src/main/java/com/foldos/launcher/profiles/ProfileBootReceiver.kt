package com.foldos.launcher.profiles

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Re-arms the profile boundary alarm (alarms do not survive a reboot) and re-evaluates the rules
 * after boot, an app update, a clock or time-zone change, and when the user grants exact alarms.
 * Needs `RECEIVE_BOOT_COMPLETED`; declared in the manifest with `android:exported="false"` and an
 * intent filter for the actions in [ACTIONS] (all protected system broadcasts).
 */
class ProfileBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val repo = FoldOsProfileRepository.from(context) ?: return
        val result = goAsync()
        repo.appScope.launch {
            try {
                repo.applyAutomaticSwitch()
                repo.rescheduleAlarms()
            } catch (e: Exception) {
                Log.w(TAG, "Rescheduling profiles after ${intent.action} failed", e)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ProfileBootReceiver"

        /** Every action the manifest intent filter lists. */
        val ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
