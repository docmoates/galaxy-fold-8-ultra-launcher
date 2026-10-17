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
  - Everything else (Gestures, Search providers, Hidden apps, Mail, AI, Backup, Accessibility, Features, About) moves under one
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

---

# Round 1.4 diagnosis (cover-screen device test of 1.2.0, folded Fold 1080x2520)

Read from the code only (no device). Paths are under `app/src/main/java/com/foldos/launcher/`. "Confirmed" = the cause is
visible in the source; "Likely" = the source shows the gap but the exact device trigger is inferred.

Agents: **home-edit** = `home/`, `edit/`, `items/`, `data/LayoutRepositoryImpl` (plus the two view-model functions named in bug 1, which belong
to the same fix); **hub-wizard** = `onboarding/`, `hub/`, `mail/`, `picker/`. No bug needs a Room schema change (version stays 3) or a `core/` contract change.

## 1. Page switched to "Dashboard" shows the apps in two cramped columns (Confirmed) - home-edit

- `LauncherViewModel.kt` L660-663 `seedDashboardIfEmpty`: `if (graph.layouts.items(pageId).first().isNotEmpty()) return`. A GRID page holds APP items, so the check
  returns early and no widgets are seeded. It is called from `setPageStyle` (L646-652), `addPage` (L666-675) and `applyStyleToScreen` (L797-805).
- `home/HomeDashboard.kt` L109-142 `HomeDashboardPage`: lays out every item of the page (`items`, L124) on `dashboardColumns(screen)` = 2 columns on the cover (L15). The apps
  keep their grid cells (cols 0..3), `reflowGridItems` (`home/HomePages.kt` ~L243) folds them into two columns, and icon tiles are drawn in half-width cells: squeezed, clipped labels.
- Fix:
  - `seedDashboardIfEmpty`: seed `defaultDashboardItems(screen, pageId)` when the page has **no WIDGET item** (`items.none { it.kind == ItemKind.WIDGET }`), not when it has no item at all.
    Never delete the apps.
  - `HomeDashboardPage`: render only `items.filter { it.kind == ItemKind.WIDGET }` (APP / FOLDER / PAIR stay in the database, hidden). Keep the `placeholder` preview only for `page == null`.
  - Same filter wherever a DASHBOARD page's items are used: `edit/EditModeOverlay.kt` `pageItems` (~L207; `style` is L202) so hidden apps cannot be selected or hit by the gesture layer;
    `LauncherViewModel.placeWidget` (L748-), the overlap checks in `moveItem` (L395-410) and `firstFreeCell` must ignore the hidden APP / FOLDER / PAIR items of a DASHBOARD page (they sit on
    the same cells as the seeded widgets); the a11y grid info in `HomeItemsGrid` (`home/HomePages.kt` L152).
  - Switching back to Grid restores the apps (never touched). The seeded widgets would then overlap them in `HomeGridPage` (it draws every item). In `setPageStyle`, when the new style is GRID
    and the page has APP / FOLDER / PAIR items, delete the WIDGET items that are still exactly the untouched `defaultDashboardItems` (same builtinId, col, row, spans); a widget the user
    added or moved stays and is moved to the nearest free cell with `nearestFreeCell` (as `moveItem` does).

## 2. "List" style in edit mode: grid outlines over the rows, scattered alphabet scrubber (Confirmed) - home-edit

- Outlines: `edit/EditModeOverlay.kt` L320-325 draws a rounded outline for every cell of `grid.rows x grid.cols` whatever the page style. A settled LIST page has no measured grid
  (`home/HomePages.kt` L60-62 removes it), so `rememberGridGeometry` falls back to `estimateGridGeometry` (`home/HomeGeometry.kt` L110-111, L133: 1 column, `heightDp / 46` rows, guessed bars) and
  the overlay paints 46dp-pitch boxes that do not line up with the real rows (`home/HomeListPage.kt` L201-212: 36sp line height plus 10dp padding). Fix: skip the guide loop (and the selection
  outline / gesture hit areas that depend on that grid) when `style == HomeStyle.LIST`; a list gets no cell guide.
