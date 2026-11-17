// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset

class EntitlementSuggestionTest {
    private val previous =
        YearEntitlement(
            year = 2026,
            days = VacationDays.of(30),
            carryOver = VacationDays.of(2),
            carryOverExpiry = MonthDay.of(6, 30),
        )

    /** A balance with only the field the suggestion reads filled in. */
    private fun balance(remaining: VacationDays) =
        VacationBalance(
            entitlement = VacationDays.of(30),
            carryOver = VacationDays.ZERO,
            carryOverExpiry = LocalDate.of(2026, 3, 31),
            carryOverUsed = VacationDays.ZERO,
            carryOverLost = VacationDays.ZERO,
            taken = VacationDays.ZERO,
            planned = VacationDays.ZERO,
            remaining = remaining,
        )

    @Test
    fun `without a year before there is nothing to suggest`() {
        assertNull(EntitlementSuggestion.forYear(2027, null, null, carryOverUnused = true))
    }

    @Test
    fun `by default the days carry over and the Resturlaub is zero`() {
        val suggestion =
            EntitlementSuggestion.forYear(
                2027,
                previous,
                balance(VacationDays.of(4.5)),
                carryOverUnused = false,
            )

        assertEquals(
            YearEntitlement(2027, VacationDays.of(30), VacationDays.ZERO, MonthDay.of(6, 30)),
            suggestion,
        )
    }

    @Test
    fun `the expiry year offset comes along from the year before`() {
        val later = previous.copy(carryOverExpiryYears = 1)

        val suggestion =
            EntitlementSuggestion.forYear(2027, later, null, carryOverUnused = false)

        assertEquals(1, suggestion?.carryOverExpiryYears)
        assertEquals(java.time.LocalDate.of(2028, 6, 30), suggestion?.carryOverExpiryDate)
    }

    @Test
    fun `with the option on the unused days become the Resturlaub`() {
        val suggestion =
            EntitlementSuggestion.forYear(
                2027,
                previous,
                balance(VacationDays.of(4.5)),
                carryOverUnused = true,
            )

        assertEquals(VacationDays.of(4.5), suggestion?.carryOver)
        assertEquals(VacationDays.of(30), suggestion?.days)
        assertEquals(MonthDay.of(6, 30), suggestion?.carryOverExpiry)
    }

    @Test
    fun `an overbooked year before gives no negative Resturlaub`() {
        val suggestion =
            EntitlementSuggestion.forYear(
                2027,
                previous,
                balance(VacationDays.of(-3)),
                carryOverUnused = true,
            )

        assertEquals(VacationDays.ZERO, suggestion?.carryOver)
    }

    @Test
    fun `with the option on but no balance the Resturlaub stays zero`() {
        val suggestion = EntitlementSuggestion.forYear(2027, previous, null, carryOverUnused = true)

        assertEquals(VacationDays.ZERO, suggestion?.carryOver)
    }

    @Test
    fun `the option is off in a new settings object`() {
        assertEquals(false, UserSettings().suggestCarryOver)
    }

    @Test
    fun `a real balance of the year before feeds the suggestion`() {
        // 30 days, 24 marked on working days in 2026 (Mon to Fri, no holidays in those weeks of
        // January and February), so 6 are left at the end of the year.
        val clock = Clock.fixed(Instant.parse("2026-12-31T12:00:00Z"), ZoneOffset.UTC)
        val settings = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN)
        val calculator =
            VacationCalculator(
                clock,
                settings.schedule,
                checkNotNull(settings.publicHolidayCheck()),
            )
        val marks =
            generateSequence(LocalDate.of(2026, 1, 12)) { it.plusDays(1) }
                .filter { it.dayOfWeek.value <= 5 }
                .take(24)
                .associateWith { DayMark.FULL }
        val entitlement = YearEntitlement(2026, VacationDays.of(30))

        val suggestion =
            EntitlementSuggestion.forYear(
                2027,
                entitlement,
                calculator.balance(entitlement, marks),
                carryOverUnused = true,
            )

        assertEquals(VacationDays.of(6), suggestion?.carryOver)
    }
}
