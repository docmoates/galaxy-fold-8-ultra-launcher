# Changelog

All notable changes to FoldOS. Versions follow `versionName` in `app/build.gradle.kts`; dates are
commit dates (all work so far landed on 2026-10-16). Nothing below has been run on a device yet; see
the README's "Not yet verified on a device" section.

## [1.2.0] - 2026-10-16

Fix round 1.3 (`versionCode` 120): remove profiles, readable labels, clean widget picker. Written from the source; not yet run on a device.

### Removed
- Profiles, end to end: the Settings > Profiles page, the "Active profile" and Profiles rows on Screens and Features, the
  Profiles button on the edit bar, the profile switcher / editor sheets, the schedule and Do Not Disturb auto-switching,
  `ProfileAlarmReceiver` / `ProfileBootReceiver`, the `SCHEDULE_EXACT_ALARM` permission (the daily wallpaper alarm is inexact)
  and the `profiles` list in backups. Old settings and old backup files that still carry `profile`, `activeProfile`, a
  `profiles` feature switch or a `profiles` list import normally; those keys are ignored.

### Changed
- Hidden apps now have their own page, Settings > More settings > Hidden apps (it used to live inside the profile editor).
- Readable app labels: names under icons on the home grid, the dock, the app pop-up, folders, app pairs and the app
  menus use one style (12sp medium weight, centered, soft shadow on the wallpaper). A long name wraps to two lines and
  gets an ellipsis only after the second line, so "Amazon Photos" and "Authenticator" are no longer cut off. Every
  icon keeps a fixed two-line label slot, so rows stay aligned. The system font scale only grows labels up to 1.15x.
- Label size: Settings > Home style shows three preview cards (Small 11sp, Medium 12sp, Large 14sp) with sample names;
  the choice is stored as `labelSize` (default medium). Easy mode adds 3sp on top.
- The home grid reserves room for two label lines under each icon, so a page can fit one row fewer on a short screen;
  the labeled bottom dock is about 13dp taller.
- New widget picker with two levels. Level 1: a search field, a Featured grid of FoldOS widgets, and an Apps list (one
  row per app that provides widgets, FoldOS first, filter chips by category). Level 2: a swipe-through detail view that
  shows each widget of the chosen app on its own page with a large preview, its size and a description, page dots, and
  an "Add to home" button. Cover: bottom sheet with a back arrow; inner and flex screens: floating card with the list on
  the left and the pager on the right. The add flow (built-in widgets placed directly, app widgets through the system
  bind and configure steps) is unchanged.
- Settings > Widgets is now one choice (Category / App / Recent, shown as three cards) plus Reset recent widgets. The
  Widget stacks and Smart switches stay on Features.

## [1.1.1] - 2026-10-16

Fix round 1.2: on-device test results on 1.1.0 (`versionCode` 111). Written from the source; not yet run on a device.

### Fixed
1. Stable signing key: debug and release builds are signed with the same committed key (`app/foldos-signing.p12`), so
   installs update in place instead of failing with a signature mismatch. Builds made before this one used a different
   key per CI run, so 1.1.0 must be uninstalled once before 1.1.1 goes on.
2. Status bar visible on dark and light: the clock and icons follow the launcher's Appearance (light icons on a dark
   launcher, dark icons on a light one) instead of the system dark mode, in the launcher, the setup wizard and Settings.
3. Setup wizard Colors step layout: the live preview shrank to about 40% of the visible height, the Dark / Light / Auto
   cards are compact whole-phone thumbnails and the swatches sit in a four-column grid, so the cards and the first
   swatches show without scrolling; the title and the Back / Continue bar stay pinned.
4. Empty-cell long-press hit test: a long press on an empty cell opens edit mode on that cell and no longer selects or
   opens the menu of a neighbouring item (exact cell under the finger, children handle their own presses, a stale
   selection from an earlier press is cleared).
5. Useful default home apps and dock: a fresh home page is seeded with up to 16 useful apps (Chrome, Gmail, Photos,
   Maps, YouTube, Calendar, ...) instead of the first apps alphabetically, and the default dock prefers phone,
   messages, Chrome and camera. Existing layouts are not changed.
