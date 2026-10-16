package com.foldos.launcher.icons

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One per-app icon override: [drawableName] from [packageName] (null package = the selected pack). */
@Serializable
data class IconOverride(
    val packageName: String? = null,
    val drawableName: String? = null,
)

@Serializable
private data class IconOverrides(
    val entries: Map<String, IconOverride> = emptyMap(),
)

/**
 * Process-wide Preferences DataStore for per-app icon overrides, separate from the settings store
 * (`foldos_settings`). Declared at file level so there is exactly one instance for the file name.
 */
private val Context.foldOsIconsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = IconOverrideStore.DATA_STORE_NAME,
)

/** Per-app icon overrides keyed by `AppInfo.key`, stored as one JSON map in DataStore `foldos_icons`. */
class IconOverrideStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsIconsDataStore

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The current overrides and every change after. */
    val overrides: Flow<Map<String, IconOverride>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decode(prefs[OVERRIDES_KEY]).entries }
        .distinctUntilChanged()

    suspend fun set(appKey: String, override: IconOverride) {
        dataStore.edit { prefs ->
            val current = decode(prefs[OVERRIDES_KEY])
            prefs[OVERRIDES_KEY] = encode(current.copy(entries = current.entries + (appKey to override)))
        }
    }

    suspend fun clear(appKey: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[OVERRIDES_KEY])
            if (!current.entries.containsKey(appKey)) return@edit
            prefs[OVERRIDES_KEY] = encode(current.copy(entries = current.entries - appKey))
        }
    }

    private fun encode(value: IconOverrides): String =
        json.encodeToString(IconOverrides.serializer(), value)

    private fun decode(raw: String?): IconOverrides {
        if (raw.isNullOrBlank()) return IconOverrides()
        return try {
            json.decodeFromString(IconOverrides.serializer(), raw)
        } catch (e: Exception) {
            IconOverrides()
        }
    }

    companion object {
        const val DATA_STORE_NAME: String = "foldos_icons"
        private val OVERRIDES_KEY: Preferences.Key<String> = stringPreferencesKey("overrides")
    }
}
