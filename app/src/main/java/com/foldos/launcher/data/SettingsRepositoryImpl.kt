package com.foldos.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Process-wide Preferences DataStore for launcher settings. Declared at file level so there is
 * exactly one instance per file name, as DataStore requires.
 */
private val Context.foldOsSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SettingsRepositoryImpl.DATA_STORE_NAME,
)

/**
 * [SettingsRepository] backed by a Preferences DataStore. The whole [LauncherSettings] object is
 * stored as one JSON string, so adding fields to the model never needs a migration: unknown keys
 * are ignored on read and defaults fill in anything missing.
 */
class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsSettingsDataStore

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val settings: Flow<LauncherSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decode(prefs[SETTINGS_KEY]) }

    override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        dataStore.edit { prefs ->
            val current = decode(prefs[SETTINGS_KEY])
            prefs[SETTINGS_KEY] = encode(transform(current))
        }
    }

    private fun encode(settings: LauncherSettings): String =
        json.encodeToString(LauncherSettings.serializer(), settings)

    /** Falls back to defaults on a missing, blank or corrupt value. */
    private fun decode(raw: String?): LauncherSettings {
        if (raw.isNullOrBlank()) return LauncherSettings()
        return try {
            json.decodeFromString(LauncherSettings.serializer(), raw)
        } catch (e: Exception) {
            LauncherSettings()
        }
    }

    companion object {
        const val DATA_STORE_NAME: String = "foldos_settings"
        private val SETTINGS_KEY: Preferences.Key<String> = stringPreferencesKey("settings")
    }
}
