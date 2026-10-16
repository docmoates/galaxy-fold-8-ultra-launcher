# FoldOS module contracts

Single Gradle module `:app`, base package `com.foldos.launcher`. Kotlin 2.1.0, Compose BOM 2024.12.01,
compileSdk/targetSdk 35, minSdk 34. Nothing compiles locally; GitHub Actions builds the APK, so every
import must be real and every signature exact.

**Room is pinned to 2.7.0, not 2.6.1.** Room 2.6.1's KSP processor cannot read Kotlin 2.1.0 class
metadata and fails the CI build with "Unsupported metadata version"; 2.7.0 is the first release that
can. Do not revert `room = "2.7.0"` in `gradle/libs.versions.toml`. Every other version follows the
pinned list (AGP 8.7.3, Gradle 8.9, KSP 2.1.0-1.0.29, activity-compose 1.9.3, lifecycle 2.8.7,
window 1.3.0, datastore-preferences 1.1.1, kotlinx-serialization-json 1.7.3, kotlinx-coroutines 1.9.0,
core-ktx 1.15.0, navigation-compose 2.8.5).

## Ownership

### Phase 1 (done)

| Package / path | Owner | Public API it must expose |
|---|---|---|
| `core/` (everything below, incl. Fix round 1.1 `core/ui/Drag.kt`) | Scaffold / Contracts | Shared contracts. Read, do not edit. |
| `MainActivity.kt`, `LauncherViewModel.kt`, `LauncherRoot.kt` | Integrate | `com.foldos.launcher.MainActivity : ComponentActivity` (ROLE_HOME entry; declared in the manifest), the `LauncherViewModel` and the `LauncherRoot` composable that wires home, dock, drawer, hub placeholder and search bar together |
| `home/` | Home | Home pages, grid/dashboard/list/hybrid styles, dock rendering from `DockConfig`, page indicator, hub page shell |
| `drawer/` | Drawer | Pop-up and full-screen app drawer, search, folders |
| `apps/` | Apps | `AppsRepository` implementation on `LauncherApps` (`class LauncherAppsRepository(context) : AppsRepository`) |
| `data/` | Data | Room database + `LayoutRepository` implementation (`class LayoutRepositoryImpl(db) : LayoutRepository`), `SettingsRepositoryImpl`, `AppsRepositoryImpl`, `AppGraphImpl` |
| `settings/` | Settings | `SettingsActivity` (uses the `SettingsRepository` from `FoldOsApplication.graph`; the DataStore implementation lives in `data/`), wizard |
| `fold/` | Fold | `FoldState` producer from Jetpack WindowManager (`fun rememberFoldState(activity): State<FoldState>` or a `Flow<FoldState>`) |
| `theme/` | Theme | Compose `FoldOsTheme(settings, content)` for the four `ThemeId`s, dark/light, `ColorSchemeConfig` recoloring |
| `core/FoldOsApplication.kt` `onCreate` body only | Integrate | Builds the concrete `AppGraph` and assigns `graph` |

### Phase 2+3 (widgets, looks & styles)

| Package / path | Owner | Public API it must expose |
|---|---|---|
| `core/model/Layout.kt`, `core/repo/*.kt`, `core/AppGraph.kt`, `data/`, this file | Contracts | The shared types below plus the `data/` placeholders (`PlaceholderWidgetCatalog`, `EmptyBuiltInWidgetRegistry`, `PassthroughIconResolver`) that the agents below replace in `AppGraphImpl` |
| `widgets/` | Widgets-host | Hosted app widgets: `AppWidgetHostView` wrapper composable for a `HomeItem` with `appWidgetId`, bind flow (`ACTION_APPWIDGET_BIND` / `bindAppWidgetIdIfAllowed`), configure activity launch, `allocateAppWidgetId` / `deleteAppWidgetId` around `graph.appWidgetHost`. Stacks (swipe to flip items sharing `stackId`) are rendered by `home.HomeStackSlot`, the single stack implementation |
| `builtins/` | Builtins | `class FoldOsBuiltInWidgets : BuiltInWidgetRegistry` with the Compose built-in widgets (Clock, Weather, Calendar agenda, Glance header, Battery, ...); replaces `EmptyBuiltInWidgetRegistry` in `AppGraphImpl.builtIns` |
| `picker/` | Picker | `class FoldOsWidgetCatalog(...) : WidgetCatalog` merging app widget providers (categorised) and `BuiltInWidgetRegistry.all`, plus the widget picker UI (cover sheet / inner card, mock-up `wpOpen` / `.wp*`); replaces `PlaceholderWidgetCatalog` in `AppGraphImpl.widgets` |
| `edit/` | Edit | Live edit mode: long-press, drag (`LayoutRepository.moveItem`), resize (`resizeItem`), free placement (`HomeItem.free` + dp fields), per-item context menu, page add/style/delete (`addPage`, `setPageStyle`, `deletePage`), create stack (`createStack`) |
| `home/` | Home | Renders widget items (app widget via `widgets/`, built-in via `graph.builtIns.byId(item.builtinId)`), stacks, free-placed items, the four home styles; draws icons through `graph.icons.icon(app)` |
| `onboarding/` | Wizard | First-run setup wizard (5 steps, tap-a-preview) and preset gallery; sets `LauncherSettings.wizardDone` / `preset` |
| `icons/` | Icons | `class IconPackResolver(...) : IconResolver` (appfilter.xml parser, per-app override store, `IconShape` masking); replaces `PassthroughIconResolver` in `AppGraphImpl.icons`. **May also edit `data/AppsRepositoryImpl.kt` `iconLoader`** to route through the resolver. |

| `MainActivity.kt`, `LauncherViewModel.kt`, `LauncherRoot.kt`, `AndroidManifest.xml`, `README.md`, this file's ownership table | Integrate | Wires the packages above together (done, see below) |

Keep class names unique across packages (no second `MainActivity`, `Theme`, `HomeScreen`, etc.).
Agents replacing a placeholder edit only the matching `by lazy { ... }` line in `data/AppGraphImpl.kt`.

### Phase 4+5 (power features, Hub & AI)

| Package / path | Owner | Public API it must expose |
|---|---|---|
| `core/model/Layout.kt`, `core/model/LauncherSettings.kt`, `core/repo/*.kt`, `core/AppGraph.kt`, `data/`, this file | Contracts | The folder / pair / gesture / badge / Hub / mail / AI contracts below plus the `data/` placeholders (`EmptyBadgeRepository`, `EmptyHubRepository`, `NotConfiguredMailRepository`, `TemplateAiCommandRouter` + `TemplateAiProvider`) that the agents below replace in `AppGraphImpl` |
| `gestures/` | Gestures | Home-screen gesture detection (`GestureKind`) and dispatch of `settings.gesture(kind)` (`GestureAction.TYPE_*`: drawer, search, hub, settings, notifications / quick settings shade, app launch, edit mode, lock / recents hints); honours `FEATURE_GESTURES`. **May also edit `settings/GesturesPage.kt`** (per-gesture action picker writing `withGesture`) |
| `search/` | Search | Universal search UI and providers (`LauncherSettings.searchProviders`: apps, contacts, settings, files, calendar, web, calculator, units) with the web engine from `searchEngine` (`SEARCH_ENGINES`); results come from `graph.apps.apps` (hidden apps excluded), `graph.hub`, and the system providers. **May add `settings/SearchSettingsPage.kt`** (and its route in `settings/SettingsRoot.kt` only) |
| `items/` | Folders + pairs | Folder open sheet (styles `FOLDER_GRID` / `FOLDER_LIST` / `FOLDER_BIG` from `item.folderStyle ?: settings.folderStyle`), create / rename / dissolve via `LayoutRepository.createFolder` / `children` / `removeFromFolder` / `upsertItem`, app-pair icon and split-screen launch (`createPair`, `HomeItem.isPair`, `FEATURE_PAIRS`), drag-into-folder in edit mode |
| `home/` | Home | Renders FOLDER and PAIR items through `items/`, draws badges from `graph.badges.counts` in `settings.badgeStyle`, hooks `gestures/` onto the page container, hides `hiddenApps` (already filtered by `graph.apps.apps`) |
| `profiles/` | (removed in Fix round 1.3) | The Profiles feature no longer exists: no `Profile`, `ProfileRepository`, `AppGraph.profiles`, `FEATURE_PROFILES`, `LauncherSettings.profile` / `activeProfile`, receivers or `profiles/` package. See "Fix round 1.3" |
| `notifications/` | Badges | `class NotificationBadgeRepository(...) : BadgeRepository` (replaces `EmptyBadgeRepository`) fed by the listener; long-press preview (`notificationPreview`, `notificationsFor`); honours `FEATURE_BADGES` and `badgeStyle`. **May edit `builtins/FoldOsNotificationListener.kt`** to publish posted / removed notifications |
| `hub/` | Hub-core | `class AndroidHubRepository(...) : HubRepository` (CalendarContract, CallLog, ContactsContract, a Room or DataStore task store; replaces `EmptyHubRepository`) and the Hub page UI: Today briefing, Calendar and Contacts tabs (`hubTabs`, `hubPos`, `hubBar`) |
| `mail/`, `ai/` | Hub-mail-AI | `class GmailMailRepository(...) : MailRepository` (replaces `NotConfiguredMailRepository`; `mailClientId`), `class FoldOsAiCommandRouter(...) : AiCommandRouter` with `AiProvider`s for `AI_MODE_TEMPLATE` / `AI_MODE_ONDEVICE` (replaces `TemplateAiCommandRouter`), Mail and AI tabs. **May edit `settings/AiPage.kt`** |
| `MainActivity.kt`, `LauncherViewModel.kt`, `LauncherRoot.kt`, `AndroidManifest.xml`, `README.md`, this file's ownership table | Integrate | Wires the packages above together (done, see below) |

