// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import java.time.LocalDate

/**
 * What a marked day is for the user. Only [GRANTED] versus [PLANNED] is stored (the employer has
 * said yes or not yet); [TAKEN] follows from the date, like in [VacationBalance].
 */
enum class MarkStatus {
    /** Up to and including today, whatever the stored flag says: the day has happened. */
    TAKEN,

    /** After today and approved by the employer. Counts as planned in the balance. */
    GRANTED,

    /** After today and not approved yet. */
    PLANNED,
}

/**
 * The marked days with the one extra fact the maths does not need: which of them the employer has
 * granted. The balance, trips and bridges only look at [marks]; [granted] is for what the screens
 * and the export show. Every granted date is also a mark, so a flag never outlives its day.
 */
data class MarkedDays(
    val marks: Map<LocalDate, DayMark> = emptyMap(),
    val granted: Set<LocalDate> = emptySet(),
) {
    init {
        require(marks.keys.containsAll(granted)) { "A granted day must be a marked day" }
    }

    /** Null when [date] is not marked. [today] comes from the clock. */
    fun statusOn(
        date: LocalDate,
        today: LocalDate,
    ): MarkStatus? =
        when {
            date !in marks -> null
            date <= today -> MarkStatus.TAKEN
            date in granted -> MarkStatus.GRANTED
            else -> MarkStatus.PLANNED
        }

    /** The same days without the marks of [dates] (and so without their granted flags). */
    operator fun minus(dates: Collection<LocalDate>): MarkedDays =
        MarkedDays(marks - dates.toSet(), granted - dates.toSet())

    val isEmpty: Boolean get() = marks.isEmpty()
}

/**
 * The cost of the granted days of [year] after today: the part of the planned days the employer
 * has said yes to, for the line "davon genehmigt". It is a share of [VacationBalance.planned], so
 * it never changes the balance.
 */
fun VacationCalculator.grantedPlanned(
    year: Int,
    days: MarkedDays,
    today: LocalDate,
): VacationDays {
    var sum = VacationDays.ZERO
    for (date in days.granted) {
        if (date.year == year && date > today) sum += cost(date, days.marks.getValue(date))
    }
    return sum
}
