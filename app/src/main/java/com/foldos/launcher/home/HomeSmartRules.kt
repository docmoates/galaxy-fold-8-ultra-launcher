package com.foldos.launcher.home

import com.foldos.launcher.core.model.HomeItem
import java.time.LocalTime

/**
 * A time-of-day rule attached to a stack member (PLAN.md 3.2 "Smart / contextual" widgets): the
 * member is shown automatically while the local time is inside [startMinute, endMinute) (minutes
 * after midnight). A window that ends before it starts wraps past midnight; start == end means
 * all day.
 */
data class HomeTimeRule(val startMinute: Int, val endMinute: Int) {
    fun matches(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> true
        startMinute < endMinute -> minuteOfDay >= startMinute && minuteOfDay < endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    fun matches(time: LocalTime): Boolean = matches(time.hour * 60 + time.minute)
}

/** `rule:HH:MM-HH:MM` anywhere in a title or built-in id, e.g. `Work rule:09:00-17:00`. */
private val HOME_TIME_RULE: Regex = Regex("""rule:(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})""", RegexOption.IGNORE_CASE)

/** Parses the first `rule:HH:MM-HH:MM` tag in [text]; null when there is none or it is malformed. */
fun parseHomeTimeRule(text: String?): HomeTimeRule? {
    if (text == null) return null
    val match = HOME_TIME_RULE.find(text) ?: return null
    val h1 = match.groupValues[1].toIntOrNull() ?: return null
    val m1 = match.groupValues[2].toIntOrNull() ?: return null
    val h2 = match.groupValues[3].toIntOrNull() ?: return null
    val m2 = match.groupValues[4].toIntOrNull() ?: return null
    if (h1 > 24 || h2 > 24 || m1 > 59 || m2 > 59) return null
    val start = (h1 * 60 + m1) % 1440
    val end = (h2 * 60 + m2) % 1440
    return HomeTimeRule(start, end)
}

/** The time rule tagged on a stack member: in its title first, then in its built-in id. */
fun homeTimeRuleOf(item: HomeItem): HomeTimeRule? = parseHomeTimeRule(item.title) ?: parseHomeTimeRule(item.builtinId)

/** True when at least one of [items] carries a time rule, i.e. the stack is a smart stack. */
fun hasHomeSmartRules(items: List<HomeItem>): Boolean = items.any { homeTimeRuleOf(it) != null }

/**
 * The index of the stack member to show at [now]: the first member whose rule matches, else the
 * first member without a rule (the stack's default face), else null (every member has a rule and
 * none applies: leave the stack where the user put it).
 */
fun homeSmartStackIndex(items: List<HomeItem>, now: LocalTime): Int? {
    val rules = items.map(::homeTimeRuleOf)
    if (rules.all { it == null }) return null
    rules.forEachIndexed { index, rule -> if (rule != null && rule.matches(now)) return index }
    val plain = rules.indexOfFirst { it == null }
    return if (plain >= 0) plain else null
}
