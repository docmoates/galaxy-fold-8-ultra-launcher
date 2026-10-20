# Widgets +

A home-screen widget for people who keep the One UI or Pixel launcher as their home screen. One resizable widget acts as
a full page with five tabs along the bottom. **It is a complete app on its own: FoldOS does not need to be installed.**

| Tab | Shows | Tap |
|---|---|---|
| Today | Greeting, event count, a "Next up" card and the events after it | Event opens in your calendar app |
| Calendar | The next 7 days of events (as many rows as the widget is tall) | Event opens in your calendar app |
| Contacts | Starred contacts with a Call button | Row opens the contact; Call opens the dialer with the number filled in |
| Mail | Your inbox from the Crosspoint server: unread count, newest messages, a Write button | Message opens in Gmail; Write opens your mail app |
| AI | A text box, and quick questions: What's next, Plan my day, Summarize my mail | Opens the Ask AI sheet |

## Sign in (same account as FoldOS)

Open **Widgets +** and sign in with the same Crosspoint email and password you use in FoldOS. Widgets + signs in to the
server by itself (`ServerClient`, `ServerAuthStore` in `server/`), keeps its own token in encrypted storage that is
excluded from backups, and reads the inbox from the server. The one-time Gmail link is done from the same screen
("Link Gmail" opens the server's approval page). If you already linked Gmail through FoldOS, the server remembers it.
If the server cannot be reached, the widget shows the last inbox it loaded and says it is offline.

## Ask AI

Android widgets cannot hold a typing field, so the AI tab's text box opens the **Ask AI sheet**: a bottom sheet over the
home screen with a real text box and the keyboard already up. The AI is a rule-based engine on the phone (like FoldOS's
template engine), not a language model: it answers from your calendar, contacts and inbox
("what's next?", "plan my day", "what's on tomorrow?", "summarize my mail") and can start things that wait for your
tap ("call Sam", "text Priya", "find Dr Lee", "set a 10 minute timer", "set an alarm for 7 am", "new event dentist",
"directions to the airport", "write an email"). For anything else it offers a web search or your phone's assistant.
Nothing leaves the phone except the mail request to the server.

## Install and add

1. Install `WidgetsPlus-1.2.0-debug.apk` (see `releases/`).
2. Open **Widgets +**: sign in, tap *Allow calendar and contacts*, link Gmail if asked.
3. Tap *Add widget to home screen*, or long-press the home screen, choose Widgets, find **Widgets +** and drag it
   onto a page. Stretch it to fill the page (it is built for 4 by 5 cells and up).

## Limits

- A widget cannot scroll or take typed input, so Mail is a read only list and AI opens a sheet.
- Android refreshes widgets at most every 30 minutes, plus on tab taps, date changes and when you tap the widget
  title. Tap the title to refresh by hand. Opening the Mail tab asks the server, which can take a few seconds.
- Reading and replying to a message happens in Gmail; this app only lists the inbox.

## How it is built

Plain `RemoteViews` (no Compose or Glance), colours from the wallpaper (`@android:color/system_*`, light and dark).
Dependencies: `core-ktx` and `security-crypto`. `WidgetRenderer` fills a fixed set of rows in
`res/layout/widget_page.xml` and hides the rows that do not fit the widget's height. The module signs with the same
committed key as `:app` (`app/foldos-signing.p12`), so debug and release builds update each other in place.
