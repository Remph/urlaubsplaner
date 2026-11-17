// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** The app under test, the same instance the screens and the widget use. */
val targetApp: UrlaubsplanerApp
    get() =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as UrlaubsplanerApp

/**
 * Starts every test with an empty database and leaves it empty. The tests work on the app's real
 * database (the widget and the screens only know that one), so on a phone with real data they
 * would wipe it: the rule refuses to run anywhere but on an emulator, unless the run is started
 * with `-Pandroid.testInstrumentationRunnerArguments.allowRealDevice=true`.
 *
 * This is an [ExternalResource] and not a `TestWatcher` on purpose: when `TestWatcher.starting`
 * throws, JUnit still runs the test body and `finished`, which would be the wipe. When
 * `ExternalResource.before` throws, neither the test nor `after` runs.
 */
class FreshApp : ExternalResource() {
    override fun before() {
        requireEmulator()
        wipe()
    }

    override fun after() = wipe()

    private fun requireEmulator() {
        val arguments = InstrumentationRegistry.getArguments()
        check(isEmulator() || arguments.getString(ALLOW_REAL_DEVICE) == "true") {
            "These tests empty the app's database. Run them on an emulator; on a device pass " +
                "$ALLOW_REAL_DEVICE=true only if its Urlaubsplaner data may be deleted (and make " +
                "a backup first)."
        }
    }

    private fun wipe() {
        // Checked again here, so no path can reach the wipe without the guard.
        requireEmulator()
        // clearAllTables must not run on the main thread; the test thread is not the main thread,
        // but the IO dispatcher keeps that obvious.
        runBlocking(Dispatchers.IO) { targetApp.database.clearAllTables() }
    }

    private fun isEmulator() =
        Build.HARDWARE == "goldfish" ||
            Build.HARDWARE == "ranchu" ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.PRODUCT.contains("sdk_gphone") ||
            Build.MODEL.contains("Emulator")

    private companion object {
        const val ALLOW_REAL_DEVICE = "allowRealDevice"
    }
}
