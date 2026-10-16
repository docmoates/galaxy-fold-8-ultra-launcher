package com.foldos.launcher.builtins

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

/** One entry of the To-do widget. */
@Serializable
data class TodoItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Process-wide DataStore for the To-do widget; one instance per file name, as DataStore requires. */
private val Context.foldOsTodoDataStore: DataStore<Preferences> by preferencesDataStore(
    name = TodoStore.DATA_STORE_NAME,
)

/**
 * Local to-do list behind the To-do widget, stored as one JSON array in the Preferences DataStore
 * `foldos_todo`. Open items first, newest last; completed items sink to the bottom.
 */
class TodoStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsTodoDataStore

    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val serializer = ListSerializer(TodoItem.serializer())

    val items: Flow<List<TodoItem>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decode(prefs[ITEMS_KEY]).sortedForDisplay() }

    suspend fun add(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        mutate { list -> list + TodoItem(id = nextId(list), text = trimmed) }
    }

    suspend fun setDone(id: Long, done: Boolean) {
        mutate { list -> list.map { if (it.id == id) it.copy(done = done) else it } }
    }

    suspend fun remove(id: Long) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    suspend fun clearCompleted() {
        mutate { list -> list.filterNot { it.done } }
    }

    private suspend fun mutate(transform: (List<TodoItem>) -> List<TodoItem>) {
        dataStore.edit { prefs ->
            prefs[ITEMS_KEY] = json.encodeToString(serializer, transform(decode(prefs[ITEMS_KEY])))
        }
    }

    private fun decode(raw: String?): List<TodoItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun nextId(list: List<TodoItem>): Long = (list.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun List<TodoItem>.sortedForDisplay(): List<TodoItem> =
        sortedWith(compareBy<TodoItem> { it.done }.thenBy { it.createdAt }.thenBy { it.id })

    companion object {
        const val DATA_STORE_NAME: String = "foldos_todo"
        private val ITEMS_KEY: Preferences.Key<String> = stringPreferencesKey("items")
    }
}
