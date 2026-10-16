# FoldOS Launcher — Product & Build Plan

Target device: Samsung Galaxy Z Fold 8 Ultra (Android 16 / One UI 8)
Status: v1.0, design complete and ready for build. Mock-up: https://claude.ai/artifact/PPHtkLNoKCFtTFaMYfkoeC (source: `mockup/foldos-mockup.html`).

---

## 1. Vision

A launcher built for a foldable, where **every behavior is a user choice**. Nothing is forced:
cover and inner screens can be identical or completely different, the home style can be a
widget dashboard, icon grid, minimal list, or hybrid panels (mixed per page), and every feature
ships with an on/off switch. Simple by default, limitless in Advanced.

Guiding rules:
1. **Everything is a toggle.** Every feature in this document has a setting to disable it.
2. **Two screens, one brain.** Cover and inner screens share data and settings but can have independent layouts.
3. **Live edit first.** Long-press anything on the home screen to change it in place; the Settings app is the complete reference.
4. **Fast on-device.** 120 Hz smooth, AI runs on-device (Gemini Nano via AICore) with no cloud dependency.

---

## 2. Screens & Layouts

### 2.1 Screen modes (Settings → Screens)
| Option | Behavior |
|---|---|
| **Same** | One layout; inner screen scales the cover layout with bigger grid |
| **Different** (default) | Independent home pages, docks, widgets per screen |
| **Continuity** | Different layouts, but unfolding expands the current page into its inner-screen counterpart |

Fold/unfold transition: animated morph (toggle), or instant.

### 2.2 Home styles (per page, mix and match)
| Style | Description |
|---|---|
| Widget dashboard | Rich, resizable widgets fill the page; apps in dock/drawer |
| Classic grid | Icon grid + folders, optional widgets |
| Minimal list | Text list of favorites, alphabet scrubber, no icons |
| Hybrid panels | Inner screen split: app panel + widget/feed panel (ratios adjustable) |

Each page has its own style. Pages can be reordered, duplicated, or copied cover ↔ inner.

### 2.3 Visual themes (Settings → Look)
- **Material You** — dynamic color from wallpaper, Android 16 expressive shapes
- **Dark Glass** — OLED black, frosted blur panels, subtle glow
- **One UI Native** — matches Samsung stock look
- **Bold / Futuristic** — high contrast, neon accent, animated backgrounds
- Each theme exposes: accent override, corner radius, blur intensity, icon shape, label font, transparency.
- **Every theme has a dark and a light version.** Appearance setting: Dark / Light / Auto (switches at sunrise and sunset, or follows the system). Wallpapers can differ per mode.

### 2.4 Grid options
- Grid mode (columns/rows per screen, per orientation) **or** Free placement (snap-to-pixel, any size)
- Icon size, label size/hide, padding, dock size, page indicator style
- Independent settings for cover, inner-portrait, inner-landscape, and Flex Mode (half-folded)

### 2.5 Hub page
One page beside home (left by default, like Google Discover; can be moved to the right):

`[ Hub ]  ←  [ Home 1 ] [ Home 2 ] [ Home 3 ]`

The hub opens on **Today**, a dashboard built from everything else, with a tab bar (bottom by default, or top) for the other tabs:

| Tab | Content | Data source |
|---|---|---|
| **Today** (default) | Greeting and weather; AI morning briefing in plain sentences; next event with a Join button; tasks due; today's schedule; emails that need you; people to reach out to (missed calls, upcoming meetings, shared items). Inner screen: two panes split at the hinge. | Combines the four sources below; briefing written on-device |
| **AI** | Ask bar, conversation, "Try asking" prompts. Inner screen adds suggested actions and a feed. | On-device Gemini Nano (AICore); reads Calendar, Gmail, Todoist with permission |
| **Mail** | Inbox with Primary / Updates / Starred, AI summary, compose. Inner screen: list + reading pane with suggested replies. | **Gmail API** via Google Sign-In (OAuth, `gmail.readonly` + `gmail.send`; restricted scope needs Google verification). Fallback: any account through Android's account manager / IMAP. |
| **Calendar** | Week strip, today's timeline with a now line, upcoming. Inner screen: month grid + day timeline. | **Android Calendar Provider** (`CalendarContract`): covers Google, Samsung and Outlook accounts with no extra API. Optional Google Calendar API. |
| **Contacts** | Favorites, search, A–Z list with call/message. Inner screen: list + contact card with recent activity. | **Android Contacts Provider** (`ContactsContract`): Google contacts sync automatically. Optional People API. |

