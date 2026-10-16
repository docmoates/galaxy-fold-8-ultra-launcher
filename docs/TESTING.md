# FoldOS manual device test plan

Target: Samsung Galaxy Z Fold 8 Ultra (One UI 8, Android 16). Build under test: the
`foldos-release-apk` artifact (or the debug APK for the first pass). Nothing in FoldOS has been run
on a device before this plan; expect failures and record them.

How to use: work top to bottom. Each row is an action and the result you should see. Mark Pass /
Fail and note the build, the screen (cover / inner / Flex) and a screen recording for failures.
Unless a section says otherwise, run it on both screens.

## 0. Setup

| # | Action | Expected |
|---|---|---|
| 0.1 | `adb install -r app-release.apk` | `Success`. App "FoldOS" appears in the app list (its icon is on the Settings activity, not on the home activity). |
| 0.2 | Open FoldOS from the app list | Settings opens with a "Set as default home" offer. |
| 0.3 | Accept the role dialog | FoldOS becomes the home app; pressing Home shows the FoldOS wizard on first run. |
| 0.4 | Check Settings > Apps > Choose default apps > Home app | FoldOS is selected. |
| 0.5 | Complete the 5-step wizard (screens, home style, look, colors, dock) by tapping previews | Each step advances; Finish lands on a home that matches the choices; the wizard does not return after a restart. |
| 0.6 | Force-stop FoldOS, press Home | Home reappears with the same layout in under 2 s. |

## 1. Screens and fold states

Defaults: cover is Classic grid with a floating pill dock; inner is Widget dashboard with the taskbar
dock; screen mode "Different layouts".

| # | Action | Expected |
|---|---|---|
| 1.1 | Cover screen, closed | 4-column grid, floating pill dock, search bar, page dots with the hub icon. No content under the status bar or gesture bar. |
| 1.2 | Unfold with FoldOS on screen | Activity is not recreated (no flash of a blank home, open sheets stay open); the inner dashboard layout appears; widgets reflow. |
| 1.3 | Inner screen portrait / landscape | Dashboard uses the wider grid; dock stays within the screen; an 18 dp gutter sits over the hinge only when the hinge is vertical and the page style is grid. |
| 1.4 | Half-fold to Flex Mode (laptop posture) | Top half shows page 1 above the hinge, the app row and dock sit below; nothing crosses the hinge line. |
| 1.5 | Un-half-fold | Returns to the full inner layout with scroll position and open sheets preserved. |
| 1.6 | Fold again | Cover layout returns; the page you were on is kept when screen mode is Continuity, page 1 otherwise. |
| 1.7 | Settings > Screens: set "Same layout" | Inner screen shows the cover layout scaled to a bigger grid. Set back to "Different" and the inner layout is intact. |
| 1.8 | Settings > Screens: "Continuity" | Unfolding expands the current cover page into its inner counterpart. |
| 1.9 | Toggle the fold transition (animated / instant) | Animated: a morph on fold and unfold. Instant: no animation. |
| 1.10 | Rotate the device on the inner screen | Layout re-flows without losing state. |

## 2. Home styles

Set each style from edit mode (long-press > style chip) on one page and check it, then mix styles on
different pages.

| # | Style | Expected |
|---|---|---|
| 2.1 | Classic grid | Icons and labels aligned to cells, folders and pairs allowed, drag moves icons between cells and pages. |
| 2.2 | Widget dashboard | Widgets fill the page; resize handles on corners; apps live in dock and drawer. |
| 2.3 | Minimal list | Text list of favorites with an alphabet scrubber, no icons. |
| 2.4 | Hybrid panels | Inner screen splits into an app panel and a widget panel; the ratio can be adjusted; cover falls back to a single column. |
| 2.5 | Per-page mix | Page 1 grid, page 2 dashboard, page 3 list: swipe through, each keeps its style after a restart. |
| 2.6 | Add, duplicate-style, delete a page in edit mode | Dots update; deleting re-indexes the remaining pages; items on the deleted page are gone. |

## 3. Dock presets and traits

Settings > Dock, separately for cover and inner.

