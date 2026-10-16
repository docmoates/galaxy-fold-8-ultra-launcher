package com.foldos.launcher.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldos.launcher.core.repo.NotificationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Process-wide DataStore for the quiet drawer; one instance per file name, as DataStore requires. */
private val Context.foldOsQuietDrawerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = QuietDrawerStore.DATA_STORE_NAME,
)

/**
 * Quiet notification drawer settings (PLAN.md 4.1, from Before Launcher): when [enabled], the
 * notifications of [packages] never badge or preview on the home screen and show only inside
 * [QuietDrawerSheet].
 */
data class QuietDrawerConfig(
    val enabled: Boolean = false,
    val packages: Set<String> = emptySet(),
) {
    /** True when notifications of [packageName] are routed to the quiet drawer. */
    fun isQuiet(packageName: String): Boolean = enabled && packageName in packages
}

/**
 * Persists [QuietDrawerConfig] in the Preferences DataStore `foldos_quiet_drawer`. Lives outside
 * `LauncherSettings` because the package list is per device.
 */
class QuietDrawerStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsQuietDrawerDataStore

    val config: Flow<QuietDrawerConfig> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            QuietDrawerConfig(
                enabled = prefs[ENABLED_KEY] ?: false,
                packages = prefs[PACKAGES_KEY] ?: emptySet(),
            )
        }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLED_KEY] = enabled }
    }

    suspend fun setPackages(packages: Set<String>) {
        dataStore.edit { prefs -> prefs[PACKAGES_KEY] = packages }
    }

    /** Adds or removes one package from the quiet list. */
    suspend fun setQuiet(packageName: String, quiet: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[PACKAGES_KEY] ?: emptySet()
            prefs[PACKAGES_KEY] = if (quiet) current + packageName else current - packageName
        }
    }

    companion object {
        const val DATA_STORE_NAME: String = "foldos_quiet_drawer"
        private val ENABLED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("enabled")
        private val PACKAGES_KEY: Preferences.Key<Set<String>> = stringSetPreferencesKey("packages")
    }
}

/**
 * The notifications routed to the quiet drawer, newest first: empty while the drawer is off, the
 * list is empty or notification access is missing.
 */
fun quietNotificationsFlow(store: QuietDrawerStore): Flow<List<NotificationSummary>> =
    combine(ActiveNotifications.notifications, store.config) { list, config ->
        if (!config.enabled || config.packages.isEmpty()) {
            emptyList()
        } else {
            list.filter { it.packageName in config.packages }
                .sortedByDescending { it.postTime }
                .map { it.toSummary() }
        }
    }.distinctUntilChanged()
