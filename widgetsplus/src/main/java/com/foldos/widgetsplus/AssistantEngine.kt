package com.foldos.widgetsplus

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** A button under an answer; tapping it starts [intent] (the dialer, a timer, navigation...). Nothing runs without that tap. */
data class AssistantAction(val label: String, val intent: Intent)

data class AssistantReply(val text: String, val actions: List<AssistantAction> = emptyList())

/**
 * The Ask AI brain of Widgets +: no model and no cloud service, like the FoldOS Hub's template engine. It matches
 * keywords in the question and fills sentences from your calendar, your contacts and (when signed in) your inbox.
 * Requests it can act on ("call Sam", "10 minute timer", "new event dentist") come back with an action button; for
 * anything else it offers a web search and your phone's assistant. Call from a worker thread (mail uses the network).
 */
object AssistantEngine {

    fun answer(context: Context, prompt: String): AssistantReply {
        val raw = prompt.trim()
        val p = raw.lowercase(Locale.getDefault())
        if (p.isEmpty()) return AssistantReply(helpText())
        return when {
            HELP.containsMatchIn(p) -> AssistantReply(helpText())
            THANKS.containsMatchIn(p) -> AssistantReply("Anytime.")
            TIMER.containsMatchIn(p) -> timer(p)
            ALARM.containsMatchIn(p) -> alarm(p)
            CALL.containsMatchIn(p) -> contactAction(context, CALL.find(p)!!.groupValues[1], "call")
            TEXT.containsMatchIn(p) -> contactAction(context, TEXT.find(p)!!.groupValues[1], "text")
            FIND.containsMatchIn(p) -> contactAction(context, FIND.find(p)!!.groupValues[1], "find")
            DIRECTIONS.containsMatchIn(p) -> directions(DIRECTIONS.find(raw.lowercase(Locale.getDefault()))!!.groupValues[1])
            ADD_EVENT.containsMatchIn(p) -> addEvent(ADD_EVENT.find(raw)?.groupValues?.get(1)?.trim().orEmpty())
            COMPOSE.containsMatchIn(p) -> AssistantReply(
                "Opening a new email in your mail app.",
                listOf(AssistantAction("Write an email", Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))),
            )
            TIME.containsMatchIn(p) -> AssistantReply("It's " + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a 'on' EEEE, MMMM d", Locale.getDefault())) + ".")
            NEXT.containsMatchIn(p) -> next(context)
            TOMORROW.containsMatchIn(p) -> day(context, 1)
            WEEK.containsMatchIn(p) -> week(context)
            BRIEFING.containsMatchIn(p) -> briefing(context)
            MAIL.containsMatchIn(p) -> mail(context)
            SCHEDULE.containsMatchIn(p) -> day(context, 0)
            else -> fallback(raw)
        }
    }

    // ---- Questions about your data -------------------------------------------------------------------

    private fun events(context: Context): List<EventItem>? =
        if (WidgetData.hasCalendar(context)) WidgetData.upcomingEvents(context, days = 8, limit = 60) else null

    private const val NO_CALENDAR = "Calendar access is off. Open Widgets + and tap Allow so I can read your events."

    private fun next(context: Context): AssistantReply {
        val events = events(context) ?: return AssistantReply(NO_CALENDAR)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val current = events.firstOrNull { !it.allDay && it.begin <= now && it.end > now }
        val upcoming = events.firstOrNull { !it.allDay && it.begin > now }
        val text = when {
            current != null && upcoming != null -> "${current.title} is on now until ${clock(current.end, zone)}. After that: ${describe(upcoming, zone)}."
            current != null -> "${current.title} is on now until ${clock(current.end, zone)}, and nothing is scheduled after it."
            upcoming != null -> "Next is ${describe(upcoming, zone)}."
            else -> "Nothing is scheduled in the next week."
        }
        return AssistantReply(text)
    }

    private fun day(context: Context, offset: Long): AssistantReply {
        val events = events(context) ?: return AssistantReply(NO_CALENDAR)
        val zone = ZoneId.systemDefault()
        val target = LocalDate.now(zone).plusDays(offset)
        val label = if (offset == 0L) "today" else "tomorrow"
        val list = events.filter { it.day(zone) == target }
        if (list.isEmpty()) return AssistantReply("Nothing on the calendar $label.")
        val lines = list.joinToString("\n") { "• " + (if (it.allDay) "All day" else clock(it.begin, zone)) + "  " + it.title + (it.location?.let { l -> " ($l)" } ?: "") }
        return AssistantReply("${plural(list.size, "event")} $label:\n$lines")
    }

    private fun week(context: Context): AssistantReply {
        val events = events(context) ?: return AssistantReply(NO_CALENDAR)
        if (events.isEmpty()) return AssistantReply("Nothing is scheduled this week.")
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val lines = events.groupBy { it.day(zone) }.entries.take(7).joinToString("\n") { (d, list) ->
            "• ${dayName(d, today)}: ${list.take(3).joinToString(", ") { it.title }}" + if (list.size > 3) " +${list.size - 3}" else ""
        }
        return AssistantReply("${plural(events.size, "event")} in the next week:\n$lines")
    }

