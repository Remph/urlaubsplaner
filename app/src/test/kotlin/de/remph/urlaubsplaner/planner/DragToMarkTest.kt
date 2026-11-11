package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * Drag to mark (0.11.0, item S2) on the real calendar: a long press and a drag across the days of
 * the month view, the bubble, the ticks, and the day sheet that opens with the run. Today is
 * Wednesday 10 March 2027. In that month 22 to 25 March are working days, Karfreitag (26th) and
 * Ostermontag (29th) are holidays, the 27th and 28th are the weekend, and the 30th and 31st work.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "de-w411dp-h891dp")
class DragToMarkTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var fixture: PlannerFixture
    private lateinit var model: PlannerViewModel
    private val haptics = RecordedHaptics()

    @Before
    fun open() {
        fixture = PlannerFixture()
        fixture.fill()
        model = fixture.viewModel()
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                CalendarScreen(model, PaddingValues(), onOpenSettings = {})
            }
        }
        waitForText("Karfreitag")
    }

    @After
    fun close() = fixture.close()

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    /** The bubble's text: the month card under the grid has amounts like "2 Tage" too. */
    private fun bubble(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(DRAG_BUBBLE_TAG)))

    private fun day(description: String) =
        compose.onNode(hasContentDescription(description, substring = true))

    /** The middle of a day's tile in the window, moved by [dx] and [dy] cells. */
    private fun at(
        description: String,
        dx: Int = 0,
        dy: Int = 0,
    ): Offset {
        val bounds = day(description).fetchSemanticsNode().boundsInRoot
        return Offset(
            bounds.center.x + dx * bounds.width,
            bounds.center.y + dy * (bounds.height + 4f * ROBOLECTRIC_DENSITY),
        )
    }

    /**
     * The touch goes through one node that stays (the weekday name), because the bubble is a
     * window of its own and with two windows the root is no longer a single node. Its position is
     * taken off, so the points are the window's.
     */
    private fun touch(block: TouchInjectionScope.(Offset) -> Unit) {
        val anchor = compose.onNodeWithText("Mo")
        val origin = anchor.fetchSemanticsNode().boundsInRoot.topLeft
        anchor.performTouchInput { block(origin) }
    }

    private fun longPress(
        from: Offset,
        moves: List<Offset> = emptyList(),
        release: Boolean = true,
    ) {
        touch { origin ->
            down(from - origin)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
        }
        compose.mainClock.advanceTimeBy(700)
        for (point in moves) {
            touch { origin -> moveTo(point - origin, delayMillis = 50) }
            compose.waitForIdle()
        }
        if (release) {
            touch { up() }
            compose.waitForIdle()
        }
    }

    private val ticks get() = haptics.performed.count { it == HapticFeedbackType.SegmentTick }

    @Test
    fun `a long press and a drag across days opens the sheet with the range filled in`() {
        longPress(
            at("Montag, 22. März 2027"),
            listOf(at("Dienstag, 23. März 2027"), at("Mittwoch, 24. März 2027")),
        )

        waitForText("Bis: 24.03.2027")
        compose.onNodeWithText("Montag, 22. März 2027").assertIsDisplayed()
        compose.onNodeWithText("Kostet 3 Urlaubstage", substring = true).assertIsDisplayed()
        assertEquals(
            listOf(
                HapticFeedbackType.LongPress,
                HapticFeedbackType.SegmentTick,
                HapticFeedbackType.SegmentTick,
            ),
            haptics.performed,
        )
        // Nothing is marked by letting go; the sheet's button does that.
        assertEquals(YearMonth.of(2027, 3), model.calendarMonth.value)
    }

    @Test
    fun `a bubble with the cost follows the finger and is gone when the sheet opens`() {
        longPress(
            at("Montag, 22. März 2027"),
            listOf(at("Mittwoch, 24. März 2027")),
            release = false,
        )

        compose.onNodeWithTag(DRAG_BUBBLE_TAG).assertIsDisplayed()
        bubble("3 Tage").assertIsDisplayed()

        touch { up() }
        waitForText("Bis: 24.03.2027")
        compose.onAllNodesWithTag(DRAG_BUBBLE_TAG).assertCountEquals(0)
    }

    @Test
    fun `the bubble says one day for the day of the long press`() {
        longPress(at("Montag, 22. März 2027"), release = false)

        bubble("1 Tag").assertIsDisplayed()
        touch { up() }
        waitForText("Bis: 22.03.2027")
    }

    @Test
    fun `dragging backwards makes the earlier day the first day`() {
        longPress(at("Donnerstag, 25. März 2027"), listOf(at("Dienstag, 23. März 2027")))

        waitForText("Bis: 25.03.2027")
        compose.onNodeWithText("Dienstag, 23. März 2027").assertIsDisplayed()
        compose.onNodeWithText("Kostet 3 Urlaubstage", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a holiday and a weekend inside the run cost nothing`() {
        longPress(
            at("Donnerstag, 25. März 2027"),
            listOf(at("Dienstag, 30. März 2027")),
            release = false,
        )

        // 25th and 30th: Karfreitag, the weekend and Ostermontag are in between and free.
        bubble("2 Tage").assertIsDisplayed()
        touch { up() }
        waitForText("Bis: 30.03.2027")
        compose.onNodeWithText("Kostet 2 Urlaubstage", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a backwards drag that ends on a free day opens on the nearest working day`() {
        // Sunday 28th back from Tuesday 30th: the free days at the front are cut off.
        longPress(at("Dienstag, 30. März 2027"), listOf(at("Sonntag, 28. März 2027")))

        waitForText("Bis: 30.03.2027")
        compose.onNodeWithText("Dienstag, 30. März 2027").assertIsDisplayed()
    }

    @Test
    fun `a forward drag that ends on a free day stops at the last working day`() {
        longPress(at("Donnerstag, 25. März 2027"), listOf(at("Samstag, 27. März 2027")))

        waitForText("Bis: 25.03.2027")
        compose.onNodeWithText("Donnerstag, 25. März 2027").assertIsDisplayed()
    }

    @Test
    fun `dragging back to the first day leaves one day and the normal sheet`() {
        longPress(
            at("Montag, 22. März 2027"),
            listOf(at("Dienstag, 23. März 2027"), at("Montag, 22. März 2027")),
        )

        waitForText("Bis: 22.03.2027")
        compose.onNodeWithText("Kostet 1 Urlaubstag", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a long press on a day that costs nothing says no and opens nothing`() {
        longPress(at("Samstag, 27. März 2027"))

        assertEquals(listOf(HapticFeedbackType.Reject), haptics.performed)
        compose.onAllNodesWithTag(DRAG_BUBBLE_TAG).assertCountEquals(0)
        compose.onAllNodesWithText("Samstag, 27. März 2027").assertCountEquals(0)
        compose.onAllNodesWithText("Bis:", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a long press on a holiday says no too`() {
        longPress(at("Freitag, 26. März 2027"))

        assertEquals(listOf(HapticFeedbackType.Reject), haptics.performed)
        compose.onAllNodesWithText("Bis:", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the run stops at the last day of the month with one no`() {
        // 31 March is a Wednesday: the cells to its right in the last row are blank.
        longPress(
            at("Dienstag, 30. März 2027"),
            listOf(
                at("Mittwoch, 31. März 2027"),
                at("Mittwoch, 31. März 2027", dx = 1),
                at("Mittwoch, 31. März 2027", dx = 2),
                at("Mittwoch, 31. März 2027", dx = 3),
            ),
        )

        waitForText("Bis: 31.03.2027")
        compose.onNodeWithText("Dienstag, 30. März 2027").assertIsDisplayed()
        assertEquals(
            listOf(
                HapticFeedbackType.LongPress,
                HapticFeedbackType.SegmentTick,
                HapticFeedbackType.Reject,
            ),
            haptics.performed,
        )
        // The month did not change: the pager stayed.
        assertEquals(YearMonth.of(2027, 3), model.calendarMonth.value)
    }

    @Test
    fun `dragging far sideways does not page the calendar`() {
        longPress(
            at("Montag, 22. März 2027"),
            listOf(at("Montag, 22. März 2027", dx = 5), at("Montag, 22. März 2027", dx = 8)),
        )

        waitForText("Bis: ")
        assertEquals(YearMonth.of(2027, 3), model.calendarMonth.value)
        compose.onNodeWithText("März 2027").assertIsDisplayed()
    }

    @Test
    fun `a swipe that starts on a day still pages the calendar`() {
        val start = at("Mittwoch, 24. März 2027")
        touch { origin ->
            swipe(start - origin, Offset(start.x - 400f, start.y) - origin, durationMillis = 150)
        }

        waitForText("April 2027")
        compose.waitForIdle()
        assertEquals(YearMonth.of(2027, 4), model.calendarMonth.value)
        compose.onAllNodesWithText("Bis:", substring = true).assertCountEquals(0)
        assertEquals(0, haptics.performed.count { it == HapticFeedbackType.LongPress })
    }

    @Test
    fun `a touch the system takes away opens nothing and removes the bubble`() {
        longPress(
            at("Montag, 22. März 2027"),
            listOf(at("Dienstag, 23. März 2027")),
            release = false,
        )
        compose.onNodeWithTag(DRAG_BUBBLE_TAG).assertIsDisplayed()

        touch { cancel() }
        compose.waitForIdle()

        compose.onAllNodesWithTag(DRAG_BUBBLE_TAG).assertCountEquals(0)
        compose.onAllNodesWithText("Bis:", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a tap on a day still opens the sheet for that day`() {
        touch { origin ->
            down(at("Mittwoch, 24. März 2027") - origin)
            up()
        }

        waitForText("Bis: 24.03.2027")
        assertEquals(emptyList<HapticFeedbackType>(), haptics.performed)
    }

    @Test
    fun `a day that is let go early after a short hold is a tap and not a drag`() {
        touch { origin ->
            down(at("Mittwoch, 24. März 2027") - origin)
            advanceEventTime(100)
            up()
        }

        waitForText("Bis: 24.03.2027")
        compose.onAllNodesWithTag(DRAG_BUBBLE_TAG).assertCountEquals(0)
    }

    private companion object {
        /** Robolectric's default is one pixel per dp, the 4 dp between two rows. */
        const val ROBOLECTRIC_DENSITY = 1f
    }
}
