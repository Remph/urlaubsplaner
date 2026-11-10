package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "Bis" choice of the day sheet, through the real calendar: what it shows and hands back. */
@RunWith(AndroidJUnit4::class)
class RangePickerTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var fixture: PlannerFixture
    private val haptics = RecordedHaptics()

    @Before
    fun open() {
        fixture = PlannerFixture()
        fixture.fill()
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                CalendarScreen(fixture.viewModel(), PaddingValues(), onOpenSettings = {})
            }
        }
    }

    @After
    fun close() = fixture.close()

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    /** Opens the sheet for Thursday 25 March 2027 (before Easter) and then its "Bis" choice. */
    private fun openPicker() {
        waitForText("Karfreitag")
        calendarDay("Donnerstag, 25. März 2027").performClick()
        waitForText("Bis: 25.03.2027")
        compose.onNodeWithText("Bis: 25.03.2027").performScrollTo().performClick()
        waitForText("Bis wann?")
    }

    private fun calendarDay(description: String) =
        compose.onNode(hasContentDescription(description, substring = true))

    /** A node inside the picker: the sheet and the calendar behind it have the same days. */
    private fun inPicker(description: String): SemanticsNodeInteraction =
        compose.onNode(
            hasContentDescription(description, substring = true) and
                hasAnyAncestor(hasTestTag(RANGE_PICKER_TAG)),
        )

    private fun pickerText(
        text: String,
        substring: Boolean = false,
    ) = compose.onNode(
        hasText(text, substring = substring) and hasAnyAncestor(hasTestTag(RANGE_PICKER_TAG)),
    )

    private fun nextMonth() = inPicker("Nächster Monat").performClick()

    @Test
    @Config(qualifiers = "de")
    fun `the picker names the first day and starts with just that day`() {
        openPicker()

        compose.onNodeWithText("Von Donnerstag, 25. März 2027").assertIsDisplayed()
        pickerText("März 2027").assertIsDisplayed()
        inPicker("Donnerstag, 25. März 2027").assertIsDisplayed()
        pickerText("Kostet 1 Urlaubstag", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `picking an end day in the next month shows the cost and OK hands it to the sheet`() {
        openPicker()

        nextMonth()
        inPicker("Mittwoch, 7. April 2027").performClick()
        // Karfreitag, Ostermontag and two weekends cost nothing: 8 of the 14 days.
        pickerText("Kostet 8 Urlaubstage", substring = true).performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText(
                "Arbeitsfreie Tage und Feiertage",
                substring = true,
            ).assertIsDisplayed()
        compose.onNodeWithText("OK").performScrollTo().performClick()

        waitForText("Bis: 07.04.2027")
        compose
            .onNodeWithText("Kostet 8 Urlaubstage", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `days inside the range say so in words and the ends are selected`() {
        openPicker()
        nextMonth()
        inPicker("Mittwoch, 7. April 2027").performClick()

        inPicker("Mittwoch, 7. April 2027").assertIsDisplayed()
        compose
            .onNode(
                hasContentDescription("Dienstag, 6. April 2027", substring = true) and
                    hasAnyAncestor(hasTestTag(RANGE_PICKER_TAG)),
            ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `days before the first day cannot be picked`() {
        openPicker()

        inPicker("Mittwoch, 24. März 2027").assertIsNotEnabled()
        inPicker("Freitag, 26. März 2027").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the months stop at the first day's month and at December`() {
        openPicker()

        inPicker("Vorheriger Monat").assertIsNotEnabled()
        repeat(9) { nextMonth() }
        pickerText("Dezember 2027").assertIsDisplayed()
        inPicker("Nächster Monat").assertIsNotEnabled()
        inPicker("Vorheriger Monat").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a half day halves the cost the picker shows`() {
        waitForText("Karfreitag")
        calendarDay("Donnerstag, 25. März 2027").performClick()
        waitForText("Bis: 25.03.2027")
        compose.onNodeWithText("Halber Tag").performClick()
        compose.onNodeWithText("Bis: 25.03.2027").performScrollTo().performClick()
        waitForText("Bis wann?")

        nextMonth()
        inPicker("Mittwoch, 7. April 2027").performClick()

        pickerText("Kostet 4 Urlaubstage", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `cancelling leaves the end day as it was`() {
        openPicker()
        nextMonth()
        inPicker("Mittwoch, 7. April 2027").performClick()
        compose.onNodeWithText("Abbrechen").performScrollTo().performClick()

        waitForText("Bis: 25.03.2027")
        compose.onNodeWithText("Bis: 25.03.2027").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `choosing a new end day gives a tick and choosing the same one again does not`() {
        openPicker()

        inPicker("Freitag, 26. März 2027").performClick()
        inPicker("Freitag, 26. März 2027").performClick()

        assertEquals(1, haptics.performed.count { it == HapticFeedbackType.SegmentTick })
    }

    @Test
    @Config(qualifiers = "de")
    fun `the range can be chosen and marked from the picker`() {
        openPicker()
        nextMonth()
        inPicker("Mittwoch, 7. April 2027").performClick()
        compose.onNodeWithText("OK").performScrollTo().performClick()
        waitForText("Bis: 07.04.2027")
        compose.onNodeWithText("Eintragen").performScrollTo().performClick()

        compose.waitUntil(5_000) {
            compose
                .onAllNodes(hasContentDescription("Donnerstag, 25. März 2027, Urlaub geplant"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        calendarDay("Dienstag, 30. März 2027").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the picker has English words too`() {
        waitForText("Good Friday")
        calendarDay("Thursday, March 25, 2027").performClick()
        waitForText("Until: Mar 25, 2027")
        compose.onNodeWithText("Until: Mar 25, 2027").performScrollTo().performClick()
        waitForText("Until when?")

        pickerText("From Thursday, March 25, 2027").assertIsDisplayed()
        compose.onNodeWithText("OK").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed()
    }
}
