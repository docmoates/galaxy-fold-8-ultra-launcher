package com.foldos.launcher.profiles

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.Profile
import com.foldos.launcher.core.repo.ProfileRepository
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.UUID

/** Process-wide Preferences DataStore for profiles; one instance per file name, as DataStore requires. */
private val Context.foldOsProfilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FoldOsProfileRepository.DATA_STORE_NAME,
)

/**
 * JSON codec shared by the profile store and the profile UI. A profile's `settingsJson` is a
 * [LauncherSettings] with `activeProfile` cleared, encoded exactly the way `SettingsRepositoryImpl`
 * stores settings (`encodeDefaults`, `ignoreUnknownKeys`), so new fields never need a migration.
 */
object ProfileJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The part of the settings a profile owns: everything except the active-profile marker. */
    fun snapshot(settings: LauncherSettings): LauncherSettings = settings.copy(activeProfile = null)

    fun encodeSettings(settings: LauncherSettings): String =
        json.encodeToString(LauncherSettings.serializer(), snapshot(settings))

    /** Null when [raw] is blank or not a settings object. */
    fun decodeSettingsOrNull(raw: String): LauncherSettings? {
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString(LauncherSettings.serializer(), raw)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Persisted [ProfileRepository]: profiles live as one JSON array in the Preferences DataStore
 * `foldos_profiles`. Replaces `data.InMemoryProfileRepository` in `AppGraphImpl.profiles`.
 *
 * - [activate] decodes the profile's `settingsJson`, keeps the device's `wizardDone` plus the
 *   Mail client id and the Profiles feature switch from the current settings, and writes the
 *   profile's `hiddenApps`, its name into the legacy `profile` field and `activeProfile = id`.
 * - The active profile is live: every settings change made while it is active is mirrored back
 *   into its `settingsJson` / `hiddenApps`, so switching away and back restores the latest look.
 *   Saving the active profile from the editor re-applies it.
 * - Default profiles are seeded once, right after the setup wizard finishes (so they snapshot the
 *   wizard's choices): Work, Home and Night (dark appearance). None carries a schedule; time
 *   windows and Do Not Disturb rules are opt-in from the profile editor.
 * - Automatic switching: [applyAutomaticSwitch] picks the profile the rules select right now
 *   (Do Not Disturb profiles first, then time windows) and only acts when that choice *changes*,
 *   so a manual pick inside a window stands until the next boundary. When a rule fires while no
 *   profile is active, the live settings are first parked in the fallback (rule-less) profile so
 *   they come back when the window ends. [ProfileScheduler] sets an alarm for the next boundary;
 *   `ProfileAlarmReceiver`, `ProfileBootReceiver`, `ProfileDndReceiver` and
 *   [ProfileAutoSwitchEffect] call back in here.
 */
class FoldOsProfileRepository(
    context: Context,
    private val settings: SettingsRepository,
    /** Never-cancelled scope (`AppGraph.appScope`) for seeding, mirroring and receiver work. */
    val appScope: CoroutineScope,
) : ProfileRepository {

    private val appContext: Context = context.applicationContext
    private val dataStore: DataStore<Preferences> = appContext.foldOsProfilesDataStore
    private val listSerializer = ListSerializer(Profile.serializer())

    /** Alarm scheduling for time-window profiles. */
    val scheduler: ProfileScheduler = ProfileScheduler(appContext)

    /** Serialises every write that reads settings and profiles together (activate, mirror, auto-switch). */
    private val writeMutex = Mutex()

    override val profiles: Flow<List<Profile>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decodeList(prefs[PROFILES_KEY]) }

    init {
        // Seed, then arm the alarm once; `ProfileBootReceiver` / `ProfileAutoSwitchEffect` re-arm it
        // after boot and on every resume, so no second launch is needed here.
        appScope.launch {
            try {
                seedDefaultsIfNeeded()
                rescheduleAlarms()
            } catch (e: Exception) {
                Log.w(TAG, "Seeding default profiles failed", e)
            }
        }
        appScope.launch { mirrorActiveProfile() }
    }

    // ------------------------------------------------------------------------------------------
    // ProfileRepository
    // ------------------------------------------------------------------------------------------

    override suspend fun save(profile: Profile) {
        writeMutex.withLock {
            mutateList { list ->
                if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it }
                else list + profile
            }
            // The active profile is live: saving it (new hidden apps, re-captured settings) applies it.
            val current = settings.settings.first()
            if (current.activeProfile == profile.id) applyLocked(profile, current)
        }
        rescheduleAlarms()
    }

    override suspend fun delete(id: String) {
        writeMutex.withLock {
            mutateList { list -> list.filterNot { it.id == id } }
            settings.update { current ->
                if (current.activeProfile == id) current.copy(activeProfile = null) else current
            }
        }
        rescheduleAlarms()
    }

    override suspend fun activate(id: String?) {
        writeMutex.withLock { activateLocked(id) }
    }

    override suspend fun captureCurrent(name: String): Profile {
        val current = settings.settings.first()
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Profile" },
            settingsJson = ProfileJson.encodeSettings(current),
            hiddenApps = current.hiddenApps,
        )
        save(profile)
        return profile
    }

    // ------------------------------------------------------------------------------------------
    // Automatic switching
    // ------------------------------------------------------------------------------------------

    /**
     * Evaluates the schedule / Do Not Disturb rules at [now] and switches profiles when the rules'
     * choice differs from the last evaluation. No-op while the Profiles feature is off.
     *
     * When a window ends and nothing else matches, the profile that was active before the rules took
     * over is restored; without one, the first profile without rules is applied, and if there is
     * none `activeProfile` is cleared and the settings stay as they are.
     */
    suspend fun applyAutomaticSwitch(now: Long = System.currentTimeMillis()) {
        writeMutex.withLock { evaluateRulesLocked(now) }
    }

    private suspend fun evaluateRulesLocked(now: Long) {
        val current = settings.settings.first()
        if (!current.feature(LauncherSettings.FEATURE_PROFILES)) return
        val list = profiles.first()
        val match = ProfileScheduler.matching(list, now, isDndOn())
        val decision = match?.id ?: NO_MATCH
        val previous = dataStore.data.first()[AUTO_DECISION_KEY]
        if (previous == decision) return
        dataStore.edit { prefs -> prefs[AUTO_DECISION_KEY] = decision }
        if (match != null) {
            if (current.activeProfile != match.id) {
                // Remember the profile the user was in so the window's end returns to it (not
                // always the first rule-less profile).
                val before = list.firstOrNull { it.id == current.activeProfile }
                if (before != null && !before.scheduled && !before.onDnd) {
                    dataStore.edit { prefs -> prefs[PRE_RULE_PROFILE_KEY] = before.id }
                } else if (before == null) {
                    dataStore.edit { prefs -> prefs.remove(PRE_RULE_PROFILE_KEY) }
                }
                // Settings changed while no profile was active are mirrored nowhere; park them in
                // the profile the window's end will restore, so the round trip keeps them.
                if (current.activeProfile == null) preserveLiveSettingsLocked(list, current)
                applyLocked(match, current)
            }
            return
        }
        if (previous == null) return
        // A window (or Do Not Disturb) just ended. Only step away from a profile the rules put there.
        val active = list.firstOrNull { it.id == current.activeProfile } ?: return
        if (!active.scheduled && !active.onDnd) return
        val rememberedId = dataStore.data.first()[PRE_RULE_PROFILE_KEY]
        val remembered = list.firstOrNull { it.id == rememberedId && !it.scheduled && !it.onDnd }
        val fallback = remembered ?: list.firstOrNull { !it.scheduled && !it.onDnd }
        dataStore.edit { prefs -> prefs.remove(PRE_RULE_PROFILE_KEY) }
        if (fallback != null) applyLocked(fallback, current) else activateLocked(null)
    }

    /** Re-arms the alarm for the next schedule boundary, or cancels it when the feature is off. */
    suspend fun rescheduleAlarms() {
        val current = settings.settings.first()
        if (!current.feature(LauncherSettings.FEATURE_PROFILES)) {
            scheduler.cancel()
            return
        }
        scheduler.schedule(profiles.first())
    }

    /** True while any Do Not Disturb mode (priority, alarms only, total silence) is active. */
    fun isDndOn(): Boolean {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return false
        val filter = try {
            manager.currentInterruptionFilter
        } catch (e: Exception) {
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    // ------------------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------------------

    private suspend fun activateLocked(id: String?) {
        if (id == null) {
            settings.update { it.copy(activeProfile = null) }
            return
        }
        val profile = profiles.first().firstOrNull { it.id == id } ?: return
        applyLocked(profile, settings.settings.first())
    }

    /** Writes [profile]'s settings and hidden apps; device state comes from [current]. */
    private suspend fun applyLocked(profile: Profile, current: LauncherSettings) {
        val stored = ProfileJson.decodeSettingsOrNull(profile.settingsJson)
        if (stored == null) Log.w(TAG, "Profile ${profile.id} has unreadable settings; applying hidden apps only")
        settings.update { latest ->
            val base = stored ?: latest
            base.copy(
                hiddenApps = profile.hiddenApps,
                activeProfile = profile.id,
                profile = profile.name,
                // Setup state, the Mail sign-in and the Profiles switch itself are device state.
                wizardDone = latest.wizardDone || current.wizardDone,
                mailClientId = latest.mailClientId,
                features = base.features + (LauncherSettings.FEATURE_PROFILES to latest.feature(LauncherSettings.FEATURE_PROFILES)),
            )
        }
    }

    /**
     * Snapshots the live [current] settings (no profile active) into the first rule-less profile,
     * the one [evaluateRulesLocked] falls back to when the window ends; without one, a new
     * "Before schedule" profile is added so the customisations are never lost.
     */
    private suspend fun preserveLiveSettingsLocked(list: List<Profile>, current: LauncherSettings) {
        val fallback = list.firstOrNull { !it.scheduled && !it.onDnd }
        val keeper = fallback?.copy(
            settingsJson = ProfileJson.encodeSettings(current.copy(profile = fallback.name)),
            hiddenApps = current.hiddenApps,
        ) ?: Profile(
            id = UUID.randomUUID().toString(),
            name = "Before schedule",
            settingsJson = ProfileJson.encodeSettings(current.copy(profile = "Before schedule")),
            hiddenApps = current.hiddenApps,
        )
        mutateList { stored ->
            if (stored.any { it.id == keeper.id }) stored.map { if (it.id == keeper.id) keeper else it }
            else stored + keeper
        }
    }

    /** Keeps the active profile's snapshot equal to the live settings. */
    private suspend fun mirrorActiveProfile() {
        settings.settings
            .distinctUntilChanged()
            .collect { current ->
                val id = current.activeProfile ?: return@collect
                try {
                    writeMutex.withLock {
                        val snapshot = ProfileJson.snapshot(current)
                        dataStore.edit { prefs ->
                            val list = decodeList(prefs[PROFILES_KEY])
                            val profile = list.firstOrNull { it.id == id } ?: return@edit
                            val stored = ProfileJson.decodeSettingsOrNull(profile.settingsJson)
                            if (stored == snapshot && profile.hiddenApps == current.hiddenApps) return@edit
                            val updated = profile.copy(
                                settingsJson = ProfileJson.json.encodeToString(LauncherSettings.serializer(), snapshot),
                                hiddenApps = current.hiddenApps,
                            )
                            prefs[PROFILES_KEY] = encodeList(list.map { if (it.id == id) updated else it })
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Mirroring settings into profile $id failed", e)
                }
            }
    }

    /**
     * Seeds Work / Home / Night once. Waits for the wizard to finish so the snapshots carry the
     * user's first choices rather than the factory defaults. The seeds carry no time windows and
     * hide nothing extra: automatic switching only starts once the user adds a schedule (or a Do
     * Not Disturb rule) in the profile editor.
     */
    private suspend fun seedDefaultsIfNeeded() {
        if (dataStore.data.first()[SEEDED_KEY] == true) return
        val current = settings.settings.first { it.wizardDone }
        val base = ProfileJson.snapshot(current)
        val defaults = listOf(
            Profile(
                id = DEFAULT_WORK_ID,
                name = "Work",
                settingsJson = ProfileJson.json.encodeToString(LauncherSettings.serializer(), base.copy(profile = "Work")),
                hiddenApps = current.hiddenApps,
            ),
            Profile(
                id = DEFAULT_HOME_ID,
                name = "Home",
                settingsJson = ProfileJson.json.encodeToString(LauncherSettings.serializer(), base.copy(profile = "Home")),
                hiddenApps = current.hiddenApps,
            ),
            Profile(
                id = DEFAULT_NIGHT_ID,
                name = "Night",
                settingsJson = ProfileJson.json.encodeToString(
                    LauncherSettings.serializer(),
                    base.copy(profile = "Night", appearance = Appearance.DARK),
                ),
                hiddenApps = current.hiddenApps,
            ),
        )
        dataStore.edit { prefs ->
            if (prefs[SEEDED_KEY] == true) return@edit
            // A profile saved before seeding ran wins; never overwrite user data.
            if (decodeList(prefs[PROFILES_KEY]).isEmpty()) prefs[PROFILES_KEY] = encodeList(defaults)
            prefs[SEEDED_KEY] = true
        }
    }

    private suspend fun mutateList(transform: (List<Profile>) -> List<Profile>) {
        dataStore.edit { prefs ->
            prefs[PROFILES_KEY] = encodeList(transform(decodeList(prefs[PROFILES_KEY])))
        }
    }

    private fun encodeList(list: List<Profile>): String = ProfileJson.json.encodeToString(listSerializer, list)

    private fun decodeList(raw: String?): List<Profile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            ProfileJson.json.decodeFromString(listSerializer, raw)
        } catch (e: Exception) {
            Log.w(TAG, "Stored profiles are unreadable; starting empty", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "FoldOsProfiles"

        const val DATA_STORE_NAME: String = "foldos_profiles"

        /** Ids of the seeded default profiles. */
        const val DEFAULT_WORK_ID: String = "work"
        const val DEFAULT_HOME_ID: String = "home"
        const val DEFAULT_NIGHT_ID: String = "night"

        /** `java.util.Calendar` days Monday..Friday. */
        val WEEKDAYS: Set<Int> = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,
        )

        private val PROFILES_KEY: Preferences.Key<String> = stringPreferencesKey("profiles")
        private val SEEDED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("seeded")
        private val AUTO_DECISION_KEY: Preferences.Key<String> = stringPreferencesKey("auto_decision")

        /** The manually chosen profile that was active when a schedule / DND rule took over. */
        private val PRE_RULE_PROFILE_KEY: Preferences.Key<String> = stringPreferencesKey("pre_rule_profile")

        /** Stored [AUTO_DECISION_KEY] value when no rule matched. */
        private const val NO_MATCH = ""

        /**
         * The graph's profile repository when it is this implementation (the Integrate agent's
         * `AppGraphImpl.profiles` line); null before `Application.onCreate` or with the placeholder.
         */
        fun from(context: Context): FoldOsProfileRepository? = try {
            FoldOsApplication.from(context).graph.profiles as? FoldOsProfileRepository
        } catch (e: Exception) {
            null
        }
    }
}