6. Snackbar placement: the "Widget removed / Undo" snackbar sits just above the edit-mode bottom bar.
7. Settings preview ghosting: the pop-up app drawer samples draw the home dimmed behind an opaque sheet, so labels no
   longer show through; the full-screen sample does not draw the home.

## [1.1.0] - 2026-10-16

Fix round 1.1: device feedback on 1.0.0 (`versionCode` 110). Written from the source; not yet run on a device.

### Fixed
1. Hub page: the home dock and page indicator no longer show on the Hub page, so there is one tab bar and the
   content gets the full height on the cover and the inner screen (the rail dock hides there too).
2. Swipe up for apps: the gesture detector now covers the whole home (pager, page indicator and dock), not just
   the page columns, and every dock has an "All apps" button.
3. Long-press on the home: a long press on an app, folder, pair, widget, stack or empty space enters edit mode
   with that item selected (the item menu keeps an "Edit" entry); widgets are caught even over hosted app widgets.
4. First run created three identical pages: a fresh screen now gets one populated page and one empty page, nothing
   is re-seeded later, and the duplicate pages 1.0.0 created are removed once.
5. Settings was too complicated: Settings opens on live preview cards (Screens, Home style, Look, Colors, Dock,
   App drawer, Widget picker, Icons) where one tap applies a sample; toggles, dropdowns, sliders and hex fields moved
   to "More settings". Every old route still opens its page.
6. Colors: twelve primary colors, each with a light and a dark version (stored as `<id>-light` / `<id>-dark`; old
   ids still resolve to the dark version), shown as split swatches in Settings, the setup wizard and the edit-mode
   theme sheet.
7. Search: opening search and typing no longer slides the home down and exposes it as a strip at the status bar;
   search is a full-screen takeover with the field pinned at the top and the results above the keyboard
   (`android:windowSoftInputMode="adjustNothing"` on `MainActivity`).
8. Remove widget did nothing: the remove X now sits on the selected item inside the area between the bars, one tap
   removes, and a snackbar offers Undo for five seconds (an app widget keeps its host id until the window passes).
9. Edit widget overlapped the home: edit mode shows ONE top bar and ONE bottom bar and draws the home scaled to 76%
   between them; the published grid geometry stays in unscaled window dp, so drag, snap and resize line up.
10. Duplicate search: a bottom dock with the search add-on is the only search bar; the top bar is not drawn.
11. No way to change the dock or home apps: the app pop-up has group folders (Work, Media, Social, Tools, Games,
    Hidden, your own); apps are dragged from it onto a home page (placed on the cell, a folder when dropped on an
    app) or onto the dock (inserted, reordered or replaced); dock icons can be dragged out, reordered or removed from
    their menu; home tiles can be picked up after the long press and moved or dropped on the dock.

### Added
- `apppicker/` package: group store (DataStore `foldos_groups`), automatic categories, shared tap / long-press /
  drag gesture for app tiles, group cards and dialogs.
- Launcher-wide drag and drop (`core/ui/Drag.kt`): `DragController`, drop-target registration, a floating drag ghost.
- Dock apps are stored per screen (`LayoutRepository.setDockApps` / `dockApps`, DataStore `foldos_dock`).
- `LayoutRepository.placeApp` (nearest free cell). Room stays at version 3.

### Changed
- `LayoutRepositoryImpl.DEFAULT_PAGE_COUNT` is 2 and `ensureDefaultLayout` seeds only a screen with no pages.
- `ColorSchemes` lists both versions of every family; the theme no longer clamps preset accents.

## [1.0.0] - 2026-10-16

Phase 6: polish and release. First release build (`versionCode` 100).

### Added
- Backup and restore: whole-launcher export to a JSON document (settings, every page and item of
  every screen, folders and stacks, profiles, icon overrides) and import that replaces the current
  layout in one transaction; malformed or newer-version files are rejected without touching data.
  Google Auto Backup status and a request-a-backup row; grid size import from Lawnchair or Nova files.
- Accessibility settings: high contrast, reduce motion, text scale (0.85x to 1.6x on top of the system
  font scale), large icons (easy mode) and greyscale focus mode; semantics, touch targets and focus
  order on home, dock, drawer and settings.
