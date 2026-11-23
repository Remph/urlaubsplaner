// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class PhoneClockTest {
    private lateinit var original: TimeZone

    @Before
    fun remember() {
        original = TimeZone.getDefault()
    }

    @After
    fun restore() {
        TimeZone.setDefault(original)
    }

    // 22:30 on 10 March in New York (UTC-5, summer time starts on the 14th) is 03:30 UTC, 04:30 in
    // Berlin and 12:30 in Tokyo on the 11th.
    private val evening = Instant.parse("2027-03-11T03:30:00Z")

    @Test
    fun `today follows the phone when the time zone moves while the clock lives`() {
        val clock = PhoneClock { evening.toEpochMilli() }

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        assertEquals(LocalDate.parse("2027-03-11"), LocalDate.now(clock))

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals(LocalDate.parse("2027-03-10"), LocalDate.now(clock))
        assertEquals(ZoneId.of("America/New_York"), clock.zone)
    }

    @Test
    fun `the time comes from the source and withZone fixes the zone`() {
        val clock = PhoneClock { evening.toEpochMilli() }
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))

        assertEquals(evening, clock.instant())
        val tokyo = clock.withZone(ZoneId.of("Asia/Tokyo"))
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals(ZoneId.of("Asia/Tokyo"), tokyo.zone)
        assertEquals(LocalDate.parse("2027-03-11"), LocalDate.now(tokyo))
    }
}
