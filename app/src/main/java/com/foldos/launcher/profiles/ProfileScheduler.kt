package com.foldos.launcher.profiles

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.foldos.launcher.core.model.Profile
import java.util.Calendar

/**
 * Arms one `AlarmManager` alarm for the next start or end boundary of any profile with a time
 * rule. The alarm fires [ProfileAlarmReceiver], which applies the matching profile and calls
 * [schedule] again. Exact alarms (`setExactAndAllowWhileIdle`) are used when the user has allowed
 * them (Settings > Apps > Alarms & reminders; `SCHEDULE_EXACT_ALARM`), otherwise an inexact
 * `setAndAllowWhileIdle` alarm that may run a few minutes late.
 *
 * The companion holds the pure time math (`isInWindow`, `nextBoundary`, `matching`) so it can be
 * reasoned about without a device: minutes are minutes after local midnight, days are
 * `java.util.Calendar.SUNDAY..SATURDAY`, an empty day set means every day, and a window whose end
 * is before its start runs overnight (22:00-06:00 belongs to the day it starts on).
 */
class ProfileScheduler(context: Context) {

    private val appContext: Context = context.applicationContext
    private val alarmManager: AlarmManager? = appContext.getSystemService(AlarmManager::class.java)

    /**
     * Arms the alarm for the next boundary after [now] and returns its time, or cancels any pending
     * alarm and returns null when no profile has a time rule.
     */
    fun schedule(profiles: List<Profile>, now: Long = System.currentTimeMillis()): Long? {
        val manager = alarmManager ?: return null
        val intent = pendingIntent()
        val next = nextBoundary(profiles, now)
        if (next == null) {
            runCatching { manager.cancel(intent) }
            return null
        }
        // A short cushion so the evaluation at fire time lands inside the new minute.
        val triggerAt = next + FIRE_CUSHION_MS
        try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked between the check and the call; fall back to inexact.
            Log.w(TAG, "Exact alarm refused, scheduling inexact", e)
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent) }
        } catch (e: Exception) {
            Log.w(TAG, "Scheduling the profile alarm failed", e)
        }
        return next
    }

    /** Cancels the pending boundary alarm, if any. */
    fun cancel() {
        val manager = alarmManager ?: return
        runCatching { manager.cancel(pendingIntent()) }
    }

    /** True when the system lets this app use exact alarms. */
    fun canScheduleExact(): Boolean = try {
        alarmManager?.canScheduleExactAlarms() ?: false
    } catch (e: Exception) {
        false
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(appContext, ProfileAlarmReceiver::class.java).setAction(ProfileAlarmReceiver.ACTION_BOUNDARY)
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "ProfileScheduler"
        private const val REQUEST_CODE = 0x50F1
        private const val FIRE_CUSHION_MS = 1_500L

        /** How far ahead [nextBoundary] looks; every weekly rule has a boundary within 8 days. */
        private const val LOOKAHEAD_DAYS = 8

        /**
         * The profile the rules select at [now]: the first `onDnd` profile while [dndOn], else the
         * first scheduled profile whose window contains [now], else null.
         */
        fun matching(profiles: List<Profile>, now: Long, dndOn: Boolean): Profile? {
            if (dndOn) profiles.firstOrNull { it.onDnd }?.let { return it }
            return profiles.firstOrNull { it.scheduled && isInWindow(it, now) }
        }

        /** True when [now] falls inside the profile's time window on an allowed day. */
        fun isInWindow(profile: Profile, now: Long): Boolean {
            val start = profile.startMinute ?: return false
            val end = profile.endMinute ?: return false
            if (start == end) return false
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            return if (start < end) {
                minute >= start && minute < end && allowsDay(profile, day)
            } else {
                (minute >= start && allowsDay(profile, day)) ||
                    (minute < end && allowsDay(profile, previousDay(day)))
            }
        }

        /** The earliest start or end of any time window strictly after [now], or null without rules. */
        fun nextBoundary(profiles: List<Profile>, now: Long): Long? {
            var best: Long? = null
            val cursor = Calendar.getInstance()
            for (profile in profiles) {
                val start = profile.startMinute ?: continue
                val end = profile.endMinute ?: continue
                if (start == end) continue
                for (offset in 0..LOOKAHEAD_DAYS) {
                    cursor.timeInMillis = now
                    cursor.add(Calendar.DAY_OF_YEAR, offset)
                    val day = cursor.get(Calendar.DAY_OF_WEEK)
                    if (allowsDay(profile, day)) {
                        val at = atMinute(cursor, start)
                        if (at > now && (best == null || at < best)) best = at
                    }
                    // An overnight window's end on this day belongs to the start on the day before.
                    val endDay = if (start < end) day else previousDay(day)
                    if (allowsDay(profile, endDay)) {
                        val at = atMinute(cursor, end)
                        if (at > now && (best == null || at < best)) best = at
                    }
                }
            }
            return best
        }

        fun allowsDay(profile: Profile, calendarDay: Int): Boolean =
            profile.days.isEmpty() || calendarDay in profile.days

        fun previousDay(calendarDay: Int): Int =
            if (calendarDay == Calendar.SUNDAY) Calendar.SATURDAY else calendarDay - 1

        private fun atMinute(day: Calendar, minute: Int): Long {
            val at = day.clone() as Calendar
            at.set(Calendar.HOUR_OF_DAY, minute / 60)
            at.set(Calendar.MINUTE, minute % 60)
            at.set(Calendar.SECOND, 0)
            at.set(Calendar.MILLISECOND, 0)
            return at.timeInMillis
        }
    }
}
