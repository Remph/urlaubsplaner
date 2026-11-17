// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

// 2026: 15 June is a Monday, so 16 to 18 June are working days.
class MarkedDaysTest {
    private val today = LocalDate.of(2026, 6, 15)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val calculator = VacationCalculator(clock) { false }

    private fun d(
        month: Int,
        day: Int,
    ) = LocalDate.of(2026, month, day)

    @Test
    fun `a day that is not marked has no status`() {
        assertNull(MarkedDays().statusOn(d(6, 16), today))
    }

    @Test
    fun `a future mark is planned until it is granted`() {
        val days =
            MarkedDays(mapOf(d(6, 16) to DayMark.FULL, d(6, 17) to DayMark.FULL), setOf(d(6, 17)))
        assertEquals(MarkStatus.PLANNED, days.statusOn(d(6, 16), today))
        assertEquals(MarkStatus.GRANTED, days.statusOn(d(6, 17), today))
    }

    @Test
    fun `today and the past are taken whatever the granted flag says`() {
        val days =
            MarkedDays(
                mapOf(d(6, 15) to DayMark.FULL, d(6, 12) to DayMark.FULL, d(6, 11) to DayMark.HALF),
                setOf(d(6, 15), d(6, 12)),
            )
        assertEquals(MarkStatus.TAKEN, days.statusOn(d(6, 15), today))
        assertEquals(MarkStatus.TAKEN, days.statusOn(d(6, 12), today))
        assertEquals(MarkStatus.TAKEN, days.statusOn(d(6, 11), today))
    }

    @Test
    fun `a granted day must be marked`() {
        assertThrows(IllegalArgumentException::class.java) {
            MarkedDays(emptyMap(), setOf(d(6, 16)))
        }
    }

    @Test
    fun `removing marks removes their granted flags`() {
        val days =
            MarkedDays(mapOf(d(6, 16) to DayMark.FULL, d(6, 17) to DayMark.FULL), setOf(d(6, 17)))
        assertEquals(MarkedDays(mapOf(d(6, 16) to DayMark.FULL)), days - listOf(d(6, 17)))
    }

    @Test
    fun `the granted share counts only future granted days of that year`() {
        val days =
            MarkedDays(
                mapOf(
                    d(6, 12) to DayMark.FULL, // taken
                    d(6, 16) to DayMark.FULL,
                    d(6, 17) to DayMark.HALF,
                    d(6, 18) to DayMark.FULL, // planned, not granted
                    LocalDate.of(2027, 1, 4) to DayMark.FULL, // next year
                ),
                setOf(d(6, 12), d(6, 16), d(6, 17), LocalDate.of(2027, 1, 4)),
            )
        assertEquals(VacationDays.of(1.5), calculator.grantedPlanned(2026, days, today))
    }

    @Test
    fun `granting does not change the balance`() {
        val entitlement = YearEntitlement(2026, VacationDays.of(30))
        val marks = mapOf(d(6, 16) to DayMark.FULL, d(6, 17) to DayMark.HALF)
        // balance() takes only the marks, so a granted flag has no way in; this pins that.
        val balance = calculator.balance(entitlement, MarkedDays(marks, setOf(d(6, 16))).marks)
        assertEquals(calculator.balance(entitlement, marks), balance)
        assertEquals(VacationDays.of(1.5), balance.planned)
    }
}
