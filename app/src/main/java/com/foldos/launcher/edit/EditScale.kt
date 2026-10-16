package com.foldos.launcher.edit

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yDuration
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlin.math.min

/** The home is drawn at this scale while edit mode is up (mock-up `.editing .home`). */
const val EDIT_SCALE: Float = 0.76f

/** Height of the edit top bar below the status bar inset (style control + page strip). */
val EditTopBarHeight: Dp = 104.dp

/** Height of the edit bottom bar above the navigation bar inset (the action row). */
val EditBottomBarHeight: Dp = 96.dp

/**
 * Pixels reserved by the two edit bars: [topPx] from the window top edge down to the bottom of the
 * top bar (system status inset included), [bottomPx] from the window bottom edge up to the top of
 * the bottom bar (navigation inset included). The home is scaled into the space between them.
 */
@Immutable
data class EditChrome(val topPx: Int, val bottomPx: Int) {
    companion object {
        val None: EditChrome = EditChrome(0, 0)
    }
}

/**
 * How the home is placed in edit mode: drawn at [scale] with its top-left corner moved by
 * ([translationX], [translationY]) window px. A point `p` of the unscaled home appears at
 * `p * scale + translation`.
 */
@Immutable
data class EditFrame(val scale: Float, val translationX: Float, val translationY: Float) {
    /** Where the unscaled home point [p] (px) is drawn. */
    fun toScreen(p: Offset): Offset = Offset(p.x * scale + translationX, p.y * scale + translationY)

    /** The unscaled home point (px) that is drawn at the screen point [p]. */
    fun toUnscaled(p: Offset): Offset = Offset((p.x - translationX) / scale, (p.y - translationY) / scale)

    companion object {
        val Identity: EditFrame = EditFrame(1f, 0f, 0f)
    }
}

/**
 * The frame that centres a window of [widthPx] x [heightPx] at [EDIT_SCALE] (or smaller, when the
 * space between the bars is shorter) in the space between the edit bars of [chrome].
 */
fun editFrameFor(widthPx: Float, heightPx: Float, chrome: EditChrome): EditFrame {
    if (widthPx <= 0f || heightPx <= 0f) return EditFrame.Identity
    val availableH = (heightPx - chrome.topPx - chrome.bottomPx).coerceAtLeast(1f)
    val scale = min(EDIT_SCALE, availableH / heightPx).coerceAtLeast(0.3f)
    val tx = (widthPx - widthPx * scale) / 2f
    val ty = chrome.topPx + (availableH - heightPx * scale) / 2f
    return EditFrame(scale, tx, ty)
}

/**
 * The bar heights of the edit overlay in px for the current window insets. [EditModeOverlay] lays
 * its bars out at exactly [EditTopBarHeight] / [EditBottomBarHeight] below / above the system bar
 * insets, so this equals what the overlay measures (it also reports it through `onChromeMeasured`).
 */
@Composable
fun rememberEditChrome(): EditChrome {
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val top = insets.getTop(density) + with(density) { EditTopBarHeight.roundToPx() }
    val bottom = insets.getBottom(density) + with(density) { EditBottomBarHeight.roundToPx() }
    return EditChrome(top, bottom)
}

/**
 * The frame the home is drawn with while edit mode is active, [EditFrame.Identity] otherwise. Provided
 * by [EditScaledHome]. `HomeCellGrid` must subtract it from `positionInWindow()` (see
 * [EditFrame.toUnscaled]) before publishing the grid geometry, so the geometry stays in unscaled
 * window dp.
 */
val LocalEditFrame: ProvidableCompositionLocal<EditFrame> = compositionLocalOf { EditFrame.Identity }

/**
 * Holds the layout coordinates of the home content inside [EditScaledHome], in the UNSCALED space
 * (the node sits inside the scaling layer, so its coordinates ignore the scale). Plain field, not
 * snapshot state: it is written from `onGloballyPositioned` and read when a grid publishes its geometry.
 */
class EditHomeRoot {
    var coordinates: LayoutCoordinates? = null
}

/**
 * The [EditHomeRoot] of the enclosing [EditScaledHome], null outside one. `HomeCellGrid` measures its
 * origin relative to it (`localPositionOf`), which gives the unscaled window position of the grid
 * at any moment of the edit-mode scale animation, so the published geometry never depends on the scale.
 */
val LocalEditHomeRoot: ProvidableCompositionLocal<EditHomeRoot?> = compositionLocalOf { null }

/**
 * Wraps the home in edit mode: while [active] the content animates down to [EDIT_SCALE] and is
 * centred between the edit bars (rounded, outlined in the accent colour, like the mock-up), and
 * [LocalEditFrame] carries the settled frame. Inactive it is a plain full-size `Box`.
 *
 * Put the wallpaper outside (behind) it and compose [EditModeOverlay] after it in the same parent,
 * both full size, so the overlay's bars and the home's frame use the same window.
 */
@Composable
fun EditScaledHome(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val density = LocalDensity.current
    val chrome = rememberEditChrome()
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = a11yDuration(260), easing = FastOutSlowInEasing),
        label = "editScale",
    )
    var size by remember { mutableStateOf(IntSize.Zero) }
    val target = remember(size, chrome) { editFrameFor(size.width.toFloat(), size.height.toFloat(), chrome) }
    val cornerPx = with(density) { (tokens.radius + 10.dp).toPx() }
    val shape = remember(cornerPx) { RoundedCornerShape(cornerPx) }
    val outline = tokens.accent

    val homeRoot = remember { EditHomeRoot() }
    Box(modifier = modifier.fillMaxSize().onSizeChanged { size = it }) {
        CompositionLocalProvider(
            LocalEditFrame provides (if (active) target else EditFrame.Identity),
            LocalEditHomeRoot provides homeRoot,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress
                        val s = 1f + (target.scale - 1f) * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = target.translationX * p
                        translationY = target.translationY * p
                        this.shape = shape
                        clip = p > 0f
                    }
                    .drawWithContent {
                        drawContent()
                        val p = progress
                        if (p > 0f) {
                            val w = 3.dp.toPx()
                            drawRoundRect(
                                color = outline.copy(alpha = p),
                                topLeft = Offset(w / 2f, w / 2f),
                                size = Size(this.size.width - w, this.size.height - w),
                                cornerRadius = CornerRadius(cornerPx),
                                style = Stroke(width = w),
                            )
                        }
                    }
                    // Inside the scaling layer: these coordinates are the unscaled home.
                    .onGloballyPositioned { homeRoot.coordinates = it },
            ) {
                content()
            }
        }
    }
}
