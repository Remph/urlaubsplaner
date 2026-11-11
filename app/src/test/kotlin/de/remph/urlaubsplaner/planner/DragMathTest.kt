package de.remph.urlaubsplaner.planner

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/**
 * The maths of drag to mark that needs no screen: which day is under the finger, the run from two
 * days, what the phone feels, which days of the run cost something, and the bubble's place.
 */
@RunWith(AndroidJUnit4::class)
class DragMathTest {
    private val fixture = PlannerFixture()

    @After
    fun close() = fixture.close()

    private fun date(day: Int) = LocalDate.of(2027, 4, day)

    // April 2027 starts on a Thursday (three blank cells) and has 30 days: five rows, the last
    // holds 26 to 30 in its first five columns.
    private val april = YearMonth.of(2027, 4)

    @Test
    fun `a cell of the grid is the day written in it`() {
        assertEquals(DragTarget(date(1), pastEdge = false), dragTarget(april, row = 0, column = 3))
        assertEquals(DragTarget(date(9), pastEdge = false), dragTarget(april, row = 1, column = 4))
        assertEquals(DragTarget(date(30), pastEdge = false), dragTarget(april, row = 4, column = 4))
    }

    @Test
    fun `a blank cell before the first day is past the edge and stays on the first day`() {
        assertEquals(DragTarget(date(1), pastEdge = true), dragTarget(april, row = 0, column = 0))
        assertEquals(DragTarget(date(1), pastEdge = true), dragTarget(april, row = 0, column = 2))
    }

    @Test
    fun `a blank cell after the last day is past the edge and stays on the last day`() {
        assertEquals(DragTarget(date(30), pastEdge = true), dragTarget(april, row = 4, column = 5))
        assertEquals(DragTarget(date(30), pastEdge = true), dragTarget(april, row = 4, column = 6))
    }

    @Test
    fun `rows above the grid and under it are past the edge`() {
        assertEquals(DragTarget(date(1), pastEdge = true), dragTarget(april, row = -1, column = 4))
        assertEquals(DragTarget(date(30), pastEdge = true), dragTarget(april, row = 5, column = 1))
    }

    // 700 px wide: columns of 100 px. The weekday names end at 30 px, the rows are 56 px apart.
    private fun at(
        x: Float,
        y: Float,
    ) = dragTargetAt(april, x, y, width = 700f, rowsTop = 30f, rowPitch = 56f)

    @Test
    fun `a point of the grid finds its column and row`() {
        assertEquals(DragTarget(date(1), false), at(x = 350f, y = 40f))
        assertEquals(DragTarget(date(11), false), at(x = 650f, y = 40f + 56f))
        assertEquals(DragTarget(date(19), false), at(x = 20f, y = 30f + 3 * 56f + 10f))
    }

    @Test
    fun `the gap under a row belongs to that row`() {
        // The row is 52 px high and 4 px of gap follow: 82 to 86 is still the first row.
        assertEquals(DragTarget(date(1), false), at(x = 350f, y = 85f))
        assertEquals(DragTarget(date(8), false), at(x = 350f, y = 87f))
    }

    @Test
    fun `a finger above the weekday names or under the grid is past the edge`() {
        assertTrue(at(x = 350f, y = 5f).pastEdge)
        assertTrue(at(x = 350f, y = 30f + 5 * 56f + 1f).pastEdge)
    }

    @Test
    fun `a finger beyond the sides stays in the first or the last column`() {
        // Second row: the first column is the 5th, the last the 11th.
        assertEquals(DragTarget(date(5), false), at(x = -400f, y = 100f))
        assertEquals(DragTarget(date(11), false), at(x = 4000f, y = 100f))
    }

    @Test
    fun `a run goes from the earlier day to the later one`() {
        val forward = MarkDrag(date(12)).apply { moveTo(DragTarget(date(15), false)) }
        val backward = MarkDrag(date(12)).apply { moveTo(DragTarget(date(8), false)) }

        assertEquals(DayRange(date(12), date(15)), forward.range)
        assertEquals(DayRange(date(8), date(12)), backward.range)
        assertEquals(DayRange(date(12), date(12)), MarkDrag(date(12)).range)
    }

