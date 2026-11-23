// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which layout a widget cell gets (UI audit 06): thresholds that grow with the font scale. */
class WidgetTierTest {
    private fun size(
        width: Int,
        height: Int,
    ) = DpSize(width.dp, height.dp)

    @Test
    fun `the wide widget has its full layout from 320 x 150 at the normal font`() {
        assertEquals(WideTier.FULL, wideTier(size(320, 150), 1f))
        assertEquals(WideTier.FULL, wideTier(size(360, 200), 1f))
    }

    @Test
    fun `at its declared minimum the wide widget keeps a one line warning`() {
        assertEquals(WideTier.COMPACT, wideTier(size(250, 110), 1f))
        assertEquals(WideTier.COMPACT, wideTier(size(280, 120), 1f))
    }

    @Test
    fun `a larger font moves every threshold up`() {
        assertEquals(WideTier.COMPACT, wideTier(size(320, 150), 1.3f))
        assertEquals(WideTier.MINIMAL, wideTier(size(250, 110), 1.3f))
        assertEquals(WideTier.FULL, wideTier(size(400, 200), 1.3f))
    }

    @Test
    fun `a font scale below one changes nothing`() {
        assertEquals(wideTier(size(250, 110), 1f), wideTier(size(250, 110), 0.85f))
    }

    @Test
    fun `without a known size the layout is the full one`() {
        assertEquals(WideTier.FULL, wideTier(DpSize.Zero, 2f))
        assertEquals(WideTier.FULL, wideTier(DpSize.Unspecified, 1f))
        assertEquals(SmallTier.FULL, smallTier(DpSize.Zero, 2f))
        assertTrue(showYear(DpSize.Zero, 2f))
    }

    @Test
    fun `the small widget shows only the number and its unit below about 60 dp`() {
        assertEquals(SmallTier.NUMBER, smallTier(size(110, 40), 1f))
        assertEquals(SmallTier.NUMBER, smallTier(size(146, 60), 1f))
        assertEquals(SmallTier.FULL, smallTier(size(180, 70), 1f))
        assertEquals(SmallTier.NUMBER, smallTier(size(146, 70), 1.5f))
    }

    @Test
    fun `the number is sized to the cell in both directions`() {
        val min = size(110, 40)
        val normal = numberSize(min, 8.dp, 6.dp, 1f, "31,5", "Tage übrig").value
        // Not taller than the height allows and not wider than the room next to the unit.
        assertTrue(normal in 14f..21f)
        // A bigger cell gives a bigger number, up to 26 sp.
        assertEquals(
            26f,
            numberSize(size(200, 90), 8.dp, 6.dp, 1f, "31,5", "Tage übrig").value,
            0.01f,
        )
        // A larger font makes the same sp bigger: the sp goes down.
        assertTrue(numberSize(min, 8.dp, 6.dp, 1.3f, "31,5", "Tage übrig").value <= normal)
        // Never below 12 sp, however narrow.
        assertEquals(
            12f,
            numberSize(size(60, 20), 8.dp, 6.dp, 2f, "31,5", "Tage übrig").value,
            0.01f,
        )
    }

    @Test
    fun `the year is only written next to the number where it fits`() {
        assertTrue(showYear(size(250, 110), 1f))
        assertFalse(showYear(size(250, 110), 1.3f))
        assertTrue(showYear(size(320, 150), 1.3f))
    }
}
