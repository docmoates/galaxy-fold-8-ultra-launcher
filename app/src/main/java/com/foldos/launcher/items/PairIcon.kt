package com.foldos.launcher.items

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.home.rememberHomeAppIconPainter
import com.foldos.launcher.theme.FoldOsTokens
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * The icon of a PAIR item: one icon-shaped tile split along the diagonal, app A in the top-left
 * half and app B in the bottom-right half (mock-up `.ic.pair`, drawn as a diagonal split so each
 * app keeps a recognisable corner of its icon). A missing app leaves its half tinted in the theme
 * highlight. Icon only; the home tile draws the label (see [pairTitle]).
 *
 * @param a app A (`pair.packageName` / `activityName`), null when it is no longer installed.
 * @param b app B (`pair.packageName2` / `activityName2`), null when it is no longer installed.
 * @param sizeDp the icon size in dp (`LauncherSettings.iconSizeDp`).
 */
@Composable
fun PairIcon(
    pair: HomeItem,
    a: AppInfo?,
    b: AppInfo?,
    sizeDp: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val iconSize: Dp = sizeDp.coerceAtLeast(24).dp
    val shape = itemCornerShape(iconSize, tokens)
    val sizePx = with(LocalDensity.current) { iconSize.roundToPx() }
    val dividerPx = with(LocalDensity.current) { 2.dp.toPx() }
    val painterA: Painter? = if (a != null) rememberHomeAppIconPainter(a, sizePx) else null
    val painterB: Painter? = if (b != null) rememberHomeAppIconPainter(b, sizePx) else null
    val topLeft = remember {
        GenericShape { size, _ ->
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(0f, size.height)
            close()
        }
    }
    val bottomRight = remember {
        GenericShape { size, _ ->
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
    val description = pairTitle(pair, a, b)

    Box(
        modifier = modifier
            .size(iconSize)
            .clip(shape)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder, shape)
            .semantics { contentDescription = "$description, app pair" },
    ) {
        PairHalf(painterA, a?.label, topLeft, Alignment.TopStart, iconSize, tokens)
        PairHalf(painterB, b?.label, bottomRight, Alignment.BottomEnd, iconSize, tokens)
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = tokens.onSurface.copy(alpha = 0.22f),
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = dividerPx,
            )
        }
    }
}

/** One triangular half: the app icon at 74% anchored into its corner, or a highlight tint when missing. */
@Composable
private fun PairHalf(
    painter: Painter?,
    label: String?,
    clip: Shape,
    anchor: Alignment,
    iconSize: Dp,
    tokens: FoldOsTokens,
) {
    Box(Modifier.fillMaxSize().clip(clip)) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = label,
                modifier = Modifier
                    .align(anchor)
                    .padding(iconSize * 0.04f)
                    .size(iconSize * 0.74f),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(Modifier.fillMaxSize().background(tokens.highlight))
        }
    }
}
