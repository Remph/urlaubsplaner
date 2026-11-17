// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.bridge.BridgeFinder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

// The balance and the bridge finder take the holiday check as a function, so the opt-out reaches
// them only through the function the caller builds. These tests build it the way the app will.
// 15 August 2030 is a Thursday; there is no other holiday in Bayern around it.
class OptOutReachesRulesTest {
    private val thursday = LocalDate.of(2030, 8, 15)
    private val friday = LocalDate.of(2030, 8, 16)
    private val clock =
        Clock.fixed(
            LocalDate
                .of(2030, 1, 2)
                .atTime(12, 0)
                .atZone(ZoneOffset.UTC)
                .toInstant(),
            ZoneOffset.UTC,
        )

    private fun isHoliday(optOuts: Set<RegionalOptOut>): (LocalDate) -> Boolean =
        { PublicHolidays.isHoliday(Bundesland.BAYERN, it, optOuts = optOuts) }

    private val optOut = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN)

    @Test
    fun `a marked day costs nothing on Mariae Himmelfahrt, but a full day with the opt-out`() {
        val withHoliday = VacationCalculator(clock, isPublicHoliday = isHoliday(emptySet()))
        val withoutHoliday = VacationCalculator(clock, isPublicHoliday = isHoliday(optOut))

        assertEquals(VacationDays.ZERO, withHoliday.cost(thursday))
        assertEquals(VacationDays.ONE, withoutHoliday.cost(thursday))
    }

    @Test
    fun `the balance counts the marked day only with the opt-out`() {
        val entitlement = YearEntitlement(2030, VacationDays.of(30))
        val marks = mapOf(thursday to DayMark.FULL)

        val remainingWith =
            VacationCalculator(clock, isPublicHoliday = isHoliday(emptySet()))
                .balance(entitlement, marks)
                .remaining
        val remainingWithout =
            VacationCalculator(clock, isPublicHoliday = isHoliday(optOut))
                .balance(entitlement, marks)
                .remaining

        assertEquals(VacationDays.of(30), remainingWith)
        assertEquals(VacationDays.of(29), remainingWithout)
    }

    @Test
    fun `the Friday after Mariae Himmelfahrt is a bridge only without the opt-out`() {
        val withHoliday = BridgeFinder(clock, isPublicHoliday = isHoliday(emptySet())).find(2030)
        val withoutHoliday = BridgeFinder(clock, isPublicHoliday = isHoliday(optOut)).find(2030)

        val bridge = withHoliday.single { friday in it.leave }
        assertEquals(thursday, bridge.start)
        assertTrue(withoutHoliday.none { friday in it.leave || thursday in it.leave })
    }
}
