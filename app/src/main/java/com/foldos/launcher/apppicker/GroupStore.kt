package com.foldos.launcher.apppicker

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

/**
 * Process-wide Preferences DataStore for the app pop-up's user groups. Declared at file level so there
 * is exactly one instance per file name, as DataStore requires.
 */
private val Context.foldOsGroupsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = GroupStore.DATA_STORE_NAME,
)

/** A user-made group of apps: [name] (unique, case-insensitive) and the [appKeys] (`AppInfo.key`) it holds. */
data class AppGroup(val name: String, val appKeys: List<String>)

/**
 * User groups of the app pop-up ("group folders"), persisted in the Preferences DataStore
 * `foldos_groups`: one `order` key (the group names, newline-joined, in creation order) and one
 * `g:<name>` key per group (its app keys, newline-joined; app keys are "package/activity" and never
 * contain a newline). Keys of uninstalled apps stay stored and are skipped when resolving.
 */
class GroupStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsGroupsDataStore

    /** The groups in creation order. Re-emits on every change. */
    val groups: Flow<List<AppGroup>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            decode(prefs[ORDER_KEY]).map { name ->
                AppGroup(name, decode(prefs[groupKey(name)]))
            }
        }
        .distinctUntilChanged()

    /**
     * Creates the group [rawName] (optionally with [firstAppKey] already in it). Returns the stored
     * (cleaned) name, or null when the name is blank or a group with that name (ignoring case) exists.
     */
    suspend fun createGroup(rawName: String, firstAppKey: String? = null): String? {
        val name = cleanName(rawName)
        if (name.isEmpty()) return null
        var created = false
        dataStore.edit { prefs ->
            val order = decode(prefs[ORDER_KEY])
            if (order.none { it.equals(name, ignoreCase = true) }) {
                prefs[ORDER_KEY] = (order + name).joinToString(SEPARATOR)
                val first = firstAppKey?.trim().orEmpty()
                if (first.isNotEmpty()) prefs[groupKey(name)] = first else prefs.remove(groupKey(name))
                created = true
            }
        }
        return if (created) name else null
    }

    suspend fun deleteGroup(name: String) {
        dataStore.edit { prefs ->
            val order = decode(prefs[ORDER_KEY])
            val next = order.filterNot { it == name }
            if (next.isEmpty()) prefs.remove(ORDER_KEY) else prefs[ORDER_KEY] = next.joinToString(SEPARATOR)
            prefs.remove(groupKey(name))
        }
    }

    /** Renames [oldName] to [rawNewName]; false when the new name is blank or already used by another group. */
    suspend fun renameGroup(oldName: String, rawNewName: String): Boolean {
        val newName = cleanName(rawNewName)
        if (newName.isEmpty()) return false
        var renamed = false
        dataStore.edit { prefs ->
            val order = decode(prefs[ORDER_KEY])
            val index = order.indexOf(oldName)
            val clash = order.any { it != oldName && it.equals(newName, ignoreCase = true) }
            if (index >= 0 && !clash) {
                val keys = prefs[groupKey(oldName)]
                val nextOrder = order.toMutableList().also { it[index] = newName }
                prefs[ORDER_KEY] = nextOrder.joinToString(SEPARATOR)
                if (newName != oldName) {
                    prefs.remove(groupKey(oldName))
                }
                if (keys.isNullOrEmpty()) prefs.remove(groupKey(newName)) else prefs[groupKey(newName)] = keys
                renamed = true
            }
        }
        return renamed
    }

    suspend fun addApp(groupName: String, appKey: String) {
        val key = appKey.trim()
        if (key.isEmpty()) return
        dataStore.edit { prefs ->
            if (groupName !in decode(prefs[ORDER_KEY])) return@edit
            val keys = decode(prefs[groupKey(groupName)])
            if (key !in keys) prefs[groupKey(groupName)] = (keys + key).joinToString(SEPARATOR)
        }
    }

    suspend fun removeApp(groupName: String, appKey: String) {
        dataStore.edit { prefs ->
            if (groupName !in decode(prefs[ORDER_KEY])) return@edit
            val keys = decode(prefs[groupKey(groupName)]).filterNot { it == appKey }
            if (keys.isEmpty()) prefs.remove(groupKey(groupName)) else prefs[groupKey(groupName)] = keys.joinToString(SEPARATOR)
        }
    }

    private fun groupKey(name: String): Preferences.Key<String> = stringPreferencesKey("g:$name")

    private fun decode(raw: String?): List<String> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEPARATOR).filter { it.isNotEmpty() }

    companion object {
        const val DATA_STORE_NAME: String = "foldos_groups"
        const val MAX_NAME_LENGTH: Int = 24
        private const val SEPARATOR: String = "\n"
        private val ORDER_KEY: Preferences.Key<String> = stringPreferencesKey("order")

        /** Trims, flattens line breaks and caps the length of a group name. */
        fun cleanName(raw: String): String =
            raw.replace('\n', ' ').replace('\r', ' ').trim().take(MAX_NAME_LENGTH).trim()
    }
}
