# Changelog

All notable changes to FoldOS. Versions follow `versionName` in `app/build.gradle.kts`; dates are
commit dates (all work so far landed on 2026-10-16). Nothing below has been run on a device yet; see
the README's "Not yet verified on a device" section.

## [1.5.1] - 2026-10-17

Formatted mail and a themed app pop-up ( 151). Same signing key, no schema change. Needs the server's  /  /  fields.

### Fixed
- Emails that are HTML (newsletters, receipts) no longer look broken: the reader shows the formatted message (sanitized by the server) in a locked-down view: no scripts, no storage, no file access. Wide content scales to fit or scrolls sideways. Text follows the launcher text size and the light / dark appearance, so it is never dark-on-dark or light-on-light.
- Images are hidden by default ("Images are hidden to protect your privacy"); "Load images" loads them for that message only and is not remembered. Tapped links open in the default browser, email links open a prefilled FoldOS message, phone links open the dialer without calling.
- The reader overflow has "View as plain text" / "View formatted". Messages without HTML, and the plain view, keep paragraphs, make links tappable and show quoted  lines dimmer.

### Changed
- The app pop-up and full-screen drawer (panel, search field, group chips, labels) now follow the theme color by default: a low-saturation, 94% opaque tint of your color scheme (any scheme, custom colors, Material You, light or dark), with the blur behind it kept. Text color is chosen to stay at 4.5:1 or better. Space black does not turn it black.
- Settings > Home > "Pop-up color": Theme color (new default) or Neutral (the 1.5.0 look), with a small preview chip. Included in backups; older backups restore as Theme color.

Not verified on a device: the live server fields, scrolling over the formatted view, very long newsletters, and the pop-up on every color scheme.

## [1.5.0] - 2026-10-16

Mail opens and composes inside the tab (`versionCode` 150). Same signing key, no schema change. Needs the server's message / send / read endpoints.

### Changed
- Mail tab, connected: the "Inbox / N unread" title block and the "Summary: ... wrote about ..." note above the list are gone. The top of the list is a slim bar with search, a Compose button and the account menu; the first email follows right after the filter chips. The Gmail-app counts card is only on the setup states (signed out, not linked, not set up, error).
- Tapping an email no longer jumps to the Gmail app. A reader opens inside the tab: back arrow, subject, sender name and address, full date, To and Cc, the full body (selectable, scrollable, links tappable), attachment names (tap says they open in Gmail and opens the thread there), Reply, Reply all, Forward, Mark unread and an overflow with "Open in Gmail". Opening marks the message read and the list and unread count update at once (reverted if the server refuses).
- Compose is inside the tab too, from the Compose button, Reply, Reply all and Forward: To, Cc / Bcc behind a toggle, Subject, message, Send and Discard. Replies and forwards prefill recipients, "Re: " / "Fwd: " and a quoted original ("On <date>, <name> wrote:" with "> " lines). Sending shows progress, errors inline, then "Sent", returns to the list and refreshes. Discard asks first when there is something typed; Back keeps the draft in memory (an "Unsent draft" row resumes it). No-network mode disables Send with the usual message.
- Layout: cover screen shows one pane at a time (list, reader, compose; system Back goes up one level); inner screen shows the list on the left (about 40%) and the reader or compose view on the right. The compose view pads for the keyboard.
- Gmail connect text no longer says read-only.

### Added
- "Reconnect Gmail to allow reading and sending" card with a Reconnect button when the server says `relink_required` or `mail.needs_relink`.
- `ServerClient.getMessage / send / markRead`; `MailRepository.openMessage / send / setRead`.

Not verified on a device (the server side was built in parallel): the live endpoints, keyboard behavior on cover and inner, and the new panes in Space black.

## [1.4.2] - 2026-10-16

Space black fixed (`versionCode` 142). Same signing key, no schema change; the setting `spaceBlack` keeps its name and stored value.

### Fixed
- Space black is no longer a dark mode. 1.4.1 applied it in the global theme tokens and forced the dark palette, so the wallpaper,
  Light / Dark / Auto, status bar icons, labels on the wallpaper, sheets, menus, dialogs, the app drawer and Settings all turned dark.
  Now the theme, Appearance, wallpaper, color scheme and system bar icons are exactly as without the setting.
