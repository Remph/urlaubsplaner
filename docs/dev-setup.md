# Setting up a Windows PC for development

Written 2026-10-26 while setting up the maintainer's PC (Windows, nothing Android installed yet). The
versions below are what was current then; the Android Studio download page always offers the
current stable release, take that one. Everything else (JDK for Gradle, SDK platform) is pinned by
the repo.

## What you need

| Tool | Why | Notes |
| --- | --- | --- |
| Git for Windows | clone, branches | 2.55 on 2026-10-26; `git --version` to check |
| Android Studio (stable) | IDE, SDK Manager, emulator, adb | brings its own JDK (JetBrains Runtime), so no separate JDK |
| Android SDK platform 37, build-tools 37.0.0, platform-tools, emulator | what `compileSdk = 37` needs | install through Tools > SDK Manager |

IntelliJ IDEA can open the project too, but Android Studio sets up the SDK and emulators in one
place, so it is the one used here. Both can be installed side by side.

## Steps

1. **Clone** into a folder of your choice (no spaces needed, none were a problem):

   ```
   git clone https://github.com/Remph/urlaubsplaner.git
   ```

2. **Open** the folder in Android Studio (Open, trust the project). The first Gradle sync
   downloads Gradle 9.8.2 and all libraries, about two minutes. Ignore the Upgrade Assistant: the
   versions in `gradle/libs.versions.toml` are chosen on purpose. The warning "The Kotlin Gradle
   plugin was loaded multiple times" is harmless.
3. **SDK:** Tools > SDK Manager. SDK Platforms: Android API 37. SDK Tools: Android SDK Build-Tools
   37.0.0, Android SDK Platform-Tools, Android Emulator. Apply and accept the licences.
4. **Check:** in the Studio terminal (or PowerShell in the repo folder):

   ```
   .\gradlew lint test assembleDebug spotlessCheck
   ```

   It must end with `BUILD SUCCESSFUL` (first run a few minutes, later runs under a minute).
   Do not add `-ProbolectricRepoUrl`: that is only for the cloud.

## Windows specifics

- One unit test is skipped on Windows (`CalendarExporterTest`, "opening writes a cache file..."):
  androidx.core's `FileProvider` looks for the path separator '/' only. The skip is
  `assumeUnixPaths()` in `app/src/test/.../WindowsPaths.kt`; the test still runs in the cloud.
  Expect `255 passed, 1 skipped` for `:app:testDebugUnitTest`.
- Android Studio may create `gradle/gradle-daemon-jvm.properties` (untracked). It is not part of
  the repo, do not commit it: `git add -A` is risky, add files by name.
- Never add two files whose names differ only in case (Windows is case-insensitive); `CLAUDE.md` has the rule.
- Never put keystores, `keystore.properties` or `local.properties` into git (all are in `.gitignore`).

## Phone and emulators

- **Phone via USB:** Settings > About phone > Software information, tap Build number seven
  times; then Settings > Developer options > USB debugging on. Plug in, allow the computer on the
  phone. `adb devices` must show the phone as `device` (not `unauthorized`).
- `adb` is in `%LOCALAPPDATA%\Android\Sdk\platform-tools`. To call it by its short name, add that
  folder to the user PATH (only the user part, not a copy of the whole PATH):

  ```
  [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";$env:LOCALAPPDATA\Android\Sdk\platform-tools", "User")
  ```

- **Emulators** (Tools > Device Manager > Create Virtual Device, Medium Phone profile, image
  "Google APIs Intel x86_64"): one with API 30 (Android 11, our `minSdk`), one with API 37 (our
  `targetSdk`). Use x86_64, not the 32-bit x86 image. If an emulator will not start, check that
  virtualization is on in the BIOS and "Windows Hypervisor Platform" is on in the Windows
  features.
- **Debug and release side by side:** the debug build has the application ID
  `de.remph.urlaubsplaner.debug` (suffix `.debug`) and the label "Urlaubsplaner Debug". It is a
  separate app from the release build `de.remph.urlaubsplaner`: own data, own widgets, and both can
  be installed on the same phone. Data does not move between them by itself; use Einstellungen >
  Datensicherung (export in one, import in the other).
- **APK signatures:** the debug APK built on the PC is signed with the PC's own debug key, the
  cloud builds with the shared cloud debug key. A phone does not update from one to the other
  (uninstall first, back up before through Einstellungen > Datensicherung). Pick one source per
  device: the PC build for emulators, the cloud APK for the phone.
- `.\gradlew connectedDebugAndroidTest` runs the instrumented tests (`app/src/androidTest`) on every
  connected device or running emulator. Run them on both emulators (API 30 and 37) and **not on
  your phone**: they empty the app's database, and Gradle uninstalls the app afterwards. A rule in
  the tests refuses to run anywhere but on an emulator, but that rule cannot stop Gradle itself
  from installing and then uninstalling the app on the phone. So name the emulator, even when the
  phone is unplugged: in PowerShell `$env:ANDROID_SERIAL = "emulator-5554"` (see the serial with
  `adb devices`), then `.\gradlew connectedDebugAndroidTest`; or pick the emulator in Android Studio.
  If the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, an app signed with another key is on
  the emulator: uninstall it there (`adb -s emulator-5554 uninstall de.remph.urlaubsplaner.debug`) and run
  again. Gradle still ends in BUILD SUCCESSFUL in that case, so read the test report.
  What they cover and what not is in the "Instrumented tests" section of
  [plan.md](plan.md#instrumented-tests-092).

## The release key

Made once on the PC with the `keytool` that ships with Android Studio, never in the cloud or in git.
The exact commands, the back-up advice and the fingerprint are in [release.md](release.md).