| # | Action | Expected |
|---|---|---|
| 3.1 | Cycle all 20 presets (Floating pill, Edge shelf, Icons only, Labeled, Two rows, Scrolling, Taskbar, Search dock, Magnify, 3D shelf, Hairline, Text dock, Bubbles, Folder dock, Smart dock, Edge rail, Swipe handle, Notification dock, Media dock, Center action) | Dock redraws immediately each time, no clipped icons, labels or bars; the edge rail is vertical with icons only. |
| 3.2 | From a preset, switch one trait (e.g. add Search bar) | The preset name becomes "Custom mix"; icons shrink when many add-ons share a row. |
| 3.3 | Tap each dock app, long-press one | App launches; context menu appears. |
| 3.4 | Search dock: tap the bar | Universal search opens (section 7). |
| 3.5 | Media dock with music playing | Shows title and play / pause / next controls that work. |
| 3.6 | Notification dock with notifications | Shows the latest notification title; tap opens the quiet drawer. |
| 3.7 | Swipe handle: swipe up from the handle | Drawer opens. |
| 3.8 | Cover and inner use different docks | Changing one does not alter the other. |

## 4. App drawer

| # | Action | Expected |
|---|---|---|
| 4.1 | Swipe up on the cover (default gesture) | Pop-up sheet rises to about 78 % height with a grab handle; home is blurred and dimmed behind it. |
| 4.2 | Open the drawer on the inner screen | Floating centered card, not a bottom sheet. |
| 4.3 | Settings > App drawer: switch to Full screen | Classic full-screen drawer. Turn "pop-up blur" off: backdrop is dimmed, not blurred. |
| 4.4 | Type in the drawer search | List filters live; Enter launches the first result. |
| 4.5 | Group folders (All, Work, Media, Social, Tools, Games, your own), sort order | Tapping a folder filters the grid; sort changes the order. Section 16 covers adding apps to groups and dragging them to the home screen and dock. |
| 4.6 | Hide an app (context menu or Settings > Hidden apps) | It disappears from drawer, search and Add-to-folder pickers; it is still listed on the Hidden apps page. |
| 4.7 | Uninstall action in the app context menu | System uninstall dialog for that package only. |

## 5. Widget picker, widgets and live edit

| # | Action | Expected |
|---|---|---|
| 5.1 | Long-press empty home space or a widget | Edit mode: the home shrinks to about 76 % between one top bar and one bottom bar (Wallpaper, Themes, Widgets, Dock, Settings). |
| 5.2 | Open Widgets on the cover | Bottom sheet (about 84 % high, grab handle) with a search field, a Featured grid of FoldOS widgets and an Apps list (FoldOS first, then apps A-Z) with filter chips. Tapping an app or a featured card replaces the list with that app's detail view and a back arrow; the back button returns to the list. |
| 5.3 | Open Widgets on the inner screen | Floating card: the list in a left pane (320 dp) and the swipe-through detail view on the right, with the first featured widget already selected. Tapping another app or card updates the right pane. |
| 5.4 | Search "calendar" | Flat results match widget names, app names and categories; clearing restores the Featured grid and Apps list. |
| 5.5 | Settings > Widgets > Widget picker groups by Category / App / Recent | Category shows the filter chips, App hides them, Recent adds a "Recently used" section above Featured (after adding a widget). Reset recent widgets clears that section. |
| 5.5a | In an app's detail view, swipe left and right | One widget per page with a large uncropped preview, its name, size, source and a short description; page dots (or "3 / 12") follow the swipe; "Add to home" adds the widget on the current page and closes the picker. Built-in widgets are placed at once; an app widget opens the system bind dialog and its configure screen as before. |
| 5.6 | Add each built-in widget (Clock, Glance, Weather, Calendar agenda, Devices, To-do, Now Playing, Focus, Photos, News) | Placed at the first free cell; adapts to 1x1 up to 4x4; follows theme and dark / light. |
| 5.7 | Add a Calendar agenda widget without the permission | Shows an "Allow calendar access" button; granting it shows events. Same for Photos (photo access) and Devices (Bluetooth). |
| 5.8 | Add an app widget (e.g. Google Clock) | System bind dialog the first time; widget appears; a widget with a configure screen opens it first. Cancelling configure removes the pending widget. |
| 5.9 | Resize a widget from its corner | Live preview while dragging; snaps to cells; content adapts. |
| 5.10 | Drag a widget onto another | They become a stack; swiping flips between them. Settings > Features > Widget stacks off: the drop just moves/overlaps nothing. |
| 5.11 | Free placement (page mode Free) | Items sit at arbitrary positions and persist after a restart. |
| 5.12 | Delete a widget with the X on the selected item | Gone, with an Undo snackbar; after the Undo window (about 6 s) its app widget id is released (re-adding works). |
| 5.13 | Drag an item toward the page edge and hold | Page switches; the item drops on the new page. |
| 5.14 | Lock layout on (if present) | Long-press no longer enters edit mode / moves items. |
| 5.15 | Reboot the phone | Hosted widgets rebind and render without being re-added. |