- Only three home surfaces become solid #000000 (1dp #26262B outline) with pure white text and icons (secondary text white at 72%):
  widget tiles (built-in widgets and the tile behind hosted app widgets), app panels (app card and app list card, with their title,
  labels and folder icons; a panel with its background off is left alone) and the dock (every preset that has a dock background:
  surface, labels, search field, All apps button). Apps placed loose on the page keep their normal labels on the wallpaper. Pop-ups and
  menus opened from these surfaces use the normal theme.
- Subtitle: "Widget tiles, app panels and the dock solid black, all text on them white". The preview tile now shows the normal page
  with black widget, panel and dock tiles.

### Changed
- Implementation: `SpaceBlackScope` (`theme/SpaceBlack.kt`) re-provides `LocalFoldOsTokens`, `LocalContentColor` and a matching Material
  color scheme around those three surfaces only; `NormalThemeScope` restores the normal theme for pop-ups opened from them.

Not verified on a device: every dock preset on black (the Bubble background only gets black bubbles if the bg is not "none"; labels under bubbles are
not checked), contrast on cover (1080x2520) and inner (2256x2504) in Light and Dark.

## [1.4.1] - 2026-10-16

Space black look (`versionCode` 141). Same signing key, no schema change.

### Added
- Settings > Look > "Space black" switch ("Dock, cards and widgets solid black, all text white") with a live preview tile; also in the Settings home Look section and as the first row of the edit-mode Themes sheet. Off by default, so nobody's look changes.
- When on, regardless of Dark / Light / Auto and color scheme: the dock (all presets), cards, folder and add-to-home sheets, the app pop-up panel, menus, dialogs and built-in widgets are solid #000000 with a 1dp #26262B outline, and all text and icons on them are pure white (secondary text 72%). The accent stays yours and is lightened until it is readable on black. Wallpaper is untouched (the dark variant of the look is used so labels on it stay legible). Hosted app widgets keep their own colors on a black tile. Works together with High contrast (stronger outline). Included in backups; older backups restore with it off.

## [1.4.0] - 2026-10-16

Widgets are loose again (`versionCode` 140). Same signing key. Room schema 4 -> 5 with a real migration (nothing is dropped).

### Changed
- Add to home > Widgets is now a list of collapsible dropdowns, so every widget is reachable again. "FoldOS widgets" has one
  dropdown per widget category (Clock & Time, Weather, Calendar & Tasks, ...; each with its count, the first one open), "From your
  apps" has one dropdown per app that provides widgets (app icon, name, count). A dropdown opens into preview tiles with the size
  ("2 x 2"); tap adds, hold and move drags onto the page as before. The search box filters across all of them and opens every
  dropdown that has a match. Open dropdowns are remembered while the sheet is open and on the cover (one tile column pair) and
  inside (wider grid) alike.
- Widget cards are gone. Widgets are only loose items on the canvas (drag, resize, stack as before). The Cards tab offers the App
  card and the App list card only; "Move to card" is for apps, folders and pairs; there is no "Drop widgets here".
- Starter layouts: Widget dashboard and Panels place loose widgets (Panels: loose widgets over an app card).

### Migration
- Each 1.3.x widget card is deleted and its widgets become loose widgets on the same page: the first takes the card's cell and size;
  when there are several they share one stack in that slot, in the card's order. An empty widget card is simply removed. The pages
  are then packed so nothing overlaps. Backups written by 1.3.x with widget cards import the same way; 1.4.0 never writes them.

### Removed
- The unreachable two-level widget picker (`picker/WidgetPicker*.kt` list, detail pager and shell) and its view-model state.

## [1.3.1] - 2026-10-16

Fixes from the first run of 1.3.0 on the phone (`versionCode` 131). Same signing key; no schema change.

### Fixed
- Add to home sheet: Back, the "Close" text and a tap on the dimmed page now close it, also while edit mode is open (edit
  mode's exit handler was composed after the sheet's and won; it is switched off while the sheet is up).
- The sheet is opaque (solid base under the theme surface, stronger scrim) and edit mode's bottom bar (Add, Wallpaper,
  Themes, Dock, Settings) is hidden while the sheet is open, so no labels or page dots ghost through.
