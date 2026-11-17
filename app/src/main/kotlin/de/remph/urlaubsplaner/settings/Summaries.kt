// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import de.remph.urlaubsplaner.core.balance.AmountText
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.planner.shortText
import java.time.DayOfWeek
import java.util.Locale

/**
 * "Mo bis Fr" for a run of three or more days in a row, otherwise "Mo, Mi, Fr". [range] puts the
 * two ends together ("%1$s bis %2$s"), because that word depends on the language.
 */
fun workDaysSummary(
    days: Set<DayOfWeek>,
    locale: Locale,
    range: (first: String, last: String) -> String,
): String {
    val sorted = days.sorted()
    if (sorted.isEmpty()) return ""
    val name = { day: DayOfWeek -> day.shortText(locale) }
    val inARow = sorted.zipWithNext().all { (a, b) -> b.value == a.value + 1 }
    return if (sorted.size >= 3 && inARow) {
        range(name(sorted.first()), name(sorted.last()))
    } else {
        sorted.joinToString(", ", transform = name)
    }
}

/** "1 Tag", "12,5 Tage": the unit follows the amount, one day is the only singular. */
fun daysWithUnit(
    days: VacationDays,
    locale: Locale,
    one: (String) -> String,
    other: (String) -> String,
): String {
    val text = AmountText.format(days, locale)
    return if (days == VacationDays.ONE) one(text) else other(text)
}
