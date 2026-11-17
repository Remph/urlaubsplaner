// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DateTimeException
import java.time.LocalDate
import java.time.MonthDay
import java.util.Locale

/**
 * How amounts and the Verfall day are typed in and shown. In German the comma separates the
 * decimals ("12,5 Tage"), in English the point; the input accepts both so a phone keyboard with
 * the wrong layout does not get in the way.
 */
object AmountText {
    /** More than a year has days is a typo; it also keeps the sums far from overflowing. */
    private const val MAX_DAYS = 366

    fun format(
        days: VacationDays,
        locale: Locale,
    ): String = DecimalFormat("0.#", DecimalFormatSymbols(locale)).format(days.days)

    /**
     * Whole or half days between 0 and 366, like "12", "12,5" or "12.5". Null for anything else
     * ("12,3", a negative number, empty text), so the form can say what is wrong.
     */
    fun parseDays(text: String): VacationDays? {
        val clean = text.trim().replace(',', '.')
        if (!DAYS_PATTERN.matches(clean)) return null
        val days = clean.toDouble()
        if (days > MAX_DAYS || days * 2 != Math.rint(days * 2)) return null
        return VacationDays.of(days)
    }

    /** "31.03." for the Verfall day; the same in both languages. */
    fun formatDayMonth(day: MonthDay): String = "%02d.%02d.".format(day.dayOfMonth, day.monthValue)

    /** "31.03.2028" for a Verfall day in a later year, where the year is what matters. */
    fun formatDate(date: LocalDate): String = formatDayMonth(MonthDay.from(date)) + date.year

    /** "31.3.", "31.03" and "31.03." all mean 31 March. Null if it is not a real day. */
    fun parseDayMonth(text: String): MonthDay? {
        val match = DAY_MONTH_PATTERN.matchEntire(text.trim()) ?: return null
        val (day, month) = match.destructured
        return try {
            MonthDay.of(month.toInt(), day.toInt())
        } catch (_: DateTimeException) {
            null
        }
    }

    private val DAYS_PATTERN = Regex("""\d{1,3}(\.\d)?""")
    private val DAY_MONTH_PATTERN = Regex("""(\d{1,2})\.(\d{1,2})\.?""")
}
