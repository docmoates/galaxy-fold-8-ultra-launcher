package com.foldos.launcher.perf

import android.app.Activity
import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cold-start bookkeeping. Everything is logged to Logcat under tag [TAG] and mirrored into
 * `android.os.Trace` so the same spans show up in a Perfetto / system trace:
 *
 * - [section] wraps a block (for example the app graph construction) in a Trace section and records
 *   how long it took.
 * - [onActivityCreate] marks the start of `MainActivity.onCreate` and opens the async Trace section
 *   "FoldOS.firstFrame".
 * - [onFirstFrame] runs after the first composed frame: it logs the totals, closes the async section
 *   and calls `Activity.reportFullyDrawn()` (the system then reports the app-start time itself; see
 *   `adb shell am start -W` and the "Fully drawn" line in Logcat).
 *
 * Cold vs warm: a process whose age is below [COLD_PROCESS_AGE_MS] when the first Activity is created
 * is a cold start and the total is measured from process start (`Process.getStartElapsedRealtime`,
 * so Application.onCreate and the graph construction are included). An older process (started by a
 * receiver or the notification listener earlier) is logged as warm and measured from `onCreate`.
 *
 * All entry points are cheap and safe to call from any thread, and each reports at most once per
 * process.
 */
object StartupTracker {

    const val TAG: String = "FoldOsStartup"

    /** Trace cookie of the async "FoldOS.firstFrame" section. */
    private const val TRACE_COOKIE: Int = 0x501D
    private const val TRACE_FIRST_FRAME: String = "FoldOS.firstFrame"

    /** Process age below which the first Activity creation counts as a cold start. */
    private const val COLD_PROCESS_AGE_MS: Long = 10_000L

    private val activityStarted = AtomicBoolean(false)
    private val reported = AtomicBoolean(false)
    private val sections = ConcurrentLinkedQueue<String>()

    @Volatile
    private var activityCreateElapsed: Long = 0L

    @Volatile
    private var processAgeAtCreate: Long = 0L

    /**
     * Runs [block] inside a Trace section called [name] and records its duration. The block's
     * exceptions propagate unchanged. Example:
     * `graph = StartupTracker.section("graphInit") { AppGraphImpl(this) }`.
     */
    fun <T> section(name: String, block: () -> T): T {
        val label = if (name.length > MAX_SECTION_NAME) name.substring(0, MAX_SECTION_NAME) else name
        val start = SystemClock.elapsedRealtime()
        Trace.beginSection(label)
        try {
            return block()
        } finally {
            Trace.endSection()
            val took = SystemClock.elapsedRealtime() - start
            sections.add("$label=${took}ms")
            Log.i(TAG, "$label took $took ms")
        }
    }

    /** Call at the very top of `MainActivity.onCreate`. Only the first call per process counts. */
    fun onActivityCreate() {
        if (!activityStarted.compareAndSet(false, true)) return
        val now = SystemClock.elapsedRealtime()
        activityCreateElapsed = now
        processAgeAtCreate = now - Process.getStartElapsedRealtime()
        Trace.beginAsyncSection(TRACE_FIRST_FRAME, TRACE_COOKIE)
        Log.i(TAG, "MainActivity.onCreate reached $processAgeAtCreate ms after process start")
    }

    /**
     * Call once the first frame with content is on screen. Logs the cold / warm start time, closes
     * the Trace section and tells the system the app is fully drawn.
     */
    fun onFirstFrame(activity: Activity) {
        if (!reported.compareAndSet(false, true)) return
        val now = SystemClock.elapsedRealtime()
        if (activityStarted.get()) {
            Trace.endAsyncSection(TRACE_FIRST_FRAME, TRACE_COOKIE)
            val cold = processAgeAtCreate < COLD_PROCESS_AGE_MS
            val total = if (cold) now - Process.getStartElapsedRealtime() else now - activityCreateElapsed
            val toFrame = now - activityCreateElapsed
            val kind = if (cold) "COLD" else "WARM"
            val detail = if (sections.isEmpty()) "" else " sections: " + sections.joinToString(", ")
            Log.i(
                TAG,
                "$kind start: first frame $total ms (onCreate to first frame $toFrame ms, " +
                    "process age at onCreate $processAgeAtCreate ms)$detail",
            )
        } else {
            Log.i(TAG, "First frame reached without onActivityCreate()")
        }
        runCatching { activity.reportFullyDrawn() }
            .onFailure { Log.w(TAG, "reportFullyDrawn failed", it) }
    }

    private const val MAX_SECTION_NAME = 120
}

/**
 * Reports the first frame to [StartupTracker] (which calls `reportFullyDrawn()`): the effect starts
 * after the first composition has been applied and waits for the next frame before reporting.
 * Compose it once, near the root.
 */
@Composable
fun ReportFullyDrawnOnFirstFrame(activity: Activity) {
    LaunchedEffect(activity) {
        withFrameNanos { }
        StartupTracker.onFirstFrame(activity)
    }
}