## 6. Gestures

Settings > Gestures. Run each on a home page with edit mode off.

| # | Gesture | Default expected | Rebind check |
|---|---|---|---|
| 6.1 | Swipe up | Opens drawer | Rebind to Search: opens search instead, once (not twice) |
| 6.2 | Swipe down | Pulls the notification shade | Rebind to Quick settings |
| 6.3 | Two-finger swipe up | Opens search | |
| 6.4 | Two-finger swipe down / left / right | None | Bind each to an app and confirm it launches |
| 6.5 | Double tap | None | Bind to Edit mode: enters edit mode |
| 6.6 | Pinch in | Edit mode | Pinch out bound to Hub: scrolls to the Hub page |
| 6.7 | Home key double press | None | Bind to Search: second press within the double-tap window opens search |
| 6.8 | Fold / unfold | None | Bind unfold to an app: unfolding launches it once |
| 6.9 | Settings > Features > Gestures off | None of the above fire; swipe up still opens the drawer |
| 6.10 | Gestures while the drawer, picker, search or edit mode is open | Not triggered |

## 7. Universal search

| # | Action | Expected |
|---|---|---|
| 7.1 | Open from the home search bar | Keyboard up, sectioned results as you type. |
| 7.2 | Type an app name | App and its shortcuts; usage ranks previously picked results first. |
| 7.3 | Type a contact name (permission granted) | Contact row with call / message. Without permission: an Allow row, not an error. |
| 7.4 | Type "wifi" | FoldOS or system settings entries. |
| 7.5 | Type a file name that exists in Downloads | File result opens in the matching app. |
| 7.6 | Type an event title | Calendar result (permission required). |
| 7.7 | Type `12*(3+4)` | Calculator answer 84. |
| 7.8 | Type `5 km in miles` | Unit conversion about 3.11 mi. |
| 7.9 | Type a free-text query, tap the web row | Browser opens the chosen engine (Settings > Search > engine). `!w kotlin` style bangs route to the right site. |
| 7.10 | Turn off a provider in Settings > Search | Its section disappears from results. |
| 7.11 | Lens button | Opens Google Lens when installed; otherwise hidden or a toast. |
| 7.12 | Settings > Features > Privacy > No-network mode on | The web row, URL row, Lens button and "web" in the placeholder disappear from search; Mail shows the "No-network mode is on" message; AI keeps answering on the device. |

## 8. Folders and app pairs

| # | Action | Expected |
|---|---|---|
| 8.1 | In edit mode drop an app on another | A folder is created with both; it can be renamed. |
| 8.2 | Tap the folder | Opens next to its icon in the chosen style; Grid / List / Big look different. |
| 8.3 | Drag an app out of the folder | It returns to the grid; the last child leaving deletes the folder. |
| 8.4 | Long-press an app > Add to pair, pick a second app | A pair icon "A & B" appears. |
| 8.5 | Tap the pair | Both apps launch side by side in split screen (inner screen: left and right; cover: top and bottom). |
| 8.6 | Features > App pairs off | Pair icons stop launching pairs / the menu entry is hidden. |

## 9. Notifications and badges