Settings pages other than the ones named above stay with the Settings owner; new fields are read with the
`LauncherSettings` helpers (`gesture`, `withGesture`, `isHidden`, `searchProvider`, `withSearchProvider`).

#### Phase 4+5 integration (done)

- `data/AppGraphImpl.kt`: `badges = notifications.FoldOsBadgeRepository(appContext, settings)`,
  `hub = hub.FoldOsHubRepository(appContext)`
  (`AndroidHubRepository` is its typealias), `mail = mail.GmailMailRepository(appContext, settings, appScope)`,
  `ai = ai.FoldOsAiCommandRouter(appContext, settings, apps, hub, badges, mail)` (template provider by default,
  `AiCoreProvider` when `aiMode == ondevice` and it reports `available()`). The `data/` placeholders stay as unused fallbacks.
- `home/HomeScreen.kt` gained `swipeUpOpensDrawer: Boolean = true` (false hands the swipes to the detector in
  `pageModifier`, so SWIPE_UP does not fire twice) and `hubSelectRequest: Int = 0` (a counter; each increment scrolls the
  pager to the hub page, the `TYPE_HUB` action). `HomeContext.swipeUpOpensDrawer` and `Modifier.homeSwipeUp(enabled, ...)`
  carry it to the page column and the Flex top half.
- `LauncherViewModel`: `searchOpen` / `openSearch` / `closeSearch` (falls back to the drawer while `FEATURE_SEARCH` is off),
  `hubRequests` / `openHub`, `gestureHost(context)` + `performGesture(context, action)` (`GestureActions.perform` with the
  view model as `GestureHost`; TYPE_APP resolves the key against `apps`, then the package's launch intent),
  `onHomeKeyPressed(context)` (`HomeKeyDoubleTapDetector`, called from `MainActivity.onNewIntent`). `moveItem` now turns an
  APP / PAIR dropped onto another APP / PAIR into `items.Folders.createFromDrop` and a drop onto a FOLDER into
  `Folders.addToFolder`, before the widget-stack and plain-move cases.
- `LauncherRoot`: `search.SearchBar` replaces the search-bar stub, `hub.HubPage(fold.kind, settings)` replaces the Phase 1
  hub placeholder, `search.UniversalSearch` is composed last (above the drawer), `Modifier.homeGestures(...)` goes in as
  `HomeScreen.pageModifier` (disabled while the drawer / picker / search / edit mode are up) with `swipeUpOpensDrawer =
  !FEATURE_GESTURES`, `HingeGestureEffect(fold, wizardDone, settings, onAction)`
  run beside the home, and the dock's notification add-on (`Dock(onNotifications = ...)`, new optional
  parameter) opens `notifications.QuietDrawerSheet`. The item context menu (folders, "Add to pair" > `PairEditorSheet`,
  hide, icon override) and `FolderSheet` are the `home/` defaults through `LocalHomeItemHooks`; no hooks are overridden.
- Settings: `SettingsRoot` routes `MailSettingsRows.ROUTE`; `SettingsPages.all` lists `SearchRows.page` and
  `MailSettingsRows.page` (Gestures, AI were already routed). Features shows
  `notifications.NotificationBadgeSettingsRows` (badge style, preview toggle, access card) and `QuietDrawerSettingsRows`
  under a "Notifications" header, with `NotificationSettingsRowInfos.all` in its searchable rows; Home has a Folder style
  select (`LauncherSettings.folderStyle`).
- Manifest: `READ_CONTACTS`, `READ_CALL_LOG`, `EXPAND_STATUS_BAR`, `RECEIVE_BOOT_COMPLETED`,
  `INTERNET`, `com.google.android.gm.permission.READ_CONTENT_PROVIDER`; `mail.MailAuthActivity` (singleTop, not
  exported); `<queries>` for
  `com.google.android.gm`, `com.google.ar.lens`, `com.google.android.aicore`, https VIEW and RECOGNIZE_SPEECH. The
  `appAuthRedirectScheme` placeholder was already in `app/build.gradle.kts` (AppAuth 0.11.1, security-crypto in
  `libs.versions.toml`). Backup rules exclude the `foldos_mail_auth*` prefs (Keystore-bound tokens).

### Phase 6 (backup, performance, accessibility, release)

