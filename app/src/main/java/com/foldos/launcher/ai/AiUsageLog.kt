package com.foldos.launcher.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One line of the AI usage log (Settings › AI › AI usage log): what ran, on which data, with
 * which engine. [time] is epoch millis.
 */
@Serializable
data class AiUsageEntry(
    val time: Long,
    /** "Applied setting: inner screen -> dashboard", "Answered: what's next?" ... */
    val action: String,
    /** The data the action read: "calendar, tasks", "settings", "nothing". */
    val data: String,
    /** Engine name: "Rules", "Template", "Gemini Nano". */
    val engine: String,
)

private val Context.foldOsAiLogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AiUsageLog.DATA_STORE_NAME,
)

/**
 * Privacy log of every assistant action, newest first, capped at [MAX_ENTRIES], stored as one JSON
 * array in the Preferences DataStore `foldos_ai_log` (the "AI usage log" idea borrowed from
 * Nothing Launcher, PLAN.md 4.1).
 */
class AiUsageLog(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsAiLogDataStore
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(AiUsageEntry.serializer())

    /** Entries newest first. */
    val entries: Flow<List<AiUsageEntry>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decode(prefs[ENTRIES_KEY]).sortedByDescending { it.time } }

    suspend fun record(action: String, data: String, engine: String) {
        val entry = AiUsageEntry(System.currentTimeMillis(), action.take(160), data.take(160), engine)
        dataStore.edit { prefs ->
            val current = decode(prefs[ENTRIES_KEY])
            val next = (listOf(entry) + current).take(MAX_ENTRIES)
            prefs[ENTRIES_KEY] = json.encodeToString(serializer, next)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(ENTRIES_KEY) }
    }

    private fun decode(raw: String?): List<AiUsageEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val DATA_STORE_NAME: String = "foldos_ai_log"
        const val MAX_ENTRIES: Int = 200
        private val ENTRIES_KEY: Preferences.Key<String> = stringPreferencesKey("entries")
    }
}
