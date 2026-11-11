package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.YearMonth

/** The Brückentage card and sheet on the overview, the dashed days and the year view's legend. */
@RunWith(AndroidJUnit4::class)
class BridgesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var fixture: PlannerFixture

    @Before
    fun open() {
        fixture = PlannerFixture()
        fixture.fill()
    }

    @After
    fun close() = fixture.close()

    private val haptics = RecordedHaptics()

    private fun showOverview(waitFor: String) {
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                OverviewScreen(
                    fixture.viewModel(),
                    PaddingValues(),
                    onOpenSettings = {},
                    onOpenBackup = {},
                    onOpenMonth = {},
                    onShowDate = {},
                )
            }
        }
        waitForText(waitFor)
        // The data arrives from Room in steps (a new Land redraws the card), so let it settle.
        compose.waitForIdle()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    /** The first line with [text]: two holidays can both say "danach: Fr nehmen". */
    private fun line(text: String) =
        compose.onAllNodes(hasText(text, substring = true) and hasClickAction()).onFirst()

    /** The sheet's own button: the lines on the card end in "Eintragen" too. */
    private fun sheetButton(text: String) =
        compose.onNode(hasText(text) and !hasText("nehmen", substring = true))

    private fun marks() =
        runBlocking {
            fixture.repository
                .observeMarkedDays(fixture.date("2027-01-01"), fixture.date("2027-12-31"))
                .first()
                .marks
        }

    @Test
    @Config(qualifiers = "de")
    fun `the card names each holiday with the days to take`() {
        showOverview("Brückentage")

        compose.onNodeWithText("Brückentage").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText(
                "Christi Himmelfahrt, Do 06.05.",
            ).performScrollTo()
            .assertIsDisplayed()
        line("danach: Fr nehmen, 4 Tage frei").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fronleichnam, Do 27.05.").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the card reads in English`() {
        showOverview("Bridge days")

        compose.onNodeWithText("Ascension Day, Thu, May 6").performScrollTo().assertIsDisplayed()
        line("after: take Fri, 4 days off").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a Wednesday holiday shows before and after under one heading`() {
        runBlocking {
            fixture.repository.saveSettings(UserSettings(land = Bundesland.SACHSEN))
        }
        showOverview("Buß- und Bettag")

        compose.onNodeWithText("Buß- und Bettag, Mi 17.11.").performScrollTo().assertIsDisplayed()
        line("davor: Mo und Di nehmen, 5 Tage frei").performScrollTo().assertIsDisplayed()
        line("danach: Do und Fr nehmen, 5 Tage frei").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `tapping a line asks first and Eintragen stores the days`() {
        showOverview("danach: Fr nehmen")

        line("danach: Fr nehmen, 4 Tage frei").performScrollTo().performClick()
        waitForText("Brückentage eintragen")
        compose.onNodeWithText("Fr 07.05.").assertIsDisplayed()
        compose
            .onNodeWithText(
                "4 Tage frei am Stück, 06.05.2027 bis 09.05.2027",
            ).assertIsDisplayed()
        compose.onNodeWithText("Kostet 1 Urlaubstag, danach noch 32 übrig").assertIsDisplayed()
        // Asking stores nothing yet.
        assertTrue(fixture.date("2027-05-07") !in marks())

        sheetButton("Eintragen").performClick()

        compose.waitUntil(5_000) { fixture.date("2027-05-07") in marks() }
        assertEquals(DayMark.FULL, marks()[fixture.date("2027-05-07")])
        // The suggestion is gone once the day is marked.
        compose.waitUntil(5_000) {
            compose
                .onAllNodesWithText("Christi Himmelfahrt, Do 06.05.")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun `Eintragen gives a confirm tick and the sheet slides down, though the bridge is gone`() {
        showOverview("danach: Fr nehmen")
        line("danach: Fr nehmen, 4 Tage frei").performScrollTo().performClick()
        waitForText("Brückentage eintragen")
        compose.mainClock.autoAdvance = false

        sheetButton("Eintragen").performClick()
        // Long enough for the marked day to reach the screen (the bridge leaves the card), not
        // for the slide to end.
        compose.waitUntil(5_000) { fixture.date("2027-05-07") in marks() }
        Thread.sleep(200)
        compose.mainClock.advanceTimeBy(64)

        compose.onNodeWithText("Brückentage eintragen").assertExists()
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics.performed)

        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Brückentage eintragen").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun `Abbrechen stores nothing`() {
        showOverview("danach: Fr nehmen")

        line("danach: Fr nehmen, 4 Tage frei").performScrollTo().performClick()
        waitForText("Brückentage eintragen")
        compose.onNodeWithText("Abbrechen").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Brückentage eintragen").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(fixture.date("2027-05-07") !in marks())
        // Nothing stored, so nothing to confirm.
        assertEquals(emptyList<HapticFeedbackType>(), haptics.performed)
    }

    @Test
    @Config(qualifiers = "en")
    fun `the sheet reads in English too`() {
        showOverview("after: take Fri")

        line("after: take Fri, 4 days off").performScrollTo().performClick()
        waitForText("Add bridge days")
        compose.onNodeWithText("Fri, May 7").assertIsDisplayed()
        compose.onNodeWithText("4 days off in a row, ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a possible bridge day says so in the calendar and the legend names it`() {
        val viewModel = fixture.viewModel()
        viewModel.showMonth(YearMonth.of(2027, 5))
        compose.setContent {
            CalendarScreen(viewModel, PaddingValues(), onOpenSettings = {})
        }
        waitForText("Mai 2027")

        compose
            .onNode(hasContentDescription("Freitag, 7. Mai 2027, möglicher Brückentag"))
            .assertExists()
        compose.onNodeWithText("Brückentag").performScrollTo().assertIsDisplayed()
        // An ordinary working day is not one.
        compose
            .onNode(hasContentDescription("Montag, 10. Mai 2027", substring = true))
            .assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the legend has no Brückentag entry in a month without one`() {
        val viewModel = fixture.viewModel()
        compose.setContent {
            CalendarScreen(viewModel, PaddingValues(), onOpenSettings = {})
        }
        waitForText("März 2027")

        compose.onNodeWithText("Brückentag").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the year view names the Brückentag in its legend`() {
        val viewModel = fixture.viewModel()
        viewModel.showMonth(YearMonth.of(2027, 5))
        viewModel.showMode(CalendarMode.YEAR)
        compose.setContent {
            CalendarScreen(viewModel, PaddingValues(), onOpenSettings = {})
        }
        waitForText("2027")

        compose.onNodeWithText("Brückentag").performScrollTo().assertIsDisplayed()
    }
}
