# Widgets +

A home-screen widget for people who keep the One UI or Pixel launcher as their home screen. One resizable
widget acts as a full page with five tabs along the bottom:

| Tab | Shows | Tap |
|---|---|---|
| Today | Greeting, event count, a "Next up" card and the events after it | Event opens in your calendar app |
| Calendar | The next 7 days of events (as many rows as the widget is tall) | Event opens in your calendar app |
| Contacts | Starred contacts with a Call button | Row opens the contact; Call opens the dialer with the number filled in |
| Mail | Shortcuts: open inbox, new email, Mail in FoldOS Hub | Opens the app |
| AI | Shortcuts: assistant, voice command, FoldOS AI, new calendar event | Opens the app |

Reads only: `READ_CALENDAR` and `READ_CONTACTS`. It never writes either and never places a call.

## Install and add

1. Install `WidgetsPlus-1.0.0-debug.apk` (see `releases/`).
2. Open **Widgets +**, tap *Allow calendar and contacts*.
3. Tap *Add widget to home screen*, or long-press the home screen, choose Widgets, find **Widgets +** and drag it
   onto a page. Stretch it to fill the page (it is built for 4 by 5 cells and up).

## Limits

- A widget cannot scroll or run a text field, so Mail and AI are shortcuts, not a reader or a chat. The full Mail and
  AI tabs live in **FoldOS Hub** (the `:app` module); when FoldOS is installed the Mail and AI tiles open its tabs.
- Mail has no unread count: that needs a Gmail sign in, which the widget does not do.
- Android refreshes widgets on a schedule (at most every 30 minutes) plus on tab taps, date changes and when you tap
  the widget title. Tap the title to refresh by hand.

## How it is built

Plain `RemoteViews` (no Compose or Glance), colours from the wallpaper (`@android:color/system_*`, light and dark),
no dependencies beyond `core-ktx`. `WidgetRenderer` fills a fixed set of rows in `res/layout/widget_page.xml` and hides
the rows that do not fit the widget's height. The module signs with the same committed key as `:app`
(`app/foldos-signing.p12`), so debug and release builds update each other in place.
