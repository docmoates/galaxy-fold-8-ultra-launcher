package com.foldos.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import com.foldos.launcher.ai.FoldOsAiCommandRouter
import com.foldos.launcher.backup.FoldOsBackupRepository
import com.foldos.launcher.builtins.FoldOsBuiltInWidgets
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.repo.AiCommandRouter
import com.foldos.launcher.core.repo.AppsRepository
import com.foldos.launcher.core.repo.BackupRepository
import com.foldos.launcher.core.repo.BadgeRepository
import com.foldos.launcher.core.repo.BuiltInWidgetRegistry
import com.foldos.launcher.core.repo.HubRepository
import com.foldos.launcher.core.repo.IconResolver
import com.foldos.launcher.core.repo.LayoutRepository
import com.foldos.launcher.core.repo.MailRepository
import com.foldos.launcher.core.repo.ProfileRepository
import com.foldos.launcher.core.repo.SettingsRepository
import com.foldos.launcher.core.repo.WidgetCatalog
import com.foldos.launcher.data.db.LayoutDatabase
import com.foldos.launcher.hub.FoldOsHubRepository
import com.foldos.launcher.icons.FoldOsIconResolver
import com.foldos.launcher.mail.GmailMailRepository
import com.foldos.launcher.notifications.FoldOsBadgeRepository
import com.foldos.launcher.picker.FoldOsWidgetCatalog
import com.foldos.launcher.profiles.FoldOsProfileRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-scoped [AppGraph]. Each repository is created on first use and then kept for the
 * life of the process; the [appWidgetHost] is created right away (not started, see its KDoc), because the graph
 * is constructed exactly once, in `FoldOsApplication.onCreate`:
 *
 * ```
 * graph = AppGraphImpl(this)
 * ```
 *
 * Phase 2+3 wiring: [builtIns] is the FoldOS widget library, [widgets] the catalog merging it with
 * the installed AppWidget providers, [icons] the icon-pack resolver, and [apps] routes every
 * `AppInfo.iconLoader` through that resolver.
 *
 * Phase 4+5 wiring: [badges] (notification listener), [profiles] (DataStore + alarms), [hub]
 * (calendar / calls / contacts / tasks), [mail] (Gmail API via AppAuth) and [ai] (command router
 * with the template and on-device providers).
 */
class AppGraphImpl(context: Context) : AppGraph {

    private val appContext: Context = context.applicationContext

    /** The Room database behind [layouts]; exposed for callers that need direct DAO access. */
    val database: LayoutDatabase by lazy { LayoutDatabase.create(appContext) }

    override val settings: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }

    override val layouts: LayoutRepository by lazy { LayoutRepositoryImpl(database, DockAppsStore(appContext), appContext.noBackupFilesDir) }

    /**
     * Icons go through [icons] (packs, overrides, shape); the lambda keeps the two lazies acyclic.
     * `apps` hides `LauncherSettings.hiddenApps`; `allApps` on the impl has everything.
     */
    override val apps: AppsRepository by lazy { AppsRepositoryImpl(appContext, settings) { icons } }

    override val widgets: WidgetCatalog by lazy {
        FoldOsWidgetCatalog(appContext, AppWidgetManager.getInstance(appContext), builtIns, icons, appScope)
    }

    override val builtIns: BuiltInWidgetRegistry by lazy { FoldOsBuiltInWidgets(appContext) }

    override val icons: IconResolver by lazy { FoldOsIconResolver(appContext, settings) }

    // Phase 4+5 wiring. The `data/` placeholders (EmptyBadgeRepository, InMemoryProfileRepository,
    // EmptyHubRepository, NotConfiguredMailRepository, TemplateAiCommandRouter) stay as unused fallbacks.

    /** Badges and long-press previews from the notification listener, minus the quiet drawer's apps. */
    override val badges: BadgeRepository by lazy { FoldOsBadgeRepository(appContext, settings) }

    /** Persisted profiles with schedule / Do Not Disturb auto-switching (alarms re-armed by the receivers). */
    override val profiles: ProfileRepository by lazy { FoldOsProfileRepository(appContext, settings, appScope) }

    /** CalendarContract, CallLog, ContactsContract and the local task store behind the Hub. */
    override val hub: HubRepository by lazy { FoldOsHubRepository(appContext) }

    /** Gmail API through AppAuth (`LauncherSettings.mailClientId`); Gmail-app label counts without sign-in. */
    override val mail: MailRepository by lazy { GmailMailRepository(appContext, settings, appScope) }

    /**
     * Natural-language launcher commands plus the AI provider for `LauncherSettings.aiMode`: the
     * template engine by default, Gemini Nano (`AiCoreProvider`) when `aiMode == ondevice` and the
     * engine reports itself available, else the template engine again.
     */
    override val ai: AiCommandRouter by lazy { FoldOsAiCommandRouter(appContext, settings, apps, hub, badges, mail) }

    /**
     * Phase 6: JSON export / import of settings, layouts, profiles and icon overrides. The
     * `NotImplementedBackupRepository` placeholder stays in `data/` as an unused fallback.
     */
    override val backup: BackupRepository by lazy {
        FoldOsBackupRepository(appContext, settings, layouts, profiles, icons, apps)
    }

    /**
     * Not started here: `MainActivity` starts listening in `onStart` and stops in `onStop`, the way
     * Launcher3 does, so processes started without an Activity (boot receiver, wallpaper alarm,
     * notification listener) and a backgrounded launcher never wake up for widget updates.
     */
    override val appWidgetHost: AppWidgetHost = AppWidgetHost(appContext, AppGraph.APP_WIDGET_HOST_ID)

    override val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t -> Log.w(TAG, "appScope task failed", t) },
    )

    private companion object {
        const val TAG = "AppGraphImpl"
    }
}
