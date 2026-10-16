package com.foldos.launcher.ai

import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.ColorSchemes
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.GestureAction
import com.foldos.launcher.core.model.GestureKind
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.ThemeId

/**
 * A natural-language launcher command that [SettingsCommandParser] understood.
 *
 * @property description what changes, for the AI usage log ("inner screen -> dashboard").
 * @property confirmation the sentence shown to the user once [apply] has been written.
 * @property dataUsed what the parser looked at besides the request ("installed apps", "settings").
 */
data class SettingsCommand(
    val description: String,
    val confirmation: String,
    val dataUsed: String = "settings",
    val apply: (LauncherSettings) -> LauncherSettings,
)

/**
 * Regex parser for the launcher commands the AI tab accepts without a model:
 *
 * - "make my inner screen a dashboard" (also grid / list / panels; cover / inner / both)
 * - "switch to dark mode", "light mode", "auto mode"
 * - "use the ocean colors" (ocean, forest, sunset, rose, gold, mono, theme colors)
 * - "set the bold theme" (Material You, Dark Glass, One UI, Bold)
 * - "hide Reddit" / "unhide Reddit"
 * - "open Spotify when I unfold" (also fold; "do nothing when I unfold")
 * - "turn off badges / gestures / search / app pairs / stacks / smart widgets / drawer tabs / profiles / AI / dock"
 * - "move the hub to the right", "hide the hub", "tab bar on top", "hide labels", "use dots for badges",
 *   "use a full-screen drawer", "make folders a list", "search with DuckDuckGo", "set the cover screen to 5 columns",
 *   "make icons bigger"
 *
 * Returns null for anything else so the router can hand the text to the [com.foldos.launcher.core.repo.AiProvider].
 */
class SettingsCommandParser {

    fun parse(text: String, apps: List<AppInfo>, current: LauncherSettings): SettingsCommand? {
        val t = text.trim().trimEnd('.', '!').replace(Regex("\\s+"), " ").lowercase()
        if (t.isEmpty()) return null
        return parseLabels(t)
            ?: parseHub(t)
            ?: parseFeature(t)
            ?: parseBadgeStyle(t)
            ?: parseDrawer(t)
            ?: parseFolders(t)
            ?: parseSearchEngine(t)
            ?: parseColumns(t)
            ?: parseIconSize(t, current)
            ?: parseHomeStyle(t)
            ?: parseTheme(t)
            ?: parseColors(t)
            ?: parseAppearance(t)
            ?: parseFoldGesture(t, apps)
            ?: parseHide(t, apps)
            ?: parseUnhide(t, apps)
    }

    /* ---- home style ---- */

    private fun parseHomeStyle(t: String): SettingsCommand? {
        val m = HOME_STYLE.find(t) ?: return null
        val screens = screensOf(m.groupValues[1]) ?: return null
        val style = styleOf(m.groupValues[2]) ?: return null
        val styleName = when (style) {
            HomeStyle.GRID -> "a classic grid"
            HomeStyle.DASHBOARD -> "a widget dashboard"
            HomeStyle.LIST -> "a minimal list"
            HomeStyle.HYBRID -> "hybrid panels"
        }
        val target = when {
            screens.size == 2 -> "both screens are"
            screens.first() == ScreenKind.COVER -> "your cover screen is"
            else -> "your inner screen is"
        }
        val extra = if (SCHEDULE_TAIL.containsMatchIn(t)) " Time-based rules aren't available yet, so the change applies all day." else ""
        return SettingsCommand(
            description = "${screens.joinToString("+") { it.name.lowercase() }} screen -> ${style.name.lowercase()}",
            confirmation = "Done. Now $target $styleName.$extra",
        ) { s ->
            var next = s
            if (ScreenKind.COVER in screens) next = next.copy(styleCover = style)
            if (ScreenKind.INNER in screens) next = next.copy(styleInner = style)
            next
        }
    }

