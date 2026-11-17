// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.FreshApp
import de.remph.urlaubsplaner.MainActivity
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.settings.labelRes
import de.remph.urlaubsplaner.targetApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The real [MainActivity] on the device, with the real database: the two things a user does first.
 * Texts come from the app's string resources, so the tests do not care whether the emulator is
 * set to German or English.
 *
 * Not covered: the file picker, the widget (see the widget test), and the Länder other than the two
 * used here (the all-Länder test covers their rules).
 */
@RunWith(AndroidJUnit4::class)
class MarkingOnDeviceTest {
    private val fresh = FreshApp()
    private val compose = createAndroidComposeRule<MainActivity>()

    // The database is emptied before the activity starts, not after it has read it.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fresh).around(compose)

    private val app = targetApp
    private val repository = app.repository

    private fun string(id: Int) = compose.activity.getString(id)

    private fun tab(id: Int) =
        compose.onNode(
            hasText(
                string(id),
            ) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        )

    private fun stored() = runBlocking { repository.readAll() }

    private fun waitUntil(condition: () -> Boolean) =
        compose.waitUntil(TIMEOUT_MILLIS) { condition() }

    @Test
    fun `choosing the Bundesland in the settings stores it`() {
        val land = Bundesland.entries.first()

        tab(R.string.nav_settings).performClick()
        compose.onNodeWithText(string(R.string.land_choose)).performClick()
        compose.onNodeWithText(string(land.labelRes())).performClick()

        waitUntil { stored().settings.land == land }
        // The dialog is gone and the list says which Land is set.
        waitUntil {
            compose
                .onAllNodesWithText(string(R.string.land_choose))
                .fetchSemanticsNodes()
                .isEmpty()
        }
        compose.onNodeWithText(string(land.labelRes())).assertExists()
    }

    @Test
    fun `marking a day in the calendar stores it and removing it clears it`() {
        runBlocking { repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN)) }
        val date = aWorkingDayThisMonth()
        val locale = compose.activity.resources.configuration.locales[0]
        val day = hasContentDescription(date.fullText(locale), substring = true)

        tab(R.string.nav_calendar).performClick()
        waitUntil { compose.onAllNodes(day).fetchSemanticsNodes().isNotEmpty() }
        // The calendar may be taller than a small emulator's screen.
        runCatching { compose.onNode(day).performScrollTo() }
        compose.onNode(day).performClick()
        compose.onNodeWithText(string(R.string.mark_save)).performClick()

        waitUntil { stored().marks[date] == DayMark.FULL }
        // The sheet slides down after Eintragen; the next tap is for the calendar, not the sheet.
        val save = string(R.string.mark_save)
        waitUntil { compose.onAllNodesWithText(save).fetchSemanticsNodes().isEmpty() }

        compose.onNode(day).performClick()
        compose.onNodeWithText(string(R.string.mark_remove)).performClick()

        waitUntil { date !in stored().marks }
        assertEquals(emptyMap<LocalDate, DayMark>(), stored().marks)
    }

    /** A day of this month that is not a weekend or a holiday in NRW, so the sheet offers to mark it. */
    private fun aWorkingDayThisMonth(): LocalDate {
        val today = LocalDate.now(app.clock)
        val check = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN).publicHolidayCheck()!!
        val calculator = VacationCalculator(app.clock, isPublicHoliday = check)
        return generateSequence(today.withDayOfMonth(8)) { it.plusDays(1) }
            .first { calculator.cost(it) > VacationDays.ZERO }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
