// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.appearance

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    private val wallpaper = darkColorScheme(primary = Color(0xFFFF00FF))

    @Test
    fun `system colours are used when on and available`() {
        val colors = appColorScheme(true, Appearance(), wallpaper)

        assertEquals(wallpaper.primary, colors.primary)
    }

    @Test
    fun `the app's own colours are used when system colours are off`() {
        val colors = appColorScheme(true, Appearance(systemColors = false), wallpaper)

        assertEquals(FixedDark.primary, colors.primary)
    }

    @Test
    fun `the app's own colours are used where the phone has no system colours`() {
        assertEquals(FixedLight.primary, appColorScheme(false, Appearance(), null).primary)
        assertEquals(FixedDark.primary, appColorScheme(true, Appearance(), null).primary)
    }

    @Test
    fun `pure black makes the dark background black but cards stay apart from it`() {
        val colors = appColorScheme(true, Appearance(oled = true, systemColors = false), null)

        assertEquals(Color.Black, colors.background)
        assertEquals(Color.Black, colors.surface)
        assertNotEquals(Color.Black, colors.surfaceContainerLow)
        assertNotEquals(colors.surfaceContainerLow, colors.surfaceContainerHigh)
    }

    @Test
    fun `pure black also applies on top of the system colours`() {
        val colors = appColorScheme(true, Appearance(oled = true), wallpaper)

        assertEquals(Color.Black, colors.background)
        assertEquals(wallpaper.primary, colors.primary)
    }

    @Test
    fun `pure black does nothing in the light theme`() {
        val colors = appColorScheme(false, Appearance(oled = true, systemColors = false), null)

        assertEquals(FixedLight.background, colors.background)
    }

    // Seen on a phone, 2026-10-25: the wallpaper palette's dark background is nearly black.
    private val nearBlack =
        darkColorScheme(
            background = Color(0xFF010102),
            surface = Color(0xFF010102),
            surfaceContainerLow = Color(0xFF252528),
        )

    @Test
    fun `a wallpaper palette that is nearly black is lifted to a dark grey in the dark theme`() {
        val colors = appColorScheme(true, Appearance(), nearBlack)

        assertNotEquals(Color.Black, colors.background)
        assertEquals(colors.background, colors.surface)
        // Still darker than the cards on it, so the tiles stand out.
        assertTrue(colors.background.luminance() < colors.surfaceContainerLow.luminance())
        assertTrue(colors.background.luminance() > nearBlack.background.luminance())
    }

    @Test
    fun `with pure black on, the same palette becomes black and dark differs from it`() {
        val dark = appColorScheme(true, Appearance(), nearBlack)
        val black = appColorScheme(true, Appearance(oled = true), nearBlack)

        assertEquals(Color.Black, black.background)
        assertNotEquals(dark.background, black.background)
    }

    @Test
    fun `a palette with a normal dark grey is left alone`() {
        val colors = appColorScheme(true, Appearance(), wallpaper)

        assertEquals(wallpaper.background, colors.background)
    }

    @Test
    fun `the app's own dark colours are not lifted`() {
        val colors = appColorScheme(true, Appearance(systemColors = false), null)

        assertEquals(FixedDark.background, colors.background)
    }
}