    private fun screensOf(word: String): List<ScreenKind>? = when (word.trim()) {
        "cover", "front", "outer", "outside", "small", "folded", "closed" -> listOf(ScreenKind.COVER)
        "inner", "inside", "main", "big", "large", "unfolded", "tablet", "open" -> listOf(ScreenKind.INNER)
        "both", "all" -> listOf(ScreenKind.COVER, ScreenKind.INNER)
        else -> null
    }

    private fun styleOf(word: String): HomeStyle? = when (word.trim()) {
        "dashboard", "dashboards", "widgets" -> HomeStyle.DASHBOARD
        "grid", "icons", "icon grid" -> HomeStyle.GRID
        "list", "minimal" -> HomeStyle.LIST
        "panel", "panels", "hybrid", "split" -> HomeStyle.HYBRID
        else -> null
    }

    /* ---- appearance / theme / colors ---- */

    private fun parseAppearance(t: String): SettingsCommand? {
        val modeOnOff = APPEARANCE_ON_OFF.find(t)
        val turn = if (modeOnOff == null) APPEARANCE_TURN.find(t) else null
        val parsed: Appearance? = when {
            modeOnOff != null -> toggled(appearanceOf(modeOnOff.groupValues[1]), off = modeOnOff.groupValues[2] == "off")
            turn != null -> toggled(appearanceOf(turn.groupValues[2]), off = turn.groupValues[1] == "off")
            else -> APPEARANCE_WORD.find(t)?.let { appearanceOf(it.groupValues[1]) }
                ?: APPEARANCE_SWITCH.find(t)?.let { appearanceOf(it.groupValues[1]) }
        }
        val appearance: Appearance = parsed ?: return null
        val label = when (appearance) {
            Appearance.DARK -> "dark mode"
            Appearance.LIGHT -> "light mode"
            Appearance.AUTO -> "auto (follows the system, or sunrise and sunset)"
        }
        return SettingsCommand("appearance -> ${appearance.name.lowercase()}", "Switched to $label.") { it.copy(appearance = appearance) }
    }

    /** "dark mode off" means light, "light mode off" means dark. */
    private fun toggled(mode: Appearance?, off: Boolean): Appearance? = when (mode) {
        Appearance.DARK -> if (off) Appearance.LIGHT else Appearance.DARK
        Appearance.LIGHT -> if (off) Appearance.DARK else Appearance.LIGHT
        Appearance.AUTO -> if (off) Appearance.DARK else Appearance.AUTO
        null -> null
    }

    private fun appearanceOf(word: String): Appearance? = when (word.trim()) {
        "dark", "night" -> Appearance.DARK
        "light", "day", "bright" -> Appearance.LIGHT
        "auto", "automatic", "system" -> Appearance.AUTO
        else -> null
    }

    private fun parseTheme(t: String): SettingsCommand? {
        val word = THEME_SET.find(t)?.groupValues?.get(1) ?: THEME_TO.find(t)?.groupValues?.get(1) ?: return null
        val theme = themeOf(word) ?: return null
        val name = when (theme) {
            ThemeId.MATERIAL -> "Material You"
            ThemeId.GLASS -> "Dark Glass"
            ThemeId.ONEUI -> "One UI"
            ThemeId.BOLD -> "Bold"
        }
        return SettingsCommand("theme -> ${theme.name.lowercase()}", "Applied the $name theme. Dark and light versions both follow your appearance setting.") {
            it.copy(theme = theme)
        }
    }

    private fun themeOf(word: String): ThemeId? = when (word.trim().replace(Regex("\\s+"), " ")) {
        "material", "material you", "you", "dynamic" -> ThemeId.MATERIAL
        "glass", "dark glass", "frosted" -> ThemeId.GLASS
        "one ui", "oneui", "samsung", "stock", "native" -> ThemeId.ONEUI
        "bold", "futuristic", "neon" -> ThemeId.BOLD
        else -> null
    }

