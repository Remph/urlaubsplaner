# Milestone 0.11.0

The plan for the second minor release, written 2026-11-10 for the threads that build it, from the
maintainer's answers in the planning thread. 0.11.0 holds the features you can see. 1.0.0 comes
after it as the "stable" release (R8, baseline profile, open fixes, full device test; see the end).
Austria and Switzerland are for 1.x.x and stay out of this milestone. Nothing else goes in unless
the maintainer asks. The rules in `CLAUDE.md` apply unchanged.

## Scope

| # | What | Origin | Where in the code (checked on `main` at `4a22a3a`, 0.10.1) | Data change |
| --- | --- | --- | --- | --- |
| L | Hide the legend while the year view is expanded, to give the rows more space | Maintainer's idea, 2026-11-10 | `CalendarScreen` (the year branch draws `Legend` under `YearGrid`; `rowsExpanded` from `PlannerViewModel.expandYearRows`), and the expanded-row fit, which measures "the page minus the grid" | none |
| 27 | Tapping the open Kalender tab again goes back to today's month (month view) or this year (year view) | parked 27 | `ui/AppRoot.kt` (`NavigationBarItem.onClick` only sets the tab), `PlannerViewModel` | none |
| 25 | Lock to portrait on phones only (smallest width under 600 dp). Tablets keep rotating, which leaves room for a tablet layout later (parked 26) | parked 25 | `MainActivity` (the manifest cannot tell a phone from a tablet: set `requestedOrientation` at start from `smallestScreenWidthDp`), `WidgetConfigActivity` | none |
| 28 | Calendar swipe that follows the finger and snaps (a pager) | parked 28 | `CalendarScreen` (`ScreenFrame` + `Modifier.swipeToStep` in `SwipeToStep.kt`, `AnimatedContent` keyed on `CalendarPage.generation`), `CalendarPage.kt`, `Highlight.kt` | none |
| S1 | Backup reminder: the backup page shows when the last backup was saved, and after 3 months without one a quiet line on the Übersicht reminds you | Claude's suggestion, accepted 2026-11-10 | `settings/` backup page, `OverviewScreen`, a small local store like `appearance/AppearanceStore.kt` | none in Room or the backup file (see below) |
| 24 | Clearer date picker that makes the picked range obvious | parked 24 | `planner/DaySheet.kt` (`EndDatePicker`: M3 `DatePicker` in a `DatePickerDialog`, end day in the same year as the start) | none |
| S2 | Mark a range by long-pressing a day in the month view and dragging across the days | Claude's suggestion, accepted 2026-11-10 | `DayCells.kt` / `MonthGrid`, `PlannerViewModel` (the "Bis" range of the day sheet already marks ranges) | none |

Not in 0.11.0: app shortcuts on the app icon (suggestion S3; the maintainer parked it for 1.0.0 on
2026-11-10; it is parked item 30).

What the code and the answers say about each:

- **L.** Legend only, not the "school holidays unknown" note or the empty state. The expanded height
  is "what fits" (`expandedRowHeight`), measured from the page minus the grid, so the measurement must
  not run a frame with the legend still there. Otherwise the rows jump twice. The legend should fade
  out and back with the row animation, or the page changes height in a jump. Collapsed rows keep the
  legend. Tests: Compose test (legend there when collapsed, gone when expanded, back after
  collapsing), and the fit check from 0.10.0 on 360x640 and at 130 % font size, now with more room.
- **27.** A second tap on the open Kalender tab calls the same "today" step as the rest of the app
  (month view: current month, year view: current year, mode stays). It is a no-op when already
  there. The Übersicht and Einstellungen tabs keep doing nothing on a second tap, or scroll to the
  top if that's easy. Ask the thread's design question only if the year/month choice turns out to
  be unclear. TalkBack: the tab's action label says what it does.
- **25.** Phones only, decided. One check: the widget setup screen and every dialog still look right
  in portrait on a phone that is held sideways (the activity must not flash landscape first). Rotation
  tests that exist today keep running for the tablet case (Robolectric `sw600dp` qualifier).