- Scrubber: `home/HomeListPage.kt` L175 `letters = favorites.map { it.scrubberLetter() }.distinct()`. `favorites` (`home/HomeSupport.kt` L276-285) is the page's APP items in `row, col` order, and the
  seed order is the `preferredHomeApps` priority list (browser, mail, photos, maps ... `home/HomeDefaultApps.kt` L68), not alphabetical. So the letters come out in list order (for example C G P M ...) with
  repeats dropped, and the list itself is unsorted. `HomeAlphabetScrubber` (L264-325) spreads them with `Arrangement.SpaceBetween` while `letterAt` maps y by `y / height * letters.size`, so touch slots
  and drawn letters drift apart.
- Fix: for LIST pages sort the shown apps by label (`favorites.sortedBy { it.label.lowercase() }`, both the list and the scrubber input) so the list is A-Z; build `letters` from that sorted list
  (`distinct()` keeps them in order); in `HomeAlphabetScrubber` give every letter an equal slot (`Modifier.weight(1f)`) with the glyph centred, so the hit slot equals the drawn slot and the spacing is even.

## 3. Long press just below the last icon row opens the icon's menu instead of edit mode (Confirmed) - home-edit

- The hit area of a tile is the whole grid cell, which is taller than the icon plus label:
  - `home/HomeCellGrid.kt` L369-378: `rows = maxHeight / minCellHeightPx`, then `cellH = height / rows`, so the cells stretch to fill the page.
  - `home/HomePages.kt` L222: `minCellHeight = iconSize + appLabelBlockHeight + 20.dp`; the label block always reserves two lines (`home/AppLabelStyle.kt` L204), so a one-line label leaves another
    ~14dp unused, on top of the 20dp margin.
  - `home/HomeAppIcon.kt` L128-160 `HomeAppTile`: the outer `Column` is `fillMaxSize()` with `homeTapGestures(onTap, onLongPress)` (L154) and top-aligned content (4dp padding), so everything under the
    label up to the cell edge belongs to the tile. Same in `HomeFolderTile` (`home/HomeFolderPair.kt` L81) and `HomePairTile` (L183). `homeItemDrag` sits on the full-size `Box` (`home/HomePages.kt` ~L316).
  - `HomeGridMetrics.itemIndexAt` (`home/HomeCellGrid.kt` ~L137-153) tests the full cell rectangle, so the grid's own empty-space long press (~L292-326) ignores a press anywhere in a cell. At 4 columns
    and ~2.6 px/dp the slack under the label is the 40-80 px band seen on the device (a press 100+ px lower lands in the empty cell below, which is why that one works).
- Fix: make the hit area the visible content. In `HomeAppTile` / `HomeFolderTile` / `HomePairTile` attach `homeTapGestures` (and the semantics) to an inner node sized to icon + label
  (`Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top))` inside the cell) and make `HomeGridMetrics.itemIndexAt` use the same content height for 1x1 APP / FOLDER / PAIR cells (add a
  `tileContentHeightPx` parameter to `HomeCellGrid`, = `iconSize + AppLabelStyle.ICON_GAP + labelBlockHeight + 8.dp`; widgets keep the full span). The grid's `onEmptyLongPress` then fires on the slack
  and enters edit mode. At most 8-12dp of extra tolerance.

## 4. Delete page: the confirm tap does nothing (Confirmed UI cause; persistence path checked) - home-edit

- `edit/EditModeOverlay.kt` L489 turns the **page chip** into "Delete page?" (`EditChip(label = pageLabel, onClick = null, ...)`, L518-520). The only control that runs the delete is the separate trash
  `EditRoundButton` (~L541-556, `onDeletePage(currentPage.id)` at L550). A user who taps the chip that now reads "Delete page?" taps a static label (`onClick = null` makes `EditChip` non-clickable,
  `edit/EditChrome.kt` ~L217); after 3 s (`delay(3000L)`, L230) `confirmDeletePage` resets. The page count stays 3, matching the report.
