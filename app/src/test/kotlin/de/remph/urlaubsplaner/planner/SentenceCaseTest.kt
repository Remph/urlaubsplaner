// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHoliday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** English school holiday names come in Title Case from the data file and are shown in sentence case. */
class SentenceCaseTest {
    @Test
    fun `generic words lose their capital and feasts keep theirs`() {
        assertEquals("Easter holidays", "Easter Holidays".sentenceCase())
        assertEquals("Winter holidays", "Winter Holidays".sentenceCase())
        assertEquals("Christmas holidays", "Christmas Holidays".sentenceCase())
        assertEquals("Pentecost holidays", "Pentecost Holidays".sentenceCase())
        assertEquals("Mid-year holidays", "Mid-Year Holidays".sentenceCase())
        assertEquals("Day off from school", "Day off from school".sentenceCase())
    }

    @Test
    fun `named days and dates keep their capitals`() {
        assertEquals("Repentance Day", "Repentance Day".sentenceCase())
        assertEquals("Reformation Day", "Reformation Day".sentenceCase())
        assertEquals("Maundy Thursday", "Maundy Thursday".sentenceCase())
        assertEquals("Day after Ascension", "Day after Ascension".sentenceCase())
        assertEquals("Days after the 3 October", "Days after the 3 October".sentenceCase())
        assertEquals("Day before 1 May", "Day before 1 May".sentenceCase())
        assertEquals("Bridge day", "Bridge Day".sentenceCase())
    }

    @Test
    fun `the English name is shown in sentence case and the German name is untouched`() {
        val holiday =
            SchoolHoliday(
                Bundesland.BERLIN,
                LocalDate.of(2027, 3, 22),
                LocalDate.of(2027, 4, 3),
                "Osterferien",
                "Easter Holidays",
            )

        assertEquals("Easter holidays", holiday.nameIn(Locale.ENGLISH))
        assertEquals("Osterferien", holiday.nameIn(Locale.GERMAN))
    }
}
