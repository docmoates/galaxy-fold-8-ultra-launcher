package com.foldos.launcher.search

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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.exp

/** Picks of one result key: the most recent pick timestamps, newest last. */
@Serializable
data class SearchUsageEntry(val key: String, val picks: List<Long>)

/** Process-wide DataStore for search usage; one instance per file name, as DataStore requires. */
private val Context.foldOsSearchUsageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SearchUsageStore.DATA_STORE_NAME,
)

/**
 * Usage ranking and recent queries behind universal search (PLAN.md 4.1 "Usage-ranked results").
 * Every pick of a result is counted per [SearchResult.key] and weighted by recency: a pick made
 * now counts 1, one made [HALF_LIFE_MILLIS] ago counts 0.5. Recent queries feed the empty-query
 * suggestions. "Clear search history" in Settings > Search calls [clear].
 */
class SearchUsageStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsSearchUsageDataStore
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val usageSerializer = ListSerializer(SearchUsageEntry.serializer())
    private val queriesSerializer = ListSerializer(String.serializer())

    /** Recency-weighted pick score per result key; absent = 0. */
    val scores: Flow<Map<String, Double>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> scoresOf(decodeUsage(prefs[USAGE_KEY]), System.currentTimeMillis()) }

    /** The last [MAX_QUERIES] distinct queries, most recent first. */
    val recentQueries: Flow<List<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decodeQueries(prefs[QUERIES_KEY]) }

    /** Counts one pick of [key] now. */
    suspend fun recordPick(key: String, nowMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            val entries = decodeUsage(prefs[USAGE_KEY]).associateBy { it.key }.toMutableMap()
            val old = entries[key]?.picks.orEmpty()
            entries[key] = SearchUsageEntry(key, (old + nowMillis).takeLast(MAX_PICKS_PER_KEY))
            // Forget keys nobody has picked in a long time so the file stays small.
            val cutoff = nowMillis - HALF_LIFE_MILLIS * 8
            val kept = entries.values.filter { entry -> entry.picks.any { it >= cutoff } }
            prefs[USAGE_KEY] = json.encodeToString(usageSerializer, kept)
        }
    }

    /** Remembers [query] as the most recent search (blank queries are ignored). */
    suspend fun recordQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            val current = decodeQueries(prefs[QUERIES_KEY])
            val next = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_QUERIES)
            prefs[QUERIES_KEY] = json.encodeToString(queriesSerializer, next)
        }
    }

    /** Forgets every pick and every recent query. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(USAGE_KEY)
            prefs.remove(QUERIES_KEY)
        }
    }

    private fun decodeUsage(raw: String?): List<SearchUsageEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(usageSerializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeQueries(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(queriesSerializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val DATA_STORE_NAME: String = "foldos_search_usage"
        private val USAGE_KEY: Preferences.Key<String> = stringPreferencesKey("usage")
        private val QUERIES_KEY: Preferences.Key<String> = stringPreferencesKey("queries")
        private const val MAX_PICKS_PER_KEY = 24
        private const val MAX_QUERIES = 12

        /** A pick loses half its weight every week. */
        const val HALF_LIFE_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

        /** Sum over picks of 0.5^(age / half-life). */
        fun scoresOf(entries: List<SearchUsageEntry>, nowMillis: Long): Map<String, Double> {
            if (entries.isEmpty()) return emptyMap()
            val ln2 = 0.6931471805599453
            val result = HashMap<String, Double>(entries.size)
            for (entry in entries) {
                var total = 0.0
                for (pick in entry.picks) {
                    val age = (nowMillis - pick).coerceAtLeast(0L).toDouble()
                    total += exp(-ln2 * age / HALF_LIFE_MILLIS)
                }
                if (total > 0.0) result[entry.key] = total
            }
            return result
        }
    }
}
