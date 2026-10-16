package com.foldos.launcher.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/** Width of the cover screen in dp (mock-up `DEV.cover.w`); every preview is laid out at this width. */
val PREVIEW_COVER_WIDTH: Dp = Dp(411f)

/** Height previews lay a whole cover home out at: tall enough for a search bar, four rows and a dock. */
val PREVIEW_COVER_HEIGHT: Dp = Dp(700f)

/** Height of the dock-only strip (mock-up `miniDock`: 190px). */
val PREVIEW_DOCK_STRIP_HEIGHT: Dp = Dp(190f)

/**
 * Renders [content] laid out at [virtualWidth] x [virtualHeight] dp and scales it down to fit the
 * width of the box it is given (mock-up `.mprev` + `transform: scale()`): real composables, real
 * text, just smaller. The box's height comes from [modifier]; anything that does not fit is
 * cropped at the bottom, like the mock-up's 250px-tall wizard cards.
 *
 * The scaled content keeps its own pointer handling, so callers that want the preview to act as
 * one tap target place a clickable overlay above it (see `WizardPreviewCard`).
 */
@Composable
fun ScaledPreview(
    virtualWidth: Dp,
    virtualHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.TopCenter) {
        val boundedWidth = if (maxWidth == Dp.Infinity || maxWidth.value <= 0f) virtualWidth else maxWidth
        val scale: Float = boundedWidth / virtualWidth
        Box(
            modifier = Modifier
                .size(virtualWidth * scale, virtualHeight * scale)
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .requiredSize(virtualWidth, virtualHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            // The preview is a fixed 411dp design: the system font scale (large on the inner screen) would make
            // its text outgrow the cards and clip, so it is laid out at font scale 1.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                content()
            }
        }
    }
}
