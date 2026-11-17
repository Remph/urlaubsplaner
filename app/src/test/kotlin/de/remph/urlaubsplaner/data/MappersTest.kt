// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

class MappersTest {
    private val full =
        UserSettings(
            land = Bundesland.BAYERN,
            extras = setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST),
            optOuts = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN),
            schedule =
                WorkSchedule(
                    workingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                    specialDays =
                        mapOf(
                            WorkSchedule.HEILIGABEND to VacationDays.HALF,
                            WorkSchedule.SILVESTER to VacationDays.ZERO,
                        ),
                ),
        )

    @Test
    fun `settings survive a round trip`() {
        assertEquals(full, full.toEntity().toUserSettings())
    }

    @Test
    fun `no settings row means the defaults`() {
        assertEquals(UserSettings(), null.toUserSettings())
    }

    @Test
    fun `a row with no working days stays empty, not Monday to Friday`() {
        val settings = UserSettings(schedule = WorkSchedule(workingDays = emptySet()))

        val back = settings.toEntity().toUserSettings()

        assertEquals(emptySet<DayOfWeek>(), back.schedule.workingDays)
    }

    @Test
    fun `an unknown Land is no Land`() {
        assertNull(
            full
                .toEntity()
                .copy(land = "XX")
                .toUserSettings()
                .land,
        )
    }

    @Test
    fun `unknown names and bad special days are skipped, not fatal`() {
        val entity =
            full.toEntity().copy(
                regionalExtras = "AUGSBURGER_FRIEDENSFEST,FROM_THE_FUTURE",
                optOuts = "FROM_THE_FUTURE",
                workingDays = "MONDAY,FUNDAY",
                // Fine, three halves (more than a day), not a date, not a number.
                // Also no colon, an empty half count, and a third part.
                specialDays = "--12-24:1,--12-31:3,12-25:1,--01-01:x,--12-26,--12-27:,--12-28:1:1",
            )

        val settings = entity.toUserSettings()

        assertEquals(setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST), settings.extras)
        assertEquals(emptySet<RegionalOptOut>(), settings.optOuts)
        assertEquals(setOf(DayOfWeek.MONDAY), settings.schedule.workingDays)
        val expected = mapOf(WorkSchedule.HEILIGABEND to VacationDays.HALF)
        assertEquals(expected, settings.schedule.specialDays)
    }

    @Test
    fun `every option is stored and found again`() {
        val everything =
            UserSettings(
                extras = RegionalExtra.entries.toSet(),
                optOuts = RegionalOptOut.entries.toSet(),
                schedule = WorkSchedule(workingDays = DayOfWeek.entries.toSet()),
            )

        assertEquals(everything, everything.toEntity().toUserSettings())
        Bundesland.entries.forEach {
            assertEquals(it, UserSettings(land = it).toEntity().toUserSettings().land)
        }
    }

    @Test
    fun `a stored enum name has not been renamed`() {
        // The names are stored, so a rename must fail here before it loses somebody's setting.
        assertEquals("MARIAE_HIMMELFAHRT_BAYERN", RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN.name)
        assertEquals("AUGSBURGER_FRIEDENSFEST", RegionalExtra.AUGSBURGER_FRIEDENSFEST.name)
        assertEquals("FRONLEICHNAM_IN_TEILEN", RegionalExtra.FRONLEICHNAM_IN_TEILEN.name)
        assertEquals(setOf("FULL", "HALF"), DayMark.entries.map { it.name }.toSet())
    }

    @Test
    fun `an entitlement with half days survives a round trip`() {
        val entitlement =
            YearEntitlement(
                year = 2026,
                days = VacationDays.of(30),
                carryOver = VacationDays.of(2.5),
                carryOverExpiry = MonthDay.of(6, 30),
            )

        assertEquals(entitlement, entitlement.toEntity().toYearEntitlement())
    }

    @Test
    fun `an expiry in the year after survives a round trip and a wrong offset row is skipped`() {
        val entitlement =
            YearEntitlement(2027, VacationDays.of(30), VacationDays.of(4), carryOverExpiryYears = 1)

        val row = entitlement.toEntity()
        assertEquals(1, row.carryOverExpiryYears)
        assertEquals(entitlement, row.toYearEntitlement())
        assertNull(row.copy(carryOverExpiryYears = 2).toYearEntitlement())
        assertNull(row.copy(carryOverExpiryYears = -1).toYearEntitlement())
    }

    @Test
    fun `a damaged entitlement row is skipped`() {
        val row = YearEntitlement(2026, VacationDays.of(30)).toEntity()

        assertNull(row.copy(halves = -2).toYearEntitlement())
        assertNull(row.copy(carryOverExpiry = "31. März").toYearEntitlement())
    }

    @Test
    fun `marks survive a round trip and bad rows are skipped`() {
        val rows =
            listOf(
                markedDayEntity(LocalDate.of(2026, 12, 24), DayMark.HALF),
                markedDayEntity(LocalDate.of(2026, 12, 28), DayMark.FULL),
                MarkedDayEntity(date = "tomorrow", mark = "FULL"),
                MarkedDayEntity(date = "2026-12-29", mark = "QUARTER"),
            )

        assertEquals(
            mapOf(
                LocalDate.of(2026, 12, 24) to DayMark.HALF,
                LocalDate.of(2026, 12, 28) to DayMark.FULL,
            ),
            rows.toMarkedDays().marks,
        )
    }

    @Test
    fun `the granted flag survives a round trip and a bad row takes its flag with it`() {
        val rows =
            listOf(
                markedDayEntity(LocalDate.of(2026, 12, 24), DayMark.HALF, granted = true),
                markedDayEntity(LocalDate.of(2026, 12, 28), DayMark.FULL),
                MarkedDayEntity(date = "tomorrow", mark = "FULL", granted = true),
                MarkedDayEntity(date = "2026-12-29", mark = "QUARTER", granted = true),
            )

        assertEquals(setOf(LocalDate.of(2026, 12, 24)), rows.toMarkedDays().granted)
    }

    @Test
    fun `the granted days setting is stored and a missing row means off`() {
        assertEquals(true, UserSettings(grantedDays = true).toEntity().grantedDays)
        assertEquals(true, UserSettings(grantedDays = true).toEntity().toUserSettings().grantedDays)
        assertEquals(false, UserSettings().toEntity().toUserSettings().grantedDays)
        assertEquals(false, (null as SettingsEntity?).toUserSettings().grantedDays)
    }
}
