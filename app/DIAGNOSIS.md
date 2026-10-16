# Fix round 1.1 diagnosis (device feedback on 1.0.0)

Read from the code only (no device). Paths are under `app/src/main/java/com/foldos/launcher/`. "Confirmed" means the
cause is visible in the source; "Likely" means the source shows the gap but the exact device trigger is inferred.

Agents: **Home** (`home/`), **Dock** (`dock/`), **Drawer** (`drawer/`), **Edit** (`edit/`), **Search** (`search/`),
**Settings** (`settings/`, plus the color step of `onboarding/SetupWizard.kt`), **Theme** (`theme/`), **Data** (`data/`),
**Hub** (`hub/`), **Integrate** (`LauncherRoot.kt`, `LauncherViewModel.kt`, `MainActivity.kt`, `AndroidManifest.xml`).
Shared contracts for 6 and 11 are already in `core/` (see `app/CONTRACTS.md`, "Fix round 1.1").

## 1. Two docks on the Hub page, everything scrunched (Confirmed)

- `home/HomeScreen.kt` `HomePagedLayout` (~L276-314): the `HorizontalPager` holds the hub as one page, but
  `HomePageIndicator(...)` and `dock()` are siblings of the pager in the same `Column`, so they are drawn on every page,
  the hub included.
- `hub/HubPage.kt` L136-145 adds its own floating tab bar (`HubTabBarRow`, 64dp + 14dp insets, bottom by default) on
  top of the content, with `contentPadding` reserving `HubTabBarInset + HubTabBarHeight + 16dp` at the bottom.
- Result on the hub: page indicator (27dp) + home dock (60-130dp) + Hub tab bar stacked at the bottom, the Hub content squeezed
  into what is left.
- Fix, owner **Home**: in `HomePagedLayout` hide the indicator and `dock()` while `pagerState.currentPage == hubIndex`
  (animate with `AnimatedVisibility` / `animateDpAsState` on the height so the pager grows to the full height). The
  indicator keeps its hub dot but is only shown on home pages. Flex mode has no hub page and is unaffected.
- Owner **Hub**: nothing structural; verify the tab bar clears the navigation bar (HomeScreen already pads
  `WindowInsets.systemBars`) and that Mail/AI tab content uses the freed height.

## 2. "Swipe up for apps" does nothing (Likely, with one Confirmed gap)

- Confirmed: the default cover dock (`DockConfig()` = floating pill, no add-ons) has no apps button at all.
  `DockEntry.AllApps` is only added by the taskbar add-on (`dock/Dock.kt` L183-184) and the handle add-on is off, so on
  the cover the drawer is reachable only by the swipe.
- The swipe detector (`gestures/HomeGestures.kt`, installed through `HomeScreen.pageModifier` and
  `home/HomeScreen.kt` `HomePageColumn` L318-340) is attached to each page column only. A launcher swipe normally starts on the
  dock / page-indicator strip (bottom ~100-160dp), which is outside every page column, so nothing sees it. The
  `swipeUpOpensDrawer` fallback in `HomeSupport.kt` L320 has the same reach.
- The detector additionally abandons the gesture whenever a child consumed a move (`takenByChild`, L185), so a start on a
  widget, a stack (`HomeStackSlot` flips by swipe) or a hosted widget view is lost.
- Fix:
  - **Home**: attach `pageModifier` (and the Phase 1 fallback) to the root `Box` of `HomeScreen` so it covers pager,
    page indicator and dock (still excluding the hub page: the hub content scrolls vertically).
  - **Dock**: always include an "All apps" button (`DockEntry.AllApps`) in every dock config, not only with the taskbar
    add-on; with the taskbar add-on keep the existing one.
  - **Drawer**: confirm the open animation works from the state change (it does: `viewModel.openDrawer()` is wired in
    `LauncherRoot.kt` and `GestureActions.perform(TYPE_DRAWER)`).
  - **Integrate**: no change except passing the same modifier; keep `swipeUpOpensDrawer = !gesturesOn`.

## 3. Long-press on the home screen does not open edit mode ("add widget", "delete page") (Likely)

- Wiring is correct for empty space: `HomeCellGrid` `onEmptyLongPress` -> `HomeContext.emptyLongPress` ->
  `HomeCallbacks.onEmptyLongPress` -> `LauncherViewModel.enterEditMode()` (`LauncherRoot.kt` L168-173).
