// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class WidgetDatesTest {
    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `a date in this year has no year`() {
        assertEquals("14.04.", WidgetDates.short(date("2027-04-14"), 2027))
        assertEquals("05.01.", WidgetDates.short(date("2027-01-05"), 2027))
    }

    @Test
    fun `a date in another year has its year`() {
        // "14.01." in December would be read as this January.
        assertEquals("14.01.2028", WidgetDates.short(date("2028-01-14"), 2027))
    }

    private fun range(
        from: String,
        to: String,
        locale: Locale,
        year: Int = 2027,
    ) = WidgetDates.range(
        date(from),
        date(to),
        year,
        locale,
        if (locale.language ==
            "de"
        ) {
            "d."
        } else {
            "d"
        },
    )

    @Test
    fun `a trip inside one month is written as one range`() {
        assertEquals("14.–16. April", range("2027-04-14", "2027-04-16", Locale.GERMAN))
        assertEquals("14–16 April", range("2027-04-14", "2027-04-16", Locale.ENGLISH))
    }

    @Test
    fun `a one day trip has one day`() {
        assertEquals("14. April", range("2027-04-14", "2027-04-14", Locale.GERMAN))
        assertEquals("14 April", range("2027-04-14", "2027-04-14", Locale.ENGLISH))
    }

    @Test
    fun `a trip over a month end names both months`() {
        assertEquals("30. April – 2. Mai", range("2027-04-30", "2027-05-02", Locale.GERMAN))
        assertEquals("30 April – 2 May", range("2027-04-30", "2027-05-02", Locale.ENGLISH))
    }

    @Test
    fun `a trip over the new year names both years`() {
        assertEquals(
            "30. Dezember 2027 – 2. Januar 2028",
            range("2027-12-30", "2028-01-02", Locale.GERMAN),
        )
    }

    @Test
    fun `a trip in another year than the current one has the year`() {
        assertEquals("14.–16. April 2028", range("2028-04-14", "2028-04-16", Locale.GERMAN))
        assertEquals("30. April – 2. Mai 2028", range("2028-04-30", "2028-05-02", Locale.GERMAN))
    }
}
