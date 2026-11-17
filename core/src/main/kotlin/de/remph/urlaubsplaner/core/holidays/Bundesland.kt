// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

/** The 16 Bundesländer. [code] is the official two-letter abbreviation, used for storage. */
enum class Bundesland(
    val code: String,
) {
    BADEN_WUERTTEMBERG("BW"),
    BAYERN("BY"),
    BERLIN("BE"),
    BRANDENBURG("BB"),
    BREMEN("HB"),
    HAMBURG("HH"),
    HESSEN("HE"),
    MECKLENBURG_VORPOMMERN("MV"),
    NIEDERSACHSEN("NI"),
    NORDRHEIN_WESTFALEN("NW"),
    RHEINLAND_PFALZ("RP"),
    SAARLAND("SL"),
    SACHSEN("SN"),
    SACHSEN_ANHALT("ST"),
    SCHLESWIG_HOLSTEIN("SH"),
    THUERINGEN("TH"),
    ;

    companion object {
        fun fromCode(code: String): Bundesland? = entries.firstOrNull { it.code == code }
    }
}