    private fun briefing(context: Context): AssistantReply {
        val zone = ZoneId.systemDefault()
        val parts = ArrayList<String>()
        val events = events(context)
        if (events == null) {
            parts += NO_CALENDAR
        } else {
            val today = LocalDate.now(zone)
            val todays = events.filter { it.day(zone) == today }
            parts += when {
                todays.isEmpty() -> "Nothing on the calendar today."
                else -> "${plural(todays.size, "event")} today: " + todays.take(4).joinToString("; ") { (if (it.allDay) "all day" else clock(it.begin, zone)) + " " + it.title } + "."
            }
            val upcoming = events.firstOrNull { !it.allDay && it.begin > System.currentTimeMillis() }
            if (upcoming != null) parts += "Next up: ${describe(upcoming, zone)}."
        }
        if (MailService.isSignedIn(context)) {
            when (val m = MailService.inbox(context, 5)) {
                is MailResult.Connected -> parts += if (m.unread == 0) "Your inbox is caught up." else "${plural(m.unread, "unread email")}; the latest is from ${m.rows.firstOrNull { it.unread }?.sender ?: m.rows.firstOrNull()?.sender ?: "someone"}."
                else -> Unit
            }
        }
        return AssistantReply(parts.joinToString(" "))
    }

    private fun mail(context: Context): AssistantReply {
        return when (val m = MailService.inbox(context, 10)) {
            is MailResult.Connected -> {
                val unread = m.rows.filter { it.unread }
                val text = if (unread.isEmpty()) {
                    "Your inbox is caught up: no unread mail in the last ${m.rows.size} messages."
                } else {
                    "${plural(m.unread, "unread email")}.\n" + unread.take(4).joinToString("\n") { "• ${it.sender}: “${it.subject}”" } +
                        if (unread.size > 4) "\nAnd ${unread.size - 4} more." else ""
                }
                AssistantReply(text + if (m.offline) "\n(Offline: this is the last copy I have.)" else "", listOf(AssistantAction("Open mail", mailApp())))
            }
            MailResult.SignedOut -> AssistantReply("You are not signed in. Open Widgets + and sign in with your account to see your mail.")
            MailResult.NotLinked -> AssistantReply("Gmail is not linked yet. Open Widgets + and tap Link Gmail.")
            MailResult.NotConfigured -> AssistantReply("Mail is not set up on the server yet.")
            is MailResult.Error -> AssistantReply("I couldn't load your mail: ${m.message}")
        }
    }

    // ---- Things it can start (each needs a tap) ------------------------------------------------------

