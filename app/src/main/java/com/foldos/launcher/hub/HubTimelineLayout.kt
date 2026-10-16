package com.foldos.launcher.hub

import com.foldos.launcher.core.repo.HubEvent
import kotlin.math.max

/** One timed event placed in the day timeline: column [column] of [columns] inside its overlap cluster. */
data class HubTimelineSlot(val event: HubEvent, val column: Int, val columns: Int)

/**
 * Side-by-side layout for overlapping events (the Calendar tab's day timeline). Timed events are
 * grouped into clusters of transitively overlapping events; inside a cluster each event takes the
 * first column whose previous event has already ended, and every event of the cluster is as wide as
 * `1 / columns` of the timeline. All-day events are skipped. Pure, so it is unit-testable.
 */
fun layoutHubTimeline(events: List<HubEvent>): List<HubTimelineSlot> {
    val timed = events
        .filter { !it.allDay && it.end > it.begin }
        .sortedWith(compareBy<HubEvent> { it.begin }.thenByDescending { it.end })
    if (timed.isEmpty()) return emptyList()

    val result = ArrayList<HubTimelineSlot>(timed.size)
    val cluster = ArrayList<HubEvent>()
    var clusterEnd = Long.MIN_VALUE

    fun flush() {
        if (cluster.isEmpty()) return
        val columnEnds = ArrayList<Long>()
        val columnOf = IntArray(cluster.size)
        cluster.forEachIndexed { index, event ->
            var column = columnEnds.indexOfFirst { it <= event.begin }
            if (column < 0) {
                column = columnEnds.size
                columnEnds.add(event.end)
            } else {
                columnEnds[column] = event.end
            }
            columnOf[index] = column
        }
        val columns = columnEnds.size
        cluster.forEachIndexed { index, event -> result += HubTimelineSlot(event, columnOf[index], columns) }
        cluster.clear()
    }

    for (event in timed) {
        if (cluster.isNotEmpty() && event.begin >= clusterEnd) flush()
        cluster += event
        clusterEnd = max(clusterEnd, event.end)
    }
    flush()
    return result
}

/**
 * The whole hours the timeline should span for [events] on a day starting at [dayStartMillis]:
 * at least [minStartHour]..[minEndHour], widened to cover every timed event (clamped to 0..24).
 */
fun hubTimelineHours(
    events: List<HubEvent>,
    dayStartMillis: Long,
    minStartHour: Int = 8,
    minEndHour: Int = 18,
): IntRange {
    var start = minStartHour
    var end = minEndHour
    for (event in events) {
        if (event.allDay) continue
        val beginHour = ((event.begin - dayStartMillis) / 3_600_000.0)
        val endHour = ((event.end - dayStartMillis) / 3_600_000.0)
        if (beginHour < start) start = kotlin.math.floor(beginHour).toInt()
        if (endHour > end) end = kotlin.math.ceil(endHour).toInt()
    }
    start = start.coerceIn(0, 23)
    end = end.coerceIn(start + 1, 24)
    return start..end
}
