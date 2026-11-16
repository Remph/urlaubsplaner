// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import androidx.annotation.StringRes
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.holidays.Holiday

/** The name of a public holiday in the app language; a new holiday must get a text here. */
@StringRes
fun Holiday.labelRes(): Int =
    when (this) {
        Holiday.NEUJAHR -> R.string.holiday_neujahr
        Holiday.HEILIGE_DREI_KOENIGE -> R.string.holiday_heilige_drei_koenige
        Holiday.INTERNATIONALER_FRAUENTAG -> R.string.holiday_frauentag
        Holiday.KARFREITAG -> R.string.holiday_karfreitag
        Holiday.OSTERSONNTAG -> R.string.holiday_ostersonntag
        Holiday.OSTERMONTAG -> R.string.holiday_ostermontag
        Holiday.TAG_DER_ARBEIT -> R.string.holiday_tag_der_arbeit
        Holiday.TAG_DER_BEFREIUNG -> R.string.holiday_tag_der_befreiung
        Holiday.VOLKSAUFSTAND_17_JUNI -> R.string.holiday_17_juni
        Holiday.CHRISTI_HIMMELFAHRT -> R.string.holiday_christi_himmelfahrt
        Holiday.PFINGSTSONNTAG -> R.string.holiday_pfingstsonntag
        Holiday.PFINGSTMONTAG -> R.string.holiday_pfingstmontag
        Holiday.FRONLEICHNAM -> R.string.holiday_fronleichnam
        Holiday.AUGSBURGER_FRIEDENSFEST -> R.string.holiday_friedensfest
        Holiday.MARIAE_HIMMELFAHRT -> R.string.holiday_mariae_himmelfahrt
        Holiday.WELTKINDERTAG -> R.string.holiday_weltkindertag
        Holiday.TAG_DER_DEUTSCHEN_EINHEIT -> R.string.holiday_deutsche_einheit
        Holiday.REFORMATIONSTAG -> R.string.holiday_reformationstag
        Holiday.ALLERHEILIGEN -> R.string.holiday_allerheiligen
        Holiday.BUSS_UND_BETTAG -> R.string.holiday_buss_und_bettag
        Holiday.ERSTER_WEIHNACHTSTAG -> R.string.holiday_weihnachtstag_1
        Holiday.ZWEITER_WEIHNACHTSTAG -> R.string.holiday_weihnachtstag_2
    }
