// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import de.remph.urlaubsplaner.R

/**
 * The app language is a setting of its own, not only the system language (the maintainer, 2026-10-25).
 * [AppCompatDelegate] keeps it, so it is not in the database or the backup file. With
 * [SYSTEM] the system language applies, and German when that is neither German nor English.
 */
enum class LanguageChoice(
    val tag: String,
) {
    SYSTEM(""),
    GERMAN("de"),
    ENGLISH("en"),
    ;

    @StringRes
    fun labelRes(): Int =
        when (this) {
            SYSTEM -> R.string.language_system
            GERMAN -> R.string.language_german
            ENGLISH -> R.string.language_english
        }

    companion object {
        /** The choice that belongs to the stored language tags; anything else counts as system. */
        fun fromTags(tags: String): LanguageChoice =
            entries.firstOrNull {
                it != SYSTEM &&
                    tags.substringBefore(',').substringBefore('-') == it.tag
            }
                ?: SYSTEM

        fun current(): LanguageChoice =
            fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

        /**
         * The choice for code that runs outside an activity, like the widget. AppCompat only knows
         * the language once an activity has started in this process, so it is read from the system
         * (Android 13 and up) or from the copy [apply] wrote.
         */
        fun stored(context: Context): LanguageChoice =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val manager = context.getSystemService(LocaleManager::class.java)
                fromTags(manager.applicationLocales.toLanguageTags())
            } else {
                fromTags(preferences(context).getString(TAGS, "").orEmpty())
            }

        /** Restarts the visible activity in the new language. */
        fun apply(
            context: Context,
            choice: LanguageChoice,
        ) {
            // From Android 13 the system keeps the language and can change it behind the app's
            // back (system settings), so a copy would go stale; [stored] reads it there instead.
            preferences(context).edit { putString(TAGS, choice.tag) }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(choice.tag))
        }

        private fun preferences(context: Context) =
            context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

        private const val FILE_NAME = "language"
        private const val TAGS = "tags"
    }
}
