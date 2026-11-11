package de.remph.urlaubsplaner.planner

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

class CalendarPagesTest {
    private val range = YearMonth.of(2019, 1)..YearMonth.of(2028, 12)
    private val months = CalendarPages(CalendarMode.MONTH, range)
    private val years = CalendarPages(CalendarMode.YEAR, range)

    @Test
    fun `there is a page for every month and for every year of the range`() {
        assertEquals(120, months.count)
        assertEquals(10, years.count)
    }

    @Test
    fun `a month is its page and back`() {
        assertEquals(0, months.indexOf(YearMonth.of(2019, 1)))
        assertEquals(26, months.indexOf(YearMonth.of(2021, 3)))
        assertEquals(119, months.indexOf(YearMonth.of(2028, 12)))
        for (index in 0 until months.count) {
            assertEquals(index, months.indexOf(months.monthAt(index, YearMonth.of(2027, 3))))
        }
    }

    @Test
    fun `the year of a month page is the year of its month`() {
        assertEquals(2019, months.yearAt(11))
        assertEquals(2020, months.yearAt(12))
        assertEquals(2028, months.yearAt(119))
    }

    @Test
    fun `a year page keeps the month of the year the calendar is at`() {
        val march = YearMonth.of(2027, 3)

        assertEquals(8, years.indexOf(march))
        assertEquals(YearMonth.of(2028, 3), years.monthAt(9, march))
        assertEquals(YearMonth.of(2019, 3), years.monthAt(0, march))
        assertEquals(2028, years.yearAt(9))
    }

    @Test
    fun `the titles are the month and year, or only the year`() {
        assertEquals(
            "März 2027",
            months.title(months.indexOf(YearMonth.of(2027, 3)), Locale.GERMAN),
        )
        assertEquals(
            "March 2027",
            months.title(months.indexOf(YearMonth.of(2027, 3)), Locale.ENGLISH),
        )
        assertEquals("2027", years.title(8, Locale.GERMAN))
    }

    @Test
    fun `a calendar of a single year has one year page`() {
        val one = CalendarPages(CalendarMode.YEAR, YearMonth.of(2027, 1)..YearMonth.of(2027, 12))

        assertEquals(1, one.count)
        assertEquals(0, one.indexOf(YearMonth.of(2027, 6)))
    }
}