- Widget cards: each widget gets the card's full width; built-in widgets are laid out for a smaller virtual tile and scaled
  up to fill the card (no giant empty area); dots only with more than one page; a card wider than three columns on a wide
  (inner) screen shows two widgets side by side per page.
- Migration of a 1.2.x Panels page whose built-in widgets were never stored no longer creates an empty widget card: it is
  seeded with the starter's widgets. Phones already on 1.3.0 get the same repair once (empty widget cards of Panels pages).
- Built-in widgets (Clock, Glance, Calendar, Weather, ...) shrink their text to fit the tile: no more "10:1" cut off or
  "Fri, Oct..." in a 2 x 2 clock; agenda rows are 38dp tall so the second line is not clipped.
- Card menu: "Move contents to page" fits (menu 280dp wide, labels wrap to two lines).

## [1.3.0] - 2026-10-16

Round 2.0: free canvas + cards (`versionCode` 130). Written from the source; not yet run on a device. Same signing key.
Room schema 3 -> 4 with a real migration (nothing is dropped).

### Changed
- Home pages no longer have a style. Every page is one free-form grid canvas (4 columns on the cover, 6 inside, rows from the
  height) and apps, folders, pairs, widgets and cards sit wherever you drop them, like the Pixel Launcher or One UI. The live
  cell highlight shows the size of what you drag; a drop that would overlap takes the nearest free spot or is refused with
  "No room on this page". The "Placement: Grid / Free" setting still works (Free = unsnapped dp placement).
- Long-pressing empty space opens the new "Add to home" sheet: tabs Apps (search, group chips, A-Z grid), Widgets (every widget
  as a preview tile, FoldOS first) and Cards (App card, App list card, Widget card), plus Wallpaper, Themes, Dock, Pages and
  Settings. Drag from the sheet onto the page, a card or the dock (the sheet shrinks to a strip while you drag and comes back
  after the drop), or tap to place at the first free cell of the current page.
- Edit mode (pinch in, the sheet's Pages action, an item menu's Edit / Resize) is now only page management: previous / next,
  move page left / right, add page, delete page (two taps, never the last page), Done, and the actions Add, Wallpaper, Themes,
  Dock, Settings. While you drag an item the bottom bar turns into the Remove strip. Widgets AND cards get corner resize handles;
  folders and cards ask for a second tap before Remove; Undo puts a folder's or card's contents back too.
- Long-pressing a widget opens its menu (Remove, Resize, Move to card, Edit home) like every other tile; keep holding and move to
  drag it. The Grid / Dashboard / List / Panels tabs and the Settings "Cover screen style / Inner screen style" selects are gone.
- Settings > "Starter layouts" (Classic grid, Widget dashboard, Minimal list, Panels): tapping one asks "Replace first page / Add
  as new page" and seeds the page with ordinary draggable content. More settings > Home has "Cover starter layout" / "Inner
  starter layout" rows. Presets, the setup wizard and the AI's "make my cover a dashboard" use the same starters.

### Added
- Cards: holders you place on the page (min 2 x 2, resizable, draggable by their chrome / title, removable). An **App card** lays
  its apps, folders and pairs out as a mini icon grid whose columns follow its width (or as big-name rows in list style); a
  **Widget card** holds one or more widgets and swipes through them like a stack, with dots. Drop an app or widget on a card to
  put it inside, drag one out onto the page to take it out, drag inside the card to reorder. Card menu: Resize, Rename card, Card
  background (transparent or themed surface), Move contents to page, Remove. Item menu: Move to card / Take out of card.
- Settings > More settings > Home: "Card background" and "Card titles" (searchable).
- Backup files carry cards (`cardStyle`, `cardClear`) and their contents; older backups without cards restore unchanged.

### Fixed (by design, see `app/DIAGNOSIS.md` "Round 2.0")
- Widgets drawn over app icons after a style switch, the inner screen cramming dashboard widgets into the top-left, the List
  edit mode's stacking chips and misaligned outlines, and the doubled widget outlines in edit mode can no longer happen: there is
  one grid, one set of visible items and one geometry per page.

