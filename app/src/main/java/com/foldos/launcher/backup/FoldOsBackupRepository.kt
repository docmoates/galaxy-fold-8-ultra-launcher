package com.foldos.launcher.backup

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.foldos.launcher.apppicker.GroupStore
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.icons.IconOverride
import com.foldos.launcher.icons.IconOverrideStore
import com.foldos.launcher.core.repo.BackupFile
import com.foldos.launcher.core.repo.BackupRepository
import com.foldos.launcher.core.repo.IconResolver
import com.foldos.launcher.core.repo.ImportResult
import com.foldos.launcher.core.repo.LayoutRepository
import com.foldos.launcher.core.repo.ProfileRepository
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The real [BackupRepository]: exports the settings, home layouts, profiles and per-app icon
 * overrides as one [BackupFile] JSON document and restores such a document.
 *
 * Export leaves out hosted app widgets (their ids belong to this install), search ranking, mail
 * tokens and notification state. Import validates first (malformed JSON, a `version` newer than
 * [BackupCodec.SUPPORTED_VERSION], or a document with no usable page changes nothing), then:
 *
 * 1. replaces the layouts in one transaction (`LayoutRepository.replaceAll`), skipping apps that are
 *    not installed on this device, and releases the old hosted widget ids,
 * 2. replaces the saved profiles,
 * 3. writes the settings, keeping this device's `wizardDone = true` (and its Mail client id when the
 *    backup has none),
 * 4. replaces the per-app icon overrides,
 * 5. restores the per-screen dock apps and the app pop-up groups (both optional in the document),
 * 6. asks Android for an Auto Backup pass.
 *
 * Steps 2 to 5 are independent: a failure there is reported in the message but does not undo the
 * layout.
 */
