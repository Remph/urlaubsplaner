// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.appearance

import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The XML theme's window colour is what the splash and the first frames show before Compose
 * runs; it must be the app's own background, not AppCompat's grey.
 */
@RunWith(AndroidJUnit4::class)
class WindowBackgroundTest {
    private fun windowBackground(): Int {
        val context =
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.Theme_Urlaubsplaner,
            )
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.windowBackground, value, true)
        return value.data
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `the light window is the light background`() {
        assertEquals(FixedLight.background.toArgb(), windowBackground())
    }

    @Test
    @Config(qualifiers = "night")
    fun `the dark window is the dark background`() {
        assertEquals(FixedDark.background.toArgb(), windowBackground())
    }
}
