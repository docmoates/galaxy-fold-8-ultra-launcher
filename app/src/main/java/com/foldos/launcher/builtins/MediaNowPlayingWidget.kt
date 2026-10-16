package com.foldos.launcher.builtins

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * Now Playing for any media session (mock-up `wgRender` kind `media`). Needs notification access
 * for `MediaSessionManager.getActiveSessions`; until granted it shows an "Allow notification
 * access" button that opens the system listener settings. Play / pause / next / previous go
 * through `MediaController.transportControls`.
 */
@Composable
fun MediaNowPlayingWidget(spanX: Int, spanY: Int, modifier: Modifier) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val iw = maxWidth.value
        val ih = maxHeight.value
        val context = LocalContext.current
        val resumeTick = rememberResumeTick()
        val enabled = remember(resumeTick) { FoldOsNotificationListener.isEnabled(context) }
        if (!enabled) {
            WidgetPermissionPrompt(
                message = "Now Playing needs notification access to see what is playing.",
                buttonText = "Allow notification access",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                one = one,
            )
            return@BuiltInCard
        }

        val nowPlaying = rememberNowPlaying(enabled = true)
        val tick = rememberTicker(1000L)
        val progress = nowPlaying?.progressAt(tick) ?: 0f
        val title = nowPlaying?.title ?: "Nothing playing"
        val subtitle = nowPlaying?.artist?.takeIf { it.isNotBlank() } ?: nowPlaying?.appLabel ?: "Start something in any app"
        val playing = nowPlaying?.playing == true
        val onToggle: () -> Unit = { nowPlaying?.togglePlayPause() }
        val onNext: () -> Unit = { nowPlaying?.skipToNext() }
        val onPrevious: () -> Unit = { nowPlaying?.skipToPrevious() }

        if (one) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AlbumArt(nowPlaying?.art, ih.dp, radius = 10.dp)
                if (spanX >= 2) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        WidgetTitleText(title, size = 13.5.sp)
                        WidgetDimText(subtitle)
                        if (spanX >= 4) WidgetBar(progress)
                    }
                }
                if (spanX >= 4) {
                    TransportControls(playing, onPrevious, onToggle, onNext, small = true)
                } else {
                    PlayPauseButton(playing, onToggle, size = 34.dp)
                }
            }
        } else if (spanX >= 3) {
            val d = minDp(ih, iw * 0.42f)
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AlbumArt(nowPlaying?.art, d.dp, radius = 16.dp)
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    WidgetHeader(Icons.Outlined.MusicNote, nowPlaying?.appLabel ?: "Now playing")
                    Column {
                        WidgetTitleText(title, size = 16.sp)
                        WidgetDimText(subtitle, size = 13.sp)
                    }
                    WidgetBar(progress)
                    TransportControls(playing, onPrevious, onToggle, onNext, small = spanY < 3)
                }
            }
        } else {
            val d = minDp(iw, ih - 74f).coerceAtLeast(40f)
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                AlbumArt(nowPlaying?.art, d.dp, radius = 14.dp)
                Column(Modifier.fillMaxWidth()) {
                    WidgetTitleText(title, size = 13.5.sp)
                    WidgetDimText(subtitle)
                }
                WidgetBar(progress)
                TransportControls(playing, onPrevious, onToggle, onNext, small = true)
            }
        }
    }
}

/** Album art, or the mock-up's warm gradient placeholder (`.art`) when the session has none. */
@Composable
private fun AlbumArt(art: ImageBitmap?, size: Dp, radius: Dp) {
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFF6A59), Color(0xFFC1357A), Color(0xFF3B2A8C)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(bitmap = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(size * 0.4f),
            )
        }
    }
}

/** Accent circle with play or pause (mock-up `.ctl .pp`). */
@Composable
private fun PlayPauseButton(playing: Boolean, onToggle: () -> Unit, size: Dp) {
    val tokens = LocalFoldOsTokens.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(tokens.accent)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = tokens.onAccent,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}
