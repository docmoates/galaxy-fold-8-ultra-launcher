package com.foldos.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * Dots for the home pages plus a hub icon slot on the hub's side (mock-up `dots()`).
 *
 * @param currentPage index of the active home page, or -1 when the hub page is showing.
 */
@Composable
fun HomePageIndicator(
    pageCount: Int,
    currentPage: Int,
    hubOn: Boolean,
    hubOnLeft: Boolean,
    hubSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val total = pageCount.coerceAtLeast(1)
    val spoken = when {
        hubSelected -> "Hub page"
        else -> "Home page ${currentPage.coerceAtLeast(0) + 1} of $total"
    }
    Row(
        // One announced element ("Home page 2 of 3"); page changes are read out politely.
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hubOn && hubOnLeft) {
            HomeHubIndicatorSlot(selected = hubSelected, accent = tokens.accent, idle = tokens.onSurface)
        }
        repeat(pageCount.coerceAtLeast(1)) { index ->
            val alpha = if (index == currentPage) 1f else 0.4f
            Box(
                Modifier
                    .size(7.dp)
                    .background(tokens.onSurface.copy(alpha = alpha), CircleShape),
            )
        }
        if (hubOn && !hubOnLeft) {
            HomeHubIndicatorSlot(selected = hubSelected, accent = tokens.accent, idle = tokens.onSurface)
        }
    }
}

@Composable
private fun HomeHubIndicatorSlot(selected: Boolean, accent: Color, idle: Color) {
    Icon(
        imageVector = Icons.Rounded.Dashboard,
        contentDescription = "Hub",
        tint = if (selected) accent else idle.copy(alpha = 0.45f),
        modifier = Modifier.size(14.dp),
    )
}