- The confirm state is also reset by `LaunchedEffect(currentPage?.id, sortedPages)` (L223-226) whenever the current page or the page list changes, which can silently undo it.
- Persistence path: `LauncherRoot.kt` L485 -> `LauncherViewModel.deletePage` (L681-695) -> `LayoutRepositoryImpl.deletePage` (L244-253: `DELETE FROM pages`, items follow through `ForeignKey.CASCADE`,
  the remaining pages are re-indexed in the same transaction). Nothing there fails for the last page and nothing re-adds pages (`ensureDefaultLayout` only seeds a screen with 0 pages). No defect found.
- Fix: while `confirmDeletePage`, make the chip itself the confirm target: label "Tap again to delete", red container (e.g. 0xFFD93025 with white text) and
  `onClick = { confirmDeletePage = false; selectedId = null; onDeletePage(currentPage.id) }` (add a `danger` / `containerColor` parameter to `EditChip` in `edit/EditChrome.kt`); tint the trash button red
  too and keep its second tap working. Raise the timeout to about 5 s and reset only when the current page id changes, not on every `sortedPages` emission.

## 5. Dropped-on folder: mini preview shows blank purple squares instead of the apps' icons (Likely) - home-edit (items/)

- `items/FolderIcon.kt` L58-63 builds `preview` with `children.sortedWith(row, col).mapNotNull { apps.firstAppOf(it) }`; `FolderGridIcon` (L75-108) always draws four slots and every slot beyond
  `preview` is an `tokens.accent.copy(alpha = 0.35f)` rounded square (L99; the stack style does the same at L163). A two-app folder therefore always shows two accent-tinted blank squares, and any child whose app
  is not in `ctx.appsByKey` (hidden app, an app without a launcher activity such as AICore, work-profile or uninstalled app) is dropped silently by `mapNotNull` and shows as a blank slot too. While a
  bitmap loads, `ItemAppIcon` (`items/ItemSupport.kt` L77-96) shows `tokens.surface`.
- The data path is sound: `createFolder` / `placeInFolder` (`data/LayoutRepositoryImpl.kt` L280-296) and `placeDroppedApp` (`LauncherViewModel.kt` ~L495-515) store both children with `packageName`,
  `activityName` and `parentId`. I found no write that loses them. Could not confirm without a device whether the top row also rendered blank; if it did, the app lookup above is the cause.
- Fix (items/): resolve each child with the key lookup and then a `PackageManager` fallback (reuse the `fallbackApp` logic of `items/ItemContextMenu.kt` L400, move it to `ItemSupport.kt` as `internal`) so a
  child always gets an icon; draw unused slots as nothing (or a faint `onSurface` at 0.08 alpha), never accent squares; with a single child draw one centred icon.

## 6. Wizard "Pick your colors": preview scrolls away, "Theme colors" truncated (Confirmed) - hub-wizard

- `onboarding/SetupWizard.kt` L137-155: every step, including `4 -> ColorsStep(...)` (L149), is drawn inside one `Column(...verticalScroll(scrollState))` (L142), and `ColorsStep` (L262-) puts the
  live `SampleHomePreview` (L274-295) as the first scrolling child, so it leaves the screen as soon as the swatches are scrolled.
- Label: `ColorTile` (~L390-420) is one quarter of the width on the cover (`tiles.chunked(4)`, ~L354) with `maxLines = 1`, `TextOverflow.Ellipsis`, 12sp; "Theme colors" (L326) does not fit and shows as "Theme col...".
- Fix: for step 4 compose the preview (about 30% of `viewportHeight`, `coerceIn(120.dp, 260.dp)`, centred, same `SampleHomePreview` / aspect ratio) **above** the scrolling Column inside the
  `BoxWithConstraints` (L137), with the Appearance cards and swatches in the `verticalScroll` area below it. Rename the tile to "Default" (L326). The same label also exists in
  `edit/ThemeQuickSheet.kt` L127 (home-edit's package) and `settings/SettingsHomePage.kt` L482 (outside both agents' packages); change the ThemeQuickSheet one too for consistency.

## 7. Hub > Mail without an account: long explainer, Compose FAB overlaps the Gmail app card buttons (Confirmed) - hub-wizard

