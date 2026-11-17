// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * Records which classes and methods the app uses and writes them to the baseline profile
 * (docs/baseline-profile.md: `./gradlew :app:generateReleaseBaselineProfile` with a device attached).
 *
 * Two recordings on purpose: the startup profile (`startup-prof.txt`) decides how R8 orders the
 * classes in the dex files, and it helps most when it holds only what the cold start touches. The
 * rest of the journey goes into the baseline profile alone.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() =
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            startApp()
        }

    @Test
    fun journey() =
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            userJourney()
        }
}