    private fun parseColors(t: String): SettingsCommand? {
        val word = COLORS.find(t)?.groupValues?.get(1)?.trim() ?: return null
        val scheme = when (word) {
            "theme", "default", "wallpaper", "dynamic", "material you", "system" -> ColorSchemeConfig.SCHEME_THEME
            "mono", "monochrome", "grey", "gray", "greyscale", "grayscale" -> "mono"
            else -> ColorSchemes.byId(word)?.id
        } ?: return null
        val name = if (scheme == ColorSchemeConfig.SCHEME_THEME) "theme colors (Material You from the wallpaper)" else "${ColorSchemes.byId(scheme)?.name ?: scheme} colors"
        return SettingsCommand("colors -> $scheme", "Using the $name. Accent, highlights and the wallpaper gradient follow.") {
            it.copy(colors = it.colors.copy(scheme = scheme))
        }
    }

    /* ---- hidden apps ---- */

    private fun parseHide(t: String, apps: List<AppInfo>): SettingsCommand? {
        val m = HIDE.find(t) ?: return null
        val phrase = m.groupValues[1].replace(SCHEDULE_TAIL, "").trim()
        if (phrase.isEmpty() || phrase in RESERVED_WORDS) return null
        val app = matchApp(phrase, apps) ?: return SettingsCommand(
            description = "hide app (no match for \"$phrase\")",
            confirmation = "I couldn't find an app called “$phrase”. Say the name as it appears in the drawer.",
            dataUsed = "installed apps",
        ) { it }
        val schedule = if (SCHEDULE_TAIL.containsMatchIn(t)) " Scheduled hiding isn't available yet, so it's hidden from now on; say “unhide ${app.label}” to bring it back." else ""
        return SettingsCommand("hide ${app.packageName}", "Hidden ${app.label} from the drawer, search and home.$schedule", "installed apps") {
            it.copy(hiddenApps = it.hiddenApps + app.packageName)
        }
    }

    private fun parseUnhide(t: String, apps: List<AppInfo>): SettingsCommand? {
        val m = UNHIDE.find(t) ?: return null
        val phrase = m.groupValues[1].trim()
        if (phrase.isEmpty() || phrase in RESERVED_WORDS) return null
        val app = matchApp(phrase, apps) ?: return null
        return SettingsCommand("unhide ${app.packageName}", "${app.label} is visible again.", "installed apps") { s ->
            s.copy(hiddenApps = s.hiddenApps - app.packageName - app.key)
        }
    }

    /* ---- fold / unfold gesture ---- */

    private fun parseFoldGesture(t: String, apps: List<AppInfo>): SettingsCommand? {
        val nothing = FOLD_NOTHING.find(t)
        if (nothing != null) {
            val word = nothing.groupValues[1].ifEmpty { nothing.groupValues[2] }
            val kind = if (word.startsWith("un") || word == "open") GestureKind.UNFOLD else GestureKind.FOLD
            return SettingsCommand("${kind.name.lowercase()} -> none", "Okay, nothing happens when you ${kind.name.lowercase()} anymore.") {
                it.withGesture(kind, GestureAction.NONE)
            }
        }
        val a = FOLD_OPEN_A.find(t)
        val b = if (a == null) FOLD_OPEN_B.find(t) else null
        val (appPhrase, verb) = when {
            a != null -> a.groupValues[1] to a.groupValues[2] // "open X when I unfold"
            b != null -> b.groupValues[2] to b.groupValues[1] // "when I unfold, open X"
            else -> return null
        }
        val kind = if (verb.startsWith("un") || verb == "open") GestureKind.UNFOLD else GestureKind.FOLD
        val trimmed = appPhrase.trim().removePrefix("the ").removeSuffix(" app").trim()
        val action = when (trimmed) {
            "the drawer", "drawer", "all apps", "app drawer" -> GestureAction(GestureAction.TYPE_DRAWER) to "the app drawer"
            "search", "the search", "universal search" -> GestureAction(GestureAction.TYPE_SEARCH) to "search"
            "the hub", "hub", "today" -> GestureAction(GestureAction.TYPE_HUB) to "the hub"
            "settings", "the settings" -> GestureAction(GestureAction.TYPE_SETTINGS) to "Settings"
            "notifications", "the notifications", "the shade" -> GestureAction(GestureAction.TYPE_NOTIFICATIONS) to "the notification shade"
            else -> {
                val app = matchApp(trimmed, apps) ?: return SettingsCommand(
                    description = "${kind.name.lowercase()} -> app (no match for \"$trimmed\")",
                    confirmation = "I couldn't find an app called “$trimmed” to open when you ${kind.name.lowercase()}.",
                    dataUsed = "installed apps",
                ) { it }
                GestureAction.app(app.key) to app.label
            }
        }
        return SettingsCommand(
            description = "${kind.name.lowercase()} -> ${action.first.type}${action.first.target?.let { ":$it" } ?: ""}",
            confirmation = "Done. ${action.second.replaceFirstChar { c -> c.titlecase() }} opens when you ${kind.name.lowercase()}.",
            dataUsed = "installed apps",
        ) { it.withGesture(kind, action.first) }
    }