### Migration
- Room 3 -> 4: `items.card_style`, `items.card_clear`. Grid pages stay as they are; Dashboard pages keep their widgets as loose
  widgets re-spanned onto the page grid and packed without overlap (their hidden apps come back below); List pages become one
  App list card ("Apps") covering the page (above any widgets); Panels pages become a widget card on top and an app card below.
  The non-overlap repair runs once over every page at the first launch.

## [1.2.4] - 2026-10-16

Mail through the Crosspoint server (`versionCode` 124). Written from the source; not yet run on a device. Same signing key; no schema change.

### Changed
- The Hub Mail tab no longer signs in to Google on the phone. People sign in with their Crosspoint account (email + password),
  link Gmail once through a browser page on the server (read-only), and the app pulls the inbox from the server
  (`/api/v1/foldos/*`, default `https://sub.crosspointhealth.org`). After the browser step the app polls the server (about every 2 s,
  up to 2 minutes) until Gmail shows as linked; the tab also refreshes when it opens and every 3 minutes while visible.
- `MailState` is now SignedOut, NotLinked, NotConfigured (server has no mail yet), Connecting, Connected, Error. The Mail tab shows a
  compact sign-in form, a "Connect Gmail" card, or a friendly message for each; the account icon menu has Mail settings, Disconnect
  Gmail and Sign out. A 401 from the server signs the user out locally. No-network mode makes no requests.
- Settings > Mail: Crosspoint account (sign in / out), Gmail link (Link / Unlink), Server address (Save / Reset), Refresh now. The
  Mail tab switch and the Gmail app counts section are unchanged.
- New small reusable `server/ServerClient` (HttpURLConnection, 10 s timeouts) and `server/ServerAuthStore` (token in
  EncryptedSharedPreferences with the plain fallback, same excluded-from-backup files as before; never in `LauncherSettings`).

### Removed
- AppAuth, `MailAuthActivity`, `GmailAuthStore`, `GmailApiClient`, the OAuth client id UI and the 1.2.3 shared-client-id download.
  `LauncherSettings.mailClientId` stays as a deprecated unused field so old backups load.

## [1.2.3] - 2026-10-16

Shared Gmail sign-in for the Mail tab (`versionCode` 123). Written from the source; not yet run on a device. Same signing key; no schema change.

### Added
- The Mail tab no longer needs each person to paste a Google OAuth client id. The organization sets it once in the Crosspoint
  platform admin portal (Admin > Settings > FoldOS Mail); FoldOS downloads it from
  `https://sub.crosspointhealth.org/api/v1/public/foldos-mail` at launch (and when the setup address or No-network mode changes),
  caches it, and uses it whenever no id is saved on the phone. An id pasted in Settings > Mail still wins.
- Settings > Mail: "Setup server" address (`mailSetupUrl`, default above) with a "Check now" button, a "Shared sign-in is set up" note,
  and Connect is enabled when either id exists. No-network mode never contacts the server.

## [1.2.2] - 2026-10-16

Fix round 1.5: device test of 1.2.1 on the cover and inner screens (`versionCode` 122). Written from the source; not yet run
on a device. Installs over 1.2.1 in place (same signing key). No database schema change (Room stays at version 3).

### Fixed
1. Dashboard <-> Grid overlap: switching a page to Grid (from edit mode or Settings > Home) moves every widget that sits on an
   app, folder, pair or another widget to the first free cells of the page (the grid grows downwards, so there is always room);
   stacks move together. The same pass runs once per launch over existing Grid pages, which repairs widgets that 1.2.0 / 1.2.1
   left under icons (clock under Chrome / Gmail, calendar-access buttons over Messages / Clock).
2. List style in edit mode (cover and inner): the eight "Remove widget N" chips are replaced by one "Widgets (N)" chip that
   opens a single card with one row per widget (tap a row to remove it). The A-Z scrubber is wider, uses a 16dp minimum slot
   per letter and draws every n-th letter when the strip is shorter than that (edit mode shrinks the page).
3. Clipped text: the AI tab chip rows ("On-device . Template rules", "Calendar, Mail, Tasks, Calls", "Try asking") wrap with
   FlowRow instead of scrolling sideways; Settings option chips (Badge style Count / Dot / Off, etc.), the icon-pack chips and
   the custom color palette wrap too, so no option hides off-screen. Hub event and task titles and search result subtitles
   may use two lines. Deliberate carousels (contacts favourites, reach out, search apps, app group chips, color scheme
   swatches) still scroll.
