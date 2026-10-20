package com.foldos.launcher.hub

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Pinned and recently launched apps of the Hub's Launch tab, kept in SharedPreferences
 * `foldos_hub_launch` as newline-joined app keys (`AppInfo.key`, "package/activity", never contains a
 * newline). Keys of uninstalled apps stay stored and are skipped when the tab resolves them.
 * [pinned] and [recent] are Compose state, so the tab redraws on every change.
 */
internal class HubLaunchStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val pinnedState = mutableStateOf(read(KEY_PINNED))
    private val recentState = mutableStateOf(read(KEY_RECENT))

    /** Pinned app keys in the order they were pinned. */
    val pinned: List<String> get() = pinnedState.value

    /** Launched app keys, most recent first, at most [MAX_RECENT]. */
    val recent: List<String> get() = recentState.value

    fun isPinned(key: String): Boolean = key in pinnedState.value

    fun togglePin(key: String) {
        val next = if (isPinned(key)) pinnedState.value - key else pinnedState.value + key
        pinnedState.value = next
        write(KEY_PINNED, next)
    }

    fun recordLaunch(key: String) {
        val next = (listOf(key) + recentState.value.filterNot { it == key }).take(MAX_RECENT)
        recentState.value = next
        write(KEY_RECENT, next)
    }

    private fun read(name: String): List<String> =
        prefs.getString(name, null).orEmpty().split('\n').filter { it.isNotBlank() }

    private fun write(name: String, keys: List<String>) {
        prefs.edit().putString(name, keys.joinToString("\n")).apply()
    }

    private companion object {
        const val PREFS_NAME = "foldos_hub_launch"
        const val KEY_PINNED = "pinned"
        const val KEY_RECENT = "recent"
        const val MAX_RECENT = 12
    }
}
