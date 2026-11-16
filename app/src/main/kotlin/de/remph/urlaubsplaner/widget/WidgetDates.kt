// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Short dates for a widget, where every character counts. [year] is the current year: a date in
 * it has no year, one in another year gets it, because "14.01." in December means next January.
 */
object WidgetDates {
    /** "14.04." as in the mock-up the maintainer approved, the same in German and English. */
    fun short(
        date: LocalDate,
        year: Int,
    ): String =
        if (date.year == year) {
            "%02d.%02d.".format(date.dayOfMonth, date.monthValue)
        } else {
            "%02d.%02d.%d".format(date.dayOfMonth, date.monthValue, date.year)
        }

    /**
     * "14.–16. April" or "14–16 April" in one month, "30. April – 2. Mai" across months, with the
     * years when the trip is not entirely in [year]. [dayPattern] is "d." in German and "d" in
     * English (a string resource, so the order and the full stop stay with the language).
     */
    fun range(
        start: LocalDate,
        end: LocalDate,
        year: Int,
        locale: Locale,
        dayPattern: String,
    ): String {
        fun format(
            date: LocalDate,
            pattern: String,
        ) = DateTimeFormatter.ofPattern(pattern, locale).format(date)

        val sameMonth = start.year == end.year && start.month == end.month
        if (start == end || sameMonth) {
            val yearPart = if (end.year != year) " yyyy" else ""
            val month = format(end, "MMMM$yearPart")
            return if (start == end) {
                "${format(end, dayPattern)} $month"
            } else {
                "${format(start, dayPattern)}–${format(end, dayPattern)} $month"
            }
        }
        val startYear = if (start.year != end.year) " yyyy" else ""
        val endYear = if (end.year != year || start.year != end.year) " yyyy" else ""
        return "${format(
            start,
            "$dayPattern MMMM$startYear",
        )} – ${format(end, "$dayPattern MMMM$endYear")}"
    }
}
