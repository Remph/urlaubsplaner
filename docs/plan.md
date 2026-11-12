# Plan: skeleton and milestones

Agreed with the maintainer on 2026-10-25. Scope is the MVP in `CLAUDE.md`; nothing beyond it unless the maintainer asks.

## Layout

```
settings.gradle.kts         includes :core always, :app unless -PcoreOnly
build.gradle.kts            Spotless only; no Android plugins (so -PcoreOnly never resolves them)
gradle/libs.versions.toml   the only place with versions
core/                       plain Kotlin/JVM, no Android imports: all rules
app/                        Android: Compose, Room, widget; depends on :core
data/school-holidays/       later: yearly ODbL file with the licence text, own directory
```

Planned packages in `:core` (`de.remph.urlaubsplaner.core`): `holidays` (Easter rule, Länder
tables), `balance` (entitlement, Resturlaub expiry, half days), `bridge` (Brückentage), `backup`
(versioned JSON format), `ics`.

Rules:

- `:core` takes a `java.time.Clock` wherever it needs "today". No `now()` in logic.
- `:app` holds screens, Room and glue. Every rule lives in `:core` and has a unit test there.
- Room schemas are exported to `app/schemas/` and committed; every change gets a migration test.
- Every visible text is a string resource in `values/` (German) and `values-en/` (English).

## Build settings

- minSdk 30 (the maintainer, 2026-10-25), compileSdk 37 and targetSdk 37. compileSdk 37 is not a choice:
  the current AndroidX releases require it. `minSdk` is set once, in `gradle/libs.versions.toml`.
- API level guard (0.9.1): `:core` is plain JVM, so lint's NewApi does not cover it, and two calls
  newer than Android 11 (`LocalDate.datesUntil`, `ByteArrayOutputStream.toString(Charset)`) only
  crashed on an emulator. `ApiLevelGuardTest` in `:core` disassembles the compiled classes with
  `javap` and checks every `java.*`, `javax.*` and `android.*` reference against the SDK's
  `api-versions.xml`; it runs with `test`. Its own tests compile a call to each of the two methods
  and expect the scan to flag it.
- Bytecode level 17, so any JDK from 17 up builds it. The cloud container has JDK 21. JDK 25 is
  not used: no statement found that the Android Gradle Plugin supports it (2026-10-25).
- Versions (latest stable on 2026-10-25): Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21, Compose BOM
  2026.09.00, activity-compose 1.13.0, JUnit 6.1.3, Spotless 8.10.4 (ktlint).
- Well-known, maintained libraries may be added when they make development easier; name them in
  the PR and ask first when unsure.
- Widget with Jetpack Glance 1.2.0 (AndroidX, Apache-2.0, no Google Play Services, fine for F-Droid;
  it brings WorkManager and DataStore). Details under "Home screen widget (M4)".

## Milestones

| # | Milestone | Contents | PRs (each with a review) |
| --- | --- | --- | --- |
| M0 | Skeleton | `LICENSE`, Gradle setup, `:core` with a smoke test, `:app` with an empty screen in German and English, Spotless, README, this plan | 1 |
| M1 | Rules in `:core` | Public holidays for all 16 Länder (Easter rule plus tables, regional cases noted); vacation balance (entitlement, Resturlaub expiry, half days); Brückentage | 3 |
| M2 | Data and storage | Room (settings, marked days, entitlement); school holiday file and loader (ODbL); backup export/import | 3 |
| M3 | Screens | Status home (remaining days, next trip), year and month view, marking single and half days, settings (Bundesland, language, regional holiday opt-ins and the Mariä Himmelfahrt opt-out for Bayern), Brückentage in the view | 2 to 3 |
| M4 | Export and widget | ICS export (with the ODbL credit), home screen widget; both follow the chosen app language | 2 |
| M5 | Polish | Animations, a final UI review of all screens and the widget, a tech debt review (the maintainer, 2026-10-26) | 2 to 3 |
| M6 | Release prep | About screen with credits, icon, F-Droid metadata, signing; the Google Play decision comes up here | 1 to 2 |

M1 comes first after M0 because it is testable in the cloud and everything else depends on it.

After 0.9.x: the features of 0.10.0 (parked items 18 to 23) are built on the integration branch
`release/0.10.0`; order, threads, the Room and backup version steps and the release steps are in
`docs/milestone-0.10.0.md`.

After 0.10.x: 0.11.0 (the visible features: legend, Kalender tab, portrait on phones, pager swipe,
backup reminder, range picker and drag to mark) is built on `release/0.11.0`, planned in
`docs/milestone-0.11.0.md`. 1.0.0 follows as the stable release; its candidates are listed there.

Language (the maintainer, 2026-10-25): the app language is a setting, not only the system language: System
(default), Deutsch, English. If the system language is neither, German applies (the default
resources). Plan: `androidx.appcompat` `AppCompatDelegate.setApplicationLocales()` (works back to
Android 11), `autoStoreLocales` so it is remembered below Android 13, and a `localeConfig` so
Android 13+ lists the app in the system's per-app language settings. To check when building:
whether the activity must extend `AppCompatActivity`, and how the widget and ICS export get the
chosen language below Android 13 (they do not run inside the activity).

Public holidays (M1, PR #5): `PublicHolidays.of(land, year, extras)` supports 2019 and later (earlier
years differ). Mariä Himmelfahrt counts for all of Bayern (the maintainer, 2026-10-25); Augsburger
Friedensfest and Fronleichnam in parts of Sachsen/Thüringen are opt-in `RegionalExtra`s. Berlin
one-offs (8 May 2020 and 2025, 17 June 2028) are in the table. Each year: check the Länder laws for
new or changed holidays and extend `HolidayRules`.

Vacation balance (M1, second piece): `VacationCalculator(clock, schedule, isPublicHoliday)` in
`core.balance`. Decided with the maintainer on 2026-10-25:

- Amounts are `VacationDays`, counted in half days. A marked day is a full or a half day
  (`DayMark`); it costs vacation only on a working day that is not a public holiday. Working
  weekdays are a setting (Monday to Friday by default).
- Heiligabend and Silvester are full working days by default. `WorkSchedule.specialDays` can set
  either (or any other date) to a half working day or a day off like a holiday. A half working day
  caps the cost at half a day, even for a full mark.
- Public holidays come in as a function `(LocalDate) -> Boolean`, so regional extras and the
  Bayern opt-out reach the balance without changing it.