| # | Action | Expected |
|---|---|---|
| 9.1 | Grant notification access (see README) | Settings card changes to granted. |
| 9.2 | Receive messages in two apps | Badge counts (or dots, per Badge style) on those icons; clearing the notification clears the badge. |
| 9.3 | Long-press a badged icon | Preview list of that app's notifications; tapping one opens its target. |
| 9.4 | Quiet drawer | Apps marked quiet stay out of badges and appear in the quiet drawer. |
| 9.5 | Badge style off | No badges. |
| 9.6 | Revoke access | Badges clear; Settings shows the access card again; no crash. |

## 10. Hidden apps

| # | Action | Expected |
|---|---|---|
| 10.1 | Settings > More settings > Hidden apps | A searchable list of every installed app with a checkbox; hidden apps are listed first. |
| 10.2 | Tick an app | It disappears from the drawer, universal search and its home icon cell (the cell stays empty). |
| 10.3 | Untick it | The app is back in the drawer and search. |
| 10.4 | Long-press an icon > Hide, then open the Hidden apps page | The app is ticked there. |
| 10.5 | Open the app after upgrading from a build that still had profiles | Old `profile` / `activeProfile` settings are ignored, nothing crashes, hidden apps are unchanged. |

## 11. Hub

Swipe right from home page 1 (hub is on the left by default).

| # | Action | Expected |
|---|---|---|
| 11.1 | Open the Hub | Opens on Today with the tab bar at the bottom. Inner screen: two panes split at the hinge. |
| 11.2 | Today | Greeting, briefing sentences, next event with Join (if it has a link), tasks due, schedule, mail that needs you, people to reach out to. Empty states instead of errors without permissions. |
| 11.3 | Calendar | Week strip, day timeline with a now line; inner screen adds a month grid. Events match the Samsung / Google Calendar app. |
| 11.4 | Contacts | Favorites, A-Z list, scrubber, call / message buttons place the intent; inner screen shows a contact card. |
| 11.5 | Tasks | Add a task, mark done: persists after a restart. |
| 11.6 | Mail without a client id | Gmail app label counts or "Not configured" with setup hint. |
| 11.7 | Mail with a client id (README steps) | Connect opens a Custom Tab, returns to FoldOS, inbox lists messages; Disconnect clears tokens. |
| 11.8 | AI tab | Ask "what is on my calendar today": a reply. Ask "make my inner screen a dashboard": settings change and the usage log lists the action. |
| 11.9 | Settings > Hub: hub off / right side / tab bar top / each tab off / tab order | Home and Hub reflect each setting. |
| 11.10 | No network on | Mail shows disconnected / offline state; AI keeps working with the template or on-device engine. |

## 12. Backup and restore

Use Settings > Backup.

| # | Action | Expected |
|---|---|---|
| 12.1 | Make a distinctive layout: folder with 3 apps, a pair, a stack of 2 widgets, free-placed widget, a custom icon shape, one hidden app | |
| 12.2 | Export | A `.json` file is saved or shared; opening it shows `"version": 1`, settings, pages with items. No Gmail tokens. |
| 12.3 | Change several settings and wipe pages | |
| 12.4 | Import the file | Success message with an item count; layout, settings (including the hidden app) and icon overrides are back, the folder keeps its children, the stack keeps its order. Hosted app widgets are not restored (they need the system bind dialog) and are reported or skipped without a crash. |
| 12.5 | Import garbage text | Failure message; current data untouched. |
| 12.6 | Import a file edited to `"version": 2` | Rejected with a message; data untouched. |
| 12.7 | Uninstall, reinstall, skip the wizard if offered, import | Layout restored on a clean install; `wizardDone` stays true on the device. |
| 12.8 | Export on the cover, import on the inner screen | Same data; no per-screen duplication. |
| 12.9 | Settings > Backup > Request a backup now; Quick setup > Export / Import rows | The Auto Backup status line matches Android settings; Quick setup export and import behave like the Backup page rows. |
| 12.10 | Android Auto Backup | After `adb shell bmgr backupnow com.foldos.launcher` and a restore, Gmail tokens are absent and the layout is present. |

## 13. Accessibility

Run with TalkBack on for 13.1 to 13.3, then without.

