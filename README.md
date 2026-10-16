# FoldOS

An Android launcher for the Samsung Galaxy Z Fold 8 Ultra, written in Kotlin with Jetpack Compose
(Material 3). Version 1.2.0.

FoldOS treats the fold as a design input. The cover screen and the inner screen get their own home
layouts (or share one), the home keeps a gap over the hinge, Flex Mode splits the home at the
hinge, and every behavior has an on/off switch. It ships a dock built from mixable traits, a pop-up
app drawer, widgets (hosted Android widgets plus built-ins), gestures, universal search, folders
and app pairs, notification badges, a Hub page (Today, Calendar, Contacts, Mail, AI),
accessibility options, backup and restore, and a full settings app.

- Design: [PLAN.md](PLAN.md)
- Interactive mock-up (every screen is built from it): https://claude.ai/artifact/PPHtkLNoKCFtTFaMYfkoeC
  (source: `mockup/foldos-mockup.html`)
- Release history: [CHANGELOG.md](CHANGELOG.md)
- Manual device test plan: [docs/TESTING.md](docs/TESTING.md)
- Module contracts and package ownership: [app/CONTRACTS.md](app/CONTRACTS.md)

> Status: nothing in this repository has been run on a device yet. GitHub Actions is the only
> thing that has compiled it. See [Not yet verified on a device](#not-yet-verified-on-a-device).

## Screenshots

There are no device screenshots yet. The interactive mock-up is the reference for how every screen
should look, at the Fold 8 Ultra's real dp sizes, in all four themes, dark and light:

https://claude.ai/artifact/PPHtkLNoKCFtTFaMYfkoeC

Open it, use the screen switcher (cover, inner, Flex) and the lab panel to try themes, docks,
the widget picker and the Hub. Device screenshots will replace this section after the first run
through [docs/TESTING.md](docs/TESTING.md).

## Features by phase

Every feature has a switch (Settings > Features and the page it belongs to).

### Phase 1: core launcher (0.1.x)

- Cover and inner screens with independent layout stores, chosen from the window size and the
  reported `FoldingFeature`; "Same layout" mode reuses the cover layout on the inner screen.
- Home styles per page: classic grid (18 dp gutter over the hinge), widget dashboard, minimal list,
  hybrid panels. Pages are stored in Room.
- Flex Mode: first page above the hinge, an app row and the dock below it.
- Dock with 20 presets, every trait mixable (pill, shelf, hairline, bubbles, 3D shelf, text, two
  rows, scrolling, taskbar, search bar, folder, media, notification, center button, swipe handle,
  edge rail, magnify). Separate docks for cover and inner.
- App pop-up (bottom sheet on the cover, floating card on the inner screen) or full screen: search, group folders
  (All, Work, Media, Social, Tools, Games, Hidden and your own groups), backdrop blur. It is also how you change the
  home screen and the dock: long-press an app and drag it to a home page or to the dock (the pop-up folds away while you
  drag), or long-press and release for its menu (Add to group, Add to home screen, Add to dock, App info).
- Four themes (Material You, Glass, One UI, Bold), dark / light / auto, twelve primary colors that each come in a light
  and a dark version (the version follows the Appearance), plus custom colors.
- Settings picked from samples: Screens, Home style, Look, Colors, Dock, App drawer, Widget picker and Icons are rows of
  live preview cards (tap to apply); everything else is under "More settings" (Simple / Advanced levels). Search is the
  icon in the top bar.
- 0.1.1: app icon and an offer to become the default home.

### Phase 2+3: widgets, looks and styles (0.2.0)

- Hosted Android app widgets (`AppWidgetHost`) with the system bind dialog and configure activity.
- Built-in widgets: Clock, Glance, Weather (sample data), Calendar agenda, Devices battery, To-do,
  Now Playing, Focus timer, Photos, News.
- Two-level widget picker (1.2.0): search, a Featured grid of FoldOS widgets and an Apps list (category chips, or
  recently used first); tapping an app opens a swipe-through detail view with a large preview and an "Add to home"
  button. Bottom sheet on the cover, floating card with list and pager side by side on the inner screen.
- Widget stacks (swipe to flip), free placement, resize.
- Live edit mode: long-press, drag, resize, per-page style, add and delete pages, theme and
  wallpaper sheets.
- Icon packs (appfilter.xml) and icon shapes.
- First-run setup wizard (5 steps, tap a live preview) and the preset gallery.

### Phase 4+5: power features, Hub and AI (0.3.0)

- Gestures: swipes, two-finger swipes, pinch, double tap, home key double press, fold / unfold;
  each bound to an action.
- Universal search: apps, shortcuts, contacts, settings, files, calendar, calculator, units, web
  with `!bangs`.
- Folders (grid / list / big) and split-screen app pairs.
- Notification badges, long-press previews and the quiet notification drawer.
- Hub page: Today briefing, Calendar, Contacts, Mail (Gmail API) and AI tabs.
- AI command router: plain-language launcher commands, template engine by default, optional
  on-device AICore.

### Phase 6: polish and release (1.0.0)

- Backup and restore: export the whole launcher (settings, every page and item, icon
  overrides) as one JSON file and import it on this or another device (Settings > Backup, also
  Quick setup); Google Auto Backup status with a "request a backup now" row; grid size import from
  a Lawnchair or Nova settings file; reset layout.
- Accessibility: high contrast, reduce motion, text scale, large icons (easy mode), greyscale
  focus mode, content descriptions and touch targets across home, dock, drawer and settings.
- Privacy: a hard "No network" switch (Settings > Features > Privacy) that turns off the Gmail API, web previews and any
  network use by AI.
- Daily wallpaper (Settings > Look, off by default): a gradient generated from the date is set as the home
  wallpaper once a day, with no WorkManager and no download.
- Hardware keyboard: type on the home screen to search, arrow keys move between icons.
- Startup and scrolling performance work, baseline profile, R8-shrunk release build.
- Signed release APK from GitHub Actions and a GitHub Release on every `v*` tag.

### Fix round 1.1: device feedback (1.1.0)

- The Hub page has no home dock, page indicator or search bar: one tab bar, full height, on the cover and the inner screen.
- Swipe up opens the app pop-up from anywhere on the home, including the dock strip; every dock has an "All apps" button.
- Long-press anywhere on the home (apps, widgets, empty space) enters edit mode. Edit mode draws the home at 76% between
  one top bar (page style, page strip, Done) and one bottom bar (Wallpaper, Themes, Widgets, Dock, Settings); Remove works on
  every item and offers Undo for five seconds.
- A fresh install gets one populated page and one empty page per screen; the duplicate pages of 1.0.0 are cleaned up once.
- Settings opens on preview cards (Screens, Home style, Look, Colors, Dock, App drawer, Widget picker, Icons): tap a sample to apply it.
  Toggles, dropdowns, sliders and hex fields are under "More settings".
- Twelve primary colors, each with a light and a dark version, in Settings, the setup wizard and the edit-mode theme sheet.
- Universal search is a full-screen takeover with the field pinned at the top and the results above the keyboard; a dock
  with the search add-on no longer gets a second search bar at the top of the home.

### Fix round 1.3: no profiles, readable labels, clean widget picker (1.2.0)

- Profiles are gone: no profile switcher, editor, schedule or Do Not Disturb switching, and no exact-alarm permission.
  Hidden apps moved to Settings > More settings > Hidden apps. Old settings and backups that mention profiles still load.
- App names under icons wrap to two lines instead of being cut off, in one shared style across the home, dock, pop-up,
  folders and pairs; Settings > Home style has Small / Medium / Large preview cards for the size.
- The widget picker is a two-level chooser: Featured widgets and an Apps list, then a swipe-through detail view per app
  with "Add to home".

## Building

### GitHub Actions (recommended)

`.github/workflows/android.yml` has two jobs:

| Job | Runs on | Output |
|---|---|---|
| Assemble debug APK | every push to `main`, every pull request, tags `v*`, manual run | artifact `foldos-debug-apk` (`app-debug.apk`) |
| Assemble release APK | same triggers | artifact `foldos-release-apk` (`app-release.apk`); on a tag `v*` also a GitHub Release with `FoldOS-<tag>.apk` attached |

Open the run under the repository's Actions tab and download the artifact (a zip holding the APK).

### Locally

Requirements: JDK 17 and an Android SDK with platform 35 (`compileSdk = 35`). Point the build at
the SDK with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=...`.

```
./gradlew assembleDebug          # Linux / macOS
gradlew.bat assembleDebug        # Windows
./gradlew assembleRelease        # shrunk release APK
```

Outputs: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`.

Pinned versions: Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29, Compose
BOM 2024.12.01, Room 2.7.0 (2.6.1's KSP processor cannot read Kotlin 2.1 metadata; do not
downgrade it), minSdk 34, targetSdk 35. `versionName` is `1.2.0`, `versionCode` is `120`.

If the release build fails inside R8, build with `-Pfoldos.minify=false` to confirm that shrinking
is the cause, then add the missing keep rule to `app/proguard-rules.pro`.

## Releasing

1. Update `CHANGELOG.md` and, when needed, `versionName` / `versionCode` in `app/build.gradle.kts`.
2. Create a signing key once (keep it and its passwords somewhere safe; losing it means users
   cannot update in place):

   ```
   keytool -genkeypair -v -keystore foldos-release.jks -alias foldos \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

3. Add four repository secrets (Settings > Secrets and variables > Actions):

   | Secret | Value |
   |---|---|
   | `FOLDOS_KEYSTORE_BASE64` | `base64 -w0 foldos-release.jks` (macOS: `base64 -i foldos-release.jks`) |
   | `FOLDOS_KEYSTORE_PASSWORD` | the keystore password |
   | `FOLDOS_KEY_ALIAS` | `foldos` (the alias used above) |
   | `FOLDOS_KEY_PASSWORD` | the key password (empty = same as the keystore password) |

   The workflow writes the keystore to the runner's temp directory and exports
   `FOLDOS_KEYSTORE_PATH`, `FOLDOS_KEYSTORE_PASSWORD`, `FOLDOS_KEY_ALIAS`, `FOLDOS_KEY_PASSWORD` to
   Gradle. To build a signed APK on your own machine export the same four variables
   (`FOLDOS_KEYSTORE_PATH` is a file path) before `./gradlew assembleRelease`.

   Without the secrets the release build is signed with the debug key so that CI still produces an
   installable APK. Such a build is published as a pre-release and cannot update a build signed with
   another key.
4. Tag and push:

   ```
   git tag v1.2.0
   git push origin v1.2.0
   ```

   The release job builds the APK, uploads `foldos-release-apk` and creates the GitHub Release
   `FoldOS v1.2.0` with `FoldOS-v1.2.0.apk` attached.

The Gmail OAuth client (below) is tied to the signing key's SHA-1, so register the release key's
fingerprint too.

## Installing on the Fold

1. On the phone enable Developer options and USB debugging, then connect it over USB (or use
   wireless debugging). Alternatively download the APK on the phone and open it (allow "Install
   unknown apps" for your browser or file manager when asked).
2. Install over USB:

   ```
   adb install -r app-release.apk
   ```

   `-r` keeps the launcher's data when you install a newer build over an old one. A build signed with a
   different key (debug vs release) cannot replace the installed one; uninstall first, or export a
   backup (Settings > Backup) and import it afterwards.

### Setting FoldOS as the default home

On first launch FoldOS asks once to become the default home through the system's role dialog
(`RoleManager.ROLE_HOME`). If you dismissed it, or want to switch later:

- Settings > Apps > Choose default apps > Home app > FoldOS, or
- `adb shell cmd role add-role-holder android.app.role.HOME com.foldos.launcher`

To go back to One UI Home pick it in the same place. Uninstall with
`adb uninstall com.foldos.launcher`.

## Permissions

Everything is requested when the feature that needs it is first used, except the install-time ones.
Declining a permission only disables the feature behind it.

| Permission | Why | When |
|---|---|---|
| `QUERY_ALL_PACKAGES` | List every launchable app, widget providers and icon packs | Install time |
| `REQUEST_DELETE_PACKAGES` | The "Uninstall" action in the app context menu | Install time |
| `BIND_APPWIDGET` | Hosting app widgets. A sideloaded launcher is not granted it; the real grant is the per-app system dialog shown the first time | Adding an app widget |
| `READ_CALENDAR` | Calendar agenda widget, Hub Calendar and Today, calendar search | "Allow" card on the widget, tab or search result |
| `READ_CONTACTS` | Hub Contacts and Today, contact search | "Allow" card |
| `READ_CALL_LOG` | Missed calls on Today and in a contact's card | "Allow" card |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED` | Photos widget (full or "selected photos" access) | "Allow photo access" button on the widget |
| `BLUETOOTH_CONNECT` | Devices battery widget (Watch, Buds) | "Allow" button on the widget |
| `EXPAND_STATUS_BAR` | Gesture actions "Notifications" and "Quick settings" | Install time (normal) |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the daily wallpaper alarm after reboot, app update, clock or time zone change | Install time (normal) |
| `SET_WALLPAPER` | Daily wallpaper (off by default) | Install time (normal) |
| `INTERNET` | Gmail API and the OAuth token exchange (nothing else). Disabled in effect by the "No network" switch | Install time (normal) |
| `com.google.android.gm.permission.READ_CONTENT_PROVIDER` | Unread counts per label from the Gmail app, no sign-in | Install time (normal) |
| Notification access (`FoldOsNotificationListener`) | Badges, long-press previews, quiet drawer, Now Playing widget | Settings > Notifications > Device & app notifications > FoldOS |
| Default home role (`ROLE_HOME`) | Be the launcher | Role dialog on first launch |

## Privacy

- Everything stays on the device. Layouts are in a local Room database, settings in DataStore,
  Hub data is read live from the Android Calendar, Contacts and Call Log providers and never copied
  anywhere.
- The AI engine is a template/rules engine by default, or Gemini Nano through AICore when the device
  offers it; both run on the phone. The AI usage log (Settings > AI) lists every action the assistant took.
- The only network use is the optional Gmail integration (Hub > Mail). It is off until you enter your own OAuth client
  id and sign in. Gmail tokens are stored in `EncryptedSharedPreferences`, bound to the Android
  Keystore, and excluded from Android backups.
- **No network switch** (Settings > Features > Privacy): turns off the Gmail API, web previews and every
  other network call at once. Web search still opens your browser when you tap a result, because
  that is the browser's request, not FoldOS's.
- No analytics, no ads, no accounts.
- Backups are plain JSON files you save yourself; they contain your layout and settings, not Gmail
  tokens or search history.

## Gmail OAuth setup

The Mail tab works in two layers. Without any setup it shows the Gmail app's unread counts per
label (Primary, Updates, Starred, ...) for a Google account you pick. The inbox itself comes from
the Gmail API and needs an OAuth client that belongs to you (FoldOS ships no client id):

1. In the [Google Cloud Console](https://console.cloud.google.com/) create a project (or reuse
   one) and enable the **Gmail API** under APIs & Services > Library.
2. Under APIs & Services > OAuth consent screen choose *External*, fill in the app name and your
   e-mail, and add the scope `https://www.googleapis.com/auth/gmail.readonly`. While the app is
   in *Testing*, add the Google accounts that may sign in as test users.
3. Under Credentials create an **OAuth client ID** of type **Android**: package name
   `com.foldos.launcher`, and the SHA-1 of the signing key.
   - Release build: the SHA-1 of your release keystore,
     `keytool -list -v -keystore foldos-release.jks -alias foldos`.
   - Debug or fallback-signed CI build: the debug keystore fingerprint,
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`.
     The Actions runner generates its own debug key, so either sign the APK yourself or register
     each fingerprint as a separate Android client.
4. Copy the client id (`...apps.googleusercontent.com`) into FoldOS Settings > Mail > OAuth client
   id, then tap **Connect**. Sign-in runs through AppAuth in a Custom Tab; the redirect
   `com.foldos.launcher:/oauth2redirect` is registered by the `appAuthRedirectScheme` placeholder in
   `app/build.gradle.kts`.

`gmail.readonly` is a restricted scope: publishing the consent screen for everyone requires
Google's verification, so until then only the test users from step 2 can connect (test-mode
tokens also expire after 7 days). Turn on **No network** to disconnect the integration at any time.

## Troubleshooting

**Home role: "Set as default" does nothing, or the system keeps showing One UI Home.**
Open Settings > Apps > Choose default apps > Home app and pick FoldOS. If FoldOS is not listed,
the installed APK has no HOME activity (reinstall) or the install was blocked half-way. If the
home screen is blank after switching, force-stop FoldOS and press Home again. If you removed the
role by mistake and are stuck on a blank launcher, `adb shell cmd role add-role-holder
android.app.role.HOME com.samsung.android.app.launcher` restores One UI Home.

**Widget will not add, or says "needs permission".**
Hosting an app widget requires the system's bind dialog, which appears the first time you add a
widget from each app. If you tapped Don't allow, add the widget again and accept. Some widgets
have a configure screen that must be finished before the widget appears; cancelling it cancels
the add. If a widget stays blank after a reboot, remove it and add it again so FoldOS can rebind it.
Widgets from apps that were uninstalled show as empty placeholders; delete them in edit mode.

**No badges, previews or Now Playing.**
These need Notification access. Settings > Notifications > Device & app notifications >
FoldOS > Allow. On One UI the toggle is greyed out for sideloaded apps until you open the app
info page, tap the three-dot menu and choose "Allow restricted settings".

**Calendar, contacts or missed calls are empty on the Hub.**
Tap "Allow" on the tab; permissions are not requested until then. After a denial, enable them in
Settings > Apps > FoldOS > Permissions.

**Gmail "Connect" fails.**
Check that the client id is of type Android, the package is `com.foldos.launcher`, and the
registered SHA-1 matches the key the installed APK is signed with. While the consent screen is in
Testing, your account must be a test user. Make sure the "No network" switch is off.

**Install fails with "App not installed" or a signature conflict.**
The installed build was signed with a different key. Export a backup, uninstall, install the new
APK, then import the backup.

## Repository layout

```
PLAN.md                     Design plan (screens, themes, docks, hub, settings, roadmap)
CHANGELOG.md                Release history 0.1.0 to 1.2.0
mockup/foldos-mockup.html   Interactive mock-up; the JS objects S0, DOCKS, SCHEMES, HOME define behavior
docs/                       Launcher research, the widget catalog, TESTING.md (manual device test plan)
app/                        The single Gradle module (com.foldos.launcher)
  CONTRACTS.md              Package ownership and shared contracts
  proguard-rules.pro        R8 keep rules for the release build
  src/main/java/com/foldos/launcher/
    core/                   Shared models (settings, dock config, layout), repository interfaces, AppGraph
    data/                   Room layout database, DataStore settings, LauncherApps-backed app list, AppGraphImpl
    fold/                   FoldState from Jetpack WindowManager, effectiveScreen()
    theme/                  FoldOsTheme, design tokens for Material / Glass / One UI / Bold, wallpaper brush
    home/                   HomeScreen pager, grid / dashboard / list / hybrid pages, hinge gutter, Flex layout
    dock/                   Dock composable: 20 presets, every trait mixable
    drawer/                 App pop-up (and full-screen variant): search, group folders, grid, backdrop blur, drag-out collapse
    apppicker/              Group folders (Work, Media, Social, Tools, Games, Hidden, yours), app menu, tap / long-press / drag gestures
    settings/               SettingsActivity: sample-based home (preview cards), More settings (detailed pages), search
    widgets/                AppWidgetHost wrapper: bind / configure flow, hosted widget view, stacks, sizing
    builtins/               FoldOS built-in widgets and the notification listener
    picker/                 Widget catalog (built-ins + installed providers, 17 categories) and the picker
    edit/                   Live edit mode: home scaled to 76% between one top and one bottom bar; drag, resize, delete with Undo, page style / add / delete, theme and wallpaper sheets
    icons/                  Icon pack parser, per-app overrides, icon shape masking
    onboarding/             First-run setup wizard (5 steps) and the preset gallery
    gestures/               Home gesture detector, hinge and home-key detectors, action dispatch
    search/                 Universal search sheet, home search bar, providers
    items/                  Folders, app pairs, item context menu
    notifications/          Notification badges and previews, quiet notification drawer
    hub/                    Hub page: Today, Calendar, Contacts; Calendar / CallLog / Contacts repository, task store
    mail/                   Gmail API through AppAuth, Gmail-app label counts, Mail tab
    ai/                     AI command router, template and on-device providers, AI tab, usage log
    backup/                 Backup export / import (JSON) and optional scheduled export
    a11y/                   High contrast, reduce motion, text scale, large icons, greyscale helpers
    perf/                   Startup work and baseline-profile support
    MainActivity.kt         ROLE_HOME activity; permission requester, widget configure results, home-key double press
    LauncherViewModel.kt    Hot flows for settings, apps and pages; drawer, search, hub, edit mode, layout mutations
    LauncherRoot.kt         Wizard gate, then wallpaper, HomeScreen, dock, hub, edit overlay, picker, drawer, search
.github/workflows/android.yml   CI: debug APK, release APK, GitHub Release on v* tags
```

## Not yet verified on a device

All of the following compile in CI but have never been run on a Galaxy Z Fold 8 Ultra. The test plan in
[docs/TESTING.md](docs/TESTING.md) covers each item.

- Becoming the default home through the role dialog, and fold / unfold while the launcher is the
  foreground home (activity is not recreated; `configChanges` set).
- Flex Mode layout (half-folded) and the hinge gutter on real `FoldingFeature` bounds.
- Hosted widgets from real apps (Samsung, Google, third party): bind, configure, resize, stacks.
- Folders, app pairs and split-screen launch behavior under One UI's multi-window rules.
- Notification badges with real notifications, the quiet drawer, and Now Playing.
- Gmail sign-in end to end (needs a Cloud project) and the Gmail-app label counts.
- On-device AI through AICore (the GenAI SDK is not bundled; the template engine is what runs).
- Weather is still sample data; no provider is wired.
- Backup export and import across a reinstall, and importing on a second device.
- Accessibility options with TalkBack, Switch Access and the system font and display size at maximum.
- R8-shrunk release behavior (reflection use: `StatusBarManager` gestures, AppAuth, serialization,
  Room); if something works in debug but not in release, suspect a missing keep rule.
- Startup time and 120 Hz scrolling; the baseline profile has not been measured.
- Recent apps screen: One UI keeps it in its own launcher, so FoldOS cannot restyle it.
- Gemini Nano, SmartThings, Health Connect and other integrations in the plan beyond Phase 5.

Nothing in this repository is compiled on the authoring machine; GitHub Actions is the source of
truth for whether a commit builds.
