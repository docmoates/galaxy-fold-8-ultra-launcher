package com.foldos.launcher.builtins

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Persisted focus-timer state. While running only [endTimeMillis] matters (remaining = end - now,
 * so the countdown survives process death); while paused [remainingMillis] is authoritative.
 */
data class FocusTimerSnapshot(
    val running: Boolean = false,
    val endTimeMillis: Long = 0L,
    val remainingMillis: Long = FocusTimerStore.SESSION_MILLIS,
    /** Completed sessions today, for the "round 2 of 4" caption. */
    val roundsDone: Int = 0,
) {
    fun remainingAt(nowMillis: Long): Long =
        if (running) (endTimeMillis - nowMillis).coerceAtLeast(0L) else remainingMillis.coerceAtLeast(0L)
}

private val Context.foldOsFocusDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FocusTimerStore.DATA_STORE_NAME,
)

/** DataStore (`foldos_focus`) behind the Focus timer widget: end time, paused remainder, rounds. */
class FocusTimerStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsFocusDataStore

    val snapshot: Flow<FocusTimerSnapshot> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            FocusTimerSnapshot(
                running = prefs[RUNNING] ?: false,
                endTimeMillis = prefs[END_TIME] ?: 0L,
                remainingMillis = prefs[REMAINING] ?: SESSION_MILLIS,
                roundsDone = prefs[ROUNDS] ?: 0,
            )
        }

    /** Starts (or resumes) a countdown that ends at now + [remainingMillis]. */
    suspend fun start(remainingMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[RUNNING] = true
            prefs[END_TIME] = nowMillis + remainingMillis.coerceAtLeast(0L)
            prefs[REMAINING] = remainingMillis.coerceAtLeast(0L)
        }
    }

    /** Pauses with [remainingMillis] left. */
    suspend fun pause(remainingMillis: Long) {
        dataStore.edit { prefs ->
            prefs[RUNNING] = false
            prefs[REMAINING] = remainingMillis.coerceAtLeast(0L)
        }
    }

    /** Back to a full, paused 25:00 session. */
    suspend fun reset() {
        dataStore.edit { prefs ->
            prefs[RUNNING] = false
            prefs[END_TIME] = 0L
            prefs[REMAINING] = SESSION_MILLIS
        }
    }

    /** Called when the countdown hits zero: counts the round and arms a fresh paused session. */
    suspend fun complete() {
        dataStore.edit { prefs ->
            prefs[RUNNING] = false
            prefs[END_TIME] = 0L
            prefs[REMAINING] = SESSION_MILLIS
            prefs[ROUNDS] = (prefs[ROUNDS] ?: 0) + 1
        }
    }

    companion object {
        const val DATA_STORE_NAME: String = "foldos_focus"

        /** 25 minutes, the Pomodoro default. */
        const val SESSION_MILLIS: Long = 25L * 60L * 1000L

        /** Rounds per Pomodoro set shown as "round 2 of 4". */
        const val ROUNDS_PER_SET: Int = 4

        private val RUNNING = booleanPreferencesKey("running")
        private val END_TIME = longPreferencesKey("end_time")
        private val REMAINING = longPreferencesKey("remaining")
        private val ROUNDS = intPreferencesKey("rounds")
    }
}
