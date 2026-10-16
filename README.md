# FoldOS

An Android launcher for the Samsung Galaxy Z Fold 8 Ultra, written in Kotlin with Jetpack
Compose (Material 3). It gives the cover screen and the inner screen their own home layouts,
keeps a gap over the hinge, splits the home at the hinge in Flex Mode, and ships a dock built
from mixable traits, a pop-up app drawer with backdrop blur, a hub page and a full settings app.

The design is in `PLAN.md`; `mockup/foldos-mockup.html` is the interactive mock-up every screen
is built from.

## Repository layout

```
PLAN.md                     Design plan (screens, themes, docks, hub, settings, roadmap)
mockup/foldos-mockup.html   Interactive mock-up; the JS objects S0, DOCKS, SCHEMES, HOME define behavior
docs/                       Launcher research and the widget catalog
app/                        The single Gradle module (com.foldos.launcher)
  CONTRACTS.md              Package ownership and shared contracts
  src/main/java/com/foldos/launcher/
    core/                   Shared models (settings, dock config, layout), repository interfaces, AppGraph
    data/                   Room layout database, DataStore settings, LauncherApps-backed app list, AppGraphImpl
    fold/                   FoldState from Jetpack WindowManager, effectiveScreen()
    theme/                  FoldOsTheme, design tokens for Material / Glass / One UI / Bold, wallpaper brush
    home/                   HomeScreen pager, grid / dashboard / list / hybrid pages, hinge gutter, Flex layout
    dock/                   Dock composable: 20 presets, every trait mixable
    drawer/                 Pop-up and full-screen app drawer with search and backdrop blur
    settings/               SettingsActivity with Simple / Advanced rows and search
    widgets/                AppWidgetHost wrapper: bind / configure flow, hosted widget view, stacks, sizing
    builtins/               FoldOS built-in widgets (Clock, Glance, Weather, Calendar, Devices, To-do, Now Playing, Focus, Photos, News)
    picker/                 Widget catalog (built-ins + installed providers, 17 categories) and the "Add a widget" picker
    edit/                   Live edit mode: drag, resize, delete, page style / add / delete, theme and wallpaper sheets
    icons/                  Icon pack parser (appfilter.xml), per-app overrides, icon shape masking, Settings sheets
    onboarding/             First-run setup wizard (5 steps) and the preset gallery
    MainActivity.kt         ROLE_HOME activity; permission requester, widget configure results, host start/stop
    LauncherViewModel.kt    Hot flows for settings, apps and pages; drawer, edit mode, picker and layout mutations
    LauncherRoot.kt         Wizard gate, then wallpaper, HomeScreen, dock, edit overlay, widget picker and drawer
.github/workflows/android.yml   CI: assembles the debug APK on every push
```

## Building

### GitHub Actions (recommended)

Every push to `main` (and every pull request) runs `.github/workflows/android.yml`, which
assembles `app-debug.apk` and uploads it as the `foldos-debug-apk` workflow artifact. Open the
run under the repository's Actions tab and download the artifact; the zip contains the APK.

### Locally

