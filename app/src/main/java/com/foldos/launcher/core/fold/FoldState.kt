package com.foldos.launcher.core.fold

import android.graphics.Rect
import com.foldos.launcher.core.model.ScreenKind

/**
 * The current posture of the device, derived from Jetpack WindowManager by the Fold agent.
 *
 * @property kind COVER when folded, INNER when fully open, FLEX when half opened with the hinge
 *   splitting the inner screen.
 * @property isHalfOpened true while the FoldingFeature state is HALF_OPENED.
 * @property hingeBounds the hinge rectangle in window coordinates, or null when no folding
 *   feature is reported (cover screen, or not a foldable).
 * @property widthDp current window width in dp.
 * @property heightDp current window height in dp.
 */
data class FoldState(
    val kind: ScreenKind,
    val isHalfOpened: Boolean,
    val hingeBounds: Rect?,
    val widthDp: Int,
    val heightDp: Int,
)
