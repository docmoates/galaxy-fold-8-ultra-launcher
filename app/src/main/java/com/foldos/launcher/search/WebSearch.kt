package com.foldos.launcher.search

import android.net.Uri
import com.foldos.launcher.core.model.LauncherSettings

/**
 * Web search engines (`LauncherSettings.searchEngine`, one of `SEARCH_ENGINES`) and !bangs
 * (PLAN.md 4.1 "Custom engines and !bangs"): `!g` Google, `!ddg` DuckDuckGo, `!yt` YouTube,
 * `!w` Wikipedia, `!maps` Google Maps, `!amazon` Amazon, plus `!b` Bing, `!kagi`, `!brave`,
 * `!ecosia`, `!gh` GitHub, `!r` Reddit, `!play` Play Store.
 */
object WebSearch {

    /** A search destination: an engine from Settings or a !bang target. */
    data class Engine(val id: String, val name: String, val urlTemplate: String) {
        fun url(query: String): String = urlTemplate.replace("{q}", Uri.encode(query))
    }

    /** What a typed query resolves to once bangs are stripped. */
    data class Resolved(
        /** Query text without the bang. */
        val query: String,
        val engine: Engine,
        /** The bang that picked [engine], without "!", or null for the default engine. */
        val bang: String?,
    )

    val engines: List<Engine> = listOf(
        Engine(LauncherSettings.SEARCH_ENGINE_GOOGLE, "Google", "https://www.google.com/search?q={q}"),
        Engine(LauncherSettings.SEARCH_ENGINE_BING, "Bing", "https://www.bing.com/search?q={q}"),
        Engine(LauncherSettings.SEARCH_ENGINE_DUCKDUCKGO, "DuckDuckGo", "https://duckduckgo.com/?q={q}"),
        Engine(LauncherSettings.SEARCH_ENGINE_KAGI, "Kagi", "https://kagi.com/search?q={q}"),
        Engine(LauncherSettings.SEARCH_ENGINE_BRAVE, "Brave Search", "https://search.brave.com/search?q={q}"),
        Engine(LauncherSettings.SEARCH_ENGINE_ECOSIA, "Ecosia", "https://www.ecosia.org/search?q={q}"),
    )

    private val bangTargets: Map<String, Engine> = mapOf(
        "g" to engineById(LauncherSettings.SEARCH_ENGINE_GOOGLE),
        "google" to engineById(LauncherSettings.SEARCH_ENGINE_GOOGLE),
        "ddg" to engineById(LauncherSettings.SEARCH_ENGINE_DUCKDUCKGO),
        "b" to engineById(LauncherSettings.SEARCH_ENGINE_BING),
        "bing" to engineById(LauncherSettings.SEARCH_ENGINE_BING),
        "kagi" to engineById(LauncherSettings.SEARCH_ENGINE_KAGI),
        "brave" to engineById(LauncherSettings.SEARCH_ENGINE_BRAVE),
        "ecosia" to engineById(LauncherSettings.SEARCH_ENGINE_ECOSIA),
        "yt" to Engine("youtube", "YouTube", "https://www.youtube.com/results?search_query={q}"),
        "w" to Engine("wikipedia", "Wikipedia", "https://en.wikipedia.org/w/index.php?search={q}"),
        "wiki" to Engine("wikipedia", "Wikipedia", "https://en.wikipedia.org/w/index.php?search={q}"),
        "maps" to Engine("maps", "Google Maps", "https://www.google.com/maps/search/?api=1&query={q}"),
        "amazon" to Engine("amazon", "Amazon", "https://www.amazon.com/s?k={q}"),
        "a" to Engine("amazon", "Amazon", "https://www.amazon.com/s?k={q}"),
        "gh" to Engine("github", "GitHub", "https://github.com/search?q={q}"),
        "r" to Engine("reddit", "Reddit", "https://www.reddit.com/search/?q={q}"),
        "play" to Engine("play", "Play Store", "https://play.google.com/store/search?q={q}&c=apps"),
    )

    /** Every bang the sheet understands, for the Settings subtitle and the empty-state hint. */
    val bangs: List<String> = bangTargets.keys.sorted()

    private val bangPattern = Regex("(?:^|\\s)!([a-zA-Z]+)(?=\\s|$)")
    private val urlPattern = Regex(
        "^(?:https?://)?(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?::\\d+)?(?:/\\S*)?$",
    )

    fun engineById(id: String): Engine = engines.firstOrNull { it.id == id } ?: engines.first()

    fun engineName(id: String): String = engineById(id).name

    /** Options for the Settings > Search engine dropdown. */
    val engineOptions: List<Pair<String, String>> = engines.map { it.id to it.name }

    /** Resolves [text] against the default [engineId], honouring one !bang anywhere in the text. */
    fun resolve(text: String, engineId: String): Resolved {
        val trimmed = text.trim()
        val match = bangPattern.find(trimmed)
        if (match != null) {
            val bang = match.groupValues[1].lowercase()
            val target = bangTargets[bang]
            if (target != null) {
                val rest = trimmed.removeRange(match.range).trim()
                return Resolved(query = rest, engine = target, bang = bang)
            }
        }
        return Resolved(query = trimmed, engine = engineById(engineId), bang = null)
    }

    /** "https://example.com/x" when [text] is a bare or schemed web address, else null. */
    fun asUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.contains(' ') || !urlPattern.matches(trimmed)) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    /** Google image search for [query]; the fallback when Google Lens is not installed. */
    fun imageSearchUrl(query: String): String =
        if (query.isBlank()) "https://lens.google.com/" else "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}"
}