| Package / path | Owner | Public API it must expose |
|---|---|---|
| `core/model/LauncherSettings.kt`, `core/repo/BackupRepository.kt`, `core/repo/LayoutRepository.kt`, `core/AppGraph.kt`, `data/` (incl. `data/NotImplementedBackupRepository.kt`, `LayoutRepositoryImpl`, `LayoutDao`), this file | Contracts | The backup / accessibility / privacy contracts below. `AppGraphImpl.backup` is a placeholder (`NotImplementedBackupRepository`) |
| `backup/` | Backup | `class FoldOsBackupRepository(...) : BackupRepository` (replaces the placeholder on the `backup` line of `AppGraphImpl`), export / import UI (scheduled auto export was not built; `backupAuto` is a reserved, unused setting). **May also edit `settings/BackupPage.kt`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`** |
| `perf/` | Performance | Startup work (lazy init, `Application.onCreate` slimming), baseline profile and benchmarks. **May also edit `app/build.gradle.kts`, add `app/src/main/baseline-prof.txt` and edit `app/proguard-rules.pro`** |
| `a11y/` | Accessibility | Helpers and settings for `highContrast`, `reduceMotion`, `textScale`, `largeIcons`, `greyscale`. **May edit `home/`, `drawer/`, `dock/`, `settings/` for semantics (content descriptions, roles, touch targets, focus order, animation gating) only** |
| `release/` | Release | Release build type, signing config (release keystore from env/secrets with the debug keystore as fallback), `versionName = "1.0.0"`, final `README.md`, `CHANGELOG.md`, GitHub Actions release job. Edits `app/build.gradle.kts` signing / buildTypes / versioning and `.github/workflows/` |

Backup, perf and release all touch `app/build.gradle.kts`: perf owns dependencies / plugins / baseline-profile wiring, release owns `signingConfigs`, `buildTypes` and `versionName` / `versionCode`; neither rewrites the other's blocks.

#### Phase 6 contracts

- `core/repo/BackupRepository.kt`: `interface BackupRepository { suspend fun exportJson(): String; suspend fun importJson(json: String): ImportResult }`; `data class ImportResult(ok: Boolean, message: String, itemsImported: Int)` with `ImportResult.failure(message)`.
  - Backup document (all `@Serializable`, same file): `BackupFile(version: Int = 1, createdAt: Long, settings: LauncherSettings, pages: List<BackupPage>, iconOverrides: Map<String, String> = emptyMap())`; `BackupPage(screen: String /* ScreenKind.name */, index: Int, style: String /* HomeStyle.name */, items: List<BackupItem> = emptyList())`; `BackupItem(kind: String /* ItemKind.name */, col, row, spanX = 1, spanY = 1, packageName?, activityName?, title?, builtinId?, stackAnchorIndex: Int? = null, stackIndex = 0, free = false, xDp?, yDp?, wDp?, hDp?, parentIndex: Int? = null, packageName2?, activityName2?, folderStyle?)`.
  - `BackupItem` is `HomeItem` minus `id`, `pageId`, `appWidgetId`, `stackId`, `parentId`. `parentIndex` / `stackAnchorIndex` are positions in the same `BackupPage.items` list (folder children and stack mates always live on their folder's / anchor's page). Hosted app widgets (`isAppWidget`) are not exported; indexes are computed after filtering them out. Search ranking / recents are not part of the backup. `iconOverrides` maps `AppInfo.key` to the stored override value.
  - Encode / decode with `Json { ignoreUnknownKeys = true; encodeDefaults = true }` (otherwise `version` is omitted). `importJson` must reject a `version` greater than 1 and malformed JSON with `ImportResult.failure(...)` and never touch current data in that case. It applies settings through `SettingsRepository.update`, layouts through `LayoutRepository.replaceAll`, then icon overrides, and should keep this device's `wizardDone = true`.
- `AppGraph.backup: BackupRepository` (new member). `AppGraphImpl.backup` = `NotImplementedBackupRepository()` (`exportJson()` returns `"{}"`, `importJson` returns `ImportResult(false, "Backup is not implemented yet", 0)`); the Backup agent replaces that one `by lazy` line.
- `LayoutRepository` gained two methods (implemented in `LayoutRepositoryImpl`, backed by new `LayoutDao` queries `allPagesOnce`, `allItemsByPageOnce`, `deleteAllItems`, `deleteAllPages`; no schema change, Room version stays 3):
  - `suspend fun allPagesWithItems(): List<Pair<HomePage, List<HomeItem>>>`: every page of every screen (ordered by `ScreenKind` ordinal then `index`) with ALL its items, including folder children (`parentId` set), stack mates and app widgets, items in id order (a folder child can precede its folder).
  - `suspend fun replaceAll(pages: List<Pair<HomePage, List<HomeItem>>>)`: one transaction; deletes every page and item, then inserts. Input ids are local keys: pages and id-0 items get fresh row ids, `item.pageId` is overwritten with the new page id, non-zero `item.id`s are remapped so `parentId` and `stackId` follow (a reference to an unknown id is cleared and `stackIndex` reset to 0). App widget ids are not released. Callers pass any unique local number (e.g. a running counter) as `HomeItem.id` for items that are referenced by others, else 0.
- `LauncherSettings` new fields (all defaulted, stored with the rest of the JSON, no migration): `highContrast: Boolean = false`, `reduceMotion: Boolean = false`, `textScale: Float = 1.0f` (multiplier on the system font scale, expected range 0.85..1.6), `largeIcons: Boolean = false` (Easy / large mode), `greyscale: Boolean = false`, `noNetwork: Boolean = false` (hard switch: Gmail API, web search previews and AI network use all off; every network-touching feature must check `!settings.noNetwork` first), `dailyWallpaper: Boolean = false`, `backupAuto: Boolean = true` (reserved: no scheduled exporter exists and no UI reads it).

#### Phase 6 integration (done)

- `data/AppGraphImpl.kt`: `backup = backup.FoldOsBackupRepository(appContext, settings, layouts, icons)`
  (`NotImplementedBackupRepository` stays as an unused fallback). `core/FoldOsApplication.onCreate` builds the graph inside
  `perf.StartupTracker.section("graphInit")` and then calls `wallpaper.DailyWallpaper.start(this, graph)`.
- Settings navigation: `SettingsRoot` routes `BackupRows.ROUTE` ("backup") and `AccessibilityRows.ROUTE` ("accessibility") and
  accepts both as `startRoute`; `SettingsPages.all` lists `BackupRows.page` and `AccessibilityRows.page` (after AI, before
  Features), so their rows are searchable. Neither route is in `SettingsRoutes`. Quick setup keeps its Export / Import rows (they
  call `backup.rememberBackupActions()` directly) and has a "More backup options" row, `QuickSetupPage(onOpenBackup = ...)`,
  that opens the Backup page. "Reset all settings" now keeps `wizardDone`.
- Window roots: `MainActivity` composes `FoldOsTheme { A11yRoot(settings) { LauncherRoot } }` and `ReportFullyDrawnOnFirstFrame`;
  `SettingsActivity` and `OnboardingActivity` also wrap in `A11yRoot`. `A11yRoot` provides `LocalReduceMotion`,
  `LocalHighContrast`, `LocalGreyscale`, `LocalLargeIcons`, multiplies the font scale by `settings.textScale` and renders the content
  in greyscale; `FoldOsTheme` applies `highContrast` to the tokens. The `ModalBottomSheet`s pass `Modifier.a11yGreyscale()`; other
  separate windows (`Popup` menus, `Dialog`, `AlertDialog`) are not covered by the greyscale effect yet.
- Hardware keyboard: `LauncherHome` provides `a11y.LocalKeyboardShortcuts` around `HomeScreen`; `onTypeToSearch` is set while the
  Search feature is on and no overlay / edit mode is up and calls `LauncherViewModel.openSearch(initialText)`. The typed character
  arrives in `search.UniversalSearch(initialQuery = viewModel.searchSeed)`, which puts the caret after it. `HomeScreen` gets
  `keyboardRefocusKey = Triple(drawerOpen, pickerOpen, searchOpen)`.
- No-network mode (`LauncherSettings.noNetwork`, Settings > Features > Privacy): `mail.GmailMailRepository` reports
  `MailState.Error("No-network mode is on...")`, skips `connect` and `loadInbox`, and refreshes again when the switch goes off;
  `search.webSearchOn()` (Web provider on AND not offline) replaces every `searchProvider(SEARCH_WEB)` check in `search/`
  (web / URL / bang rows, the Lens button, the "web" placeholder); `ai.FoldOsAiCommandRouter` never selects an
  `ai.NetworkAiProvider` while offline (the template engine and `AiCoreProvider` run on the device and are unaffected). Any new
  code that talks to the network must check `!settings.noNetwork` first. Web results still open the browser on tap.
- Daily wallpaper (`LauncherSettings.dailyWallpaper`, off by default, Settings > Look): `wallpaper.DailyWallpaperGenerator`
  (gradient seeded by `LocalDate.toEpochDay()`, no assets), `wallpaper.DailyWallpaper` (`start`, `applyIfDue`, `schedule`, `cancel`;
  `AlarmManager.setAndAllowWhileIdle` shortly after local midnight, `WallpaperManager.setBitmap(.., FLAG_SYSTEM)`, last day kept in
  the `foldos_daily_wallpaper` prefs, not backed up) and `wallpaper.DailyWallpaperReceiver` (the alarm plus BOOT_COMPLETED,
  MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED). No WorkManager.
- Manifest: `SET_WALLPAPER`; receiver `wallpaper.DailyWallpaperReceiver` (not exported). No other component changes.
- Build: `androidx.profileinstaller` 1.4.1 is a dependency and `app/src/main/baseline-prof.txt` is picked up by AGP (rules cover the
  startup packages including `a11y/`; `settings/`, `onboarding/`, `backup/`, `wallpaper/` are class-only). Release uses R8
  (`-Pfoldos.minify=false` bisects), debug-keystore fallback signing, versionName 1.0.0 / versionCode 100.
- Docs: `README.md`, `CHANGELOG.md` (1.0.0) and `docs/TESTING.md` describe all of the above.


### Fix round 1.1 (device feedback on 1.0.0; see `app/DIAGNOSIS.md`)

Contracts-owned changes (everything below already exists in `core/` and `data/`; the agents only consume it).

| Package / path | Owner | Task |
|---|---|---|
| `core/ui/Drag.kt`, `core/model/ColorSchemeConfig.kt`, `core/repo/LayoutRepository.kt`, `data/DockAppsStore.kt`, `LayoutRepositoryImpl` (`placeApp`, `setDockApps`, `dockApps`), this file, `DIAGNOSIS.md` | Contracts | Shared drag-and-drop, light/dark color families, dock apps + `placeApp` |
| `home/` | Home | Hide indicator + dock on the hub page; swipe-up detector on the whole `HomeScreen` root; grid `dragTarget` |
| `dock/` | Dock | Always an "All apps" button; `dragTarget(DOCK)`; reorder / remove |
| `drawer/` | Drawer | App pop-up with group folders and `dragSource` tiles |
| `edit/` | Edit | `Modifier.editModeScale`, overlay geometry for the scaled home, Remove chip |
| `search/` | Search | Full-screen opaque search surface, fade only, `imePadding()` |
| `settings/`, wizard color step | Settings | Sample-based pages; color families |
| `theme/` | Theme | `ColorTone` aware `recolor` |
| `data/LayoutRepositoryImpl.kt` seeding | Data | One default page per screen; no re-seeding |
| `LauncherRoot.kt`, `LauncherViewModel.kt`, `AndroidManifest.xml` | Integrate | Provide the controller, dock apps flow, edit scale, `searchBar = null` with the dock search add-on, `adjustNothing` |

#### Drag and drop (`core/ui/Drag.kt`, package `com.foldos.launcher.core.ui`)

- `sealed interface DragPayload { data class App(val app: AppInfo); data class Item(val item: HomeItem) }`.
- `class DragController` (`@Stable`): `val payload: State<DragPayload?>`, `val pointerInRoot: State<Offset>`, `val hoverTargetId: State<String?>`
  (the accepting target under the pointer), `val isDragging: Boolean`, `fun start(payload, pointerInRoot)`, `fun move(pointerInRoot)`,
  `fun drop(): Boolean` (true when a target took the payload), `fun cancel()`, `fun registerTarget(id: String, bounds: () -> Rect?, accepts: (DragPayload) -> Boolean, onDrop: (DragPayload, Offset) -> Unit)`,
  `fun unregisterTarget(id: String)`.
  - All positions are root coordinates (`localToRoot` / `boundsInRoot()`); the launcher fills the window, so they are window coordinates.
  - `drop()` hit-tests the registered targets, **last registered first** (re-registering an id keeps its place); the first whose `bounds()` contains the pointer and whose
    `accepts(payload)` is true gets `onDrop(payload, pointerInRoot)`. The state is cleared **before** `onDrop` runs. No hit = the payload is discarded and `drop()` returns false.
  - `start` replaces a drag in progress; `move` without a drag is a no-op.
- `val LocalDragController = staticCompositionLocalOf<DragController> { error(...) }`; `@Composable fun rememberDragController()`. The Integrate agent provides it around the whole launcher.
- `object DragTargetIds { DOCK = "dock"; HOME_GRID = "home-grid"; REMOVE = "remove" }`: the dock registers `DOCK`, the settled home page's item grid registers `HOME_GRID`
  (`onDrop` maps the pointer to a cell with `GridGeometry.cellAt` on dp, i.e. `pointer / density`, then the Integrate agent's view-model function calls `LayoutRepository.placeApp` or `moveItem`),
  an optional remove strip registers `REMOVE`.
- Helpers in the same file: `Modifier.dragSource(controller, payload: () -> DragPayload?, enabled = true, onStarted = {})` (long press, then drag; consumes moves once started; the
  node must stay composed until the drop, so a host such as the app pop-up hides with `graphicsLayer { alpha = 0f }` while `controller.isDragging` instead of leaving composition),
  `Modifier.dragTarget(controller, id, accepts = { true }, onDrop)` (registers while composed; lambdas are read fresh at drop), `DragGhostLayer(controller, modifier, ghost: @Composable (DragPayload) -> Unit)`
  (draws the ghost centred on the pointer; compose last in a window-filling `Box`; never takes touches).

#### Dock apps and `placeApp` (`LayoutRepository`, implemented in `data/`)

- `suspend fun placeApp(app: AppInfo, pageId: Long, col: Int, row: Int, cols: Int? = null, rows: Int? = null, growsRows: Boolean = true): Boolean`: inserts a 1 x 1 APP item at the cell, moves nothing; if that cell is
  covered it uses the nearest free cell (smallest |dcol| + |drow|, ties lower row then lower column) inside `cols` x `rows` (pass `GridGeometry.cols` / `rows`; null searches the
  rectangle of the requested cell and the occupied cells plus one extra row). When the bounded grid is full the item goes to column 0 of the row below the lowest item when `growsRows`; with `growsRows = false` and `rows` given (fixed-row grid pages) nothing is stored and the result is false
  (dashboard / hybrid pages grow, like `firstFreeCell`). Dropping onto an APP / FOLDER cell is the caller's decision (`Folders.createFromDrop` / `addToFolder`, as `LauncherViewModel.moveItem`
  does); call `placeApp` only for free cells.
- `suspend fun setDockApps(screen: ScreenKind, appKeys: List<String>)` and `fun dockApps(screen: ScreenKind): Flow<List<String>>`: the ordered `AppInfo.key` list of the dock per
  `ScreenKind` (the value passed, FLEX is stored separately from INNER; use `effectiveScreen` as the key everywhere). Empty list = use the default dock apps (`defaultDockApps`); `setDockApps`
  with an empty list clears the stored value; duplicates and blanks are dropped; keys of uninstalled apps stay stored and are skipped when resolving.
  The dock shows at most `DockDefaults.pinnedCount(config, screen)` resolved apps, any extra ones fill the folder add-on, as before.
- Persistence: `data/DockAppsStore.kt` (Preferences DataStore file `foldos_dock`, key `dock_<ScreenKind>`, newline-joined keys). `LayoutRepositoryImpl(db, dockStore)`; `AppGraphImpl`
  builds `LayoutRepositoryImpl(database, DockAppsStore(appContext))`. No Room schema change (version stays 3). The backup file does not include dock apps.

#### Light and dark color families (`core/model/ColorSchemeConfig.kt`)

- `enum class ColorTone { LIGHT, DARK }`; `ColorSchemePreset(id, name, accent, wall1, wall2, family = id, tone = DARK)`; `ColorFamily(id, name, light, dark)` with `forTone(tone)`.
- `ColorSchemes.families`: twelve primary colors (ocean, teal, forest, lime, gold, sunset, red, rose, violet, indigo, slate, mono), each with a `-dark` (the original deep mock-up colors) and a `-light` (pale tints) preset;
  `ColorSchemes.presets` = every family's dark then light preset (24); `byId` accepts `"ocean-dark"`, `"ocean-light"` and the legacy `"ocean"` (= dark); `familyById`; `resolve(config)` unchanged.
  `ColorSchemeConfig.scheme` stores the exact preset id.

#### Fix round 1.1 integration (done)

- `LauncherRoot.kt`: `LocalDragController` and `DragGhostLayer` around the whole root; `EditScaledHome(active = editMode)` wraps `HomeScreen` and the rail dock (the wallpaper stays outside, full size);
  a touch sink below `EditModeOverlay` while editing; `Dock(... dragController, onDockAppsChange = setDockApps, onHomeItemTakenIntoDock = deleteItem)`; `AppDrawer(... dragController, onAddToHome, onAddToDock)`;
  `EditModeOverlay(... onRestoreItem = restoreItem, selectRequestId)`; `searchBar = null` when the bottom dock has the search add-on. `HomeCallbacks.onItemLongPress` records the item as the edit selection.
- Drop targets: each GRID / DASHBOARD page registers `page:<pageId>` (`HomeCellGrid(dropTarget = HomeGridDrop)`), list / hybrid pages register their body under the same id (cell -1 / -1), the dock registers
  `DragTargetIds.DOCK`. `DragTargetIds.HOME_GRID` / `REMOVE` are not used. `LauncherViewModel.dropOnPage` maps a drop to `moveItem` (tile) or `placeApp` / folder create / add (app).
- Edit scale: `edit/EditScale.kt` exports `EDIT_SCALE`, `EditChrome`, `EditFrame`, `rememberEditChrome()`, `EditScaledHome`, `LocalEditFrame` and `LocalEditHomeRoot`. `HomeCellGrid` publishes its geometry relative to the
  home root inside the scaling layer (`localPositionOf`), so `GridGeometry` is always in UNSCALED window dp and `EditModeOverlay` maps it through the same `EditFrame`.
- Undo: `LauncherViewModel.deleteItem(item)` keeps a removed app widget's host id for `UNDO_WINDOW_MS + 1.5 s` and then releases it; `restoreItem(item)` writes the item back (same id) and cancels the release.
  `addAppToHome(app, pageId, style, geometry)` backs the pop-up menu's "Add to home screen". `dockApps(screen)` / `setDockApps(screen, keys)` use `effectiveScreen` as the key.
- `AndroidManifest.xml`: `MainActivity` has `windowSoftInputMode="adjustNothing"`; the search surface and the pop-up handle the keyboard with `imePadding()`.

#### Phase 2+3 integration (done)

- `data/AppGraphImpl.kt`: `builtIns = FoldOsBuiltInWidgets(appContext)`, `widgets = FoldOsWidgetCatalog(...)`,
  `icons = FoldOsIconResolver(appContext, settings)`, `apps = AppsRepositoryImpl(appContext) { icons }` (every
  `AppInfo.iconLoader` goes through the icon pack / shape resolver). The `data/` placeholders stay as unused fallbacks.
- `MainActivity`: provides `LocalAppGraph` and `builtins.LocalPermissionRequester` (backed by
  `ActivityResultContracts.RequestPermission`), forwards the deprecated `onActivityResult` to
  `WidgetHostController.from(this, graph)` (app-widget configure results), and calls
  `appWidgetHost.startListening()` / `stopListening()` in `onStart` / `onStop` (`AppGraphImpl` no longer starts listening, so processes without an Activity do not).
- `LauncherViewModel`: edit mode, widget picker and current-page state; layout mutations (`moveItem` turns a drop onto
  another widget into `createStack` when the Stacks feature is on, `resizeItem`, `deleteItem` also releases the app
  widget id, `addPage` / `setPageStyle` / `deletePage`, `addBuiltInWidget` / `addAppWidget` at the first free cell);
  propagates Settings > Home > style changes to every page of that screen; clears `home.HomeIconCache` when
  `FoldOsIconResolver.generation` changes.
- `LauncherRoot`: shows `onboarding.SetupWizard` until `settings.wizardDone` (only once the stored settings have
  loaded); otherwise the home with `edit.EditModeOverlay` (geometry from `home.rememberGridGeometry`, items of the
  settled page), `ThemeQuickSheet`, `WallpaperSheet`, `picker.WidgetPicker` (built-ins placed directly; app widgets
  through `widgets.rememberAddAppWidgetFlow`) and the drawer. `HomeScreen` is composed with `perPageStyle = true` and
  the new `onCurrentPageChanged` / `pageSelectRequest` parameters (`home.PageSelectRequest`).
- `home.HomeBuiltInIds` now equal the `FoldOsBuiltInWidgets` ids (`"foldos.<name>"`); `FoldOsBuiltInWidgets.byId`
  also accepts the bare name for rows stored before the prefix.
- `LayoutRepository.ensureDefaultLayout(screen, apps, columns, style)` gained an optional `style` (the wizard's
  choice for the seeded pages); `LayoutRepositoryImpl` seeds a dashboard first page with `home.defaultDashboardItems`
  instead of apps, and fills any empty dashboard page with them. `LayoutDao` gained `countItemsForPage` and
  `itemsByPageOnce` (no schema change).
- Settings: `SettingsActivity.intent(context, route)` opens a page directly (`SettingsRoot(startRoute)`); Quick setup
  rows start `onboarding.OnboardingActivity` (wizard / gallery); Look has Icon pack and Icon shape rows opening
  `icons.IconPackPicker` / `IconShapePicker`; Widgets has "Reset recent widgets" (`FoldOsWidgetCatalog.clearRecents`).
- Manifest: `BIND_APPWIDGET` (ignored lint), `READ_CALENDAR`, `READ_MEDIA_IMAGES`, `BLUETOOTH_CONNECT`,
  `APPWIDGET_UPDATE` query, `onboarding.OnboardingActivity`, `builtins.FoldOsNotificationListener` service.

### Fix round 1.3 (remove profiles)

Contracts-owned change; the Profiles feature is gone end to end.

- Removed: `core.model.Profile`, `core.repo.ProfileRepository`, `AppGraph.profiles`, `data.InMemoryProfileRepository`, the `profiles/` package
  (`FoldOsProfileRepository`, `ProfileScheduler`, `ProfileAlarmReceiver`, `ProfileBootReceiver`, `ProfileAutoSwitchEffect`,
  `ProfileSwitcherSheet`, `ProfileEditor`), `settings/ProfilesPage.kt` (`ProfilesRows`, `SettingsRoutes.PROFILES`), the Screens "Profiles" /
  "Active profile" rows, the Features "Profiles" row, `LauncherSettings.FEATURE_PROFILES` (and its `ALL_FEATURES` entry),
  `LauncherSettings.profile`, `LauncherSettings.activeProfile`, `BackupFile.profiles`, `EditModeOverlay(onOpenProfiles = ...)` and
  `EditTags.ACTION_PROFILES`. `FoldOsBackupRepository(context, settings, layouts, icons, apps)` no longer takes a profile repository.
- Compatibility: `LauncherSettings` JSON (DataStore `foldos_settings`) and `BackupFile` JSON are decoded with `ignoreUnknownKeys = true`, so
  stored settings or older backups that still contain `profile`, `activeProfile`, `features.profiles` or a `profiles` list load normally and
  the keys are dropped on the next write. The DataStore file names are unchanged (the unused `foldos_profiles` store is no longer in the
  backup rules).
- Hidden apps are independent of profiles (`LauncherSettings.hiddenApps`) and keep their own page: `settings.HiddenAppsPage` /
  `HiddenAppsRows.ROUTE` ("hidden_apps"), listed in `SettingsPages.all` (Settings > More settings > Hidden apps), checkbox per installed app
  from `graph.apps.allApps`.
- Manifest: `ProfileAlarmReceiver`, `ProfileBootReceiver` and `SCHEDULE_EXACT_ALARM` are gone. `RECEIVE_BOOT_COMPLETED` stays for
  `wallpaper.DailyWallpaperReceiver` (the daily alarm is inexact, `setAndAllowWhileIdle`).
- `dock/Dock.kt`: the dock folder pop-up title used `settings.profile`; it is now the literal "More apps" (one-line change outside the Contracts-owned files).

Also in round 1.3 (1.2.0, `versionCode` 120):
- `LauncherSettings.labelSize: String = "medium"` ("small" 11sp / "medium" 12sp / "large" 14sp). `home/AppLabelStyle.kt` owns the shared label
  style: `AppLabelStyle`, `LocalLabelSizeSp`, `ProvideLabelSize(settings)` (called by `HomeScreen`, `AppDrawer`, `Dock`), `appLabelTextStyle`,
  `AppLabel`, `appLabelBlockHeight`. Every app / folder / pair name under an icon (home, drawer, dock, folder sheet, pair editor) uses `AppLabel`.
- `picker/WidgetPicker` keeps its signature (`visible, screen, settings, onPick, onDismiss, modifier`) and `rememberWidgetPickerBackdropModifier`;
  it is now a two-level picker (`WidgetPickerList` level 1, `WidgetPickerDetail` level 2 with a `HorizontalPager`, `WidgetPickerModel`,
  `WidgetPickerParts`, `WidgetPreviews`; `WidgetPreviewTile.kt` is gone). "Add to home" calls `markUsed`, `onPick(entry)`, `onDismiss()`.
  `settings.WidgetsPage` is the grouping choice (`wpGroup`) plus Reset recent widgets (`WidgetsRows.groupOptions` and the stacks / smart /
  integrations / theme rows are removed; stacks and smart stay on Features).

## Shared contracts (owned by Contracts)

All under `app/src/main/java/com/foldos/launcher/core/`.

### model/Enums.kt
- `enum class ScreenKind { COVER, INNER, FLEX }`
- `enum class ScreenMode { SAME, DIFFERENT, CONTINUITY }`
- `enum class HomeStyle { GRID, DASHBOARD, LIST, HYBRID }`
- `enum class GridMode { GRID, FREE }`
- `enum class ThemeId { MATERIAL, GLASS, ONEUI, BOLD }`
- `enum class Appearance { DARK, LIGHT, AUTO }`
- `enum class DrawerStyle { POPUP, FULL }`
- `enum class DockBackground { PILL, SHELF, NONE, BUBBLE, LINE, REFLECT }`
- `enum class DockPosition { BOTTOM, RAIL }`
- `enum class DockContent { ICONS, TEXT }`

### model/DockConfig.kt
- `@Serializable data class DockConfig(bg: DockBackground = PILL, pos: DockPosition = BOTTOM, content: DockContent = ICONS, labels = false, two = false, scroll = false, search = false, taskbar = false, smart = false, folder = false, media = false, notify = false, center = false, handle = false, magnify = false)` (defaults = mock-up `DOCK_DEF`)
- `data class DockPreset(id: String, name: String, description: String, config: DockConfig)`
- `object DockPresets { const val CUSTOM_ID = "custom"; val all: List<DockPreset> /* 20 presets */; fun byId(id): DockPreset?; fun configFor(id): DockConfig }`
- `fun DockConfig.matchingPresetId(): String?` (null = custom mix)

### model/ColorSchemeConfig.kt
- `@Serializable data class ColorSchemeConfig(scheme: String = "theme", accent: Long = 0xFF7AD1FF, wall1: Long = 0xFF4F46E5, wall2: Long = 0xFFDB2777)` with `SCHEME_THEME`, `SCHEME_CUSTOM` constants
- `data class ColorSchemePreset(id, name, accent: Long, wall1: Long, wall2: Long, family = id, tone: ColorTone = DARK)`, `enum class ColorTone { LIGHT, DARK }`, `data class ColorFamily(id, name, light, dark)` (Fix round 1.1: every primary color has a light and a dark preset)
- `object ColorSchemes { val families: List<ColorFamily> /* ocean, forest, sunset, rose, gold, violet, teal, red, mono */; val presets: List<ColorSchemePreset> /* "<family>-dark" and "<family>-light" */; fun byId(id): ColorSchemePreset? /* legacy "ocean" = dark */; fun familyById(id): ColorFamily?; fun resolve(config): List<Long>? }`

### model/LauncherSettings.kt
- `@Serializable data class LauncherSettings(screenMode = DIFFERENT, appearance = DARK, styleCover = GRID, styleInner = DASHBOARD, theme = GLASS, gridMode = GRID, colsCover = 4, colsInner = 6, iconSizeDp = 58, radiusDp = 22, labels = true, dockCover: DockConfig = pill, dockInner: DockConfig = taskbar, drawerStyle = POPUP, popBlur = true, hubOn = true, hubPos = "left", hubBar = "bottom", hubTabs: Set<String> = {ai, mail, cal, people}, colors: ColorSchemeConfig = ColorSchemeConfig(), wpGroup = "category", features: Map<String, Boolean> = {search, dock, badges, gestures, pairs, ai, stacks, smart, drawerTabs -> true}, iconPack: String? = null, iconShape: IconShape = IconShape.SYSTEM, wizardDone: Boolean = false, preset: String? = null, hiddenApps: Set<String> = emptySet(), gestures: Map<String, GestureAction> = DEFAULT_GESTURES, searchEngine: String = "google", searchProviders: Set<String> = ALL_SEARCH_PROVIDERS.toSet(), badgeStyle: String = "count", mailClientId: String? = null, aiMode: String = "template", folderStyle: String = "grid", notificationPreview: Boolean = true, highContrast: Boolean = false, reduceMotion: Boolean = false, textScale: Float = 1.0f, largeIcons: Boolean = false, greyscale: Boolean = false, noNetwork: Boolean = false, dailyWallpaper: Boolean = false, backupAuto: Boolean = true)`
  - `hiddenApps`: package names (an `AppInfo.key` "package/activity" is accepted too); `AppsRepository.apps` already excludes them.
  - `gestures`: keyed by `GestureKind.name`; missing key = `GestureAction.NONE`. `DEFAULT_GESTURES` = SWIPE_UP→drawer, SWIPE_DOWN→notifications, DOUBLE_TAP→none, PINCH_IN→edit, SWIPE_UP_2F→search, UNFOLD→none.
  - `searchEngine` ∈ `SEARCH_ENGINES` = `SEARCH_ENGINE_GOOGLE/BING/DUCKDUCKGO/KAGI/BRAVE/ECOSIA` ("google", "bing", "duckduckgo", "kagi", "brave", "ecosia").
  - `searchProviders` ⊆ `ALL_SEARCH_PROVIDERS` = `SEARCH_APPS/CONTACTS/SETTINGS/FILES/CALENDAR/WEB/CALCULATOR/UNITS` ("apps", "contacts", "settings", "files", "calendar", "web", "calculator", "units"); default = all.
  - `badgeStyle` ∈ `BADGE_COUNT` "count" / `BADGE_DOT` "dot" / `BADGE_OFF` "off"; `aiMode` ∈ `AI_MODE_TEMPLATE` "template" / `AI_MODE_ONDEVICE` "ondevice" / `AI_MODE_OFF` "off"; `folderStyle` ∈ `FOLDER_STYLES` = `FOLDER_GRID` "grid" / `FOLDER_LIST` "list" / `FOLDER_BIG` "big".
  - `mailClientId` = OAuth client id for the Mail tab (null = not configured). Old stored JSON that still has `profile`, `activeProfile` or a `profiles` feature key loads normally (`ignoreUnknownKeys`; a stale `features["profiles"]` entry is ignored).
- Helpers: `feature(key): Boolean`, `withFeature(key, enabled)`, `dockFor(screen)`, `styleFor(screen)`, `colsFor(screen)`, `gesture(kind: GestureKind): GestureAction`, `withGesture(kind, action)`, `isHidden(packageName, activityName: String? = null)`, `searchProvider(key): Boolean`, `withSearchProvider(key, enabled)`; companion constants `HUB_POS_*`, `HUB_BAR_*`, `HUB_TAB_*`, `WP_GROUP_*`, `FEATURE_*`, `ALL_FEATURES`, `DEFAULT_GESTURES`, `SEARCH_ENGINE_*`, `SEARCH_ENGINES`, `SEARCH_*` providers, `ALL_SEARCH_PROVIDERS`, `BADGE_*`, `AI_MODE_*`, `FOLDER_*`, `FOLDER_STYLES`
- `wpGroup` values equal `WidgetCatalog.GROUP_CATEGORY` / `GROUP_APP` / `GROUP_RECENT`; pass it straight to `WidgetCatalog.grouped(by)`.
- Stored as one JSON string (`ignoreUnknownKeys`, `encodeDefaults`), so new fields with defaults never need a migration.
### model/AppInfo.kt
- `class AppInfo(packageName: String, activityName: String, label: String, user: android.os.UserHandle, iconLoader: () -> android.graphics.drawable.Drawable)`; `val key: String` = "package/activity"; equality ignores `iconLoader`

### model/Layout.kt
- `data class HomePage(id: Long, screen: ScreenKind, index: Int, style: HomeStyle)`
- `enum class ItemKind { APP, FOLDER, WIDGET, PAIR }`
- `data class HomeItem(id: Long, pageId: Long, kind: ItemKind, col: Int, row: Int, spanX: Int = 1, spanY: Int = 1, packageName: String? = null, activityName: String? = null, title: String? = null, appWidgetId: Int? = null, builtinId: String? = null, stackId: Long? = null, stackIndex: Int = 0, free: Boolean = false, xDp: Float? = null, yDp: Float? = null, wDp: Float? = null, hDp: Float? = null, parentId: Long? = null, packageName2: String? = null, activityName2: String? = null, folderStyle: String? = null)`
  - helpers `isAppWidget` (`kind == WIDGET && appWidgetId != null`), `isBuiltInWidget` (`kind == WIDGET && builtinId != null`), `isFolder` (`kind == FOLDER`), `isPair` (`kind == PAIR` with both second-app fields set), `inFolder` (`parentId != null`)
  - a widget item sets exactly one of `appWidgetId` (from `graph.appWidgetHost.allocateAppWidgetId()`) or `builtinId` (`BuiltInWidget.id`)
  - stack: items sharing `stackId` occupy one slot (same page/col/row/span) and flip in `stackIndex` order; `stackId` == the anchor item's row id
  - free placement: `free = true` means position/size come from `xDp/yDp/wDp/hDp` (dp from the page's top-left); grid fields remain as the fallback
  - folder: a FOLDER item is the container placed on the grid (`title`, optional `folderStyle` override "grid" / "list" / "big", null = `LauncherSettings.folderStyle`); its children have `parentId = folder.id`, keep the folder's `pageId` (so a page delete cascades) and are **not** returned by `LayoutRepository.items(pageId)`, only by `children(folderId)`; inside a folder `row` is the child's order and `col` is 0
  - pair: a PAIR item holds app A in `packageName` / `activityName` and app B in `packageName2` / `activityName2`; `title` defaults to "A & B"
- `enum class GestureKind { SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT_2F, SWIPE_RIGHT_2F, SWIPE_UP_2F, SWIPE_DOWN_2F, DOUBLE_TAP, PINCH_IN, PINCH_OUT, HOME_DOUBLE, FOLD, UNFOLD }`
- `@Serializable data class GestureAction(type: String = TYPE_NONE, target: String? = null)`; `type` ∈ `TYPE_NONE` "none", `TYPE_DRAWER` "drawer", `TYPE_SEARCH` "search", `TYPE_HUB` "hub", `TYPE_SETTINGS` "settings", `TYPE_NOTIFICATIONS` "notifications", `TYPE_QUICK_SETTINGS` "quick_settings", `TYPE_APP` "app", `TYPE_EDIT` "edit", `TYPE_LOCK_HINT` "lock_hint", `TYPE_RECENTS_HINT` "recents_hint" (`ALL_TYPES` in that order); `target` = `AppInfo.key` for `TYPE_APP`, else null. Companion `NONE`, `fun app(appKey): GestureAction`.
- `enum class WidgetCategory(displayName: String, iconName: String) { CLOCK("Clock & Time","schedule"), WEATHER("Weather","partly_cloudy_day"), CALENDAR("Calendar & Tasks","event"), COMMUNICATION("Communication","chat"), MEDIA("Media & Audio","music_note"), PHOTOS("Photos","photo_library"), PRODUCTIVITY("Productivity & Notes","edit_note"), HEALTH("Health & Fitness","favorite"), DEVICE("Device & System","smartphone"), SMART_HOME("Smart Home","home"), TRAVEL("Travel & Maps","map"), FINANCE("Finance","payments"), NEWS("News & Reading","newspaper"), SEARCH("Search & Shortcuts","search"), SOCIAL("Social","group"), UTILITIES("Utilities","build"), FUN("Fun & Personal","mood") }` (mock-up `CATS` / `CATICON`); `companion fun byDisplayName(name): WidgetCategory?`. `iconName` is the Material Symbols name; UI maps it to an `Icons.*` vector itself.
- `data class WidgetEntry(id: String, name: String, source: String, category: WidgetCategory, minSpanX: Int, minSpanY: Int, defaultSpanX: Int, defaultSpanY: Int, isBuiltIn: Boolean, provider: android.content.ComponentName? = null, previewLoader: (() -> android.graphics.drawable.Drawable?)? = null, descriptionLoader: (() -> String?)? = null)` — `id` = `ComponentName.flattenToString()` for app widgets, `BuiltInWidget.id` for built-ins; `source` = app label or "FoldOS built-in"

### repo/SettingsRepository.kt
- `interface SettingsRepository { val settings: Flow<LauncherSettings>; suspend fun update(transform: (LauncherSettings) -> LauncherSettings) }`

### repo/LayoutRepository.kt
- `interface LayoutRepository {`
  - `fun pages(screen: ScreenKind): Flow<List<HomePage>>`
  - `fun items(pageId: Long): Flow<List<HomeItem>>` (items on the grid, ordered by row, col, stackIndex; folder children excluded)
  - `fun children(folderId: Long): Flow<List<HomeItem>>` (items with `parentId == folderId`, ordered by row then col)
  - `suspend fun ensureDefaultLayout(screen: ScreenKind, apps: List<AppInfo>, columns: Int? = null, style: HomeStyle? = null)` (`columns` = the grid column count the seeded items are laid out for, normally `settings.colsFor(screen)`; `style` = the seeded pages' style, normally `settings.styleFor(screen)`; null = implementation default. A DASHBOARD first page is seeded with `home.defaultDashboardItems` instead of apps)
  - `suspend fun upsertItem(item: HomeItem)` (id 0 = assign one)
  - `suspend fun deleteItem(id: Long)` (a FOLDER takes its children with it)
  - `suspend fun moveItem(id: Long, pageId: Long, col: Int, row: Int)` (stack mates move too; a folder's children follow it to the new page)
  - `suspend fun resizeItem(id: Long, spanX: Int, spanY: Int)` (stack mates resize too; spans clamped to >= 1)
  - `suspend fun addPage(screen: ScreenKind, style: HomeStyle): HomePage` (appended after the last page)
  - `suspend fun setPageStyle(pageId: Long, style: HomeStyle)`
  - `suspend fun deletePage(pageId: Long)` (cascades to items, re-indexes remaining pages 0..n-1)
  - `suspend fun createStack(itemIds: List<Long>): Long` (all items take the first item's slot; returns the stack id; items already stacked are moved into the new stack)
  - `suspend fun createFolder(pageId: Long, col: Int, row: Int, title: String, itemIds: List<Long>): Long` (inserts the FOLDER item, moves `itemIds` into it in list order, clearing their stack / free placement; returns the folder id)
  - `suspend fun createPair(pageId: Long, col: Int, row: Int, a: AppInfo, b: AppInfo): Long` (inserts a PAIR item; returns its id)
  - `suspend fun removeFromFolder(itemId: Long, toPageId: Long, col: Int, row: Int)` (back onto the grid; deletes the folder when it ends up empty; no-op for an item not in a folder)
  - `suspend fun allPagesWithItems(): List<Pair<HomePage, List<HomeItem>>>` (Phase 6: all pages with all items incl. folder children and app widgets)
  - `suspend fun replaceAll(pages: List<Pair<HomePage, List<HomeItem>>>)` (Phase 6: transactional full replace; ids are local keys, see Phase 6 contracts)
  - `suspend fun placeApp(app: AppInfo, pageId: Long, col: Int, row: Int, cols: Int? = null, rows: Int? = null, growsRows: Boolean = true): Boolean` (Fix round 1.1: inserts an APP item at the cell, nearest free cell when occupied; see "Fix round 1.1")
  - `suspend fun setDockApps(screen: ScreenKind, appKeys: List<String>)` / `fun dockApps(screen: ScreenKind): Flow<List<String>>` (Fix round 1.1: ordered `AppInfo.key` list of the dock per screen, empty = default dock apps)
  - `}`

### repo/AppsRepository.kt
- `interface AppsRepository { val apps: Flow<List<AppInfo>> /* minus LauncherSettings.hiddenApps */; val allApps: Flow<List<AppInfo>> /* everything, for the Hidden apps settings page */; fun launch(app: AppInfo) }`

### repo/BadgeRepository.kt
- `data class NotificationSummary(key: String, packageName: String, title: String, text: String, postTime: Long, contentIntent: android.app.PendingIntent?)` (`key` = `StatusBarNotification.key`)
- `interface BadgeRepository { val counts: Flow<Map<String, Int>> /* packageName -> unread count; absent = 0 */; fun notificationsFor(packageName: String): Flow<List<NotificationSummary>> /* newest first */ }`

### repo/HubRepository.kt
- `data class HubEvent(id: Long, title: String, begin: Long, end: Long, allDay: Boolean, location: String?, color: Int /* ARGB */)`
- `data class HubCall(id: Long, number: String, name: String?, time: Long)`
- `data class HubContact(id: Long, name: String, phone: String?, email: String?, photoUri: String?, starred: Boolean, lookupKey: String)`
- `data class HubTask(id: Long, title: String, dueMillis: Long?, done: Boolean)`
- `interface HubRepository { fun todayEvents(): Flow<List<HubEvent>>; fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<HubEvent>>; fun missedCalls(): Flow<List<HubCall>>; fun contacts(): Flow<List<HubContact>>; fun favorites(): Flow<List<HubContact>>; fun tasks(): Flow<List<HubTask>>; suspend fun setTaskDone(id: Long, done: Boolean); suspend fun addTask(title: String, dueMillis: Long?) }` (flows emit empty lists while a permission is missing)

### repo/MailRepository.kt
- `data class MailMessage(id: String, from: String, subject: String, snippet: String, date: Long, unread: Boolean)`
- `sealed class MailState { data object NotConfigured; data object Disconnected; data object Connecting; data class Connected(messages: List<MailMessage>, unread: Int); data class Error(message: String) }`
- `interface MailRepository { val state: Flow<MailState>; suspend fun connect(activity: android.app.Activity); suspend fun disconnect(); suspend fun refresh() }`

### repo/AiProvider.kt
- `interface AiProvider { val name: String; suspend fun complete(prompt: String, context: String): String /* throws on failure */; suspend fun available(): Boolean }`
- `data class AiReply(text: String, appliedSettings: Boolean)`
- `interface AiCommandRouter { suspend fun handle(text: String): AiReply }` (settings commands are parsed and applied, everything else goes to the provider for `LauncherSettings.aiMode`)

### repo/BackupRepository.kt
- See "Phase 6 contracts" above: `BackupRepository`, `ImportResult`, `BackupFile`, `BackupPage`, `BackupItem`.

### repo/WidgetCatalog.kt
- `interface WidgetCatalog { val entries: Flow<List<WidgetEntry>>; fun grouped(by: String): Flow<List<Pair<String, List<WidgetEntry>>>>; suspend fun markUsed(id: String) }`
- `by` is `GROUP_CATEGORY` ("category": one group per `WidgetCategory` in enum order, title = `displayName`), `GROUP_APP` ("app": one group per `source`, A-Z) or `GROUP_RECENT` ("recent": a `RECENT_GROUP_TITLE` = "Recently used" group first, then the category groups). Empty groups are omitted.
- Companion constants: `GROUP_CATEGORY`, `GROUP_APP`, `GROUP_RECENT`, `RECENT_GROUP_TITLE`.

### repo/BuiltInWidgets.kt
- `data class BuiltInWidget(id: String, name: String, category: WidgetCategory, sizes: List<Pair<Int, Int>>, content: @Composable (spanX: Int, spanY: Int, modifier: Modifier) -> Unit)`; helpers `defaultSize` (first size), `minSpanX`, `minSpanY`
- `interface BuiltInWidgetRegistry { val all: List<BuiltInWidget>; fun byId(id: String): BuiltInWidget? }`

### repo/IconResolver.kt
- `data class IconPackInfo(packageName: String, label: String)`
- `@Serializable enum class IconShape { SYSTEM, CIRCLE, SQUIRCLE, ROUNDED, TEARDROP }`
- `interface IconResolver { fun icon(app: AppInfo): android.graphics.drawable.Drawable; val packs: Flow<List<IconPackInfo>>; suspend fun setPack(packageName: String?); suspend fun setShape(shape: IconShape); suspend fun override(appKey: String, packageName: String?, drawableName: String?) }` (`setPack` / `setShape` persist to `LauncherSettings.iconPack` / `iconShape`; `override` with both nulls clears the per-app override; `appKey` = `AppInfo.key`)

### fold/FoldState.kt
- `data class FoldState(kind: ScreenKind, isHalfOpened: Boolean, hingeBounds: android.graphics.Rect?, widthDp: Int, heightDp: Int)`

### AppGraph.kt
- `interface AppGraph { val settings: SettingsRepository; val layouts: LayoutRepository; val apps: AppsRepository; val widgets: WidgetCatalog; val builtIns: BuiltInWidgetRegistry; val icons: IconResolver; val badges: BadgeRepository; val hub: HubRepository; val mail: MailRepository; val ai: AiCommandRouter; val backup: BackupRepository; val appWidgetHost: android.appwidget.AppWidgetHost; val appScope: CoroutineScope; companion object { const val APP_WIDGET_HOST_ID: Int = 1024 } }`
  - `appScope` = never-cancelled `SupervisorJob() + Dispatchers.IO` scope for writes that must outlive the UI that started them
  - `appWidgetHost` = `AppWidgetHost(context, 1024)`, created and `startListening()`-ed when `AppGraphImpl` is constructed in `Application.onCreate`; never stopped. Activities use `createView(context, appWidgetId, providerInfo)`, `allocateAppWidgetId()`, `deleteAppWidgetId(id)` on it. Binding a provider to an id needs user consent (`AppWidgetManager.ACTION_APPWIDGET_BIND`) unless `bindAppWidgetIdIfAllowed` succeeds; that flow belongs to `widgets/`.
- `val LocalAppGraph: ProvidableCompositionLocal<AppGraph>` (static; throws if unprovided)

### FoldOsApplication.kt
- `class FoldOsApplication : Application() { lateinit var graph: AppGraph; companion object { fun from(context: Context): FoldOsApplication } }`; `onCreate` does `graph = AppGraphImpl(this)`

## Data layer (owned by Contracts / Data)

All under `app/src/main/java/com/foldos/launcher/data/`.

- `db/LayoutDatabase.kt`: Room **version 3**, entities `PageEntity`, `ItemEntity`, converters `LayoutConverters` (enums by name). `LayoutDatabase.MIGRATION_1_2` adds to `items`: `app_widget_id INTEGER`, `builtin_id TEXT`, `stack_id INTEGER`, `stack_index INTEGER NOT NULL DEFAULT 0`, `free INTEGER NOT NULL DEFAULT 0`, `x_dp REAL`, `y_dp REAL`, `w_dp REAL`, `h_dp REAL`. `MIGRATION_2_3` adds `parent_id INTEGER` (+ `index_items_parent_id`), `package_name_2 TEXT`, `activity_name_2 TEXT`, `folder_style TEXT`. `create(context)` adds both migrations and keeps `fallbackToDestructiveMigration(dropAllTables = true)` as the last resort. Every future schema change bumps the version and adds a `Migration`.
- `db/LayoutEntities.kt`: `PageEntity(id, screen, page_index, style)`; `ItemEntity(id, page_id, kind, grid_col, grid_row, span_x, span_y, package_name, activity_name, title, app_widget_id, builtin_id, stack_id, stack_index, free, x_dp, y_dp, w_dp, h_dp, parent_id, package_name_2, activity_name_2, folder_style)` with `toModel()` / `fromModel()`.
- `db/LayoutDao.kt`: `pagesByScreen`, `pagesByScreenOnce`, `pageById`, `firstPage`, `countPages`, `maxPageIndex`, `upsertPage`, `setPageStyle(pageId, style: String)`, `setPageIndex`, `deletePage`, `itemsByPage` / `itemsByPageOnce` (both `parent_id IS NULL`), `childrenOf(parentId): Flow`, `childrenOfOnce`, `countChildren`, `placeInFolder(id, parentId, pageId, col, row)`, `removeFromFolder(id, pageId, col, row)`, `moveChildren(parentId, pageId)`, `setFolderStyle(id, style: String?)`, `deleteChildren(parentId)`, `itemById`, `itemsByIds`, `countItemsForScreen`, `countItemsForPage`, `upsertItem`, `upsertItems`, `moveItem`, `moveStack`, `resizeItem`, `resizeStack`, `placeInStack`, `deleteItem`, `allPagesOnce`, `allItemsByPageOnce`, `deleteAllItems`, `deleteAllPages` (Phase 6).
- `LayoutRepositoryImpl(db: LayoutDatabase, dockStore: DockAppsStore) : LayoutRepository` implements every method above (including `children`, `createFolder`, `createPair`, `removeFromFolder`); extra public `suspend fun upsertPage(page: HomePage): Long` and `suspend fun setFolderStyle(folderId: Long, style: String?)`. Fix round 1.1 adds `DockAppsStore(context)` (`data/DockAppsStore.kt`: `apps(screen): Flow<List<String>>`, `suspend set(screen, appKeys)`).
- `SettingsRepositoryImpl(context)`; `AppsRepositoryImpl(context, settings: SettingsRepository? = null, iconResolver: () -> IconResolver? = { null })` — `apps` = `allApps` minus `settings.hiddenApps` (package name or `AppInfo.key`), re-emitting when the set changes; `allApps` = every launchable activity.
- Placeholders replaced in later phases: `PlaceholderWidgetCatalog(context)`, `EmptyBuiltInWidgetRegistry()`, `PassthroughIconResolver(settings)` (Phase 2+3, now unused fallbacks); Phase 4+5: `EmptyBadgeRepository()` (empty flows), `EmptyHubRepository()` (empty flows, no-op writes), `NotConfiguredMailRepository()` (`MailState.NotConfigured`, no-op calls), `TemplateAiCommandRouter(provider: AiProvider = TemplateAiProvider())` (echo reply, `appliedSettings = false`).
- `AppGraphImpl(context) : AppGraph` wires all of the above (`badges`, `hub`, `mail`, `ai` are the placeholders, one `by lazy` line each); Phase 6 adds `backup` = `NotImplementedBackupRepository()` (`data/NotImplementedBackupRepository.kt`); `appWidgetHost` is created eagerly and started in `init`.
