// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "urlaubsplaner"

include(":core")
// The Android app is skipped with -PcoreOnly, so :core builds and tests where the Android SDK
// and Google Maven are not reachable. Never apply Android plugins in the root build file.
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
    // Generates the baseline profile and measures the start on a device; never part of the app.
    include(":baselineprofile")
}
