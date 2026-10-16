package com.foldos.launcher.onboarding

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Process
import androidx.core.graphics.ColorUtils
import com.foldos.launcher.core.model.AppInfo

/** One made-up app for the previews: a label and a brand-like color (mock-up `A` table). */
data class SampleAppSpec(val label: String, val color: Long)

/**
 * Fake apps used by every wizard and preset preview so the previews look the same on every device
 * and need no package queries. [all] are real [AppInfo]s whose [AppInfo.iconLoader] draws a
 * gradient tile, so they can be handed to the real `Dock` composable.
 */
object SampleApps {
    val specs: List<SampleAppSpec> = listOf(
        SampleAppSpec("Phone", 0xFF2F9E5A),
        SampleAppSpec("Messages", 0xFF2B7DE9),
        SampleAppSpec("Chrome", 0xFFE0533D),
        SampleAppSpec("Camera", 0xFF4B5563),
        SampleAppSpec("Gmail", 0xFFD93025),
        SampleAppSpec("Maps", 0xFF1A9A6C),
        SampleAppSpec("Photos", 0xFFE9A400),
        SampleAppSpec("YouTube", 0xFFE62117),
        SampleAppSpec("Spotify", 0xFF1DB954),
        SampleAppSpec("Calendar", 0xFF3B6FE0),
        SampleAppSpec("Notes", 0xFFF08C1A),
        SampleAppSpec("Slack", 0xFF5B2A86),
        SampleAppSpec("Teams", 0xFF4B53BC),
        SampleAppSpec("Notion", 0xFF2F2F2F),
        SampleAppSpec("Outlook", 0xFF0A66C2),
        SampleAppSpec("Health", 0xFFE0407A),
    )

    /** The sample apps as [AppInfo]s (package `com.foldos.sample.N`), icons drawn on demand. */
    val all: List<AppInfo> by lazy {
        val user = Process.myUserHandle()
        specs.mapIndexed { index, spec ->
            AppInfo(
                packageName = "com.foldos.sample.$index",
                activityName = "Sample$index",
                label = spec.label,
                user = user,
                iconLoader = { sampleIcon(spec.color) },
            )
        }
    }

    /** Favorites shown by the Minimal list style, in the mock-up's `FAV` order. */
    val favorites: List<SampleAppSpec> = listOf(1, 4, 8, 5, 9, 12, 10, 14, 6, 15, 13).map { specs[it] }

    /** Apps the dock's taskbar / smart add-ons show as "recent". */
    val recents: List<AppInfo> get() = all.drop(8).take(4)

    /** A square gradient tile; the dock clips it to the theme's icon shape. */
    fun sampleIcon(color: Long): Drawable {
        val base = color.toInt()
        val light = ColorUtils.blendARGB(base, 0xFFFFFFFF.toInt(), 0.28f)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(light, base)).apply {
            shape = GradientDrawable.RECTANGLE
        }
    }
}