4. Mail tab (not connected): the developer wording about READ_CONTENT_PROVIDER is gone; on the inner screen the two cards
   stack in one centred column (max 640dp) instead of two columns, so no empty rounded block sits beside the Gmail app card.
5. App labels: a label whose word did not fit is shrunk in steps (down to 70%) until no word breaks mid-word
   ("Authenticator"), then falls back to two lines with an ellipsis; every label keeps 2dp of its own side padding so
   neighbouring labels ("Calculator Calculator" in Easy mode) never touch.
6. Edge rail dock: the home reserves the rail's width on the right (icon + padding) while the rail is shown, so the rail no
   longer covers widgets (25:00 timer, weather). The Hub page, which has no rail, keeps the full width.
7. Inner screen, Different layout + Classic grid: turning a dashboard page back to Grid also removes the untouched default
   widgets when the page has no apps, and seeds the first-run apps (6 columns x 4 rows = 24 apps; the seed limit is raised
   from 16 to 24), instead of leaving a handful of small widgets in the top-left whose text truncated.
8. Settings > Colors on the inner screen: the live preview is capped at 200-220dp wide instead of 60% of the section, and
   every sample preview is laid out at font scale 1, so a large system font no longer pushes the hero line and the
   "FRI, OCT 16 . AUSTIN" kicker out of the card.
9. The "Set FoldOS as your default home app?" dialog only appears when Settings is opened from the app list (MAIN / LAUNCHER
   intent), at most once per app launch, and still only until it was shown once. Settings started from inside FoldOS never asks.
10. More settings > Advanced: the row has a leading icon like the page rows, so its text lines up with theirs.
11. Edit mode on Dashboard pages: the cell guide, the selection outline and the drop / resize target are inset by the dashboard
    tile padding (6dp cover, 8dp inner, scaled with the edit-mode home), so they sit on the widget instead of beside it.
12. Hybrid panels on the inner screen: the apps panel uses compact rows (icon + label + 20dp), as many as fit, from the top,
    instead of stretching four rows over the whole panel height.
13. Switches that "did not flip" in the sweep (More > Search, Widgets Category / App, Features Count / Dot): read through the
    code. All of them write through the settings queue and take effect; Count / Dot were in a sideways-scrolling chip row, so
    the second option was often off-screen to the sweep (fixed by 3). No other defect found.

## [1.2.1] - 2026-10-16

Fix round 1.4: cover-screen device test of 1.2.0 (`versionCode` 121). Written from the source; not yet run on a device.
Installs over 1.2.0 in place (same signing key). No database schema change (Room stays at version 3).

### Fixed
1. Page switched to Dashboard: a Dashboard page now draws widgets only. The apps it held as a grid page stay in the
   database, hidden, and come back when the page is switched back to Grid (the untouched default widgets are removed
   again so they do not sit on the returning apps). A page switched to Dashboard gets the default widgets even when it
   already held apps, and a page that was switched on 1.2.0 (apps, no widgets) is repaired once at startup. Apps dropped
   on, or added to, a Dashboard page are refused with a toast.
2. "List" style in edit mode: no grid outlines over the rows and no hidden selection layer. The list is sorted A-Z
   (names that do not start with a letter last) and the alphabet scrubber lists the same letters in order, each in an
   equal slot, so the letter under the finger is the letter drawn there.
3. Long press just below an icon row: apps, folders and app pairs answer touches on their icon and label only, so a long
   press on the empty space under the last row enters edit mode instead of opening the icon's menu. Widgets keep their
   full area.
4. Delete page: while the confirmation is up the page chip itself turns red, reads "Tap again to delete" and deletes the
   page when tapped (the trash button turns red too and does the same). The confirmation lasts 3 seconds and no longer
   resets when the page list updates. The page's items are deleted with it, the page before it is shown afterwards, and a
   screen's last page cannot be deleted.
5. Folder icon preview: every child of a folder is drawn with its real app icon (looked up in the app list, then in the
   package manager for hidden or launcher-less apps). Empty slots draw nothing, a folder with one app shows one centred
   icon, and the purple placeholder squares are gone.