| # | Action | Expected |
|---|---|---|
| 13.1 | Swipe through home with TalkBack | Every icon announces its label (and badge count), widgets announce their name, dock items have labels, nothing is announced as "unlabelled". Focus order is top to bottom, left to right, dock last. |
| 13.2 | Drawer, search, settings with TalkBack | Rows read their title and state (switches say on / off); sheets announce when they open; Back closes them. |
| 13.3 | Edit mode with TalkBack | Move / resize / delete are reachable as actions (not only by dragging). |
| 13.4 | Settings > Accessibility: High contrast on | Text and borders reach at least 4.5:1 against their backgrounds on all four themes, dark and light; glass blur panels become solid. |
| 13.5 | Reduce motion on | Fold morph, page transitions, wobble, parallax and auto-rotating stacks stop or become instant; also respects the system "Remove animations". |
| 13.6 | Text scale 0.85x, 1.0x, 1.6x (and system font at the largest size) | No clipped or overlapping text on home, dock labels, drawer, settings, Hub; layouts wrap or scroll instead. |
| 13.7 | Large icons (easy mode) | Bigger icons and labels, fewer columns, touch targets of at least 48 dp. |
| 13.8 | Greyscale on | Whole launcher UI is greyscale; turning it off restores color without a restart. |
| 13.9 | Combine: high contrast + 1.6x + large icons + reduce motion | Still usable on both screens and in Flex Mode. |
| 13.10 | Switch Access or keyboard navigation (Tab, arrows, Enter) on the inner screen | Every control can be focused and activated; focus is visible. |
| 13.11 | Hardware keyboard on the home screen: type a letter | Universal search opens with that letter already typed and the caret after it; Escape closes it and the home takes keyboard focus back. |

## 14. Look, themes, wallpaper

| # | Action | Expected |
|---|---|---|
| 14.1 | Switch each of the four themes | Home, dock, drawer, settings, Hub all follow; no unreadable text. |
| 14.2 | Appearance Dark / Light / Auto | Auto follows the system (or sunrise and sunset when chosen). |
| 14.3 | Color schemes: Theme colors, each of the twelve primary colors (Blue, Teal, Green, Lime, Amber, Orange, Red, Pink, Purple, Indigo, Slate, Mono), Custom, in Dark and in Light | Accent, highlight cards and wallpaper gradient follow; each primary color shows its light version in Light and its dark version in Dark. |
| 14.4 | Icon pack and icon shape | Installed packs are listed; icons change on home at once (drawer on next launch). |
| 14.5 | Settings > Look > Daily wallpaper on | Home wallpaper changes to a generated gradient at once; change the date or wait until after midnight and it changes again; off stops it. Reboot with it on: next change still happens. |

## 15. Performance and stability

| # | Action | Expected |
|---|---|---|
| 15.1 | Cold start: force-stop, press Home | First frame in under about 1 s on the Fold 8 Ultra; no ANR dialog. |
| 15.2 | Scroll pages and the drawer on the 120 Hz display | No visible dropped frames; check "Profile GPU rendering" or `adb shell dumpsys gfxinfo com.foldos.launcher` for janky frame percentage below about 5 %. |
| 15.3 | Fold / unfold 20 times | No crash, no memory growth (`adb shell dumpsys meminfo com.foldos.launcher` stable). |
| 15.4 | Leave on for a day | Battery usage of FoldOS below ordinary launcher levels (a few percent); no wakelocks. |
| 15.5 | Release-only: repeat sections 6, 7, 11.7, 12 with the R8-shrunk APK | Same behavior as debug. Gesture notification shade (reflection), Gmail sign-in, backup (serialization) and Room are the likely suspects. |
| 15.6 | `adb logcat -b crash` while testing | No FATAL EXCEPTION from `com.foldos.launcher`. |

## 16. Fix round 1.1 scenarios (1.1.0)

Run these first on a fresh install of 1.1.0 (uninstall 1.0.0 once to test the first-run layout, and keep a
second pass over 1.0.0 data to test the duplicate-page cleanup). Both screens unless noted.

