// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.MonthDay
import java.util.Locale

class AmountTextTest {
    private val german = Locale.GERMANY
    private val english = Locale.UK

    @Test
    fun `German shows a decimal comma and English a point`() {
        val days = VacationDays.of(12.5)

        assertEquals("12,5", AmountText.format(days, german))
        assertEquals("12.5", AmountText.format(days, english))
    }

    @Test
    fun `whole days and zero have no decimals`() {
        assertEquals("30", AmountText.format(VacationDays.of(30), german))
        assertEquals("0", AmountText.format(VacationDays.ZERO, german))
        assertEquals("0,5", AmountText.format(VacationDays.HALF, german))
    }

    @Test
    fun `a negative amount keeps its sign`() {
        assertEquals("-1,5", AmountText.format(VacationDays.of(-1.5), german))
    }

    @Test
    fun `typed amounts accept comma and point`() {
        assertEquals(VacationDays.of(12.5), AmountText.parseDays("12,5"))
        assertEquals(VacationDays.of(12.5), AmountText.parseDays("12.5"))
        assertEquals(VacationDays.of(30), AmountText.parseDays(" 30 "))
        assertEquals(VacationDays.HALF, AmountText.parseDays("0,5"))
        assertEquals(VacationDays.ZERO, AmountText.parseDays("0"))
    }

    @Test
    fun `only half steps are accepted`() {
        assertNull(AmountText.parseDays("12,3"))
        assertNull(AmountText.parseDays("12,25"))
        assertEquals(VacationDays.of(12), AmountText.parseDays("12,0"))
    }

    @Test
    fun `text that is not a plain amount is refused`() {
        for (text in listOf(
            "",
            " ",
            "abc",
            "-1",
            "+1",
            "1e1",
            "12,",
            ",5",
            "1.000",
            "1 2",
            "12,5,5",
        )) {
            assertNull(AmountText.parseDays(text), "\"$text\"")
        }
    }

    @Test
    fun `more than a year of days is refused`() {
        assertEquals(VacationDays.of(366), AmountText.parseDays("366"))
        assertNull(AmountText.parseDays("366,5"))
        assertNull(AmountText.parseDays("367"))
        assertNull(AmountText.parseDays("999"))
    }

    @Test
    fun `the Verfall day is written with two digits`() {
        assertEquals("31.03.", AmountText.formatDayMonth(MonthDay.of(3, 31)))
        assertEquals("05.01.", AmountText.formatDayMonth(MonthDay.of(1, 5)))
    }

    @Test
    fun `a Verfall date in a later year is written with the year`() {
        assertEquals("31.03.2028", AmountText.formatDate(java.time.LocalDate.of(2028, 3, 31)))
    }

    @Test
    fun `the Verfall day is read in short and long forms`() {
        val march31 = MonthDay.of(3, 31)
        for (text in listOf("31.03.", "31.3.", "31.03", "31.3", " 31.03. ")) {
            assertEquals(march31, AmountText.parseDayMonth(text), "\"$text\"")
        }
    }

    @Test
    fun `a day that does not exist is refused`() {
        for (text in listOf(
            "",
            "31.04.",
            "32.01.",
            "0.1.",
            "1.13.",
            "1.0.",
            "1-1",
            "2026-03-31",
            "31",
        )) {
            assertNull(AmountText.parseDayMonth(text), "\"$text\"")
        }
    }

    @Test
    fun `29 February is allowed because the year is not known`() {
        assertEquals(MonthDay.of(2, 29), AmountText.parseDayMonth("29.02."))
    }
}
