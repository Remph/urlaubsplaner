// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Every public holiday that exists in at least one Bundesland, with the rule for its date.
 * Which Länder have it is in [HolidayRules]. The names shown to the user are string resources in
 * `:app`, so this enum is only the stable id.
 */
enum class Holiday(
    private val dateIn: (Int) -> LocalDate,
) {
    NEUJAHR({ LocalDate.of(it, 1, 1) }),
    HEILIGE_DREI_KOENIGE({ LocalDate.of(it, 1, 6) }),
    INTERNATIONALER_FRAUENTAG({ LocalDate.of(it, 3, 8) }),
    KARFREITAG({ Easter.sunday(it).minusDays(2) }),
    OSTERSONNTAG({ Easter.sunday(it) }),
    OSTERMONTAG({ Easter.sunday(it).plusDays(1) }),
    TAG_DER_ARBEIT({ LocalDate.of(it, 5, 1) }),

    /** 8 May, a one-off in Berlin (75th and 80th anniversary of the end of the war). */
    TAG_DER_BEFREIUNG({ LocalDate.of(it, 5, 8) }),

    /** 17 June 2028, a one-off in Berlin (75th anniversary of the 1953 uprising); a Saturday. */
    VOLKSAUFSTAND_17_JUNI({ LocalDate.of(it, 6, 17) }),
    CHRISTI_HIMMELFAHRT({ Easter.sunday(it).plusDays(39) }),
    PFINGSTSONNTAG({ Easter.sunday(it).plusDays(49) }),
    PFINGSTMONTAG({ Easter.sunday(it).plusDays(50) }),
    FRONLEICHNAM({ Easter.sunday(it).plusDays(60) }),
    AUGSBURGER_FRIEDENSFEST({ LocalDate.of(it, 8, 8) }),
    MARIAE_HIMMELFAHRT({ LocalDate.of(it, 8, 15) }),
    WELTKINDERTAG({ LocalDate.of(it, 9, 20) }),
    TAG_DER_DEUTSCHEN_EINHEIT({ LocalDate.of(it, 10, 3) }),
    REFORMATIONSTAG({ LocalDate.of(it, 10, 31) }),
    ALLERHEILIGEN({ LocalDate.of(it, 11, 1) }),

    // Wednesday before 23 November; 22 November itself counts when it is a Wednesday.
    BUSS_UND_BETTAG({
        LocalDate.of(it, 11, 22).with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY))
    }),
    ERSTER_WEIHNACHTSTAG({ LocalDate.of(it, 12, 25) }),
    ZWEITER_WEIHNACHTSTAG({ LocalDate.of(it, 12, 26) }),
    ;

    fun date(year: Int): LocalDate = dateIn(year)
}
