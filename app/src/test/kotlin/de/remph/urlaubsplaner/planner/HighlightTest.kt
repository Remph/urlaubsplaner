// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** The highlight of a day's tile: its timing, what it draws and when it is consumed. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class HighlightTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var fixture: PlannerFixture
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Before
    fun open() {
        fixture = PlannerFixture()
        fixture.fill()
    }

    private val viewModels = ViewModelStore()

    private fun viewModel(): PlannerViewModel =
        fixture.viewModel().also { viewModels.put("planner${viewModels.keys().size}", it) }

    // Cleared first: that cancels the ViewModel's coroutines before the database closes.
    @After
    fun close() {
        viewModels.clear()
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        fixture.close()
    }

    @Test
    fun `a ring is only there while it grows`() {
        assertNull(ringProgress(-1f, 0))
        assertNull(ringProgress(0f, SECOND_RING_AT))
        assertNull(ringProgress(RING_MILLIS + 1f, 0))
        assertEquals(0f, ringProgress(0f, 0)!!, 0f)
        assertEquals(1f, ringProgress(RING_MILLIS.toFloat(), 0)!!, 0.001f)
        // The second ring is a step behind the first.
        assertTrue(ringProgress(600f, 0)!! > ringProgress(600f, SECOND_RING_AT)!!)
    }

    @Test
    fun `the tile swells at the start and is back to its size after`() {
        assertEquals(1f, popScale(-1f), 0f)
        assertEquals(1f, popScale(0f), 0.001f)
        assertEquals(1.1f, popScale(POP_MILLIS / 2f), 0.001f)
        assertEquals(1f, popScale(POP_MILLIS + 1f), 0f)
    }

    @Test
    fun `the whole highlight ends after the second ring`() {
        assertEquals(SECOND_RING_AT + RING_MILLIS, HIGHLIGHT_MILLIS)
    }

    /** The pixel one dp below the cell's top edge: where the first ring starts, outside the 2 dp inset. */
    private fun edgePixel(): Int {
        val image = compose.onNodeWithTag("cell").captureToImage()
        val density = compose.density.density
        return image.toPixelMap()[(30 * density).toInt(), density.toInt()].toArgb()
    }

    @Test
    fun `the ring is drawn around the tile and nothing while idle`() {
        val clock = HighlightClock().also { it.elapsed = 0f }
        compose.setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .testTag("cell")
                        .size(60.dp, 52.dp)
                        .highlightRings(
                            clock,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.shapes.large,
                            2.dp,
                        ),
                )
            }
        }
        val withRing = edgePixel()

        compose.runOnIdle { clock.elapsed = -1f }
        val idle = edgePixel()

        assertNotEquals("the ring colours the edge", idle, withRing)
    }

    @Test
    fun `without animations the tile shows a still outline`() {
        val clock = HighlightClock().also { it.still = true }
        compose.setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .testTag("cell")
                        .size(60.dp, 52.dp)
                        .highlightRings(
                            clock,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.shapes.large,
                            2.dp,
                        ),
                )
            }
        }

        assertEquals(0xFF, edgePixel() ushr 24)
    }

    private fun showCalendar(viewModel: PlannerViewModel) {
        compose.setContent { CalendarScreen(viewModel, PaddingValues(), onOpenSettings = {}) }
        compose.waitUntil(5_000) {
            compose
                .onAllNodesWithText(
                    "Karfreitag",
                    substring = true,
                ).fetchSemanticsNodes()
                .isNotEmpty() ||
                compose
                    .onAllNodesWithText(
                        "Good Friday",
                        substring = true,
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun `the highlight plays once and is used up`() {
        val viewModel = viewModel()
        viewModel.showDate(LocalDate.parse("2027-03-26"))
        assertNotNull(viewModel.highlight.value)

        showCalendar(viewModel)
        compose.waitUntil(5_000) { viewModel.highlight.value == null }
        compose
            .onNode(
                hasContentDescription("Freitag, 26. März 2027", substring = true),
            ).assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun `with animations removed it is used up after the still outline`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val viewModel = viewModel()
        viewModel.showDate(LocalDate.parse("2027-03-26"))

        showCalendar(viewModel)

        compose.waitUntil(5_000) { viewModel.highlight.value == null }
    }

    @Test
    @Config(qualifiers = "de")
    fun `another month shown before it plays drops the highlight`() {
        val viewModel = viewModel()
        viewModel.showDate(LocalDate.parse("2027-03-26"))
        viewModel.showMonth(YearMonth.of(2027, 4))

        compose.setContent { CalendarScreen(viewModel, PaddingValues(), onOpenSettings = {}) }
        compose.waitUntil(5_000) { viewModel.highlight.value == null }
    }

    @Test
    @Config(qualifiers = "de")
    fun `leaving the calendar drops a highlight that has not played`() {
        val viewModel = viewModel()
        viewModel.showDate(LocalDate.parse("2027-03-26"))
        val visible = mutableStateOf(true)
        // The clock stands still, so the highlight (which waits for the slide to the calendar)
        // cannot play before the calendar is taken away again.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (visible.value) CalendarScreen(viewModel, PaddingValues(), onOpenSettings = {})
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        assertNotNull(viewModel.highlight.value)

        compose.runOnUiThread { visible.value = false }
        // The next frame takes the calendar away, 300 ms before the highlight would start.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertNull(viewModel.highlight.value)
    }
}
