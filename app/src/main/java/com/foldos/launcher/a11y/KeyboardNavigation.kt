package com.foldos.launcher.a11y

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * Hardware-keyboard hooks the host (Integrate) provides through [LocalKeyboardShortcuts].
 *
 * @property onTypeToSearch called with the typed character (as a string) when a printable key is
 *   pressed on the home screen with nothing else handling it; the host opens universal search with
 *   that text as the first character of the query. Null disables type-to-search.
 */
@Immutable
data class KeyboardShortcuts(
    val onTypeToSearch: ((initialText: String) -> Unit)? = null,
)

/** Provided around `HomeScreen` by the Integrate agent; the default has every shortcut off. */
val LocalKeyboardShortcuts: ProvidableCompositionLocal<KeyboardShortcuts> =
    compositionLocalOf { KeyboardShortcuts() }

/** The printable character of a key-down [event] typed without Ctrl / Alt / Meta, or null. */
fun typedCharacterOf(event: KeyEvent): String? {
    if (event.type != KeyEventType.KeyDown) return null
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return null
    val codePoint = event.utf16CodePoint
    if (codePoint <= 0 || !Character.isValidCodePoint(codePoint)) return null
    if (!Character.isLetterOrDigit(codePoint)) return null
    return String(Character.toChars(codePoint))
}

/**
 * Type-to-search for the home screen: while this node (or nothing inside it that handles keys) has
 * focus, a printable key calls `LocalKeyboardShortcuts.current.onTypeToSearch` with the character.
 * The node requests focus when it enters the composition and again whenever [refocusKey] changes
 * (pass something that changes when an overlay closes), unless focus is already inside it. Returns
 * the modifier unchanged when no
 * handler is provided.
 */
@Composable
fun Modifier.a11yTypeToSearch(refocusKey: Any? = null): Modifier {
    val handler = LocalKeyboardShortcuts.current.onTypeToSearch
    val latest by rememberUpdatedState(handler)
    val requester = remember { FocusRequester() }
    // True while this node or anything inside it (a Hub text field, a focused tile) has focus.
    val holdsFocus = remember { booleanArrayOf(false) }
    val active = handler != null
    LaunchedEffect(active, refocusKey) {
        // Do not pull focus away from a text input or tile inside this node.
        if (active && !holdsFocus[0]) runCatching { requester.requestFocus() }
    }
    if (!active) {
        holdsFocus[0] = false
        return this
    }
    return this
        .onKeyEvent { event ->
            val text = typedCharacterOf(event)
            val callback = latest
            if (text != null && callback != null) {
                callback(text)
                true
            } else {
                false
            }
        }
        .onFocusChanged { holdsFocus[0] = it.hasFocus }
        .focusRequester(requester)
        .focusTarget()
}

/**
 * Arrow keys move focus between the focusable children of this node (drawer grid, dock, home
 * tiles): a key-down of Left / Right / Up / Down moves focus in that direction and is consumed
 * when focus moved. Tab and Shift+Tab keep the platform behavior.
 */
@Composable
fun Modifier.a11yDirectionalFocus(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            false
        } else {
            val direction = when (event.key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> null
            }
            direction != null && focusManager.moveFocus(direction)
        }
    }
}

/**
 * Makes a pointer-gesture tile reachable by keyboard / D-pad: focusable, a 2dp accent ring while
 * focused, Enter / Space / D-pad center call [onActivate], and the Menu key or Shift+F10 call
 * [onMenu] (the long-press menu) when given. Tiles built on `clickable` already have all of this.
 */
@Composable
fun Modifier.a11yFocusTile(
    onActivate: () -> Unit,
    onMenu: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val activate by rememberUpdatedState(onActivate)
    val menu by rememberUpdatedState(onMenu)
    val ring = LocalFoldOsTokens.current.accent
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else if (
                event.key == Key.Enter || event.key == Key.NumPadEnter ||
                event.key == Key.DirectionCenter || event.key == Key.Spacebar
            ) {
                activate()
                true
            } else if (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed)) {
                val open = menu
                if (open != null) {
                    open()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        .then(if (focused) Modifier.border(2.dp, ring, shape) else Modifier)
        .focusable(interactionSource = source)
}
