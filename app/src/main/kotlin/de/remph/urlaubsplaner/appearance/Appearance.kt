// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.appearance

/** Hell, dunkel, or what the system says (the maintainer, 2026-10-25). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How the app looks. It belongs to the phone, not to the vacation data: like the language it is
 * not in the database or the backup file.
 *
 * [oled] makes the dark theme's background pure black (it has no effect in the light theme).
 * [systemColors] takes the colours from the wallpaper on Android 12 and up (Material You) and is
 * on by default; off, or on Android 11, the app's own teal colours are used.
 */
data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val oled: Boolean = false,
    val systemColors: Boolean = true,
)