    /* ---- features, hub, labels, badge style, drawer, folders, search, columns, icons ---- */

    private fun parseFeature(t: String): SettingsCommand? {
        val a = FEATURE_VERB.find(t)
        val b = if (a == null) FEATURE_SUFFIX.find(t) else null
        val (featureWord, on) = when {
            a != null -> a.groupValues[2] to (a.groupValues[1] in ON_WORDS)
            b != null -> b.groupValues[1] to (b.groupValues[2] == "on")
            else -> return null
        }
        val (key, name) = featureOf(featureWord) ?: return null
        if (key == HUB_PSEUDO_FEATURE) {
            return SettingsCommand("hub -> ${if (on) "on" else "off"}", if (on) "The hub page is back beside home." else "The hub page is off; swipe past home no longer shows it.") {
                it.copy(hubOn = on)
            }
        }
        return SettingsCommand("$key -> ${if (on) "on" else "off"}", "${name.replaceFirstChar { c -> c.titlecase() }} ${if (on) "is on" else "is off"}.") {
            it.withFeature(key, on)
        }
    }

    private fun featureOf(word: String): Pair<String, String>? = when (word.trim()) {
        "badge", "badges", "notification badges", "notification badge" -> LauncherSettings.FEATURE_BADGES to "notification badges"
        "gesture", "gestures" -> LauncherSettings.FEATURE_GESTURES to "gestures"
        "search", "universal search" -> LauncherSettings.FEATURE_SEARCH to "universal search"
        "pair", "pairs", "app pair", "app pairs" -> LauncherSettings.FEATURE_PAIRS to "app pairs"
        "stack", "stacks", "widget stack", "widget stacks" -> LauncherSettings.FEATURE_STACKS to "widget stacks"
        "smart widgets", "smart widget", "smart rules", "smart" -> LauncherSettings.FEATURE_SMART to "smart widget rules"
        "drawer tabs", "drawer tab", "tabs" -> LauncherSettings.FEATURE_DRAWER_TABS to "drawer tabs"
        "profile", "profiles" -> LauncherSettings.FEATURE_PROFILES to "profiles"
        "ai", "assistant", "the assistant" -> LauncherSettings.FEATURE_AI to "AI features"
        "dock", "the dock" -> LauncherSettings.FEATURE_DOCK to "the dock"
        "hub", "the hub", "hub page" -> HUB_PSEUDO_FEATURE to "the hub"
        else -> null
    }