- `mail/MailTab.kt` L479-518 `explanation`: a "CONNECT GMAIL" card with a six-line OAuth paragraph (L498-505), a three-line paragraph (L508-511) and a button; on the cover it stacks above the "Gmail app"
  card (L520-) in one scrolling Column (L599-609), which pushes that card's button row ("Choose account", "Open Gmail") to the bottom edge of the viewport.
- The FAB is an overlay: `ExtendedFloatingActionButton` (L183-192) is aligned `BottomEnd` in the root `Box` of `MailTab` (L142-193) and reserves no layout space. The only reserve is a `Spacer(72.dp)`
  at the end of the scroll content (L607), which helps only after scrolling to the very end, so at rest the FAB sits over the buttons.
- Fix: `explanation` becomes a short card of 3 short sentences (for example "Mail needs a Google sign-in. Until you connect, this tab shows the unread counts the Gmail app already keeps. Setup is a one-time step.")
  plus an expandable row "How to connect" (`AnimatedVisibility`) holding the OAuth steps (Cloud project, Gmail API, Android OAuth client for `com.foldos.launcher` with the SHA-1, paste the client id under
  Settings > Mail, tap Connect) and the "Open Settings > Mail" button. Replace the overlay with layout space: `Column { content(Modifier.weight(1f)); FAB row aligned end with
  padding(end = 20.dp, bottom = 20.dp) }`, or keep the Box and give the scrolling content a bottom padding of about 88dp (FAB 56dp + 2 x 16dp) in every state that shows the FAB (NotConfigured and Connected, cover only).

## 8. Hub > Calendar / Contacts: large blank area when permission is missing (Confirmed) - hub-wizard

- Calendar (`hub/HubCalendarTab.kt`): with `granted == false` the cover layout (L163-176) still draws the header, the empty `HubWeekStrip` card (L169-173) and then `dayCard`, which is only a small
  `HubPermissionCard` (L127-134); `upcomingCard` is empty (L153-155) and the "New" FAB (L207-212) still shows. The rest of the scroll column is blank.
- Contacts (`hub/HubContactsTab.kt`): header, search pill (L152 / L162) and `list(...weight(1f))` (L126-137) draw the permission card inside a `HubListCard` at the top of a `weight(1f)` area
  (`HubPermissionCard`, `hub/HubPieces.kt` L451-484: a small lock icon, text and Allow pill in one row), so everything below it is blank.
- Fix: when the permission is missing render one centred permission state instead of the normal content: update `HubPermissionCard` (hub/HubPieces.kt) to a vertical layout with a large illustration icon
  (for example `Icons.Rounded.CalendarMonth` / `Icons.Rounded.People` in a 72dp tinted circle), a one-line explanation and the Allow pill, placed in `Box(Modifier.fillMaxSize().padding(contentPadding),
  contentAlignment = Alignment.Center)`. In `HubCalendarTab` skip the week strip, month grid, upcoming card and FAB when `!granted`; in `HubContactsTab` skip the search pill, favourites and list (keep or drop the header)
  and, on the inner screen, replace the right pane text with the same card. The Today tab already guards its sections.

## 9. Widget picker detail: large empty area after "Add to home" (Confirmed) - hub-wizard

- `picker/WidgetPickerDetail.kt` L169-260 `DetailPage`: the page is `Column(Modifier.fillMaxSize().verticalScroll(...), verticalArrangement = Arrangement.spacedBy(8.dp))` (L188-194), so preview, name,
  size line, description and the button are stacked from the top and the button ends right after the content (L248-256). The `HorizontalPager` above gets `weight(1f)` (L142-) of a tall sheet (about 78% of
  2520 px on the cover), so all the remaining height is blank below the button.
- Fix: make `DetailPage` a `Column(Modifier.fillMaxSize())`: a `Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center)` holding the preview card, name, size and description (inside its own
  `verticalScroll` column with `Arrangement.spacedBy(8.dp)`, centred horizontally; the Box centres it vertically), then the "Add to home" `Button` as the last child with
  `Modifier.padding(horizontal = 20.dp, vertical = 12.dp)` so it stays anchored near the bottom above the page indicator. `maxInnerHeight` (0.42 x page height, L180) can stay.