    @Test
    fun `dragging across the first day turns the run around`() {
        val drag = MarkDrag(date(12))
        drag.moveTo(DragTarget(date(14), false))
        drag.moveTo(DragTarget(date(10), false))

        assertEquals(DayRange(date(10), date(12)), drag.range)
    }

    @Test
    fun `back on the first day it is one day again`() {
        val drag = MarkDrag(date(12))
        drag.moveTo(DragTarget(date(14), false))
        drag.moveTo(DragTarget(date(12), false))

        assertEquals(DayRange(date(12), date(12)), drag.range)
    }

    @Test
    fun `every new day ticks and staying on a day does not`() {
        val drag = MarkDrag(date(12))

        assertEquals(DragFeedback.TICK, drag.moveTo(DragTarget(date(13), false)))
        assertEquals(DragFeedback.NONE, drag.moveTo(DragTarget(date(13), false)))
        assertEquals(DragFeedback.TICK, drag.moveTo(DragTarget(date(12), false)))
    }

    @Test
    fun `going past the edge says no once and not again while the finger stays there`() {
        val drag = MarkDrag(date(28))
        assertEquals(DragFeedback.TICK, drag.moveTo(DragTarget(date(30), false)))

        assertEquals(DragFeedback.REJECT, drag.moveTo(DragTarget(date(30), true)))
        assertEquals(DragFeedback.NONE, drag.moveTo(DragTarget(date(30), true)))
        assertEquals(DragFeedback.NONE, drag.moveTo(DragTarget(date(30), true)))
        assertEquals(date(30), drag.target)
    }

    @Test
    fun `the finger coming back from the edge and going out again says no again`() {
        val drag = MarkDrag(date(28))
        drag.moveTo(DragTarget(date(30), true))

        assertEquals(DragFeedback.NONE, drag.moveTo(DragTarget(date(30), false)))
        assertEquals(DragFeedback.REJECT, drag.moveTo(DragTarget(date(30), true)))
    }

    @Test
    fun `leaving the edge for another day ticks`() {
        val drag = MarkDrag(date(28))
        drag.moveTo(DragTarget(date(30), true))

        assertEquals(DragFeedback.TICK, drag.moveTo(DragTarget(date(29), false)))
    }

