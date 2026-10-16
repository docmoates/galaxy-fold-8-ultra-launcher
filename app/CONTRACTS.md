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
| `core/` (everything below) | Scaffold / Contracts | Shared contracts. Read, do not edit. |
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

#### Phase 2+3 integration (done)

- `data/AppGraphImpl.kt`: `builtIns = FoldOsBuiltInWidgets(appContext)`, `widgets = FoldOsWidgetCatalog(...)`,
  `icons = FoldOsIconResolver(appContext, settings)`, `apps = AppsRepositoryImpl(appContext) { icons }` (every
  `AppInfo.iconLoader` goes through the icon pack / shape resolver). The `data/` placeholders stay as unused fallbacks.
- `MainActivity`: provides `LocalAppGraph` and `builtins.LocalPermissionRequester` (backed by
  `ActivityResultContracts.RequestPermission`), forwards the deprecated `onActivityResult` to
  `WidgetHostController.from(this, graph)` (app-widget configure results), and calls
  `appWidgetHost.startListening()` / `stopListening()` in `onStart` / `onStop` on top of the eager start in `AppGraphImpl`.
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
- `data class ColorSchemePreset(id, name, accent: Long, wall1: Long, wall2: Long)`
- `object ColorSchemes { val presets: List<ColorSchemePreset> /* ocean, forest, sunset, rose, gold, mono */; fun byId(id): ColorSchemePreset?; fun resolve(config): List<Long>? }`

### model/LauncherSettings.kt
- `@Serializable data class LauncherSettings(screenMode = DIFFERENT, appearance = DARK, styleCover = GRID, styleInner = DASHBOARD, theme = GLASS, gridMode = GRID, colsCover = 4, colsInner = 6, iconSizeDp = 58, radiusDp = 22, labels = true, profile = "Work", dockCover: DockConfig = pill, dockInner: DockConfig = taskbar, drawerStyle = POPUP, popBlur = true, hubOn = true, hubPos = "left", hubBar = "bottom", hubTabs: Set<String> = {ai, mail, cal, people}, colors: ColorSchemeConfig = ColorSchemeConfig(), wpGroup = "category", features: Map<String, Boolean> = {search, dock, badges, gestures, pairs, ai, stacks, smart, drawerTabs, profiles -> true}, iconPack: String? = null, iconShape: IconShape = IconShape.SYSTEM, wizardDone: Boolean = false, preset: String? = null)`
- Helpers: `feature(key): Boolean`, `withFeature(key, enabled)`, `dockFor(screen)`, `styleFor(screen)`, `colsFor(screen)`; companion constants `HUB_POS_*`, `HUB_BAR_*`, `HUB_TAB_*`, `WP_GROUP_*`, `FEATURE_*`, `ALL_FEATURES`
- `wpGroup` values equal `WidgetCatalog.GROUP_CATEGORY` / `GROUP_APP` / `GROUP_RECENT`; pass it straight to `WidgetCatalog.grouped(by)`.
- Stored as one JSON string (`ignoreUnknownKeys`, `encodeDefaults`), so new fields with defaults never need a migration.

### model/AppInfo.kt
- `class AppInfo(packageName: String, activityName: String, label: String, user: android.os.UserHandle, iconLoader: () -> android.graphics.drawable.Drawable)`; `val key: String` = "package/activity"; equality ignores `iconLoader`

