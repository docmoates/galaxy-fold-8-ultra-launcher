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
    MainActivity.kt         ROLE_HOME activity
    LauncherViewModel.kt    Hot flows for settings, apps and pages; drawer and home-key state
    LauncherRoot.kt         Wires wallpaper, HomeScreen, dock, search bar, hub placeholder and drawer
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
- Settings app with Simple / Advanced levels and search; long-press the home background to open it.

Not in this build (later phases):

- Drag-and-drop editing of home pages, folders and app pairs; the long-press opens Settings.
- Hosting Android app widgets; dashboard cards are placeholders with a live clock and date.
- Real hub tabs (AI, Mail, Calendar, Contacts), notification badges, recent apps in the taskbar,
  media controls, smart dock suggestions, drawer categories beyond "All", icon packs, profiles
  and gesture mapping. Their switches exist in Settings but only change the UI shell.
- The setup wizard and preset gallery.
- Frosted "blur behind" on Glass / One UI surfaces is a translucent fill for now; only the
  drawer's backdrop blur uses a RenderEffect.

Nothing in this repository is compiled on the authoring machine; GitHub Actions is the source
of truth for whether a commit builds.
