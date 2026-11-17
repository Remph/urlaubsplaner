// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

// No Android plugins here: -PcoreOnly must be able to skip them (see settings.gradle.kts).
plugins {
    alias(libs.plugins.spotless)
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

spotless {
    kotlin {
        target("*/src/**/*.kt")
        ktlint()
        // The GPL asks for a notice in each file; the SPDX line keeps it short and tools can read it.
        // spotlessApply adds it, spotlessCheck fails when a file lacks it.
        licenseHeaderFile(
            rootProject.file("gradle/license-header.txt"),
            "(package |@file:|import )",
        )
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint()
    }
}
