package com.foldos.launcher.a11y

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Accessibility switches from `LauncherSettings`, provided at the window root by [A11yRoot] so any
 * composable can read them without being handed the whole settings object. All default to off, so
 * code that is composed outside [A11yRoot] (previews, tests) behaves like the stock launcher.
 */

/** `LauncherSettings.reduceMotion`: animated transitions are replaced by instant changes. */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** `LauncherSettings.highContrast`: the theme already boosts its tokens; this is for custom drawing. */
val LocalHighContrast: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** `LauncherSettings.greyscale`: the window root is rendered in greyscale (see [a11yGreyscale]). */
val LocalGreyscale: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** `LauncherSettings.largeIcons` (Easy mode): bigger labels and touch targets. */
val LocalLargeIcons: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
