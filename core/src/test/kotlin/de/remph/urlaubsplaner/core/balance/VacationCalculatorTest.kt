// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset

// 2026 calendar used below: 1 Jan is a Thursday, 31 Mar a Tuesday, 15 Jun a Monday.
class VacationCalculatorTest {
    private val zone: ZoneId = ZoneOffset.UTC

    private fun clockAt(date: LocalDate) =
        Clock.fixed(date.atTime(12, 0).atZone(zone).toInstant(), zone)

    private fun calculator(
        today: LocalDate = LocalDate.of(2026, 6, 15),
        schedule: WorkSchedule = WorkSchedule(),
        holidays: Set<LocalDate> = emptySet(),
    ) = VacationCalculator(clockAt(today), schedule) { it in holidays }

    private fun year2026(
        days: Int = 30,
        carryOver: Int = 0,
        expiry: MonthDay = MonthDay.of(3, 31),
    ) = YearEntitlement(2026, VacationDays.of(days), VacationDays.of(carryOver), expiry)

    private fun full(vararg dates: LocalDate): Map<LocalDate, DayMark> =
        dates.associateWith {
            DayMark.FULL
        }

    private fun d(
        month: Int,
        day: Int,
        year: Int = 2026,
    ) = LocalDate.of(year, month, day)

    // --- cost of a marked day ---

    @Test
    fun `a full day on a normal working day costs one day`() {
        assertEquals(VacationDays.ONE, calculator().cost(d(6, 15), DayMark.FULL))
    }

    @Test
    fun `a half day mark costs half a day`() {
        assertEquals(VacationDays.HALF, calculator().cost(d(6, 15), DayMark.HALF))
    }

    @Test
    fun `a weekend day costs nothing`() {
        assertEquals(VacationDays.ZERO, calculator().cost(d(6, 20), DayMark.FULL))
    }

    @Test
    fun `a public holiday on a weekday costs nothing`() {
        assertEquals(
            VacationDays.ZERO,
            calculator(holidays = setOf(d(6, 16))).cost(d(6, 16), DayMark.FULL),
        )
    }

    @Test
    fun `Berlin Volksaufstand on Saturday 17 June 2028 costs nothing`() {
        val berlin =
            VacationCalculator(
                clockAt(d(6, 1, 2028)),
            ) { PublicHolidays.isHoliday(Bundesland.BERLIN, it) }
        assertEquals(VacationDays.ZERO, berlin.cost(d(6, 17, 2028), DayMark.FULL))
        assertEquals(VacationDays.ONE, berlin.cost(d(6, 16, 2028), DayMark.FULL))
    }

