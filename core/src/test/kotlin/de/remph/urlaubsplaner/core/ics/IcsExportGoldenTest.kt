// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.ics

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * Pins the whole file for big, mixed sets of marks, so a faster way to build it (walking the
 * sorted marks once, tech audit S03) is known to write the same bytes. The hashes were made with
 * the quadratic code that came before; they change only when the file format does.
 */
class IcsExportGoldenTest {
    private val clock = Clock.fixed(Instant.parse("2027-03-10T08:30:15Z"), ZoneOffset.UTC)
    private val check = UserSettings(land = Bundesland.BAYERN).publicHolidayCheck()!!
    private val schedule = WorkSchedule()
    private val export = IcsExport(clock, VacationCalculator(clock, schedule, check))

    /** Seeded, so the same every run: about a third of the days from 2024 to 2029, some half, some granted. */
    private fun mixed(seed: Long): MarkedDays {
        val random = Random(seed)
        val marks = mutableMapOf<LocalDate, DayMark>()
        val granted = mutableSetOf<LocalDate>()
        var day = LocalDate.of(2024, 1, 1)
        while (day.year <= 2029) {
            if (random.nextInt(3) == 0) {
                marks[day] = if (random.nextInt(5) == 0) DayMark.HALF else DayMark.FULL
                if (random.nextBoolean()) granted += day
            }
            day = day.plusDays(1)
        }
        return MarkedDays(marks, granted)
    }

    private fun sha256(text: String) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `a big mixed year set writes the same file as before`() {
        val days = mixed(seed = 7)
        val results =
            listOf(
                IcsLanguage.GERMAN to true,
                IcsLanguage.ENGLISH to true,
                IcsLanguage.GERMAN to false,
            ).map { (language, withStatus) ->
                export.tripCount(days, withStatus) to
                    sha256(export.encode(days, language, withStatus))
            }

        assertEquals(EXPECTED, results)
    }

    @Test
    fun `a second seed with a four day week writes the same file as before`() {
        val fourDays =
            WorkSchedule(
                workingDays =
                    setOf(
                        java.time.DayOfWeek.MONDAY,
                        java.time.DayOfWeek.TUESDAY,
                        java.time.DayOfWeek.THURSDAY,
                        java.time.DayOfWeek.FRIDAY,
                    ),
            )
        val own = IcsExport(clock, VacationCalculator(clock, fourDays, check))
        val days = mixed(seed = 99)

        val result = own.tripCount(days, true) to sha256(own.encode(days, IcsLanguage.GERMAN, true))

        assertEquals(EXPECTED_FOUR_DAYS, result)
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `forty thousand one-day trips are written fast`() {
        // Every other day from 2019 on: each mark is a trip of its own (the day between is a
        // working day that costs). The quadratic code needed about 18 seconds for 41,000 trips.
        val marks = mutableMapOf<LocalDate, DayMark>()
        var day = LocalDate.of(2019, 1, 1)
        while (marks.size < 41_000) {
            marks[day] = DayMark.FULL
            day = day.plusDays(2)
        }
        val days = MarkedDays(marks)

        val text = export.encode(days, IcsLanguage.GERMAN, withStatus = true)

        assertTrue(export.tripCount(days, withStatus = true) > 10_000)
        assertTrue(text.length > 1_000_000)
    }

    private companion object {
        val EXPECTED: List<Pair<Int, String>> =
            listOf(
                377 to "94f7fa70db71b711b608f67f9b474f0cc67a2a2b9926b14e78cecd5a607a405f",
                377 to "0a3b4b1e8be3f3671158313b7cc1c18e2ec3824b3b00999235f5d0ad57a20978",
                334 to "042b9f9315a7948f101603d216ee33b1116f7e2d355797cd7d8537d3cad0db45",
            )
        val EXPECTED_FOUR_DAYS: Pair<Int, String> =
            299 to "6503d3cbc729108e357200d5c9b96be50e7148df644607a5bd832a0336b06c36"
    }
}