- Resturlaub is typed in per year (the employer's figure), not computed. It is used first by days
  up to and including the expiry date (31 March by default, configurable) and lost the day after.
- Marked days up to and including today (from the `Clock`) are taken, later ones are planned.
  Remaining days can go negative.
- Not built: pro-rata entitlement in the first year, and the rule that Resturlaub only lapses when
  the employer has told the employee about it.

Brückentage (M1, third piece): `BridgeFinder(clock, schedule, isPublicHoliday)` in `core.bridge`.
Decided with the maintainer on 2026-10-25:

- A bridge is a whole gap of days that cost vacation between two free stretches (weekend, holiday,
  day off in the schedule, marked day) that costs at most 2 days by default (`maxCost`, half days
  counted). The joined stretch must hold a public holiday or special day off on a weekday the user
  works, so plain weekends and holidays on never-worked days give no bridge. Works with a 4-day week.
- Days already marked count as free time and cost nothing more; a half marked day still leaves
  half a day to take.
- `find(year, marks, maxCost)` lists the year's bridges by date (a bridge belongs to the year of its
  first day to take, the stretch may cross New Year); `best(year, 3, ...)` returns the three with
  the most days off per vacation day from tomorrow on.
- Use years after 2019 with `PublicHolidays`: a stretch at the start of January looks at the
  last days of the year before, which that class does not know for 2018.
- Not built: combining several gaps, ICS, screens.

Opt-out for Mariä Himmelfahrt in Bayern (the maintainer, 2026-10-25): the holiday is counted for all of
Bayern by default, but some communities do not have it, so there is a setting to switch it off.
It sits in the M3 settings next to the regional opt-ins. Built in `:core` (M1, last piece):
`RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN`, passed as `optOuts` next to `extras` to
`PublicHolidays.of` and `isHoliday`. It only affects Bayern (Saarland has the holiday everywhere)
and the default is unchanged. `VacationCalculator` and `BridgeFinder` needed no change; they read
the holidays through the function the caller builds, and a test runs both through it with the
opt-out. M3 stores the set like the extras and builds that function from Land, extras and opt-outs
for marked days, the balance and Brückentage alike. Not built: Room, screen.

Storage (M2, first piece): Room in `:app`, package `de.remph.urlaubsplaner.data`, database
`UrlaubsplanerDatabase` (version 1). Design approved by the maintainer on 2026-10-25:

- Three tables: `settings` (one row: Land code, working weekdays, regional extras, opt-outs, and the
  special days like Heiligabend/Silvester as `day:halves` text), `year_entitlement` (year, half
  days, Resturlaub, expiry day) and `marked_day` (ISO date, `FULL` or `HALF`). Entities hold plain
  text and numbers; `Mappers.kt` turns them into the `:core` types and skips a value it does not
  understand instead of crashing. The enum names are stored, so renaming one needs a migration (a
  test pins them).
- `UserSettings` (Land or null, extras, opt-outs, `WorkSchedule`) is in `:core.settings`, so the
  backup format can use it too. No Land is chosen until the user picks one; no default.
- `UserDataRepository` is what screens and the widget use: flows of `UserSettings`,
  `YearEntitlement?` and a date range of marks, plus the writes.
- The app language is not stored here: `AppCompatDelegate` remembers it (`autoStoreLocales`). The
  backup file leaves it out.
- Schemas go to `app/schemas/` (committed). `UrlaubsplanerDatabase.MIGRATIONS` has one entry per
  version step and a test fails if one is missing. No destructive fallback: it is the user's data.
- Tests run on the JVM with Robolectric (JUnit 4 in `:app`, Jupiter stays in `:core`). Robolectric
  uses Android 15 (`robolectric.properties`). In the cloud pass
  `-ProbolectricRepoUrl=https://maven-central.storage-download.googleapis.com/maven2`, because it
  downloads its Android image from Maven Central, which answers 429 there.
- The schema directory is a `debug` asset directory because Robolectric reads assets from the
  debug build; the release APK does not contain the schemas.
- Not built: screens, a place that creates the database
  (`UrlaubsplanerDatabase.create(context)` exists; M3 calls it from an `Application`).

School holidays (M2, second piece): design approved by the maintainer on 2026-10-25.

- The file is `data/school-holidays/school-holidays.txt` (plain text, one line per holiday:
  `land;from;to;name_de;name_en`), 2026 to 2029, all 16 Länder, exported from OpenHolidays and
  cross-checked with KMK on 2026-10-25. It sits with its ODbL text and a README (provenance, what
  was left out, yearly update steps, cross-check log) in `data/school-holidays/`, outside `app/`
  so `:core`'s tests read the same file the app ships. `tools/update-school-holidays.py` rebuilds
  it. No new library.
- Left out: Schleswig-Holstein's island dates, Mecklenburg-Vorpommern's vocational schools (the
  general schools are used).
- `:core.schoolholidays`: `SchoolHolidayData.parse(text)` (strict; a damaged line throws with the
  line number), `on(land, date)`, `between(land, start, end)`, `covers(...)`, `coverage`,
  `version` and `CREDIT` (the line for the about screen and the ICS export). Outside `coverage`
  the answer is "unknown", so screens check `covers` before they show "no school holidays".
- `:app` only reads the asset: `SchoolHolidayAsset.load(context)`. The whole `data/school-holidays`
  directory is an asset directory of `main`, so the licence text goes into the APK. M3 loads it
  once and keeps it, and shows the credit; M4 adds the credit to the ICS export.
- Not built: showing the holidays, the credit in the about screen or the ICS, the yearly renewal
  of the file (next due before 2027 ends; `covers` says when the file runs out).

Backup (M2, third piece): design approved by the maintainer on 2026-10-25, including "import replaces
everything".

- `:core.backup`: `BackupFile.encode/decode/writeTo/readFrom/fileName` and `BackupData` (settings,
  entitlements, marked days). One UTF-8 JSON file with `app`, `formatVersion` (1; 2 since 0.10.0, see "Granted days") and `exportedOn`.
  The file has its own stable names (a `when` per enum, so a new enum value does not compile until
  it has one) and counts amounts in half days. Not in it: the app language and the school holiday
  file. The format is documented in the `BackupFile` comment; a test reads a hand-written version 1
  file so a rename in the app cannot silently change it.