- But the grid fills the page. Almost every touch lands on a tile; a long press on an app tile or widget goes through
  `HomeContext.longPress` (`HomeSupport.kt` L157-165) which, outside edit mode, opens the item popup / context menu
  (`HomeItemPopup`) and not edit mode. On a dashboard page (inner default) widgets cover everything. The "Edit" entry only exists
  inside that popup.
- When edit mode does open, its chrome is drawn over an unscaled home (problem 9), so it reads as broken.
- Fix:
  - **Home / Integrate**: a long press on any tile, widget or empty space must enter edit mode (item long press = enter
    edit mode with that item selected); the app tile popup (notification preview, shortcuts, add to pair, hide) is shown
    by tapping the selected item in edit mode, or as the first long-press frame before the lift. Pick one and keep it
    consistent for apps, folders, pairs and widgets.
  - **Edit**: after fixing 9, the entry bar must show Widgets, Wallpaper, Themes, Dock, Settings plus Add page / Delete page.
  - The pinch-in gesture and the Home Settings page already offer edit mode as a second entry.

## 4. First run creates three identical home pages (Confirmed)

- `data/LayoutRepositoryImpl.kt`: `DEFAULT_PAGE_COUNT = 3` (L336) and `ensureDefaultLayout` L47-50 inserts 3 pages per screen.
- L72-79 then fills every empty DASHBOARD page with `defaultDashboardItems(screen, page.id)`, so the inner/flex
  screens get three dashboards with the same widgets. It also runs on every launch: a dashboard page the user emptied
  or restyled is re-filled with the defaults.
- Fix, owner **Data**: seed exactly one page per screen (`DEFAULT_PAGE_COUNT = 1`); seed the dashboard widgets only when
  the screen has no items at all (same guard as the grid apps, `countItemsForScreen == 0`), never on later launches.
  One-time cleanup for devices that already have the duplicates: on the first `ensureDefaultLayout` after the update delete
  trailing pages (index > 0) of a screen whose item signature (kind, builtinId / package, col, row, spans) equals page 0's,
  or that are empty, but never touch a page the user changed. `LauncherViewModel.seedDashboardIfEmpty` (page restyled to
  dashboard) stays.

## 5. Settings too complicated: toggles and dropdowns instead of samples to pick (Confirmed)

- 16 root pages (`settings/SettingsRootPage.kt` `SettingsPages.all`), built from `ToggleRow` / `SelectRow` / `ChipsRow` /
  `SliderRow` / `ColorRow` (hex fields, `settings/SettingsRows.kt`) and an "Advanced" switch. `LookPage.kt` is a chip row of
  theme names, a chip row of scheme names, three hex color fields and a corner-radius slider.
- The setup wizard already shows live samples (`onboarding/SampleHomePreview.kt` `SampleHomePreview` /
  `SampleDockPreview`, `WizardPieces.kt` `WizardPreviewCard`, `ColorSwatch`), and `theme/ThemePreviews.kt`; Settings does not use them.
- Fix, owner **Settings**: rebuild the primary path as picking samples. Settings root = a short list of visual
  categories (Theme, Colors, Home layout, Dock, Icons, Hub, Wallpaper); each page is a grid of tappable sample cards,
  the current one outlined, one tap applies it. No switches, dropdowns, sliders or hex fields on those pages. Samples:
  - Theme: 4 live theme previews (`SampleHomePreview` with `ThemeId`).
  - Colors: one swatch pair (light + dark) per `ColorSchemes.families` (problem 6), plus "Theme colors".
  - Home layout: the 4 `HomeStyle` previews; density samples (columns/icon size) as 3 tiles (Compact / Comfortable / Large) instead of sliders.
  - Dock: the 20 `DockPresets` as `SampleDockPreview` cards (already in `DockPage`; keep the preset tiles, drop the trait toggles to "Customize").
  - Icons: shape samples (`IconShape`) and the existing icon-pack picker.
  - Hub: sample cards for tab bar top/bottom and left/right position.
  - Everything else (Gestures, Search providers, Profiles, Mail, AI, Backup, Accessibility, Features, About) moves under one
    "More settings" entry; those pages may keep their toggles. `SettingsRowInfo` search keeps working over all pages.
  - Reuse, do not duplicate: `internal` composables in `onboarding/` are visible module-wide.

## 6. Colors need a light and a dark version of every primary color (Confirmed)

- `core/model/ColorSchemeConfig.kt` had 6 single-triple presets (ocean, forest, sunset, rose, gold, mono).
  `theme/ThemeTokens.kt` `recolor` only clamps the one accent lightness (>= 66% in dark UI, <= 42% in light UI).
