package com.foldos.launcher.settings

import android.util.Log
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/**
 * Applies settings transforms one at a time, in the order they were requested, per [SettingsRepository].
 * `appScope` is multi-threaded, so two quick taps launched as separate coroutines could be applied in
 * either order; a single consumer per repository per process keeps the last tap last. Used by the
 * Settings pages, the setup wizard and the edit-mode theme sheet.
 */
internal object SettingsWriteQueue {

    private const val TAG = "SettingsWriteQueue"

    private val queues = WeakHashMap<SettingsRepository, Channel<(LauncherSettings) -> LauncherSettings>>()

    /** Queues [transform]; [scope] must outlive the screen (the app graph's `appScope`). */
    fun enqueue(scope: CoroutineScope, repo: SettingsRepository, transform: (LauncherSettings) -> LauncherSettings) {
        val channel = synchronized(queues) {
            queues[repo] ?: Channel<(LauncherSettings) -> LauncherSettings>(Channel.UNLIMITED).also { created ->
                queues[repo] = created
                scope.launch {
                    for (next in created) {
                        try {
                            repo.update(next)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "settings update failed", e)
                        }
                    }
                }
            }
        }
        channel.trySend(transform)
    }
}