# Fix round 1.5 diagnosis (device test of 1.2.1, cover + inner)

Paths under `app/src/main/java/com/foldos/launcher/`.

1. Dashboard <-> Grid overlap (Confirmed): `LauncherViewModel.setPageStyle` / `applyStyleToScreen` only removed the exactly-seeded
   default widgets, and only when the page also held apps. A moved / resized / added widget, or one seeded by 1.2.0, kept its
   stored cell on top of the apps. Fix: `moveWidgetsOffApps` (called on every switch to Grid and once per launch for Grid pages)
   relocates colliding widget slots to the first free cells.
2. List edit chips (Confirmed): `edit/EditModeOverlay.kt` drew one `EditChip` per widget in a column over the page. Now one
   "Widgets (N)" chip + a card. `home/HomeListPage.kt` `HomeAlphabetScrubber` gave every letter an equal share of a strip that edit
   mode shrinks to 76%; it now thins the letters to fit a 16dp slot.
3. Clipped text (Confirmed): `horizontalScroll` rows hid chips off-screen (`ai/AiTab.kt`, `ChipsRow`, icon packs, palette); replaced
   with `FlowRow`. Carousels that are meant to scroll were kept.
4. Mail tab (Likely): the inner layout was a two-column `Row` of two cards of very different heights, and the hint text named a
   manifest permission. One centred column and user wording now.
5. Label wrapping (Confirmed): `AppLabel` is a plain two-line `Text`; Compose breaks a word that is wider than the cell. It now
   shrinks until no word is split. The parent gives labels 1dp per side, so adjacent labels touched: 2dp added inside the label.
6. Edge rail (Confirmed): `LauncherRoot` drew the rail dock as an overlay on top of `HomeScreen` with no layout space. `HomeScreen`
   now sits in a box with `padding(end = railInset)`.
7. Inner Different + Classic grid (Confirmed): Settings > Home style restyles the dashboard pages to Grid with their 4-column
   dashboard widgets in a 6-column grid (top-left, small tiles, truncated text) and no apps. `removeSeededDashboardWidgets` now
   also runs for widget-only pages and seeds the first-run apps.
8. Colors preview (Confirmed): `fillMaxWidth(0.6f).widthIn(max = 240.dp)` - `widthIn` after a fixed-fraction width does not cap it,
   so the 411 x 700 preview was 60% of the section wide. `ScaledPreview` also inherited the system font scale; it now provides
   font scale 1 so the fixed-size cards cannot clip their text.
9. Role dialog (Likely): `SettingsActivity.maybeRequestHomeRole` ran on every fresh start of the activity, including Settings opened
   from FoldOS. Now only for a MAIN / LAUNCHER start, once per process, and the stored "already asked" flag stays.
10. Advanced row (Confirmed): the only row without `leadingContent`; `ToggleRow` takes an optional leading icon now.
11. Dashboard edit outline (Confirmed): the tiles are inset by `cellPadding` (6 / 8dp) but the overlay drew the selection at the full
    cell and the guide at 3dp. Same inset now (scaled by `EDIT_SCALE`).
12. Hybrid inner apps (Confirmed): `HomeFavoriteGrid` used `fixedRows` + `weight(1f)`, so 4 rows were stretched over the panel
    height. Now a fixed compact row height with as many rows as fit.
13. "Not flipping" rows (Not reproduced): the toggles and chips are wired and persisted (`SettingsWriteQueue` -> DataStore);
    Count / Dot sat in a horizontally scrolling chip row, see 3.

# Round 2.0 diagnosis: why the page templates had to go (1.3.0)

Read from the code and the 1.2.x device reports. The four per-page styles each had their own renderer, their own
column count and their own idea of which stored items were "visible", and every switch between them was a conversion
that had to be repaired afterwards. The redesign (one free-form canvas per page plus cards as holders, see
`app/CONTRACTS.md` "Round 2.0") removes the following root causes instead of patching them again:

