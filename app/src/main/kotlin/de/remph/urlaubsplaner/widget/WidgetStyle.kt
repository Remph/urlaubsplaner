// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Which colours a widget uses: the phone's light or dark mode, or one of them for good. */
enum class WidgetColors { PHONE, LIGHT, DARK }

/**
 * What the user chose when setting up one widget (the maintainer, 2026-10-26): how see-through the
 * background is, and the colours, which keep the text readable on a see-through background.
 * Kept per widget in Glance's preferences; a new option is a new key with a default, so widgets
 * set up before it keep working.
 */
data class WidgetStyle(
    /** 0 is a solid background, 100 shows only the wallpaper. */
    val transparencyPercent: Int = DEFAULT_TRANSPARENCY,
    val colors: WidgetColors = WidgetColors.PHONE,
) {
    /** 1 for a solid background down to 0 for none. */
    val backgroundAlpha: Float get() = (100 - transparencyPercent) / 100f

    fun writeTo(preferences: MutablePreferences) {
        preferences[TRANSPARENCY] = transparencyPercent
        preferences[COLORS] = colors.name
    }

    companion object {
        const val DEFAULT_TRANSPARENCY = 0

        private val TRANSPARENCY = intPreferencesKey("transparency_percent")
        private val COLORS = stringPreferencesKey("colors")

        /** A missing or unknown value falls back to the default, so a widget never fails to draw. */
        fun from(preferences: Preferences): WidgetStyle =
            WidgetStyle(
                transparencyPercent =
                    (preferences[TRANSPARENCY] ?: DEFAULT_TRANSPARENCY).coerceIn(0, 100),
                colors =
                    WidgetColors.entries.firstOrNull { it.name == preferences[COLORS] }
                        ?: WidgetColors.PHONE,
            )
    }
}