- **28.** The biggest item. Today the month or year slides only after the finger is released
  (0.10.0 T4, `rememberSharedAxis`). A pager (`HorizontalPager` from Compose foundation, no new
  library) moves with the finger and snaps. It has to keep: the previous/next arrows and TalkBack
  (nothing gesture-only), the year step limits (`canStep`), the "Nächster Feiertag" highlight that
  plays after the slide (`Highlight.kt`), the vertical expand-by-swipe of the year rows
  (`expandBySwipe`), the first-switch preloading from PR #52, and "animations off" in the system
  settings. With portrait locked (25) on phones, landscape is only a tablet check. Design question
  first: one shared pager for month and year (recommended) or only months; and how far neighbour
  pages are drawn (recommended: the next page appears only while you drag, no peeking).
- **28, as built (T4, `feature/0-11-pager-swipe`).** One `HorizontalPager` for month and year
  (made again when the mode changes), no peeking: the next page shows only while a finger drags.
  The ViewModel stays the truth for month and mode; `PagerFollowsViewModel` in `CalendarScreen`
  scrolls the pager when the arrows or "today" change the month, and sets the month when a swipe
  has settled (with the tick; the arrows have none). Every page reads the view of its own year
  (`PlannerViewModel.calendarView(year)`), and the ViewModel keeps the years next to the page in
  view read ahead, so a page dragged in from December to January is not blank. This replaced
  `CalendarPage`, its `generation` and the `AnimatedContent` slide. The header stays; each page
  scrolls on its own (a pager around the page's scroll, not inside it, or TalkBack would scroll
  the pager when it reaches for an item below the screen). The title moves with the pager.
  A swipe past the first or last month stretches the page and gives the "no" tick. The decision
  was asked of the maintainer on a card (recommended option built; the card's answer is noted in
  the PR).
- **S1.** The last-backup date is a fact about this phone, not user data: it goes into a small local
  store (SharedPreferences or DataStore like `AppearanceStore`), not into Room and not into the backup
  file, so no migration and no backup format change. It is set when "Sicherung speichern" succeeds.
  A restore does not count. Logic with an injected clock and unit tests: hint when the last backup
  is more than 90 days old, or when there has never been one and marked days exist (design question:
  how soon after the first marks; recommended 14 days). The hint is one line on the Übersicht with
  a tap to the backup page, and no notification (no new permission). Design questions first (mock-up):
  where the line sits on the Übersicht and its wording in German and English, for example "Letzte
  Sicherung vor 4 Monaten" / "Last backup 4 months ago". The backup page shows "Zuletzt gesichert:
  3. Nov. 2026" or "Noch keine Sicherung".
- **24 and S2 together.** Both are about marking several days, so they get one design round with
  mock-ups (HTML plus Playwright screenshots, like `design/mockups-r1/`), one question at a time:
  - 24: Material 3 `DateRangePicker` (already in the Compose Material 3 library, no new dependency),
    a styled single picker that colours the days between start and end, or our own small month grid
    in the sheet that reuses the calendar's tiles. Recommended: compare the three in mock-ups. If a
    library comes up, the usual rules apply (well-known, maintained, free licence, no Play Services,
    nothing tied to Palantir or Thiel). The range stays inside one year, because the balance is per
    year.
  - S2: long-press a day, then drag across the following days. The covered days highlight while
    you drag, and letting go opens the same choice as the day sheet's "Bis" range (Urlaub,
    halber Tag, Entfernen) instead of marking right away. A haptic tick per day, like the 0.10.1
    ticks. A long press is needed so the drag doesn't fight the pager swipe (28) or the scroll. The
    range can go across weeks, and stops at the month edge: across months is the picker's job. TalkBack
    users keep the sheet and the picker, so drag is never the only way.

## Order and threads

```
Phase 1 (in parallel)   T1 L + 27 + 25        T2 S1               T3 design of 24 + S2, build 24
Phase 2                 T4 item 28 (pager)
Phase 3                 T5 S2 (drag to mark)
Phase 4                 T6 release 0.11.0
```

