# Widgets +

A home-screen widget for people who keep the One UI or Pixel launcher as their home screen. One resizable
widget acts as a full page with five tabs along the bottom:

| Tab | Shows | Tap |
|---|---|---|
| Today | Greeting, event count, a "Next up" card and the events after it | Event opens in your calendar app |
| Calendar | The next 7 days of events (as many rows as the widget is tall) | Event opens in your calendar app |
| Contacts | Starred contacts with a Call button | Row opens the contact; Call opens the dialer with the number filled in |
| Mail | Your Hub inbox: unread count and the newest messages, plus a Write button | Opens Mail in FoldOS Hub |
| AI | Shortcuts: assistant, voice command, FoldOS AI, new calendar event | Opens the app |

Reads only: `READ_CALENDAR` and `READ_CONTACTS`. It never writes either and never places a call.

## Install and add

1. Install `FoldOS-1.7.2-debug.apk` and `WidgetsPlus-1.1.0-debug.apk` (see `releases/`). FoldOS is only needed for the Mail tab and the Hub shortcuts.
2. Open **Widgets +**, tap *Allow calendar and contacts*.
3. Tap *Add widget to home screen*, or long-press the home screen, choose Widgets, find **Widgets +** and drag it
   onto a page. Stretch it to fill the page (it is built for 4 by 5 cells and up).

## Limits

- A widget cannot scroll or run a text field, so Mail is a read only list and AI is shortcuts, not a reader or a chat.
  Reading, replying and the AI chat live in **FoldOS Hub** (the `:app` module); taps open the matching tab.
- Mail uses the server sign in you already made in FoldOS Hub > Mail. Widgets + has no login of its own: it asks
  FoldOS through `MailBridgeProvider` (a read only content provider protected by a signature permission, so only apps
  signed with this repository's key can use it). The Crosspoint token never leaves FoldOS. Without FoldOS installed,
  or before you sign in there, the Mail tab says what is missing.
- Android refreshes widgets on a schedule (at most every 30 minutes) plus on tab taps, date changes and when you tap
  the widget title. Tap the title to refresh by hand.

## How it is built

Plain `RemoteViews` (no Compose or Glance), colours from the wallpaper (`@android:color/system_*`, light and dark),
no dependencies beyond `core-ktx`. `WidgetRenderer` fills a fixed set of rows in `res/layout/widget_page.xml` and hides
the rows that do not fit the widget's height. The module signs with the same committed key as `:app`
(`app/foldos-signing.p12`), so debug and release builds update each other in place.
