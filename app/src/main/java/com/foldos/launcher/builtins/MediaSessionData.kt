package com.foldos.launcher.builtins

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/** What the Now Playing widget shows for the active media session. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val appLabel: String,
    val playing: Boolean,
    val art: ImageBitmap?,
    val durationMillis: Long,
    private val positionMillis: Long,
    private val positionUpdatedAtMillis: Long,
    private val speed: Float,
    private val controller: MediaController?,
) {
    /** Playback progress in 0..1 at [nowMillis], extrapolated from the last state update. */
    fun progressAt(nowMillis: Long): Float {
        if (durationMillis <= 0L) return 0f
        val extra = if (playing) ((nowMillis - positionUpdatedAtMillis) * speed).toLong() else 0L
        return ((positionMillis + extra).toFloat() / durationMillis).coerceIn(0f, 1f)
    }

    fun togglePlayPause() {
        val controls = controller?.transportControls ?: return
        runCatching { if (playing) controls.pause() else controls.play() }
    }

    fun skipToNext() {
        runCatching { controller?.transportControls?.skipToNext() }
    }

    fun skipToPrevious() {
        runCatching { controller?.transportControls?.skipToPrevious() }
    }
}

/**
 * Observes the active media sessions through [MediaSessionManager] and keeps the most relevant one
 * (playing first, then the first listed) as state. Returns null when [enabled] is false, when no
 * session exists, or when the listener service has not been granted access.
 */
@Composable
internal fun rememberNowPlaying(enabled: Boolean): NowPlaying? {
    val context = LocalContext.current
    var nowPlaying by remember { mutableStateOf<NowPlaying?>(null) }

    DisposableEffect(enabled) {
        if (!enabled) {
            nowPlaying = null
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = FoldOsNotificationListener.componentName(context)
        var current: MediaController? = null
        var callback: MediaController.Callback? = null

        fun publish() {
            nowPlaying = current?.let { controller -> controller.toNowPlaying(context) }
        }

        fun attach(controllers: List<MediaController>?) {
            current?.let { c -> callback?.let { c.unregisterCallback(it) } }
            callback = null
            current = pickController(controllers)
            current?.let { controller ->
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                    override fun onSessionDestroyed() {
                        attach(runCatching { manager?.getActiveSessions(component) }.getOrNull())
                    }
                }
                callback = cb
                controller.registerCallback(cb)
            }
            publish()
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers -> attach(controllers) }
        val registered = runCatching {
            manager?.addOnActiveSessionsChangedListener(sessionsListener, component)
            attach(manager?.getActiveSessions(component))
        }.isSuccess
        if (!registered) nowPlaying = null

        onDispose {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            current?.let { c -> callback?.let { c.unregisterCallback(it) } }
        }
    }
    return nowPlaying
}

private fun pickController(controllers: List<MediaController>?): MediaController? {
    if (controllers.isNullOrEmpty()) return null
    return controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.first()
}

private fun MediaController.toNowPlaying(context: Context): NowPlaying {
    val meta: MediaMetadata? = runCatching { this.metadata }.getOrNull()
    val state: PlaybackState? = runCatching { this.playbackState }.getOrNull()
    val art: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    return NowPlaying(
        title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Nothing playing",
        artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: "",
        appLabel = appLabel(context, this.packageName),
        playing = state?.state == PlaybackState.STATE_PLAYING,
        art = art?.asImageBitmap(),
        durationMillis = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        positionMillis = state?.position ?: 0L,
        positionUpdatedAtMillis = if (state != null) System.currentTimeMillis() else 0L,
        speed = state?.playbackSpeed ?: 1f,
        controller = this,
    )
}

private fun appLabel(context: Context, packageName: String?): String {
    if (packageName.isNullOrEmpty()) return "Media"
    val pm: PackageManager = context.packageManager
    return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
        .getOrDefault(packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() })
}
