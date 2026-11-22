// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkStatus
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.bridge.BridgeFinder
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.Holiday
import de.remph.urlaubsplaner.core.settings.UserSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** What the screens read from a year: the day looks, the balance, the preview and the trips. */
@RunWith(AndroidJUnit4::class)
class YearViewTest {
    private val fixture = PlannerFixture()
    private val settings = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN)
    private val entitlement = YearEntitlement(2027, VacationDays.of(30), VacationDays.of(10))

    @After
    fun close() = fixture.close()

    private fun view(
        marks: Map<LocalDate, DayMark> =
            mapOf(
                fixture.date("2027-03-08") to DayMark.FULL,
                fixture.date("2027-03-15") to DayMark.HALF,
            ),
        entitlement: YearEntitlement? = this.entitlement,
        settings: UserSettings = this.settings,
        granted: Set<LocalDate> = emptySet(),
    ) = YearView(
        year = 2027,
        today = fixture.date("2027-03-10"),
        settings = settings,
        entitlement = entitlement,
        marks = marks,
        granted = granted,
        calculator =
            settings.publicHolidayCheck()?.let {
                VacationCalculator(fixture.clock, settings.schedule, it)
            },
        schoolData = fixture.schoolHolidays,
        bridgeFinder =
            settings.publicHolidayCheck()?.let {
                BridgeFinder(fixture.clock, settings.schedule, it)
            },
    )

    // Today is 10 March 2027: the 8th is taken, the 15th and the 16th are planned.
    private val grantedSettings = settings.copy(grantedDays = true)
    private val marks =
        mapOf(
            fixture.date("2027-03-08") to DayMark.FULL,
            fixture.date("2027-03-15") to DayMark.FULL,
            fixture.date("2027-03-16") to DayMark.HALF,
        )
    private val grantedDays =
        setOf(fixture.date("2027-03-08"), fixture.date("2027-03-16"))

    @Test
    fun `with the setting on a future granted day is granted and a past one stays taken`() {
        val view = view(marks, settings = grantedSettings, granted = grantedDays)

        assertEquals(MarkStatus.TAKEN, view.statusOn(fixture.date("2027-03-08")))
        assertEquals(MarkStatus.PLANNED, view.statusOn(fixture.date("2027-03-15")))
        assertEquals(MarkStatus.GRANTED, view.statusOn(fixture.date("2027-03-16")))
        assertNull(view.statusOn(fixture.date("2027-03-17")))
        assertTrue(view.look(fixture.date("2027-03-16")).granted)
        assertFalse(view.look(fixture.date("2027-03-15")).granted)
        assertFalse(view.look(fixture.date("2027-03-08")).granted)
    }

    @Test
    fun `with the setting off nothing is granted, though the flags are kept`() {
        val view = view(marks, granted = grantedDays)

        assertFalse(view.grantedEnabled)
        assertEquals(MarkStatus.PLANNED, view.statusOn(fixture.date("2027-03-16")))
        assertFalse(view.look(fixture.date("2027-03-16")).granted)
        assertFalse(view.isGranted(fixture.date("2027-03-16")))
        assertEquals(VacationDays.ZERO, view.grantedPlanned)
    }

    @Test
    fun `the granted share counts only future granted days and the balance is the same`() {
        val withFlags = view(marks, settings = grantedSettings, granted = grantedDays)
        val without = view(marks, settings = grantedSettings)

        assertEquals(VacationDays.HALF, withFlags.grantedPlanned)
        assertEquals(VacationDays.ZERO, without.grantedPlanned)
        assertEquals(without.balance, withFlags.balance)
        assertEquals(VacationDays.of(1.5), withFlags.balance?.planned)
    }

    @Test
    fun `a granted flag on a day that costs nothing does not light the tile`() {
        // Saturday 13 March: a mark there costs nothing, so the tile shows nothing.
        val saturday = fixture.date("2027-03-13")
        val view =
            view(
                mapOf(saturday to DayMark.FULL),
                settings = grantedSettings,
                granted = setOf(saturday),
            )

        assertFalse(view.look(saturday).granted)
        assertEquals(VacationDays.ZERO, view.grantedPlanned)
    }

    @Test
    fun `NRW 2027 has the Fridays after Himmelfahrt and Fronleichnam as bridges`() {
        val view = view()

        assertEquals(
            listOf(fixture.date("2027-05-07"), fixture.date("2027-05-28")),
            view.upcomingBridges.map { it.leave.single() },
        )
        assertEquals(setOf("2027-05-07", "2027-05-28").map(fixture::date).toSet(), view.bridgeDays)
        val groups = view.bridgeGroups
        assertEquals(
            listOf(fixture.date("2027-05-06"), fixture.date("2027-05-27")),
            groups.map { it.anchor },
        )
        assertEquals(Holiday.CHRISTI_HIMMELFAHRT, view.holidayOn(groups.first().anchor))
        assertNull(groups.first().before)
        assertTrue(view.look(fixture.date("2027-05-07")).bridge)
        assertFalse(view.look(fixture.date("2027-05-10")).bridge)
    }

    @Test
    fun `a bridge day that is marked is no longer offered`() {
        val friday = fixture.date("2027-05-07")

        val view = view(marks = mapOf(friday to DayMark.FULL))

        assertEquals(listOf(fixture.date("2027-05-28")), view.bridgeDays.toList())
        assertFalse(view.look(friday).bridge)
    }

    @Test
    fun `a Wednesday holiday gives one group with a bridge before and after`() {
        val view = view(settings = UserSettings(land = Bundesland.SACHSEN))

        // 17 Nov 2027 is Buß- und Bettag, a Wednesday in Sachsen.
        val group = view.bridgeGroups.single { it.anchor == fixture.date("2027-11-17") }
        assertEquals(Holiday.BUSS_UND_BETTAG, view.holidayOn(group.anchor))
        assertEquals(
            listOf(fixture.date("2027-11-15"), fixture.date("2027-11-16")),
            group.before?.leave,
        )
        assertEquals(
            listOf(fixture.date("2027-11-18"), fixture.date("2027-11-19")),
            group.after?.leave,
        )
    }

    @Test
    fun `without a Land there are no bridges, and the first supported year has its own`() {
        assertTrue(view(settings = UserSettings()).upcomingBridges.isEmpty())

        // The finder reads the day before the year; the holiday check answers "no holiday" for it.
        val s = settings
        val first =
            YearView(
                year = 2019,
                today = fixture.date("2019-01-01"),
                settings = s,
                entitlement = null,
                marks = emptyMap(),
                calculator = null,
                schoolData = fixture.schoolHolidays,
                bridgeFinder = BridgeFinder(fixture.clock, s.schedule, s.publicHolidayCheck()!!),
            )
        // Christi Himmelfahrt 2019 was Thursday 30 May in NRW, so Friday the 31st is a bridge.
        assertTrue(first.upcomingBridges.any { it.leave == listOf(fixture.date("2019-05-31")) })
        // The groups for the overview are made when the view is built; the fixture clock is in 2027,
        // so none of 2019 is "best" any more, but building them did not throw.
        assertTrue(first.bridgeGroups.isEmpty())
    }

    @Test
    fun `the year knows its public holidays by date`() {
        val view = view()

        assertEquals(Holiday.KARFREITAG, view.holidays[fixture.date("2027-03-26")])
        assertEquals(Holiday.OSTERMONTAG, view.holidays[fixture.date("2027-03-29")])
        assertNull(view.holidays[fixture.date("2027-03-25")])
    }

    @Test
    fun `a day looks taken before today, planned after, and half when half`() {
        val view = view()

        val taken = view.look(fixture.date("2027-03-08"))
        assertEquals(DayMark.FULL, taken.mark)
        assertTrue(taken.taken)

        val planned = view.look(fixture.date("2027-03-15"))
        assertEquals(DayMark.HALF, planned.mark)
        assertFalse(planned.taken)
    }

    @Test
    fun `a month adds up its taken days up to today and its planned days after`() {
        val view =
            view(
                marks =
                    mapOf(
                        fixture.date("2027-03-08") to DayMark.FULL,
                        fixture.date("2027-03-10") to DayMark.HALF,
                        fixture.date("2027-03-15") to DayMark.FULL,
                        fixture.date("2027-03-16") to DayMark.HALF,
                        fixture.date("2027-03-13") to DayMark.FULL,
                        fixture.date("2027-04-01") to DayMark.FULL,
                    ),
            )

        val march = view.vacationIn(YearMonth.of(2027, 3))

        // Today (the 10th) counts as taken; the Saturday costs nothing; April is not March.
        assertEquals(VacationDays.of(1.5), march.taken)
        assertEquals(VacationDays.of(1.5), march.planned)
        assertEquals(VacationDays.ZERO, view.vacationIn(YearMonth.of(2027, 5)).planned)
    }

    @Test
    fun `a mark that costs nothing shows nothing`() {
        val saturday = fixture.date("2027-03-13")

        val look = view(marks = mapOf(saturday to DayMark.FULL)).look(saturday)

        assertNull(look.mark)
        assertFalse(look.workDay)
    }

    @Test
    fun `today and school holidays are part of the look`() {
        val view = view()

        assertTrue(view.look(fixture.date("2027-03-10")).today)
        assertTrue(view.look(fixture.date("2027-03-30")).school)
        assertFalse(view.look(fixture.date("2027-03-25")).school)
        assertEquals(
            "Osterferien",
            view.schoolHolidaysOn(fixture.date("2027-03-30")).single().nameDe,
        )
    }

    @Test
    fun `school holidays are known only inside the file's coverage`() {
        val view = view()

        assertTrue(view.schoolHolidaysKnown(fixture.date("2027-03-01"), fixture.date("2027-03-31")))
        assertFalse(
            view.schoolHolidaysKnown(fixture.date("2027-12-01"), fixture.date("2028-01-31")),
        )
    }

    @Test
    fun `the balance counts taken and planned marks`() {
        val balance = view().balance!!

        assertEquals(VacationDays.ONE, balance.taken)
        assertEquals(VacationDays.HALF, balance.planned)
        assertEquals(VacationDays.of(38.5), balance.remaining)
    }

    @Test
    fun `without the year's numbers there is no balance but marking still works`() {
        val view = view(entitlement = null)

        assertNull(view.balance)
        assertNull(view.remainingAfter(listOf(fixture.date("2027-03-16")), DayMark.FULL))
        assertTrue(view.canMark(fixture.date("2027-03-16")))
    }

    @Test
    fun `without a Land nothing can be marked`() {
        val view = view(settings = UserSettings())

        assertFalse(view.hasLand)
        assertFalse(view.canMark(fixture.date("2027-03-16")))
        assertEquals(
            emptyList<LocalDate>(),
            view.costlyDays(fixture.date("2027-03-15"), fixture.date("2027-03-19")),
        )
    }

    @Test
    fun `a weekend and a public holiday cannot be marked`() {
        val view = view()

        assertFalse(view.canMark(fixture.date("2027-03-13")))
        assertFalse(view.canMark(fixture.date("2027-03-26")))
        assertTrue(view.canMark(fixture.date("2027-03-25")))
    }

    @Test
    fun `a range of days over Good Friday and a weekend marks only working days`() {
        val days = view().costlyDays(fixture.date("2027-03-24"), fixture.date("2027-03-30"))

        assertEquals(
            listOf("2027-03-24", "2027-03-25", "2027-03-30").map(fixture::date),
            days,
        )
    }

    @Test
    fun `the preview says how many days are left after marking or unmarking`() {
        val view = view()
        val week = (16..18).map { fixture.date("2027-03-$it") }

        assertEquals(VacationDays.of(35.5), view.remainingAfter(week, DayMark.FULL))
        assertEquals(VacationDays.of(37), view.remainingAfter(week, DayMark.HALF))
        assertEquals(
            VacationDays.of(39.5),
            view.remainingAfter(listOf(fixture.date("2027-03-08")), null),
        )
    }

    @Test
    fun `the next trip is the one that includes today or comes next`() {
        val marks =
            mapOf(
                fixture.date("2027-03-02") to DayMark.FULL,
                fixture.date("2027-03-09") to DayMark.FULL,
                fixture.date("2027-03-10") to DayMark.FULL,
                fixture.date("2027-03-11") to DayMark.FULL,
                fixture.date("2027-06-01") to DayMark.FULL,
            )

        val view = view(marks = marks)

        assertEquals(fixture.date("2027-03-09"), view.nextTrip?.start)
        assertEquals(fixture.date("2027-03-11"), view.nextTrip?.end)
        assertNotNull(view.trips.firstOrNull { it.start == fixture.date("2027-06-01") })
    }

    @Test
    fun `no marks in the future give no next trip`() {
        assertNull(view(marks = mapOf(fixture.date("2027-03-02") to DayMark.FULL)).nextTrip)
    }
}
