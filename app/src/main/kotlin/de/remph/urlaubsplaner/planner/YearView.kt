// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkStatus
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.Trip
import de.remph.urlaubsplaner.core.balance.VacationBalance
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.balance.grantedPlanned
import de.remph.urlaubsplaner.core.bridge.Bridge
import de.remph.urlaubsplaner.core.bridge.BridgeFinder
import de.remph.urlaubsplaner.core.bridge.BridgeGroup
import de.remph.urlaubsplaner.core.bridge.groupedByAnchor
import de.remph.urlaubsplaner.core.holidays.Holiday
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHoliday
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHolidayData
import de.remph.urlaubsplaner.core.settings.UserSettings
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the overview and the calendar show about one year, worked out once: what each day
 * is (holiday, school holiday, marked), the balance and the trips. The screens only draw it.
 *
 * [calculator] is null while no Land is chosen, because then there is no holiday rule and so no
 * cost; the screens send the user to the settings in that case.
 */
class YearView(
    val year: Int,
    val today: LocalDate,
    val settings: UserSettings,
    val entitlement: YearEntitlement?,
    /** Marks from the December before to the December after, so trips over New Year work. */
    val marks: Map<LocalDate, DayMark>,
    /** The marked days the employer has granted, as stored; [grantedOn] and [status] apply the setting. */
    private val granted: Set<LocalDate> = emptySet(),
    private val calculator: VacationCalculator?,
    private val schoolData: SchoolHolidayData,
    private val bridgeFinder: BridgeFinder? = null,
) {
    val hasLand: Boolean get() = settings.land != null

    val holidays: Map<LocalDate, Holiday> =
        settings.publicHolidays(year).associate { it.date to it.holiday }

    private val schoolHolidays: List<SchoolHoliday> =
        settings.land?.let { schoolData.between(it, firstDay, lastDay) }.orEmpty()

    val balance: VacationBalance? =
        if (calculator != null &&
            entitlement != null
        ) {
            calculator.balance(entitlement, marks)
        } else {
            null
        }

    val trips: List<Trip> = calculator?.trips(marks).orEmpty()

    /**
     * Every Brückentag still to come this year (at most 2 vacation days each), for the calendar.
     * Not for the first supported year: the finder looks at the day before the year starts, and
     * the holiday rules do not go back that far.
     */
    val upcomingBridges: List<Bridge> =
        if (bridgeFinder != null && year > PublicHolidays.FIRST_SUPPORTED_YEAR) {
            bridgeFinder.find(year, marks).filter { it.leave.first() > today }
        } else {
            emptyList()
        }

    /** The days to take for any of [upcomingBridges]; a day already marked is not among them. */
    val bridgeDays: Set<LocalDate> = upcomingBridges.flatMap { it.leave }.toSet()

    /** The best few bridges, grouped under the holiday they belong to, for the overview. */
    val bridgeGroups: List<BridgeGroup> =
        if (bridgeFinder != null && year > PublicHolidays.FIRST_SUPPORTED_YEAR) {
            bridgeFinder.best(year, BEST_BRIDGES, marks).groupedByAnchor()
        } else {
            emptyList()
        }

    /** The public holiday on [date], or null: then the day is a special day off of the schedule. */
    fun holidayOn(date: LocalDate): Holiday? =
        settings.publicHolidays(date.year).firstOrNull { it.date == date }?.holiday

    private val firstDay: LocalDate get() = LocalDate.of(year, 1, 1)
    private val lastDay: LocalDate get() = LocalDate.of(year, 12, 31)

    /** Whether the Land's school holidays are known for all of [from] to [to]. */
    fun schoolHolidaysKnown(
        from: LocalDate,
        to: LocalDate,
    ): Boolean = schoolData.covers(from, to)

    fun schoolHolidaysOn(date: LocalDate): List<SchoolHoliday> =
        schoolHolidays.filter { date in it }

    /** The school holidays that touch [from] to [to], for the list under a month. */
    fun schoolHolidaysBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<SchoolHoliday> = schoolHolidays.filter { it.overlaps(from, to) }

    fun markOn(date: LocalDate): DayMark? = marks[date]

    /** Whether the granted days setting is on; off hides every trace of granted days. */
    val grantedEnabled: Boolean get() = settings.grantedDays

    private val markedDays =
        MarkedDays(
            marks,
            if (grantedEnabled) {
                granted.filterTo(mutableSetOf()) {
                    it in marks
                }
            } else {
                emptySet()
            },
        )

    /** Taken, granted or planned, null when [date] is not marked. Never granted while the setting is off. */
    fun statusOn(date: LocalDate): MarkStatus? = markedDays.statusOn(date, today)

    /** The granted flag [date] has, for the day sheet's switch; false while the setting is off. */
    fun isGranted(date: LocalDate): Boolean = date in markedDays.granted

    /** The part of [VacationBalance.planned] that is granted, for the overview; zero while off. */
    val grantedPlanned: VacationDays =
        if (grantedEnabled && calculator != null) {
            calculator.grantedPlanned(year, markedDays, today)
        } else {
            VacationDays.ZERO
        }

    /**
     * The vacation days marked in [month], split at today: up to and including today they are
     * taken, after it planned. Like [look], a mark that costs nothing does not count.
     */
    fun vacationIn(month: YearMonth): MonthVacation {
        var taken = VacationDays.ZERO
        var planned = VacationDays.ZERO
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val mark = markOn(date) ?: continue
            val cost = cost(date, mark)
            if (date.isAfter(today)) planned += cost else taken += cost
        }
        return MonthVacation(taken, planned)
    }

    /** Whether marking [date] would cost anything: a working day that is not a holiday. */
    fun canMark(date: LocalDate): Boolean =
        calculator != null && calculator.cost(date) > VacationDays.ZERO

    /** What [mark] on [date] would cost, zero where it costs nothing. */
    fun cost(
        date: LocalDate,
        mark: DayMark,
    ): VacationDays = calculator?.cost(date, mark) ?: VacationDays.ZERO

    /** The days from [from] to [to] that cost vacation, which is what a range marks. */
    fun costlyDays(
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> = calculator?.costlyDays(from, to).orEmpty()

    /**
     * The days left if [dates] were marked with [mark] (or unmarked when [mark] is null), for the
     * preview in the day sheet. Null without the year's numbers.
     */
    fun remainingAfter(
        dates: Collection<LocalDate>,
        mark: DayMark?,
    ): VacationDays? {
        val entitlement = entitlement ?: return null
        val calculator = calculator ?: return null
        val changed =
            if (mark == null) marks - dates.toSet() else marks + dates.associateWith { mark }
        return calculator.balance(entitlement, changed).remaining
    }

    /** The trip that includes [today] or the first one after it. */
    val nextTrip: Trip? get() = trips.firstOrNull { !it.end.isBefore(today) }
}

/** The days of a month that are taken (until today) and planned (after it). */
class MonthVacation(
    val taken: VacationDays,
    val planned: VacationDays,
)

private const val BEST_BRIDGES = 3