Settings › Hub: hub on/off, position (left/right), tab bar (top/bottom), each tab on/off, briefing sections and their order, opens on (Today or last tab), tab order. The page indicator shows a hub icon beside the home dots.

### 2.6 Docks: 20 presets, every feature mixable
A dock is built from traits, so features from any style can be combined:

| Trait | Options |
|---|---|
| Background | Pill · Edge shelf · None · Bubbles · Hairline · 3D shelf |
| Position | Bottom · Edge rail (vertical, icons only) |
| Items | Icons · App names |
| Layout | Labels · Two rows · Scrolling · Magnify |
| Add-ons | Search bar · Recent apps (taskbar) · AI suggestions · Folder · Media player · Notification preview · Center button · Swipe handle |

The 20 named styles (Floating pill, Edge shelf, Icons only, Labeled, Two rows, Scrolling, Taskbar, Search dock, Magnify, 3D shelf, Hairline, Text dock, Bubbles, Folder dock, Smart dock, Edge rail, Swipe handle, Notification dock, Media dock, Center action) are presets of these traits. Pick a preset, then switch any trait on or off; the dock becomes a "Custom mix". Icons shrink automatically when many add-ons share one row. Cover and inner screens each have their own dock. Defaults: Floating pill (cover), Taskbar (inner).

### 2.7 App drawer
- **Pop-up sheet (default):** Pixel-style. On the cover screen it rises from the bottom to about 78% height with rounded corners and a grab handle; on the inner screen it's a floating centered card (Pixel Tablet style). Home stays visible behind it, blurred and dimmed (blur can be switched off).
- **Full screen:** classic drawer.

### 2.8 Colors
Any theme can be recolored. Settings › Look › Color scheme: Theme colors (default), Ocean, Forest, Sunset, Rose, Gold, Mono, or Custom with three pickers (accent, wallpaper color 1, wallpaper color 2). The launcher derives readable variants for dark and light mode automatically (accent lightness is clamped for contrast; highlight cards, themed icons and wallpaper gradients follow the picked colors). Material You's dynamic color from the wallpaper remains available as "Theme colors".

### 2.9 Setup wizard: pick from previews
Every wizard question is answered by tapping a live preview, not a text option:
1. Screens: same / different / continuity, with pictures.
2. Home style: four live home-screen previews.
3. Look: each theme previewed on a real home screen.
4. Colors: palette swatches over a live preview, or custom pickers.
5. Dock: six dock presets previewed; mix features later in Settings › Dock.

### 2.10 Widget gallery
The widget picker is a searchable gallery of 532 widgets (catalog: [docs/widget-catalog.json](docs/widget-catalog.json)) in 17 categories: standard Android and Google/Samsung widgets, popular Play Store app widgets (hosted through `AppWidgetHost`), and 160 FoldOS built-in widgets that run on Android APIs (CalendarContract, ContactsContract, MediaSession, BatteryManager, Health Connect, NotificationListenerService, UsageStatsManager, Jetpack WindowManager for hinge-aware widgets, etc.). Filters: category, source (built-in / app), size, and text search across names, apps and APIs. Every widget previews in the current theme, colors and dark/light mode, drawn from 20 size-aware templates that fill the widget's box at every size from 1x1 to 4x4.

**Adding a widget on the phone (long-press home › Widgets):** the picker is organized by category.
- Cover screen: a pop-up sheet (home blurred behind it) with a search bar, a row of category chips, and one collapsible section per category, each showing a swipeable row of live previews with name, app and size. Tapping a chip jumps to that section; tapping a preview drops the widget on the page.
- Inner screen: a wide floating card with the category list down the left (icon, name, count) and a grid of previews for the selected category on the right.
- Settings › Widgets › "Widget picker groups by": Category (default), App, or Recently used first then category. Search works across names, apps and the Android feature a widget uses.

---

## 3. Widgets

### 3.1 Built-in widget library
| Category | Widgets |
|---|---|
| At a glance | Clock (analog/digital/word), Weather (hourly/7-day), Calendar agenda, combined "Glance" header |
| Productivity | Notes, To-do, Reminders, Focus timer / Pomodoro, Countdown, Habit tracker |
| Device status | Battery (phone / Watch / Buds / Ring), Storage, RAM, Network, Fold-state, Data usage |
| Media & feeds | Now Playing (any media session), Photo frame, News / RSS, Stocks, Sports scores, Podcast queue |
| Smart | Suggested apps, Recent files, Contacts, Smart Things devices, Quick actions |

