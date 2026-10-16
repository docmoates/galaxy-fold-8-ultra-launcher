package com.foldos.launcher.profiles

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

/**
 * Do Not Disturb rule: `ACTION_INTERRUPTION_FILTER_CHANGED` is only delivered to context-registered
 * receivers, so this one is registered at runtime through [register] (from
 * [ProfileAutoSwitchEffect], or from `Application.onCreate` for the life of the process) and
 * re-evaluates the profile rules whenever the filter changes.
 */
class ProfileDndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) return
        val repo = FoldOsProfileRepository.from(context) ?: return
        val result = goAsync()
        repo.appScope.launch {
            try {
                repo.applyAutomaticSwitch()
            } catch (e: Exception) {
                Log.w(TAG, "Do Not Disturb evaluation failed", e)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ProfileDndReceiver"

        /**
         * Registers a receiver on [context] (use the application context when it should outlive an
         * activity). The action is a protected system broadcast, so the exported flag only matters
         * for delivery, never for who can send it. Returns null when registration fails.
         */
        fun register(context: Context): ProfileDndReceiver? {
            val receiver = ProfileDndReceiver()
            return try {
                context.registerReceiver(
                    receiver,
                    IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
                    Context.RECEIVER_EXPORTED,
                )
                receiver
            } catch (e: Exception) {
                Log.w(TAG, "Could not register for interruption filter changes", e)
                null
            }
        }

        fun unregister(context: Context, receiver: ProfileDndReceiver?) {
            if (receiver == null) return
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

/**
 * Hooks automatic profile switching onto the launcher UI: every time the lifecycle resumes the
 * schedule and Do Not Disturb rules are re-evaluated (the DND state is polled, since the launcher
 * may have missed the broadcast while stopped) and the boundary alarm is re-armed; while composed,
 * a [ProfileDndReceiver] listens for filter changes. No-op until `AppGraphImpl.profiles` is a
 * [FoldOsProfileRepository]. Compose it once, in `MainActivity` / `LauncherRoot`.
 */
@Composable
fun ProfileAutoSwitchEffect() {
    val context = LocalContext.current
    val repo = remember(context) { FoldOsProfileRepository.from(context) } ?: return
    LifecycleResumeEffect(repo) {
        repo.appScope.launch {
            try {
                repo.applyAutomaticSwitch()
                repo.rescheduleAlarms()
            } catch (e: Exception) {
                Log.w("ProfileAutoSwitch", "Profile evaluation on resume failed", e)
            }
        }
        onPauseOrDispose { }
    }
    DisposableEffect(context) {
        val receiver = ProfileDndReceiver.register(context)
        onDispose { ProfileDndReceiver.unregister(context, receiver) }
    }
}
