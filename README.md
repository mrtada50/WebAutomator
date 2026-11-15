# Web Automator

Native Kotlin Android app (no Flutter/React Native): build visual browser automations without code.
Open a site in the built-in WebView, tap elements to select them, save the automation once and let
the app run it repeatedly in the background and notify you with the extracted data.

## Features
- Integrated WebView browser (cookies, sessions, zoom, back/forward, load progress, URL validation).
- Visual selection engine (`assets/selector.js`): highlight on tap, stable CSS selector, XPath, text preview,
  tables, repeating lists, attributes. The app reads the selection by polling the page; the page never gets a
  native JavaScript interface.
- Action sequences: open URL, wait for load, wait for element, wait, click, extract text/number/attribute/table/list,
  navigate, repeat, save results, notify. Add, edit, duplicate, delete and reorder in the editor.
- Scheduler: interval, enable/disable, start/stop, run now, only with internet, daily limit, next estimated run.
- Background: WorkManager for 15+ minutes (re-scheduled after reboot), visible foreground service for shorter intervals,
  retries, duplicate-run protection, execution log.
- Notifications: every run, only on change, on condition, on failure/disappearance. Android 13+ permission handled.
- Comparison engine: previous/current, percentage difference, added/removed items, conditions
  (greater, less, equal, contains, not contains, changed).
- Results: history, changes, errors, CSV and JSON export. Arabic/English, RTL, light/dark theme.

## Honest limitations
- WebView is not headless. Each run creates a WebView inside the app process, bounded to 120 seconds, and destroys it.
  Android may delay or skip background runs (Doze, battery optimization, manufacturer limits). Exact timing is never guaranteed.
- Intervals under 15 minutes need the foreground mode with a persistent notification. After a reboot it must be resumed
  from a notification tap. On Android 15 the data-sync foreground type has a time limit.
- Sites needing CAPTCHA, extra login steps or blocking automated browsers will fail and be reported as "needs attention".
  No attempt is made to bypass them.

## Build with GitHub Actions
Push to `main`. The workflow checks out the repo, sets up JDK 17, the Android SDK (licenses accepted) and Gradle 8.9,
runs unit tests, builds the debug APK and uploads it as the `WebAutomator-debug-apk` artifact.
No Gradle wrapper or Android Studio is needed.

## Termux
```
pkg update && pkg install git unzip
cd ~ && unzip -o WebAutomator.zip && cd WebAutomator
git init && git branch -M main
git add -A && git commit -m "Web Automator"
git remote add origin https://github.com/USERNAME/REPO.git
git push -u origin main
```