    private fun view(marks: Map<LocalDate, DayMark> = emptyMap()): YearView {
        val settings = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN)
        return YearView(
            year = 2027,
            today = fixture.date("2027-03-10"),
            settings = settings,
            entitlement = YearEntitlement(2027, VacationDays.of(30), VacationDays.ZERO),
            marks = marks,
            calculator =
                settings.publicHolidayCheck()?.let {
                    VacationCalculator(fixture.clock, settings.schedule, it)
                },
            schoolData = fixture.schoolHolidays,
        )
    }

    private fun march(day: Int) = LocalDate.of(2027, 3, day)

    // March 2027: 22 to 25 work, Karfreitag 26, weekend 27 and 28, Ostermontag 29, 30 and 31 work.
    @Test
    fun `a run is cut to its first and last working day`() {
        val view = view()

        assertEquals(
            DayRange(march(22), march(25)),
            view.costlyBounds(DayRange(march(20), march(26))),
        )
        assertEquals(
            DayRange(march(25), march(30)),
            view.costlyBounds(DayRange(march(25), march(30))),
        )
        assertEquals(
            DayRange(march(30), march(30)),
            view.costlyBounds(DayRange(march(27), march(30))),
        )
    }

    @Test
    fun `a run of days that cost nothing has no bounds`() {
        assertNull(view().costlyBounds(DayRange(march(26), march(29))))
    }

    @Test
    fun `the bubble counts the working days of the run once and a marked day too`() {
        val plain = view()
        val marked = view(mapOf(march(23) to DayMark.HALF))

        assertEquals(VacationDays.of(4), plain.dragCost(DayRange(march(22), march(25))))
        assertEquals(VacationDays.of(2), plain.dragCost(DayRange(march(25), march(30))))
        assertEquals(VacationDays.ZERO, plain.dragCost(DayRange(march(27), march(28))))
        // The sheet would mark the whole days again, so the bubble counts them whole.
        assertEquals(VacationDays.of(4), marked.dragCost(DayRange(march(22), march(25))))
    }

    @Test
    fun `the bubble sits above the finger`() {
        val place =
            bubblePosition(IntOffset(500, 600), gap = 100, IntSize(1080, 2000), IntSize(200, 80))

        assertEquals(IntOffset(400, 420), place)
    }

    @Test
    fun `the bubble goes under the finger when there is no room above`() {
        val place =
            bubblePosition(IntOffset(500, 150), gap = 100, IntSize(1080, 2000), IntSize(200, 80))

        assertEquals(IntOffset(400, 250), place)
    }

    @Test
    fun `the bubble stays inside the window at the sides`() {
        val window = IntSize(1080, 2000)
        val bubble = IntSize(200, 80)

        assertEquals(0, bubblePosition(IntOffset(10, 600), 100, window, bubble).x)
        assertEquals(880, bubblePosition(IntOffset(1075, 600), 100, window, bubble).x)
    }

    private val scheme = lightColorScheme()
    private val colors =
        DayColors(
            holiday = Color(0xFFF7E3B5),
            onHoliday = Color(0xFF5A3C0A),
            school = Color(0xFFE6DFF7),
            schoolBar = Color(0xFF6B50B5),
            taken = Color.Blue,
            onTaken = Color.White,
            planned = Color.Blue,
            granted = Color.Cyan,
            onGranted = Color.Black,
            grantedHalf = Color.Blue,
            weekend = Color.Gray,
        )

    private fun look(holiday: Boolean = false) =
        DayLook(
            holiday = holiday,
            school = false,
            mark = null,
            taken = false,
            granted = false,
            today = false,
            workDay = true,
        )

    private val other = Color.Magenta

    @Test
    fun `the two ends of a run are the strongest thing a day can be`() {
        for (role in listOf(RangeRole.START, RangeRole.END)) {
            assertEquals(
                scheme.primary,
                rangeFill(role, look(holiday = true), colors, scheme, other),
            )
            assertEquals(scheme.onPrimary, rangeText(role, look(), colors, scheme, other))
        }
    }

    @Test
    fun `a holiday inside the run stays gold and gets a ring`() {
        val holiday = look(holiday = true)

        assertEquals(colors.holiday, rangeFill(RangeRole.INSIDE, holiday, colors, scheme, other))
        assertEquals(colors.onHoliday, rangeText(RangeRole.INSIDE, holiday, colors, scheme, other))
        assertTrue(rangeRing(RangeRole.INSIDE, holiday))
        assertFalse(rangeRing(RangeRole.INSIDE, look()))
        assertFalse(rangeRing(RangeRole.START, holiday))
    }

    @Test
    fun `a plain day inside the run is tinted`() {
        assertEquals(
            scheme.primaryContainer,
            rangeFill(RangeRole.INSIDE, look(), colors, scheme, other),
        )
        assertEquals(
            scheme.onPrimaryContainer,
            rangeText(RangeRole.INSIDE, look(), colors, scheme, other),
        )
    }

    @Test
    fun `a day outside the run keeps the colour its caller gives it but a holiday stays gold`() {
        assertEquals(other, rangeFill(RangeRole.AFTER_END, look(), colors, scheme, other))
        assertEquals(other, rangeText(RangeRole.BEFORE_START, look(), colors, scheme, other))
        assertEquals(
            colors.holiday,
            rangeFill(RangeRole.AFTER_END, look(holiday = true), colors, scheme, other),
        )
    }

    @Test
    fun `only the days from the first to the last are in the range`() {
        assertTrue(RangeRole.START.inRange)
        assertTrue(RangeRole.INSIDE.inRange)
        assertTrue(RangeRole.END.inRange)
        assertFalse(RangeRole.BEFORE_START.inRange)
        assertFalse(RangeRole.AFTER_END.inRange)
    }
}