- Done in the contracts (this round): `ColorSchemes.families` (9 primary colors: ocean, forest, sunset, rose, gold, violet,
  teal, red, mono), each a `ColorFamily(id, name, light, dark)`; preset ids are `"<family>-dark"` / `"<family>-light"`;
  old ids (`"ocean"`) still resolve to the dark version (`ColorSchemes.byId`); `ColorSchemes.presets` now lists both
  versions of every family.
- Owner **Theme**: in `tokensFor`/`recolor` a `ColorTone.LIGHT` preset gives pale accent + pale wallpaper and `DARK` deep
  accent + deep wallpaper; for presets do not run the lightness clamp, derive `onAccent`/`onHighlight` from luminance so
  text stays legible in both Appearance modes (the clamp stays for `SCHEME_CUSTOM`).
- Owners **Settings** (Colors page), **Edit** (`edit/ThemeQuickSheet.kt` L127 lists `ColorSchemes.presets`),
  **Settings** for `onboarding/SetupWizard.kt` L291 (wizard color step): show families as a light/dark swatch pair,
  selection compares `colors.scheme` with the exact preset id (treat a legacy id as its `-dark` version).

## 7. Opening search and typing: the top of the UI slides down, old UI shows as a strip at the status bar (Confirmed)

- `search/UniversalSearch.kt` L159-178: the sheet is an `AnimatedVisibility` with `slideInVertically { -it / 3 }` where `it` is the height of
  the *full-screen* `SearchSheet` (`BoxWithConstraints(Modifier.fillMaxSize())`), so the whole panel travels a third of the
  screen height; the exit mirrors it. The sheet content itself is inset by `WindowInsets.statusBars` + 10dp (L292-293) over a
  translucent 25-45% scrim (L155), so the old home stays visible in the status bar strip above and beside the sheet.
- No `imePadding()` and no `windowSoftInputMode` on `MainActivity` (manifest L94-108): when the keyboard opens the results list is not
  laid out against the keyboard.
- Fix, owner **Search**: make search a full-screen takeover: opaque (theme `drawerScrim`/surface) background drawn to the window
  edges including under the status bar, the field pinned at the top (status bar inset as padding inside the surface), fade-only
  enter/exit (no slide), `Modifier.imePadding()` on the results list, `weight(1f)` results filling the space above the keyboard.
  Owner **Integrate**: set `android:windowSoftInputMode="adjustResize"` on `MainActivity` (final decision: `adjustNothing` may stop the IME inset reaching Compose; edge-to-edge means the window is not resized on API 30+) so the system never pans/resizes the
  launcher window (insets are handled in Compose).

## 8. "Remove widget" does nothing (Likely)

- `edit/EditModeOverlay.kt` L309-317: the X chip (`EditDeleteChip`) is centred on the top edge of the selected item
  (`rect.center.x, rect.top`). The top chrome `Column` (L324-391: style segments, Done, page chips) is composed after it and, with the
  unscaled home (problem 9), covers y = status bar + 46dp ... ~+130dp across the full width. A widget in the first rows
  (dashboard: the hero widget spans the width) has its chip under the segmented control / page chips, which take the tap.
- The delete path itself is sound: `LauncherRoot.kt` L368 -> `LauncherViewModel.deleteItem` -> `widgetHost.deleteId` +
  `layouts.deleteItem`.
- Fix, owner **Edit**: with problem 9's layout fix (home scaled down inside a reserved frame) the chip no longer sits under
  the chrome; additionally draw the chip inside the item's bounds (top-right inset, 28dp) and put the selected-item actions (Remove,
  Resize) in the bottom bar as well. Add a test hit: Remove must work for every row.

## 9. "Edit widget" shows a pile of buttons overlapping the home screen (Confirmed)

- `edit/EditModeOverlay.kt` documents "drawn on top of the home content that the caller has already scaled to 76%", but
  `LauncherRoot.kt` L283-345 never scales or insets the home in edit mode. The chrome (top segments + Done + page chips L324-391,
  bottom `EditActionBar` L394-408 at 26dp from the bottom) is therefore painted over the live search bar, first icon row, page
  indicator and dock.
