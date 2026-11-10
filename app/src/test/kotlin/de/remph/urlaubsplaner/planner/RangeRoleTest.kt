package de.remph.urlaubsplaner.planner

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The logic of the "Bis" picker that needs no screen: a day's role and the months to show. */
class RangeRoleTest {
    private val start = LocalDate.of(2027, 3, 25)
    private val end = LocalDate.of(2027, 4, 7)

    @Test
    fun `days before the first day cannot end the range`() {
        assertEquals(RangeRole.BEFORE_START, rangeRole(start.minusDays(1), start, end))
        assertEquals(RangeRole.BEFORE_START, rangeRole(LocalDate.of(2027, 1, 1), start, end))
    }

    @Test
    fun `the first day and the last day are the two ends`() {
        assertEquals(RangeRole.START, rangeRole(start, start, end))
        assertEquals(RangeRole.END, rangeRole(end, start, end))
    }

    @Test
    fun `days in between are inside across the month change`() {
        assertEquals(RangeRole.INSIDE, rangeRole(LocalDate.of(2027, 3, 26), start, end))
        assertEquals(RangeRole.INSIDE, rangeRole(LocalDate.of(2027, 3, 31), start, end))
        assertEquals(RangeRole.INSIDE, rangeRole(LocalDate.of(2027, 4, 1), start, end))
        assertEquals(RangeRole.INSIDE, rangeRole(LocalDate.of(2027, 4, 6), start, end))
    }

    @Test
    fun `days after the last day are open`() {
        assertEquals(RangeRole.AFTER_END, rangeRole(end.plusDays(1), start, end))
        assertEquals(RangeRole.AFTER_END, rangeRole(LocalDate.of(2027, 12, 31), start, end))
    }

    @Test
    fun `a range of one day is only the first day`() {
        assertEquals(RangeRole.START, rangeRole(start, start, start))
        assertEquals(RangeRole.AFTER_END, rangeRole(start.plusDays(1), start, start))
    }

    @Test
    fun `the picker shows the months from the first day to December of the same year`() {
        val months = pickerMonths(start)
        assertEquals(YearMonth.of(2027, 3), months.start)
        assertEquals(YearMonth.of(2027, 12), months.endInclusive)
    }

    @Test
    fun `the last month of the year has nothing after it`() {
        val months = pickerMonths(LocalDate.of(2027, 12, 20))
        assertEquals(months.start, months.endInclusive)
    }
}
