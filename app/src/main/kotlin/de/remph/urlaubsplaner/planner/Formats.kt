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

/** The data file carries German and English names; any other language gets English. */
fun SchoolHoliday.nameIn(locale: Locale): String = if (locale.language == "de") nameDe else nameEn

fun VacationDays.text(locale: Locale): String = AmountText.format(this, locale)

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