    private fun parseHub(t: String): SettingsCommand? {
        HUB_MOVE.find(t)?.let { m ->
            val pos = if (m.groupValues[1] == "left") LauncherSettings.HUB_POS_LEFT else LauncherSettings.HUB_POS_RIGHT
            return SettingsCommand("hub position -> $pos", "The hub now sits to the $pos of your home pages.") { it.copy(hubOn = true, hubPos = pos) }
        }
        HUB_BAR.find(t)?.let { m ->
            val bar = if (m.groupValues[1] == "top") LauncherSettings.HUB_BAR_TOP else LauncherSettings.HUB_BAR_BOTTOM
            return SettingsCommand("hub tab bar -> $bar", "The hub's tab bar is at the $bar.") { it.copy(hubBar = bar) }
        }
        HUB_HIDE.find(t)?.let {
            return SettingsCommand("hub -> off", "The hub page is off; swiping past home no longer shows it.") { s -> s.copy(hubOn = false) }
        }
        HUB_SHOW.find(t)?.let {
            return SettingsCommand("hub -> on", "The hub page is back beside home.") { s -> s.copy(hubOn = true) }
        }
        return null
    }

    private fun parseLabels(t: String): SettingsCommand? {
        val m = LABELS.find(t) ?: return null
        val on = m.groupValues[1] in ON_WORDS || m.groupValues[1] == "show"
        return SettingsCommand("labels -> ${if (on) "on" else "off"}", if (on) "App labels are back under the icons." else "Hidden the app labels; icons only now.") {
            it.copy(labels = on)
        }
    }

    private fun parseBadgeStyle(t: String): SettingsCommand? {
        val style = when {
            BADGE_DOTS.containsMatchIn(t) -> LauncherSettings.BADGE_DOT
            BADGE_COUNTS.containsMatchIn(t) -> LauncherSettings.BADGE_COUNT
            BADGE_NONE.containsMatchIn(t) -> LauncherSettings.BADGE_OFF
            else -> return null
        }
        val text = when (style) {
            LauncherSettings.BADGE_DOT -> "Badges are dots now."
            LauncherSettings.BADGE_COUNT -> "Badges show unread counts now."
            else -> "Badges are off."
        }
        return SettingsCommand("badge style -> $style", text) { it.copy(badgeStyle = style) }
    }

    private fun parseDrawer(t: String): SettingsCommand? {
        val word = DRAWER_A.find(t)?.groupValues?.get(1) ?: DRAWER_B.find(t)?.groupValues?.get(1) ?: return null
        val style = if (word.startsWith("pop") || word == "sheet") DrawerStyle.POPUP else DrawerStyle.FULL
        return SettingsCommand("drawer -> ${style.name.lowercase()}", if (style == DrawerStyle.POPUP) "The app drawer is a pop-up sheet again." else "The app drawer is full screen now.") {
            it.copy(drawerStyle = style)
        }
    }

    private fun parseFolders(t: String): SettingsCommand? {
        val word = FOLDERS_A.find(t)?.groupValues?.get(1) ?: FOLDERS_B.find(t)?.groupValues?.get(1) ?: return null
        val style = when (word) {
            "grid" -> LauncherSettings.FOLDER_GRID
            "list" -> LauncherSettings.FOLDER_LIST
            else -> LauncherSettings.FOLDER_BIG
        }
        return SettingsCommand("folder style -> $style", "Folders open as a $style now (per-folder overrides stay).") { it.copy(folderStyle = style) }
    }

    private fun parseSearchEngine(t: String): SettingsCommand? {
        val word = SEARCH_ENGINE.find(t)?.groupValues?.get(1)?.replace(" ", "") ?: return null
        val engine = when (word) {
            "google" -> LauncherSettings.SEARCH_ENGINE_GOOGLE
            "bing" -> LauncherSettings.SEARCH_ENGINE_BING
            "duckduckgo", "ddg" -> LauncherSettings.SEARCH_ENGINE_DUCKDUCKGO
            "kagi" -> LauncherSettings.SEARCH_ENGINE_KAGI
            "brave" -> LauncherSettings.SEARCH_ENGINE_BRAVE
            "ecosia" -> LauncherSettings.SEARCH_ENGINE_ECOSIA
            else -> return null
        }
        val pretty = when (engine) {
            LauncherSettings.SEARCH_ENGINE_DUCKDUCKGO -> "DuckDuckGo"
            else -> engine.replaceFirstChar { c -> c.titlecase() }
        }
        return SettingsCommand("search engine -> $engine", "Web searches go to $pretty now.") {
            it.copy(searchEngine = engine).withSearchProvider(LauncherSettings.SEARCH_WEB, true)
        }
    }

