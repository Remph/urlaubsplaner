// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class WidgetSetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var style by mutableStateOf(WidgetStyle())
    private var done = 0

    private fun show() =
        compose.setContent {
            WidgetSetupScreen(
                style,
                onChange = { style = it },
                onDone = { done++ },
            )
        }

    @Test
    @Config(qualifiers = "de")
    fun `a new widget starts solid and like the phone`() {
        show()

        compose.onNodeWithText("Widget einrichten").assertIsDisplayed()
        compose.onNodeWithText("Transparenz des Hintergrunds").assertIsDisplayed()
        compose.onNodeWithText("0 %").assertIsDisplayed()
        compose.onNodeWithText("Wie das Telefon").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "de")
    fun `choosing a colour changes the style`() {
        show()

        compose.onNode(hasText("Dunkel")).performScrollTo().performClick()

        assertEquals(WidgetColors.DARK, style.colors)
        compose.onNodeWithText("Dunkel").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the saved transparency is shown`() {
        style = WidgetStyle(transparencyPercent = 60, colors = WidgetColors.LIGHT)
        show()

        compose.onNodeWithText("60 %").assertIsDisplayed()
        compose.onNodeWithText("Hell").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the setup is in English too and done finishes it`() {
        show()

        compose.onNodeWithText("Set up widget").assertIsDisplayed()
        compose.onNodeWithText("Widget colours").assertIsDisplayed()
        compose.onNodeWithText("Like the phone").assertIsSelected()
        compose.onNodeWithText("Done").performScrollTo().performClick()

        assertEquals(1, done)
    }

    @Test
    @Config(qualifiers = "de-w360dp-h780dp-xxhdpi")
    fun `at a 200 percent font the percent value keeps its place inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                WidgetSetupScreen(style, onChange = { style = it }, onDone = {})
            }
        }

        val value = compose.onNodeWithText("0 %").getUnclippedBoundsInRoot()
        assertTrue(value.right <= 360.dp)
        assertTrue(value.width > 0.dp)
        // One line: "0" and "%" are not split.
        assertTrue(value.height < 60.dp)
    }
}
