package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.settings.daysWithUnit
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor

// Drag to mark (0.11.0, item S2): long-press a day in the month view and drag across the days.
// The maths here is plain Kotlin, so it is tested without a screen; the gesture and the bubble
// come after it.

/** A run of days from [start] to [end], both included. */
internal data class DayRange(
    val start: LocalDate,
    val end: LocalDate,
)

/**
 * The day under the finger, kept inside the month: [pastEdge] says the finger is beyond the first
 * or the last day (on a blank cell of the grid, or outside the grid), and [date] is then the day
 * at that edge.
 */
internal data class DragTarget(
    val date: LocalDate,
    val pastEdge: Boolean,
)

/**
 * The target for the grid cell at [row] and [column] (0-based, Monday first). A row that does not
 * exist (above the first, under the last) is as much "past the edge" as a blank cell.
 */
internal fun dragTarget(
    month: YearMonth,
    row: Int,
    column: Int,
): DragTarget {
    val blanks = month.atDay(1).dayOfWeek.value - 1
    val day = row * 7 + column - blanks + 1
    val inside = day.coerceIn(1, month.lengthOfMonth())
    return DragTarget(month.atDay(inside), pastEdge = day != inside)
}

/**
 * The target at a point of the grid, in pixels from its top left corner. [rowsTop] is where the
 * first row starts (under the weekday names) and [rowPitch] the distance from one row to the
 * next, including the gap, which belongs to the row above it. Sideways the grid is seven equal
 * columns; a finger beyond its sides stays in the first or the last column.
 */
internal fun dragTargetAt(
    month: YearMonth,
    x: Float,
    y: Float,
    width: Float,
    rowsTop: Float,
    rowPitch: Float,
): DragTarget {
    val column = floor(x / (width / 7)).toInt().coerceIn(0, 6)
    val row = floor((y - rowsTop) / rowPitch).toInt()
    return dragTarget(month, row, column)
}

/** What the phone should feel when the finger reaches a new target. */
internal enum class DragFeedback { NONE, TICK, REJECT }

/**
 * One drag: it started on [anchor], and the finger has moved to [target] since. The earlier of the
 * two is the first day of the range, so dragging backwards works.
 */
internal class MarkDrag(
    val anchor: LocalDate,
) {
    var target: LocalDate = anchor
        private set
    private var pastEdge = false

    val range: DayRange
        get() =
            if (target.isBefore(anchor)) DayRange(target, anchor) else DayRange(anchor, target)

    /**
     * Moves the finger to [next] and says what that feels like: a tick for a new day, one "no" when
     * the finger goes past the month's edge (not again on every move while it stays there).
     */
    fun moveTo(next: DragTarget): DragFeedback {
        val feedback =
            when {
                next.pastEdge && !pastEdge -> DragFeedback.REJECT
                !next.pastEdge && next.date != target -> DragFeedback.TICK
                else -> DragFeedback.NONE
            }
        pastEdge = next.pastEdge
        target = next.date
        return feedback
    }
}

/**
 * [range] cut down to the days that cost vacation: from the first to the last of them. The day
 * sheet marks only those, and it opens on the first day, which must be one it can mark (a
 * backwards drag can end on a Saturday). Null when none costs anything.
 */
internal fun YearView.costlyBounds(range: DayRange): DayRange? {
    val days = costlyDays(range.start, range.end)
    return if (days.isEmpty()) null else DayRange(days.first(), days.last())
}

/** What marking [range] with whole days would cost: the number in the bubble. */
internal fun YearView.dragCost(range: DayRange): VacationDays =
    costlyDays(range.start, range.end).fold(VacationDays.ZERO) { sum, day ->
        sum + cost(day, DayMark.FULL)
    }

/**
 * A long press, then a drag. [onLongPress] is called with the finger's position once the system's
 * long press time has passed without the finger moving more than the touch slop; it says whether
 * a drag starts. From then on the gesture is ours: every change is consumed on the way down, so
 * the pager (and the page's scroll) does not move and the day under the finger does not get a
 * click when it is let go. [onDrag] follows the finger, [onEnd] is called when it lifts and
 * [onCancel] when the system takes the touch away. If no drag starts the rest of the touch is
 * swallowed, so a long press on a day that costs nothing does not open a sheet on release.
 *
 * Before the long press the touch is not touched: a swipe starts the pager as usual, and a tap
 * is a click. The first change beyond the slop ends the wait.
 */
@Composable
internal fun Modifier.longPressDrag(
    onLongPress: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onEnd: (Offset) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val start by rememberUpdatedState(onLongPress)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var position = down.position
            // Timing out is the "yes": the finger stayed and was held. Every other end of the
            // wait returns false.
            val ended =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var waiting = true
                    while (waiting) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        waiting =
                            change != null && change.pressed && !change.isConsumed &&
                            event.changes.count { it.pressed } == 1 &&
                            (change.position - down.position).getDistance() <= slop
                        if (change != null) position = change.position
                    }
                    false
                }
            if (ended != null) return@awaitEachGesture

            val active = start(position)
            var finished = false
            try {
                while (!finished) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    when {
                        change == null -> {
                            finished = true
                            if (active) cancel()
                        }

                        !change.pressed -> {
                            finished = true
                            // A touch the system takes away arrives as a lift without a real
                            // event behind it; that opens nothing.
                            if (active) {
                                if (event.motionEvent ==
                                    null
                                ) {
                                    cancel()
                                } else {
                                    end(change.position)
                                }
                            }
                        }

                        active -> {
                            move(change.position)
                        }
                    }
                }
            } finally {
                // The coroutine was cancelled (the screen left, the pointer input restarted).
                if (!finished && active) cancel()
            }
        }
    }
}

/** A tag for tests: the bubble is a window of its own and shows texts like "2 Tage" the card has too. */
internal const val DRAG_BUBBLE_TAG = "dragBubble"

/**
 * The little label that follows the finger with what the range costs ("4 Tage"). It is a popup so
 * it can sit over the header, outside the page, and it is above the finger unless that would
 * leave the screen.
 */
@Composable
internal fun DragBubble(
    cost: VacationDays,
    finger: State<Offset>,
) {
    val resources = LocalResources.current
    val locale = currentLocale()
    val gap = with(LocalDensity.current) { BubbleGap.roundToPx() }
    val at = finger.value
    val provider = BubbleAbove(IntOffset(at.x.toInt(), at.y.toInt()), gap)
    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
            modifier = Modifier.testTag(DRAG_BUBBLE_TAG),
        ) {
            Text(
                daysWithUnit(
                    cost,
                    locale,
                    one = { resources.getString(R.string.days_one, it) },
                    other = { resources.getString(R.string.days_other, it) },
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** From the finger to the bubble's edge: clear of the fingertip and the hand's shadow. */
private val BubbleGap: Dp = 56.dp

/**
 * Where the bubble sits: centred over [finger] (window pixels) but inside the window, [gap] above
 * it, or [gap] under it when there is no room above.
 */
internal fun bubblePosition(
    finger: IntOffset,
    gap: Int,
    window: IntSize,
    bubble: IntSize,
): IntOffset {
    val x = (finger.x - bubble.width / 2).coerceIn(0, maxOf(0, window.width - bubble.width))
    val above = finger.y - gap - bubble.height
    return IntOffset(x, if (above >= 0) above else finger.y + gap)
}

private class BubbleAbove(
    private val finger: IntOffset,
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = bubblePosition(finger, gap, windowSize, popupContentSize)
}
