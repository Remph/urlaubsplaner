// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.schoolholidays

import de.remph.urlaubsplaner.core.holidays.Bundesland
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Reads the real bundled file (data/school-holidays/school-holidays.txt, added to the test
// resources in core/build.gradle.kts). When the file is renewed, these tests are what tells you
// it still has the shape the app relies on.
class BundledSchoolHolidaysTest {
    companion object {
        val data: SchoolHolidayData by lazy {
            val stream =
                checkNotNull(
                    BundledSchoolHolidaysTest::class.java.getResourceAsStream(
                        "/school-holidays.txt",
                    ),
                ) {
                    "school-holidays.txt is not on the test classpath"
                }
            SchoolHolidayData.parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        val years = 2026..2029
    }

    private fun date(text: String) = LocalDate.parse(text)

    private fun yearOf(
        land: Bundesland,
        year: Int,
    ) = data.between(land, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    @Test
    fun `the file covers 2026 to 2029`() {
        assertEquals(date("2026-01-01")..date("2029-12-31"), data.coverage)
    }

    @Test
    fun `every Land has a summer holiday of about six weeks in every year`() {
        for (land in Bundesland.entries) {
            for (year in years) {
                val summer = yearOf(land, year).filter { it.nameDe == "Sommerferien" }
                assertEquals(1, summer.size, "$land $year")
                val days = ChronoUnit.DAYS.between(summer.single().from, summer.single().to) + 1
                assertTrue(days in 35..48, "$land $year has $days summer days")
                assertTrue(summer.single().from.year == year, "$land $year starts in $year")
            }
        }
    }

    @Test
    fun `every Land has Weihnachtsferien around the end of every year`() {
        for (land in Bundesland.entries) {
            for (year in years) {
                val christmas = data.on(land, LocalDate.of(year, 12, 27))
                assertEquals(listOf("Weihnachtsferien"), christmas.map { it.nameDe }, "$land $year")
            }
        }
    }

    @Test
    fun `every Land has an autumn holiday in every year`() {
        for (land in Bundesland.entries) {
            for (year in years) {
                val autumn = yearOf(land, year).filter { it.nameDe == "Herbstferien" }
                assertTrue(autumn.isNotEmpty(), "$land $year")
                assertTrue(autumn.all { it.from.monthValue in 9..11 }, "$land $year: $autumn")
            }
        }
    }

    @Test
    fun `no two holidays of one Land with the same name overlap`() {
        for (land in Bundesland.entries) {
            val all =
                data.between(
                    land,
                    data.coverage.start.minusYears(1),
                    data.coverage.endInclusive.plusYears(1),
                )
            for ((i, a) in all.withIndex()) {
                for (b in all.drop(i + 1)) {
                    if (a.nameDe ==
                        b.nameDe
                    ) {
                        assertFalse(a.overlaps(b.from, b.to), "$land: $a overlaps $b")
                    }
                }
            }
        }
    }

    @Test
    fun `no entry is outside the years the file claims, apart from the Christmas holidays`() {
        for (land in Bundesland.entries) {
            for (h in data.between(land, LocalDate.MIN.plusDays(1), LocalDate.MAX.minusDays(1))) {
                assertTrue(
                    h.to >= data.coverage.start && h.from <= data.coverage.endInclusive,
                    "$h",
                )
                if (h.from < data.coverage.start || h.to > data.coverage.endInclusive) {
                    assertEquals("Weihnachtsferien", h.nameDe, "$h")
                }
            }
        }
    }

    // Spot checks against the KMK tables (Sommerferienregelung 2025 bis 2030 and the yearly
    // Ferienkalender, checked 2026-10-25). KMK gives first and last holiday day, like the file.
    @Test
    fun `summer holidays match the KMK table`() {
        fun summer(
            land: Bundesland,
            year: Int,
        ) = yearOf(land, year)
            .single {
                it.nameDe ==
                    "Sommerferien"
            }.let { it.from to it.to }
        assertEquals(date("2026-08-03") to date("2026-09-14"), summer(Bundesland.BAYERN, 2026))
        assertEquals(date("2026-07-09") to date("2026-08-22"), summer(Bundesland.BERLIN, 2026))
        assertEquals(
            date("2027-07-19") to date("2027-08-31"),
            summer(Bundesland.NORDRHEIN_WESTFALEN, 2027),
        )
        assertEquals(
            date("2028-07-27") to date("2028-09-09"),
            summer(Bundesland.BADEN_WUERTTEMBERG, 2028),
        )
        assertEquals(date("2029-07-02") to date("2029-08-10"), summer(Bundesland.HAMBURG, 2029))
        assertEquals(
            date("2029-06-23") to date("2029-08-03"),
            summer(Bundesland.SCHLESWIG_HOLSTEIN, 2029),
        )
    }

    @Test
    fun `Mecklenburg-Vorpommern has the dates of the general schools`() {
        // The vocational schools (BBS) have Sommerferien 2026-07-13..2026-08-29; they are left out.
        val summer =
            yearOf(Bundesland.MECKLENBURG_VORPOMMERN, 2026).single {
                it.nameDe ==
                    "Sommerferien"
            }
        assertEquals(date("2026-07-13") to date("2026-08-22"), summer.from to summer.to)
    }

    @Test
    fun `Schleswig-Holstein has the mainland dates, not the island ones`() {
        val summer =
            yearOf(Bundesland.SCHLESWIG_HOLSTEIN, 2026).single {
                it.nameDe ==
                    "Sommerferien"
            }
        assertEquals(date("2026-07-04") to date("2026-08-15"), summer.from to summer.to)
    }

    @Test
    fun `the English names are filled in`() {
        assertEquals(
            "Summer Holidays",
            data.on(Bundesland.BAYERN, date("2026-08-10")).single().nameEn,
        )
        for (land in Bundesland.entries) {
            for (h in data.between(land, data.coverage.start, data.coverage.endInclusive)) {
                assertTrue(h.nameDe.isNotBlank() && h.nameEn.isNotBlank(), "$h")
            }
        }
    }
}
