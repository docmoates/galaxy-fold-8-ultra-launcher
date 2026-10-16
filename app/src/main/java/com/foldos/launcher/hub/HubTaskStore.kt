package com.foldos.launcher.hub

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldos.launcher.core.repo.HubTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One stored Hub task; [dueMillis] null = no due date. */
@Serializable
data class HubTaskRecord(
    val id: Long,
    val title: String,
    val dueMillis: Long? = null,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toModel(): HubTask = HubTask(id = id, title = title, dueMillis = dueMillis, done = done)
}

/** Process-wide DataStore for the Hub's tasks; exactly one instance per file name, as DataStore requires. */
private val Context.foldOsTasksDataStore: DataStore<Preferences> by preferencesDataStore(
    name = HubTaskStore.DATA_STORE_NAME,
)

/**
 * The Hub's local task list (Today tab "Tasks" card), stored as one JSON array in the Preferences
 * DataStore `foldos_tasks`. Separate from the To-do widget's `foldos_todo` store on purpose: the
 * Hub tasks carry due dates and feed the briefing.
 *
 * Order: open tasks first, by due date (undated last, then by creation), then completed tasks.
 */
class HubTaskStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.foldOsTasksDataStore

    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val serializer = ListSerializer(HubTaskRecord.serializer())

    val tasks: Flow<List<HubTask>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decode(prefs[ITEMS_KEY]).sortedForDisplay().map { it.toModel() } }

    suspend fun add(title: String, dueMillis: Long?) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        mutate { list -> list + HubTaskRecord(id = nextId(list), title = trimmed, dueMillis = dueMillis) }
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

    private suspend fun mutate(transform: (List<HubTaskRecord>) -> List<HubTaskRecord>) {
        dataStore.edit { prefs ->
            prefs[ITEMS_KEY] = json.encodeToString(serializer, transform(decode(prefs[ITEMS_KEY])))
        }
    }

    private fun decode(raw: String?): List<HubTaskRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun nextId(list: List<HubTaskRecord>): Long = (list.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun List<HubTaskRecord>.sortedForDisplay(): List<HubTaskRecord> =
        sortedWith(
            compareBy<HubTaskRecord> { it.done }
                .thenBy { it.dueMillis == null }
                .thenBy { it.dueMillis ?: Long.MAX_VALUE }
                .thenBy { it.createdAt }
                .thenBy { it.id },
        )

    companion object {
        const val DATA_STORE_NAME: String = "foldos_tasks"
        private val ITEMS_KEY: Preferences.Key<String> = stringPreferencesKey("tasks")
    }
}
