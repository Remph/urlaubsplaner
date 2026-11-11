package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.MonthDay

/** Composes the real calendar: the month, the colours in words, and marking through the sheet. */
@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
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

    private fun show(
        waitFor: String = "März 2027",
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
                LocalHapticFeedback provides haptics,
            ) {
                CalendarScreen(fixture.viewModel(), PaddingValues(), onOpenSettings = {})
            }
        }
        waitForText(waitFor)
    }

    private fun waitForText(
        text: String,
        substring: Boolean = false,
    ) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun day(description: String) =
        compose.onNode(hasContentDescription(description, substring = true))

    @Test
    @Config(qualifiers = "de")
    fun `the calendar shows this month with weekday names`() {
        show()

        compose.onNodeWithText("Mo").assertIsDisplayed()
        compose.onNodeWithText("So").assertIsDisplayed()
        compose.onNodeWithText("Monat").assertIsDisplayed()
        compose.onNodeWithText("Jahr").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `days say in words what the colours say`() {
        show()
        waitForText("Karfreitag")

        day("Freitag, 26. März 2027, Feiertag: Karfreitag").assertExists()
        day("Montag, 8. März 2027, Urlaub genommen").assertExists()
        day("Montag, 15. März 2027, Urlaub geplant").assertExists()
        day("Mittwoch, 10. März 2027, heute").assertExists()
        day("Dienstag, 30. März 2027, Schulferien").assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the month lists its holidays and school holidays by name`() {
        show()
        waitForText("Karfreitag")

        compose.onNodeWithText("Karfreitag").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ostermontag").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Osterferien").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the month card adds up the days taken and planned in the month`() {
        show()
        waitForText("Karfreitag")

        compose.onNodeWithText("2 Tage").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("5 Tage").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `stepping to the next month shows April`() {
        show()

        compose.onNodeWithContentDescription("Nächster Monat").performClick()

        waitForText("April 2027")
        compose.onNodeWithText("Osterferien").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `swiping left shows the next month and swiping right the previous one`() {
        show()

        compose.onRoot().performTouchInput { swipeLeft() }
        waitForText("April 2027")

        compose.onRoot().performTouchInput { swipeRight() }
        waitForText("März 2027")
        compose.onRoot().performTouchInput { swipeRight() }
        waitForText("Februar 2027")
    }

    @Test
    @Config(qualifiers = "de")
    fun `a swipe that starts on a day steps the month and does not open the sheet`() {
        show()
        waitForText("Karfreitag")

        day("Donnerstag, 25. März 2027").performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - 200f)
        }

        waitForText("April 2027")
        compose.onNodeWithText("Ganzer Tag").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a short drag does not change the month`() {
        show()

        compose.onRoot().performTouchInput { swipeLeft(startX = centerX, endX = centerX - 20f) }
        compose.waitForIdle()

        compose.onNodeWithText("März 2027").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `in the year mode a swipe steps a whole year`() {
        showYear()

        compose.onRoot().performTouchInput { swipeLeft() }
        waitForText("2028")
        compose.onRoot().performTouchInput { swipeRight() }
        compose.onRoot().performTouchInput { swipeRight() }
        waitForText("2026")
    }

    @Test
    @Config(qualifiers = "de")
    fun `swiping at the end of the calendar stays on the last month`() {
        show()
        repeat(10) { compose.onNodeWithContentDescription("Nächster Monat").performClick() }
        waitForText("Januar 2028")
        repeat(11) { compose.onNodeWithContentDescription("Nächster Monat").performClick() }
        waitForText("Dezember 2028")

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Dezember 2028").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `in a right to left layout the swipe directions are mirrored`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CalendarScreen(fixture.viewModel(), PaddingValues(), onOpenSettings = {})
            }
        }
        waitForText("März 2027")

        compose.onRoot().performTouchInput { swipeRight() }

        waitForText("April 2027")
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English calendar swipes too`() {
        show("March 2027")

        compose.onRoot().performTouchInput { swipeLeft() }

        waitForText("April 2027")
    }

    @Test
    @Config(qualifiers = "de")
    fun `a month outside the school holiday file says they are unknown`() {
        show()

        repeat(10) { compose.onNodeWithContentDescription("Nächster Monat").performClick() }

        waitForText("Januar 2028")
        // The new year's data arrives a moment after the title changes.
        waitForText("noch keine Schulferien", substring = true)
        compose
            .onNodeWithText("noch keine Schulferien", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `tapping a working day opens the sheet and marking it stores the day`() {
        show()
        waitForText("Karfreitag")

        day("Donnerstag, 25. März 2027").performClick()
        waitForText("Donnerstag, 25. März 2027")
        compose.onNodeWithText("Ganzer Tag").assertIsDisplayed()
        compose.onNodeWithText("Bis: 25.03.2027").assertIsDisplayed()
        compose.onNodeWithText("Kostet 1 Urlaubstag, danach noch 32 übrig").assertIsDisplayed()
        compose.onNodeWithText("Eintragen").performClick()

        compose.waitUntil(5_000) {
            compose
                .onAllNodes(hasContentDescription("Donnerstag, 25. März 2027, Urlaub geplant"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun `a half day shows what it costs`() {
        show()
        waitForText("Karfreitag")

        day("Donnerstag, 25. März 2027").performClick()
        compose.onNodeWithText("Halber Tag").performClick()

        compose.onNodeWithText("Kostet 0,5 Urlaubstage, danach noch 32,5 übrig").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a marked day offers Ändern and Entfernen and removing clears it`() {
        show()
        waitForText("Karfreitag")

        day("Montag, 15. März 2027").performClick()
        compose.onNodeWithText("Ändern").assertIsDisplayed()
        compose.onNodeWithText("Entfernen").performClick()

        compose.waitUntil(5_000) {
            compose
                .onAllNodes(hasContentDescription("Montag, 15. März 2027, Urlaub geplant"))
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun `a weekend day cannot be marked`() {
        show()
        waitForText("Karfreitag")

        day("Samstag, 13. März 2027").performClick()

        compose.onNodeWithText("kostet keinen Urlaub", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Eintragen").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a public holiday names itself in the sheet and cannot be marked`() {
        show()
        waitForText("Karfreitag")

        day("Freitag, 26. März 2027").performClick()

        compose.onNodeWithText("kostet keinen Urlaub", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Eintragen").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the year mode shows the whole year and the way back to a month`() {
        show()

        compose.onNodeWithText("Jahr").performClick()

        compose
            .onNode(hasContentDescription("Jahresübersicht 2027", substring = true))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Nächstes Jahr").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the year mode says too when the school holidays of that year are unknown`() {
        show()
        compose.onNodeWithText("Jahr").performClick()

        compose.onNodeWithContentDescription("Nächstes Jahr").performClick()

        waitForText("noch keine Schulferien", substring = true)
        compose
            .onNodeWithText("noch keine Schulferien", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the year grid offers a TalkBack action per month that opens it`() {
        show()
        compose.onNodeWithText("Jahr").performClick()
        waitForText("2027")

        val grid = compose.onNode(hasContentDescription("Jahresübersicht 2027", substring = true))
        val actions = grid.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(12, actions.size)
        assertEquals("April", actions[3].label)
        compose.runOnUiThread { actions[3].action() }

        waitForText("April 2027")
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English calendar has English month names and day descriptions`() {
        show("March 2027")
        waitForText("Good Friday")

        day("Friday, March 26, 2027, Public holiday: Good Friday").assertExists()
        day("Monday, March 8, 2027, Vacation taken").assertExists()
        compose.onNodeWithText("Easter holidays").performScrollTo().assertIsDisplayed()
    }

    private fun yearGrid() =
        compose.onNode(hasContentDescription("Jahresübersicht 2027", substring = true))

    private fun showYear(fontScale: Float = 1f) {
        show(fontScale = fontScale)
        compose.onNodeWithText("Jahr").performClick()
        waitForText("Jan")
    }

    @Test
    @Config(qualifiers = "de")
    fun `the year grid starts compact and the handle expands and collapses it`() {
        showYear()
        yearGrid().assertHeightIsEqualTo(YearRowsCompact * 12)

        compose
            .onNodeWithContentDescription("Jahresübersicht größer anzeigen")
            .performScrollTo()
            .performClick()
        compose.mainClock.advanceTimeBy(ANIMATION_MILLIS)
        yearGrid().assertHeightIsEqualTo(YearRowsMin * 12)

        compose
            .onNodeWithContentDescription("Jahresübersicht kleiner anzeigen")
            .performScrollTo()
            .performClick()
        compose.mainClock.advanceTimeBy(ANIMATION_MILLIS)
        yearGrid().assertHeightIsEqualTo(YearRowsCompact * 12)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h1250dp")
    fun `swiping down expands the year grid and swiping up makes it compact again`() {
        showYear()

        yearGrid().performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertExpanded()

        yearGrid().performTouchInput { swipeUp() }
        compose.waitForIdle()
        yearGrid().assertHeightIsEqualTo(YearRowsCompact * 12)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h500dp")
    fun `on a short screen a swipe up scrolls the expanded grid before it makes it compact`() {
        showYear()
        compose
            .onNodeWithContentDescription("Jahresübersicht größer anzeigen")
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        yearGrid().performTouchInput { swipeUp() }
        compose.waitForIdle()

        yearGrid().assertHeightIsEqualTo(YearRowsMin * 12)
    }

    private fun gridHeight() = yearGrid().getBoundsInRoot().height

    /** More than compact, and within what the expanded rows may be. */
    private fun assertExpanded() {
        val height = gridHeight()
        assertTrue("$height", height > YearRowsCompact * 12)
        assertTrue("$height", height >= YearRowsMin * 12 && height <= YearRowsMax * 12)
    }

    private fun expandRows() {
        compose
            .onNodeWithContentDescription("Jahresübersicht größer anzeigen")
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
    }

    private fun handle(expanded: Boolean) =
        compose.onNodeWithContentDescription(
            if (expanded) "Jahresübersicht kleiner anzeigen" else "Jahresübersicht größer anzeigen",
        )

    /**
     * Where the page ends and where the screen does, to see whether the page scrolls. The handle
     * is the last thing on the page while the rows are expanded (the legend is gone then), and the
     * frame has no padding under it then: the handle has air of its own. The rows are whole
     * pixels, so what is left is less than one pixel for each of the twelve rows.
     */
    private fun assertPageFillsScreenWithoutScrolling() {
        val handleBottom = handle(expanded = true).getBoundsInRoot().bottom
        val screen = compose.onRoot().getBoundsInRoot().height
        val slack = with(compose.density) { 12.toDp() }
        assertTrue("$handleBottom of $screen", handleBottom <= screen)
        assertTrue("$handleBottom of $screen", handleBottom >= screen - slack)
    }

    private fun legendItems() = compose.onAllNodesWithText("Geplant")

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `the expanded rows fill the screen and the page does not scroll`() {
        showYear()
        expandRows()

        assertExpanded()
        assertTrue(gridHeight() > YearRowsMin * 12 && gridHeight() < YearRowsMax * 12)
        assertPageFillsScreenWithoutScrolling()
    }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-420dpi")
    fun `the expanded rows leave less than a pixel per row on a phone with 2_6 pixels per dp`() {
        showYear()
        expandRows()

        assertExpanded()
        assertPageFillsScreenWithoutScrolling()
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `a note under the expanded rows keeps the frame padding below it and the page fits`() {
        runBlocking {
            fixture.repository.saveEntitlement(
                YearEntitlement(2026, VacationDays.of(30), VacationDays.of(0), MonthDay.of(3, 31)),
            )
        }
        showYear()
        compose.onNodeWithContentDescription("Vorheriges Jahr").performClick()
        waitForText("2026")
        expandRows()
        compose.waitForIdle()

        // 2026 has no school holidays in the test file: the note is the last thing on the page,
        // so unlike the handle it must not sit against the bottom edge.
        val note = compose.onNodeWithText("Schulferien", substring = true)
        note.assertIsDisplayed()
        val screen = compose.onRoot().getBoundsInRoot().height
        val noteBottom = note.getBoundsInRoot().bottom
        assertTrue("$noteBottom of $screen", noteBottom <= screen - 16.dp)
        // The page fills the screen to within the whole-pixel slack of the rows and does not scroll.
        val slack = with(compose.density) { 12.toDp() }
        assertTrue("$noteBottom of $screen", noteBottom >= screen - 16.dp - slack - 8.dp)
        val grid2026 =
            compose
                .onNode(hasContentDescription("Jahresübersicht 2026", substring = true))
                .getBoundsInRoot()
                .height
        assertTrue("$grid2026", grid2026 > YearRowsCompact * 12)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `the legend is under the compact year, gone while the rows are expanded and back after`() {
        showYear()
        legendItems().assertCountEquals(1)
        compose.onNodeWithText("Feiertag").assertExists()

        expandRows()
        legendItems().assertCountEquals(0)
        compose.onAllNodesWithText("Feiertag").assertCountEquals(0)
        compose.onAllNodesWithText("Schulferien").assertCountEquals(0)

        handle(expanded = true).performScrollTo().performClick()
        compose.waitForIdle()
        legendItems().assertCountEquals(1)
        yearGrid().assertHeightIsEqualTo(YearRowsCompact * 12)
        // Back where it was: the legend sits under the handle, a frame gap away.
        val below = compose.onNodeWithText("Geplant").getBoundsInRoot().top
        assertTrue(below > handle(expanded = false).getBoundsInRoot().bottom)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `the month view keeps its legend whether or not the year rows are expanded`() {
        showYear()
        expandRows()
        compose.onNodeWithText("Monat").performClick()
        waitForText("März 2027")
        compose.waitForIdle()

        compose.onAllNodesWithText("Schulferien").assertCountEquals(1)
        compose.onAllNodesWithText("Feiertag").assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `the legend with the granted entry goes too, and the rows fill the same room`() {
        showYear()
        expandRows()
        val without = gridHeight()
        handle(expanded = true).performScrollTo().performClick()
        compose.waitForIdle()
        turnGrantedDaysOn()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Genehmigt").fetchSemanticsNodes().isNotEmpty()
        }
        expandRows()

        compose.onAllNodesWithText("Genehmigt").assertCountEquals(0)
        assertEquals(without, gridHeight())
        assertPageFillsScreenWithoutScrolling()
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `the rows grow into the room of the legend in one move without a second jump`() {
        showYear()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        handle(expanded = false).performScrollTo().performClick()

        val heights = mutableListOf<Float>()
        val legendAlphas = mutableListOf<Int>()
        repeat(120) {
            compose.mainClock.advanceTimeByFrame()
            heights += gridHeight().value
            legendAlphas += legendItems().fetchSemanticsNodes().size
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val end = gridHeight().value

        // Never shrinks on the way, never overshoots the end: the target did not move while the
        // legend was leaving (it would jump up and then settle if the fit counted the legend's
        // remaining room).
        assertTrue("$heights", heights.zipWithNext().all { (a, b) -> b >= a - 0.01f })
        assertTrue("$heights", heights.all { it <= end + 0.5f })
        assertEquals(end, heights.last(), 0.5f)
        // Settled after the spring, not still chasing a target that moves with the legend: 12
        // frames of 16 ms (the row animation alone takes 11).
        assertEquals("$heights", end, heights[12], 1f)
        // The legend is there while it fades and gone at the end.
        assertTrue("$legendAlphas", legendAlphas.first() == 1 && legendAlphas.last() == 0)
        assertPageFillsScreenWithoutScrolling()
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `a larger font gives the rows less room but the page still fits`() {
        showYear(fontScale = 1.3f)
        expandRows()

        assertExpanded()
        legendItems().assertCountEquals(0)
        assertPageFillsScreenWithoutScrolling()
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `the year sliding in while expanded has no legend and keeps its rows`() {
        runBlocking {
            fixture.repository.saveEntitlement(
                YearEntitlement(2026, VacationDays.of(30), VacationDays.of(0), MonthDay.of(3, 31)),
            )
        }
        showYear()
        expandRows()

        compose.onNodeWithContentDescription("Vorheriges Jahr").performClick()
        waitForText("2026")
        compose.waitForIdle()

        // 2026 has a note about unknown school holidays under the rows, so its rows are lower than
        // 2027's; what counts is that the legend is not there and the rows are not at their least.
        legendItems().assertCountEquals(0)
        compose.onAllNodesWithText("Schulferien").assertCountEquals(0)
        val grid2026 =
            compose
                .onNode(hasContentDescription("Jahresübersicht 2026", substring = true))
                .getBoundsInRoot()
                .height
        assertTrue("$grid2026", grid2026 > YearRowsMin * 12)
    }

    @Test
    @Config(qualifiers = "de-w360dp-h600dp")
    fun `on a small phone the rows stop at the minimum and the page scrolls`() {
        showYear()
        expandRows()

        yearGrid().assertHeightIsEqualTo(YearRowsMin * 12)
    }

    @Test
    @Config(qualifiers = "de-w800dp-h360dp")
    fun `in landscape the rows stop at the minimum`() {
        showYear()
        expandRows()

        yearGrid().assertHeightIsEqualTo(YearRowsMin * 12)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h2400dp")
    fun `on a tall screen the rows stop at the maximum`() {
        showYear()
        expandRows()

        yearGrid().assertHeightIsEqualTo(YearRowsMax * 12)
    }

    @Test
    @Config(qualifiers = "de")
    fun `after a step the old month is gone and the new one is drawn`() {
        show()

        compose.onNodeWithContentDescription("Nächster Monat").performClick()
        waitForText("April 2027")
        compose.waitForIdle()

        compose.onAllNodesWithText("März 2027").assertCountEquals(0)
        compose.onAllNodesWithText("Karfreitag").assertCountEquals(0)
        compose.onNodeWithText("Osterferien").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `stepping between years with and without vacation days never mixes their pages`() {
        showYear()
        compose.mainClock.autoAdvance = false

        // 2027 has Urlaubstage, 2028 does not: the other year's empty state must not show under
        // this year's title, in either direction.
        for (button in listOf("Nächstes Jahr", "Vorheriges Jahr")) {
            compose.onNodeWithContentDescription(button).performClick()
            repeat(80) {
                Thread.sleep(5)
                compose.mainClock.advanceTimeByFrame()
                assertEmptyStatesMatchTitles()
            }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEmptyStatesMatchTitles()
        compose.onAllNodesWithText("Für 2028", substring = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "de")
    fun `switching between the year and the month view never shows both at once`() {
        showYear()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Monat").performClick()
        compose.mainClock.advanceTimeByFrame()
        // The first frame after the tap, where AnimatedContent used to keep the old page.
        compose.onAllNodesWithText("2027").assertCountEquals(0)
        compose.onAllNodesWithText("März 2027").assertCountEquals(1)
        yearGrid().assertDoesNotExist()

        compose.onNodeWithText("Jahr").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText("März 2027").assertCountEquals(0)
        compose.onAllNodesWithText("2027").assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h900dp")
    fun `the year sliding in does not start at the least row height and grow`() {
        runBlocking {
            fixture.repository.saveEntitlement(
                YearEntitlement(2026, VacationDays.of(30), VacationDays.of(0), MonthDay.of(3, 31)),
            )
        }
        showYear()
        expandRows()
        val fit2027 = gridHeight()
        compose.onNodeWithContentDescription("Vorheriges Jahr").performClick()
        waitForText("2026")
        compose.waitForIdle()
        val fit2026 =
            compose
                .onNode(hasContentDescription("Jahresübersicht 2026", substring = true))
                .getBoundsInRoot()
                .height
        val least = minOf(fit2027, fit2026)
        assertTrue("$fit2026 $fit2027", least > YearRowsMin * 12)
        compose.mainClock.autoAdvance = false

        // Both years are expanded, so while they slide neither grid may be smaller than the
        // smaller of the two.
        for (button in listOf("Nächstes Jahr", "Vorheriges Jahr")) {
            compose.onNodeWithContentDescription(button).performClick()
            repeat(80) {
                Thread.sleep(5)
                compose.mainClock.advanceTimeByFrame()
                val heights =
                    compose
                        .onAllNodes(hasContentDescription("Jahresübersicht 20", substring = true))
                        .fetchSemanticsNodes()
                        .map { it.size.height }
                assertTrue("$heights", heights.all { it >= least.value })
            }
        }
    }

    private fun assertEmptyStatesMatchTitles() {
        for (year in 2026..2028) {
            val empty =
                compose
                    .onAllNodesWithText("Für $year sind noch keine", substring = true)
                    .fetchSemanticsNodes()
            val title = compose.onAllNodesWithText("$year").fetchSemanticsNodes()
            assertTrue(
                "the empty state of $year without its title",
                empty.isEmpty() || title.isNotEmpty(),
            )
        }
    }

    private companion object {
        const val ANIMATION_MILLIS = 1_000L
    }

    private fun grantedSwitch() = compose.onNode(hasText("Genehmigt") and isToggleable())

    private fun turnGrantedDaysOn() =
        runBlocking {
            fixture.repository.saveSettings(
                UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN, grantedDays = true),
            )
        }

    @Test
    @Config(qualifiers = "de")
    fun `with granted days off there is no switch, no legend entry and no words for it`() {
        runBlocking { fixture.repository.setGranted(listOf(fixture.date("2027-03-15")), true) }
        show()
        waitForText("Karfreitag")

        day("Montag, 15. März 2027, Urlaub geplant").performClick()

        compose.onNodeWithText("Ändern").assertIsDisplayed()
        compose.onNode(hasText("Genehmigt")).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `with granted days on a granted day says so in words and the legend has the entry`() {
        runBlocking { fixture.repository.setGranted(listOf(fixture.date("2027-03-15")), true) }
        turnGrantedDaysOn()
        show()
        waitForText("Karfreitag")

        day("Montag, 15. März 2027, Urlaub genehmigt").assertExists()
        day("Dienstag, 16. März 2027, Urlaub geplant").assertExists()
        compose.onNodeWithText("Genehmigt").assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun `granting a planned day in the sheet stores it`() {
        turnGrantedDaysOn()
        show()
        waitForText("Karfreitag")

        day("Montag, 15. März 2027, Urlaub geplant").performClick()
        grantedSwitch().assertIsOff().performClick()
        compose.onNodeWithText("Ändern").performClick()

        compose.waitUntil(5_000) {
            compose
                .onAllNodes(hasContentDescription("Montag, 15. März 2027, Urlaub genehmigt"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertEquals(
            setOf(fixture.date("2027-03-15")),
            runBlocking { fixture.repository.readAll() }.granted,
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun `the sheet starts with the flag the day has and a new mark starts as planned`() {
        runBlocking { fixture.repository.setGranted(listOf(fixture.date("2027-03-15")), true) }
        turnGrantedDaysOn()
        show()
        waitForText("Karfreitag")

        day("Montag, 15. März 2027, Urlaub genehmigt").performClick()
        grantedSwitch().assertIsOn()
        compose.onNodeWithText("Entfernen").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose
                .onAllNodes(hasContentDescription("Montag, 15. März 2027, Urlaub genehmigt"))
                .fetchSemanticsNodes()
                .isEmpty()
        }
        // Marking the same day again must not bring the old flag back.
        day("Montag, 15. März 2027").performClick()
        grantedSwitch().assertIsOff()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a day that is taken has no granted switch`() {
        turnGrantedDaysOn()
        show()
        waitForText("Karfreitag")

        day("Montag, 8. März 2027, Urlaub genommen").performClick()

        compose.onNodeWithText("Ändern").assertIsDisplayed()
        grantedSwitch().assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `changing a granted day to a half day with the setting off keeps it granted`() {
        runBlocking { fixture.repository.setGranted(listOf(fixture.date("2027-03-15")), true) }
        show()
        waitForText("Karfreitag")

        day("Montag, 15. März 2027, Urlaub geplant").performClick()
        compose.onNodeWithText("Halber Tag").performClick()
        compose.onNodeWithText("Ändern").performClick()

        compose.waitUntil(5_000) {
            compose
                .onAllNodes(
                    hasContentDescription("Montag, 15. März 2027, Halber Urlaubstag geplant"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        val stored = runBlocking { fixture.repository.readAll() }
        assertEquals(DayMark.HALF, stored.marks.getValue(fixture.date("2027-03-15")))
        assertEquals(setOf(fixture.date("2027-03-15")), stored.granted)
    }

    @Test
    @Config(qualifiers = "de")
    fun `a swipe that steps the month ticks and the arrow buttons do not`() {
        show()

        compose.onRoot().performTouchInput { swipeLeft() }
        waitForText("April 2027")
        assertEquals(listOf(HapticFeedbackType.SegmentTick), haptics.performed)

        compose.onNodeWithContentDescription("Nächster Monat").performClick()
        waitForText("Mai 2027")
        assertEquals(listOf(HapticFeedbackType.SegmentTick), haptics.performed)
    }

    @Test
    @Config(qualifiers = "de")
    fun `a swipe at the end of the calendar says no`() {
        show()
        repeat(21) { compose.onNodeWithContentDescription("Nächster Monat").performClick() }
        waitForText("Dezember 2028")

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(listOf(HapticFeedbackType.Reject), haptics.performed)
        compose.onNodeWithText("Dezember 2028").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `marking and removing a day each give a confirm tick`() {
        show()
        waitForText("Karfreitag")

        day("Donnerstag, 25. März 2027").performClick()
        compose.onNodeWithText("Eintragen").performClick()
        compose.waitUntil(
            5_000,
        ) { compose.onAllNodesWithText("Eintragen").fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics.performed)

        day("Montag, 15. März 2027").performClick()
        compose.onNodeWithText("Entfernen").performClick()
        compose.waitUntil(
            5_000,
        ) { compose.onAllNodesWithText("Entfernen").fetchSemanticsNodes().isEmpty() }
        assertEquals(
            listOf(HapticFeedbackType.Confirm, HapticFeedbackType.Confirm),
            haptics.performed,
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun `the sheet slides down after Eintragen instead of vanishing at once`() {
        show()
        waitForText("Karfreitag")
        day("Donnerstag, 25. März 2027").performClick()
        waitForText("Eintragen")
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Eintragen").performClick()
        compose.mainClock.advanceTimeBy(64)

        // Half way down it still shows the day as it was tapped.
        compose.onNodeWithText("Eintragen").assertExists()
        compose.onNodeWithText("Kostet 1 Urlaubstag, danach noch 32 übrig").assertExists()

        compose.mainClock.autoAdvance = true
        compose.waitUntil(
            5_000,
        ) { compose.onAllNodesWithText("Eintragen").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    @Config(qualifiers = "de")
    fun `Entfernen keeps the button while the sheet slides down`() {
        show()
        waitForText("Karfreitag")
        day("Montag, 15. März 2027").performClick()
        waitForText("Entfernen")
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Entfernen").performClick()
        // Long enough for the removal to reach the screen, not for the slide to end.
        Thread.sleep(200)
        compose.mainClock.advanceTimeBy(64)

        compose.onNodeWithText("Entfernen").assertExists()
        compose.onNodeWithText("Ändern").assertExists()

        compose.mainClock.autoAdvance = true
        compose.waitUntil(
            5_000,
        ) { compose.onAllNodesWithText("Entfernen").fetchSemanticsNodes().isEmpty() }
    }
}