    private fun parseColumns(t: String): SettingsCommand? {
        // "5 columns on the cover" names the screen after the number; try it before the generic form.
        val b = COLUMNS_B.find(t)
        val a = if (b == null) COLUMNS_A.find(t) else null
        val (screenWord, number) = when {
            b != null -> b.groupValues[2] to b.groupValues[1]
            a != null -> a.groupValues[1] to a.groupValues[2]
            else -> return null
        }
        val columns = number.toIntOrNull() ?: return null
        val screens = if (screenWord.isBlank()) listOf(ScreenKind.COVER, ScreenKind.INNER) else screensOf(screenWord) ?: return null
        return SettingsCommand(
            description = "${screens.joinToString("+") { it.name.lowercase() }} columns -> $columns",
            confirmation = "Set ${if (screens.size == 2) "both screens" else "the ${screens.first().name.lowercase()} screen"} to ${aiPlural(columns, "column")}.",
        ) { s ->
            var next = s
            if (ScreenKind.COVER in screens) next = next.copy(colsCover = columns.coerceIn(3, 6))
            if (ScreenKind.INNER in screens) next = next.copy(colsInner = columns.coerceIn(4, 10))
            next
        }
    }

    private fun parseIconSize(t: String, current: LauncherSettings): SettingsCommand? {
        val m = ICON_SIZE.find(t) ?: return null
        val bigger = m.groupValues[1] in setOf("bigger", "larger", "big", "huge")
        val next = (current.iconSizeDp + if (bigger) 6 else -6).coerceIn(40, 80)
        if (next == current.iconSizeDp) return SettingsCommand("icon size unchanged", "Icons are already at the ${if (bigger) "largest" else "smallest"} size.") { it }
        return SettingsCommand("icon size -> ${next}dp", "Icons are ${if (bigger) "bigger" else "smaller"} now (${next}dp).") { it.copy(iconSizeDp = next) }
    }

