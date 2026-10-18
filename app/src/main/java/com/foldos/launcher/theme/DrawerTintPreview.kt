package com.foldos.launcher.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.DrawerTint
import com.foldos.launcher.core.model.LauncherSettings

/**
 * Small preview chip for Settings > Home > Pop-up color: the wallpaper of the current look with a pop-up panel on
 * it, filled the way [tint] would fill the real pop-up (search pill and a few app dots in the panel's text color).
 */
@Composable
fun DrawerTintPreviewChip(base: LauncherSettings, tint: DrawerTint, modifier: Modifier = Modifier) {
    FoldOsTheme(settings = base) {
        val tokens = LocalFoldOsTokens.current
        val colors = tokens.drawerPanelColors(tint, base.highContrast)
        Box(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
            Wallpaper()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.panel)
                    .border(1.dp, tokens.surfaceBorder, RoundedCornerShape(8.dp))
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.onPanel.copy(alpha = 0.3f), CircleShape),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { Box(Modifier.size(7.dp).background(colors.onPanel.copy(alpha = 0.85f), CircleShape)) }
                }
            }
        }
    }
}
