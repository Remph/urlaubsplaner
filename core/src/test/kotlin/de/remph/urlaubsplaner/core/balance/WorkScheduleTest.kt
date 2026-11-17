// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorkScheduleTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private val saturday = LocalDate.of(2026, 10, 31)
    private val sunday = LocalDate.of(2026, 11, 1)

    @Test
    fun `by default Monday to Friday are full working days and the weekend is not`() {
        val schedule = WorkSchedule()
        assertEquals(VacationDays.ONE, schedule.workingValue(monday))
        assertEquals(VacationDays.ONE, schedule.workingValue(monday.plusDays(4)))
        assertEquals(VacationDays.ZERO, schedule.workingValue(saturday))
        assertEquals(VacationDays.ZERO, schedule.workingValue(sunday))
    }

    @Test
    fun `working weekdays can be changed for a four day week`() {
        val schedule =
            WorkSchedule(
                workingDays =
                    setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                    ),
            )
        assertEquals(VacationDays.ONE, schedule.workingValue(monday))
        assertEquals(VacationDays.ZERO, schedule.workingValue(monday.plusDays(4)))
    }

    @Test
    fun `Heiligabend and Silvester are full working days by default`() {
        val schedule = WorkSchedule()
        assertEquals(VacationDays.ONE, schedule.workingValue(LocalDate.of(2026, 12, 24)))
        assertEquals(VacationDays.ONE, schedule.workingValue(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `Heiligabend can count as a half working day`() {
        val schedule =
            WorkSchedule(
                specialDays =
                    mapOf(WorkSchedule.HEILIGABEND to VacationDays.HALF),
            )
        assertEquals(VacationDays.HALF, schedule.workingValue(LocalDate.of(2026, 12, 24)))
        assertEquals(VacationDays.ONE, schedule.workingValue(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `Silvester can count as a day off like a holiday`() {
        val schedule =
            WorkSchedule(specialDays = mapOf(WorkSchedule.SILVESTER to VacationDays.ZERO))
        assertEquals(VacationDays.ZERO, schedule.workingValue(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `a special day on a weekend stays a day off`() {
        val schedule =
            WorkSchedule(
                specialDays =
                    mapOf(WorkSchedule.HEILIGABEND to VacationDays.HALF),
            )
        // 24 December 2022 is a Saturday.
        assertEquals(VacationDays.ZERO, schedule.workingValue(LocalDate.of(2022, 12, 24)))
    }

    @Test
    fun `a special day cannot be worth more than a full day`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkSchedule(specialDays = mapOf(WorkSchedule.HEILIGABEND to VacationDays.of(2)))
        }
    }

    @Test
    fun `a special day cannot be worth less than nothing`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkSchedule(specialDays = mapOf(WorkSchedule.SILVESTER to -VacationDays.HALF))
        }
    }

    @Test
    fun `a half day mark is worth half a day and a full mark a full day`() {
        assertEquals(VacationDays.HALF, DayMark.HALF.value)
        assertEquals(VacationDays.ONE, DayMark.FULL.value)
    }
}