class FoldOsBackupRepository(
    context: Context,
    private val settings: SettingsRepository,
    private val layouts: LayoutRepository,
    private val profiles: ProfileRepository,
    private val icons: IconResolver,
    private val apps: AppsRepository,
) : BackupRepository {

    private val appContext: Context = context.applicationContext
    private val iconStore = IconOverrideStore(appContext)
    private val groupStore = GroupStore(appContext)

    override suspend fun exportJson(): String {
        val currentSettings = settings.settings.first()
        val pages = layouts.allPagesWithItems()
        val savedProfiles = profiles.profiles.first()
        val overrides = iconStore.overrides.first()
        val dock = ScreenKind.values().associate { it.name to layouts.dockApps(it).first() }.filterValues { it.isNotEmpty() }
        val groups = groupStore.groups.first().associate { it.name to it.appKeys }
        return withContext(Dispatchers.Default) {
            val file = BackupFile(
                version = BackupCodec.SUPPORTED_VERSION,
                createdAt = System.currentTimeMillis(),
                settings = currentSettings,
                pages = BackupCodec.exportPages(pages),
                profiles = savedProfiles,
                dockApps = dock,
                appGroups = groups,
                iconOverrides = overrides.mapValues { (_, value) ->
                    BackupCodec.json.encodeToString(IconOverride.serializer(), value)
                },
            )
            BackupCodec.encode(file)
        }
    }

    override suspend fun importJson(json: String): ImportResult {
        val text = json.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return ImportResult.failure("The file is empty.")

        // Validate everything before touching current data.
        val version = try {
            BackupCodec.peekVersion(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult.failure("This is not a FoldOS backup: the file is not valid JSON.")
        }
        if (version == null || version < 1) {
            return ImportResult.failure("This is not a FoldOS backup file.")
        }
        if (version > BackupCodec.SUPPORTED_VERSION) {
            return ImportResult.failure(
                "This backup was made by a newer FoldOS (backup format $version, this version reads up to " +
                    "${BackupCodec.SUPPORTED_VERSION}). Update FoldOS and try again.",
            )
        }
        val backup: BackupFile = try {
            withContext(Dispatchers.Default) { BackupCodec.decode(text) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult.failure("This is not a valid FoldOS backup file.")
        }

        val resolver = AppResolver(appContext)
        val built = withContext(Dispatchers.Default) { BackupCodec.buildLayout(backup, resolver::resolve) }
        if (built.pages.isEmpty() && backup.pages.isNotEmpty()) {
            return ImportResult.failure("The backup has no home pages this version of FoldOS can read.")
        }

        // 1. Layout: one transaction, so a failure leaves the current layout untouched.
        val previousWidgetIds = try {
            layouts.allPagesWithItems().flatMap { it.second }.mapNotNull { it.appWidgetId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        try {
            layouts.replaceAll(built.pages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "replaceAll failed", e)
            return ImportResult.failure("Could not write the home layout: ${e.message ?: e.javaClass.simpleName}.")
        }
        BackupSupport.releaseAppWidgetIds(appContext, previousWidgetIds)

        val problems = ArrayList<String>()

        // 2. Profiles. Deleting the active one clears `activeProfile`; the final settings write below
        // sets it again from the backup.
        attempt("profiles", problems) {
            val wanted = backup.profiles.map { it.id }.toSet()
            for (existing in profiles.profiles.first()) {
                if (existing.id !in wanted) profiles.delete(existing.id)
            }
            for (profile in backup.profiles) profiles.save(profile)
        }

        // 3. Settings.
        attempt("settings", problems) {
            val restored = sanitize(backup.settings)
            val activeId = restored.activeProfile?.takeIf { id -> backup.profiles.any { it.id == id } }
            settings.update { current ->
                val next = restored.copy(
                    wizardDone = true,
                    activeProfile = activeId,
                    mailClientId = restored.mailClientId ?: current.mailClientId,
                )
                // The restored pages already carry their own styles; keep the settings watcher from
                // flattening them to the global style.
                StyleRestoreGate.arm(current, next)
                next
            }
        }

        // A backup can lack a screen (never opened INNER, SAME mode): seed it now, with the restored
        // column count and style, because the launcher only ensures the default layout once a process.
        attempt("default pages", problems) {
            val current = settings.settings.first()
            val present = layouts.allPagesWithItems().map { it.first.screen }.toSet()
            val list = apps.apps.first()
            for (screen in listOf(ScreenKind.COVER, ScreenKind.INNER)) {
                if (screen !in present) {
                    layouts.ensureDefaultLayout(screen, list, current.colsFor(screen), current.styleFor(screen))
                }
            }
        }

        // 4. Per-app icon overrides.
        attempt("icon choices", problems) {
            val wanted = backup.iconOverrides
            for (appKey in iconStore.overrides.first().keys) {
                if (appKey !in wanted) icons.override(appKey, null, null)
            }
            for ((appKey, raw) in wanted) {
                if (!resolver.isInstalled(appKey.substringBefore('/'))) continue
                val override = try {
                    BackupCodec.json.decodeFromString(IconOverride.serializer(), raw)
                } catch (e: Exception) {
                    continue
                }
                if (override.packageName == null && override.drawableName == null) continue
                icons.override(appKey, override.packageName, override.drawableName)
            }
        }

        // 5. Dock apps and app pop-up groups. Both are optional in the document: an older backup (or
        // one with default docks / no groups) leaves this device's current choices alone.
        if (backup.dockApps.isNotEmpty()) {
            attempt("dock apps", problems) {
                for (screen in ScreenKind.values()) {
                    layouts.setDockApps(screen, backup.dockApps[screen.name].orEmpty())
                }
            }
        }
        if (backup.appGroups.isNotEmpty()) {
            attempt("app groups", problems) {
                for (existing in groupStore.groups.first()) groupStore.deleteGroup(existing.name)
                for ((name, appKeys) in backup.appGroups) {
                    val stored = groupStore.createGroup(name) ?: continue
                    for (appKey in appKeys) groupStore.addApp(stored, appKey)
                }
            }
        }

        BackupSupport.requestAutoBackup(appContext)

        val message = buildString {
            append("Restored ${built.imported} home item${if (built.imported == 1) "" else "s"}")
            if (built.skipped > 0) {
                append(", skipped ${built.skipped} (app not installed on this phone or not readable)")
            }
            append(", with settings, profiles and icon choices.")
            append(" Hosted app widgets are not part of a backup; add them again from the widget picker.")
            if (problems.isNotEmpty()) append(" Could not restore: ${problems.joinToString(", ")}.")
        }
        return ImportResult(ok = true, message = message, itemsImported = built.imported)
    }

    /** Runs one independent restore step; a failure is recorded in [problems] and never aborts the import. */
    private suspend fun attempt(label: String, problems: MutableList<String>, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Restoring $label failed", e)
            problems += label
        }
    }

    /** Keeps hand-edited or foreign values inside the ranges the UI supports. */
    private fun sanitize(value: LauncherSettings): LauncherSettings = value.copy(
        colsCover = value.colsCover.coerceIn(2, 10),
        colsInner = value.colsInner.coerceIn(2, 10),
        textScale = if (value.textScale.isFinite()) value.textScale.coerceIn(0.85f, 1.6f) else 1.0f,
    )

    /** Package checks against this device, cached for one import. */
    private class AppResolver(context: Context) {
        private val packageManager: PackageManager = context.packageManager
        private val cache = HashMap<String, String?>()

        fun isInstalled(packageName: String): Boolean = try {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        /** The activity to place for [packageName]/[activityName] here, or null when the app is gone. */
        fun resolve(packageName: String?, activityName: String?): String? {
            if (packageName.isNullOrBlank()) return null
            val key = "$packageName/${activityName.orEmpty()}"
            if (cache.containsKey(key)) return cache[key]
            val found = lookup(packageName, activityName)
            cache[key] = found
            return found
        }

        private fun lookup(packageName: String, activityName: String?): String? {
            if (!activityName.isNullOrBlank()) {
                try {
                    packageManager.getActivityInfo(
                        ComponentName(packageName, activityName),
                        PackageManager.ComponentInfoFlags.of(0L),
                    )
                    return activityName
                } catch (e: PackageManager.NameNotFoundException) {
                    // Renamed or removed activity: fall through to the package's launcher activity.
                }
            }
            return packageManager.getLaunchIntentForPackage(packageName)?.component?.className
        }
    }

    private companion object {
        const val TAG = "FoldOsBackup"
    }
}
