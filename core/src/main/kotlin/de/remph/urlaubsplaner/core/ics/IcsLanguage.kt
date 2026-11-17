// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.ics

import java.util.Locale

/** The language of the texts in the calendar file: the app's own two languages. */
enum class IcsLanguage {
    GERMAN,
    ENGLISH,
    ;

    companion object {
        /**
         * English for an English locale, German for everything else: the app has only these two
         * sets of texts and falls back to German for any other system language (the maintainer, 2026-10-26).
         */
        fun of(locale: Locale): IcsLanguage = if (locale.language == "en") ENGLISH else GERMAN

        /**
         * For a list of preferred languages: the first one the app has texts for decides, like
         * Android picks the app's screen texts. With Turkish before English the screens are
         * English, so the file is too; with no German or English in the list it is German.
         */
        fun of(locales: List<Locale>): IcsLanguage =
            when (locales.firstOrNull { it.language == "en" || it.language == "de" }?.language) {
                "en" -> ENGLISH
                else -> GERMAN
            }
    }
}
