package com.foldos.launcher.data

import android.util.Log
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.Profile
import com.foldos.launcher.core.repo.ProfileRepository
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Placeholder [ProfileRepository] that keeps profiles in memory for the life of the process.
 * Activation and capture are real (they go through [SettingsRepository]); only persistence is
 * missing. Replaced by the Profiles agent (`profiles/`).
 */
class InMemoryProfileRepository(private val settings: SettingsRepository) : ProfileRepository {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val state = MutableStateFlow<List<Profile>>(emptyList())

    override val profiles: Flow<List<Profile>> = state

    override suspend fun save(profile: Profile) {
        state.update { list ->
            if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it }
            else list + profile
        }
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
        settings.update { current -> if (current.activeProfile == id) current.copy(activeProfile = null) else current }
    }

    override suspend fun activate(id: String?) {
        if (id == null) {
            settings.update { it.copy(activeProfile = null) }
            return
        }
        val profile = state.value.firstOrNull { it.id == id } ?: return
        val stored: LauncherSettings? = try {
            json.decodeFromString(LauncherSettings.serializer(), profile.settingsJson)
        } catch (e: Exception) {
            Log.w(TAG, "Profile ${profile.id} has unreadable settings; applying hidden apps only", e)
            null
        }
        settings.update { current ->
            (stored ?: current).copy(
                hiddenApps = profile.hiddenApps,
                activeProfile = id,
                // Setup state is device state, not profile state.
                wizardDone = current.wizardDone,
            )
        }
    }

    override suspend fun captureCurrent(name: String): Profile {
        val current = settings.settings.first()
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name,
            settingsJson = json.encodeToString(LauncherSettings.serializer(), current.copy(activeProfile = null)),
            hiddenApps = current.hiddenApps,
        )
        save(profile)
        return profile
    }

    private companion object {
        const val TAG = "ProfileRepository"
    }
}
