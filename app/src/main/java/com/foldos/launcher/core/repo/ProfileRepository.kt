package com.foldos.launcher.core.repo

import com.foldos.launcher.core.model.Profile
import kotlinx.coroutines.flow.Flow

/**
 * Saved launcher profiles (Settings > Profiles). Implemented by the Profiles agent;
 * `data.InMemoryProfileRepository` is the placeholder.
 */
interface ProfileRepository {
    /** Every saved profile, in creation order. */
    val profiles: Flow<List<Profile>>

    /** Inserts the profile, or replaces the one with the same [Profile.id]. */
    suspend fun save(profile: Profile)

    /** Removes a profile; clears `LauncherSettings.activeProfile` when it was the active one. */
    suspend fun delete(id: String)

    /**
     * Applies the profile's `settingsJson` and `hiddenApps` through the [SettingsRepository] and
     * sets `LauncherSettings.activeProfile` to [id]. Null only clears `activeProfile` (the current
     * settings stay as they are). Unknown ids are ignored.
     */
    suspend fun activate(id: String?)

    /**
     * Snapshots the current settings (including `hiddenApps`) into a new, saved profile called
     * [name] with a fresh id, and returns it.
     */
    suspend fun captureCurrent(name: String): Profile
}