| # | Action | Expected |
|---|---|---|
| 16.1 | Fresh install, finish the wizard, look at the page dots | Exactly two home pages per screen: page 1 holds the apps (cover grid) or the dashboard widgets (inner); page 2 is empty. The inner screen does not show three copies of the same dashboard. |
| 16.2 | Update over a 1.0.0 install that had three identical dashboards | After the first launch two pages remain (one populated, one empty); relaunching does not change them; a page you edited before updating is untouched. |
| 16.3 | Empty page 1 completely (edit mode, remove everything), force-stop, press Home | Page 1 stays empty: defaults are not put back. |
| 16.4 | Hub: swipe to the Hub page | The home dock, page dots and top search bar are gone; the Hub tab bar is the only bar and clears the gesture bar; the tab content uses the full height (no squeezed cards). Swipe back: dock and dots return. Check the cover and the inner screen. |
| 16.5 | Hub with the Edge rail dock | The rail is hidden on the Hub page and returns on home pages. |
| 16.6 | Swipe up from empty space, from a widget, and from the dock strip | The app pop-up opens each time, once. Every dock style has an "All apps" button that opens it too. |
| 16.7 | Long-press an app icon, a widget and empty space | Empty space and widgets enter edit mode at once (the widget is selected); an app opens its menu, whose Edit entry enters edit mode with that app selected. |
| 16.8 | In edit mode | The home shrinks to about 76 % between ONE bar at the top (page style segments, page strip, Done) and ONE bar at the bottom (Wallpaper, Themes, Widgets, Dock, Settings); nothing overlaps the dock, search bar or icons; tapping the shrunken dock or search bar does nothing. |
| 16.9 | Select a widget, tap its remove X | The widget disappears and a snackbar "Widget removed" with Undo shows for about 5 s. Tap Undo: the same widget returns in the same cell with its content (an app widget keeps its data). Remove another and wait 6 s: it stays gone. |
| 16.10 | Select an app, remove it, Undo | The app tile returns in its cell. A folder asks for a second tap before removal and has no Undo. |
| 16.11 | Select a widget and drag a corner handle, drag the widget to another cell and to the next page | Handles are reachable below the top bar and above the bottom bar; resize and move snap to the grid cells under the finger (the scaled home matches the guide cells). |
| 16.12 | Add page, delete page (confirm), restyle a page with the style segments | One page is added or removed per tap; deleting asks once; the page strip stays in sync with the pager. |
| 16.13 | Dock with the Search bar add-on (Search dock preset) | Only the dock has a search bar; the top of the home has none. Switch to a dock without it: the top search bar returns. |
| 16.14 | Open search from the dock bar, type "mail" | The result sheet covers the window with no strip of the old home above it, the field stays pinned at the top, results sit above the keyboard, and the home does not slide down. Close the keyboard and search: no jump. Check on the cover and the inner screen. |
| 16.15 | With a hardware keyboard, type a letter on the home | Search opens with that letter in the field; the layout does not resize. |
| 16.16 | Open the app pop-up, tap "New group", name it, long-press an app and choose "Add to group" | The group folder appears in the folder row with the app; All, Work, Media, Social, Tools, Games (and Hidden when apps are hidden) filter the grid. Rename and delete the group from its menu. |
| 16.17 | Long-press an app in the pop-up and release without moving | A menu with Add to group, Add to home screen, Add to dock and App info. Add to home screen puts it on the current page's first free cell; Add to dock appends it (or replaces the last slot when full). |
| 16.18 | Long-press an app in the pop-up and drag onto a home page cell | The pop-up folds into a strip, a ghost icon follows the finger, the hovered cell is highlighted. Release on an empty cell: the app lands there and the pop-up closes. Release on an app: a folder with both. Release on a folder: added to it. |
| 16.19 | Drag an app from the pop-up near the left or right screen edge and hold | The pager flips to the next home page every 600 ms; release drops on that page. The Hub page is skipped. |
| 16.20 | Drag an app from the pop-up onto the dock | Empty slots show as dashed placeholders and the slot under the finger is highlighted; release inserts the app there (a full dock replaces the app under the finger); the pop-up closes; the dock keeps the change after a restart and is separate for the cover and inner screens. |
| 16.21 | Drag a pop-up app and release over nothing (for example the status bar) | Nothing is added and the pop-up opens up again. |
| 16.22 | Drag a dock icon to another slot, off the dock, and onto a home page | Reorders; removes it from the dock; moves it to the page (and removes it from the dock). Long-press a dock icon and release: a menu with Remove from dock and App info; the last dock app cannot be removed. |
| 16.23 | Long-press a home app tile and drag it past the touch slop | The popup closes and the tile is carried; drop on another cell moves it, on another app makes a folder, on the dock puts the app in the dock and removes the tile from the page. |
| 16.24 | Open Settings from the launcher | It opens on the sample cards (Screens, Home style, Look, Colors, Dock, App drawer, Widget picker, Icons) with a search icon in the top bar; tapping a card applies it and the preview updates; "More settings" lists Quick setup, Screens, Home, Dock, Hub, Look, Widgets, Gestures, Search, Hidden apps, Mail, AI, Backup, Accessibility, Features and About, and each opens. |
| 16.25 | Edit mode > Dock, and a result from the settings search | Open the matching Dock page or detailed page directly. |
| 16.26 | Settings > Colors | Twelve primary colors drawn as light / dark split swatches plus Theme colors and Custom. Pick Blue in dark mode: dark blue home. Switch Appearance to Light: the same Blue entry shows its light version (pale wallpaper, deeper accent); text stays readable in both. The wizard's Colors step shows the same tiles. |
| 16.27 | Theme sheet in edit mode | Lists the light and dark versions of the primary colors; the current one is highlighted. |

