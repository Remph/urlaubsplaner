# Releasing Urlaubsplaner

How a release is made, and what is the maintainer's own step. Facts that change over time are dated; check
them again before relying on them. Nothing here tags or publishes by itself: tags and GitHub
releases only when the maintainer asks (`CLAUDE.md`).

## Decided (the maintainer, 2026-10-26)

- Two sources: **F-Droid** (preferred) and an **APK on GitHub**. No Google Play account for now.
- First public version is **0.9.0**. Version scheme: `versionCode = major * 10000 + minor * 100 +
  patch`, so 0.9.0 is 900 and 1.0.0 is 10000. Both are set in `app/build.gradle.kts`.
- The GitHub APK is signed with the maintainer's **own release key**, made on their PC. F-Droid signs its own
  build with its own key, so the two sources cannot update each other: whoever switches from one to
  the other uninstalls first (back up through Einstellungen > Datensicherung before).
- No contact e-mail anywhere; **GitHub issues** are the contact: <https://github.com/Remph/urlaubsplaner/issues>.
  A private, non-profit app without a website probably needs no Impressum, but that is not legal advice.

## The release key (the maintainer's step, on his PC)

The key is the identity of the app for everyone who installs the GitHub APK. **Lose it and nobody
can update; the only way out is a new package name.** So:

1. Make it once, on the PC, with the JDK's `keytool` (Android Studio ships one in its `jbr\bin`
   folder). Choose strong passwords and write them into the password manager:

   ```
   keytool -genkeypair -v -keystore urlaubsplaner-release.jks -alias urlaubsplaner ^
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   (`^` continues a line in the Windows command prompt, use `\` on Linux and macOS.)
2. Keep the `.jks` file **outside the repo folder** and make a backup copy somewhere else (a second
   disk or the password manager's file store). It is never committed and never goes to the cloud;
   `*.jks`, `*.keystore` and `keystore.properties` are in `.gitignore`.
3. Write down the certificate fingerprint, it is public and later needed to register the app with
   Google (below) and to check a downloaded APK:

   ```
   keytool -list -v -keystore urlaubsplaner-release.jks -alias urlaubsplaner
   ```

   SHA-256 fingerprint of the release key (made 2026-10-26, alias `urlaubsplaner`):

   ```
   03:6E:CB:17:E5:38:A1:CA:BF:12:91:39:6E:75:7D:F1:47:6B:8C:44:0D:87:29:28:E2:B6:E7:AB:D2:03:F1:68
   ```

## Building the signed APK (on the PC)

The build reads the key from environment variables or from an untracked `keystore.properties` in
the repo root (never both needed):

```
# keystore.properties (not in git)
storeFile=C:/Users/<you>/keys/urlaubsplaner-release.jks
storePassword=...
keyAlias=urlaubsplaner
keyPassword=...
```

Write the path with forward slashes, as above: in a `.properties` file a backslash is an escape
character and `C:\Users\...` is silently mangled.

The environment variables are `URLAUBSPLANER_STORE_FILE`, `URLAUBSPLANER_STORE_PASSWORD`,
`URLAUBSPLANER_KEY_ALIAS` and `URLAUBSPLANER_KEY_PASSWORD`. A relative `storeFile` counts from the
repo root. If only some of the four are given the build stops with a message; if none is given
`assembleRelease` makes an **unsigned** APK, which is what F-Droid and the cloud get.

```
./gradlew assembleRelease
```

The signed APK is `app/build/outputs/apk/release/app-release.apk` (the unsigned one is
`app-release-unsigned.apk`). Check the signature and the key before sending it anywhere:

```
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

(`apksigner` is in the Android SDK's `build-tools` folder.) The SHA-256 line must be the
fingerprint written down above. Do not put the APK in git (`*.apk` is ignored).

### R8 (code shrinking) is off

`isMinifyEnabled` is `false` for 0.9.0. Tried in the cloud on 2026-10-26: with R8 the release APK
drops from about 30 MB to about 5.4 MB and the build passes, but Room, Glance, WorkManager and
kotlinx-serialization reach classes by reflection and the cloud has no emulator, so a shrunk APK
was never run. To turn it on later, do it in its own PR and test the release APK on a phone: open
the app, mark days, export and **import a backup**, add both widgets and let them update, change
the language. Missing rules go into a new `app/proguard-rules.pro` (not there yet).

## Before a release: test on the phone

The standard check (`./gradlew lint test assembleDebug spotlessCheck`) is not enough for a release;
the instrumented tests (`connectedDebugAndroidTest`, on both emulators, not the phone) and the widget cannot run in the cloud. Install the signed
release APK on the phone (the debug build has its own application ID `de.remph.urlaubsplaner.debug`, so it can stay installed next to it) and walk through: first
start, choose a Land, enter the entitlement, mark days, bridge days, widget (small and big),
backup export and import, calendar export, German and English, dark mode.

## Making a release (checklist)

1. A PR on its own branch (as always) that sets `versionCode` and `versionName` in
   `app/build.gradle.kts`, adds `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt` and
   the same for `en-US` (at most 500 characters), and updates the screenshots and the school holiday
   file if they changed. The maintainer merges it after the review.
2. Build the signed APK on the PC and test it (above).
3. Tag the merge commit **`v<versionName>`**, for example `v0.9.0`, and push the tag. F-Droid
   builds from tags: the tag must be on the commit that has that `versionCode`.
4. A **GitHub release** with the signed APK attached, only when the maintainer asks. Name the file
   `urlaubsplaner-<versionName>.apk`. Put the changelog text and the SHA-256 of the APK in the notes.
5. F-Droid notices the new tag by itself once the app is in (`AutoUpdateMode: Version`,
   `UpdateCheckMode: Tags`), usually within a few days.

## F-Droid (the maintainer's step: the merge request)

Official sources, read on 2026-10-26 from F-Droid's own GitLab repositories (`fdroid/fdroiddata`:
`CONTRIBUTING.md`, `templates/`, merge request template; `fdroid/fdroid-website`: Inclusion Policy,
Quick Start Guide). `f-droid.org` itself is not reachable from the cloud, `gitlab.com` is.