### 3.2 Widget capabilities (each toggleable)
- **Standard Android AppWidget host** — any third-party widget (Gmail, Spotify, KWGT, etc.)
- **Stacks** — multiple widgets in one slot, swipe to flip, optional auto-rotate / smart rotate
- **Smart / contextual** — rules engine: show by time, location, fold state, connected device, calendar status (e.g. Work widgets 9–5 at office)
- **Free placement** or grid snapping (per page)
- **Resize** with live preview, aspect-lock option
- **Widget themes** — built-in widgets follow launcher theme or override per-widget

### 3.3 Integrations (built-in widgets pull from these via official APIs)
- **Google**: Calendar, Gmail, Tasks, Keep, Drive, Photos, Fit/Health Connect, Maps
- **Samsung**: Calendar, Notes, Reminder, Health, Galaxy Watch & Buds (battery via Companion), SmartThings, Bixby Routines
- **Media**: any MediaSession (Spotify, YT Music, Netflix, podcasts), Google Cast
- **Microsoft**: Outlook, To Do, OneNote, Teams
- **Productivity**: Todoist, Notion, Slack, Trello, TickTick
- **Extensible**: Integration SDK so third parties (and "hundreds more" via Android Intents, content providers, Health Connect, MediaSession) can feed widgets. Where no API exists, the widget falls back to hosting the app's own Android widget.

---

## 4. Launcher Features (all have on/off switches)

| Feature | Details |
|---|---|
| **Universal search** | Apps, contacts, settings, files, calendar, web, calculator, unit conversion, AI answer. Swipe-down or dock bar. Providers toggleable. |
| **Gestures** | Swipe up/down/left/right (1- & 2-finger), double-tap, pinch in/out, long-press empty space, home-button double-press, hinge gestures (fold/unfold actions). Each assignable to any action/app/shortcut. |
| **Smart app drawer** | Auto-categories (AI), custom tabs, folders, hidden apps (with PIN), sort A-Z / recent / most used / color, vertical or horizontal, search bar position. |
| **App pairs & multitasking** | Create app-pair icons (split-screen 2 or 3 apps), pop-up window launch, Taskbar integration on inner screen, Flex Mode layouts. |
| **Folders** | Grid, list, cover-art, expandable "big folders", tab folders. |
| **Notifications** | Badges (dots / counts), notification previews on long-press, notification widget. |
| **Dock** | Per screen, 4–10 items, folders in dock, hide/auto-hide, swipe-up drawer or search. |
| **Icon packs** | Any Android icon pack, per-app icon/label override, adaptive icon shape, icon normalization. |
| **Profiles / modes** | Full launcher state snapshots (Work, Home, Travel, Night, Kids). Switch manually, by schedule, location, Wi-Fi, Bixby/Modes & Routines, or Do Not Disturb. |
| **AI (Hub tab)** | AI tab in the Hub, plus the Today briefing: on-device Gemini Nano (AICore) for summaries, smart suggestions, natural-language settings ("make my inner screen a dashboard"), widget rule creation. Falls back to any phone-exposed AI API (Galaxy AI / Bixby intents). Toggle: on-device only / allow exposed APIs / off. |
| **Backup** | Layout + settings included in Google device backup (Android Backup / Auto Backup). Manual export/import JSON file as well. |
| **Lock layout** | Prevent accidental edits. |
| **Privacy** | Hidden apps, private space support, app lock via biometrics. |

### 4.1 Features adopted from other launchers
From a survey of 26 Play Store launchers (full report: [docs/launcher-research.md](docs/launcher-research.md)). Each ships with its own on/off switch.

