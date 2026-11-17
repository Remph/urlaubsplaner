// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStyleTest {
    @Test
    fun `a new widget is solid and follows the phone`() {
        val style = WidgetStyle.from(emptyPreferences())

        assertEquals(0, style.transparencyPercent)
        assertEquals(WidgetColors.PHONE, style.colors)
        assertEquals(1f, style.backgroundAlpha, 0f)
    }

    @Test
    fun `what is written is read back`() {
        val style = WidgetStyle(transparencyPercent = 35, colors = WidgetColors.DARK)
        val stored = mutablePreferencesOf()

        style.writeTo(stored)

        assertEquals(style, WidgetStyle.from(stored))
    }

    @Test
    fun `transparency is the other side of the alpha`() {
        assertEquals(0f, WidgetStyle(transparencyPercent = 100).backgroundAlpha, 0f)
        assertEquals(0.75f, WidgetStyle(transparencyPercent = 25).backgroundAlpha, 0.0001f)
    }

    @Test
    fun `an unknown or out of range value falls back instead of failing`() {
        val stored =
            mutablePreferencesOf(
                intPreferencesKey("transparency_percent") to 250,
                stringPreferencesKey("colors") to "NEON",
            )

        val style = WidgetStyle.from(stored)

        assertEquals(100, style.transparencyPercent)
        assertEquals(WidgetColors.PHONE, style.colors)
    }
}
