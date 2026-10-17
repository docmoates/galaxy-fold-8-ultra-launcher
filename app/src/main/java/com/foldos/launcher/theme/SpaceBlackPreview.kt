package com.foldos.launcher.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.LauncherSettings

/**
 * Live preview tile for Settings > Look > Space black and the edit-mode Themes sheet: the normal page (wallpaper and
 * Appearance of the current look, light or dark) with a solid black widget tile (white time and caption), a black
 * app panel and a black dock pill, drawn from the real tokens of `base` with [LauncherSettings.spaceBlack] forced on.
 */
@Composable
fun SpaceBlackPreviewTile(base: LauncherSettings, modifier: Modifier = Modifier) {
    FoldOsTheme(settings = base.copy(spaceBlack = true)) {
        // Page, wallpaper and Appearance stay normal; only the widget tile and the dock pill use the black tokens.
        val tokens = LocalFoldOsTokens.current.withSpaceBlack(base.highContrast)
        val card = RoundedCornerShape((tokens.radius / 2.5f).coerceAtLeast(3.dp))
        Box(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
            Wallpaper()
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(tokens.surface, card)
                        .border(1.dp, tokens.surfaceBorder, card)
                        .padding(5.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = "12:30", color = tokens.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(text = "Mon 16", color = SpaceBlackSecondaryText, fontSize = 8.sp, maxLines = 1)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(tokens.surface, CircleShape)
                        .border(1.dp, tokens.surfaceBorder, CircleShape),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    repeat(4) { index ->
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .padding(vertical = 3.dp)
                                .width(8.dp)
                                .background(
                                    if (index == 0) tokens.accent else tokens.onSurface.copy(alpha = 0.85f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}
