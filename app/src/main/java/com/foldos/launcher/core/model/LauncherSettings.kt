package com.foldos.launcher.core.model

import com.foldos.launcher.core.repo.IconShape
import kotlinx.serialization.Serializable

/**
 * Every user-facing launcher setting, mirroring `S0` in the mock-up. Defaults are the
 * mock-up defaults (PLAN.md section 8.1). Persisted as JSON in DataStore by the Settings agent.
 */
@Serializable
data class LauncherSettings(
    val screenMode: ScreenMode = ScreenMode.DIFFERENT,
    val appearance: Appearance = Appearance.DARK,
    val styleCover: HomeStyle = HomeStyle.GRID,
    val styleInner: HomeStyle = HomeStyle.DASHBOARD,
    val theme: ThemeId = ThemeId.GLASS,
    val gridMode: GridMode = GridMode.GRID,
    val colsCover: Int = 4,
    val colsInner: Int = 6,
    val iconSizeDp: Int = 58,
    val radiusDp: Int = 22,
    val labels: Boolean = true,
    val profile: String = "Work",
    /** Cover dock; default is the "Floating pill" preset. */
    val dockCover: DockConfig = DockConfig(),
    /** Inner dock; default is the "Taskbar" preset. */
    val dockInner: DockConfig = DockConfig(taskbar = true),
    val drawerStyle: DrawerStyle = DrawerStyle.POPUP,
    val popBlur: Boolean = true,
    val hubOn: Boolean = true,
    /** "left" or "right". */
    val hubPos: String = HUB_POS_LEFT,
    /** "bottom" or "top". */
    val hubBar: String = HUB_BAR_BOTTOM,
    /** Enabled hub tabs besides Today: subset of [HUB_TAB_AI], [HUB_TAB_MAIL], [HUB_TAB_CAL], [HUB_TAB_PEOPLE]. */
    val hubTabs: Set<String> = setOf(HUB_TAB_AI, HUB_TAB_MAIL, HUB_TAB_CAL, HUB_TAB_PEOPLE),
    val colors: ColorSchemeConfig = ColorSchemeConfig(),
    /** Widget picker grouping: "category", "app" or "recent". */
    val wpGroup: String = WP_GROUP_CATEGORY,
    /** Feature switches (mock-up `f`, minus popBlur which has its own field). Missing keys mean on. */
    val features: Map<String, Boolean> = mapOf(
        FEATURE_SEARCH to true,
        FEATURE_DOCK to true,
        FEATURE_BADGES to true,
        FEATURE_GESTURES to true,
        FEATURE_PAIRS to true,
        FEATURE_AI to true,
        FEATURE_STACKS to true,
        FEATURE_SMART to true,
        FEATURE_DRAWER_TABS to true,
        FEATURE_PROFILES to true,
    ),
    /** Selected icon pack package name; null = system icons. */
    val iconPack: String? = null,
    /** Adaptive icon mask (Settings > Look > Icon shape). */
    val iconShape: IconShape = IconShape.SYSTEM,
    /** True once the first-run setup wizard has finished (or been skipped). */
    val wizardDone: Boolean = false,
    /** Id of the last applied preset (Settings > Quick setup > Preset gallery); null = none. */
    val preset: String? = null,
    /**
     * Apps hidden from the drawer, search and `AppsRepository.apps`: package names (an
     * `AppInfo.key` "package/activity" is accepted too, to hide a single activity).
     */
    val hiddenApps: Set<String> = emptySet(),
    /** Gesture bindings keyed by [GestureKind.name]; a missing key means [GestureAction.NONE]. */
    val gestures: Map<String, GestureAction> = DEFAULT_GESTURES,
    /** Web search engine id: one of [SEARCH_ENGINES]. */
    val searchEngine: String = SEARCH_ENGINE_GOOGLE,
    /** Enabled universal-search providers: subset of [ALL_SEARCH_PROVIDERS]. */
    val searchProviders: Set<String> = ALL_SEARCH_PROVIDERS.toSet(),
    /** Notification badge style: [BADGE_COUNT], [BADGE_DOT] or [BADGE_OFF]. */
    val badgeStyle: String = BADGE_COUNT,
    /** Id of the active [Profile]; null = no profile applied. */
    val activeProfile: String? = null,
    /** OAuth client id the Mail tab signs in with; null = mail not configured. */
    val mailClientId: String? = null,
    /** AI engine: [AI_MODE_TEMPLATE] (rule-based replies), [AI_MODE_ONDEVICE] (Gemini Nano) or [AI_MODE_OFF]. */
    val aiMode: String = AI_MODE_TEMPLATE,
    /** Default folder style: [FOLDER_GRID], [FOLDER_LIST] or [FOLDER_BIG]; a `HomeItem.folderStyle` overrides it. */
    val folderStyle: String = FOLDER_GRID,
    /** Show notification previews on long-press of a badged app. */
    val notificationPreview: Boolean = true,
) {
    /** The action bound to [kind]; [GestureAction.NONE] when unbound. */
    fun gesture(kind: GestureKind): GestureAction = gestures[kind.name] ?: GestureAction.NONE

    fun withGesture(kind: GestureKind, action: GestureAction): LauncherSettings =
        copy(gestures = gestures + (kind.name to action))

    /** True when the package, or the "package/activity" key when [activityName] is given, is in [hiddenApps]. */
    fun isHidden(packageName: String, activityName: String? = null): Boolean =
        packageName in hiddenApps || (activityName != null && "$packageName/$activityName" in hiddenApps)

    fun searchProvider(key: String): Boolean = key in searchProviders

    fun withSearchProvider(key: String, enabled: Boolean): LauncherSettings =
        copy(searchProviders = if (enabled) searchProviders + key else searchProviders - key)

    /** True unless the feature was explicitly switched off. */
    fun feature(key: String): Boolean = features[key] ?: true

    fun withFeature(key: String, enabled: Boolean): LauncherSettings =
        copy(features = features + (key to enabled))

    fun dockFor(screen: ScreenKind): DockConfig = when (screen) {
        ScreenKind.COVER -> dockCover
        ScreenKind.INNER, ScreenKind.FLEX -> dockInner
    }

    fun styleFor(screen: ScreenKind): HomeStyle = when (screen) {
        ScreenKind.COVER -> styleCover
        ScreenKind.INNER, ScreenKind.FLEX -> styleInner
    }

    fun colsFor(screen: ScreenKind): Int = when (screen) {
        ScreenKind.COVER -> colsCover
        ScreenKind.INNER, ScreenKind.FLEX -> colsInner
    }

    companion object {
        const val HUB_POS_LEFT: String = "left"
        const val HUB_POS_RIGHT: String = "right"
        const val HUB_BAR_BOTTOM: String = "bottom"
        const val HUB_BAR_TOP: String = "top"

        const val HUB_TAB_AI: String = "ai"
        const val HUB_TAB_MAIL: String = "mail"
        const val HUB_TAB_CAL: String = "cal"
        const val HUB_TAB_PEOPLE: String = "people"

        const val WP_GROUP_CATEGORY: String = "category"
        const val WP_GROUP_APP: String = "app"
        const val WP_GROUP_RECENT: String = "recent"

        const val FEATURE_SEARCH: String = "search"
        const val FEATURE_DOCK: String = "dock"
        const val FEATURE_BADGES: String = "badges"
        const val FEATURE_GESTURES: String = "gestures"
        const val FEATURE_PAIRS: String = "pairs"
        const val FEATURE_AI: String = "ai"
        const val FEATURE_STACKS: String = "stacks"
        const val FEATURE_SMART: String = "smart"
        const val FEATURE_DRAWER_TABS: String = "drawerTabs"
        const val FEATURE_PROFILES: String = "profiles"

        val ALL_FEATURES: List<String> = listOf(
            FEATURE_SEARCH, FEATURE_DOCK, FEATURE_BADGES, FEATURE_GESTURES, FEATURE_PAIRS,
            FEATURE_AI, FEATURE_STACKS, FEATURE_SMART, FEATURE_DRAWER_TABS, FEATURE_PROFILES,
        )

        /** Default gesture bindings (Settings > Gestures). Gestures not listed are unbound. */
        val DEFAULT_GESTURES: Map<String, GestureAction> = mapOf(
            GestureKind.SWIPE_UP.name to GestureAction(GestureAction.TYPE_DRAWER),
            GestureKind.SWIPE_DOWN.name to GestureAction(GestureAction.TYPE_NOTIFICATIONS),
            GestureKind.DOUBLE_TAP.name to GestureAction(GestureAction.TYPE_NONE),
            GestureKind.PINCH_IN.name to GestureAction(GestureAction.TYPE_EDIT),
            GestureKind.SWIPE_UP_2F.name to GestureAction(GestureAction.TYPE_SEARCH),
            GestureKind.UNFOLD.name to GestureAction(GestureAction.TYPE_NONE),
        )

        const val SEARCH_ENGINE_GOOGLE: String = "google"
        const val SEARCH_ENGINE_BING: String = "bing"
        const val SEARCH_ENGINE_DUCKDUCKGO: String = "duckduckgo"
        const val SEARCH_ENGINE_KAGI: String = "kagi"
        const val SEARCH_ENGINE_BRAVE: String = "brave"
        const val SEARCH_ENGINE_ECOSIA: String = "ecosia"

        val SEARCH_ENGINES: List<String> = listOf(
            SEARCH_ENGINE_GOOGLE, SEARCH_ENGINE_BING, SEARCH_ENGINE_DUCKDUCKGO,
            SEARCH_ENGINE_KAGI, SEARCH_ENGINE_BRAVE, SEARCH_ENGINE_ECOSIA,
        )

        const val SEARCH_APPS: String = "apps"
        const val SEARCH_CONTACTS: String = "contacts"
        const val SEARCH_SETTINGS: String = "settings"
        const val SEARCH_FILES: String = "files"
        const val SEARCH_CALENDAR: String = "calendar"
        const val SEARCH_WEB: String = "web"
        const val SEARCH_CALCULATOR: String = "calculator"
        const val SEARCH_UNITS: String = "units"

        /** Every universal-search provider, in result-section order. */
        val ALL_SEARCH_PROVIDERS: List<String> = listOf(
            SEARCH_APPS, SEARCH_CONTACTS, SEARCH_SETTINGS, SEARCH_FILES,
            SEARCH_CALENDAR, SEARCH_WEB, SEARCH_CALCULATOR, SEARCH_UNITS,
        )

        const val BADGE_COUNT: String = "count"
        const val BADGE_DOT: String = "dot"
        const val BADGE_OFF: String = "off"

        const val AI_MODE_TEMPLATE: String = "template"
        const val AI_MODE_ONDEVICE: String = "ondevice"
        const val AI_MODE_OFF: String = "off"

        const val FOLDER_GRID: String = "grid"
        const val FOLDER_LIST: String = "list"
        const val FOLDER_BIG: String = "big"

        val FOLDER_STYLES: List<String> = listOf(FOLDER_GRID, FOLDER_LIST, FOLDER_BIG)
    }
}

/**
 * A saved launcher state (Settings > Profiles): Work, Home, Travel, Night, Kids... [settingsJson]
 * is a [LauncherSettings] encoded the way `SettingsRepositoryImpl` stores it (`encodeDefaults`,
 * `ignoreUnknownKeys`); [hiddenApps] is applied alongside it. The optional schedule switches the
 * profile on automatically between [startMinute] and [endMinute] (minutes after midnight, local
 * time) on [days] (`java.util.Calendar.SUNDAY`..`SATURDAY`, i.e. 1..7; empty = every day), or
 * whenever Do Not Disturb is on when [onDnd] is true.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val settingsJson: String,
    val hiddenApps: Set<String>,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val days: Set<Int> = emptySet(),
    val onDnd: Boolean = false,
) {
    /** True when the profile has a time window to switch on automatically. */
    val scheduled: Boolean get() = startMinute != null && endMinute != null
}
