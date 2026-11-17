// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHolidayData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The about page carries what the licences of the data and the libraries ask us to show. */
@RunWith(AndroidJUnit4::class)
class AboutPageTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        compose.setContent { AboutPage(PaddingValues(), onBack = {}) }
    }

    @Test
    @Config(qualifiers = "de")
    fun `German page shows the version, the privacy sentence and the ODbL credit`() {
        show()

        compose.onNodeWithText("Über die App").assertIsDisplayed()
        compose.onNodeWithText("Version ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("keinen Internetzugriff", substring = true).assertIsDisplayed()
        compose.onNodeWithText(SchoolHolidayData.CREDIT).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `English page shows the same facts`() {
        show()

        compose.onNodeWithText("Version ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("no internet access", substring = true).assertIsDisplayed()
        compose.onNodeWithText("© 2026 Remph", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Libraries").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `every licence text is bundled and opens`() {
        show()

        compose.onNodeWithText("GPL-3.0 (this app)").performScrollTo().performClick()
        compose.onNodeWithText("GNU GENERAL PUBLIC LICENSE", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()

        compose.onNodeWithText("Apache License 2.0").performScrollTo().performClick()
        compose.onNodeWithText("TERMS AND CONDITIONS FOR USE", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()

        compose.onNodeWithText("BSD-3-Clause (Protocol Buffers)").performScrollTo().performClick()
        compose.onNodeWithText("Copyright 2008 Google Inc.", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the source row opens the repository in a browser`() {
        show()

        compose.onNodeWithText("Source code").performScrollTo().performClick()

        val started =
            shadowOf(
                ApplicationProvider.getApplicationContext<Application>(),
            ).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(SOURCE_URL, started.dataString)
    }

    @Test
    @Config(qualifiers = "en")
    fun `the website row opens the author's site in a browser`() {
        show()

        compose.onNodeWithText("Website").performScrollTo().performClick()

        val started =
            shadowOf(
                ApplicationProvider.getApplicationContext<Application>(),
            ).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(WEBSITE_URL, started.dataString)
    }
}
