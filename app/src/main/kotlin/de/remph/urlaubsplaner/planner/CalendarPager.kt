package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * The pages of the calendar pager: one per month in the month view, one per year in the year
 * view, from the first to the last month the calendar can show. The ViewModel keeps the month
 * (and the mode); a page index is only where the pager is, so the two are mapped here.
 */
internal class CalendarPages(
    val mode: CalendarMode,
    private val months: ClosedRange<YearMonth>,
) {
    val count: Int =
        if (mode == CalendarMode.MONTH) {
            ChronoUnit.MONTHS.between(months.start, months.endInclusive).toInt() + 1
        } else {
            months.endInclusive.year - months.start.year + 1
        }

    fun indexOf(month: YearMonth): Int =
        if (mode == CalendarMode.MONTH) {
            ChronoUnit.MONTHS.between(months.start, month).toInt()
        } else {
            month.year - months.start.year
        }

    fun yearAt(index: Int): Int =
        if (mode == CalendarMode.MONTH) {
            months.start.plusMonths(index.toLong()).year
        } else {
            months.start.year + index
        }

    /**
     * The month of page [index]. A year page has no month of its own: it keeps the month of
     * [current], as a step by a year does (March 2027 goes to March 2028).
     */
    fun monthAt(
        index: Int,
        current: YearMonth,
    ): YearMonth =
        if (mode == CalendarMode.MONTH) {
            months.start.plusMonths(index.toLong())
        } else {
            YearMonth.of(yearAt(index), current.monthValue)
        }

    /** What the header says over page [index]: "März 2027", or "2027" in the year view. */
    fun title(
        index: Int,
        locale: Locale,
    ): String =
        if (mode == CalendarMode.MONTH) {
            val month = monthAt(index, months.start)
            month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year
        } else {
            yearAt(index).toString()
        }
}

/**
 * The title over the pager. It follows the finger with the pages: the title of the page in view
 * slides a short way and fades out, then the neighbour's slides in, as the old slide did after
 * the finger had let go. For TalkBack it is one heading with the title of the page that is in view.
 */
@Composable
internal fun PagerTitle(
    state: PagerState,
    pages: CalendarPages,
    locale: Locale,
) {
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slide = with(LocalDensity.current) { TitleSlide.toPx() }
    val here = state.currentPage
    val offset = state.currentPageOffsetFraction
    Box(
        Modifier.clearAndSetSemantics {
            heading()
            liveRegion = LiveRegionMode.Polite
            text = AnnotatedString(pages.title(here, locale))
        },
        contentAlignment = Alignment.Center,
    ) {
        for (index in (here - 1)..(here + 1)) {
            // How far this title is from the middle, in pages: 0 in view, 1 one page away.
            val away = index - here - offset
            if (index !in 0 until pages.count || abs(away) >= 1f) continue
            Text(
                pages.title(index, locale),
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier.graphicsLayer {
                        translationX = away * slide * (if (mirrored) -1 else 1)
                        // One title at a time: the one leaving is gone at the middle and the one
                        // coming only starts there, or the two letters run into each other.
                        alpha = (1f - 2 * abs(away)).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

private val TitleSlide = 40.dp

/**
 * A drag sideways towards a page that does not exist (before the first month or after the last)
 * calls [onReject] once the finger lets go, the "no" tick. The pager itself only stretches at
 * its end; this watches the gesture without taking part in it. Like the old swipe it needs a
 * long enough movement that is mostly horizontal, so a scroll that drifts does not tick.
 */
@Composable
internal fun Modifier.rejectPastEnds(
    canMove: (forward: Boolean) -> Boolean,
    onReject: () -> Unit,
): Modifier {
    val can by rememberUpdatedState(canMove)
    val reject by rememberUpdatedState(onReject)
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(mirrored) {
        val threshold = RejectDistance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var x = 0f
            var y = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                x += change.positionChange().x
                y += change.positionChange().y
            } while (change.pressed)
            if (abs(x) >= threshold && abs(x) > abs(y)) {
                val forward = (x < 0) != mirrored
                if (!can(forward)) reject()
            }
        }
    }
}

// Far enough that a sloppy tap or a scroll that drifts sideways does not say no.
private val RejectDistance = 64.dp
