// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import de.remph.urlaubsplaner.core.ics.IcsLanguage
import de.remph.urlaubsplaner.settings.LanguageChoice
import java.util.Locale

/** The app language as the widget needs it: a [Context] to read the texts from, and its locale. */
class WidgetLanguage(
    val context: Context,
    val locale: Locale,
) {
    companion object {
        /**
         * The widget lives outside any activity, so the language the user chose in the app is not
         * applied to its context; this builds one. With the system language it is the first
         * German or English one in the phone's list, and German when there is none, the same rule
         * as in the app (the maintainer, 2026-10-26).
         */
        fun of(context: Context): WidgetLanguage = of(context, LanguageChoice.stored(context))

        // Not an app bundle split by language (the APK carries both languages), so nothing to
        // download that lint looks for.
        @SuppressLint("AppBundleLocaleChanges")
        fun of(
            context: Context,
            choice: LanguageChoice,
        ): WidgetLanguage {
            val locales = context.resources.configuration.locales
            val locale =
                when (choice) {
                    LanguageChoice.GERMAN -> {
                        Locale.GERMAN
                    }

                    LanguageChoice.ENGLISH -> {
                        Locale.ENGLISH
                    }

                    LanguageChoice.SYSTEM -> {
                        // The first language in the phone's list that the app has texts for
                        // decides, like Android picks the screens and the ICS file (one rule
                        // for both, in IcsLanguage): with Turkish before English the screens
                        // are English, so the widget is too.
                        IcsLanguage.firstSupported(List(locales.size()) { locales[it] })
                            ?: Locale.GERMAN
                    }
                }
            val configuration =
                Configuration(context.resources.configuration).apply {
                    setLocales(LocaleList(locale))
                }
            return WidgetLanguage(context.createConfigurationContext(configuration), locale)
        }
    }
}
