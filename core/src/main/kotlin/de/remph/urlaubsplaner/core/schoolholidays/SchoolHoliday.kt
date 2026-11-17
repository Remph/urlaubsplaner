// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.schoolholidays

import de.remph.urlaubsplaner.core.holidays.Bundesland
import java.time.LocalDate

/**
 * One school holiday period, or a single school-free day, of a Bundesland. [from] and [to] are
 * both inclusive and equal for a single day. The period can start in the year before or end in
 * the year after the one it is named for (Weihnachtsferien).
 *
 * The names come from the data file, in German and English, so the file can add a new kind of
 * holiday without a code change.
 */
data class SchoolHoliday(
    val land: Bundesland,
    val from: LocalDate,
    val to: LocalDate,
    val nameDe: String,
    val nameEn: String,
) {
    init {
        require(!to.isBefore(from)) { "School holiday ends before it starts: $from..$to" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)

    fun overlaps(
        start: LocalDate,
        end: LocalDate,
    ): Boolean = !to.isBefore(start) && !from.isAfter(end)
}
