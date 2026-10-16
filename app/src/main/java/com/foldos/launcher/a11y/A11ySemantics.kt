package com.foldos.launcher.a11y

import android.view.View
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/*
 * Semantics helpers shared by home, dock, drawer, settings, items, picker and hub. They only add
 * accessibility information; none of them changes what a tap or a long press does.
 */

/** Spoken name of an app: "Gmail", "Gmail, 1 notification" or "Gmail, 3 notifications". */
fun a11yAppLabel(label: String, badgeCount: Int): String = when {
    badgeCount <= 0 -> label
    badgeCount == 1 -> "$label, 1 notification"
    else -> "$label, $badgeCount notifications"
}

/**
 * Makes this node one TalkBack element named [label]: replaces the semantics of everything inside
 * it (so an icon's description and its visible label are not read twice), exposes [onActivate] as
 * the click action, [onMenu] as the long-click action and [customActions] in the actions menu.
 * Apply it OUTSIDE `clickable` / `pointerInput`; it does not make the node clickable by itself.
 *
 * @param stateDescription optional state read after the name ("Selected", "Running").
 * @param role defaults to [Role.Button].
 * @param selected non-null marks the element as selectable with that state (tabs, chips).
 */
fun Modifier.a11yButton(
    label: String,
    onActivate: () -> Unit,
    onMenu: (() -> Unit)? = null,
    menuLabel: String = "Open menu",
    activateLabel: String? = null,
    customActions: List<CustomAccessibilityAction> = emptyList(),
    stateDescription: String? = null,
    role: Role = Role.Button,
    selected: Boolean? = null,
): Modifier = this.clearAndSetSemantics {
    contentDescription = label
    this.role = role
    if (selected != null) this.selected = selected
    if (stateDescription != null) this.stateDescription = stateDescription
    onClick(label = activateLabel) {
        onActivate()
        true
    }
    if (onMenu != null) {
        onLongClick(label = menuLabel) {
            onMenu()
            true
        }
    }
    if (customActions.isNotEmpty()) this.customActions = customActions
}

/**
 * Names an icon-only control that already handles its own clicks (a `clickable` / `IconButton`
 * parent merges this description in). Use [a11yButton] when the node itself has no click semantics.
 */
fun Modifier.a11yLabel(label: String): Modifier = this.semantics { contentDescription = label }

/** Marks a section title as a heading so TalkBack can jump between sections. */
fun Modifier.a11yHeading(): Modifier = this.semantics { heading() }

/** Merges a list row's texts and icons into one TalkBack element. */
fun Modifier.a11yMergedRow(): Modifier = this.semantics(mergeDescendants = true) { }

/** Exposes a tab: [Role.Tab] with the selected state (read as "selected" / "not selected"). */
fun Modifier.a11yTab(selected: Boolean): Modifier = this.semantics {
    role = Role.Tab
    this.selected = selected
}

/** Exposes a filter chip / segmented choice: the selected state on top of its own click handling. */
fun Modifier.a11ySelectable(selected: Boolean, role: Role = Role.RadioButton): Modifier = this.semantics {
    this.role = role
    this.selected = selected
}

/** Announces changes of this node's text without moving focus (toasts, status lines, errors). */
fun Modifier.a11yLiveRegion(assertive: Boolean = false): Modifier = this.semantics {
    liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
}

/**
 * Grows the node to the 48dp minimum touch target, centering its content. Apply it BEFORE
 * `clickable` so the clickable area is the enlarged one. Wraps Material 3's
 * `minimumInteractiveComponentSize()`.
 */
fun Modifier.a11yMinTouchTarget(): Modifier = this.minimumInteractiveComponentSize()

/** Speaks [text] through TalkBack without moving focus; no-op when no screen reader is active. */
@Composable
fun rememberA11yAnnouncer(): (String) -> Unit {
    val view = LocalView.current
    return remember(view) { { text: String -> view.a11yAnnounce(text) } }
}

/** Speaks [text] through TalkBack without moving focus. */
fun View.a11yAnnounce(text: String) {
    @Suppress("DEPRECATION")
    announceForAccessibility(text)
}
