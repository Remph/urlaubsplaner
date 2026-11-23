// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The release's application ID. Permanent (CLAUDE.md), so it is written out here. */
const val TARGET_PACKAGE = "de.remph.urlaubsplaner"

private const val WAIT_MS = 5_000L

/**
 * A text in either language of the app. The device may run German or English, so the journey
 * looks for both instead of reading string resources (the app's resources are not in this
 * module's APK).
 */
private fun text(
    german: String,
    english: String,
): Pattern = Pattern.compile("^(?:${Pattern.quote(german)}|${Pattern.quote(english)})$")

private fun MacrobenchmarkScope.find(pattern: Pattern): UiObject2? =
    device.wait(Until.findObject(By.text(pattern)), WAIT_MS)

private fun MacrobenchmarkScope.click(pattern: Pattern): Boolean {
    val target = find(pattern) ?: return false
    target.click()
    device.waitForIdle()
    return true
}

private fun MacrobenchmarkScope.clickDescription(
    german: String,
    english: String,
): Boolean {
    val target =
        device.wait(Until.findObject(By.desc(text(german, english))), WAIT_MS) ?: return false
    target.click()
    device.waitForIdle()
    return true
}

private val overview = text("Übersicht", "Overview")
private val calendar = text("Kalender", "Calendar")
private val settings = text("Einstellungen", "Settings")

/** Cold start up to the first drawn screen: what the startup profile and the benchmark are about. */
fun MacrobenchmarkScope.startApp() {
    pressHome()
    startActivityAndWait()
    // The bottom bar is there once Compose has drawn the first frame.
    device.wait(Until.hasObject(By.text(overview)), WAIT_MS)
}

/**
 * The first start has no Bundesland yet and asks for one in the settings. Later runs of the same
 * journey (the generator repeats it) find it set and skip this.
 */
private fun MacrobenchmarkScope.chooseLandIfAsked() {
    click(settings)
    if (click(text("Bundesland wählen", "Choose state"))) {
        click(text("Nordrhein-Westfalen", "North Rhine-Westphalia"))
    }
}

/** Month view: step forward and back by button and by swipe, then the year view and back. */
private fun MacrobenchmarkScope.browseCalendar() {
    click(calendar)
    val width = device.displayWidth
    val height = device.displayHeight
    clickDescription("Nächster Monat", "Next month")
    clickDescription("Nächster Monat", "Next month")
    // Swipes on the pager: left is the next month, right the one before.
    device.swipe((width * 0.8).toInt(), height / 2, (width * 0.2).toInt(), height / 2, 12)
    device.waitForIdle()
    device.swipe((width * 0.2).toInt(), height / 2, (width * 0.8).toInt(), height / 2, 12)
    device.waitForIdle()
    click(text("Jahr", "Year"))
    clickDescription("Nächstes Jahr", "Next year")
    clickDescription("Vorheriges Jahr", "Previous year")
    click(text("Monat", "Month"))
}

/**
 * Opens the sheet of a day in the middle of the month grid, adds the day and removes it again, so
 * the sheet, the save and the remove with its undo message are all drawn. The tiles carry their
 * date as a content description, which is how they are told from the other buttons.
 */
private fun MacrobenchmarkScope.markADay() {
    // The tiles are not marked clickable for accessibility (they react to taps and drags through
    // pointer input), so they are found by their description, which always contains the year
    // ("Mittwoch, 18. November 2026").
    val tileDescription = Pattern.compile(".*\\d{4}.*")

    fun dayTile(): UiObject2? {
        val top = device.displayHeight * 0.30
        val bottom = device.displayHeight * 0.65
        // Compose redraws while the list is read; an object that went stale is simply skipped.
        return device
            .findObjects(By.desc(tileDescription))
            .firstOrNull { tile ->
                runCatching { tile.visibleCenter.y in top.toInt()..bottom.toInt() }
                    .getOrDefault(false)
            }
    }
    dayTile()?.click()
    device.waitForIdle()
    // The range picker (the "Until" row) is a heavy dialog: open it once and dismiss it.
    if (click(Pattern.compile("^(?:Bis|Until): .*"))) {
        device.pressBack()
        device.waitForIdle()
    }
    if (click(text("Eintragen", "Add"))) {
        dayTile()?.click()
        device.waitForIdle()
        click(text("Entfernen", "Remove"))
    }
}

/** The settings pages one after the other, each opened and left again. */
private fun MacrobenchmarkScope.visitSettings() {
    click(settings)
    val pages =
        listOf(
            // The row carries the year ("Urlaub 2026").
            Pattern.compile("^(?:Urlaub|Vacation) \\d{4}$"),
            text("Arbeitstage", "Working days"),
            text("Darstellung", "Appearance"),
            text("Datensicherung", "Backup"),
            text("Kalenderexport", "Calendar export"),
            text("Über die App", "About"),
        )
    for (page in pages) {
        // The list is longer than a small screen: scroll once if the row is not in sight.
        if (device.findObject(By.text(page)) == null) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.7).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.3).toInt(),
                20,
            )
            device.waitForIdle()
        }
        if (click(page)) {
            device.pressBack()
            device.waitForIdle()
        }
    }
}

/** The whole journey of the generator: what a person does in the first minutes of the app. */
fun MacrobenchmarkScope.userJourney() {
    startApp()
    chooseLandIfAsked()
    click(overview)
    browseCalendar()
    markADay()
    visitSettings()
    click(overview)
}
