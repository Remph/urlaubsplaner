// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.schoolholidays

import de.remph.urlaubsplaner.core.holidays.Bundesland
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SchoolHolidayDataTest {
    private val header =
        """
        # a comment
        format: 1
        version: 2026-10-25
        covers: 2026-01-01..2027-12-31
        """.trimIndent()

    private fun parse(rows: String) = SchoolHolidayData.parse("$header\n$rows\n")

    private fun date(text: String) = LocalDate.parse(text)

    private val sample =
        parse(
            """
            BY;2026-08-03;2026-09-14;Sommerferien;Summer holidays
            BY;2026-12-24;2027-01-08;Weihnachtsferien;Christmas holidays
            BY;2026-05-15;2026-05-15;Brückentag;Bridge day
            NW;2026-07-20;2026-09-01;Sommerferien;Summer holidays
            """.trimIndent(),
        )

    @Test
    fun `reads the header`() {
        assertEquals(date("2026-10-25"), sample.version)
        assertEquals(date("2026-01-01")..date("2027-12-31"), sample.coverage)
    }

    @Test
    fun `a day inside a holiday finds it, both end days included`() {
        for (day in listOf("2026-08-03", "2026-08-20", "2026-09-14")) {
            val found = sample.on(Bundesland.BAYERN, date(day))
            assertEquals(listOf("Sommerferien"), found.map { it.nameDe }, day)
        }
    }

    @Test
    fun `the days just outside a holiday find nothing`() {
        assertEquals(emptyList<SchoolHoliday>(), sample.on(Bundesland.BAYERN, date("2026-08-02")))
        assertEquals(emptyList<SchoolHoliday>(), sample.on(Bundesland.BAYERN, date("2026-09-15")))
    }

    @Test
    fun `a holiday is only found for its own Land`() {
        assertEquals(emptyList<SchoolHoliday>(), sample.on(Bundesland.BERLIN, date("2026-08-10")))
        assertEquals(1, sample.on(Bundesland.NORDRHEIN_WESTFALEN, date("2026-08-10")).size)
    }

    @Test
    fun `a holiday over the year end is found on both sides`() {
        assertEquals(
            "Weihnachtsferien",
            sample.on(Bundesland.BAYERN, date("2026-12-31")).single().nameDe,
        )
        assertEquals(
            "Weihnachtsferien",
            sample.on(Bundesland.BAYERN, date("2027-01-03")).single().nameDe,
        )
    }

    @Test
    fun `a single school-free day is a holiday of one day`() {
        val day = sample.on(Bundesland.BAYERN, date("2026-05-15")).single()
        assertEquals("Brückentag", day.nameDe)
        assertEquals("Bridge day", day.nameEn)
        assertEquals(day.from, day.to)
    }

    @Test
    fun `between finds every holiday that overlaps, also at the edges`() {
        val found = sample.between(Bundesland.BAYERN, date("2026-09-14"), date("2026-12-24"))
        assertEquals(listOf("Sommerferien", "Weihnachtsferien"), found.map { it.nameDe })
        val none = sample.between(Bundesland.BAYERN, date("2026-09-15"), date("2026-12-23"))
        assertEquals(emptyList<SchoolHoliday>(), none)
    }

    @Test
    fun `between lists holidays by start date whatever the order in the file`() {
        val found = sample.between(Bundesland.BAYERN, date("2026-01-01"), date("2027-12-31"))
        assertEquals(
            listOf("Brückentag", "Sommerferien", "Weihnachtsferien"),
            found.map { it.nameDe },
        )
    }

    @Test
    fun `between refuses a range that ends before it starts`() {
        assertThrows(IllegalArgumentException::class.java) {
            sample.between(Bundesland.BAYERN, date("2026-09-01"), date("2026-08-01"))
        }
    }

    @Test
    fun `covers says whether the file has data, not whether there is a holiday`() {
        assertTrue(sample.covers(date("2026-01-01")))
        assertTrue(sample.covers(date("2027-12-31")))
        assertFalse(sample.covers(date("2025-12-31")))
        assertFalse(sample.covers(date("2028-01-01")))
        assertTrue(sample.covers(date("2026-06-01"), date("2027-06-01")))
        assertFalse(sample.covers(date("2027-06-01"), date("2028-06-01")))
    }

    @Test
    fun `comments, blank lines, Windows line ends and a byte order mark are fine`() {
        val text =
            "﻿# c\r\nformat: 1\r\nversion: 2026-10-25\r\ncovers: 2026-01-01..2026-12-31\r\n\r\n" +
                "BY;2026-08-03;2026-09-14;Sommerferien;Summer holidays\r\n"
        val data = SchoolHolidayData.parse(text)
        assertEquals(1, data.on(Bundesland.BAYERN, date("2026-08-03")).size)
    }

    @Test
    fun `a header without any holidays is valid`() {
        assertEquals(
            emptyList<SchoolHoliday>(),
            parse("").between(Bundesland.BAYERN, date("2026-01-01"), date("2026-12-31")),
        )
    }

    @Test
    fun `the credit names OpenHolidays and the ODbL`() {
        assertTrue("https://www.openholidaysapi.org" in SchoolHolidayData.CREDIT)
        assertTrue("Open Database License (ODbL)" in SchoolHolidayData.CREDIT)
    }

    private fun assertRefused(
        text: String,
        mentions: String,
    ) {
        val e =
            assertThrows(SchoolHolidayFormatException::class.java) { SchoolHolidayData.parse(text) }
        assertTrue(mentions in e.message.orEmpty(), "'$mentions' not in '${e.message}'")
    }

    @Test
    fun `a missing or unknown format is refused`() {
        assertRefused("version: 2026-10-25\ncovers: 2026-01-01..2026-12-31\n", "format")
        assertRefused("format: 2\nversion: 2026-10-25\ncovers: 2026-01-01..2026-12-31\n", "format")
    }

    @Test
    fun `a missing version or coverage is refused`() {
        assertRefused("format: 1\ncovers: 2026-01-01..2026-12-31\n", "version")
        assertRefused("format: 1\nversion: 2026-10-25\n", "covers")
    }

    @Test
    fun `a bad coverage is refused`() {
        assertRefused("format: 1\nversion: 2026-10-25\ncovers: 2026-01-01\n", "covers")
        assertRefused("format: 1\nversion: 2026-10-25\ncovers: 2026-12-31..2026-01-01\n", "covers")
        assertRefused("format: 1\nversion: 2026-10-25\ncovers: 2026-01-01..soon\n", "bad date")
    }

    @Test
    fun `a header line that appears twice is refused`() {
        // The header takes lines 1 to 4, so the repeated line is line 5.
        assertRefused("$header\nformat: 1\n", "line 5: 'format' appears twice")
    }

    @Test
    fun `a damaged holiday line is refused and the message names the line`() {
        // The header takes lines 1 to 4, so the damaged row is line 5.
        assertRefused(
            "$header\nXX;2026-08-03;2026-09-14;Sommerferien;Summer holidays\n",
            "line 5: unknown Land",
        )
        assertRefused(
            "$header\nBY;2026-08-32;2026-09-14;Sommerferien;Summer holidays\n",
            "line 5: bad date",
        )
        assertRefused(
            "$header\nBY;2026-09-14;2026-08-03;Sommerferien;Summer holidays\n",
            "line 5: ends before",
        )
        assertRefused(
            "$header\nBY;2026-08-03;2026-09-14;Sommerferien\n",
            "line 5: expected 5 fields",
        )
        assertRefused("$header\nBY;2026-08-03;2026-09-14;;Summer holidays\n", "line 5: empty name")
        assertRefused(
            "$header\nBY;2026-08-03;2026-09-14;Sommerferien;Summer;holidays\n",
            "line 5: expected 5 fields",
        )
    }
}
