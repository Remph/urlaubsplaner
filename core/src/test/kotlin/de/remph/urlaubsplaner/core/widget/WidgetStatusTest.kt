// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.widget

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset

// 2027 calendar used below: 10 March is a Wednesday, 15 March a Monday, 19 March a Friday.
class WidgetStatusTest {
    private fun clockOn(date: String) =
        Clock.fixed(Instant.parse("${date}T10:00:00Z"), ZoneOffset.UTC)

    private val clock = clockOn("2027-03-10")
    private val bayern = UserSettings(land = Bundesland.BAYERN)
    private val year2027 = YearEntitlement(2027, VacationDays.of(30))

    private fun day(
        month: Int,
        dayOfMonth: Int,
    ) = LocalDate.of(2027, month, dayOfMonth)

    private fun full(vararg dates: LocalDate): Map<LocalDate, DayMark> =
        dates.associateWith { DayMark.FULL }

    private fun week(start: LocalDate) = (0L..4L).map { start.plusDays(it) }.toTypedArray()

    private fun ready(
        settings: UserSettings = bayern,
        entitlements: List<YearEntitlement> = listOf(year2027),
        marks: Map<LocalDate, DayMark> = emptyMap(),
        clock: Clock = this.clock,
    ) = assertInstanceOf(
        WidgetStatus.Ready::class.java,
        WidgetStatus.of(clock, settings, entitlements, marks),
    )

    @Test
    fun `without a Land the widget asks for one`() {
        val status = WidgetStatus.of(clock, UserSettings(), listOf(year2027), emptyMap())
        assertEquals(WidgetStatus.NoLand, status)
    }

    @Test
    fun `without the numbers of this year the widget asks for them`() {
        val status =
            WidgetStatus.of(
                clock,
                bayern,
                listOf(YearEntitlement(2026, VacationDays.of(30))),
                emptyMap(),
            )
        assertEquals(WidgetStatus.NoEntitlement(2027), status)
    }

    @Test
    fun `the year follows the clock, so the new year needs its own numbers`() {
        val status =
            WidgetStatus.of(clockOn("2028-01-02"), bayern, listOf(year2027), emptyMap())
        assertEquals(WidgetStatus.NoEntitlement(2028), status)
    }

    @Test
    fun `days left, taken, planned and the total with Resturlaub`() {
        val entitlement = YearEntitlement(2027, VacationDays.of(30), VacationDays.of(5))
        // Taken: Mon and Tue before today. Planned: the week of 15 March.
        val marks = full(day(3, 8), day(3, 9), *week(day(3, 15)))
        val status = ready(entitlements = listOf(entitlement), marks = marks)

        assertEquals(2027, status.year)
        assertEquals(VacationDays.of(2), status.balance.taken)
        assertEquals(VacationDays.of(5), status.balance.planned)
        assertEquals(VacationDays.of(28), status.balance.remaining)
        assertEquals(VacationDays.of(35), status.total)
    }

    @Test
    fun `half days count as halves`() {
        val marks = mapOf(day(3, 15) to DayMark.HALF)
        val status = ready(marks = marks)

        assertEquals(VacationDays.of(29.5), status.balance.remaining)
    }

    @Test
    fun `the next trip is the first one that has not ended`() {
        val marks = full(day(3, 8), day(3, 9), *week(day(3, 15)), *week(day(6, 7)))
        val status = ready(marks = marks)

        assertEquals(day(3, 15), status.nextTrip!!.start)
        assertEquals(day(3, 19), status.nextTrip.end)
        assertFalse(status.onTrip)
    }

    @Test
    fun `a trip that includes today is the current one`() {
        val status = ready(marks = full(*week(day(3, 8))))

        assertEquals(day(3, 8), status.nextTrip!!.start)
        assertTrue(status.onTrip)
    }

    @Test
    fun `the trip's last day still counts as being on vacation`() {
        val status = ready(marks = full(*week(day(3, 8))), clock = clockOn("2027-03-12"))

        assertTrue(status.onTrip)
    }

    @Test
    fun `no trip when every marked day is over`() {
        val status = ready(marks = full(day(3, 8), day(3, 9)))

        assertNull(status.nextTrip)
        assertFalse(status.onTrip)
    }

    @Test
    fun `a trip over new year is found from the year before`() {
        val marks =
            mapOf(
                LocalDate.of(2026, 12, 30) to DayMark.FULL,
                LocalDate.of(2026, 12, 31) to DayMark.FULL,
                LocalDate.of(2027, 1, 4) to DayMark.FULL,
            )
        val status = ready(marks = marks, clock = clockOn("2027-01-01"))

        assertEquals(LocalDate.of(2026, 12, 30), status.nextTrip!!.start)
        assertTrue(status.onTrip)
    }

    @Test
    fun `an overbooked year shows a negative number`() {
        val tiny = YearEntitlement(2027, VacationDays.of(2))
        val status = ready(entitlements = listOf(tiny), marks = full(*week(day(3, 15))))

        assertEquals(VacationDays.of(-3), status.balance.remaining)
    }

    @Test
    fun `Resturlaub that is still open warns until its Verfall day`() {
        val entitlement =
            YearEntitlement(2027, VacationDays.of(30), VacationDays.of(3), MonthDay.of(3, 31))
        val status = ready(entitlements = listOf(entitlement))

        assertEquals(VacationDays.of(3), status.carryOverWarning!!.days)
        assertEquals(day(3, 31), status.carryOverWarning.expiry)
    }

    @Test
    fun `the warning is there on the Verfall day itself and gone the day after`() {
        val entitlement =
            YearEntitlement(2027, VacationDays.of(30), VacationDays.of(3), MonthDay.of(3, 31))

        assertEquals(
            VacationDays.of(3),
            ready(entitlements = listOf(entitlement), clock = clockOn("2027-03-31"))
                .carryOverWarning!!
                .days,
        )
        assertNull(
            ready(
                entitlements = listOf(entitlement),
                clock = clockOn("2027-04-01"),
            ).carryOverWarning,
        )
    }

    @Test
    fun `Resturlaub that is used up gives no warning`() {
        val entitlement =
            YearEntitlement(2027, VacationDays.of(30), VacationDays.of(3), MonthDay.of(3, 31))
        val marks = full(day(3, 15), day(3, 16), day(3, 17))
        val status = ready(entitlements = listOf(entitlement), marks = marks)

        assertNull(status.carryOverWarning)
    }

    @Test
    fun `without Resturlaub there is no warning`() {
        assertNull(ready().carryOverWarning)
    }

    @Test
    fun `a mark from before 2019 does not make the status fail`() {
        val marks = mapOf(LocalDate.of(2018, 6, 4) to DayMark.FULL)

        assertEquals(VacationDays.of(30), ready(marks = marks).balance.remaining)
    }
}