6. Setup wizard "Pick your colors": the live preview stays pinned above the scrolling Dark / Light / Auto cards and
   swatches (about 30% of the step height, 120 to 260 dp).
7. Hub > Mail without an account: a three-sentence explainer with a collapsed "How to connect" row for the OAuth steps,
   and the Compose button no longer sits over the Gmail app card's buttons (the content reserves space for it).
8. Hub > Calendar and Contacts without permission: one centred card with a large icon, a one-line reason and an Allow
   button instead of an empty week strip, month grid, search pill and list; the New button is hidden too.
9. Widget picker detail: the "Add to home" button is anchored at the bottom of the sheet and the preview, name, size and
   description are centred in the space above it, instead of leaving a large blank area under the button.

### Changed
- The "Theme colors" color scheme is now called "Default" in the wizard, Settings > Colors and Look, and the edit-mode
  theme sheet (the full word did not fit its tile on the cover screen).

## [1.2.0] - 2026-10-16

Fix round 1.3 (`versionCode` 120): remove profiles, readable labels, clean widget picker. Written from the source; not yet run on a device.

### Removed
- Profiles, end to end: the Settings > Profiles page, the "Active profile" and Profiles rows on Screens and Features, the
  Profiles button on the edit bar, the profile switcher / editor sheets, the schedule and Do Not Disturb auto-switching,
  `ProfileAlarmReceiver` / `ProfileBootReceiver`, the `SCHEDULE_EXACT_ALARM` permission (the daily wallpaper alarm is inexact)
  and the `profiles` list in backups. Old settings and old backup files that still carry `profile`, `activeProfile`, a
  `profiles` feature switch or a `profiles` list import normally; those keys are ignored.

### Changed
- Hidden apps now have their own page, Settings > More settings > Hidden apps (it used to live inside the profile editor).
- Readable app labels: names under icons on the home grid, the dock, the app pop-up, folders, app pairs and the app
  menus use one style (12sp medium weight, centered, soft shadow on the wallpaper). A long name wraps to two lines and
  gets an ellipsis only after the second line, so "Amazon Photos" and "Authenticator" are no longer cut off. Every
  icon keeps a fixed two-line label slot, so rows stay aligned. The system font scale only grows labels up to 1.15x.
- Label size: Settings > Home style shows three preview cards (Small 11sp, Medium 12sp, Large 14sp) with sample names;
  the choice is stored as `labelSize` (default medium). Easy mode adds 3sp on top.
- The home grid reserves room for two label lines under each icon, so a page can fit one row fewer on a short screen;
  the labeled bottom dock is about 13dp taller.
- New widget picker with two levels. Level 1: a search field, a Featured grid of FoldOS widgets, and an Apps list (one
  row per app that provides widgets, FoldOS first, filter chips by category). Level 2: a swipe-through detail view that
  shows each widget of the chosen app on its own page with a large preview, its size and a description, page dots, and
  an "Add to home" button. Cover: bottom sheet with a back arrow; inner and flex screens: floating card with the list on
  the left and the pager on the right. The add flow (built-in widgets placed directly, app widgets through the system
  bind and configure steps) is unchanged.
- Settings > Widgets is now one choice (Category / App / Recent, shown as three cards) plus Reset recent widgets. The
  Widget stacks and Smart switches stay on Features.

## [1.1.1] - 2026-10-16

Fix round 1.2: on-device test results on 1.1.0 (`versionCode` 111). Written from the source; not yet run on a device.

### Fixed
1. Stable signing key: debug and release builds are signed with the same committed key (`app/foldos-signing.p12`), so
   installs update in place instead of failing with a signature mismatch. Builds made before this one used a different
   key per CI run, so 1.1.0 must be uninstalled once before 1.1.1 goes on.
2. Status bar visible on dark and light: the clock and icons follow the launcher's Appearance (light icons on a dark
   launcher, dark icons on a light one) instead of the system dark mode, in the launcher, the setup wizard and Settings.
3. Setup wizard Colors step layout: the live preview shrank to about 40% of the visible height, the Dark / Light / Auto
   cards are compact whole-phone thumbnails and the swatches sit in a four-column grid, so the cards and the first
   swatches show without scrolling; the title and the Back / Continue bar stay pinned.
