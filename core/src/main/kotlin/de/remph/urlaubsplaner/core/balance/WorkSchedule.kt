// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/** What a marked vacation day covers: a whole working day or half of one. */
enum class DayMark(
    val value: VacationDays,
) {
    FULL(VacationDays.ONE),
    HALF(VacationDays.HALF),
}

/**
 * Which days are working days at the user's workplace, before public holidays are applied.
 *
 * [specialDays] says how much of a working day a calendar day is worth where the workplace
 * differs from the normal week: [VacationDays.HALF] for a half working day, [VacationDays.ZERO]
 * for a day off that is not a public holiday, like Heiligabend and Silvester in many companies.
 * It is stored as a map, not two flags, because employers differ day by day.
 */
data class WorkSchedule(
    val workingDays: Set<DayOfWeek> =
        setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        ),
    val specialDays: Map<MonthDay, VacationDays> = emptyMap(),
) {
    init {
        require(specialDays.values.all { it >= VacationDays.ZERO && it <= VacationDays.ONE }) {
            "A special day is worth between no and a full working day"
        }
    }

    /** How much of a working day [date] is: one, half, or zero on days that are not worked. */
    fun workingValue(date: LocalDate): VacationDays =
        when {
            date.dayOfWeek !in workingDays -> VacationDays.ZERO
            else -> specialDays[MonthDay.from(date)] ?: VacationDays.ONE
        }

    companion object {
        val HEILIGABEND: MonthDay = MonthDay.of(12, 24)
        val SILVESTER: MonthDay = MonthDay.of(12, 31)
    }
}