| Area | Feature | Seen in |
|---|---|---|
| Notifications | Inline notifications under apps (reply/dismiss in place) | Niagara, Posidon |
| Notifications | Quiet notification drawer for low-value alerts | Before Launcher |
| Notifications | Unified messaging inbox | Ratio, AIO |
| Gestures | Per-icon swipe actions | Nova, Action, Total |
| Home | Cover folders (tap opens app, swipe opens folder) | Action, Nova |
| Home | Infinite page scroll | Nova, Lynx |
| Home | Auto-built category pages | Pixel |
| Home | Live tiles | Square Home |
| Home | Easy / large mode | One UI, Nothing, HyperOS |
| Widgets | Icon-to-widget pop-up (swipe icon to peek) | Action, Niagara |
| Search | Deep-link shortcut search | Nova, Kvaesitso |
| Search | Usage-ranked results | KISS, Kvaesitso |
| Search | Custom engines and !bangs | Lawnchair, Olauncher, Action |
| Search | Visual search button (Lens / Circle to Search) | Pixel, Microsoft |
| Search | Currency, Wikipedia and places answers | Kvaesitso |
| Drawer | Multi-tag apps | KISS, Kvaesitso |
| Drawer | Work profile tab with pause | Pixel, Lawnchair |
| Drawer | Archived apps with restore | Pixel, Lawnchair |
| Dock | Scrollable multi-page dock | Nova, Action |
| Dock | Widgets in dock | Lawnchair |
| Theming | Daily wallpaper per screen | Microsoft, Olauncher |
| Theming | Dynamic calendar and clock icons | Pixel, One UI, Nova |
| Theming | Greyscale focus mode | Ratio, Niagara |
| Theming | Theme studio and sharing gallery | Niagara, Hyperion |
| AI | Usage nudges and app limits | Niagara, Ratio |
| AI | Prompt-to-widget (describe it, AI builds it) | Nothing, AIO |
| Privacy | AI usage log | Nothing |
| Privacy | No-network / no-ads mode | Fossify, KISS, Olauncher |
| Other | Tasker and scripting API | AIO, Nova, Total |
| Backup | Multi-cloud live sync (Drive, OneDrive, WebDAV) | AIO, Microsoft |
| Backup | Import Nova, Lawnchair and Niagara backups | Nova, Lawnchair |
| Large screen | Keyboard navigation | KISS |
| Large screen | Floating launcher overlay over any app | One UI, Swipe |

**Market notes from the research:** Nova was sold to Instabridge in January 2026, and its free version (8.2.4) now has ads, which leaves room for a power launcher with no ads and clear pricing. Almost no third-party launcher treats the fold as a design input, so our screen modes and hinge-aware layouts set us apart. **Limitation:** One UI keeps the recent-apps screen in its own launcher, so restyling recents is very likely impossible for a third-party launcher.

---

## 5. Settings Architecture

### 5.1 Structure
```
Settings
├── Search settings…
├── Quick setup (re-run wizard) · Preset gallery
├── Screens         same / different / continuity, fold transition, Flex Mode
├── Home            per-screen pages, style per page, grid/free, drawer style, indicators
├── Dock            20 styles per screen, icon count, opacity, search provider
├── Hub             on/off, left/right, tab bar top/bottom, tabs (AI, Mail, Calendar, Contacts), briefing sections, accounts
├── Look            theme, dark/light/auto, accent, radius, blur, icon pack, icon shape, fonts, wallpaper per screen
├── Widgets         library, stacks, smart rules, integrations & accounts
├── App drawer      style, tabs, hidden apps, sorting
├── Gestures        every gesture → action
├── Search          providers, position, AI answers
├── Multitasking    app pairs, pop-up, taskbar
├── Profiles        create/edit, auto-switch triggers
├── AI              engine (on-device / exposed APIs / off), suggestions, permissions
├── Notifications   badges, previews
├── Backup          Google backup status, export/import
├── Advanced  [toggle]  → reveals: animation speeds, scroll physics, haptics, developer, experimental
└── About
```

### 5.2 Three editing levels
1. **Simple** — the top of each page: the 3–5 settings most people want.
2. **Advanced** — a master toggle reveals every option on every page.
3. **Live edit** — long-press home → edit mode: drag, resize, swap style per page, change theme, open widget picker, per-element context menu. Every live-edit action maps to a setting and vice-versa.

### 5.3 First run
Setup wizard (5 steps, every choice made by tapping a live preview; see 2.9): Screens → Home style → Look → Colors → Dock. Then optional import from the current launcher (reads Samsung/Pixel layout where permitted).
Preset gallery available in Settings afterwards (Minimal, Dashboard, Productivity, Media, Power User, One UI Classic) — presets apply to one screen or both, and can be saved/shared as files.

---

## 6. Technical Architecture