1. **Widgets drawn over app icons after a style switch** (1.2.1, 1.2.2): `HomeDashboardPage` drew widgets only, on 2 / 4
   widget columns, while the page's APP rows stayed in the database on the same 4 / 6 grid cells. Switching back to Grid showed
   both sets on one grid; `removeSeededDashboardWidgets` + `moveWidgetsOffApps` repaired only the cases they knew. Now there is
   one grid and one set of visible items per page, every placement goes through `nearestFreeCell` / `firstFreeCell` / `placeApp`
   with an overlap check, resizing refuses overlaps, and `repairOverlaps` packs every page once per launch. Nothing is hidden, so
   nothing can come back on top of something else.
2. **Inner screen cramming widgets into the top-left** (1.2.2 item 7): the dashboard's 4-column coordinates were drawn on a
   6-column grid when a page was restyled. The migration re-spans every dashboard widget onto the page grid once
   (`CanvasMigration`, cover x2, inner 1 -> 2 / 2 -> 3 cells) and packs it; afterwards spans are only ever set in page-grid
   cells, by the user (resize handles) or by `StarterLayouts` for the real column count.
3. **List edit mode: chips stacking, outlines that matched no row** (1.2.0, 1.2.2 item 2): the List style had no cell grid, so
   the edit overlay could neither select nor outline its rows and grew a parallel "Widgets (N)" UI. A list is now an apps card in
   list style: one grid item with a real cell rectangle, selectable, resizable and removable like everything else; the overlay has
   one code path and no per-style branches.
4. **Doubled widget outlines in edit mode** (1.2.2 item 11): the overlay drew its selection at the full cell while dashboard tiles
   were inset by a per-style padding, so two frames showed. The canvas draws every widget / card with the same 6dp tile padding
   and the overlay draws one guide inset (3dp) and one selection frame at the cell; there is no second geometry to disagree with.
5. **"No room on this page" surprises on Hybrid pages**: the Panels renderer drew exactly 8 / 16 apps and silently stored the rest.
   Cards have no fixed capacity: an apps card lays out as many rows as fit and scrolls past that; the page itself grows a row when
   its visible grid is full. A placement is refused only when the grid plus one extra row has no free spot, with a toast.
6. **Two ways to add things, neither like Pixel**: "Add widget" lived in edit mode, apps came only from the pop-up drawer, and a long
   press on empty space jumped into edit mode. Long press now opens the "Add to home" sheet (Apps / Widgets / Cards + Wallpaper,
   Themes, Dock, Pages, Settings); drag from it or tap to place at the first free cell; edit mode is only page management plus the
   Remove strip, reached by pinch, the sheet's Pages action or an item menu's Edit / Resize.

Not verified on a device (written from the source): the Initial-pass long press on a card's chrome versus its children, hosted app
widgets dropped into a widget card (bind dialog then `addAppWidgetToCard`), and the migration on a database that mixes stacks with
the old dashboard coordinates.

# Mail via the Crosspoint server (1.2.4)

- Why: on-phone Google sign-in needed an OAuth client id per person (1.2.3 fetched a shared one). Now the server owns the Google
  link: `server/ServerClient.kt` (login, logout, me, mail/link, mail/unlink, mail/inbox), token in `server/ServerAuthStore.kt`.
- `mail/GmailMailRepository` keeps its name but is server-backed. State machine: no token = SignedOut; `/me` mail.available false
  or 503 = NotConfigured; not linked or 409 not_linked = NotLinked; 502 = Error(server message); IOException = Error("Can't reach...");
  401 anywhere = token cleared, SignedOut. No-network mode shows the old Error message and sends nothing.
- Linking: `startLink(activity)` posts mail/link, opens the URL with ACTION_VIEW, then polls `/me` every 2 s for up to 2 minutes
  (`waitingForLink` in `MailAccount`). The Mail tab also refreshes on resume, which covers returning after the poll window.
- Not verified on a device: the browser hand-off, IME behaviour of the sign-in form on the cover and inner screens, real server replies.
