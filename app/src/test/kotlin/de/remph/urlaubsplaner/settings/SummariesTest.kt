// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import de.remph.urlaubsplaner.core.balance.VacationDays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.util.Locale

class SummariesTest {
    private val range = { first: String, last: String -> "$first bis $last" }

    @Test
    fun `Monday to Friday is a range`() {
        val days = DayOfWeek.entries.take(5).toSet()

        assertEquals("Mo bis Fr", workDaysSummary(days, Locale.GERMAN, range))
    }

    @Test
    fun `days with a gap are listed one by one`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        assertEquals("Mo, Mi, Fr", workDaysSummary(days, Locale.GERMAN, range))
    }

    @Test
    fun `two days in a row are listed, not made a range`() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)

        assertEquals("Di, Mi", workDaysSummary(days, Locale.GERMAN, range))
    }

    @Test
    fun `no working days give an empty text`() {
        assertEquals("", workDaysSummary(emptySet(), Locale.GERMAN, range))
    }

    @Test
    fun `only one day is singular`() {
        val one = { text: String -> "$text Tag" }
        val other = { text: String -> "$text Tage" }

        assertEquals("1 Tag", daysWithUnit(VacationDays.ONE, Locale.GERMAN, one, other))
        assertEquals("30 Tage", daysWithUnit(VacationDays.of(30), Locale.GERMAN, one, other))
        assertEquals("0,5 Tage", daysWithUnit(VacationDays.HALF, Locale.GERMAN, one, other))
    }
}
