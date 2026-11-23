// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

import java.time.LocalDate

/** A public holiday on a date. The name is looked up from [holiday] in the app's string resources. */
data class PublicHoliday(
    val date: LocalDate,
    val holiday: Holiday,
)

object PublicHolidays {
    /** Earlier years differ (Reformationstag 2017, Weltkindertag and Frauentag came in 2019). */
    const val FIRST_SUPPORTED_YEAR = 2019

    /**
     * The public holidays of [land] in [year], sorted by date. [extras] add holidays that only
     * some communities have; [optOuts] remove statewide ones that some communities lack.
     */
    fun of(
        land: Bundesland,
        year: Int,
        extras: Set<RegionalExtra> = emptySet(),
        optOuts: Set<RegionalOptOut> = emptySet(),
    ): List<PublicHoliday> {
        require(year >= FIRST_SUPPORTED_YEAR) {
            "Public holidays are supported from $FIRST_SUPPORTED_YEAR, got $year"
        }
        val optedOut = optOuts.filter { land in it.lands }.map { it.holiday }.toSet()
        val statewide =
            HolidayRules.all
                .filter { land in it.lands && it.appliesInYear(year) }
                .map { it.holiday }
                .filter { it !in optedOut }
        val regional = extras.filter { land in it.lands }.map { it.holiday }
        return (statewide + regional)
            .distinct()
            .map { PublicHoliday(it.date(year), it) }
            .sortedBy { it.date }
    }

    /** Whether [date] is a public holiday in [land]; never, before [FIRST_SUPPORTED_YEAR]. */
    fun isHoliday(
        land: Bundesland,
        date: LocalDate,
        extras: Set<RegionalExtra> = emptySet(),
        optOuts: Set<RegionalOptOut> = emptySet(),
    ): Boolean =
        date.year >= FIRST_SUPPORTED_YEAR &&
            of(land, date.year, extras, optOuts).any { it.date == date }
}
