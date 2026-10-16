package com.foldos.launcher.ai

import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ThemeId
import com.foldos.launcher.core.repo.AiProvider
import com.foldos.launcher.core.repo.MailState
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The `AI_MODE_TEMPLATE` engine: no model, no network. Classifies the question with keyword
 * patterns and fills sentence templates from an [AiContextSnapshot] (today's events, tasks, missed
 * calls, unread mail and notifications, the launcher settings). When built without a
 * [contextBuilder] it answers from the plain-text [complete] context only.
 *
 * Distinct from `data.TemplateAiProvider`, the echo placeholder this class replaces.
 */
class FoldOsTemplateAiProvider(
    private val contextBuilder: AiContextBuilder? = null,
) : AiProvider {

    override val name: String = "Template"

    override suspend fun available(): Boolean = true

    override suspend fun complete(prompt: String, context: String): String {
        val snapshot = contextBuilder?.snapshot()
        return if (snapshot != null) answer(prompt, snapshot) else answerWithoutData(prompt, context)
    }

    /** The Today tab's briefing paragraph, assembled from the snapshot. */
    fun briefing(s: AiContextSnapshot): String {
        val parts = ArrayList<String>()
        val current = s.currentEvent()
        val next = s.nextEvent()
        when {
            current != null -> parts.add("${current.title} is on now until ${aiClock(current.end)}.")
            next != null && s.todayEvents.contains(next) ->
                parts.add("Next up is ${next.title} at ${aiClock(next.begin)} (${aiRelative(next.begin, s.nowMillis)})${next.location?.let { ", at $it" } ?: ""}.")
            next != null -> parts.add("Nothing more today; your next event is ${next.title} ${aiDayName(next.begin, s.now)} at ${aiClock(next.begin)}.")
            else -> parts.add("Your calendar is clear.")
        }
        val timed = s.todayEvents.count { !it.allDay }
        val due = s.tasksDueToday()
        if (timed > 0 || due.isNotEmpty()) {
            parts.add(
                listOfNotNull(
                    if (timed > 0) aiPlural(timed, "event") else null,
                    if (due.isNotEmpty()) "${aiPlural(due.size, "task")} due" else null,
                ).joinToString(" and ").replaceFirstChar { it.titlecase(Locale.getDefault()) } + " today.",
            )
        }
        if (s.unreadMail > 0) {
            val top = s.unreadMessages.firstOrNull()
            parts.add(
                if (top != null) "${aiPlural(s.unreadMail, "unread email")}; the latest is from ${top.from} about “${top.subject}”."
                else "${aiPlural(s.unreadMail, "unread email")}.",
            )
        }
        if (s.missedCalls.isNotEmpty()) {
            val first = s.missedCalls.first()
            parts.add("You missed a call from ${first.name ?: first.number} at ${aiClock(first.time)}.")
        }
        return parts.joinToString(" ")
    }

    /** Answers [prompt] from [s]; public so the Today tab can reuse the same wording. */
    fun answer(prompt: String, s: AiContextSnapshot): String {
        val p = prompt.trim().lowercase()
        if (p.isEmpty()) return "Ask me about your day, your schedule, your inbox, or tell the launcher what to change."
        return when {
            HELP.containsMatchIn(p) -> helpText()
            THANKS.containsMatchIn(p) -> "Anytime."
            TIME.containsMatchIn(p) -> "It's ${s.now.format(DateTimeFormatter.ofPattern("h:mm a 'on' EEEE, MMMM d", Locale.getDefault()))}."
            NEXT.containsMatchIn(p) -> nextEventText(s)
            TOMORROW.containsMatchIn(p) -> dayText(s, 1)
            WEEKEND.containsMatchIn(p) -> weekendText(s)
            WEEK.containsMatchIn(p) -> weekText(s)
            BRIEFING.containsMatchIn(p) -> briefing(s)
            SCHEDULE.containsMatchIn(p) -> dayText(s, 0)
            TASKS.containsMatchIn(p) -> tasksText(s)
            MAIL.containsMatchIn(p) -> mailText(s)
            CALLS.containsMatchIn(p) -> callsText(s)
            NOTIFICATIONS.containsMatchIn(p) -> notificationsText(s)
            SETTINGS.containsMatchIn(p) -> settingsText(s)
            WEATHER.containsMatchIn(p) -> "I don't have weather data on-device. Add the Weather widget to a home page and it will show the forecast here."
            else -> fallbackText()
        }
    }

    private fun helpText(): String =
        "I run entirely on this phone. Ask about today, your next event, tomorrow, the weekend, tasks, " +
            "your inbox, missed calls or notifications, or tell the launcher what to change: " +
            "“make my inner screen a dashboard”, “switch to dark mode”, “use the ocean colors”, " +
            "“set the bold theme”, “hide Reddit”, “open Spotify when I unfold”."

    private fun fallbackText(): String =
        "I can't answer that on-device yet. I can tell you what's next, what's due, who wrote, who called, " +
            "or change a launcher setting: try “what's next?”, “what's due today?” or “switch to dark mode”."

    private fun nextEventText(s: AiContextSnapshot): String {
        val current = s.currentEvent()
        val next = s.nextEvent()
        return when {
            current != null && next != null ->
                "${current.title} is on now until ${aiClock(current.end)}. After that: ${next.title} at ${aiClock(next.begin)} ${aiDayName(next.begin, s.now)}${next.location?.let { ", at $it" } ?: ""}."
            current != null -> "${current.title} is on now until ${aiClock(current.end)}, and nothing is scheduled after it."
            next != null ->
                "Next is ${next.title} ${aiDayName(next.begin, s.now)} at ${aiClock(next.begin)}, ${aiRelative(next.begin, s.nowMillis)}" +
                    "${next.location?.let { ", at $it" } ?: ""}. It runs until ${aiClock(next.end)}."
            else -> "Nothing is scheduled in the next seven days."
        }
    }

    private fun dayText(s: AiContextSnapshot, offsetDays: Long): String {
        val day = s.now.toLocalDate().plusDays(offsetDays)
        val events = s.eventsOn(day)
        val label = if (offsetDays == 0L) "today" else "tomorrow"
        if (events.isEmpty()) {
            val due = if (offsetDays == 0L) s.tasksDueToday() else emptyList()
            return if (due.isEmpty()) "Nothing on the calendar $label." else "No events $label, but ${aiPlural(due.size, "task is", "tasks are")} due: ${due.joinToString { it.title }}."
        }
        val lines = events.map { "${aiEventTime(it)} ${it.title}${it.location?.let { loc -> " ($loc)" } ?: ""}" }
        val intro = "${aiPlural(events.size, "event")} $label: "
        val tail = if (offsetDays == 0L && s.tasksDueToday().isNotEmpty()) " Plus ${aiPlural(s.tasksDueToday().size, "task")} due." else ""
        return intro.replaceFirstChar { it.titlecase(Locale.getDefault()) } + lines.joinToString("; ") + "." + tail
    }

    private fun weekendText(s: AiContextSnapshot): String {
        val today = s.now.toLocalDate()
        var saturday = today
        while (saturday.dayOfWeek != java.time.DayOfWeek.SATURDAY) saturday = saturday.plusDays(1)
        val sunday = saturday.plusDays(1)
        val sat = s.eventsOn(saturday)
        val sun = s.eventsOn(sunday)
        if (sat.isEmpty() && sun.isEmpty()) return "The weekend is clear."
        fun dayLine(name: String, events: List<com.foldos.launcher.core.repo.HubEvent>): String =
            if (events.isEmpty()) "$name is clear" else "$name: ${events.joinToString("; ") { "${aiEventTime(it)} ${it.title}" }}"
        return dayLine("Saturday", sat) + ". " + dayLine("Sunday", sun) + "."
    }

    private fun weekText(s: AiContextSnapshot): String {
        val all = s.todayEvents + s.upcomingEvents
        if (all.isEmpty()) return "Nothing is scheduled this week."
        val byDay = all.groupBy { aiDayName(it.begin, s.now) }
        val summary = byDay.entries.take(7).joinToString("; ") { (day, events) ->
            "${day.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }}: ${events.take(3).joinToString(", ") { it.title }}${if (events.size > 3) " +${events.size - 3}" else ""}"
        }
        return "${aiPlural(all.size, "event")} this week. $summary."
    }

    private fun tasksText(s: AiContextSnapshot): String {
        val open = s.openTasks
        if (open.isEmpty()) return "No open tasks. Add one from the To-do widget or the Today tab."
        val due = s.tasksDueToday()
        val head = if (due.isNotEmpty()) "${aiPlural(due.size, "task")} due today: ${due.joinToString { it.title }}." else "Nothing is due today."
        val rest = open.filterNot { it in due }
        val tail = if (rest.isNotEmpty()) " ${aiPlural(rest.size, "more task")} open: ${rest.take(4).joinToString { it.title }}${if (rest.size > 4) ", …" else ""}." else ""
        return head + tail
    }

    private fun mailText(s: AiContextSnapshot): String = when (val m = s.mail) {
        is MailState.Connected -> {
            val unread = m.messages.filter { it.unread }
            if (unread.isEmpty()) "Your inbox is caught up: no unread mail in the last ${m.messages.size} messages."
            else "${aiPlural(unread.size, "unread email")}. " + unread.take(3).joinToString(" ") { "${it.from}: “${it.subject}”." } +
                if (unread.size > 3) " And ${unread.size - 3} more." else ""
        }
        MailState.NotConfigured -> "Mail isn't set up on the server yet. Your administrator needs to turn it on."
        MailState.SignedOut -> "You're not signed in. Open the Mail tab and sign in with your Crosspoint account."
        MailState.NotLinked -> "Gmail isn't linked yet. Open the Mail tab and tap Connect Gmail."
        MailState.Connecting -> "Still loading your inbox; ask again in a moment."
        is MailState.Error -> "Gmail could not be loaded: ${m.message}"
    }

    private fun callsText(s: AiContextSnapshot): String {
        if (s.missedCalls.isEmpty()) return "No missed calls."
        return "${aiPlural(s.missedCalls.size, "missed call")}: " +
            s.missedCalls.take(4).joinToString("; ") { "${it.name ?: it.number} ${aiRelative(it.time, s.nowMillis)}" } + "."
    }

    private fun notificationsText(s: AiContextSnapshot): String {
        if (s.badgeCounts.isEmpty()) return "No app has unread notifications (or notification access isn't granted yet)."
        val top = s.badgeCounts.entries.sortedByDescending { it.value }.take(5)
        return "${aiPlural(s.totalBadges, "notification")} across ${aiPlural(s.badgeCounts.size, "app")}: " +
            top.joinToString(", ") { "${s.appLabel(it.key)} ${it.value}" } + "."
    }

    private fun settingsText(s: AiContextSnapshot): String {
        val st = s.settings
        val theme = when (st.theme) {
            ThemeId.MATERIAL -> "Material You"
            ThemeId.GLASS -> "Dark Glass"
            ThemeId.ONEUI -> "One UI"
            ThemeId.BOLD -> "Bold"
        }
        val appearance = when (st.appearance) {
            Appearance.DARK -> "dark"
            Appearance.LIGHT -> "light"
            Appearance.AUTO -> "auto"
        }
        fun style(style: HomeStyle) = when (style) {
            HomeStyle.GRID -> "a classic grid"
            HomeStyle.DASHBOARD -> "a widget dashboard"
            HomeStyle.LIST -> "a minimal list"
            HomeStyle.HYBRID -> "hybrid panels"
        }
        return "You're on the $theme theme in $appearance mode with the ${st.colors.scheme} colors. " +
            "The cover screen is ${style(st.styleCover)} with ${st.colsCover} columns and the inner screen ${style(st.styleInner)} with ${st.colsInner}. " +
            "The hub is ${if (st.hubOn) "on the ${st.hubPos}" else "off"}, ${aiPlural(st.hiddenApps.size, "app is", "apps are")} hidden. " +
            "Say things like “switch to light mode” or “make my cover screen a list” to change any of it."
    }

    /** Fallback when no structured snapshot exists: only the rendered text is available. */
    private fun answerWithoutData(prompt: String, context: String): String {
        val p = prompt.trim().lowercase()
        return when {
            p.isEmpty() -> "Ask me about your day or tell the launcher what to change."
            HELP.containsMatchIn(p) -> helpText()
            context.isBlank() -> fallbackText()
            else -> "Here is what I know right now:\n" + context.trim().lines().take(8).joinToString("\n")
        }
    }

    private companion object {
        val HELP = Regex("""\b(what can you do|help|how do (?:i|you)|what are you)\b""")
        val THANKS = Regex("""\b(thanks|thank you|cheers|ta)\b""")
        val TIME = Regex("""\b(what time|the time|what(?:'s| is) the date|what day is|today's date)\b""")
        val NEXT = Regex("""\b(next (?:meeting|event|appointment|thing|call)|what(?:'s| is) next|when(?:'s| is) my next|coming up)\b""")
        val TOMORROW = Regex("""\btomorrow\b""")
        val WEEKEND = Regex("""\b(weekend|saturday|sunday)\b""")
        val WEEK = Regex("""\b(this week|the week|next (?:few )?days|upcoming)\b""")
        val BRIEFING = Regex("""\b(briefing|brief me|my day|how(?:'s| is) (?:my|the) day|good (?:morning|afternoon|evening)|hello|hi|hey|what(?:'s| is) (?:up|new|going on)|summar(?:y|ize|ise)|overview|catch me up)\b""")
        val SCHEDULE = Regex("""\b(schedule|calendar|agenda|meetings?|events?|appointments?|today|on today|plans?)\b""")
        val TASKS = Regex("""\b(tasks?|to-?dos?|due|reminders?|chores?)\b""")
        val MAIL = Regex("""\b(mail|e-?mails?|inbox|unread|messages?|gmail|wrote)\b""")
        val CALLS = Regex("""\b(missed calls?|who called|calls?|call(?:ed)? me|callbacks?|voicemail)\b""")
        val NOTIFICATIONS = Regex("""\b(notifications?|badges?|alerts?)\b""")
        val SETTINGS = Regex("""\b(theme|settings?|set ?up|how (?:is|am i) (?:the|my) launcher|my (?:dock|style|layout|colou?rs)|configured|appearance)\b""")
        val WEATHER = Regex("""\b(weather|temperature|rain|forecast|sunny|umbrella)\b""")
    }
}
