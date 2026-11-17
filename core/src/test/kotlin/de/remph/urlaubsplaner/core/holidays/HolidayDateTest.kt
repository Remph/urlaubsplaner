// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

// One test per date rule. The moving holidays are checked in two years with a different
// Easter date, so a wrong offset cannot hide behind one lucky year.
class HolidayDateTest {
    private fun assertDate(
        holiday: Holiday,
        year: Int,
        month: Int,
        day: Int,
    ) = assertEquals(LocalDate.of(year, month, day), holiday.date(year), "$holiday $year")

    @Test
    fun `fixed holidays fall on their calendar day`() {
        assertDate(Holiday.NEUJAHR, 2026, 1, 1)
        assertDate(Holiday.HEILIGE_DREI_KOENIGE, 2026, 1, 6)
        assertDate(Holiday.INTERNATIONALER_FRAUENTAG, 2026, 3, 8)
        assertDate(Holiday.TAG_DER_ARBEIT, 2026, 5, 1)
        assertDate(Holiday.TAG_DER_BEFREIUNG, 2025, 5, 8)
        assertDate(Holiday.VOLKSAUFSTAND_17_JUNI, 2028, 6, 17)
        assertDate(Holiday.MARIAE_HIMMELFAHRT, 2026, 8, 15)
        assertDate(Holiday.AUGSBURGER_FRIEDENSFEST, 2026, 8, 8)
        assertDate(Holiday.WELTKINDERTAG, 2026, 9, 20)
        assertDate(Holiday.TAG_DER_DEUTSCHEN_EINHEIT, 2026, 10, 3)
        assertDate(Holiday.REFORMATIONSTAG, 2026, 10, 31)
        assertDate(Holiday.ALLERHEILIGEN, 2026, 11, 1)
        assertDate(Holiday.ERSTER_WEIHNACHTSTAG, 2026, 12, 25)
        assertDate(Holiday.ZWEITER_WEIHNACHTSTAG, 2026, 12, 26)
    }

    @Test
    fun `Karfreitag is two days before Easter`() {
        assertDate(Holiday.KARFREITAG, 2026, 4, 3)
        assertDate(Holiday.KARFREITAG, 2027, 3, 26)
    }

    @Test
    fun `Ostersonntag is Easter Sunday`() {
        assertDate(Holiday.OSTERSONNTAG, 2026, 4, 5)
        assertDate(Holiday.OSTERSONNTAG, 2027, 3, 28)
    }

    @Test
    fun `Ostermontag is the day after Easter`() {
        assertDate(Holiday.OSTERMONTAG, 2026, 4, 6)
        assertDate(Holiday.OSTERMONTAG, 2027, 3, 29)
    }

    @Test
    fun `Christi Himmelfahrt is 39 days after Easter`() {
        assertDate(Holiday.CHRISTI_HIMMELFAHRT, 2026, 5, 14)
        assertDate(Holiday.CHRISTI_HIMMELFAHRT, 2027, 5, 6)
    }

    @Test
    fun `Pfingstsonntag is 49 days after Easter`() {
        assertDate(Holiday.PFINGSTSONNTAG, 2026, 5, 24)
        assertDate(Holiday.PFINGSTSONNTAG, 2027, 5, 16)
    }

    @Test
    fun `Pfingstmontag is 50 days after Easter`() {
        assertDate(Holiday.PFINGSTMONTAG, 2026, 5, 25)
        assertDate(Holiday.PFINGSTMONTAG, 2027, 5, 17)
    }

    @Test
    fun `Fronleichnam is 60 days after Easter`() {
        assertDate(Holiday.FRONLEICHNAM, 2026, 6, 4)
        assertDate(Holiday.FRONLEICHNAM, 2027, 5, 27)
    }

    // The Wednesday before 23 November: 22 November itself counts when it is a Wednesday.
    @Test
    fun `Buss- und Bettag is the Wednesday before 23 November`() {
        assertDate(Holiday.BUSS_UND_BETTAG, 2026, 11, 18)
        assertDate(Holiday.BUSS_UND_BETTAG, 2027, 11, 17)
        assertDate(Holiday.BUSS_UND_BETTAG, 2028, 11, 22)
        assertDate(Holiday.BUSS_UND_BETTAG, 2029, 11, 21)
    }

    @Test
    fun `Buss- und Bettag is always a Wednesday`() {
        for (year in 2019..2100) {
            assertEquals(DayOfWeek.WEDNESDAY, Holiday.BUSS_UND_BETTAG.date(year).dayOfWeek, "$year")
        }
    }
}
