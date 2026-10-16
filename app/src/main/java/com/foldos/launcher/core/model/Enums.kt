package com.foldos.launcher.core.model

import kotlinx.serialization.Serializable

/** Which physical screen a layout or fold state refers to. */
@Serializable
enum class ScreenKind { COVER, INNER, FLEX }

/** Settings > Screens: how cover and inner layouts relate (mock-up `screenMode`). */
@Serializable
enum class ScreenMode { SAME, DIFFERENT, CONTINUITY }

/** Home style per page (mock-up `style.cover` / `style.inner`). */
@Serializable
enum class HomeStyle { GRID, DASHBOARD, LIST, HYBRID }

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

/** Dock background trait (mock-up `bg`: pill, shelf, none, bubble, line, reflect). */
@Serializable
enum class DockBackground { PILL, SHELF, NONE, BUBBLE, LINE, REFLECT }

/** Dock position trait (mock-up `pos`: bottom, rail). */
@Serializable
enum class DockPosition { BOTTOM, RAIL }

/** Dock item content trait (mock-up `content`: icons, text). */
@Serializable
enum class DockContent { ICONS, TEXT }
