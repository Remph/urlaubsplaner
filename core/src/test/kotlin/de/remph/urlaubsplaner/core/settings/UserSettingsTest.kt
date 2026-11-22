// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.settings

import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.Holiday
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UserSettingsTest {
    @Test
    fun `a new user has no Land and works Monday to Friday`() {
        val settings = UserSettings()

        assertNull(settings.land)
        assertEquals(VacationDays.ONE, settings.schedule.workingValue(LocalDate.of(2026, 10, 26)))
        assertEquals(VacationDays.ZERO, settings.schedule.workingValue(LocalDate.of(2026, 10, 25)))
        assertEquals(VacationDays.ONE, settings.schedule.workingValue(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun `without a Land there is no holiday check`() {
        assertNull(UserSettings().publicHolidayCheck())
    }

    @Test
    fun `a date before 2019 is no public holiday instead of an error`() {
        val settings = UserSettings(land = Bundesland.BAYERN)
        val check = settings.publicHolidayCheck()!!

        // 25 December 2018 was a holiday, but the rules start in 2019 and say nothing before.
        assertFalse(check(LocalDate.of(2018, 12, 25)))
        assertFalse(check(LocalDate.of(1900, 1, 1)))
        assertFalse(check(LocalDate.MIN))
        assertTrue(settings.publicHolidays(2018).isEmpty())
        assertTrue(check(LocalDate.of(2019, 1, 1)))
        assertFalse(PublicHolidays.isHoliday(Bundesland.BAYERN, LocalDate.of(2018, 12, 25)))
    }

    @Test
    fun `the holiday check follows Land, extras and opt-outs`() {
        val assumption = LocalDate.of(2026, 8, 15)
        val friedensfest = LocalDate.of(2026, 8, 8)

        val bayern = UserSettings(land = Bundesland.BAYERN).publicHolidayCheck()!!
        assertTrue(bayern(assumption))
        assertFalse(bayern(friedensfest))
        assertTrue(bayern(LocalDate.of(2026, 12, 25)))
        assertFalse(bayern(LocalDate.of(2026, 12, 23)))

        val optedOut =
            UserSettings(
                land = Bundesland.BAYERN,
                optOuts = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN),
                extras = setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST),
            ).publicHolidayCheck()!!
        assertFalse(optedOut(assumption))
        assertTrue(optedOut(friedensfest))
    }

    @Test
    fun `the holiday check answers for several years with one function`() {
        val check = UserSettings(land = Bundesland.BERLIN).publicHolidayCheck()!!

        assertTrue(check(LocalDate.of(2026, 1, 1)))
        assertTrue(check(LocalDate.of(2027, 1, 1)))
        assertFalse(check(LocalDate.of(2027, 1, 2)))
    }

    @Test
    fun `the next public holiday is the same day when today is one`() {
        val settings = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN)

        val next = settings.nextPublicHoliday(LocalDate.of(2026, 10, 3))

        assertEquals(LocalDate.of(2026, 10, 3), next?.date)
        assertEquals(Holiday.TAG_DER_DEUTSCHEN_EINHEIT, next?.holiday)
    }

    @Test
    fun `the next public holiday after the last one of the year is in the next year`() {
        val settings = UserSettings(land = Bundesland.BAYERN)

        val next = settings.nextPublicHoliday(LocalDate.of(2026, 12, 27))

        assertEquals(LocalDate.of(2027, 1, 1), next?.date)
        assertEquals(Holiday.NEUJAHR, next?.holiday)
    }

    @Test
    fun `the next public holiday follows the regional choices`() {
        val withOptOut =
            UserSettings(
                land = Bundesland.BAYERN,
                optOuts = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN),
            )
        val without = UserSettings(land = Bundesland.BAYERN)

        assertEquals(
            Holiday.MARIAE_HIMMELFAHRT,
            without.nextPublicHoliday(LocalDate.of(2026, 8, 10))?.holiday,
        )
        assertEquals(
            Holiday.TAG_DER_DEUTSCHEN_EINHEIT,
            withOptOut.nextPublicHoliday(LocalDate.of(2026, 8, 10))?.holiday,
        )
    }

    @Test
    fun `without a Land there are no public holidays`() {
        assertEquals(emptyList<Any>(), UserSettings().publicHolidays(2026))
        assertNull(UserSettings().nextPublicHoliday(LocalDate.of(2026, 1, 1)))
    }
}