    private fun contactAction(context: Context, nameRaw: String, verb: String): AssistantReply {
        val name = nameRaw.trim().trimEnd('?', '.', '!')
        if (!WidgetData.hasContacts(context)) return AssistantReply("Contacts access is off. Open Widgets + and tap Allow so I can find $name.")
        val match = WidgetData.findContacts(context, name, 1).firstOrNull()
            ?: return AssistantReply("I couldn't find “$name” in your contacts.")
        return when (verb) {
            "call" -> AssistantReply("Ready to call ${match.name}.", listOf(AssistantAction("Call ${match.name}", Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", match.number, null)))))
            "text" -> AssistantReply("Ready to message ${match.name}.", listOf(AssistantAction("Message ${match.name}", Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", match.number, null)))))
            else -> AssistantReply(
                "${match.name}: ${match.number}",
                listOf(
                    AssistantAction("Open contact", Intent(Intent.ACTION_VIEW, match.lookupUri)),
                    AssistantAction("Call ${match.name}", Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", match.number, null))),
                ),
            )
        }
    }

    private fun timer(p: String): AssistantReply {
        val m = TIMER.find(p)!!
        val amount = (m.groups[1]?.value ?: m.groups[3]?.value)?.toIntOrNull()
        val unit = m.groups[2]?.value ?: m.groups[4]?.value ?: "minute"
        if (amount == null || amount <= 0) return AssistantReply("How long? Try “set a 10 minute timer”.")
        val seconds = when {
            unit.startsWith("h") -> amount * 3600
            unit.startsWith("s") -> amount
            else -> amount * 60
        }
        val label = "$amount ${if (unit.startsWith("h")) "hour" else if (unit.startsWith("s")) "second" else "minute"}${if (amount == 1) "" else "s"}"
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        return AssistantReply("Ready to start a $label timer.", listOf(AssistantAction("Start $label timer", intent)))
    }

    private fun alarm(p: String): AssistantReply {
        val m = ALARM.find(p)!!
        var hour = m.groupValues[1].toIntOrNull() ?: return AssistantReply("What time? Try “set an alarm for 7:30 am”.")
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val ampm = m.groupValues[3]
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return AssistantReply("That time doesn't look right. Try “set an alarm for 7:30 am”.")
        val label = java.time.LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute)
        return AssistantReply("Ready to set an alarm for $label.", listOf(AssistantAction("Set alarm for $label", intent)))
    }

    private fun directions(place: String): AssistantReply {
        val target = place.trim().trimEnd('?', '.', '!')
        if (target.isEmpty()) return AssistantReply("Where to? Try “directions to the airport”.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(target)))
        return AssistantReply("Ready to navigate to $target.", listOf(AssistantAction("Navigate to $target", intent)))
    }

    private fun addEvent(title: String): AssistantReply {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        if (title.isNotEmpty()) intent.putExtra(CalendarContract.Events.TITLE, title)
        return AssistantReply(
            if (title.isEmpty()) "Ready to add an event." else "Ready to add “$title” to your calendar.",
            listOf(AssistantAction("Add event", intent)),
        )
    }

    private fun fallback(raw: String): AssistantReply = AssistantReply(
        "I can't answer that myself. I know your calendar, contacts and mail, and I can start calls, messages, timers, alarms, " +
            "events and directions. Try “what's next?” or “call Sam”, or hand this to a search or your phone's assistant.",
        listOf(
            AssistantAction("Search the web", Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, raw)),
            AssistantAction("Ask your assistant", Intent(Intent.ACTION_ASSIST)),
        ),
    )

    private fun helpText(): String =
        "Ask me about today (“what's next?”, “plan my day”, “what's on tomorrow?”), your mail (“summarize my mail”), " +
            "or tell me to act: “call Sam”, “text Priya”, “find Dr Lee”, “set a 10 minute timer”, " +
            "“set an alarm for 7 am”, “new event dentist”, “directions to the airport”. I run on this phone and read " +
            "only what you have allowed. Anything that places a call or sends a message waits for your tap."

    // ---- Wording helpers -----------------------------------------------------------------------------

    private fun mailApp(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)

    private fun clock(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun describe(e: EventItem, zone: ZoneId): String {
        val today = LocalDate.now(zone)
        val d = e.day(zone)
        val whenText = (if (d == today) "today" else dayName(d, today)) + " at " + clock(e.begin, zone)
        val minutes = ((e.begin - System.currentTimeMillis()) / 60_000L).toInt()
        val relative = when {
            minutes < 1 -> "starting now"
            minutes < 60 -> "in $minutes min"
            minutes < 24 * 60 -> "in ${minutes / 60} h ${minutes % 60} min"
            else -> null
        }
        return "${e.title} $whenText" + (relative?.let { " ($it)" } ?: "") + (e.location?.let { ", at $it" } ?: "")
    }

    private fun dayName(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    private fun plural(n: Int, word: String): String = if (n == 1) "1 $word" else "$n ${word}s"

    private val HELP = Regex("""\b(what can you do|help|how do (?:i|you)|what are you)\b""")
    private val THANKS = Regex("""\b(thanks|thank you|cheers)\b""")
    private val TIME = Regex("""\b(what time|the time|what(?:'s| is) the date|what day is|today's date)\b""")
    private val NEXT = Regex("""\b(next (?:meeting|event|appointment|thing)|what(?:'s| is) next|when(?:'s| is) my next|coming up)\b""")
    private val TOMORROW = Regex("""\btomorrow\b""")
    private val WEEK = Regex("""\b(this week|the week|next (?:few )?days|upcoming)\b""")
    private val BRIEFING = Regex("""\b(briefing|brief me|plan my day|my day|how(?:'s| is) (?:my|the) day|good (?:morning|afternoon|evening)|catch me up|overview|summary of my day)\b""")
    private val SCHEDULE = Regex("""\b(schedule|calendar|agenda|meetings?|events?|appointments?|today|plans?)\b""")
    private val MAIL = Regex("""\b(mail|e-?mails?|inbox|unread|gmail)\b""")
    private val CALL = Regex("""^(?:please )?(?:call|dial|phone|ring)\s+(.+)$""")
    private val TEXT = Regex("""^(?:please )?(?:text|message|sms)\s+(.+)$""")
    private val FIND = Regex("""^(?:find|who is|look ?up|number for|phone for|contact for)\s+(.+)$""")
    private val DIRECTIONS = Regex("""\b(?:directions? to|navigate to|take me to|route to)\s+(.+)$""")
    private val ADD_EVENT = Regex("""(?i)\b(?:add|create|new|schedule|make)\s+(?:an?\s+)?(?:calendar\s+)?(?:event|meeting|appointment)\s*(?:called|named|for|about)?\s*(.*)$""")
    private val COMPOSE = Regex("""\b(?:compose|write|draft|send|new)\s+(?:an?\s+)?(?:e-?mail|mail)\b""")
    private val TIMER = Regex("""(?:\b(\d+)\s*-?\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)\s+timer\b|\btimer\s+(?:for\s+)?(\d+)\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)\b)""")
    private val ALARM = Regex("""\balarm\s+(?:for|at)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""")
}
