# Changelog

All notable changes to FoldOS. Versions follow `versionName` in `app/build.gradle.kts`; dates are
commit dates (all work so far landed on 2026-10-16). Nothing below has been run on a device yet; see
the README's "Not yet verified on a device" section.

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
