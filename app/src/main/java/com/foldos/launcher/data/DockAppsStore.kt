package com.foldos.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldos.launcher.core.model.ScreenKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Process-wide Preferences DataStore for the dock's apps. Declared at file level so there is exactly
 * one instance per file name, as DataStore requires.
 */
private val Context.foldOsDockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DockAppsStore.DATA_STORE_NAME,
)

/**
 * The ordered `AppInfo.key` list shown in the dock, one list per [ScreenKind]. Stored as one
 * newline-joined string per screen (app keys are "package/activity", they never contain a newline).
 * An absent / empty value means "use the default dock apps". Backs `LayoutRepository.dockApps` /
 * `setDockApps`.
 */
class DockAppsStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsDockDataStore

    fun apps(screen: ScreenKind): Flow<List<String>> {
        val key = keyFor(screen)
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> decode(prefs[key]) }
            .distinctUntilChanged()
    }

    suspend fun set(screen: ScreenKind, appKeys: List<String>) {
        val key = keyFor(screen)
        val cleaned = appKeys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        dataStore.edit { prefs ->
            if (cleaned.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = cleaned.joinToString(SEPARATOR)
            }
        }
    }

    private fun keyFor(screen: ScreenKind): Preferences.Key<String> = stringPreferencesKey("dock_${screen.name}")

    private fun decode(raw: String?): List<String> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEPARATOR).filter { it.isNotEmpty() }

    companion object {
        const val DATA_STORE_NAME: String = "foldos_dock"
        private const val SEPARATOR: String = "\n"
    }
}
