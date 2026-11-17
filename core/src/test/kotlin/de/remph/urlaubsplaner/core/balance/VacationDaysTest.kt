// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VacationDaysTest {
    @Test
    fun `whole and half days are kept exactly`() {
        assertEquals(VacationDays.ofHalves(61), VacationDays.of(30.5))
        assertEquals(30.5, VacationDays.of(30.5).days)
    }

    @Test
    fun `a quarter day is rejected because the smallest unit is a half day`() {
        assertThrows(IllegalArgumentException::class.java) { VacationDays.of(0.25) }
    }

    @Test
    fun `infinite, not-a-number and too large amounts are rejected`() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) { VacationDays.of(Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { VacationDays.of(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { VacationDays.of(1.0e12) }
    }

    @Test
    fun `days add and subtract`() {
        val sum = VacationDays.of(2) + VacationDays.HALF
        assertEquals(VacationDays.of(2.5), sum)
        assertEquals(VacationDays.of(1.5), sum - VacationDays.ONE)
    }

    @Test
    fun `days can go below zero when overbooked`() {
        assertEquals(VacationDays.ofHalves(-3), VacationDays.ONE - VacationDays.of(2.5))
    }

    @Test
    fun `days compare by amount`() {
        assertTrue(VacationDays.HALF < VacationDays.ONE)
        assertEquals(VacationDays.HALF, minOf(VacationDays.ONE, VacationDays.HALF))
    }

    @Test
    fun `text shows whole days without a fraction`() {
        assertEquals("12", VacationDays.of(12).toString())
        assertEquals("12.5", VacationDays.of(12.5).toString())
        assertEquals("-0.5", (-VacationDays.HALF).toString())
    }
}