Requirements: JDK 17 and an Android SDK with platform 35 (`compileSdk = 35`). Point the build at
the SDK with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=...`.

```
./gradlew assembleDebug          # Linux / macOS
gradlew.bat assembleDebug        # Windows
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Pinned versions: Gradle 8.9,
Android Gradle Plugin 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29, Compose BOM 2024.12.01, Room 2.7.0
(2.6.1's KSP processor cannot read Kotlin 2.1 metadata; do not downgrade it), minSdk 34, targetSdk 35.

## Installing on the Fold

1. On the phone enable Developer options and USB debugging, then connect it over USB (or use
   wireless debugging).
2. Install the debug APK:

   ```
   adb install -r app-debug.apk
   ```

   `-r` keeps the launcher's data when you install a newer build over an old one.

## Setting FoldOS as the default home

On first launch FoldOS asks once to become the default home through the system's role dialog
(`RoleManager.ROLE_HOME`). If you dismiss it, or want to switch later:

- Settings > Apps > Choose default apps > Home app > FoldOS, or
- `adb shell cmd role add-role-holder android.app.role.HOME com.foldos.launcher`

To go back to One UI Home pick it in the same place. Uninstall with
`adb uninstall com.foldos.launcher`.

## Phase 2+3: widgets, looks and styles

What this phase adds:

- **Setup wizard on first run.** Five steps (screens, home style, look, colors, dock), each answered
  by tapping a live preview; Skip or Finish marks it done. Re-run it, or open the **preset gallery**
  (Minimal, Dashboard, Productivity, Media, Power user, One UI Classic), from Settings > Quick setup.
- **Live edit mode.** Long-press the home background or any item. Drag items between cells (hold
  past the page edge to switch pages), resize widgets from their corners, remove with the X chip,
  change the current page's style (Grid / Dashboard / List / Panels), add or delete pages, and use
  the bottom bar for Wallpaper, Themes, Widgets, Dock and Settings. Drop a widget onto another one
  to make a **stack** (swipe the stack to flip; Settings > Widgets > Widget stacks switches it off).
- **Widget picker.** A bottom sheet on the cover screen, a wide card on the inner screen; grouped
  by category, app, or recently used (Settings > Widgets). Built-in widgets are placed at once;
  Android app widgets go through the system bind dialog the first time and the provider's configure
  activity when it has one.
- **Built-in widgets.** Clock, Glance, Weather (static sample data until a provider is wired),
  Calendar agenda, Devices battery, To-do, Now Playing, Focus timer, Photos and News, every one
  themed by the launcher and adapting to its cell size. A fresh dashboard page starts with the
  default set from the plan.
- **Icon packs and shapes.** Settings > Look > Icon pack lists every installed pack (Nova / ADW /
  Apex / Lawnchair format); Icon shape masks the remaining icons (circle, squircle, rounded,
  teardrop, system).

### Permissions

| Permission | Why | When |
|---|---|---|
| `BIND_APPWIDGET` (declared) | Hosting app widgets; the real grant is the per-app system dialog | Adding an app widget |
| `READ_CALENDAR` | Calendar agenda widget | "Allow calendar access" button on the widget |
| `READ_MEDIA_IMAGES` | Photos widget | "Allow photo access" button on the widget |
| `BLUETOOTH_CONNECT` | Devices battery widget (Watch, Buds) | "Allow" button on the widget |
| Notification access (`FoldOsNotificationListener`) | Now Playing widget reads active media sessions | Settings > Notifications > Device & app notifications > FoldOS |
| `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` | App list, widget providers, icon packs; usage stats for later phases | Install time |

### Known limits in this phase

- Weather is a static sample; the real provider arrives with the integrations phase.
- Drawer and dock icons pick up an icon pack change on the next launch; home-screen icons update at once.
- Free placement snaps to the cell grid while dragging; pixel-exact placement comes later.

## Phase 1 scope and limits

What works in this build:

- Cover and inner screens with independent layout stores, chosen from the window size and the
  reported `FoldingFeature`; `Same layout` mode reuses the cover layout on the inner screen.
- Home styles per page: classic grid (with an 18 dp gutter over the hinge), widget dashboard,
  minimal list and hybrid panels. Pages are stored in Room; a default layout is written on first
  run from the installed apps.
- Flex Mode: first page above the hinge, a six-app row and the dock below it.
- Dock with the 20 presets and every trait mixable (pill, shelf, hairline, bubbles, 3D shelf,
  text, two rows, scrolling, taskbar, search bar, folder, media, notification, center button,
  swipe handle, edge rail, magnify).
- Pop-up app drawer (bottom sheet on the cover, floating card on the inner screen) or full
  screen, with search, backdrop blur and category chips.
- Four themes (Material You, Glass, One UI, Bold), dark / light / auto, six color schemes plus
  custom colors, all live from Settings.
- Hub page on either side of home with the Today greeting and placeholder briefing cards.
- Settings app with Simple / Advanced levels and search; reachable from edit mode's bottom bar.

Not in this build (later phases):

- Folders and app pairs beyond their tiles; dragging apps is in, folder creation is not.
- Real hub tabs (AI, Mail, Calendar, Contacts), notification badges, recent apps in the taskbar,
  media controls in the dock, smart dock suggestions, drawer categories beyond "All", profiles
  and gesture mapping. Their switches exist in Settings but only change the UI shell.
- Frosted "blur behind" on Glass / One UI surfaces is a translucent fill for now; only the
  drawer's and picker's backdrop blur use a RenderEffect.

Nothing in this repository is compiled on the authoring machine; GitHub Actions is the source
of truth for whether a commit builds.