**What is ready in this repo** (`fastlane/metadata/android/`): `de-DE` and `en-US` with `title.txt`,
`short_description.txt` (under 80 characters, no trailing dot), `full_description.txt`,
`changelogs/<versionCode>.txt` (900, 901, 902, 1000, 1001), `images/icon.png` (512 px) and four screenshots in `images/phoneScreenshots/`.
F-Droid requires `en-US`; `de-DE` is extra. Descriptions use only the HTML tags F-Droid allows.

**What F-Droid checks**: free licence (GPL-3.0), only free dependencies and tools (no Google Play
Services: the dependency audit in the about screen thread found only Apache-2.0 and BSD-3), no
tracking or ads (the app has no INTERNET permission), tag per release, and that the app builds from
source with their tools. The school holiday data is ODbL and sits in its own directory with the
licence text, its credit is in the about screen.

**Possible snag, the "3 months" rule.** The Inclusion Policy asks that an app "has been actively
developed for at least 3 months", unless it has proven users or the author already maintains apps on
F-Droid. This repo started on 2026-10-25, so the reviewers may ask to wait, or accept it on the
strength of the finished app. Say plainly in the merge request that the app is new; that is fine.

**Steps:**

1. Make an account on <https://gitlab.com> and fork <https://gitlab.com/fdroid/fdroiddata>.
2. Optional but recommended for a first app: open a request for packaging at
   <https://gitlab.com/fdroid/rfp/-/issues> (fill in the template).
3. In the fork, new branch `de.remph.urlaubsplaner` (never commit to the fork's `master`), add
   `metadata/de.remph.urlaubsplaner.yml` (draft below), commit.
4. Check the pipeline of the fork (CI/CD menu) is green; fix what it reports.
5. Open the merge request into `fdroid/fdroiddata`, title "New app: Urlaubsplaner", fill in the
   template (what the app does, why it exists, the checklist), one app per merge request.
6. Answer the reviewers' questions. Do not rebase unless there is a conflict.

**Draft of `metadata/de.remph.urlaubsplaner.yml`** (the fields follow `templates/general.yml` and
`templates/build-gradle.yml` of fdroiddata as read on 2026-10-26; not run through `fdroid lint`,
which needs `fdroidserver` on a PC or in the fork's pipeline):

```yaml
Categories:
  - Calendar & Agenda
License: GPL-3.0-only
AuthorName: Remph
SourceCode: https://github.com/Remph/urlaubsplaner
IssueTracker: https://github.com/Remph/urlaubsplaner/issues

RepoType: git
Repo: https://github.com/Remph/urlaubsplaner.git

Builds:
  - versionName: 0.9.1
    versionCode: 901
    commit: v0.9.1
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.9.1
CurrentVersionCode: 901
```

`AuthorName` is public: the maintainer chose the GitHub name Remph (2026-10-26). `License` is
`GPL-3.0-only` because the project says "GPL-3.0" and no source file carries an "or any later
version" notice; if the maintainer means "or later", change it here and add the notice to the sources. Likely
questions from the build: the JDK of F-Droid's build server against the one in `gradle/` (the project
builds on JDK 17 and newer), whether it can fetch the Gradle version of the wrapper, and whether its
Android SDK has platform 37 (`compileSdk` and `targetSdk` are 37, very new, not checked against
their build server). If the build
fails, the pipeline log says why; fix it in the repo and move the tag, or add a `prebuild` line.

## Google developer verification (to do before 2027)

Decided "decide later" (the maintainer, 2026-10-26): **not registered now.** Facts from Google's own pages
(developer.android.com, checked 2026-10-26):

- Enforcement began on 30 September 2026 in Brazil, Indonesia, Singapore and Thailand for
  participating stores, and is to reach apps on certified Android phones worldwide in **2027**.
- Apps from F-Droid or installed by hand (sideloaded) are not affected before that date.
- A full developer account costs 25 US dollars and needs a government ID. A "limited distribution"
  account is free and allows up to 20 devices (enough for the maintainer's own phone).
- There is a route for open-source apps: register the package name `de.remph.urlaubsplaner` with the
  signing key's SHA-256. F-Droid re-signs with its own key, so its fingerprint has to be added too.

**To do before 2027:** read Google's pages again (the rules were still moving), then decide:
register the package name through the open-source route, take the limited distribution account, or
accept that the app may not install on some phones. This is the maintainer's decision, ask one question
with a recommendation.

## Store assets

- **512 px icon:** `python3 tools/render-store-icon.py` redraws it from the launcher icon's numbers
  into both locales (Pillow needed). Run it again whenever the launcher icon changes.
- **Screenshots:** four per language (overview, month, year, settings), 1080 x 2400, light, the
  app's own teal scheme, Nordrhein-Westfalen, sample data and a few marked days. They are real
  screenshots of the signed release APK on the API 37 emulator (PR #39): the sample data comes from
  a hand-made backup file, the emulator clock is set to 20 April 2026 and the status bar is in demo
  mode. Redo them on the emulator whenever screens change (0.10.0 changed the colours and the year
  view); the file names `1.png` to `4.png` set the order.
- **Texts:** `fastlane/metadata/android/<locale>/`. German first. A text that promises a feature
  must match the app: check the descriptions when a feature is added or removed.