| Thread | Items | Branch | Depends on | Why this place |
| --- | --- | --- | --- | --- |
| T1 Small calendar round | L, 27, 25 | `feature/0-11-calendar-small` | nothing | Three small items, one PR and one review (small fix round). |
| T2 Backup reminder | S1 | `feature/0-11-backup-reminder` | nothing | Settings and Übersicht only, no overlap with the calendar. |
| T3 Marking several days | design of 24 and S2, builds 24 | `feature/0-11-range-picker` | nothing | Designing both at once avoids two different range looks. Building 24 only touches `DaySheet`. |
| T4 Pager swipe | 28 | `feature/0-11-calendar-pager` | T1 merged (both change `CalendarScreen`) | The biggest change to the calendar screen gets its own thread and tests. |
| T5 Drag to mark | S2 build | `feature/0-11-drag-to-mark` | T3's decisions, T4 merged | The drag gesture must be built on top of the final pager, or the gesture work is done twice. |
| T6 Release | all | `feature/0-11-release` | T1 to T5 merged | See "Release steps". |

If the maintainer wants fewer threads at a time, run them in the order T1, T2, T3, T4, T5.

## The integration branch

As in 0.10.0 (`docs/milestone-0.10.0.md`, "The integration branch"), with 0.11 in place of 0.10:
`release/0.11.0` from `main` at `4a22a3a` (0.10.1), feature branches `feature/0-11-<topic>` made from
it, PR base `release/0.11.0`, merge commits only, nobody pushes to it directly. Patch releases
(0.10.x) keep going from `main` and are synced in by `feature/0-11-sync-main-<n>`. `versionCode`
and `versionName` stay as `main` has them until the release PR.

No item plans a Room or backup format change. If one turns out to need one, stop and ask the
maintainer first (Room is at version 4, backup format at 2).

Cloud threads: a cloud session can push only to its own session branch. If it cannot push
`feature/0-11-<topic>`, it uses its session branch as the PR head and names the item in the PR
title. The PR base is still `release/0.11.0`.

## Release steps (T6)

As in 0.10.0, with these values:

1. Sync the latest `main` into `release/0.11.0`.
2. Release PR into `release/0.11.0`: `versionCode = 1100`, `versionName = "0.11.0"`,
   `fastlane/metadata/android/{de-DE,en-US}/changelogs/1100.txt` (at most 500 characters, German
   first), new screenshots where screens changed (calendar, Übersicht with the backup line), README
   feature list and `docs/plan.md` up to date, parked items 24, 25, 27, 28 moved to Done by the
   parked-items thread (through the coordinator). Standard check and an independent review.
3. Full test on the maintainer's PC (Remote Control) on both emulators (API 30 and 37) and the
   phone: install over 0.10.1 with data, the pager swipe with the arrows and TalkBack, drag to mark,
   the range picker in German and English, the legend fading on expand on the small emulator,
   re-tapping the Kalender tab, portrait on the phone, and rotation on a tablet emulator profile.
   Also the backup date after saving, and the Übersicht hint (set the clock forward).
   `connectedDebugAndroidTest` green, `adb logcat` without FATAL.
4. PR `release/0.11.0` into `main`, merge commit. Tag `v0.11.0` from the maintainer's PC **after**
   that merge, on the merge commit (cloud tag pushes get 403). GitHub release only when the
   maintainer asks.
5. `release/0.11.0` is not reused afterwards.

## After 0.11.0: candidates for 1.0.0

The stable release. No new features beyond these unless the maintainer adds them. Its own plan
comes after 0.11.0 is out.

- Parked 8: R8 shrinking (trial 5.1 MB, green; widget, backup, ICS and about screen still untested
  with it).
- Parked 29: app baseline profile, generated on the maintainer's PC.
- Parked 7: widget stale after "Clear data" plus restore (needs the phone).
- Parked 11: widget receivers `exported="true"` (launcher test on the phone first).
- Parked 14: GPL-3.0-only or "or later".
- Parked 3: both languages complete, store texts and README.
- Parked 30 (was suggestion S3): app shortcuts on the app icon (parked for 1.0.0 by the maintainer, 2026-11-10).
- Full device test on both emulators and the phone, then F-Droid (parked 17): the 3-month rule
  allows a submission from about late January 2027, which fits 1.0.0.
- Before 2027 regardless of version: parked 15 (Google developer verification), re-check Google's
  pages first.