### model/Layout.kt
- `data class HomePage(id: Long, screen: ScreenKind, index: Int, style: HomeStyle)`
- `enum class ItemKind { APP, FOLDER, WIDGET, PAIR }`
- `data class HomeItem(id: Long, pageId: Long, kind: ItemKind, col: Int, row: Int, spanX: Int = 1, spanY: Int = 1, packageName: String? = null, activityName: String? = null, title: String? = null, appWidgetId: Int? = null, builtinId: String? = null, stackId: Long? = null, stackIndex: Int = 0, free: Boolean = false, xDp: Float? = null, yDp: Float? = null, wDp: Float? = null, hDp: Float? = null)`
  - helpers `isAppWidget` (`kind == WIDGET && appWidgetId != null`), `isBuiltInWidget` (`kind == WIDGET && builtinId != null`)
  - a widget item sets exactly one of `appWidgetId` (from `graph.appWidgetHost.allocateAppWidgetId()`) or `builtinId` (`BuiltInWidget.id`)
  - stack: items sharing `stackId` occupy one slot (same page/col/row/span) and flip in `stackIndex` order; `stackId` == the anchor item's row id
  - free placement: `free = true` means position/size come from `xDp/yDp/wDp/hDp` (dp from the page's top-left); grid fields remain as the fallback
- `enum class WidgetCategory(displayName: String, iconName: String) { CLOCK("Clock & Time","schedule"), WEATHER("Weather","partly_cloudy_day"), CALENDAR("Calendar & Tasks","event"), COMMUNICATION("Communication","chat"), MEDIA("Media & Audio","music_note"), PHOTOS("Photos","photo_library"), PRODUCTIVITY("Productivity & Notes","edit_note"), HEALTH("Health & Fitness","favorite"), DEVICE("Device & System","smartphone"), SMART_HOME("Smart Home","home"), TRAVEL("Travel & Maps","map"), FINANCE("Finance","payments"), NEWS("News & Reading","newspaper"), SEARCH("Search & Shortcuts","search"), SOCIAL("Social","group"), UTILITIES("Utilities","build"), FUN("Fun & Personal","mood") }` (mock-up `CATS` / `CATICON`); `companion fun byDisplayName(name): WidgetCategory?`. `iconName` is the Material Symbols name; UI maps it to an `Icons.*` vector itself.
- `data class WidgetEntry(id: String, name: String, source: String, category: WidgetCategory, minSpanX: Int, minSpanY: Int, defaultSpanX: Int, defaultSpanY: Int, isBuiltIn: Boolean, provider: android.content.ComponentName? = null, previewLoader: (() -> android.graphics.drawable.Drawable?)? = null)` — `id` = `ComponentName.flattenToString()` for app widgets, `BuiltInWidget.id` for built-ins; `source` = app label or "FoldOS built-in"

### repo/SettingsRepository.kt
- `interface SettingsRepository { val settings: Flow<LauncherSettings>; suspend fun update(transform: (LauncherSettings) -> LauncherSettings) }`

### repo/LayoutRepository.kt
- `interface LayoutRepository {`
  - `fun pages(screen: ScreenKind): Flow<List<HomePage>>`
  - `fun items(pageId: Long): Flow<List<HomeItem>>` (ordered by row, col, stackIndex)
  - `suspend fun ensureDefaultLayout(screen: ScreenKind, apps: List<AppInfo>, columns: Int? = null, style: HomeStyle? = null)` (`columns` = the grid column count the seeded items are laid out for, normally `settings.colsFor(screen)`; `style` = the seeded pages' style, normally `settings.styleFor(screen)`; null = implementation default. A DASHBOARD first page is seeded with `home.defaultDashboardItems` instead of apps)
  - `suspend fun upsertItem(item: HomeItem)` (id 0 = assign one)
  - `suspend fun deleteItem(id: Long)`
  - `suspend fun moveItem(id: Long, pageId: Long, col: Int, row: Int)` (stack mates move too)
  - `suspend fun resizeItem(id: Long, spanX: Int, spanY: Int)` (stack mates resize too; spans clamped to >= 1)
  - `suspend fun addPage(screen: ScreenKind, style: HomeStyle): HomePage` (appended after the last page)
  - `suspend fun setPageStyle(pageId: Long, style: HomeStyle)`
  - `suspend fun deletePage(pageId: Long)` (cascades to items, re-indexes remaining pages 0..n-1)
  - `suspend fun createStack(itemIds: List<Long>): Long` (all items take the first item's slot; returns the stack id; items already stacked are moved into the new stack)
  - `}`

### repo/AppsRepository.kt
- `interface AppsRepository { val apps: Flow<List<AppInfo>>; fun launch(app: AppInfo) }`

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
- `interface AppGraph { val settings: SettingsRepository; val layouts: LayoutRepository; val apps: AppsRepository; val widgets: WidgetCatalog; val builtIns: BuiltInWidgetRegistry; val icons: IconResolver; val appWidgetHost: android.appwidget.AppWidgetHost; val appScope: CoroutineScope; companion object { const val APP_WIDGET_HOST_ID: Int = 1024 } }`
  - `appScope` = never-cancelled `SupervisorJob() + Dispatchers.IO` scope for writes that must outlive the UI that started them
  - `appWidgetHost` = `AppWidgetHost(context, 1024)`, created and `startListening()`-ed when `AppGraphImpl` is constructed in `Application.onCreate`; never stopped. Activities use `createView(context, appWidgetId, providerInfo)`, `allocateAppWidgetId()`, `deleteAppWidgetId(id)` on it. Binding a provider to an id needs user consent (`AppWidgetManager.ACTION_APPWIDGET_BIND`) unless `bindAppWidgetIdIfAllowed` succeeds; that flow belongs to `widgets/`.
- `val LocalAppGraph: ProvidableCompositionLocal<AppGraph>` (static; throws if unprovided)

### FoldOsApplication.kt
- `class FoldOsApplication : Application() { lateinit var graph: AppGraph; companion object { fun from(context: Context): FoldOsApplication } }`; `onCreate` does `graph = AppGraphImpl(this)`

## Data layer (owned by Contracts / Data)

All under `app/src/main/java/com/foldos/launcher/data/`.

- `db/LayoutDatabase.kt`: Room **version 2**, entities `PageEntity`, `ItemEntity`, converters `LayoutConverters` (enums by name). `LayoutDatabase.MIGRATION_1_2` adds to `items`: `app_widget_id INTEGER`, `builtin_id TEXT`, `stack_id INTEGER`, `stack_index INTEGER NOT NULL DEFAULT 0`, `free INTEGER NOT NULL DEFAULT 0`, `x_dp REAL`, `y_dp REAL`, `w_dp REAL`, `h_dp REAL`. `create(context)` adds the migration and keeps `fallbackToDestructiveMigration(dropAllTables = true)` as the last resort. Every future schema change bumps the version and adds a `Migration`.
- `db/LayoutEntities.kt`: `PageEntity(id, screen, page_index, style)`; `ItemEntity(id, page_id, kind, grid_col, grid_row, span_x, span_y, package_name, activity_name, title, app_widget_id, builtin_id, stack_id, stack_index, free, x_dp, y_dp, w_dp, h_dp)` with `toModel()` / `fromModel()`.
- `db/LayoutDao.kt`: `pagesByScreen`, `pagesByScreenOnce`, `pageById`, `firstPage`, `countPages`, `maxPageIndex`, `upsertPage`, `setPageStyle(pageId, style: String)`, `setPageIndex`, `deletePage`, `itemsByPage`, `itemsByPageOnce`, `itemById`, `itemsByIds`, `countItemsForScreen`, `countItemsForPage`, `upsertItem`, `upsertItems`, `moveItem`, `moveStack`, `resizeItem`, `resizeStack`, `placeInStack`, `deleteItem`.
- `LayoutRepositoryImpl(db: LayoutDatabase) : LayoutRepository` implements every method above; extra public `suspend fun upsertPage(page: HomePage): Long`.
- `SettingsRepositoryImpl(context)`, `AppsRepositoryImpl(context)` (Phase 1, unchanged).
- Placeholders replaced in later phases: `PlaceholderWidgetCatalog(context)` (installed providers only, all `UTILITIES`, in-memory recents), `EmptyBuiltInWidgetRegistry()`, `PassthroughIconResolver(settings)` (`icon(app) = app.iconLoader()`, no packs, persists pack/shape to settings, ignores overrides).
- `AppGraphImpl(context) : AppGraph` wires all of the above; `appWidgetHost` is created eagerly and started in `init`.
