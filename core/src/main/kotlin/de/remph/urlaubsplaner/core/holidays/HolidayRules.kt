// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

import de.remph.urlaubsplaner.core.holidays.Bundesland.BADEN_WUERTTEMBERG
import de.remph.urlaubsplaner.core.holidays.Bundesland.BAYERN
import de.remph.urlaubsplaner.core.holidays.Bundesland.BERLIN
import de.remph.urlaubsplaner.core.holidays.Bundesland.BRANDENBURG
import de.remph.urlaubsplaner.core.holidays.Bundesland.BREMEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.HAMBURG
import de.remph.urlaubsplaner.core.holidays.Bundesland.HESSEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.MECKLENBURG_VORPOMMERN
import de.remph.urlaubsplaner.core.holidays.Bundesland.NIEDERSACHSEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.NORDRHEIN_WESTFALEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.RHEINLAND_PFALZ
import de.remph.urlaubsplaner.core.holidays.Bundesland.SAARLAND
import de.remph.urlaubsplaner.core.holidays.Bundesland.SACHSEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.SACHSEN_ANHALT
import de.remph.urlaubsplaner.core.holidays.Bundesland.SCHLESWIG_HOLSTEIN
import de.remph.urlaubsplaner.core.holidays.Bundesland.THUERINGEN

/** A holiday, the Länder that have it, and the years it applies in. */
internal class HolidayRule(
    val holiday: Holiday,
    val lands: Set<Bundesland>,
    val appliesInYear: (Int) -> Boolean = { true },
)

/**
 * The table of Länder rules, from the Feiertagsgesetze. The first supported year is 2019: rules
 * that changed before it (Reformationstag in the northern Länder from 2018, Weltkindertag and
 * Berlin's Frauentag from 2019) are therefore plain "always" rules here.
 */
internal object HolidayRules {
    private val ALL = Bundesland.entries.toSet()
    private const val MV_FRAUENTAG_FIRST_YEAR = 2023

    val all: List<HolidayRule> =
        listOf(
            HolidayRule(Holiday.NEUJAHR, ALL),
            HolidayRule(
                Holiday.HEILIGE_DREI_KOENIGE,
                setOf(BADEN_WUERTTEMBERG, BAYERN, SACHSEN_ANHALT),
            ),
            HolidayRule(Holiday.INTERNATIONALER_FRAUENTAG, setOf(BERLIN)),
            HolidayRule(Holiday.INTERNATIONALER_FRAUENTAG, setOf(MECKLENBURG_VORPOMMERN)) { year ->
                year >= MV_FRAUENTAG_FIRST_YEAR
            },
            HolidayRule(Holiday.KARFREITAG, ALL),
            HolidayRule(Holiday.OSTERSONNTAG, setOf(BRANDENBURG)),
            HolidayRule(Holiday.OSTERMONTAG, ALL),
            HolidayRule(Holiday.TAG_DER_ARBEIT, ALL),
            // One-off in Berlin: 75th (2020) and 80th (2025) anniversary of the end of the war.
            HolidayRule(Holiday.TAG_DER_BEFREIUNG, setOf(BERLIN)) { it == 2020 || it == 2025 },
            HolidayRule(Holiday.VOLKSAUFSTAND_17_JUNI, setOf(BERLIN)) { it == 2028 },
            HolidayRule(Holiday.CHRISTI_HIMMELFAHRT, ALL),
            HolidayRule(Holiday.PFINGSTSONNTAG, setOf(BRANDENBURG)),
            HolidayRule(Holiday.PFINGSTMONTAG, ALL),
            HolidayRule(
                Holiday.FRONLEICHNAM,
                setOf(
                    BADEN_WUERTTEMBERG,
                    BAYERN,
                    HESSEN,
                    NORDRHEIN_WESTFALEN,
                    RHEINLAND_PFALZ,
                    SAARLAND,
                ),
            ),
            // In Bayern only the communities with a mostly Catholic population have it (about 1,700
            // of 2,056). Counted for the whole Land by the maintainer's decision (2026-10-25); see RegionalOptOut.
            HolidayRule(Holiday.MARIAE_HIMMELFAHRT, setOf(BAYERN, SAARLAND)),
            HolidayRule(Holiday.WELTKINDERTAG, setOf(THUERINGEN)),
            HolidayRule(Holiday.TAG_DER_DEUTSCHEN_EINHEIT, ALL),
            HolidayRule(
                Holiday.REFORMATIONSTAG,
                setOf(
                    BRANDENBURG,
                    MECKLENBURG_VORPOMMERN,
                    SACHSEN,
                    SACHSEN_ANHALT,
                    THUERINGEN,
                    BREMEN,
                    HAMBURG,
                    NIEDERSACHSEN,
                    SCHLESWIG_HOLSTEIN,
                ),
            ),
            HolidayRule(
                Holiday.ALLERHEILIGEN,
                setOf(BADEN_WUERTTEMBERG, BAYERN, NORDRHEIN_WESTFALEN, RHEINLAND_PFALZ, SAARLAND),
            ),
            HolidayRule(Holiday.BUSS_UND_BETTAG, setOf(SACHSEN)),
            HolidayRule(Holiday.ERSTER_WEIHNACHTSTAG, ALL),
            HolidayRule(Holiday.ZWEITER_WEIHNACHTSTAG, ALL),
        )
}
