// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.bridge

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A Brückentag: the working days in [leave] sit between two stretches of free days, and taking
 * them joins both into one long stretch from [start] to [end] (both days off, inclusive).
 *
 * [leave] holds only days that still cost something; days the user already marked are part of
 * the free stretch, not of [leave]. [cost] is what [leave] costs, half days included.
 *
 * [anchor] is the public holiday (or special day off) the bridge is built around: the day of the
 * stretch that loses a working day, the nearest one to [leave] if there are several. It is what a
 * screen names the bridge after, and [isBeforeAnchor] says on which side the leave days lie.
 */
data class Bridge(
    val leave: List<LocalDate>,
    val cost: VacationDays,
    val start: LocalDate,
    val end: LocalDate,
    val anchor: LocalDate,
) {
    /** The leave days come before [anchor] ("davor") rather than after it ("danach"). */
    val isBeforeAnchor: Boolean get() = leave.last() < anchor

    /** Calendar days off in a row, [leave] included. */
    val daysOff: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    /** How well the vacation is spent; a bridge always costs something. */
    val daysOffPerVacationDay: Double get() = daysOff / cost.days
}

/**
 * Finds the Brückentage of a year for one user. A day is free when it costs no vacation
 * ([VacationCalculator.cost] is zero: weekend, public holiday, a day off in the [WorkSchedule],
 * or a day the user marked). A bridge is a gap of days that cost something between two free
 * stretches, where the whole gap costs at most the limit. It must be worth a bridge: the joined
 * stretch contains a public holiday or special day off that falls on a working weekday, so plain
 * weekends are never suggested and a holiday on a day the user never works changes nothing.
 *
 * Only a whole gap counts, since taking part of it leaves a working day in the middle. A year's
 * bridges are those whose first day to take lies in that year. The stretch may reach into the
 * neighbouring years; before `FIRST_SUPPORTED_YEAR` the holiday check answers "no holiday", and
 * the walk back stops there.
 *
 * "Today" comes from [clock] and is used only by [best].
 */
class BridgeFinder(
    private val clock: Clock,
    private val schedule: WorkSchedule = WorkSchedule(),
    private val isPublicHoliday: (LocalDate) -> Boolean,
) {
    private val calculator = VacationCalculator(clock, schedule, isPublicHoliday)

    /**
     * All bridges of [year] that cost at most [maxCost], sorted by date. [marks] are the days the
     * user has booked or planned already; they count as free time and cost nothing more.
     */
    fun find(
        year: Int,
        marks: Map<LocalDate, DayMark> = emptyMap(),
        maxCost: VacationDays = VacationDays.of(2),
    ): List<Bridge> {
        require(maxCost > VacationDays.ZERO) { "The limit must be more than zero" }

        // A half marked day still costs the other half, so the gap stays open.
        fun remaining(date: LocalDate): VacationDays {
            val marked = marks[date]?.let { calculator.cost(date, it) } ?: VacationDays.ZERO
            return calculator.cost(date, DayMark.FULL) - marked
        }

        fun isFree(date: LocalDate) = remaining(date) == VacationDays.ZERO

        // A holiday or a day off on a weekday the user works loses a working day. Marks and
        // weekends do not: they alone would make every vacation day beside a weekend a bridge.
        fun losesWorkingDay(date: LocalDate) =
            date.dayOfWeek in schedule.workingDays &&
                (isPublicHoliday(date) || schedule.workingValue(date) == VacationDays.ZERO)

        val firstSupportedDay = LocalDate.of(PublicHolidays.FIRST_SUPPORTED_YEAR, 1, 1)
        val lastDay = LocalDate.of(year, 12, 31)
        var day = LocalDate.of(year, 1, 1)
        // A gap that began in the year before belongs to that year; step over it.
        if (!isFree(day) && !isFree(day.minusDays(1))) {
            while (day <= lastDay && !isFree(day)) day = day.plusDays(1)
        }

        val bridges = mutableListOf<Bridge>()
        while (day <= lastDay) {
            if (isFree(day)) {
                day = day.plusDays(1)
                continue
            }
            val leave = mutableListOf<LocalDate>()
            var cost = VacationDays.ZERO
            while (cost <= maxCost && !isFree(day)) {
                leave += day
                cost += remaining(day)
                day = day.plusDays(1)
            }
            if (cost > maxCost) {
                // Too dear. The rest of the gap is no bridge either, so skip to its end.
                while (day <= lastDay && !isFree(day)) day = day.plusDays(1)
                continue
            }

            // A schedule with many days off can make the free stretch reach back for years; the
            // holiday rules throw before their first year, so the walk stops there.
            var start = leave.first().minusDays(1)
            while (start > firstSupportedDay && isFree(start.minusDays(1))) {
                start = start.minusDays(1)
            }
            var end = day
            while (isFree(end.plusDays(1))) end = end.plusDays(1)
            val anchor =
                // Not LocalDate.datesUntil: it is Java 9 and missing on Android 11 (minSdk 30).
                generateSequence(start) { it.plusDays(1) }
                    .takeWhile { it <= end }
                    .filter { losesWorkingDay(it) }
                    .minWithOrNull(compareBy({ distance(it, leave) }, { it }))
            if (anchor != null) bridges += Bridge(leave, cost, start, end, anchor)
            day = end.plusDays(1)
        }
        return bridges
    }

    /** Days between [day] and the nearest of the (consecutive) [leave] days. */
    private fun distance(
        day: LocalDate,
        leave: List<LocalDate>,
    ): Long =
        when {
            day < leave.first() -> ChronoUnit.DAYS.between(day, leave.first())
            day > leave.last() -> ChronoUnit.DAYS.between(leave.last(), day)
            else -> 0
        }

    /**
     * The [limit] best bridges of [year] still to come: most days off per vacation day first, the
     * earlier one on a tie. A bridge counts as to come when its first day to take is after today,
     * because days up to and including today count as taken.
     */
    fun best(
        year: Int,
        limit: Int = 3,
        marks: Map<LocalDate, DayMark> = emptyMap(),
        maxCost: VacationDays = VacationDays.of(2),
    ): List<Bridge> {
        val today = LocalDate.now(clock)
        return rank(find(year, marks, maxCost).filter { it.leave.first() > today }, limit)
    }

    /**
     * The best [limit] of [upcoming] bridges (found by [find] and already cut to those still to
     * come): most days off per vacation day first, the earlier one on a tie. For a caller that
     * has the list already and must not search a second time.
     */
    fun rank(
        upcoming: List<Bridge>,
        limit: Int = 3,
    ): List<Bridge> {
        require(limit >= 0) { "The number of bridges cannot be negative" }
        return upcoming
            .sortedWith(
                compareByDescending<Bridge> { it.daysOffPerVacationDay }.thenBy { it.start },
            ).take(limit)
    }
}
