// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.remph.urlaubsplaner.ui.rememberAnimationsRemoved
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/*
 * The highlight of a day's tile after "Nächster Feiertag" was tapped (0.10.0, item 20): a ring
 * grows out of the tile and fades, twice, and the tile pops a little (the maintainer chose it
 * over glow, bounce, flash and spotlight, mock-ups in design/0-10-holiday-highlight). The ring is
 * drawn outside the tile, so it reads on every tile colour and wallpaper.
 */

/** How long one ring takes from the tile's edge to gone. */
internal const val RING_MILLIS = 1000
internal const val SECOND_RING_AT = 450
internal const val POP_MILLIS = 420
internal const val HIGHLIGHT_MILLIS = SECOND_RING_AT + RING_MILLIS

/** The calendar slides in from the overview first (the tab slide takes 300 ms). */
internal const val HIGHLIGHT_START_DELAY = 300L

/** With Android's animations off the tile shows a still outline for this long instead. */
internal const val STILL_MILLIS = 1500L

private const val POP = 0.1f
private const val IDLE = -1f
private val RingEasing = CubicBezierEasing(0.1f, 0.6f, 0.3f, 1f)

private val RingWidth = 3.dp

/** The ring starts this far outside the tile, so it is not hidden by the tile's own outline. */
private val RingStartGap = 1.5.dp

/** How far out it grows (the cell is 4 dp wider than the tile on each side). */
private val RingGrow = 14.dp

/** Where one ring is, 0 at the tile and 1 at its full size; null before it starts and after it ended. */
internal fun ringProgress(
    elapsed: Float,
    startsAt: Int,
): Float? {
    val local = elapsed - startsAt
    if (local < 0f || local > RING_MILLIS) return null
    return RingEasing.transform(local / RING_MILLIS)
}

/** The tile's scale: a short swell and back at the start, 1 otherwise. */
internal fun popScale(elapsed: Float): Float =
    if (elapsed in 0f..POP_MILLIS.toFloat()) {
        1f + POP * sin(PI * elapsed / POP_MILLIS).toFloat()
    } else {
        1f
    }

/** Where a tile's highlight is: [elapsed] milliseconds in, or [still] for the outline without motion. */
@Stable
internal class HighlightClock {
    var elapsed by mutableFloatStateOf(IDLE)
    var still by mutableStateOf(false)
}

/**
 * Plays the highlight once when [play] turns true and then calls [onDone], also when the tile
 * leaves the screen first: a highlight is for the moment the calendar opens, not for a later visit.
 */
@Composable
internal fun rememberHighlightClock(
    play: Boolean,
    onDone: () -> Unit,
): HighlightClock {
    val clock = remember { HighlightClock() }
    val animationsRemoved = rememberAnimationsRemoved()
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        try {
            if (animationsRemoved) {
                clock.still = true
                delay(STILL_MILLIS)
            } else {
                delay(HIGHLIGHT_START_DELAY)
                animate(
                    0f,
                    HIGHLIGHT_MILLIS.toFloat(),
                    animationSpec = tween(HIGHLIGHT_MILLIS, easing = LinearEasing),
                ) { value, _ -> clock.elapsed = value }
            }
        } finally {
            clock.elapsed = IDLE
            clock.still = false
            done()
        }
    }
    return clock
}

/** Draws the rings (or the still outline) over a cell whose tile is [tileInset] inside it. */
internal fun Modifier.highlightRings(
    clock: HighlightClock,
    color: Color,
    shape: Shape,
    tileInset: Dp,
): Modifier =
    drawWithContent {
        drawContent()
        val stroke = Stroke(RingWidth.toPx())

        fun ring(
            grow: Float,
            alpha: Float,
        ) {
            val inset = tileInset.toPx() - grow
            val ringSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            translate(inset, inset) {
                drawOutline(
                    shape.createOutline(ringSize, layoutDirection, this),
                    color.copy(alpha = alpha),
                    style = stroke,
                )
            }
        }
        if (clock.still) {
            ring(RingStartGap.toPx(), 1f)
        } else {
            for (startsAt in intArrayOf(0, SECOND_RING_AT)) {
                val progress = ringProgress(clock.elapsed, startsAt) ?: continue
                ring(RingStartGap.toPx() + progress * RingGrow.toPx(), (1f - progress) * 0.95f)
            }
        }
    }