- Reading is strict and happens before anything is changed. `BackupException.NotABackup` (not JSON,
  not ours, over 2 MiB), `NewerVersion(found, supported)` (checked before the rest, so a newer
  structure still gets this message) and `Damaged(detail)` (a missing or wrong value, an unknown
  name, a duplicate year, date or special day, a value outside the rules). Nothing is skipped
  silently. The screens in M3 pick the text from the class.
- Import replaces everything: `UserDataRepository.replaceAll(data)` is one Room transaction (all or
  nothing); `readAll()` gives the current data. `BackupData.summary()` and `isEmpty` give the
  confirm dialog its numbers ("replaces 12 marked days and 2 years with ...") and tell it to skip
  the dialog on a fresh install. No schema change, so no migration.
- `:app` `BackupFileStore(contentResolver)` moves the bytes for a `Uri` from the system file picker
  (`CreateDocument` / `OpenDocument`, `BackupFile.MIME_TYPE`, no storage permission). Opening for
  write uses `"wt"` so a longer old file leaves no tail.
- New library: `kotlinx-serialization-json` 1.12.0 (JetBrains, Apache-2.0), tree API only, no
  compiler plugin. The about screen (M6) lists its licence.
- Not built: the picker launchers and the confirm dialog (M3 screens), merging, cloud sync.

Settings page (M3, first piece): design approved by the maintainer on 2026-10-25. M3 is three PRs: this one
(foundation, settings, backup), then home status with the year/month view and marking, then
Brückentage in both.

- `UrlaubsplanerApp` (an `Application`) holds the clock, the database, `UserDataRepository` and
  `BackupFileStore`. `MainActivity` is an `AppCompatActivity` (the in-app language needs it) and
  shows only the settings page until the home screen exists. The school holiday file is not loaded
  yet; the PR that shows school holidays loads it once in `UrlaubsplanerApp`.
- One scrolling page, no sub-pages: Bundesland; Urlaub of one year (stepper 2019 to next year,
  Urlaubstage, Resturlaub, Verfall day, Speichern); Arbeitstage (weekday chips, Heiligabend and
  Silvester as whole day, half day or day off); regional holidays (only what the Land has: the
  Bayern Mariä Himmelfahrt switch says "holiday" and stores the opposite, the opt-out); language;
  backup. On a fresh start only Bundesland, language and "restore backup" show (a new phone
  restores from a file before anything else).
- Everything except the year form saves at once, read-modify-write against the database under a
  lock. The year form saves with Speichern, because a new year starts with a suggestion.
- Suggestion for a year with nothing stored (the maintainer, 2026-10-25): the days and Verfall day of the
  year before. Resturlaub is 0, unless the new setting `UserSettings.suggestCarryOver` (off by
  default, the maintainer's request) is on: then it is what `EntitlementSuggestion` takes from the year
  before's `remaining` (state of today, never below 0). Never stored without Speichern.
- That setting is a column in `settings` (database version 2, migration 1 to 2 with a test) and a
  key in the backup file. The key is optional on reading (a file without it means off), so backups
  written before it still restore; `formatVersion` stays 1 because there was no release yet.
- Amounts are typed and shown with the language's decimal separator (`AmountText`: "12,5" in
  German, "12.5" in English, both accepted when typing, only half steps, 0 to 366). The Verfall
  day is typed as `TT.MM.`.
- Language: `AppCompatDelegate.setApplicationLocales`, remembered by `AppLocalesMetadataHolderService`
  below Android 13, listed by `locales_config.xml` on Android 13+. Not in the database or backup.
- Backup: `CreateDocument` with `BackupFile.fileName(today)` and `OpenDocument` for any file type
  (a hand-saved file does not always keep its type). Import reads and checks first, then, unless
  the app is empty, asks "Jetzt in der App: ... In der Datei: ... wird ersetzt". Errors: not a
  backup, newer version (both numbers), damaged (with the English detail), unreadable. Nothing
  changes in any of those cases.
- Tests: `:core` unit tests for `AmountText`, `EntitlementSuggestion`, `publicHolidayCheck` and the
  backup key; `:app` ViewModel tests (Room in memory, Robolectric), migration test, and Compose
  tests that compose the real page in German and English on the JVM. Not covered: the two file
  pickers, the keyboard, TalkBack and the language switch on a real phone.
- Not built: home status, year and month view, marking days, Brückentage, school holidays on a
  screen (the next PRs); an about screen with credits (M6).

