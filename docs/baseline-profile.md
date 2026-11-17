# Baseline profile

A baseline profile is a list of the classes and methods the app uses in its first minutes. Android
compiles them ahead of time when the app is installed, so the first start and the first visit of
each screen do not run through the slow interpreter. Without it a fresh install (and every update)
starts about 15 to 20 percent slower and stutters on the first visit of each screen (measurements
of 2026-11-16 and 2026-10-31 in `docs/plan.md`).

## What is where

- `app/src/release/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`: the
  committed profile (plain text, generated, do not edit by hand). The build turns it into the
  binary profile in the APK, after R8 has renamed the classes. A normal build, F-Droid's included,
  only reads these files: it needs no device and no extra step.
- `baselineprofile/` (module `:baselineprofile`): the journey that is recorded
  (`Journey.kt`), the generator (`BaselineProfileGenerator`) and the measurement
  (`StartupBenchmark`). It is test code: nothing of it ends up in the app. `-PcoreOnly` skips it
  like `:app`.
- Build dependencies, all Apache-2.0 from Google Maven, used at build time only and not in the
  app's code: the Gradle plugin `androidx.baselineprofile` and `androidx.benchmark:benchmark-macro-junit4`
  and `androidx.test.uiautomator` in the test module, and `androidx.profileinstaller` in the app
  (it installs the profile on first start; Glance already brought it in). Versions are in
  `gradle/libs.versions.toml`.

## Generating the profile (the maintainer's PC, an emulator or a phone)

Do it again when the screens change a lot (a new screen, a new library version) and before a
release if in doubt; a stale profile is not harmful, it only helps less.

1. Start an emulator (API 33 or newer needs no root; an older Google APIs image is rooted by
   `adb root`) or attach a phone with USB debugging, and wait until it is idle. The journey adds
   and removes one day and sets a Bundesland if none is set, so use an emulator or a phone whose
   data may change.
2. Run, from the repo root:

   ```
   .\gradlew :app:generateReleaseBaselineProfile
   ```

   The plugin builds the app without R8 (`nonMinifiedRelease`), installs it, runs the journey
   several times and writes the two files above.
3. Check `git diff --stat`: a few thousand lines are normal. Commit the two files.

## Measuring

```
.\gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

(on the PC, device attached). `StartupBenchmark` runs the cold start and the journey without the
profile (`CompilationMode.None`, the state of a fresh install) and with it (`Partial`), ten and
five runs each. Compare only numbers from the same session on the same device; wait after the
emulator started (the first runs are 50 to 100 percent slower).

## F-Droid

F-Droid builds `assembleRelease` from the tag. The committed profile is plain text and the
Gradle plugin does not generate anything during that build (`automaticGenerationDuringBuild =
false`), so the build needs no device. The tools that compile the profile come from Google Maven
like all other dependencies.
