package com.foldos.launcher.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.theme.FoldOsTokens
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * The icon of a FOLDER item on the home grid (icon only; the home tile draws the label).
 *
 * - [LauncherSettings.FOLDER_GRID] (default): a surface tile with the first four children as a
 *   2x2 grid of mini icons (mock-up `.fic`); empty slots are accent dots.
 * - [LauncherSettings.FOLDER_BIG]: a 2x2 live mini-grid: the first four children drawn at half
 *   the icon size each, no frame, so the folder reads like four small app icons (One UI "big folder").
 * - [LauncherSettings.FOLDER_LIST]: a stacked look: two cards peeking out behind a front card that
 *   carries the first child's icon and the item count.
 *
 * @param children the folder's contents from `LayoutRepository.children(folder.id)`, in order.
 * @param apps installed apps keyed by `AppInfo.key` (the home grid's `appsByKey`).
 * @param sizeDp the icon size in dp (`LauncherSettings.iconSizeDp`).
 * @param style the resolved style: `folder.folderStyle ?: settings.folderStyle` (see [Folders.styleFor]).
 */
@Composable
fun FolderIcon(
    folder: HomeItem,
    children: List<HomeItem>,
    apps: Map<String, AppInfo>,
    sizeDp: Int,
    style: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val size: Dp = sizeDp.coerceAtLeast(24).dp
    val preview: List<AppInfo> = remember(children, apps) {
        children
            .sortedWith(compareBy<HomeItem> { it.row }.thenBy { it.col })
            .mapNotNull { apps.firstAppOf(it) }
            .take(Folders.PREVIEW_COUNT)
    }
    val description = folder.title ?: Folders.DEFAULT_TITLE
    val described = modifier.semantics { contentDescription = "$description, folder with ${children.size} items" }
    when (style) {
        LauncherSettings.FOLDER_BIG -> FolderBigIcon(preview, size, tokens, described)
        LauncherSettings.FOLDER_LIST -> FolderStackIcon(preview, children.size, size, tokens, described)
        else -> FolderGridIcon(preview, size, tokens, described)
    }
}

/** Mock-up `.fic`: surface tile, 9/58 padding, 4/58 gap, four mini icons rounded 30%. */
@Composable
private fun FolderGridIcon(preview: List<AppInfo>, size: Dp, tokens: FoldOsTokens, modifier: Modifier) {
    val shape = itemCornerShape(size, tokens)
    val pad = size * 0.155f
    val gap = size * 0.07f
    val mini = ((size - pad * 2 - gap) / 2).coerceAtLeast(6.dp)
    Column(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder, shape)
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0 until 2) {
                    val app = preview.getOrNull(row * 2 + col)
                    if (app != null) {
                        ItemAppIcon(app = app, size = mini, cornerFraction = 0.3f)
                    } else {
                        Box(
                            Modifier
                                .size(mini)
                                .background(tokens.accent.copy(alpha = 0.35f), RoundedCornerShape(30)),
                        )
                    }
                }
            }
        }
    }
}

/** Four children at half size with a small gap and no frame: the folder looks like a live mini-grid. */
@Composable
private fun FolderBigIcon(preview: List<AppInfo>, size: Dp, tokens: FoldOsTokens, modifier: Modifier) {
    val gap = size * 0.06f
    val mini = ((size - gap) / 2).coerceAtLeast(8.dp)
    val slotShape = itemCornerShape(mini, tokens)
    Column(
        modifier = modifier.size(size),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0 until 2) {
                    val app = preview.getOrNull(row * 2 + col)
                    if (app != null) {
                        ItemAppIcon(app = app, size = mini)
                    } else {
                        Box(
                            Modifier
                                .size(mini)
                                .clip(slotShape)
                                .background(tokens.surface)
                                .border(1.dp, tokens.surfaceBorder, slotShape),
                        )
                    }
                }
            }
        }
    }
}

/** Two cards peeking out above a front card that shows the first child and the item count. */
@Composable
private fun FolderStackIcon(preview: List<AppInfo>, count: Int, size: Dp, tokens: FoldOsTokens, modifier: Modifier) {
    val shape = itemCornerShape(size, tokens)
    val cardHeight = size * 0.86f
    Box(modifier = modifier.size(size), contentAlignment = Alignment.BottomCenter) {
        // Back cards: scaled down and shifted up so their top edges show as two thin bands.
        StackCard(size, cardHeight, shape, tokens, scale = 0.78f, liftFraction = 0.14f, alpha = 0.45f)
        StackCard(size, cardHeight, shape, tokens, scale = 0.89f, liftFraction = 0.07f, alpha = 0.7f)
        Box(
            modifier = Modifier
                .size(width = size, height = cardHeight)
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.surfaceBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            val front = preview.firstOrNull()
            if (front != null) {
                ItemAppIcon(app = front, size = size * 0.56f)
            } else {
                Box(
                    Modifier
                        .size(size * 0.3f)
                        .background(tokens.accent.copy(alpha = 0.35f), RoundedCornerShape(30)),
                )
            }
            if (count > 1) {
                Text(
                    text = count.toString(),
                    color = tokens.onAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(size * 0.06f)
                        .background(tokens.accent, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun StackCard(
    size: Dp,
    cardHeight: Dp,
    shape: RoundedCornerShape,
    tokens: FoldOsTokens,
    scale: Float,
    liftFraction: Float,
    alpha: Float,
) {
    val cardHeightPx = cardHeight.value
    Box(
        Modifier
            .size(width = size, height = cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // Dp -> px: the layer scope's density applies the same factor as the host.
                translationY = -liftFraction * cardHeightPx * density
                this.alpha = alpha
            }
            .clip(shape)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder, shape),
    )
}