| Layer | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Compose for foldables: `WindowSizeClass`, `FoldingFeature` via Jetpack WindowManager) |
| Min SDK | Android 14 (API 34); target Android 16 |
| Launcher core | `ROLE_HOME`, `LauncherApps`, `AppWidgetHost`, `ShortcutManager`, Quick-step recents handshake (where OEM allows) |
| Fold handling | WindowManager `FoldingFeature` state (FLAT / HALF_OPENED), hinge-aware layouts, separate layout stores per `displayId` |
| Data | Room DB (layouts, widgets, rules, profiles), DataStore (settings), Android Auto Backup rules |
| Widgets | AppWidgetHost + custom Compose widgets; Glance for exported widgets |
| Integrations | WorkManager sync, OAuth via AppAuth, Health Connect, MediaSessionManager, NotificationListenerService (badges), CalendarContract |
| AI | AICore / ML Kit GenAI (Gemini Nano) on-device; optional intents to Galaxy AI |
| Search | Local index (apps, contacts, settings, files via MediaStore), pluggable providers |
| Icons | Icon pack parser (standard `appfilter.xml`), adaptive icon masking |
| Testing | Compose UI tests, foldable emulator profiles (Fold 8 Ultra dimensions), Baseline Profiles for startup |

### Fold 8 Ultra display assumptions (used in mock-up)
- Cover: ~6.5" 21:9-ish, 2520×1080 class
- Inner: ~8.2" near-square, 2184×1968 class
- Flex Mode: half-folded, top/bottom split

---

## 7. Phased Roadmap

| Phase | Scope |
|---|---|
| **0 — Design** (done) | This plan, static and interactive mock-up, launcher research, widget catalog |
| **1 — Core launcher** | Home role, app drawer, dock, folders, grid pages, cover/inner separate layouts, settings skeleton, Simple/Advanced |
| **2 — Widgets** | AppWidgetHost, built-in Glance/Clock/Weather/Calendar/Battery, stacks, free placement, live edit |
| **3 — Looks & styles** | 4 themes, 4 home styles, icon packs, wizard, presets |
| **4 — Power** | Gestures, universal search, app pairs, profiles, smart widget rules |
| **5 — Integrations & AI** | Google/Samsung/Microsoft/media integrations, integration SDK, Hub page (Today briefing, AI, Mail, Calendar, Contacts) |
| **6 — Polish & release** | Backup, performance, accessibility, sideload APK → Play Store |

---

## 8. Build readiness

### 8.1 Defaults the build starts from (all changeable in Settings)
| Setting | Default |
|---|---|
| Screens | Different layouts (cover and inner independent) |
| Appearance | Dark; Auto available |
| Theme / colors | Dark Glass, theme colors (Material You dynamic color available) |
| Cover home | Classic grid, 4 columns, floating pill dock |
| Inner home | Widget dashboard, 4 widget columns, taskbar dock |
| Hub | On, left of home, tab bar at bottom, opens on Today, all tabs on |
| App drawer | Pop-up sheet with blur |
| Widget picker | Grouped by category |
| Setup | Wizard on first run; preset gallery in Settings |

### 8.2 What the mock-up has verified
- Every screen is drawn at the device's real dp size and checked for clipped content in all 4 themes × dark/light × color schemes.
- 329 settings interactions in the lab run without errors; every dock preset works on both screens.
- 532 widget previews render without clipping; the picker and gallery share one catalog.
- The page works at phone width.

### 8.3 Decisions still open (defaults proposed; decide during Phase 1)
1. **Name.** Mock-up uses the placeholder **FoldOS**.
2. **Flex Mode.** Proposed: its own layout (media/content above the hinge, controls below), with "reuse inner layout" as the alternative setting.
3. **Hidden apps.** Proposed: biometric lock on by default.
4. **Gmail scope.** The Mail tab needs Google's restricted-scope verification before public release; the build can start with Android's account manager (IMAP/Outlook) and add Gmail API behind the verification.
5. **Recents.** One UI keeps the recent-apps screen in its own launcher; the build will not try to restyle it.

### 8.4 First build milestone (Phase 1)
Kotlin + Jetpack Compose project with `ROLE_HOME`; cover and inner layouts with `FoldingFeature`; grid home, pop-up drawer, dock with the trait system, Settings skeleton with Simple/Advanced; sideloadable APK on the Fold 8 Ultra.
