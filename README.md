# NCalendar

A minimal, Nothing OS-styled calendar app for Android, built with Kotlin and
Jetpack Compose. Reads and writes your device's real calendars (Google,
Outlook, local) via `CalendarContract`, with its own reminder notifications,
four home-screen widgets, and a dot-matrix aesthetic throughout.

## Features

- Month, week, day and agenda views with swipe navigation and smooth
  transition animations
- Reads/writes the system calendar (multi-account aware, with cross-account
  duplicate-event de-duplication); falls back to a local store when calendar
  permission isn't granted
- Recurring events, drag-to-reschedule, quick month/year picker
- Custom Nothing-styled date & time pickers (no stock Android dialogs)
- Its own reminder notifications — scheduled independently so other calendar
  apps sharing the same account don't also fire their own alerts
- Four home-screen widgets: next event, dot-matrix date, today's agenda, mini
  month
- Search by title with recent-search history
- Light / dark theme, Ndot (dot-matrix) display toggle, haptics

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
android/app/src/main/res/font/
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
[`ui/theme/Type.kt`](android/app/src/main/java/com/ncalendar/app/ui/theme/Type.kt)
at `SpaceGrotesk`/`SpaceMono` (or any other font you add) instead.

## Building

```bash
cd android
export JAVA_HOME="/path/to/your/jdk-17"   # Android Studio ships one under Android Studio/jbr
./gradlew assembleDebug                    # debug build
./gradlew assembleRelease                  # release build (needs signing, see below)
```

The debug build installs alongside a release install (`com.ncalendar.app.debug`
vs `com.ncalendar.app`) so you can keep both on one device. Debug builds run
without R8/Compose optimizations and will feel noticeably less smooth than
release — test performance on a release build, not debug.

### Signing a release build

Release builds are optional to sign — the build works unsigned if you skip
this. To produce an installable signed release:

1. Generate a keystore (see `android/keystore.properties.example` for the
   command).
2. Copy `android/keystore.properties.example` to `android/keystore.properties`
   and fill in your values. This file is gitignored — never commit it.

## Project layout

```
android/app/src/main/java/com/ncalendar/app/
  data/           EventRepository, CalendarProvider (system calendar), Room fallback
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
