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
    /** Starter layout last applied to the cover screen (seeds the first page; no rendering effect since 1.3.0). */
    val styleCover: HomeStyle = HomeStyle.GRID,
    /** Starter layout last applied to the inner screen. */
    val styleInner: HomeStyle = HomeStyle.DASHBOARD,
    val theme: ThemeId = ThemeId.GLASS,
    val gridMode: GridMode = GridMode.GRID,
    val colsCover: Int = 4,
    val colsInner: Int = 6,
    val iconSizeDp: Int = 58,
    val radiusDp: Int = 22,
    val labels: Boolean = true,
    /** App label size under icons: "small" (11sp), "medium" (12sp) or "large" (14sp); see home/AppLabelStyle.kt. */
    val labelSize: String = "medium",
    /** Cover dock; default is the "Floating pill" preset. */
    val dockCover: DockConfig = DockConfig(),
    /** Inner dock; default is the "Taskbar" preset. */
    val dockInner: DockConfig = DockConfig(taskbar = true),
    val drawerStyle: DrawerStyle = DrawerStyle.POPUP,
    val popBlur: Boolean = true,
    /** App pop-up / drawer panel color: [DrawerTint.THEME] (tinted with the color scheme, default since 1.5.1) or [DrawerTint.NEUTRAL]. */
    val drawerTint: DrawerTint = DrawerTint.THEME,
    val hubOn: Boolean = true,
    /** "left" or "right". */
    val hubPos: String = HUB_POS_LEFT,
    /** "bottom" or "top". */
    val hubBar: String = HUB_BAR_BOTTOM,
    /** Enabled hub tabs besides Today: subset of [HUB_TAB_AI], [HUB_TAB_MAIL], [HUB_TAB_CAL], [HUB_TAB_PEOPLE], [HUB_TAB_LAUNCH] (off by default). */
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
    /** Deprecated and unused since 1.2.4 (Mail signs in through the Crosspoint server); kept so old settings backups still load. */
    val mailClientId: String? = null,
    /**
     * Crosspoint server address the Mail tab signs in to and reads the inbox from (the
     * "Server address" in Settings > Mail); null = [DEFAULT_MAIL_SETUP_URL].
     */
    val mailSetupUrl: String? = null,
    /** Load https images in HTML emails automatically (default on since 1.5.2); off = banner and per-message Load images. */
    val mailLoadImages: Boolean = true,
    /** AI engine: [AI_MODE_TEMPLATE] (rule-based replies), [AI_MODE_ONDEVICE] (Gemini Nano) or [AI_MODE_OFF]. */
    val aiMode: String = AI_MODE_TEMPLATE,
    /** Hub > AI tab quick actions (ids of [AI_QUICK_ACTION_IDS]), in display order; default all 12, old backups load as all. */
    val aiQuickActions: List<String> = AI_QUICK_ACTION_IDS,
    /** 1.6.1: true once a saved list equal to the old 12-id default has been replaced by the 18-id default. */
    val aiQuickActionsMigrated: Boolean = false,
    /** Default folder style: [FOLDER_GRID], [FOLDER_LIST] or [FOLDER_BIG]; a `HomeItem.folderStyle` overrides it. */
    val folderStyle: String = FOLDER_GRID,
    /** Show notification previews on long-press of a badged app. */
    val notificationPreview: Boolean = true,
    /** Accessibility: higher-contrast surfaces / text and stronger outlines. */
    val highContrast: Boolean = false,
    /**
     * Look > Space black: solid #000000 dock, cards, sheets and widget tiles with pure white text, whatever the
     * Appearance and color scheme (the accent stays, lightened if needed). Off by default; see theme/SpaceBlack.kt.
     */
    val spaceBlack: Boolean = false,
    /** Accessibility: no animated transitions (pager, drawer, widget flips, parallax). */
    val reduceMotion: Boolean = false,
    /** Accessibility: multiplier applied on top of the system font scale to launcher text. */
    val textScale: Float = 1.0f,
    /** Easy / large mode: larger icons, labels always on, simpler layout. */
    val largeIcons: Boolean = false,
    /** Render the launcher UI and icons in greyscale (digital wellbeing). */
    val greyscale: Boolean = false,
    /** Hard switch: Gmail API, web search previews and AI network use are all off. */
    val noNetwork: Boolean = false,
    /** Set a new wallpaper from the daily source once a day. */
    val dailyWallpaper: Boolean = false,
    /** Back up settings and layouts automatically (Android Auto Backup / scheduled export). */
    val backupAuto: Boolean = true,
    /** Cards (Round 2.0): draw the rounded theme surface behind a card (a card's own `cardClear` wins). */
    val cardBackground: Boolean = true,
    /** Cards: show the card title row (cards without a title never show one). */
    val cardTitles: Boolean = true,
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

    /**
     * Icon size actually drawn on home, dock and drawer: [iconSizeDp], plus 20% in Easy mode
     * ([largeIcons]). Computed, so it is not part of the stored JSON.
     */
    val effectiveIconSizeDp: Int
        get() = if (largeIcons) (iconSizeDp * 12 + 5) / 10 else iconSizeDp

    /** App labels are always shown in Easy mode ([largeIcons]); otherwise [labels]. */
    val effectiveLabels: Boolean
        get() = labels || largeIcons

    /**
     * Grid columns actually drawn on [screen]: [colsFor], except Easy mode ([largeIcons]) which
     * uses at most [EASY_COVER_COLS] on the cover and two fewer (minimum 3) on the inner screen.
     */
    fun effectiveColsFor(screen: ScreenKind): Int {
        val base = colsFor(screen)
        if (!largeIcons) return base
        return when (screen) {
            ScreenKind.COVER -> minOf(base, EASY_COVER_COLS)
            ScreenKind.INNER, ScreenKind.FLEX -> (base - 2).coerceAtLeast(EASY_MIN_COLS).coerceAtMost(base)
        }
    }

    companion object {
        /** Where the Mail tab looks for the organization's shared Google sign-in id (Admin > Settings > FoldOS Mail). */
        const val DEFAULT_MAIL_SETUP_URL: String = "https://sub.crosspointhealth.org"

        /** Easy mode: columns on the cover screen. */
        const val EASY_COVER_COLS: Int = 3

        /** Easy mode: lower bound for the inner-screen columns. */
        const val EASY_MIN_COLS: Int = 3

        const val HUB_POS_LEFT: String = "left"
        const val HUB_POS_RIGHT: String = "right"
        const val HUB_BAR_BOTTOM: String = "bottom"
        const val HUB_BAR_TOP: String = "top"

        const val HUB_TAB_AI: String = "ai"
        const val HUB_TAB_MAIL: String = "mail"
        const val HUB_TAB_CAL: String = "cal"
        const val HUB_TAB_PEOPLE: String = "people"
        const val HUB_TAB_LAUNCH: String = "launch"

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

        val ALL_FEATURES: List<String> = listOf(
            FEATURE_SEARCH, FEATURE_DOCK, FEATURE_BADGES, FEATURE_GESTURES, FEATURE_PAIRS,
            FEATURE_AI, FEATURE_STACKS, FEATURE_SMART, FEATURE_DRAWER_TABS,
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

        /** Every AI-tab quick action id, in default order (labels and behavior live in `ai/AiQuickActions.kt`). */
        val AI_QUICK_ACTION_IDS: List<String> = listOf(
            "summarize_mail", "plan_day", "draft_reply", "read_mail", "whats_next", "call_back",
            "find_contact", "add_event", "add_task", "set_reminder", "focus_25", "directions",
            "compose_email", "whats_tomorrow", "message_someone", "timer", "set_alarm", "open_gmail",
        )

        /** The twelve ids that were the default up to 1.6.0 (used once to migrate saved lists to the 18-id default). */
        val AI_QUICK_ACTION_IDS_V160: List<String> = AI_QUICK_ACTION_IDS.take(12)

        const val AI_MODE_TEMPLATE: String = "template"
        const val AI_MODE_ONDEVICE: String = "ondevice"
        const val AI_MODE_OFF: String = "off"

        const val FOLDER_GRID: String = "grid"
        const val FOLDER_LIST: String = "list"
        const val FOLDER_BIG: String = "big"

        val FOLDER_STYLES: List<String> = listOf(FOLDER_GRID, FOLDER_LIST, FOLDER_BIG)
    }
}
