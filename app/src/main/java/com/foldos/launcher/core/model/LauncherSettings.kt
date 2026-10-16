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
) {
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
    }
}
