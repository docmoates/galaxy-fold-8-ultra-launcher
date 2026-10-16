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

| Package / path | Owner | Public API it must expose |
|---|---|---|
| `core/` (everything below) | Scaffold | Shared contracts. Read, do not edit. |
| `MainActivity.kt`, `LauncherViewModel.kt`, `LauncherRoot.kt` | Integrate | `com.foldos.launcher.MainActivity : ComponentActivity` (ROLE_HOME entry; declared in the manifest), the `LauncherViewModel` and the `LauncherRoot` composable that wires home, dock, drawer, hub placeholder and search bar together |
| `home/` | Home | Home pages, grid/dashboard/list/hybrid styles, dock rendering from `DockConfig`, page indicator, hub page shell |
| `drawer/` | Drawer | Pop-up and full-screen app drawer, search, folders |
| `apps/` | Apps | `AppsRepository` implementation on `LauncherApps` (`class LauncherAppsRepository(context) : AppsRepository`) |
| `data/` | Data | Room database + `LayoutRepository` implementation (`class RoomLayoutRepository(...) : LayoutRepository`) |
| `settings/` | Settings | `SettingsActivity` (uses the `SettingsRepository` from `FoldOsApplication.graph`; the DataStore implementation lives in `data/`), wizard |
| `fold/` | Fold | `FoldState` producer from Jetpack WindowManager (`fun rememberFoldState(activity): State<FoldState>` or a `Flow<FoldState>`) |
| `theme/` | Theme | Compose `FoldOsTheme(settings, content)` for the four `ThemeId`s, dark/light, `ColorSchemeConfig` recoloring |
| `core/FoldOsApplication.kt` `onCreate` body only | Integrate | Builds the concrete `AppGraph` and assigns `graph` |

Keep class names unique across packages (no second `MainActivity`, `Theme`, `HomeScreen`, etc.).

## Shared contracts (owned by Scaffold)

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
- `@Serializable data class LauncherSettings(screenMode = DIFFERENT, appearance = DARK, styleCover = GRID, styleInner = DASHBOARD, theme = GLASS, gridMode = GRID, colsCover = 4, colsInner = 6, iconSizeDp = 58, radiusDp = 22, labels = true, profile = "Work", dockCover: DockConfig = pill, dockInner: DockConfig = taskbar, drawerStyle = POPUP, popBlur = true, hubOn = true, hubPos = "left", hubBar = "bottom", hubTabs: Set<String> = {ai, mail, cal, people}, colors: ColorSchemeConfig = ColorSchemeConfig(), wpGroup = "category", features: Map<String, Boolean> = {search, dock, badges, gestures, pairs, ai, stacks, smart, drawerTabs, profiles -> true})`
- Helpers: `feature(key): Boolean`, `withFeature(key, enabled)`, `dockFor(screen)`, `styleFor(screen)`, `colsFor(screen)`; companion constants `HUB_POS_*`, `HUB_BAR_*`, `HUB_TAB_*`, `WP_GROUP_*`, `FEATURE_*`, `ALL_FEATURES`

### model/AppInfo.kt
- `data class AppInfo(packageName: String, activityName: String, label: String, user: android.os.UserHandle, iconLoader: () -> android.graphics.drawable.Drawable)`; `val key: String` = "package/activity"

### model/Layout.kt
- `data class HomePage(id: Long, screen: ScreenKind, index: Int, style: HomeStyle)`
- `enum class ItemKind { APP, FOLDER, WIDGET, PAIR }`
- `data class HomeItem(id: Long, pageId: Long, kind: ItemKind, col: Int, row: Int, spanX: Int = 1, spanY: Int = 1, packageName: String? = null, activityName: String? = null, title: String? = null)`

### repo/SettingsRepository.kt
- `interface SettingsRepository { val settings: Flow<LauncherSettings>; suspend fun update(transform: (LauncherSettings) -> LauncherSettings) }`

### repo/LayoutRepository.kt
- `interface LayoutRepository { fun pages(screen: ScreenKind): Flow<List<HomePage>>; fun items(pageId: Long): Flow<List<HomeItem>>; suspend fun ensureDefaultLayout(screen: ScreenKind, apps: List<AppInfo>, columns: Int? = null); suspend fun upsertItem(item: HomeItem); suspend fun deleteItem(id: Long) }` (`columns` = the grid column count the seeded items are laid out for, normally `settings.colsFor(screen)`; null = implementation default)

### repo/AppsRepository.kt
- `interface AppsRepository { val apps: Flow<List<AppInfo>>; fun launch(app: AppInfo) }`

### fold/FoldState.kt
- `data class FoldState(kind: ScreenKind, isHalfOpened: Boolean, hingeBounds: android.graphics.Rect?, widthDp: Int, heightDp: Int)`

### AppGraph.kt
- `interface AppGraph { val settings: SettingsRepository; val layouts: LayoutRepository; val apps: AppsRepository; val appScope: CoroutineScope }` (`appScope` = never-cancelled `SupervisorJob() + Dispatchers.IO` scope for writes that must outlive the UI that started them, e.g. settings changes)
- `val LocalAppGraph: ProvidableCompositionLocal<AppGraph>` (static; throws if unprovided)

### FoldOsApplication.kt
- `class FoldOsApplication : Application() { lateinit var graph: AppGraph; companion object { fun from(context: Context): FoldOsApplication } }`
