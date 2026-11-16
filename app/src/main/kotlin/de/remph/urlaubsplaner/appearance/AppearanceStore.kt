// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.appearance

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps the [Appearance] in the app's private preferences (three small values, so no database).
 * Reading never fails on a value it does not know; it falls back to the default.
 *
 * The light/dark choice is handed to [AppCompatDelegate], which makes the whole activity, window
 * and system bars included, follow it. Call [applyNightMode] once when the app starts: AppCompat
 * does not remember the night mode itself.
 */
class AppearanceStore(
    private val preferences: SharedPreferences,
) {
    private val current = MutableStateFlow(read())

    val state: StateFlow<Appearance> = current

    fun update(change: (Appearance) -> Appearance) {
        val changed = change(current.value)
        if (changed == current.value) return
        preferences.edit {
            putString(MODE, changed.mode.name)
            putBoolean(OLED, changed.oled)
            putBoolean(SYSTEM_COLORS, changed.systemColors)
        }
        current.value = changed
        applyNightMode()
    }

    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (current.value.mode) {
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }

    private fun read(): Appearance {
        val mode = ThemeMode.entries.firstOrNull { it.name == preferences.getString(MODE, null) }
        return Appearance(
            mode = mode ?: ThemeMode.SYSTEM,
            oled = preferences.getBoolean(OLED, false),
            systemColors = preferences.getBoolean(SYSTEM_COLORS, true),
        )
    }

    companion object {
        const val FILE_NAME = "appearance"
        private const val MODE = "theme_mode"
        private const val OLED = "oled"
        private const val SYSTEM_COLORS = "system_colors"
    }
}