Navigation and appearance (M3, second piece, "PR A"): the maintainer's changes after trying the settings
page, 2026-10-25. M3 is now PR 1 (merged as #13), PR A (this), PR B (Übersicht, Kalender, marking)
and PR C (Brückentage).

- `ui/AppRoot`: bottom navigation with three tabs, Übersicht, Kalender, Einstellungen (the two
  first are placeholders until PR B), and the one snackbar host. The app starts on Einstellungen.
- Settings are a short list (`SettingsListScreen`): one row per topic with what is set now as a
  second line. Bundesland and language answer in a small dialog; Urlaub of the year, Arbeitstage,
  Regionale Feiertage (only for a Land that has some), Darstellung and Datensicherung open a page
  of their own (`SettingsHost`; system back goes page to list). Same fields and the same
  `SettingsViewModel` as before, only split up; this also fixed the missing space between the
  weekday chips. "Wie das Telefon" is now "Systemsprache" / "System language".
- Darstellung (`appearance/`): Wie das System / Hell / Dunkel, a switch for pure black (OLED, only
  has an effect in the dark theme) and a switch for system colours (Material You, Android 12 and
  up, on by default; off or on Android 11 the app's own teal and gold colours). Kept in
  SharedPreferences (`AppearanceStore`), not in the database and not in the backup file, like the
  language. The light/dark choice goes through `AppCompatDelegate.setDefaultNightMode`, re-applied
  in `UrlaubsplanerApp.onCreate` because AppCompat does not remember it.
  Some phones (a test phone) already give Material You a nearly black dark background, which
  made "Dunkel" look like pure black. `appColorScheme` lifts such a palette to a soft dark grey
  (`liftedFromBlack`); pure black stays pure black.
- Rows with a tap effect (switch and choice rows) are clipped to the rounded `large` shape, so the
  ripple is no longer rectangular.
- Library: `material-icons-core` (back arrow and the tab icons; not the big extended set).
- Resturlaub that expires in a later year (the maintainer, 2026-10-25: "Urlaubsanspruch von 2026 verfällt am
  31.03.2028"): `YearEntitlement.carryOverExpiryYears` is 0 (the entitlement's own year, default) or
  1 (the year after); the Verfall day stays a `MonthDay`. `carryOverExpiryDate` is the real date and
  `VacationCalculator.balance` uses it. Room version 3 (`carryOverExpiryYears INTEGER NOT NULL
  DEFAULT 0`, migration 2 to 3 with a test), backup key `carryOverExpiryYears` (missing reads as 0,
  formatVersion stays 1), new-year suggestions copy it. Settings: one row with the Verfall field
  (TT.MM., label "Verfällt am") and two segmented buttons with the year (the maintainer chose this over a
  picker, 2026-10-25); the list summary shows the full date when it is a later year. Not
  built: marks in the following year do not use the earlier year's Resturlaub, the balance of
  one year only looks at its own marks, and the Resturlaub of the next year is still typed in.
- Tests: the settings screen tests now walk list and pages; `AppRootTest`, `AppearanceStoreTest`,
  `ThemeTest` (colour choice, pure black), `SummariesTest`. Screenshots of the real screens in
  light, dark and pure black are in `/mnt/project-files/design/0.9.0/pr-a/` (not in the repo).

Übersicht and Kalender (M3, third piece, "PR B"): designed with the maintainer in the mockup session,
2026-10-25 (`/mnt/project-files/design/0.9.0/mockups-r1/`). Built on top of PR A.

- `planner/PlannerViewModel` gives two `YearView`s: `overview` (always this year) and `calendar`
  (the year of the shown month). A `YearView` is everything one year shows, worked out once:
  public holidays by date, school holidays, marks, balance, trips, and what a change would
  leave (`remainingAfter`). The month and the Monat/Jahr mode live in the ViewModel, so they
  survive a tab change and rotation. The calendar stops at 2019 (first year with holiday rules)
  and next year.
- Rules in `:core`: `VacationCalculator.costlyDays(from, to)` (what a range marks: working days
  that are not holidays), `VacationCalculator.trips(marks)` (marked days joined when only free
  days lie between them) and `UserSettings.publicHolidays/nextPublicHoliday`. One holiday
  function (`publicHolidayCheck`) is still behind all of it.
- Übersicht: "Tage übrig" first, then taken, planned and entitlement (including Resturlaub), a
  warning card while Resturlaub is open before its Verfall day (`carryOverOpen`), next trip,
  next public holiday, and the year grid (12 rows, 31 columns; a tap opens that month).
  Without a Land or without the year's numbers it points to the settings instead of zeros.
- Kalender: month grid (Monday first) or the year grid. Gold = Feiertag, solid teal = taken,
  outlined teal = planned, lilac band = school holidays; half days carry a "½". Holiday and
  school colours are fixed (not from the wallpaper), taken follows the theme. Under the month the
  holidays and school holidays are listed by name, and a month outside the school holiday
  file says "noch keine Schulferien".
- Day sheet: whole or half day, "Bis" (since 0.11.0 an own month grid in a dialog, same year, range marks only days that cost
  vacation, so the weekend inside stays free), cost and days left afterwards before anything is
  stored, "Eintragen" or "Ändern", "Entfernen". Weekends and holidays only show what they are.
  Option C (tap start day then end day) is not built; the maintainer tries the debug APK first.
- School holiday file: `UrlaubsplanerApp.schoolHolidays` is read once (off the main thread, on
  the first view); `covers()` decides between "no school holidays" and "unknown".
- Settings: a suggestion made from the year that is still running now says so.
- Review fixes: the date is read again when the app comes back to the front
  (`PlannerViewModel.refreshToday`), because the app stays in memory for days; the year grid
  (Übersicht and Kalender year mode) says "noch keine Schulferien" like the month does and has
  one TalkBack action per month (its canvas is one touch area). The year grid rows are 20 dp
  (28 dp in the calendar), below 48 dp, by design: a tap opens the month and the month grid has
  the big targets.
- Tests: `:core` unit tests for the new rules; `:app` `YearViewTest`, `PlannerViewModelTest`
  (Robolectric, in-memory Room), `OverviewScreenTest` and `CalendarScreenTest` (German and
  English, compose the real screens), `RangePickerTest` (the "Bis" dialog). Not covered: TalkBack, real wallpaper
  colours. Not built: Brückentage on the screens (PR C).

### Brückentage (M3, PR C)

- Core: `Bridge.anchor` is the holiday (or Heiligabend/Silvester) a bridge belongs to, the nearest
  one that loses a working day; `isBeforeAnchor` tells "davor" from "danach". `BridgeGroup` and
  `groupedByAnchor()` put the (at most two) bridges around one anchor together.
- Overview card "Brückentage": the best 3 bridges still to come, one entry per holiday with a
  "davor" and a "danach" line. The card is hidden when there is none. Tapping a line opens a
  confirm sheet (leave days, days off, cost, days left) and only "Eintragen" stores the days.
  Someone who wants only part of a bridge marks those days in the day sheet; a stored mark is
  removed there too, so no bridge needs "declining".
- Calendar, month mode: possible bridge days that are not marked yet get a dashed outline, all
  upcoming ones (not only the best 3), plus a legend item that shows only when the month has one.
  Not in the year grid (rows are too small for an outline).
- `BridgeFinder` reads December of the year before, so for the first supported year (2019) the
  screens show no bridges.
- Not built: bridges that combine two gaps, bridges in the widget or the ICS export (M4).

### Home screen widget (M4)

Designed with the maintainer on 2026-10-26 (clickable mock-up `design/m4-widget-mockup/` in the project
folder); the logic is `:core.widget.WidgetStatus` (unit-tested), the rest is `:app` package `widget`.

- Two widgets in the launcher's picker (the maintainer, 2026-10-26, after the first build had one resizable
  widget with two layouts): "Urlaubsplaner (klein)" (2x1) and "Urlaubsplaner (groß)" (4x2 at least;
  both can be resized, the big one not below 4x2). Each draws its own layout (`WidgetLayout`, `SizeMode.Single`), so
  the text never overflows. Small shows the days left and one line ("Nächster Urlaub 14.04."); big adds
  the next trip as a range with its cost and "Genommen, Geplant, Anspruch" (Anspruch = entitlement
  plus Resturlaub), which the warning "3 Tage Resturlaub verfallen am 31.03." replaces while
  Resturlaub is about to expire (same rule as the overview card). Overbooked shows a negative
  number in the error colour. Receivers: `UrlaubsplanerWidgetReceiver` (small; the name stays, so
  a widget placed before the split keeps working and shows the small layout) and
  `UrlaubsplanerBigWidgetReceiver`; both share one setup screen, which finds the kind of widget by
  its provider (`widgetFor`). The big layout was found to need about 280 x 120 dp (M5 UI review);
  the big widget declares 250 x 110 dp (4 x 2 cells before Android 12) and draws the big layout
  whatever the cell gives, so a launcher with a smaller 4x2 cell would clip it; the maintainer's phone fits.
  Picker previews (`widget_preview_small/big.png` in `res/drawable-nodpi`, English in
  `drawable-en-nodpi`) are renders of the real layouts with sample data (17 days left, light, the
  app's own colours), made with a throwaway Robolectric harness that is not in the repo; redo them
  when a layout changes. A launcher without `previewImage` support shows the app icon.
- A missing Bundesland or missing days for this year show a prompt instead of numbers; a tap
  opens the Einstellungen tab, every other tap the Übersicht. `MainActivity` is `singleTask` and
  takes the tab as the intent action (`TabRequest`), at a real start and in `onNewIntent`.
- Set up per widget (`WidgetConfigActivity`, when placed, and by long press from Android 12):
  background transparency 0 to 100 % in steps of 5 (default 0, solid) and colours Wie das Telefon
  (default), Hell, Dunkel. Stored in Glance's preferences per widget (`WidgetStyle`); a new
  option is a new key with a default.
- Colours follow the phone's light/dark mode, not "Darstellung" in the app: wallpaper colours on
  Android 12+, the app's teal scheme on Android 11. Day/night are both in each `ColorProvider`,
  so the launcher switches them. Android 11 cannot round a Glance view, so a tinted shape drawable
  is the background there.
- Texts: string resources in `values/` (German) and `values-en/`, in the app language. The widget
  runs outside an activity, so `WidgetLanguage` builds a configuration context; the choice is read
  from `LocaleManager` (Android 13+) or from a copy `LanguageChoice.apply` stores (below 13).
  The first German or English language in the phone's list decides, else German (the maintainer, 2026-10-26; the same rule as the screens and the ICS file).
- Refresh: while the app process lives, `WidgetRefresher` watches the three tables (debounced
  500 ms) and redraws; the app also redraws after a language change and a configuration change.
  Glance keeps a session for about a minute and then only refreshes the widget's state, not its data, so each redraw also raises `WidgetRefresher.revision` and `liveModel` reads the data again (found in the review).
  The date changes through `DATE_CHANGED`, `TIME_SET`, `TIMEZONE_CHANGED` in the receiver and
  `updatePeriodMillis` 30 minutes as the safety net.
- Not tried on a launcher (the cloud has none): placing, the setup screen, transparency, light and
  dark, the language switch, the tap targets, the redraw after marking a day, whether Android
  delivers the date broadcasts, how the small layout fits a 2x1 cell on a real phone. The picker preview
  images are renders, not a look at a real launcher.

### Design review of the screens (after M3, 2026-10-25)

The maintainer answered each question in the thread; research (Material 3, One UI, Apple), screenshots and a
clickable mock-up are in the project folder `design/review-m3/` and `design/review-m3-mockup/`.

- Shapes: three steps from the Material 3 scale, read from `MaterialTheme.shapes`, no own radii:
  `large` 16 dp for cards, fields and the month's day cells; `extraSmall` 4 dp for legend and list
  swatches and the year grid's tiles; pills (full) for buttons and chips. The 2 dp "today" bar
  and school holiday bar inside a cell stay as they are.
- Month view: under the grid a card with the days taken (up to today) and planned (after it) in
  that month, then the month's holidays and school holidays; the legend is one small row.
- Year grid: 28 dp rows; swipe down expands them to what fits the screen without scrolling, at
  least 36 dp and at most 64 dp (the maintainer, 2026-10-27; `expandedRowHeight`, measured from the
  page's other parts, so it follows the font size), swipe up makes them compact (like the
  stock calendar app of some phones). A handle under the grid does the same by tap and for TalkBack. The swipe is
  only watched, so the page still scrolls: on a short screen a swipe up scrolls first and makes the
  rows compact only when the page is already at its end. The state lives in `PlannerViewModel`.
- The year view's legend is hidden while the rows are expanded (0.11.0, the maintainer's idea
  2026-11-10): it fades and shrinks with the row animation (`FadingLegend`, driven by an
  `animateFloatAsState` that follows `rowsExpanded`) and the rows grow into its room. The fit takes
  the legend's current height off the measured "rest of the page", so the target of the row
  animation stays the same while the legend leaves: no second jump. The month view keeps its legend,
  and the "school holidays unknown" note stays. The gap above the legend sits inside it, so
  no stray gap is left at the end.
- A second tap on the open Kalender tab goes back to today (0.11.0, parked 27): the month view to
  the current month, the year view to the current year, the mode stays (`PlannerViewModel.showToday`).
  TalkBack hears "Zum aktuellen Monat" / "Zum aktuellen Jahr" as the tab's click label. A second
  tap on the open Einstellungen tab with a settings page open goes back to the settings list, like
  the back button (0.11.1: `AppRoot` counts the taps, `SettingsHost` calls `leave()`); on the list
  itself and on Übersicht it does nothing.
- Portrait on phones (0.11.0, parked 25): `OrientationPolicy` sets `requestedOrientation` to portrait
  when the smallest width is under 600 dp and leaves it free from 600 dp (tablets). It runs as a
  lifecycle callback (`onActivityPreCreated`, before the activity's `onCreate`) for every
  activity, so the main screen and the widget setup screen are covered and a phone held sideways does
  not build a landscape screen first. The manifest cannot do this, because it has one value for all
  devices. The check is made at the start of an activity. Unfolding a foldable changes the
  smallest width, and the manifest has no `configChanges` for it, so Android recreates the activity
  and the choice is made again (reasoned from the manifest, not tried on a device).
- Swipe sideways in the calendar (the maintainer, 2026-10-26): left shows the next month (month
  view) or year (year view), right the previous one. It does not switch between month and year view
  (the segmented button does that). The arrow buttons stay for TalkBack.
- Pager (0.11.0, parked 28; before that, 0.10.0 slid the page after the finger was released with
  `AnimatedContent`, `CalendarPage` and `Modifier.swipeToStep`, all gone now): month and year are
  one `HorizontalPager` (made again when the mode changes, `CalendarPager.kt`), so a sideways drag
  moves the page with the finger and snaps. The next page is drawn only while a finger drags (no
  peeking). The ViewModel stays the truth for month and mode: `PagerFollowsViewModel` scrolls the
  pager when the arrows or "today" change the month and sets the month when a swipe has settled
  (with the tick; the arrows have none). Every page reads the view of its own year
  (`PlannerViewModel.calendarView(year)`), and the ViewModel reads the years next to the page in
  view ahead, so a page dragged in from December to January is not blank. The header stays and the
  title moves with the pager; each page scrolls on its own. A swipe past the first or last month
  stretches the page and gives the "no" tick. With animations off in the system settings the snap
  is a jump. The expanded year rows use the room the frame and the rounding leave (PR #63).
- Weekend numbers: the theme's soft grey is kept where it has 4.5:1 on the normal and the school
  holiday tile, else a fixed stronger grey (dark 5.3:1, light 6.4:1 on school tiles).
  `weekendTextColor` and `contrastRatio` are unit-tested.

### ICS export (M4, first piece)

Designed with the maintainer on 2026-10-26 (card taps, one question at a time).

- Content: only the vacation (the maintainer: "Vacation only"). No public or school holidays, so the file
  needs no ODbL credit; the credit still goes into the M6 about screen. One all-day event per
  trip (`VacationCalculator.trips`: a weekend between two marked weeks stays inside it), taken and
  planned days alike. Summary "Urlaub" / "Vacation", notes "4,5 Urlaubstage, davon 1 halber Tag"
  / "4.5 vacation days, 1 of them a half day". All trips of all years; no year choice.
- `:core.ics`: `IcsExport(clock, calculator).encode(marks, language)` (RFC 5545: CRLF, lines folded
  at 75 octets without cutting a character, values escaped), `tripCount`, `fileName`,
  `MIME_TYPE`; `IcsLanguage.of(locale)` is English for an English locale and German for every
  other (the app's own rule). The UID is `urlaubsplaner-<first day>@de.remph.urlaubsplaner`, so
  importing a newer file again updates the event in most calendar apps. DTSTAMP comes from the
  clock. The end date is exclusive, as RFC 5545 wants for all-day events.
- `:app`: page "Kalender-Export" in Einstellungen (only with a Land chosen; without a Land what a
  marked day costs is not known). It says how many vacations the file holds and has two buttons,
  "In Kalender öffnen" (`ACTION_VIEW`, `text/calendar`, a cache file read through a
  `FileProvider`, only the newest export is kept) and "Als Datei speichern" (`CreateDocument`, no
  storage permission, like the backup). Both are off with no vacation. The file's language is the
  first one in the system list that the app has texts for (`IcsLanguage.of(List<Locale>)`).
- No share sheet (the maintainer, 2026-10-26): Some phone makers' calendar apps are not offered there, it only takes a
  `.ics` file through "open". The first version had "Senden oder in Kalender öffnen" with
  `ACTION_SEND`; the maintainer tried it and chose to turn it into "open".
- Marks from before 2019 (only from a hand-made backup) are left out: the holiday rules start
  in 2019, so what they cost is not known.
- Tried by the maintainer on a phone: saving the file and opening it works. Not run: the new
  "In Kalender öffnen" button on a phone, and Google Calendar.

### Instrumented tests (0.9.2)

`app/src/androidTest` holds the first tests that run on a device (`connectedDebugAndroidTest`,
AndroidJUnit4, Compose test rule). They cover what the JVM tests cannot see: the real SQLite, a
real `ContentResolver`, the system's widget service and the Android 11 library. Written in the
cloud, which has no emulator: the cloud only compiles them (`assembleDebugAndroidTest`). First run
on the maintainer's PC, 2026-10-28: 14 of 14 pass on the API 30 and the API 37 emulator, after two
fixes in the test setup: Android 11 only has the old `appwidget` shell program (not `cmd appwidget`),
so `PlacedWidget` runs both for granting the widget bind permission; and Compose's test library
brings Espresso 3.5.0, which breaks on Android 17, so `espresso-core` 3.7.0 is a test dependency.

| Class | What it checks |
| --- | --- |
| `widget/WidgetOnDeviceTest` | Both widgets bound on a launcher of the test's own (`PlacedWidget`: `AppWidgetHost` + `AppWidgetHostView`) show the Land prompt, then follow entering days, a second change soon after, marking, unmarking, and a restored backup. This is the test for parked item 7 (widget stale after clear data + restore); it cannot clear the app's own data, so it covers the restore, not the process death. |
| `data/BackupOnDeviceTest` | Export and import through a MediaStore file (Downloads) and the real `SettingsViewModel`: round trip, the question before replacing, no tail of a longer old file ("wt"), a file that is not a backup. |
| `data/MigrationOnDeviceTest` | The Room migrations on the device's SQLite, with the committed schemas as test assets. |
| `planner/MarkingOnDeviceTest` | The real `MainActivity`: choosing a Bundesland in the settings; marking a day in the calendar and removing the mark. |
| `data/AllLaenderOnDeviceTest` | Every Bundesland through holidays, bridges, school holidays, ICS and backup on the device's Java (Android 11 for the lowest level). |

Rules for these tests: they work on the app's real database, so `FreshApp` empties it before and
after and refuses to run anywhere but on an emulator (`-Pandroid.testInstrumentationRunnerArguments.allowRealDevice=true`
overrides it, never on a phone with real data). Texts come from the app's string resources, not
literals, so the language of the emulator does not matter. Test names in backticks must not contain
commas or dots: dex cannot represent them. Not covered: the system file picker, the setup screen of
the widget, a real launcher's resizing, and TalkBack.

### About screen (M6, first piece)

Last row of Einstellungen ("Über die App" / "About"), one page: app name and version (from the
manifest), one sentence that the data stays on the phone (the app declares no INTERNET
permission), "© 2026 Remph" with the no-warranty sentence the GPL asks for, links to the source
(`SOURCE_URL`) and to the maintainer's website (`WEBSITE_URL`, both open the browser), the GPL-3.0 licence, the
OpenHolidays credit (`SchoolHolidayData.CREDIT`, English as the licence asks), a line on the
libraries and three licence texts shown in a dialog. The texts live in `app/src/main/assets/licenses/`
(GPL-3.0, Apache-2.0, BSD-3-Clause for the Protocol Buffers copy inside Glance), so they travel
with the APK. Dependency audit 2026-10-26 (`releaseRuntimeClasspath`): AndroidX, Kotlin, kotlinx and
jspecify are Apache-2.0; only `glance-appwidget-external-protobuf` is BSD-3-Clause. Add a text when a
dependency with another licence comes in. Screenshots: project folder `design/m6-about/`.

### Granted days (0.10.0, parked item 21)

A planned (future) day can be marked "genehmigt" / "granted": the employer has said yes. Decided
with the maintainer on 2026-10-27; the look was decided on 2026-10-26 (tinted fill + outline).

- **A setting, off by default** (Einstellungen > Urlaub, "Genehmigte Urlaubstage"; first chosen
  "always on", changed to a setting the same day). Off hides everything granted (day sheet switch,
  tiles, legend, overview line) and the ICS export is as in 0.9.x; the flags stay stored, so
  turning it on again brings them back. Changing a day's mark (full to half) with the setting off
  keeps its flag (`UserDataRepository.markAllKeepingGranted`).
- **Rules in `:core`:** `MarkedDays(marks, granted)` and `MarkStatus` (TAKEN, GRANTED, PLANNED).
  A day up to and including today is TAKEN whatever its flag; a granted flag only matters for
  future days. The balance is unchanged: granted counts as planned (`VacationCalculator` takes only
  the marks). `grantedPlanned(year, days, today)` is the granted share of "Geplant" for the overview.
- **Room 3 to 4:** `marked_day.granted INTEGER NOT NULL DEFAULT 0` and `settings.grantedDays
  INTEGER NOT NULL DEFAULT 0` (`MIGRATION_3_4`, `4.json`, migration test). Existing marks are
  planned, the setting is off.
- **Backup format 1 to 2:** every marked day has `"granted": true|false` (required in version 2,
  absent in version 1 = not granted); the settings have the optional `grantedDays` (missing = off).
  A version 1 file still restores; a version 3 file is refused. 0.9.x refuses a version 2 file
  ("newer version") instead of dropping the flags, so a 0.10.0 backup cannot be restored in 0.9.x.
- **Day sheet:** a "Genehmigt" switch (only for days after today, only with the setting on). It
  starts as the day's flag (off for a new mark) and applies to every day the sheet saves, so a
  "Bis" range is granted or planned together. Brückentage "Eintragen" marks as planned.
- **Look:** `DayColors.granted` (`primaryContainer`; in the light theme mixed 20 % towards `primary` so it stands out from a plain tile, Nick 2026-10-27), `onGranted`, `grantedHalf` (the "½" keeps
  the planned colour while it has 4.5:1 on the fill, else `onGranted`); 2 dp `primary` outline in
  the month, 1.5 dp in the year grid, no check or dot; legend entry "Genehmigt"; TalkBack says
  "Urlaub genehmigt" / "Halber Urlaubstag genehmigt". Tests in `DayColorsTest` (contrast, differs
  from the school and plain tile and from taken, blue wallpaper schemes).
- **Overview:** one line "Davon genehmigt: N Tage" under the figures, only with the setting on and
  at least one granted day to come.
- **ICS:** with the setting on, an event is `STATUS:CONFIRMED` (taken or granted days) or
  `STATUS:TENTATIVE` (planned days); a trip splits into one event per run of the same status, and a
  weekend between two runs belongs to neither. With it off: one event per trip and no status.
- **For the 0.10.0 changelog (release thread):** new optional setting "Genehmigte Urlaubstage"
  (shows which planned days the employer has granted, also in the calendar export); backups made
  with 0.10.0 cannot be restored in 0.9.x.

### Next holiday highlight (0.10.0, parked item 20)

On the overview "Nächster Feiertag" is a button (the whole block, chevron at the end, click label
"Feiertag im Kalender zeigen"). A tap opens the calendar in the month view on the holiday's month
(`PlannerViewModel.showDate`) and its tile plays the highlight once: a ring grows out of the tile
and fades, twice, and the tile swells by 10 % (`Highlight.kt`, drawn by `DayCell`). The maintainer
chose it on 2026-10-27 over glow, bounce, flash and spotlight (mock-ups in the project folder
`design/0-10-holiday-highlight/`). The ring is drawn outside the tile, so it reads on gold, lilac,
taken and granted tiles, in light, dark and OLED. It starts 300 ms after the calendar opens (the tab
slide) and takes 1.45 s. With Android's animations off the tile shows a still outline for 1.5 s.
The highlight is consumed when it has played, when another month is shown and when the calendar
leaves the screen, so a later visit does not light up an old day. No stored data changes.

### Clear a year (0.10.0, parked item 18)

Einstellungen > Urlaub, last row of the year page: "Urlaubstage von 2027 löschen". It removes only
the marked days of that year (`MarkedDayDao.deleteBetween`, `UserDataRepository.clearMarks`); the
year's Urlaubstage, the Resturlaub and the settings stay. A dialog names the year and the number of
days, says there is no undo and points to Datensicherung. The widgets redraw through Room's
invalidation tracker. No stored data changes.

### 0.10.0 release

Version 0.10.0, `versionCode` 1000 (0 * 10000 + 10 * 100 + 0). 0.9.2 (902, the instrumented tests
only) was never released; the maintainer skipped it on 2026-10-27 and its changelog files stay in
`fastlane/`. For the changelog texts (`changelogs/1000.txt`): new optional setting, clear a year,
holiday highlight, slide animation, year rows that fit, lilac school holidays; a backup made with
0.10.0 (format 2) cannot be restored in 0.9.x, and the database moves from Room 3 to 4 on the first
start, with no way back. The release steps and the test list are in `docs/milestone-0.10.0.md`.

### 0.10.1 (performance and feel)

Version 0.10.1, `versionCode` 1001, one PR into `main` (`feature/0-10-1-perf-feel`), from the
performance and feel review of 0.10.0 (2026-10-31). Fixes only, no stored data changes:

- Messages (snackbars) run in `AppRoot`'s scope: a settings notice is marked handled when it is
  handed over, so it stays when the tab is left and does not show again on return.
- The day sheet and the bridge sheet close through `sheetState.hide()` (`SheetClose`), so every
  button slides the sheet down like a swipe does; while it slides it keeps showing what was tapped.
- The overview's start animation is read inside `graphicsLayer {}` and small count-up texts, so
  the status no longer composes in every frame of the intro (a test counts it).
- `android:windowBackground` is the app's light or dark background (`values`, `values-night`), so
  the splash and the first frames are not AppCompat grey.
- The day tile's press ripple is drawn on the rounded tile; the touch area stays the whole cell.
- Haptic ticks through `LocalHapticFeedback` (follows Android's touch feedback setting): Confirm
  after Eintragen/Ändern/Entfernen in the day sheet and Eintragen in the bridge sheet, SegmentTick
  when a swipe steps the month or year, Reject when a swipe hits the end of the calendar. The arrow
  buttons do not tick. Below Android 14 androidx.core maps SegmentTick to a context click.
- Undo after Entfernen only (the maintainer, 2026-10-31): `UserDataRepository.removeAll` returns
  what it removed, `PlannerViewModel.removed` hands it to `AppRoot`, whose snackbar ("3 Tage
  entfernt", Rückgängig) calls `restore` with each day's mark and granted flag. Marking says nothing.

Left for later: R8 (1.0.0), an app baseline profile and holding the splash until the data is read
(both need a measurement on the PC).

Known snags:

- School holiday data: openholidaysapi.org and kmk.org were reachable from the cloud on
  2026-10-25 (the maintainer added them); the Länder ministry sites are not (probe before relying on it).
- Instrumented tests (`app/src/androidTest`) need an emulator; see "Instrumented tests (0.9.2)" below. The cloud can only compile them (`assembleDebugAndroidTest`).
- The launcher icon exists since M5 (sun over day tiles, adaptive with a themed monochrome layer; mock-ups in the project folder `design/icon/`).

### Drag to mark a range (0.11.0, S2)

In the month view a long press on a day that costs vacation, then a drag across the following
days, marks a range the same way as "Bis": letting go opens the day sheet with the range filled
in (start as the title, "Bis" set, cost shown), nothing is marked before the sheet's button. The
gesture, its maths and the cost bubble are in `planner/DragToMark.kt`, the month grid uses them
(`DayCells.kt`); the days under the finger look as in the "Bis" dialog (`RangePicker.kt`: one set of
tile colours for both). The range stops at the month's first and last day (no paging while
dragging), a backwards drag makes the earlier day the start, and the run is cut to its first and
last day that costs vacation before the sheet opens. A long press on a weekend or a holiday gives
one "no" tick and opens nothing; the year view has no drag. Haptics: a tick for every new day, one
"no" tick at the month's edge. A normal swipe still pages (the gesture waits for the system's
long-press time before it takes the pointer). TalkBack users keep the day sheet and the "Bis"
dialog: drag is never the only way. Details of the build: `docs/milestone-0.11.0.md`.

### Backup reminder (0.11.0, S1)

Android's own backup is off, so a lost or replaced phone loses everything unless a backup file was
saved. A quiet reminder, no notification and no new permission:

- `core.backup.BackupReminder` decides (unit-tested, injected dates): a hint when the last backup is
  more than 90 days old (`BackupHint.Old(months)`), or, when there was never one, 14 days after the
  app first saw marked days (`BackupHint.Never`). Nothing is shown while nothing is marked.
- `data/BackupStatusStore` keeps two dates in private preferences (`backup_status`): `last_backup`
  (set when "Sicherung speichern" has written the file; a restore does not count) and `marks_since`
  (the first day the overview saw marked days; the database does not know when a day was marked).
  Not in Room, not in the backup file: no migration, no format change. An unreadable value reads as
  unknown.
- `PlannerViewModel.backupHint` combines the store, "any marked day" (`MarkedDayDao.observeAny`, a
  query only) and today. The overview shows one line at the very bottom (the maintainer's choice,
  2026-11-10, over a place under the numbers or under the trip card): "Letzte Sicherung vor 4
  Monaten" or "Noch keine Sicherung gespeichert", the whole line a 48 dp button (label "Datensicherung
  öffnen"). It opens the settings on the backup page (`SettingsHost(startPage)`); the next ordinary
  visit to the settings starts on the list again.
- The backup page shows "Zuletzt gesichert: 03.11.2026" or "Noch keine Sicherung" (not on a fresh
  install, where restoring is the first step).
- Wording is in `strings.xml` (`backup_hint_*`, `backup_last`, `backup_never`); the two day counts
  are `BackupReminder.OLD_AFTER_DAYS` and `FIRST_HINT_AFTER_DAYS`.

## Status

- [x] M0 Skeleton (PR #3, merged 2026-10-25)
- [x] M1 Rules in `:core`: public holidays (PR #5), vacation balance (PR #7), Brückentage (PR #8, merged 2026-10-25), Bayern Mariä Himmelfahrt opt-out (PR #9, merged 2026-10-25)
- [x] M2 Data and storage: Room (PR #10), school holiday file and loader (PR #11), both merged 2026-10-25; backup export/import (PR #12, merged 2026-10-25)
- [x] M3 Screens: settings page and backup screen (PR #13, merged 2026-10-25); tabs, settings list and Darstellung (PR #14); home status, year/month view, marking (PR B); Brückentage (PR C, PR #18); design review changes (shapes, month card, year rows, weekend contrast)
- [x] M4 Export and widget: ICS export (PR #20, merged 2026-10-26); widget (PR #21, merged 2026-10-26; split into a small and a big widget, PR in review)
- [x] M5 Polish (animations, final UI review, tech debt review)
- [x] M6 Release prep (0.9.0 released on GitHub 2026-10-26; F-Droid postponed): about screen (PR #29); signing, version 0.9.0, F-Droid texts and icon, `docs/release.md` (release-files PR); still the maintainer's: the key, the F-Droid merge request, the phone test
- [x] 0.10.0 (parked items 18 to 23) built on `release/0.10.0`: clear a year (PR #44), lilac school holidays (PR #45), granted days (PR #46), month slide and year rows that fit (PR #47), next holiday highlight (PR #49); release PR `feature/0-10-release`
- [x] 0.10.1 performance and feel fixes (`feature/0-10-1-perf-feel`, PR #56, merged 2026-10-31)
- [x] 0.11.0 (the visible features) built on `release/0.11.0`: backup reminder (PR #58), range picker for "Bis" (PR #59), legend in the year view, second tap on Kalender, portrait on phones (PR #60), debug application ID suffix (PR #61), calendar pager (PR #62), expanded year rows fill the screen (PR #63), drag to mark a range (PR #64); release PR `feature/0-11-release`
