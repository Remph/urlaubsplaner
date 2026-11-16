// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A change while Glance's session still lives must reach the widget (see [liveModel]). */
@RunWith(AndroidJUnit4::class)
class LiveModelTest {
    @get:Rule
    val rule = createComposeRule()

    private fun status(number: String) =
        WidgetModel.Status(
            number = number,
            overbooked = false,
            unit = "Tage übrig",
            unitWithYear = "Tage übrig 2027",
            summary = "Kein Urlaub geplant",
            tripLabel = "Nächster Urlaub",
            tripWhen = "Noch kein Urlaub geplant",
            tripCost = null,
            figures = listOf("Genommen 0", "Geplant 0", "Anspruch 26"),
            warning = null,
        )

    private fun show(
        revision: MutableStateFlow<Int>,
        load: suspend () -> WidgetModel,
    ) = rule.setContent {
        val model = liveModel(status("26"), seen = 3, revision = revision, load = load)
        Text((model as WidgetModel.Status).number)
    }

    @Test
    fun `the first model is shown without reading the data again`() {
        var reads = 0
        show(MutableStateFlow(3)) {
            reads++
            status("25")
        }

        rule.onNodeWithText("26").assertExists()
        assertEquals(0, reads)
    }

    @Test
    fun `a change since the first read reads the data again`() {
        show(MutableStateFlow(4)) { status("25") }

        rule.onNodeWithText("25").assertExists()
    }

    @Test
    fun `every later change reads the data again`() {
        val revision = MutableStateFlow(3)
        var number = 25
        show(revision) { status((number--).toString()) }
        rule.onNodeWithText("26").assertExists()

        revision.value = 4
        rule.waitForIdle()
        rule.onNodeWithText("25").assertExists()

        revision.value = 5
        rule.waitForIdle()
        rule.onNodeWithText("24").assertExists()
    }
}
