package com.foldos.launcher.search

import android.content.Context
import android.util.Log
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * Universal search (PLAN.md section 4): runs every enabled provider
 * (`LauncherSettings.searchProviders`) for a query in parallel, ranks each section by match
 * score plus recency-weighted usage ([SearchUsageStore]) and returns one flat list in section order:
 * instant answers (calculator, units), apps, shortcuts, contacts, settings, calendar, files, web,
 * then rows for permissions a provider still needs.
 *
 * Call [attach] once with the app graph (the `UniversalSearch` sheet does it on first composition;
 * `Application.onCreate` may too). Until then [query] only runs the providers that need no
 * context: apps, settings, calculator, units and web.
 */
object SearchIndex {
    private const val TAG = "FoldOsSearch"

    private const val APPS_LIMIT = 8
    private const val SHORTCUTS_LIMIT = 4
    private const val CONTACTS_LIMIT = 4
    private const val SETTINGS_LIMIT = 4
    private const val CALENDAR_LIMIT = 3
    private const val FILES_LIMIT = 4
    private const val PROVIDER_TIMEOUT_MILLIS = 1_500L

    private class Env(context: Context, val graph: AppGraph) {
        val context: Context = context.applicationContext
        val usage = SearchUsageStore(this.context)
        val shortcuts = ShortcutSearchProvider(this.context)

        @Volatile
        var apps: List<AppInfo> = emptyList()

        @Volatile
        var settings: LauncherSettings = LauncherSettings()
    }

    private val lock = Any()

    @Volatile
    private var env: Env? = null

    val isAttached: Boolean get() = env != null

    /**
     * Binds the index to the process: context for the system providers, `graph.apps.apps` and
     * `graph.settings.settings` as the snapshots [query] `(text)` uses, `graph.hub` for calendar
     * events, `graph.appScope` for usage writes. Idempotent.
     */
    fun attach(context: Context, graph: AppGraph) {
        synchronized(lock) {
            if (env != null) return
            val created = Env(context, graph)
            env = created
            graph.appScope.launch {
                graph.apps.apps.collect { list ->
                    created.apps = list
                    created.shortcuts.invalidate()
                }
            }
            graph.appScope.launch {
                graph.settings.settings.collect { created.settings = it }
            }
        }
    }

    /** Results for [text] against the latest apps and settings snapshots ([attach] first). */
    suspend fun query(text: String): List<SearchResult> {
        val e = env
        return query(text, e?.settings ?: LauncherSettings(), e?.apps ?: emptyList())
    }

