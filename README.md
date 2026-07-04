# NCalendar

A minimal, Nothing OS-styled calendar app for Android, built with Kotlin and
Jetpack Compose. Reads and writes your device's real calendars (Google,
Outlook, local), or runs fully offline if you'd rather it didn't — with its
own reminder notifications, four home-screen widgets, and a dot-matrix
aesthetic throughout.

## Features

- Month, week, day and agenda views with swipe navigation and smooth
  transition animations
- Reads/writes the system calendar (multi-account aware, with cross-account
  duplicate-event de-duplication)
- **Privacy opt-out / local-only mode** — skip calendar permission entirely
  from first run ("Use offline"), or sign out later from Manage calendars.
  Events are then stored only in the app's own on-device database; nothing
  is read from or written to any account
- Recurring events, drag-to-reschedule, quick month/year picker (swipeable
  year row)
- Custom Nothing-styled date & 12-hour time pickers (hour / minute / AM-PM
  wheels) — no stock Android dialogs anywhere in the app
- Its own reminder notifications, scheduled independently via AlarmManager so
  other calendar apps sharing the same account don't also fire their own
  alerts; reminders whose trigger time has already passed (e.g. an event
  created minutes before it starts) fire immediately instead of being
  silently dropped; snooze runs in its own alarm slot so a later sync can't
  cancel it
- Four home-screen widgets — next event (with live countdown, correctly
  shown for events already in progress), dot-matrix date, today's agenda,
  mini month — bitmap-rendered so they can use the real Nothing fonts, with
  picker previews that match the live widget design
- Search by title with recent-search history
- Light / dark theme, Ndot (dot-matrix) display toggle, haptics, a type scale
  shared across every screen (H1/H2/H3/body/label)

## Requirements

- Android Studio (or a JDK 17 + Android SDK toolchain) — this project targets
  `compileSdk 36`, `minSdk 26`
- An Android device or emulator running Android 8.0+

## Fonts — read before building

This app uses Nothing Technology's own typefaces (**Ndot** dot-matrix and
**NType82**) for the authentic look. These are Nothing's proprietary fonts,
so the `.otf` files are **not included in this repository** and the project
**will not compile** until you add them yourself:

```
app/src/main/res/font/
  ndot55_regular.otf
  ndot55caps_regular.otf
  ndot57_regular.otf
  ndot57caps_regular.otf
  ntype82_regular.otf
  ntype82_headline.otf
  ntype82_mono.otf
```

If you have legitimate access to these fonts, place them at the paths above.
(Space Grotesk and Space Mono, used as secondary typefaces, **are** included
— they're SIL Open Font License and free to redistribute.)

If you don't have access to the Nothing fonts and just want the app to
build, the simplest option is to point `NFonts` in
[`ui/theme/Type.kt`](app/src/main/java/com/ncalendar/app/ui/theme/Type.kt)
at `SpaceGrotesk`/`SpaceMono` (or any other font you add) instead.

**Known quirk:** the `ndot55caps_regular` font fails to render inside Android
widget *picker previews* specifically (falls back to a system sans there,
even though it renders fine everywhere else, including the live widgets).
Every `previewLayout` XML under `res/layout/widget_preview_*.xml` therefore
uses `ndot55_regular` instead — don't reintroduce the caps variant there.

## Building

```bash
export JAVA_HOME="/path/to/your/jdk-17"   # Android Studio ships one under Android Studio/jbr
./gradlew assembleDebug                    # debug build
./gradlew assembleRelease                  # release build (works unsigned; see below to sign)
```

The debug build installs alongside a release install (`com.ncalendar.app.debug`
vs `com.ncalendar.app`) so you can keep both on one device. Debug builds run
without R8/Compose optimizations and will feel noticeably less smooth than
release — test performance and UI feel on a release build, not debug.

### Signing a release build

Release builds are optional to sign — the build works unsigned if you skip
this. To produce an installable signed release:

1. Generate a keystore (see `keystore.properties.example` for the command).
2. Copy `keystore.properties.example` to `keystore.properties` and fill in
   your values. This file is gitignored — never commit it.

## Privacy

NCalendar can run in two modes, and you choose which on first launch (and can
switch anytime from Settings → Manage calendars):

- **Connected** — reads and writes your device's real calendars via
  `CalendarContract`. Nothing leaves the device either way; this just means
  the app talks to the OS calendar provider instead of its own database.
- **Local-only** — no calendar permission is requested at all. Every event
  you create lives in the app's private Room database, full stop.

## Project layout

```
app/src/main/java/com/ncalendar/app/
  data/           EventRepository (system calendar + local-only Room store), CalendarProvider
  notifications/  Own AlarmManager-based reminder scheduling
  widget/         Home-screen widgets (bitmap-rendered for custom fonts)
  ui/screens/     Compose screens (Month/Week/Day/Agenda, Editor, Search, Settings...)
  ui/components/  Shared building blocks (pickers, glyphs, labels)
  ui/theme/       Colors, type scale, fonts
  viewmodel/      CalendarViewModel — app state and business logic
```

## License

MIT — see [LICENSE](LICENSE). This covers the source code only; it does not
grant any rights to the Nothing typefaces described above.