- "No network" switch (Settings > Features > Privacy) that turns off the Gmail API, web previews and any network use by AI.
- Daily wallpaper switch (Settings > Look, off by default): AlarmManager once a day plus WallpaperManager, no WorkManager; `SET_WALLPAPER` permission.
- Hardware keyboard: type-to-search on the home screen and arrow-key focus movement.
- Performance work: lazy startup initialisation, baseline profile.
- Release build type: R8 shrinking, signing from `FOLDOS_KEYSTORE_PATH` / `FOLDOS_KEYSTORE_PASSWORD` /
  `FOLDOS_KEY_ALIAS` / `FOLDOS_KEY_PASSWORD` with the debug key as fallback.
- GitHub Actions: `foldos-release-apk` artifact and a GitHub Release with the APK on every `v*` tag.
- Final README (features by phase, permissions, privacy, Gmail OAuth setup, troubleshooting) and
  `docs/TESTING.md`, a manual test plan for the Fold 8 Ultra.

### Changed
- `LayoutRepository` gained `allPagesWithItems()` and `replaceAll()` (no schema change; Room stays at
  version 3). `AppGraph` gained `backup`.
- `LauncherSettings` gained eight defaulted fields (no migration needed).
- Release lint checks no longer block `assembleRelease` in CI.

## [0.3.0] - 2026-10-16

Phase 4+5: power features, Hub and AI.

### Added
- Gestures: swipes (one and two finger), pinch in and out, double tap, home key double press, fold and
  unfold; each bound in Settings > Gestures to an action.
- Universal search: apps and shortcuts, contacts, settings, files, calendar, calculator, unit
  conversion, web search with a choice of engine and `!bangs`, Lens button, usage-ranked results.
- Folders (grid, list, big) created by dropping one app on another; app pairs that launch two apps in
  split screen.
- Notification badges (count or dot), long-press previews, quiet notification drawer.
- Profiles (Work, Home, Night seeded): full settings snapshots, schedule and Do Not Disturb switching,
  alarm and boot receivers.
- Hub page tabs: Today briefing, Calendar, Contacts, Mail (Gmail API through AppAuth, plus Gmail-app
  label counts) and AI.
- AI command router with natural-language launcher commands, template engine and AICore detection,
  AI usage log.
- Permissions: `READ_CONTACTS`, `READ_CALL_LOG`, `EXPAND_STATUS_BAR`, `RECEIVE_BOOT_COMPLETED`,
  `SCHEDULE_EXACT_ALARM`, `INTERNET`, Gmail content provider.

### Fixed
- Compile errors in the Mail tab type arguments and in universal search.

## [0.2.0] - 2026-10-16

Phase 2+3: widgets, looks and styles.

### Added
- Hosted app widgets (`AppWidgetHost`) with bind and configure flow.
- Built-in widgets: Clock, Glance, Weather (sample data), Calendar agenda, Devices battery, To-do, Now
  Playing, Focus timer, Photos, News.
- Widget picker (cover sheet, inner card) grouped by category, app or recent use.
- Live edit mode: drag, resize, widget stacks, free placement, page style, add and delete pages.
- Icon packs (appfilter.xml) and icon shapes.
- First-run setup wizard (5 steps) and the preset gallery.
- Wallpaper and theme quick sheets.

### Fixed
- `PhotosWidget` read `BoxWithConstraints.maxHeight` inside nested scopes, which did not compile.

## [0.1.1] - 2026-10-16

### Added
- App icon and an offer to set FoldOS as the default home (replaces 0.1.0 in `releases/`).

## [0.1.0] - 2026-10-16

Phase 1: core launcher. First build to pass CI.

### Added
- Home role activity, cover and inner layouts from `FoldingFeature`, Flex Mode split.
- Home styles: classic grid, widget dashboard, minimal list, hybrid panels.
- Dock with 20 presets and mixable traits; pop-up and full-screen app drawer with backdrop blur.
- Four themes, dark / light / auto, color schemes.
- Room layout database and DataStore settings; Settings app with Simple / Advanced levels and search.
- Design package: PLAN.md, interactive mock-up, launcher research, 532-entry widget catalog.