    /** Results for [text] with explicit [settings] and visible [apps] (minus hidden apps). */
    suspend fun query(text: String, settings: LauncherSettings, apps: List<AppInfo>): List<SearchResult> {
        val raw = text.trim()
        if (raw.isEmpty()) return emptyList()
        val q = SearchMatch.normalize(raw)
        val e = env
        val usage: Map<String, Double> = e?.let { runCatching { it.usage.scores.first() }.getOrNull() }.orEmpty()

        val appsOn = settings.searchProvider(LauncherSettings.SEARCH_APPS)
        val contactsOn = settings.searchProvider(LauncherSettings.SEARCH_CONTACTS)
        val settingsOn = settings.searchProvider(LauncherSettings.SEARCH_SETTINGS)
        val filesOn = settings.searchProvider(LauncherSettings.SEARCH_FILES)
        val calendarOn = settings.searchProvider(LauncherSettings.SEARCH_CALENDAR)
        val webOn = settings.searchProvider(LauncherSettings.SEARCH_WEB)
        val calculatorOn = settings.searchProvider(LauncherSettings.SEARCH_CALCULATOR)
        val unitsOn = settings.searchProvider(LauncherSettings.SEARCH_UNITS)

        val answers = ArrayList<SearchResult>()
        if (calculatorOn) Calculator.evaluate(raw)?.let { answers += SearchResult.Calculation(raw, it) }
        if (unitsOn) UnitConverter.convert(raw)?.let { answers += SearchResult.Conversion(it.input, it.output, it.category, it.note) }

        val sections: List<List<Scored>> = coroutineScope {
            val appsJob = async(Dispatchers.Default) {
                if (appsOn) AppSearchProvider.search(q, apps, APPS_LIMIT) else emptyList()
            }
            val shortcutsJob = async(Dispatchers.IO) {
                guarded("shortcuts") { if (appsOn && e != null) e.shortcuts.search(q, settings, apps, SHORTCUTS_LIMIT) else emptyList() }
            }
            val contactsJob = async(Dispatchers.IO) {
                guarded("contacts") { if (contactsOn && e != null) ContactSearchProvider.search(e.context, raw, CONTACTS_LIMIT) else emptyList() }
            }
            val settingsJob = async(Dispatchers.Default) {
                if (settingsOn) SettingsSearchIndex.search(q, SETTINGS_LIMIT) else emptyList()
            }
            val calendarJob = async(Dispatchers.IO) {
                guarded("calendar") {
                    if (calendarOn && e != null) CalendarSearchProvider.search(e.context, e.graph.hub, q, CALENDAR_LIMIT) else emptyList()
                }
            }
            val filesJob = async(Dispatchers.IO) {
                guarded("files") { if (filesOn && e != null) FileSearchProvider.search(e.context, raw, FILES_LIMIT) else emptyList() }
            }
            listOf(appsJob.await(), shortcutsJob.await(), contactsJob.await(), settingsJob.await(), calendarJob.await(), filesJob.await())
        }

        val out = ArrayList<SearchResult>()
        out += answers
        for (section in sections) out += rank(section, usage)

        if (webOn) {
            WebSearch.asUrl(raw)?.let { out += SearchResult.Url(it) }
            val resolved = WebSearch.resolve(raw, settings.searchEngine)
            if (resolved.query.isNotEmpty() || resolved.bang != null) {
                out += SearchResult.Web(
                    query = resolved.query.ifEmpty { raw },
                    engineId = resolved.engine.id,
                    engineName = resolved.engine.name,
                    url = resolved.engine.url(resolved.query.ifEmpty { raw }),
                    bang = resolved.bang,
                )
            }
            out += SearchResult.Visual(raw)
        }

        if (e != null && q.length >= 2) {
            val ctx = e.context
            if (contactsOn && !ctx.hasPermission(ContactSearchProvider.PERMISSION)) {
                out += SearchResult.Permission(ContactSearchProvider.PERMISSION, "Allow contacts", "Search people and call or message them")
            }
            if (calendarOn && !ctx.hasPermission(CalendarSearchProvider.PERMISSION)) {
                out += SearchResult.Permission(CalendarSearchProvider.PERMISSION, "Allow calendar", "Find events in the next 30 days")
            }
            if (filesOn && !FileSearchProvider.granted(ctx)) {
                out += SearchResult.Permission(FileSearchProvider.PERMISSION, "Allow photos and media", "Find recent files by name")
            }
        }
        return out
    }

    /** Recent queries, most recent first; empty until [attach]. */
    fun recentQueries(): Flow<List<String>> = env?.usage?.recentQueries ?: flowOf(emptyList())

    /** Counts a pick of [result] for [query] on the app scope; no-op until [attach]. */
    fun recordPick(result: SearchResult, query: String) {
        val e = env ?: return
        // Answers, permissions and the web fallback do not need ranking.
        val rankable = when (result) {
            is SearchResult.App, is SearchResult.Shortcut, is SearchResult.Contact,
            is SearchResult.Setting, is SearchResult.MediaFile, is SearchResult.CalendarEvent -> true
            else -> false
        }
        e.graph.appScope.launch {
            runCatching {
                if (rankable) e.usage.recordPick(result.key)
                e.usage.recordQuery(query)
            }.onFailure { Log.w(TAG, "Could not record search usage", it) }
        }
    }

    /** Forgets picks and recent queries ("Clear search history"). */
    suspend fun clearHistory(context: Context) {
        (env?.usage ?: SearchUsageStore(context)).clear()
    }

    private suspend fun guarded(name: String, block: suspend () -> List<Scored>): List<Scored> =
        try {
            withTimeoutOrNull(PROVIDER_TIMEOUT_MILLIS) { block() } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Search provider $name failed", e)
            emptyList()
        }

    /** Match score plus up to 30 points of usage, then the section's own order as the tie-break. */
    private fun rank(section: List<Scored>, usage: Map<String, Double>): List<SearchResult> {
        if (section.isEmpty()) return emptyList()
        if (usage.isEmpty()) return section.map { it.result }
        return section
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Scored>> { it.value.score + usageBoost(usage[it.value.result.key]) }
                    .thenBy { it.index },
            )
            .map { it.value.result }
    }

    private fun usageBoost(score: Double?): Int = if (score == null) 0 else min(30, (score * 10).toInt())
}
