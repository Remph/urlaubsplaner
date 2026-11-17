// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay

/**
 * The numbers the user enters for one calendar year. [carryOver] (Resturlaub) is typed in, not
 * computed from the year before, because the employer's figure is the one that counts. It can be
 * used up to and including [carryOverExpiry] (31 March by default, the usual German rule) and is
 * lost after that. Some employers let it run into a later year (a 2027 Resturlaub that expires
 * on 31 March 2028); [carryOverExpiryYears] is how many years after [year] that is, 0 or 1.
 */
data class YearEntitlement(
    val year: Int,
    val days: VacationDays,
    val carryOver: VacationDays = VacationDays.ZERO,
    val carryOverExpiry: MonthDay = MonthDay.of(3, 31),
    val carryOverExpiryYears: Int = 0,
) {
    init {
        require(days >= VacationDays.ZERO) { "The entitlement cannot be negative" }
        require(carryOver >= VacationDays.ZERO) { "The Resturlaub cannot be negative" }
        require(carryOverExpiryYears in 0..MAX_EXPIRY_YEARS) {
            "The Resturlaub can run at most $MAX_EXPIRY_YEARS year after the entitlement's year"
        }
    }

    /** The last day the Resturlaub can be used. */
    val carryOverExpiryDate: LocalDate get() = carryOverExpiry.atYear(year + carryOverExpiryYears)

    companion object {
        const val MAX_EXPIRY_YEARS = 1
    }
}

/** The status of one year. Every amount is in [VacationDays]; [remaining] can be negative. */
data class VacationBalance(
    val entitlement: VacationDays,
    val carryOver: VacationDays,
    val carryOverExpiry: LocalDate,
    /** Resturlaub used by marked days up to and including [carryOverExpiry]. */
    val carryOverUsed: VacationDays,
    /** Resturlaub that expired unused; zero until the day after [carryOverExpiry]. */
    val carryOverLost: VacationDays,
    /** Cost of marked days up to and including today. */
    val taken: VacationDays,
    /** Cost of marked days after today. */
    val planned: VacationDays,
    /**
     * What can still be booked: entitlement plus Resturlaub that has not expired, minus taken and
     * planned. Until [carryOverExpiry] passes this still counts [carryOverOpen], which only days up
     * to that date can use; a plan beyond it shows as overbooked the day after.
     */
    val remaining: VacationDays,
) {
    /** Resturlaub that is still usable and not yet used or planned. */
    val carryOverOpen: VacationDays get() = carryOver - carryOverUsed - carryOverLost
}

/**
 * Marked days in a row, from the first to the last marked day, and what they cost together.
 * Free days between them (a weekend) are inside, free days around them are not.
 */
data class Trip(
    val start: LocalDate,
    val end: LocalDate,
    val cost: VacationDays,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Works out what marked days cost and the balance of a year.
 *
 * A marked day costs vacation only on a working day that is not a public holiday, so weekends
 * and holidays in a marked week are free. [isPublicHoliday] is a function, not a Land plus
 * extras and opt-outs, so the regional extras and the Bayern opt-out reach this code without
 * changing it. With `PublicHolidays.isHoliday` it throws for dates before 2019.
 *
 * "Today" comes from [clock]: marked days up to and including today count as taken, later ones
 * as planned.
 */
class VacationCalculator(
    private val clock: Clock,
    private val schedule: WorkSchedule = WorkSchedule(),
    private val isPublicHoliday: (LocalDate) -> Boolean,
) {
    /**
     * The vacation [mark] on [date] costs. A half working day (Heiligabend, say) caps the cost at
     * half a day, so a full and a half mark cost the same there.
     */
    fun cost(
        date: LocalDate,
        mark: DayMark = DayMark.FULL,
    ): VacationDays {
        if (isPublicHoliday(date)) return VacationDays.ZERO
        return minOf(mark.value, schedule.workingValue(date))
    }

    /**
     * The days from [from] to [to] (both included) that cost vacation when marked: working days
     * that are not public holidays. Marking a range skips the rest (weekends, holidays, days
     * off), which costs nothing and would only clutter the list of marked days.
     */
    fun costlyDays(
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        require(!to.isBefore(from)) { "Range ends before it starts: $from..$to" }
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { cost(it) > VacationDays.ZERO }
            .toList()
    }

    /**
     * Groups the marked days into trips. Marked days belong to one trip when only days that cost
     * nothing (weekend, holiday, day off) lie between them, so a Monday to Friday week with a
     * weekend and a second week is one trip. Marks that cost nothing are not part of a trip.
     * The trips are sorted by start.
     */
    fun trips(marks: Map<LocalDate, DayMark>): List<Trip> {
        val costly =
            marks
                .map { (date, mark) -> date to cost(date, mark) }
                .filter { it.second > VacationDays.ZERO }
                .sortedBy { it.first }
        val trips = mutableListOf<Trip>()
        var start: LocalDate? = null
        var end: LocalDate? = null
        var cost = VacationDays.ZERO
        for ((date, dayCost) in costly) {
            if (end != null && !onlyFreeDaysBetween(end, date)) {
                trips += Trip(start!!, end, cost)
                start = null
                cost = VacationDays.ZERO
            }
            if (start == null) start = date
            end = date
            cost += dayCost
        }
        if (start != null && end != null) trips += Trip(start, end, cost)
        return trips
    }

    private fun onlyFreeDaysBetween(
        first: LocalDate,
        second: LocalDate,
    ): Boolean =
        generateSequence(first.plusDays(1)) { it.plusDays(1) }
            .takeWhile { it.isBefore(second) }
            .all { cost(it) == VacationDays.ZERO }

    /** The balance of [entitlement]'s year. Marks from other years are ignored. */
    fun balance(
        entitlement: YearEntitlement,
        marks: Map<LocalDate, DayMark>,
    ): VacationBalance {
        val today = LocalDate.now(clock)
        val expiry = entitlement.carryOverExpiryDate
        val costs =
            marks
                .filterKeys { it.year == entitlement.year }
                .map { (date, mark) -> date to cost(date, mark) }

        fun total(include: (LocalDate) -> Boolean): VacationDays {
            var sum = VacationDays.ZERO
            for ((date, cost) in costs) if (include(date)) sum += cost
            return sum
        }

        val taken = total { it <= today }
        val planned = total { it > today }
        val carryOverUsed = minOf(entitlement.carryOver, total { it <= expiry })
        val carryOverLost =
            if (today > expiry) entitlement.carryOver - carryOverUsed else VacationDays.ZERO
        return VacationBalance(
            entitlement = entitlement.days,
            carryOver = entitlement.carryOver,
            carryOverExpiry = expiry,
            carryOverUsed = carryOverUsed,
            carryOverLost = carryOverLost,
            taken = taken,
            planned = planned,
            remaining = entitlement.days + entitlement.carryOver - carryOverLost - taken - planned,
        )
    }
}
