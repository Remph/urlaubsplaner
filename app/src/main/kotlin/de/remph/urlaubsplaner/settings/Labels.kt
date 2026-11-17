// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import androidx.annotation.StringRes
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.holidays.Bundesland

/** The name of a Land in the app language. `when` without else: a new Land must get a text. */
@StringRes
fun Bundesland.labelRes(): Int =
    when (this) {
        Bundesland.BADEN_WUERTTEMBERG -> R.string.land_baden_wuerttemberg
        Bundesland.BAYERN -> R.string.land_bayern
        Bundesland.BERLIN -> R.string.land_berlin
        Bundesland.BRANDENBURG -> R.string.land_brandenburg
        Bundesland.BREMEN -> R.string.land_bremen
        Bundesland.HAMBURG -> R.string.land_hamburg
        Bundesland.HESSEN -> R.string.land_hessen
        Bundesland.MECKLENBURG_VORPOMMERN -> R.string.land_mecklenburg_vorpommern
        Bundesland.NIEDERSACHSEN -> R.string.land_niedersachsen
        Bundesland.NORDRHEIN_WESTFALEN -> R.string.land_nordrhein_westfalen
        Bundesland.RHEINLAND_PFALZ -> R.string.land_rheinland_pfalz
        Bundesland.SAARLAND -> R.string.land_saarland
        Bundesland.SACHSEN -> R.string.land_sachsen
        Bundesland.SACHSEN_ANHALT -> R.string.land_sachsen_anhalt
        Bundesland.SCHLESWIG_HOLSTEIN -> R.string.land_schleswig_holstein
        Bundesland.THUERINGEN -> R.string.land_thueringen
    }
