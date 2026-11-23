// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.AmountText
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHoliday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** "14.07.2027" in German, "Jul 14, 2027" in English. */
fun LocalDate.mediumText(locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(this)

/** "14.07.2027 bis 18.07.2027". */
@Composable
fun rangeText(
    from: LocalDate,
    to: LocalDate,
    locale: Locale,
): String = stringResource(R.string.date_range, from.mediumText(locale), to.mediumText(locale))

/** "Mittwoch, 14. Juli 2027". */
fun LocalDate.fullText(locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(this)

/**
 * The data file carries German and English names; any other language gets English. The English
 * names arrive in Title Case ("Easter Holidays"); the app writes sentence case everywhere else,
 * so they are shown as "Easter holidays". The bundled (ODbL) file itself is not changed.
 */
fun SchoolHoliday.nameIn(locale: Locale): String =
    if (locale.language == "de") nameDe else nameEn.sentenceCase()

// Words that keep their capital in the middle of an English name: feasts, days and months.
private val PROPER_WORDS =
    setOf(
        "Easter",
        "Christmas",
        "Pentecost",
        "Ascension",
        "Maundy",
        "Thursday",
        "Reformation",
        "Repentance",
        "October",
        "May",
    )

internal fun String.sentenceCase(): String {
    val words = split(' ')
    return words
        .mapIndexed { index, word ->
            val keepDay = word == "Day" && words.getOrNull(index - 1) in PROPER_WORDS
            when {
                // The first word keeps its own capital ("Easter") but "Mid-Year" becomes "Mid-year".
                index == 0 && '-' in word -> {
                    word.substringBefore('-') + "-" + word.substringAfter('-').lowercase()
                }

                index == 0 -> {
                    word
                }

                word in PROPER_WORDS || keepDay -> {
                    word
                }

                else -> {
                    word.lowercase()
                }
            }
        }.joinToString(" ")
}

fun VacationDays.text(locale: Locale): String = AmountText.format(this, locale)

/**
 * The plural form for "N Tage": only exactly one day takes "one"; 0,5 and 1,5 read "0,5 Tage",
 * so they take "other". Android's own rule would pick "one" for 1,5 in some languages.
 */
fun VacationDays.pluralQuantity(): Int = if (this == VacationDays.ONE) 1 else 2

/** "Mi 03.10." in German, "Wed, Oct 3" in English: short enough for a line, with the weekday. */
fun LocalDate.weekdayDateText(locale: Locale): String =
    if (locale.language == "de") {
        "${weekdayText(locale)} ${DateTimeFormatter.ofPattern("dd.MM.", locale).format(this)}"
    } else {
        "${weekdayText(locale)}, ${DateTimeFormatter.ofPattern("MMM d", locale).format(this)}"
    }

/** "Mi" or "Wed": the short weekday without the German full stop. */
fun LocalDate.weekdayText(locale: Locale): String = dayOfWeek.shortText(locale)

/** The short name Android gives German weekdays is "Mi." with a full stop; the app drops it. */
fun DayOfWeek.shortText(locale: Locale): String =
    getDisplayName(TextStyle.SHORT, locale).trimEnd('.')

/** "März 2027": the month's name on its own (German has "März", not "des März") and the year. */
fun YearMonth.titleText(locale: Locale): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + year
