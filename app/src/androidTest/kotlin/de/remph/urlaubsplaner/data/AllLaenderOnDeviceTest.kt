// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.backup.BackupFile
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.bridge.BridgeFinder
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.ics.IcsExport
import de.remph.urlaubsplaner.core.ics.IcsLanguage
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.targetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Every Bundesland through the rules of `:core` on the device's own Java and Android libraries.
 * `:core` is plain JVM, so the compiler and lint cannot tell when it calls something that Android
 * 11 (API 30, the lowest the app supports) does not have; two such calls crashed the app on the
 * API 30 emulator before 0.9.0 (`LocalDate.datesUntil`, `ByteArrayOutputStream.toString(Charset)`).
 * The API level check in the build catches most of them; this runs the code where it matters,
 * and catches what the check cannot see (method references, for one).
 *
 * Run it on the API 30 emulator especially. It needs no database and does not touch the app's data.
 */
@RunWith(AndroidJUnit4::class)
class AllLaenderOnDeviceTest {
    private val clock = Clock.fixed(Instant.parse("2027-03-10T10:00:00Z"), ZoneOffset.UTC)
    private val summerWeek = (5..9).map { LocalDate.of(2027, 7, it) }.associateWith { DayMark.FULL }

    @Test
    fun `every Land works from holidays to backup`() {
        val schoolHolidays = SchoolHolidayAsset.load(targetApp)

        for (land in Bundesland.entries) {
            val settings =
                UserSettings(
                    land = land,
                    extras = RegionalExtra.entries.filter { land in it.lands }.toSet(),
                    optOuts = RegionalOptOut.entries.filter { land in it.lands }.toSet(),
                )
            val check = settings.publicHolidayCheck()!!
            val calculator = VacationCalculator(clock, settings.schedule, check)
            val finder = BridgeFinder(clock, settings.schedule, check)

            for (year in 2026..2028) {
                assertTrue(
                    "$land $year has no public holidays",
                    settings.publicHolidays(year).isNotEmpty(),
                )
                finder.find(year, summerWeek)
                finder.best(year, 3, summerWeek)
            }

            assertTrue(
                "$land has no school holidays in the bundled file",
                schoolHolidays
                    .between(land, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))
                    .isNotEmpty(),
            )

            val export = IcsExport(clock, calculator)
            assertEquals(
                "$land: the marked week is one trip",
                1,
                export.tripCount(MarkedDays(summerWeek)),
            )
            for (language in IcsLanguage.entries) {
                assertTrue(
                    export.encode(MarkedDays(summerWeek), language).startsWith("BEGIN:VCALENDAR"),
                )
            }

            val data =
                BackupData(
                    settings,
                    listOf(YearEntitlement(2027, VacationDays.of(30))),
                    summerWeek,
                    granted = setOf(LocalDate.of(2027, 7, 9)),
                )
            val bytes =
                ByteArrayOutputStream().also {
                    BackupFile.writeTo(
                        it,
                        data,
                        LocalDate.of(2027, 3, 10),
                    )
                }
            assertEquals(data, BackupFile.readFrom(ByteArrayInputStream(bytes.toByteArray())))
        }
    }
}
