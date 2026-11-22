// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The phone's wall clock in the phone's current time zone. `Clock.systemDefaultZone()` fixes the
 * zone when it is made, so after a flight the app would keep counting days in the zone it started
 * in until the process ended; here the zone is asked again on every use, and so every
 * `LocalDate.now(clock)` follows the phone.
 *
 * [epochMillis] is the time source; it is only a parameter so a test can set the time.
 */
class PhoneClock(
    private val epochMillis: () -> Long = { System.currentTimeMillis() },
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    // The caller asked for that zone, so it stays; the time still comes from the same source.
    override fun withZone(zone: ZoneId): Clock {
        val source = this
        return object : Clock() {
            override fun getZone(): ZoneId = zone

            override fun withZone(zone: ZoneId): Clock = source.withZone(zone)

            override fun instant(): Instant = source.instant()
        }
    }

    override fun instant(): Instant = Instant.ofEpochMilli(epochMillis())
}