    @Test
    fun `a regional extra holiday costs nothing only when it is switched on`() {
        // 8 August 2025 is a Friday.
        val date = d(8, 8, 2025)

        fun inBayern(extras: Set<RegionalExtra>) =
            VacationCalculator(clockAt(d(6, 1, 2025))) {
                PublicHolidays.isHoliday(Bundesland.BAYERN, it, extras)
            }
        assertEquals(VacationDays.ONE, inBayern(emptySet()).cost(date))
        assertEquals(
            VacationDays.ZERO,
            inBayern(setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST)).cost(date),
        )
    }

    @Test
    fun `a half working Heiligabend costs half a day for a full or a half mark`() {
        val schedule =
            WorkSchedule(
                specialDays =
                    mapOf(WorkSchedule.HEILIGABEND to VacationDays.HALF),
            )
        val heiligabend = d(12, 24) // Thursday
        assertEquals(
            VacationDays.HALF,
            calculator(schedule = schedule).cost(heiligabend, DayMark.FULL),
        )
        assertEquals(
            VacationDays.HALF,
            calculator(schedule = schedule).cost(heiligabend, DayMark.HALF),
        )
    }

    @Test
    fun `Silvester that counts as a day off costs nothing`() {
        val schedule =
            WorkSchedule(specialDays = mapOf(WorkSchedule.SILVESTER to VacationDays.ZERO))
        assertEquals(
            VacationDays.ZERO,
            calculator(schedule = schedule).cost(d(12, 31), DayMark.FULL),
        )
    }

    @Test
    fun `Heiligabend costs a full day with the default schedule`() {
        assertEquals(VacationDays.ONE, calculator().cost(d(12, 24), DayMark.FULL))
    }

    @Test
    fun `Easter 2026 over two weeks costs eight days, Karfreitag and Ostermontag are free`() {
        val nrw =
            VacationCalculator(clockAt(d(6, 15))) {
                PublicHolidays.isHoliday(Bundesland.NORDRHEIN_WESTFALEN, it)
            }
        val marks =
            generateSequence(d(3, 30)) {
                it.plusDays(1)
            }.take(12).toList().let { full(*it.toTypedArray()) }
        assertEquals(VacationDays.of(8), nrw.balance(year2026(), marks).taken)
    }

    // --- taken, planned, remaining ---

    @Test
    fun `days up to and including today are taken and later days are planned`() {
        val marks = full(d(6, 12), d(6, 15), d(6, 16))
        val balance = calculator().balance(year2026(), marks)
        assertEquals(VacationDays.of(2), balance.taken)
        assertEquals(VacationDays.ONE, balance.planned)
    }

    @Test
    fun `half days count as half in taken and planned`() {
        val marks =
            mapOf(
                d(6, 15) to DayMark.HALF,
                d(6, 16) to DayMark.HALF,
                d(6, 17) to DayMark.FULL,
            )
        val balance = calculator().balance(year2026(), marks)
        assertEquals(VacationDays.HALF, balance.taken)
        assertEquals(VacationDays.of(1.5), balance.planned)
    }

    @Test
    fun `remaining is the entitlement minus taken and planned`() {
        val balance = calculator().balance(year2026(days = 30), full(d(6, 12), d(6, 16), d(6, 17)))
        assertEquals(VacationDays.of(27), balance.remaining)
        assertEquals(VacationDays.of(30), balance.entitlement)
    }

    @Test
    fun `marks on weekends and holidays do not reduce the remaining days`() {
        val balance =
            calculator(
                holidays = setOf(d(6, 16)),
            ).balance(year2026(), full(d(6, 13), d(6, 14), d(6, 16)))
        assertEquals(VacationDays.of(30), balance.remaining)
    }

    @Test
    fun `marks from other years are ignored`() {
        val marks = full(d(12, 1, 2025), d(1, 4, 2027), d(6, 16))
        assertEquals(VacationDays.of(29), calculator().balance(year2026(), marks).remaining)
    }

    @Test
    fun `an overbooked year shows negative remaining days`() {
        val balance = calculator().balance(year2026(days = 1), full(d(6, 16), d(6, 17), d(6, 18)))
        assertEquals(VacationDays.of(-2), balance.remaining)
    }

    // --- Resturlaub ---

    @Test
    fun `days up to the expiry date use the Resturlaub first and the unused rest is lost later`() {
        val balance = calculator().balance(year2026(carryOver = 5), full(d(3, 30), d(3, 31)))
        assertEquals(VacationDays.of(2), balance.carryOverUsed)
        assertEquals(VacationDays.of(3), balance.carryOverLost)
        assertEquals(VacationDays.of(30), balance.remaining)
    }

    @Test
    fun `the expiry date itself still uses Resturlaub but the day after does not`() {
        val balance = calculator().balance(year2026(carryOver = 5), full(d(3, 31), d(4, 1)))
        assertEquals(VacationDays.ONE, balance.carryOverUsed)
        assertEquals(VacationDays.of(4), balance.carryOverLost)
    }

    @Test
    fun `before the expiry date planned days use the Resturlaub and the rest stays open`() {
        val balance = calculator(today = d(2, 10)).balance(year2026(carryOver = 5), full(d(2, 16)))
        assertEquals(VacationDays.ONE, balance.carryOverUsed)
        assertEquals(VacationDays.ZERO, balance.carryOverLost)
        assertEquals(VacationDays.of(4), balance.carryOverOpen)
        assertEquals(VacationDays.of(34), balance.remaining)
    }

    @Test
    fun `Resturlaub is not lost on the expiry date but is lost on the day after`() {
        val onExpiry = calculator(today = d(3, 31)).balance(year2026(carryOver = 5), emptyMap())
        assertEquals(VacationDays.ZERO, onExpiry.carryOverLost)
        assertEquals(VacationDays.of(35), onExpiry.remaining)

        val dayAfter = calculator(today = d(4, 1)).balance(year2026(carryOver = 5), emptyMap())
        assertEquals(VacationDays.of(5), dayAfter.carryOverLost)
        assertEquals(VacationDays.of(30), dayAfter.remaining)
    }

    @Test
    fun `days beyond the Resturlaub are taken from the normal entitlement`() {
        val marks = full(d(3, 2), d(3, 3), d(3, 4), d(3, 5))
        val balance = calculator().balance(year2026(carryOver = 2), marks)
        assertEquals(VacationDays.of(2), balance.carryOverUsed)
        assertEquals(VacationDays.ZERO, balance.carryOverLost)
        assertEquals(VacationDays.of(28), balance.remaining)
    }

    @Test
    fun `the expiry date is configurable`() {
        val balance =
            calculator().balance(
                year2026(carryOver = 5, expiry = MonthDay.of(9, 30)),
                full(d(9, 1)),
            )
        assertEquals(d(9, 30), balance.carryOverExpiry)
        assertEquals(VacationDays.ONE, balance.carryOverUsed)
        assertEquals(VacationDays.ZERO, balance.carryOverLost)
        assertEquals(VacationDays.of(4), balance.carryOverOpen)
    }

    @Test
    fun `Resturlaub can expire in the year after the entitlement's`() {
        val entitlement =
            YearEntitlement(
                2027,
                VacationDays.of(30),
                VacationDays.of(5),
                MonthDay.of(3, 31),
                carryOverExpiryYears = 1,
            )
        assertEquals(d(3, 31, 2028), entitlement.carryOverExpiryDate)

        // Long after 31 March 2027 nothing is lost, and the Resturlaub is still open.
        val autumn = calculator(today = d(10, 1, 2027)).balance(entitlement, full(d(9, 6, 2027)))
        assertEquals(d(3, 31, 2028), autumn.carryOverExpiry)
        assertEquals(VacationDays.ONE, autumn.carryOverUsed)
        assertEquals(VacationDays.ZERO, autumn.carryOverLost)
        assertEquals(VacationDays.of(4), autumn.carryOverOpen)
        assertEquals(VacationDays.of(34), autumn.remaining)
    }

    @Test
    fun `an expiry in the year after still lets the Resturlaub run out in that year`() {
        val entitlement =
            YearEntitlement(
                2027,
                VacationDays.of(30),
                VacationDays.of(5),
                MonthDay.of(3, 31),
                carryOverExpiryYears = 1,
            )
        // The day after the expiry the unused rest is lost, though no marked day lies that far.
        val after = calculator(today = d(4, 1, 2028)).balance(entitlement, emptyMap())
        assertEquals(VacationDays.of(5), after.carryOverLost)
        assertEquals(VacationDays.of(30), after.remaining)
    }

    @Test
    fun `the expiry year offset is 0 or 1`() {
        for (bad in listOf(-1, 2)) {
            assertThrows(IllegalArgumentException::class.java) {
                YearEntitlement(2026, VacationDays.of(30), carryOverExpiryYears = bad)
            }
        }
    }

    @Test
    fun `half days use the Resturlaub too`() {
        val marks = mapOf(d(3, 2) to DayMark.HALF, d(3, 3) to DayMark.FULL)
        val balance = calculator().balance(year2026(carryOver = 5), marks)
        assertEquals(VacationDays.of(1.5), balance.carryOverUsed)
        assertEquals(VacationDays.of(3.5), balance.carryOverLost)
    }

    @Test
    fun `a 29 February expiry falls on 28 February in a year without one`() {
        val balance =
            calculator().balance(year2026(carryOver = 5, expiry = MonthDay.of(2, 29)), emptyMap())
        assertEquals(d(2, 28), balance.carryOverExpiry)
        val leap =
            calculator().balance(
                YearEntitlement(2028, VacationDays.of(30), VacationDays.of(5), MonthDay.of(2, 29)),
                emptyMap(),
            )
        assertEquals(d(2, 29, 2028), leap.carryOverExpiry)
    }

    @Test
    fun `a year that has not started plans everything and loses nothing`() {
        val balance =
            calculator(today = d(12, 1, 2025)).balance(year2026(carryOver = 5), full(d(6, 16)))
        assertEquals(VacationDays.ZERO, balance.taken)
        assertEquals(VacationDays.ONE, balance.planned)
        assertEquals(VacationDays.ZERO, balance.carryOverLost)
    }

    @Test
    fun `a past year has everything taken and the unused Resturlaub lost`() {
        val balance =
            calculator(today = d(2, 1, 2027)).balance(year2026(carryOver = 5), full(d(6, 16)))
        assertEquals(VacationDays.ONE, balance.taken)
        assertEquals(VacationDays.ZERO, balance.planned)
        assertEquals(VacationDays.of(5), balance.carryOverLost)
        assertEquals(VacationDays.of(29), balance.remaining)
    }

    @Test
    fun `before the expiry a plan beyond it still counts the open Resturlaub as bookable`() {
        val balance =
            calculator(today = d(2, 10)).balance(
                year2026(days = 1, carryOver = 1),
                full(d(6, 16), d(6, 17)),
            )
        assertEquals(VacationDays.ZERO, balance.remaining)
        val later =
            calculator(
                today = d(4, 1),
            ).balance(year2026(days = 1, carryOver = 1), full(d(6, 16), d(6, 17)))
        assertEquals(VacationDays.of(-1), later.remaining)
    }

    @Test
    fun `a half working Heiligabend costs half a day in the balance`() {
        val schedule =
            WorkSchedule(
                specialDays =
                    mapOf(WorkSchedule.HEILIGABEND to VacationDays.HALF),
            )
        val balance =
            calculator(
                schedule = schedule,
                holidays = setOf(d(12, 25)),
            ).balance(year2026(), full(d(12, 23), d(12, 24), d(12, 25)))
        assertEquals(VacationDays.of(1.5), balance.planned)
    }

    @Test
    fun `the entitlement and the Resturlaub cannot be negative`() {
        assertThrows(IllegalArgumentException::class.java) { year2026(days = -1) }
        assertThrows(IllegalArgumentException::class.java) { year2026(carryOver = -1) }
    }

    // --- marking a range ---

    @Test
    fun `a range lists the working days and skips the weekend`() {
        assertEquals(
            listOf(d(6, 15), d(6, 16), d(6, 17), d(6, 18), d(6, 19)),
            calculator().costlyDays(d(6, 15), d(6, 21)),
        )
    }

    @Test
    fun `a range skips a public holiday`() {
        val days = calculator(holidays = setOf(d(6, 17))).costlyDays(d(6, 15), d(6, 19))

        assertEquals(listOf(d(6, 15), d(6, 16), d(6, 18), d(6, 19)), days)
    }

    @Test
    fun `a range keeps a half working day and skips a day off`() {
        val schedule =
            WorkSchedule(
                specialDays =
                    mapOf(
                        MonthDay.of(6, 16) to VacationDays.HALF,
                        MonthDay.of(6, 17) to VacationDays.ZERO,
                    ),
            )

        val days = calculator(schedule = schedule).costlyDays(d(6, 15), d(6, 18))

        assertEquals(listOf(d(6, 15), d(6, 16), d(6, 18)), days)
    }

    @Test
    fun `a range of one working day is that day and one weekend day is nothing`() {
        assertEquals(listOf(d(6, 15)), calculator().costlyDays(d(6, 15), d(6, 15)))
        assertEquals(emptyList<LocalDate>(), calculator().costlyDays(d(6, 20), d(6, 20)))
    }

    @Test
    fun `a range cannot end before it starts`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator().costlyDays(d(6, 16), d(6, 15))
        }
    }

    // --- trips ---

    private val week1 = (15..19).map { d(6, it) }
    private val week2 = (22..26).map { d(6, it) }

    @Test
    fun `two weeks with a weekend between are one trip`() {
        val trips = calculator().trips(full(*(week1 + week2).toTypedArray()))

        assertEquals(listOf(Trip(d(6, 15), d(6, 26), VacationDays.of(10))), trips)
    }

    @Test
    fun `a working day between two marked days makes two trips`() {
        val trips = calculator().trips(full(d(6, 15), d(6, 17)))

        assertEquals(
            listOf(
                Trip(d(6, 15), d(6, 15), VacationDays.ONE),
                Trip(d(6, 17), d(6, 17), VacationDays.ONE),
            ),
            trips,
        )
    }

    @Test
    fun `a public holiday between two marked days keeps them in one trip`() {
        val trips = calculator(holidays = setOf(d(6, 18))).trips(full(d(6, 17), d(6, 19)))

        assertEquals(listOf(Trip(d(6, 17), d(6, 19), VacationDays.of(2))), trips)
    }

    @Test
    fun `a trip adds half days and ignores marks that cost nothing`() {
        val marks =
            mapOf(
                d(6, 15) to DayMark.HALF,
                d(6, 16) to DayMark.FULL,
                d(6, 20) to DayMark.FULL,
            )

        val trips = calculator().trips(marks)

        assertEquals(listOf(Trip(d(6, 15), d(6, 16), VacationDays.of(1.5))), trips)
    }

    @Test
    fun `trips come out sorted and no marks give no trips`() {
        assertEquals(emptyList<Trip>(), calculator().trips(emptyMap()))

        val trips = calculator().trips(full(d(9, 14), d(6, 15)))

        assertEquals(listOf(d(6, 15), d(9, 14)), trips.map { it.start })
    }

    @Test
    fun `a trip knows which days it contains`() {
        val trip = Trip(d(6, 15), d(6, 19), VacationDays.of(5))

        assertTrue(d(6, 15) in trip)
        assertTrue(d(6, 19) in trip)
        assertFalse(d(6, 20) in trip)
    }
}
