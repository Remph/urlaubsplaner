// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.settings

import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.PublicHoliday
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything the rules need to know about the user, apart from the vacation itself. It lives in
 * `:core` so the backup file can use the same type as the database.
 *
 * [land] is null until the user has chosen one: a default would show the holidays of the wrong
 * Land without anyone noticing. The app language is not here; the system remembers it.
 */
data class UserSettings(
    val land: Bundesland? = null,
    val extras: Set<RegionalExtra> = emptySet(),
    val optOuts: Set<RegionalOptOut> = emptySet(),
    val schedule: WorkSchedule = WorkSchedule(),
    /**
     * Off by default (the maintainer, 2026-10-25): when on, a new year's form suggests the days left over
     * from the year before as Resturlaub. Only a suggestion, the employer's figure still wins.
     */
    val suggestCarryOver: Boolean = false,
    /**
     * Off by default (the maintainer, 2026-10-27): when on, a planned day can be marked "granted"
     * (the employer has said yes) and the screens, the overview and the calendar export show the
     * difference. Turning it off only hides it; the granted flags stay stored.
     */
    val grantedDays: Boolean = false,
) {
    /**
     * The public holidays of [year] for this user; none while no Land is chosen, and none before
     * [PublicHolidays.FIRST_SUPPORTED_YEAR], whose rules differ and are not kept. This is the one
     * place that guards the first year, so no caller has to.
     */
    fun publicHolidays(year: Int): List<PublicHoliday> =
        if (year < PublicHolidays.FIRST_SUPPORTED_YEAR) {
            emptyList()
        } else {
            land?.let { PublicHolidays.of(it, year, extras, optOuts) }.orEmpty()
        }

    /** The first public holiday on or after [from], looking into the next year too. */
    fun nextPublicHoliday(from: LocalDate): PublicHoliday? =
        (publicHolidays(from.year) + publicHolidays(from.year + 1)).firstOrNull { it.date >= from }

    /**
     * Whether a date is a public holiday for this user, or null while no Land is chosen. One
     * function for marked days, the balance and Brückentage, so they never disagree. A date
     * before 2019 is no public holiday (see [publicHolidays]), so a mark from a hand-made backup
     * or an old database cannot make it throw.
     */
    fun publicHolidayCheck(): ((LocalDate) -> Boolean)? {
        val land = land ?: return null
        val years = ConcurrentHashMap<Int, Set<LocalDate>>()
        return { date ->
            date in
                years.getOrPut(date.year) { publicHolidays(date.year).map { it.date }.toSet() }
        }
    }
}