    companion object {
        private const val HUB_PSEUDO_FEATURE = "__hub__"
        private val ON_WORDS = setOf("turn on", "enable", "switch on", "show", "on")
        private val RESERVED_WORDS = setOf("it", "this", "that", "everything", "all", "me", "them", "the hub", "hub", "labels", "the labels")

        /** "after 10 pm", "between 9 and 5", "at night" ... a schedule we cannot honour yet. */
        private val SCHEDULE_TAIL = Regex("""\s+(?:after|before|at|until|till|from|between|during|on|every)\s+[\w: ]*(?:\d|night|morning|evening|weekdays?|weekends?|work)[\w: ]*$""")

        private val HOME_STYLE = Regex(
            """\b(?:make|set|turn|change|switch|use|give)\s+(?:my\s+|the\s+)?(cover|front|outer|outside|small|folded|closed|inner|inside|main|big|large|unfolded|tablet|open|both|all)\s*(?:screens?|displays?|homes?|home\s+screens?)?\s*(?:into|to|as|a|an)?\s*(?:a|an|the)?\s*(?:widget\s+|classic\s+|icon\s+|minimal\s+|hybrid\s+)?(dashboards?|widgets|grid|icons|icon\s+grid|list|minimal|panels?|hybrid|split)\b""",
        )
        private val APPEARANCE_ON_OFF = Regex("""\b(dark|light|night|day|auto|automatic)\s+(?:mode|theme|appearance)\s+(on|off)\b""")
        private val APPEARANCE_TURN = Regex("""\b(?:turn|switch)\s+(on|off)\s+(?:the\s+)?(dark|light|night|day|auto|automatic)\s+(?:mode|theme|appearance)\b""")
        private val APPEARANCE_WORD = Regex("""\b(dark|light|night|day|auto|automatic|system)\s+(?:mode|appearance)\b""")
        private val APPEARANCE_SWITCH = Regex("""\b(?:switch|go|change|set)\s+(?:it\s+)?(?:to\s+|into\s+)?(?:the\s+)?(dark|light|bright|auto|automatic)\s*(?:theme)?$""")
        private val THEME_SET = Regex("""\b(?:set|use|apply|switch\s+to|change\s+to|try|give\s+me|pick|choose)\s+(?:the\s+)?(material(?:\s+you)?|dark\s+glass|glass|frosted|one\s*ui|samsung|stock|native|bold|futuristic|neon)\s+(?:theme|look|style)\b""")
        private val THEME_TO = Regex("""\b(?:set|change|switch)\s+(?:the\s+)?(?:theme|look)\s+to\s+(?:the\s+)?(material(?:\s+you)?|dark\s+glass|glass|frosted|one\s*ui|samsung|stock|native|bold|futuristic|neon)\b""")
        private val COLORS = Regex("""\b(?:use|apply|set|switch\s+to|change\s+to|give\s+me|try|pick|choose)\s+(?:the\s+)?(ocean|forest|sunset|rose|gold|mono|monochrome|grey|gray|greyscale|grayscale|theme|default|wallpaper|dynamic|material\s+you|system)\s+(?:colou?rs?|colou?r\s+scheme|palette|scheme)\b""")
        private val HIDE = Regex("""^(?:please\s+)?(?:hide|conceal)\s+(?:the\s+)?(?:app\s+)?(.+?)(?:\s+app)?(?:\s+from\s+(?:the\s+)?(?:drawer|home|launcher|search|everywhere))?$""")
        private val UNHIDE = Regex("""^(?:please\s+)?(?:unhide|un-hide|reveal|restore|show)\s+(?:the\s+)?(?:app\s+)?(.+?)(?:\s+app)?(?:\s+again)?$""")
        private val FOLD_NOTHING = Regex("""\b(?:do\s+nothing|nothing)\s+when\s+(?:i\s+)?(unfold|fold|open|close)\b|\bwhen\s+(?:i\s+)?(unfold|fold|open|close)[^a-z]*(?:do\s+)?nothing\b""")
        private val FOLD_OPEN_A = Regex("""\b(?:open|launch|start)\s+(.+?)\s+when\s+(?:i\s+)?(unfold|fold|open|close)(?:\s+(?:it|the\s+phone|my\s+phone|the\s+device))?\b""")
        private val FOLD_OPEN_B = Regex("""\bwhen\s+(?:i\s+)?(unfold|fold|open|close)(?:\s+(?:it|the\s+phone|my\s+phone|the\s+device))?,?\s+(?:open|launch|start)\s+(.+?)$""")
        private val FEATURE_VERB = Regex("""\b(turn\s+on|turn\s+off|enable|disable|switch\s+on|switch\s+off)\s+(?:the\s+|my\s+)?(notification\s+badges?|badges?|gestures?|universal\s+search|search|app\s+pairs?|pairs?|widget\s+stacks?|stacks?|smart\s+(?:widgets?|rules)|drawer\s+tabs?|profiles?|ai|assistant|dock|hub(?:\s+page)?)\b""")
        private val FEATURE_SUFFIX = Regex("""\b(notification\s+badges?|badges?|gestures?|universal\s+search|app\s+pairs?|widget\s+stacks?|stacks?|smart\s+(?:widgets?|rules)|drawer\s+tabs?|profiles?|dock|hub(?:\s+page)?)\s+(on|off)\b""")
        private val HUB_MOVE = Regex("""\b(?:move|put|place|show)\s+(?:the\s+)?hub\s+(?:page\s+)?(?:to\s+|on\s+)?(?:the\s+)?(left|right)\b""")
        private val HUB_BAR = Regex("""\b(?:hub\s+)?tab\s*bar\s+(?:at\s+|on\s+|to\s+)?(?:the\s+)?(top|bottom)\b""")
        private val HUB_HIDE = Regex("""\b(?:hide|disable|remove|turn\s+off|switch\s+off)\s+(?:the\s+)?hub(?:\s+page)?\b""")
        private val HUB_SHOW = Regex("""\b(?:show|enable|turn\s+on|switch\s+on|bring\s+back|restore)\s+(?:the\s+)?hub(?:\s+page)?\b""")
        private val LABELS = Regex("""\b(hide|show|turn\s+off|turn\s+on|remove|disable|enable)\s+(?:the\s+|app\s+|icon\s+|my\s+)*labels\b""")
        private val BADGE_DOTS = Regex("""\b(?:use|show|set|switch\s+to|make\s+(?:the\s+)?badges)\s+(?:the\s+|badge\s+|notification\s+)*dots?\b""")
        private val BADGE_COUNTS = Regex("""\b(?:use|show|set|switch\s+to|make\s+(?:the\s+)?badges)\s+(?:the\s+|badge\s+|notification\s+|unread\s+)*(?:counts?|numbers?)\b""")
        private val BADGE_NONE = Regex("""\bno\s+(?:notification\s+)?badges\b""")
        private val DRAWER_A = Regex("""\b(?:use|set|make|switch\s+to)\s+(?:the\s+|a\s+)?(?:app\s+)?drawer\s+(?:a\s+|to\s+|as\s+|the\s+)?(pop-?\s?up|sheet|full[-\s]?screen|fullscreen|full)\b""")
        private val DRAWER_B = Regex("""\b(pop-?\s?up|full[-\s]?screen|fullscreen)\s+(?:app\s+)?drawer\b""")
        private val FOLDERS_A = Regex("""\b(?:use|set|make|switch\s+to|open)\s+(?:my\s+|the\s+)?folders?\s+(?:style\s+)?(?:to\s+|as\s+|a\s+|in\s+a\s+)?(grid|list|big|large|expandable)\b""")
        private val FOLDERS_B = Regex("""\b(grid|list|big|large|expandable)\s+folders?\b""")
        private val SEARCH_ENGINE = Regex("""\b(?:search|searches|use|switch\s+to)\s+(?:the\s+web\s+)?(?:with\s+|using\s+|on\s+|via\s+)?(google|bing|duck\s*duck\s*go|ddg|kagi|brave|ecosia)(?:\s+(?:for\s+)?(?:web\s+)?search(?:es)?)?\b""")
        private val COLUMNS_A = Regex("""\b(?:set|make|use|give)\s+(?:the\s+|my\s+)?(cover|front|inner|inside|main|big|both)?\s*(?:screens?\s+)?(?:grid\s+)?(?:to\s+|with\s+)?(\d{1,2})\s+(?:icon\s+)?columns?\b""")
        private val COLUMNS_B = Regex("""\b(\d{1,2})\s+columns?\s+(?:on\s+|for\s+)?(?:the\s+|my\s+)?(cover|front|inner|inside|main|big|both)\b""")
        private val ICON_SIZE = Regex("""\b(?:make\s+)?(?:the\s+|my\s+)?icons\s+(?:a\s+bit\s+|much\s+)?(bigger|larger|big|huge|smaller|tinier|small)\b""")

        /**
         * The installed app best matching a spoken name: exact label, label prefix, the phrase
         * starting with the label, label containing the phrase, then the package name.
         */
        fun matchApp(phrase: String, apps: List<AppInfo>): AppInfo? {
            val q = phrase.trim().trim('"', '“', '”', '\'', '.', ',').lowercase()
            if (q.isEmpty()) return null
            val compact = q.replace(" ", "")
            return apps.firstOrNull { it.label.lowercase() == q }
                ?: apps.filter { it.label.lowercase().startsWith(q) }.minByOrNull { it.label.length }
                ?: apps.filter { it.label.length >= 3 && q.startsWith(it.label.lowercase()) }.maxByOrNull { it.label.length }
                ?: apps.filter { it.label.lowercase().contains(q) }.minByOrNull { it.label.length }
                ?: apps.filter { compact.length >= 3 && it.packageName.lowercase().contains(compact) }.minByOrNull { it.packageName.length }
        }
    }
}
