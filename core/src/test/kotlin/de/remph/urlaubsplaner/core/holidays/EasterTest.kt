// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class EasterTest {
    // Dates from the published Easter table (Gregorian calendar, Western churches).
    @ParameterizedTest
    @CsvSource(
        "2019, 2019-04-21",
        "2020, 2020-04-12",
        "2021, 2021-04-04",
        "2022, 2022-04-17",
        "2023, 2023-04-09",
        "2024, 2024-03-31",
        "2025, 2025-04-20",
        "2026, 2026-04-05",
        "2027, 2027-03-28",
        "2028, 2028-04-16",
        "2029, 2029-04-01",
        "2030, 2030-04-21",
        "2031, 2031-04-13",
        "2032, 2032-03-28",
        "2033, 2033-04-17",
        "2034, 2034-04-09",
        "2035, 2035-03-25",
        "2036, 2036-04-13",
        "2037, 2037-04-05",
        "2039, 2039-04-10",
        "2040, 2040-04-01",
    )
    fun `Easter Sunday matches the published table`(
        year: Int,
        expected: String,
    ) {
        assertEquals(LocalDate.parse(expected), Easter.sunday(year))
    }

    @Test
    fun `latest possible Easter is 25 April`() {
        assertEquals(LocalDate.of(2038, 4, 25), Easter.sunday(2038))
    }

    @Test
    fun `earliest possible Easter is 22 March`() {
        assertEquals(LocalDate.of(2285, 3, 22), Easter.sunday(2285))
    }
}
