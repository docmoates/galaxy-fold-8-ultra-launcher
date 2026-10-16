# Android Launcher Landscape: Feature Research for FoldOS

Research date: 16 October 2026. Target: Samsung Galaxy Z Fold 8 Ultra (One UI 8.x / Android 16).

How status was checked: web searches in October 2026 against Play/F-Droid listings, APKMirror upload history, developer blogs and changelogs, and press coverage (SamMobile, Android Authority, 9to5Google, Droid-Life, Heise and others). Many "latest version" dates come from APKMirror or F-Droid upload dates rather than official changelogs, so treat them as approximate. Signature features combine what the release notes confirm with long-standing documented behaviour.

Status legend:
- **Active**: meaningful releases in 2026.
- **Maintenance**: compatibility or bug-fix builds only, no notable new features.
- **Abandoned**: no updates in years, or the developer has said it is discontinued.

---

## 1. Launcher overview

| Launcher | Status (latest notable update) | Signature features |
|---|---|---|
| **Nova Launcher** | Active, new owner. Founder Kevin Barry left Sept 2025. Instabridge bought it Jan 2026. v8.2.4 added ads/AdMob to the free tier (Prime stays ad-free). A v8.6.8 teardown shows an unreleased "Nova AI". | Per-icon swipe/double-tap gestures; Nova Search with Sesame deep-link shortcuts; subgrid positioning and scrollable/infinite dock & desktop; Cover folders (tap opens app, swipe opens folder); drawer tabs and folders, hide apps; Nova Cards/Now feed (Media, Weather cards, pinned shortcuts, 8.1.x) |
| **Niagara Launcher** | Active. "Studio Update" (Apr 2026) added Theme Studio; developer renamed Mellowdrop Studio. Nov 2025 brought new themes/clocks plus Bing search. | Single vertical list with a "wave" alphabet scroller for one-handed use; inline notifications you can read, reply to and swipe away inside the list; pop-ups (swipe an app to show its widget/shortcuts); Usage Breaker (reminder after X minutes in an app); Theme Studio (create and share themes); settings backup via Android/Drive backup |
| **Lawnchair** | Active, still beta. 15 Beta 2.1 (Mar 2026); a Beta 3 was reported ~Aug 2026; team focus is moving to Lawnchair 16. No stable 15 release yet. | Pixel/Launcher3 base (Android 15: Private Space, App Archiving); drawer folders plus "Caddy" auto-categorised drawer; dock with widgets, custom background colour and corner radius; custom web search engines (Kagi, Ecosia, Firefox...); Smartspace / At a Glance (Smartspacer support); Lawnicons themed icons, open source |
| **Smart Launcher 6** | Active. 6.6 builds every 2–4 weeks, latest 6.6 build 021 (Sept 2026). | Ambient Theme (colours from the wallpaper, refreshes with live wallpaper); automatic app categories in the drawer; Fluid Grid for icon/widget balance; responsive built-in widgets (clock shows alarm, next event, weather); bottom search bar for one-handed use; app protection/hiding |
| **Action Launcher** | Maintenance. v52 = Android compatibility plus a default layout for larger phones. Last real feature work: v47–v50. | Quicktheme (whole UI themed from the wallpaper); Covers (folder hidden behind an app icon); Shutters (swipe an icon to open that app's widget); Quickdrawer/Quickbar; widget stacks (v47); Action Search with a target bar (send query to YouTube/Wikipedia...) |
| **Microsoft Launcher** | Maintenance. 6.26x builds (Apr/May 2026); no major features since the v6 rebuild. | Glance/Feed page (calendar, To Do, Sticky Notes, news); Bing daily wallpaper; continue on PC / Link to Windows; Bing visual search in the dock; screen-time card; account-backed backup |
| **Hyperion Launcher** | Active but slow. 2.1.22 (Mar 2026) added a new theming system and home transition animation after a 9-month gap. | Per-component theming (dock, drawer, search, folders); plugin system (feed, Smartspace); custom transition animations; search bar styles; wallpaper-based colour |
| **Kvaesitso** | Active. 1.41.0 (Sept 2026): preferred currencies, exclude apps from shortcut results, settings gesture, respects "hide Private Space". | Search-first: apps, contacts, calendar, files (local and cloud), Wikipedia, places (OSM), unit and currency conversion; app shortcuts in search; single scrolling column of widgets; tags and favourites; plugin SDK (weather, file and search providers); smart clock showing music, alarm and battery |
| **Olauncher** (and forks mLauncher, Minimal Launcher) | Active. 6.4.2 on F-Droid (Apr 2026). | Text-only home (up to ~8 apps); new wallpaper daily; swipe left/right opens set apps; double-tap to lock; "!bang" web search; no tracking, GPLv3 |
| **Neo Launcher** | Maintenance or slow. Last tagged release 0.9.3 (a December re-release of a 2023 build). Only on F-Droid and GitHub. | Lawnchair/Omega fork; Neo Feed (RSS -1 page); full control of drawer categories; custom search engines; gesture controls; settings backup/restore |
| **Total Launcher** | Active. 3.4.x builds Feb–Apr 2026. Same developer as Square Home. | Free-form object layout (any size or position); panels and sub-pages; tap, long-press and swipe actions on each object; deep shape/visual styling; theme import/export |
| **Apex Launcher** | Abandoned. Last major release v4.0 (2018), which drew complaints about ads and lost settings. | Drawer folders; hidden apps; scrollable dock; icon packs |
| **POCO Launcher / HyperOS Launcher** | Active. Synced with HyperOS 3 (Jan 2026); July 2026 build (stability only). | Drawer auto-categories plus sort-by-colour; swipe-down App Finder search; hide apps; theme store and icon packs; large "super icons" (HyperOS) |
| **Pixel Launcher** | Active. Android 17 betas: hide app labels, customisable pinned search bar, option to remove At a Glance. A "home organizer" that auto-fills pages by category is in development. | At a Glance (contextual header); Google Discover -1 feed; app predictions row in dock/drawer; themed (monochrome) icons; taskbar on foldables/tablets; Private Space and app archiving |
| **Samsung One UI Home + Good Lock Home Up** | Active. One UI 8 moved many Home Up options into stock settings. Home Up 17.5 for One UI 8.5 (Jan 2026). On the Fold 8 the taskbar shows 2–4 recent or suggested apps. | Taskbar with recent/suggested apps; cover-screen mirroring on/off; widget stacks plus Now Bar; Edge Panel side launcher; resizable, scrollable large folders with optional blur; per-icon label font and colour (8.5); Task Changer layouts; DIY free placement; Easy mode |
| **Nothing Launcher** | Active. Nothing OS 4.0/4.1 (2026). | Monochrome Nothing icons and icon packs; Smart Drawer AI categories plus hidden apps; resizable big icons/folders and 2x2 Quick Settings tile widgets; Essential Apps (AI-generated widgets from a prompt, Playground alpha); AI usage dashboard; Extra Dark mode |
| **Ratio** (Blloc) | Active. 7.3.0 (Jun 2026). | "Tree" of grouped app blocks plus dock; unified messaging inbox ("Fluid"); time tracker; app lock; greyscale, low-distraction look; on-device processing only |
| **Before Launcher** | Active, but no recent feature news (8.0.1: edge-to-edge and notification-screen fixes; date unconfirmed). | Notification filter (trivial alerts go silently to a separate drawer); text-list home; distraction-free design |
| **Square Home** | Active. 4.0.x redesign (Jun–Jul 2026): "Glass edge" and "Blurred (Inverse)" tile styles, rounded tiles, Global Search as a page. | Windows Metro-style live tiles (notifications, photos, contacts on tiles); many tile sizes and styles; search as its own page; good tablet/landscape layout; lock screen |
| **AIO Launcher** | Active. 7.6.0 (Oct 9, 2026); 7.4 Focus/Text widgets, 7.2 cloud sync plus AI script generator. | Home is a single scrolling list of info widgets; Lua scripting plus Tasker integration; AI script generator; sync via Google Drive, OneDrive or WebDAV; Focus (Pomodoro), Habits and App Usage widgets; messenger and notification widgets |
| **KISS Launcher** | Active. 3.26.0 (Jul 2026). | Search-only home; results ranked by usage history; tiny (~250 KB); contacts, calls and settings in results; tags/favourites; inline calculator (supports %) |
| **Lynx Launcher** | Active but slow. 1.4.8 (May 2026). | Three-page layout: search page, home, categorised drawer; drawer auto-categories; swipe gestures; infinite page scrolling; movable dock |
| **Fossify Launcher** | Active, beta. 1.10.0 (Feb–Apr 2026). | No INTERNET permission; simple grid plus drawer; widget support; open source (Fossify suite) |
| **MinkLauncher** | Active. F-Droid release Aug 2026. | Keyboard-first navigation; work-profile apps and badges; optional daily app-time limits |
| **Posidon Launcher** | Abandoned. Discontinued; last commit Sept 2023. | One-page home with built-in RSS news feed; notifications on home; blur UI |
| **Lean / Rootless Pixel Launcher** | Abandoned. Lean's last release was 2018 and its repo was archived Dec 2023; Rootless is stale. | Pixel look without root; Google feed bridge; icon packs. No longer relevant beyond history. |

Takeaways:
1. The 2025–26 **Nova saga** (founder left, sale, ads plus trackers) shows a clear market opening: users want a power launcher with **transparent monetisation and no ad SDKs**.
2. Third-party launchers have almost **no foldable-specific features**. Only One UI Home (taskbar, cover mirroring) and Pixel (taskbar) treat the hinge as a first-class design input. FoldOS's screen modes are a real differentiator.
3. The most active third-party projects in 2026 are **AIO, Smart Launcher, Kvaesitso, Square Home, Niagara, KISS**. They focus on search, widget feeds, scripting, digital wellbeing and theming tools.

---

## 2. Master feature list (deduplicated, by category)

Key: **[P]** = already covered by PLAN.md, **[P~]** = partly covered, **[N]** = not in plan.

### Home / layout
| Feature | Description | Launchers |
|---|---|---|
| Icon grid with configurable rows/cols [P] | Classic pages of icons and widgets | Nova, Lawnchair, Smart, Action, Pixel, One UI, Hyperion, Neo, POCO, Nothing, Fossify |
| Subgrid / free placement [P] | Place items between cells or anywhere | Nova (subgrid), Total (free-form), One UI Home Up (DIY Home), Smart (Fluid Grid) |
| Vertical list home [P] | Text or icon list instead of pages | Niagara, Olauncher, Before, Ratio, KISS (search list) |
| Widget-feed home [P] | Home is a scrolling column of widgets | AIO, Kvaesitso, Microsoft (Feed), Posidon |
| Live tiles [N] | Tiles that show changing content (photos, unread, contacts) | Square Home |
| Infinite / looping page scroll [N] | Last page wraps around to the first | Nova, Lynx |
| Panels and sub-pages [P~] | Sliding panels or nested pages inside a page | Total, Square Home (search page), Lynx (search page) |
| -1 feed page [P~] | Discover/news/RSS page left of home | Pixel (Discover), Microsoft (Feed), Neo (Neo Feed), Nova (Nova Now), Posidon |
| Auto-built pages by category [N] | Launcher creates a page filled with "Games"/"Productivity" apps and widgets | Pixel (Android 17, in development) |
| Hide labels / per-icon label styling [P~] | Hide all labels; per-icon label text, font and colour | Pixel (A17), One UI Home Up 8.5, Nova |
| Lock layout [P] | Prevent accidental edits | Nova, One UI, Lawnchair, Smart |
| Easy / senior mode [N] | Big icons, simplified layout | One UI (Easy mode), Nothing (big icons), POCO/HyperOS (super icons) |

### Dock
| Feature | Description | Launchers |
|---|---|---|
| Configurable dock with folders [P] | Size, items, folders | All mainstream |
| Scrollable / multi-page dock [N] | Dock pages you swipe sideways | Nova, Action (multiple dock pages) |
| Widgets in the dock [N] | Small widgets beside dock icons | Lawnchair 15 |
| Dock search bar styling [P~] | Search pill in the dock, customisable | Pixel, Lawnchair, Action, Hyperion |
| Taskbar with recent/suggested apps [P] | Taskbar showing 2–4 recent or predicted apps | One UI (Fold 8), Pixel (tablet/fold) |
| Predicted apps row [P~] | Row of AI-predicted apps | Pixel, Smart, Microsoft |

### App drawer
| Feature | Description | Launchers |
|---|---|---|
| Auto-categories [P] | Drawer sorted into categories automatically | Smart, Nothing Smart Drawer, POCO, Lawnchair (Caddy), Lynx |
| Tabs and drawer folders [P] | Custom tabs and folders | Nova, Lawnchair, Action, Neo, Apex |
| Tags (many tags per app) [N] | One app can belong to several tags/filters | KISS, Kvaesitso |
| Sort by colour [P] | Filter or sort icons by dominant colour | POCO |
| Hidden apps [P] | Hide from drawer and search | Nearly all |
| Alphabet scroller / wave scroller [P~] | Fast-scroll rail; Niagara's animated "wave" for one-handed use | Niagara, Pixel, Nova, Ratio |
| Work profile tab and pause [N] | Separate work apps, pause the work profile | Pixel, Lawnchair, Nova, MinkLauncher |
| Private Space and archived apps [P~] | Android 15+ Private Space; show archived apps with a re-download badge | Pixel, Lawnchair 15, Kvaesitso |

### Search
| Feature | Description | Launchers |
|---|---|---|
| Universal on-device search [P] | Apps, contacts, settings, files, calendar | Kvaesitso, KISS, Smart, Nova, Pixel, Microsoft, Square Home |
| App shortcut / deep-link search [N] | Jump into actions inside apps (e.g. "Spotify > liked songs") | Nova (Sesame), Kvaesitso (shortcut results), Action Search |
| Usage-ranked results [N] | Results ordered by history and how recent | KISS, Kvaesitso, Niagara |
| Custom web engines and bangs [N] | Choose or add engines (Kagi, Ecosia, Brave); "!bang" shortcuts; send query to a specific app | Lawnchair, Olauncher, Action (target bar), Neo, Niagara (Bing) |
| Currency, Wikipedia, places answers [P~] | Instant answers beyond unit conversion | Kvaesitso, KISS (calculator %) |
| Cloud file search [P~] | Search Drive/OneDrive/Nextcloud files | Kvaesitso |
| Visual search / Lens in search [N] | Camera/Lens entry point in the search bar | Microsoft (Bing visual), Pixel (Lens), Action (Lens trigger) |
| Search as a page or swipe target [P] | Dedicated search page | Square Home, Lynx, Kvaesitso, Nova |

### Gestures
| Feature | Description | Launchers |
|---|---|---|
| Home-screen gestures [P] | Swipes, double-tap, pinch | Nova, Smart, Hyperion, Neo, Lynx, Kvaesitso, Olauncher, Action |
| Per-icon swipe actions [N] | Swipe up on a single icon to open a second app/shortcut | Nova, Action (Shutters), Niagara (pop-ups), Total (per-object actions) |
| Cover folders [N] | Tap the icon opens the main app, swipe opens the folder | Action (Covers), Nova |
| Double-tap to lock [P] | Lock the screen via accessibility | Olauncher, Nova, Smart, Niagara |
| Keyboard navigation [N] | Arrow keys, type-to-search, shortcuts with a physical keyboard | MinkLauncher, KISS, Kvaesitso |
| Floating launcher overlay [N] | Launcher panel you can open over any app (edge swipe or power long-press) | One UI Edge Panel, Swipe Launcher |

### Widgets
| Feature | Description | Launchers |
|---|---|---|
| Android widget hosting [P] | Any AppWidget | All |
| Widget stacks [P] | Swipe through stacked widgets | One UI, Action (v47), Smart, Nothing |
| Icon-to-widget pop-up [N] | Swipe or long-press an icon to show that app's widget in a pop-over | Action (Shutters), Niagara (pop-ups) |
| Built-in productivity widgets [P] | Focus/Pomodoro, habits, notes, to-do, app usage | AIO, Microsoft, Smart, Kvaesitso |
| Scripted/custom widgets [N] | User-written (Lua) or AI-generated widgets | AIO (Lua plus AI script generator), Nothing Essential Apps |
| Text/URL widget [N] | Show manual text, a local file or a URL's text | AIO 7.4 |
| Now Bar / live activity strip [P~] | Live media, sports, timers pinned to home/cover | One UI Now Bar, Pixel At a Glance |

### Theming / icons
| Feature | Description | Launchers |
|---|---|---|
| Wallpaper-derived colours [P] | Ambient Theme, Quicktheme, Material You | Smart, Action, Pixel, Lawnchair, Hyperion |
| Icon packs and per-app override [P] | Third-party packs, single icon edits | Nova, Lawnchair, Smart, Action, Hyperion, Neo, POCO, Nothing |
| Themed monochrome icons [P~] | Force monochrome or tinted icons, including apps without support | Pixel, Lawnchair (Lawnicons), Nothing, Neo |
| Theme editor and community sharing [P~] | Build a theme in-app and share it with a gallery | Niagara Theme Studio, Hyperion, Total |
| Daily wallpaper [N] | Auto-rotating wallpaper (Bing, curated) | Microsoft (Bing), Olauncher |
| Tile/glass/blur styles [P] | Glass edge, inverse blur, corner radius | Square Home, One UI (folder blur) |
| Dynamic icons [N] | Calendar date and clock icons that update live | Pixel, One UI, Nova, POCO/HyperOS |
| Greyscale / low-stimulus mode [N] | Desaturate icons or the whole UI to reduce distraction | Ratio, Niagara, Olauncher |

### Notifications
| Feature | Description | Launchers |
|---|---|---|
| Badges / unread counts [P] | Dots or counts through a notification listener | Nova, Action, Smart, Lawnchair, Niagara, Hyperion |
| Long-press preview [P] | Notification preview on long-press | Pixel, Action, One UI |
| Inline actionable notifications [N] | Notification text shown under the app in the list/home; reply or dismiss in place | Niagara, Posidon, Square Home (live tiles) |
| Notification filter / quiet drawer [N] | Trivial notifications go silently to a separate drawer | Before Launcher |
| Unified messaging inbox [N] | All chat apps' messages in one launcher view | Ratio (Fluid), AIO (messenger widgets) |

### Privacy
| Feature | Description | Launchers |
|---|---|---|
| Hidden apps with lock [P] | PIN/biometric for hidden apps | Smart, Nova, Ratio, Nothing, POCO |
| Private Space support [P] | Android 15+ | Pixel, Lawnchair, Kvaesitso |
| No-network guarantee [N] | Build or toggle without INTERNET; no ad or analytics SDKs | Fossify, KISS, Olauncher, MinLauncher |
| AI usage transparency [N] | Dashboard of what AI ran, when, on what data | Nothing OS 4 |
| On-device-only processing claim [P] | No cloud processing | Ratio, Kvaesitso (FOSS build) |

### AI / smart
| Feature | Description | Launchers |
|---|---|---|
| Predictions and contextual header [P] | Predicted apps; At a Glance-style header | Pixel, Smart, Microsoft |
| AI categorisation [P] | ML-sorted drawer | Nothing, POCO, Lawnchair (Caddy) |
| Prompt-to-widget generation [N] | Describe a widget, AI builds it | Nothing Essential Apps, AIO (AI script generator) |
| Launcher AI assistant [P] | Chat/assistant in the launcher | Nova AI (teardown), AIO, FoldOS plan |
| Wellbeing nudges [N] | Usage Breaker reminders, daily app-time limits, time tracker | Niagara, MinkLauncher, Ratio, AIO (App Usage), Olauncher |

### Backup / sync
| Feature | Description | Launchers |
|---|---|---|
| Android/Google auto-backup [P] | Settings in device backup | Niagara, Pixel, One UI |
| Manual export/import [P] | Backup file | Nova, Lawnchair, Neo, Smart, Square Home |
| Multi-cloud sync [N] | Sync settings live via Drive, OneDrive or WebDAV; across devices | AIO, Microsoft (account) |
| Import from other launchers [P~] | Import a Nova/Lawnchair/One UI layout | Nova, Lawnchair, Action |

### Foldable / large-screen
| Feature | Description | Launchers |
|---|---|---|
| Separate cover and inner layouts [P] | Independent layouts per screen | One UI (mirroring toggle) |
| Taskbar [P] | Persistent taskbar on the inner screen | One UI, Pixel |
| Landscape / tablet layouts [P] | Landscape-specific grids | Square Home, Smart, Lawnchair, Action (larger phone layout) |
| App pairs [P] | Split-screen shortcuts | One UI, Pixel |
| Floating/pop-up window launch [P] | Launch an app in a window | One UI, Nothing (two floating icons) |
| Recents / Task Changer layouts [N] | Grid, stack or list recents | One UI Home Up, POCO (system-level; third-party launchers usually cannot replace recents on Samsung) |

### Performance / other
| Feature | Description | Launchers |
|---|---|---|
| Tiny footprint [P~] | Very small APK and RAM use | KISS (~250 KB), Olauncher, Fossify |
| Scripting and automation [N] | Lua, Tasker plugin/actions, intents API | AIO, Nova (Tasker), Total |
| Transition animation choices [P] | Custom open/close/home animations | Hyperion, One UI Home Up, Action |
| Transparent, ad-free monetisation [N] | Clear one-time Pro, no ad SDKs | Niagara, Lawnchair/KISS (free), Nova (counter-example, 2026) |

---

## 3. Features NOT already in our plan

Checked against PLAN.md (v0.1). These are candidates to adopt, each behind a toggle as the plan requires. The **FoldOS fit** column says how each fits our design.

| # | Category | Feature | From | FoldOS fit |
|---|---|---|---|---|
| 1 | Notifications | Inline actionable notifications | Niagara, Posidon | Show the latest notification under the app in Minimal list style; reply or dismiss in place. Good fit for the cover screen. |
| 2 | Notifications | Notification filter / quiet drawer | Before Launcher | Rules route low-value notifications to a silent drawer; ties into Profiles. |
| 3 | Notifications | Unified messaging inbox | Ratio, AIO | A widget/panel merging WhatsApp, Messages, Slack and others (NotificationListener). |
| 4 | Gestures | Per-icon swipe actions | Nova, Action, Total | Swipe up on an icon opens a second app, shortcut or widget. |
| 5 | Folders | Cover folders | Action, Nova | Tap opens the main app, swipe opens the folder. Saves dock space. |
| 6 | Widgets | Icon-to-widget pop-up | Action Shutters, Niagara | Swipe an icon to peek at its widget without placing it. |
| 7 | Search | App shortcut / deep-link search | Nova (Sesame), Kvaesitso | Index ShortcutManager shortcuts and in-app actions. |
| 8 | Search | Usage-ranked results | KISS, Kvaesitso | Rank by how often and how recently items are used, on-device. |
| 9 | Search | Custom engines, bangs, send-to-app | Lawnchair, Olauncher, Action | Kagi/Ecosia/Brave, "!yt" bangs, target bar. |
| 10 | Search | Visual search entry | Pixel, Microsoft, Action | Lens/Circle-to-Search button in the search bar. |
| 11 | Search | Currency, Wikipedia and places answers | Kvaesitso | Extends the unit converter; offline caches. |
| 12 | Drawer | Tags (multi-membership) | KISS, Kvaesitso | One app in many tabs or filters; tags searchable. |
| 13 | Drawer | Work profile tab and pause toggle | Pixel, Lawnchair, MinkLauncher | Not listed explicitly in the plan. |
| 14 | Drawer | Archived-app display | Pixel, Lawnchair 15 | Show archived apps with a cloud badge; tap to restore. |
| 15 | Dock | Scrollable multi-page dock | Nova, Action | Useful on the narrow cover screen. |
| 16 | Dock | Widgets in the dock | Lawnchair 15 | e.g. a mini media or clock widget in the inner-screen dock. |
| 17 | Home | Infinite / looping page scroll | Nova, Lynx | Simple toggle. |
| 18 | Home | Auto-build page from a category | Pixel (A17, in development) | "New page: Travel" fills it with matching apps and widgets. Fits Presets and AI. |
| 19 | Home | Live tiles | Square Home | A tile style for the dashboard showing photos, unread and contacts. |
| 20 | Home | Easy / large mode | One UI, Nothing, HyperOS | Accessibility preset: big icons, simple pages. |
| 21 | Theming | Daily wallpaper rotation | Microsoft (Bing), Olauncher | Per-screen and per-profile source (Bing, NASA, local album). |
| 22 | Theming | Dynamic icons | Pixel, One UI, Nova | Calendar date and clock icons that update live. |
| 23 | Theming | Greyscale / low-stimulus mode | Ratio, Niagara | Desaturates icons; can be scheduled by Profile. |
| 24 | Theming | Theme Studio plus community gallery | Niagara, Hyperion | The plan shares presets as files; add an in-app gallery or share link. |
| 25 | Wellbeing | Usage nudges and app-time limits | Niagara, MinkLauncher, Ratio | Gentle reminder after X minutes; daily limits; time on home. |
| 26 | AI | Prompt-to-widget generation | Nothing Essential Apps, AIO | Gemini Nano builds a custom built-in widget from a description. |
| 27 | Privacy | AI usage transparency log | Nothing OS 4 | Shows each AI action, its data source and whether it ran on-device. |
| 28 | Privacy | No-network mode / no ad SDKs | Fossify, KISS, Olauncher | Hard switch that disables all network providers; publish a "no ads, no trackers" pledge (lesson from Nova 2026). |
| 29 | Automation | Tasker plugin, intents API, scripting | AIO, Nova, Total | Expose launcher actions and state to Tasker and Samsung Modes; optional Lua or JS widgets. |
| 30 | Sync | Multi-cloud live sync | AIO, Microsoft | Drive, OneDrive or WebDAV sync of layouts across devices (phone and tablet), beyond Google backup. |
| 31 | Backup | Import from third-party launchers | Nova, Lawnchair | Extend the wizard import to Nova (.novabackup), Lawnchair and Niagara exports. |
| 32 | Input | Keyboard-first navigation | MinkLauncher, KISS | Type-to-search and arrow navigation on the inner screen with a BT keyboard or DeX. |
| 33 | Multitasking | Floating launcher overlay | One UI Edge Panel, Swipe Launcher | Mini app and pair launcher over any app (needs an overlay permission). |
| 34 | Multitasking | Recents layout styling | One UI Home Up, POCO | Probably not feasible: One UI keeps recents in the system launcher. Note it as a limitation. |

---

## Sources

- Nova: [novalauncher.com update](https://novalauncher.com/nova-is-here-to-stay), [Gigazine on acquisition](https://gigazine.net/gsc_news/en/20260121-instabridge-acquired-nova-launcher), [Heise](https://heise.de/-11147990), [Android Authority](https://www.androidauthority.com/used-nova-launcher-10-years-done-with-it-why-3659417/)
- Niagara: [Studio Update](https://medium.com/niagara-launcher/studio-update-509479fdcf4c), [Android Authority](https://www.androidauthority.com/niagara-launcher-update-new-themes-wallpapers-3613608/), [Android Police hands-on](https://www.androidpolice.com/2021/02/02/hands-on-the-minimalist-niagara-launcher-is-officially-out-of-beta/)
- Lawnchair: [15 Beta 1 blog](https://lawnchair.app/blog/lawnchair-15-beta-1/), [APKMirror 15 Beta 2.1](https://www.apkmirror.com/?p=12803294)
- Smart Launcher: [smartlauncher.net](https://www.smartlauncher.net), [APKMirror history](https://www.apkmirror.com/apk/smart-launcher-team/smart-launcher/)
- Action Launcher: [release notes](https://www.actionlauncher.com/release-notes)
- Microsoft Launcher: [APKMirror](https://www.apkmirror.com/apk/microsoft-corporation/microsoft-launcher/)
- Hyperion: [APKMirror 2.1.22](https://www.apkmirror.com/?p=12894875)
- Kvaesitso: [F-Droid](https://f-droid.org/en/packages/de.mm20.launcher2.release/)
- Olauncher: [F-Droid](https://f-droid.org/zh_Hant/packages/app.olauncher/)
- Neo Launcher: [GitHub releases](https://github.com/NeoApplications/Neo-Launcher/releases)
- Total Launcher / Square Home: [APKMirror Square Home](https://www.apkmirror.com/apk/total_apps/square-home/), [APKMirror Total Launcher](https://www.apkmirror.com/?p=4699577)
- Apex: [Notebookcheck on v4.0](https://www.notebookcheck.net/Apex-Launcher-4-0-is-a-complete-mess.317306.0.html)
- POCO/HyperOS: [Xiaomiforall](https://xiaomiforall.com/poco-launcher-hyperos-3-update/), [Ximitime July 2026](https://ximitime.com/poco-launcher-july-2026-update-released-new-version-now-available-98399/)
- Pixel Launcher: [Droid-Life Android 17 Beta 3](https://www.droid-life.com/2026/03/27/the-6-best-new-features-in-android-17-beta-3/), [PhoneArena](https://www.phonearena.com/news/the-google-pixel-launcher-is-finally-learning-a-trick-samsung-mastered-years-ago_id179235)
- One UI / Home Up: [SamMobile Home Up 8.5](https://www.sammobile.com/2026/01/19/samsung-good-lock-home-up-update-one-ui-8-5-new-features/), [9to5Google One UI 8 Good Lock](https://9to5google.com/2025/07/22/samsung-one-ui-8-good-lock-updates/), [SamMobile Fold 8 hidden settings](https://www.sammobile.com/news/hidden-settings-available-when-you-unfold-the-galaxy-z-fold-8/)
- Nothing: [91mobiles Nothing OS 4 roundup](https://www.91mobiles.com/hub/nothing-os-4-roundup-release-timeline-eligible-phones-features/amp/), [GSMArena Phone (4a) Pro review](https://m.gsmarena.com/nothing_phone_4a_pro-review-2949p4.php)
- Ratio: [APKMirror](https://www.apkmirror.com/apk/prometheus-interactive-llc/ratio/ratio-5-2-6-release/ratio-productivity-homescreen-5-2-6-android-apk-download/)
- Before Launcher: [APKMirror](https://www.apkmirror.com/apk/⎋-before-labs/before-launcher-go-minimal)
- AIO Launcher: [APKMirror](https://www.apkmirror.com/apk/aio-mobile-apps/aio-launcher/), [7.4.0 notes](https://www.apkmirror.com/apk/aio-mobile-apps/aio-launcher/aio-launcher-7-4-0-release/)
- KISS: [F-Droid](https://www.f-droid.org/en/packages/fr.neamar.kiss)
- Lynx: [APKMirror](https://www.apkmirror.com/apk/n116-soft/lynx-launcher/lynx-launcher-1-4-5-1025-release/)
- Posidon: [AlternativeTo](https://alternativeto.net/software/posidon-launcher/about)
- Lean/Rootless: [AlternativeTo Lean](https://alternativeto.net/software/lean-launcher/about), [Android Authority Rootless](https://www.androidauthority.com/rootless-pixel-launcher-removed-google-play-store-901563/)
- Fossify: [fossify.org](https://www.fossify.org/apps/launcher/)
- Minimal roundup 2026: [Alphonso Labs](https://www.alphonsolabs.com/best-android-launchers-minimalist-2026/amp/), [SlashGear](https://www.slashgear.com/2237061/best-android-launchers-must-try-2026/)
