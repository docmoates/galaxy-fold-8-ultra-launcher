package com.foldos.launcher.core.model

import kotlinx.serialization.Serializable

/** Which physical screen a layout or fold state refers to. */
@Serializable
enum class ScreenKind { COVER, INNER, FLEX }

/** Settings > Screens: how cover and inner layouts relate (mock-up `screenMode`). */
@Serializable
enum class ScreenMode { SAME, DIFFERENT, CONTINUITY }

/**
 * Starter layout (Round 2.0; formerly the per-page home style). Every page is one free-form grid
 * canvas now; a starter only decides what a page is SEEDED with (apps grid, loose widgets, an apps
 * card in list style, loose widgets over an app card). `LauncherSettings.styleCover` / `styleInner`
 * keep the last starter applied to that screen. The name is kept so stored rows and backups load.
 */
@Serializable
enum class HomeStyle { GRID, DASHBOARD, LIST, HYBRID }

/** Readable alias: the enum is a starter layout, not a rendering style. */
typealias StarterLayout = HomeStyle

/** Grid placement vs. free placement (mock-up `gridMode`). */
@Serializable
enum class GridMode { GRID, FREE }

/** Visual theme (mock-up `theme`: material, glass, oneui, bold). */
@Serializable
enum class ThemeId { MATERIAL, GLASS, ONEUI, BOLD }

/** Dark / light / auto (mock-up `mode`). */
@Serializable
enum class Appearance { DARK, LIGHT, AUTO }

/** App drawer style (mock-up `drawerStyle`: popup, full). */
@Serializable
enum class DrawerStyle { POPUP, FULL }

/**
 * Color of the app pop-up / drawer panel (Settings > Home > Pop-up color): [THEME] tints it with the chosen color
 * scheme, [NEUTRAL] keeps the plain theme surface.
 */
@Serializable
enum class DrawerTint { THEME, NEUTRAL }

/** Dock background trait (mock-up `bg`: pill, shelf, none, bubble, line, reflect). */
@Serializable
enum class DockBackground { PILL, SHELF, NONE, BUBBLE, LINE, REFLECT }

/** Dock position trait (mock-up `pos`: bottom, rail). */
@Serializable
enum class DockPosition { BOTTOM, RAIL }

/** Dock item content trait (mock-up `content`: icons, text). */
@Serializable
enum class DockContent { ICONS, TEXT }