- Fix:
  - **Edit**: export the frame: `const val EDIT_SCALE = 0.76f` and `fun Modifier.editModeScale(active: Boolean): Modifier`
    (animated `graphicsLayer`, pivot top-centre, translation so the scaled home sits between the top and bottom chrome),
    and make `EditGridPx.from(geometry, density)` account for the scale (`cellW`/`cellH` and origin are measured by the home
    in unscaled px; the overlay must scale them the same way, or map pointers back to unscaled space).
  - **Integrate**: apply `Modifier.editModeScale(editMode)` to the `Box` that wraps `Wallpaper` + `HomeScreen` (the one with `backdrop`),
    not to the overlay.
  - **Home**: hide the live search bar / dock hit targets while editing (`HomeScreen.editMode` is already passed) so nothing underneath takes touches.

## 10. Duplicate search at the top when the dock has the search add-on (Confirmed)

- `home/HomeScreen.kt` L339 (`HomePageColumn`) and `home/HomeFlexLayout.kt` L53 draw `searchBar()` on every page, and
  `LauncherRoot.kt` L309 passes the search bar whenever `FEATURE_SEARCH` is on. `dock/Dock.kt` L365 additionally draws
  `DockSearchBar` when `DockConfig.search` is set. Nothing connects the two.
- Fix, owner **Integrate**: pass `searchBar = null` when `dockOn && !railDock && dockConfig.search` (the dock's bar is the search
  entry). Everything else (type-to-search, gestures) is untouched.

## 11. No way to change the apps on the dock or home screen (Confirmed)

- The dock apps come from `defaultDockApps(apps, n)` (`LauncherRoot.kt` L141-143), a keyword heuristic, with no persistence.
  Home items are seeded once by `ensureDefaultLayout`; nothing adds an app afterwards (the drawer tile only has `clickable`,
  `drawer/DrawerContent.kt` L250; the long-press popup has no "add to home"). `DrawerCategory` chips are stubs
  (`filter` returns every app, `drawer/DrawerCategory.kt`), so there are no group folders in the drawer.
- Wanted: an app pop-up (the drawer, with group folders) whose apps can be dragged to a home page cell or to the dock.
- Contracts added now (`core/ui/Drag.kt`, `LayoutRepository.placeApp / setDockApps / dockApps`, implemented in `data/`):
  - **Integrate**: provide `LocalDragController` (+ `DragGhostLayer`) at the root; `LauncherViewModel`: `dockApps(screen)` flow
    resolved against `apps`, `setDockApps`, `placeApp` (with folder creation when the drop cell holds an APP, like `moveItem`);
    dock apps = resolved stored keys, `defaultDockApps` only when the stored list is empty.
  - **Drawer**: each app tile uses `Modifier.dragSource` (payload `DragPayload.App`); while `controller.isDragging` the pop-up hides with
    `graphicsLayer { alpha = 0f }` (it must stay composed until the drop) and the home/dock are shown to drop on; real group folders:
    auto groups (by package heuristics) plus user groups stored by the drawer (`drawer/` owns a DataStore store of group name -> app keys),
    a group opens as a folder inside the pop-up; the apps inside are also draggable.
  - **Dock**: registers `dragTarget(id = DragTargetIds.DOCK)`; a drop inserts at the nearest slot (or replaces the app under the pointer) and writes
    `setDockApps`; dragging a dock icon out (payload `DragPayload.Item`/`App` from the dock) to the drawer's "Remove" strip or off the dock removes it.
  - **Home**: the settled page's grid registers `dragTarget(id = DragTargetIds.HOME_GRID)` and maps the drop pointer to a cell with `GridGeometry.cellAt`;
    `hoverTargetId`/pointer drive a highlighted cell.
  - **Edit**: edit-mode moves may keep their own gesture; the `REMOVE` target is optional.
  - **Data**: done (`LayoutRepositoryImpl.placeApp`, `DockAppsStore`).

## Decisions recorded after review (fix round 2)

- Item 3 (long press): widgets and widget stacks go straight to edit mode (`HomeContext.widgetLongPress`). App, folder and pair tiles
  deliberately keep the item popup (notification preview, shortcuts, Edit, Remove, add to pair); its "Edit" entry and a long press on
  empty space / any widget enter edit mode, and in edit mode a long press selects the tile. The rule is: tiles with an app-style popup
  open it, everything else enters edit mode. Not changed in code.
- Hybrid (Panels) pages draw 8 (cover) or 16 (inner / flex) apps; adding or dropping a further app shows "No room on this page"
  (`LauncherViewModel.hybridPageIsFull`). List pages stay unbounded.
- Manual JSON backup now also carries the per-screen dock apps and the app pop-up groups (optional fields, older files still import).
  The daily-wallpaper and widget-ids-checked DataStores are intentionally not part of Auto Backup (per-device state).