## 17. Fix round 1.3 scenarios (1.2.0)

Install 1.2.0 over 1.1.x (same signing key) and over a build that still had profiles. Both screens unless noted.

| # | Action | Expected |
|---|---|---|
| 17.1 | Open Settings, More settings, Features, Screens and the edit-mode bottom bar | No Profiles page or row, no "Active profile" row, no Profiles button; Hidden apps is a page of its own under More settings. |
| 17.2 | Check Settings > Apps > FoldOS > Permissions / special access | No "Alarms & reminders" request; the daily wallpaper still changes (inexact alarm). |
| 17.3 | Restore a backup written by 1.1.x that has a `profiles` list | Imports without an error; layout, settings and icon overrides are restored. |
| 17.4 | Settings > Home style, scroll to "Label size" | Three cards (Small, Medium, Large) each showing "Messages" and "Amazon Photos" at 11, 12 and 14 sp; the selected one has the accent border; tapping one changes the labels everywhere at once. With App labels off the card section says the size applies once labels show. |
| 17.5 | Home grid with "Amazon Photos", "Authenticator", "Google Play Store" at Medium on the cover (4 columns) | Names are centered, medium weight, readable on a bright wallpaper (soft shadow), wrap to two lines at word boundaries and never end in an ellipsis unless a second line also overflows. Icons in a row stay aligned whether a name takes one line or two. |
| 17.6 | Same names at Large, and in Easy mode | Larger text (14 sp, Easy mode +3 sp); a very long name wraps to two lines and only then shows an ellipsis. |
| 17.7 | Check the app pop-up and drawer, dock labels (Labeled preset), a folder (grid and list), the pair editor and the app long-press menu header | All use the same style and size; the dock label takes up to two lines; the menu title wraps to two lines. |
| 17.8 | System font size at the largest, then text scale 1.6x in Accessibility | App labels stop growing at about 1.15x; nothing overlaps. |
| 17.9 | Widgets from the edit bar: open an app with several widgets (for example Google Clock) | Detail view shows its widgets one per page with a real preview (a small preview stays small, never blown up); swiping changes the name, size and dots. |
| 17.10 | Add a built-in widget and an app widget from the new picker | Built-in lands at the first free cell; the app widget goes through the bind dialog and configure screen; cancelling configure removes the pending widget. Reopen the picker: the widget shows under Recently used when Recent grouping is on. |
| 17.11 | Back button in the picker on the cover (list, detail) and on the inner screen | Detail on the cover goes back to the list; otherwise it closes the picker. Tapping the dimmed backdrop closes it. |

## Reporting

For each failure record: section number, device state (cover / inner portrait / inner landscape /
Flex), build (`versionName` and debug or release), steps, expected vs actual, and `adb logcat`
excerpt. Keep a backup export from before the failure when the layout is involved.