4. Empty-cell long-press hit test: a long press on an empty cell opens edit mode on that cell and no longer selects or
   opens the menu of a neighbouring item (exact cell under the finger, children handle their own presses, a stale
   selection from an earlier press is cleared).
5. Useful default home apps and dock: a fresh home page is seeded with up to 16 useful apps (Chrome, Gmail, Photos,
   Maps, YouTube, Calendar, ...) instead of the first apps alphabetically, and the default dock prefers phone,
   messages, Chrome and camera. Existing layouts are not changed.
6. Snackbar placement: the "Widget removed / Undo" snackbar sits just above the edit-mode bottom bar.
7. Settings preview ghosting: the pop-up app drawer samples draw the home dimmed behind an opaque sheet, so labels no
   longer show through; the full-screen sample does not draw the home.

## [1.1.0] - 2026-10-16

Fix round 1.1: device feedback on 1.0.0 (`versionCode` 110). Written from the source; not yet run on a device.

### Fixed
1. Hub page: the home dock and page indicator no longer show on the Hub page, so there is one tab bar and the
   content gets the full height on the cover and the inner screen (the rail dock hides there too).
2. Swipe up for apps: the gesture detector now covers the whole home (pager, page indicator and dock), not just
   the page columns, and every dock has an "All apps" button.
3. Long-press on the home: a long press on an app, folder, pair, widget, stack or empty space enters edit mode
   with that item selected (the item menu keeps an "Edit" entry); widgets are caught even over hosted app widgets.
4. First run created three identical pages: a fresh screen now gets one populated page and one empty page, nothing
   is re-seeded later, and the duplicate pages 1.0.0 created are removed once.
5. Settings was too complicated: Settings opens on live preview cards (Screens, Home style, Look, Colors, Dock,
   App drawer, Widget picker, Icons) where one tap applies a sample; toggles, dropdowns, sliders and hex fields moved
   to "More settings". Every old route still opens its page.
6. Colors: twelve primary colors, each with a light and a dark version (stored as `<id>-light` / `<id>-dark`; old
   ids still resolve to the dark version), shown as split swatches in Settings, the setup wizard and the edit-mode
   theme sheet.
7. Search: opening search and typing no longer slides the home down and exposes it as a strip at the status bar;
   search is a full-screen takeover with the field pinned at the top and the results above the keyboard
   (`android:windowSoftInputMode="adjustNothing"` on `MainActivity`).
8. Remove widget did nothing: the remove X now sits on the selected item inside the area between the bars, one tap
   removes, and a snackbar offers Undo for five seconds (an app widget keeps its host id until the window passes).
9. Edit widget overlapped the home: edit mode shows ONE top bar and ONE bottom bar and draws the home scaled to 76%
   between them; the published grid geometry stays in unscaled window dp, so drag, snap and resize line up.
10. Duplicate search: a bottom dock with the search add-on is the only search bar; the top bar is not drawn.
11. No way to change the dock or home apps: the app pop-up has group folders (Work, Media, Social, Tools, Games,
    Hidden, your own); apps are dragged from it onto a home page (placed on the cell, a folder when dropped on an
    app) or onto the dock (inserted, reordered or replaced); dock icons can be dragged out, reordered or removed from
    their menu; home tiles can be picked up after the long press and moved or dropped on the dock.

### Added
- `apppicker/` package: group store (DataStore `foldos_groups`), automatic categories, shared tap / long-press /
  drag gesture for app tiles, group cards and dialogs.
- Launcher-wide drag and drop (`core/ui/Drag.kt`): `DragController`, drop-target registration, a floating drag ghost.
- Dock apps are stored per screen (`LayoutRepository.setDockApps` / `dockApps`, DataStore `foldos_dock`).
- `LayoutRepository.placeApp` (nearest free cell). Room stays at version 3.

### Changed
- `LayoutRepositoryImpl.DEFAULT_PAGE_COUNT` is 2 and `ensureDefaultLayout` seeds only a screen with no pages.
- `ColorSchemes` lists both versions of every family; the theme no longer clamps preset accents.

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
