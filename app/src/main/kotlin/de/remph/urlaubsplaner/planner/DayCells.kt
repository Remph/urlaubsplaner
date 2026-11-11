package de.remph.urlaubsplaner.planner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkStatus
import de.remph.urlaubsplaner.core.balance.VacationDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** What a day is, put together once for the cell, the year grid and the spoken description. */
class DayLook(
    val holiday: Boolean,
    val school: Boolean,
    val mark: DayMark?,
    val taken: Boolean,
    /** Planned and granted by the employer; only while the granted days setting is on. */
    val granted: Boolean,
    val today: Boolean,
    val workDay: Boolean,
    /** A day to take for a possible Brückentag that is not marked yet. */
    val bridge: Boolean = false,
)

/** [mark] counts only when it costs something, so a mark left on a Sunday shows nothing. */
fun YearView.look(date: LocalDate): DayLook {
    val mark = markOn(date)?.takeIf { cost(date, it) > VacationDays.ZERO }
    return DayLook(
        holiday = date in holidays,
        school = schoolHolidaysOn(date).isNotEmpty(),
        mark = mark,
        taken = mark != null && !date.isAfter(today),
        granted = mark != null && statusOn(date) == MarkStatus.GRANTED,
        today = date == today,
        workDay = settings.schedule.workingValue(date) > VacationDays.ZERO,
        bridge = mark == null && date in bridgeDays,
    )
}

/** The seven weekday names over the month grid, Monday first as in Germany. */
@Composable
internal fun WeekdayHeader(locale: Locale) {
    Row(Modifier.fillMaxWidth()) {
        for (day in DayOfWeek.entries) {
            Text(
                text = day.shortText(locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The fade of a day's tile when it is marked or unmarked: short, so the status stays first. */
private val DayFade = tween<Color>(durationMillis = 150)

/**
 * One month as a grid of days, six rows at most. Days outside the month stay empty. A long press
 * on a day that costs vacation, then a drag, marks a run of days: while the finger is down the
 * days from the first to the one under it look as in the "Bis" picker and a bubble says what the
 * run costs; letting go calls [onRangeDrag] with the first and the last day that cost vacation.
 */
@Composable
fun MonthGrid(
    view: YearView,
    month: YearMonth,
    highlight: LocalDate? = null,
    onHighlightDone: () -> Unit = {},
    onRangeDrag: (first: LocalDate, last: LocalDate) -> Unit = { _, _ -> },
    onDayClick: (LocalDate) -> Unit,
) {
    val locale = currentLocale()
    val blanks = month.atDay(1).dayOfWeek.value - 1
    val cells = blanks + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val drag = rememberMarkDrag(view, month, onRangeDrag)
    Box {
        Column(
            modifier = drag.modifier,
            verticalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            Box(Modifier.onSizeChanged { drag.headerPx = it.height }) { WeekdayHeader(locale) }
            for (row in 0 until rows) {
                // The rings of the highlighted day reach into the rows around it, so its row is drawn last.
                val highlightRow =
                    highlight != null && YearMonth.from(highlight) == month &&
                        (highlight.dayOfMonth + blanks - 1) / 7 == row
                Row(Modifier.fillMaxWidth().zIndex(if (highlightRow) 1f else 0f)) {
                    for (column in 0 until 7) {
                        val day = row * 7 + column - blanks + 1
                        if (day < 1 || day > month.lengthOfMonth()) {
                            Box(Modifier.weight(1f).height(DayHeight))
                        } else {
                            // Keyed by date: a cell that appears with a new month starts at its colours
                            // instead of fading from the previous month's day at the same place.
                            key(month.atDay(day)) {
                                val date = month.atDay(day)
                                DayCell(
                                    view,
                                    date,
                                    locale,
                                    onDayClick,
                                    Modifier.weight(1f),
                                    highlight = date == highlight,
                                    onHighlightDone = onHighlightDone,
                                    range = drag.range,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Beside the grid, not in its column: a child there would get a gap of its own.
        drag.range?.let { DragBubble(view.dragCost(it), drag.finger) }
    }
}

private val DayHeight = 52.dp
private val GridGap = 4.dp

/**
 * The drag to mark of one month grid: the range being dragged (null while none), the finger's
 * place in the window for the bubble, and the [modifier] with the gesture for the grid's column.
 * The grid is measured, not asked: the weekday names above the days are as high as the text
 * makes them ([headerPx]), the days are [DayHeight] with a [GridGap] between the rows.
 */
@Stable
internal class MarkDragState {
    var range by mutableStateOf<DayRange?>(null)
    val finger = mutableStateOf(Offset.Zero)
    var headerPx by mutableIntStateOf(0)
    lateinit var modifier: Modifier
}

@Composable
private fun rememberMarkDrag(
    view: YearView,
    month: YearMonth,
    onRangeDrag: (LocalDate, LocalDate) -> Unit,
): MarkDragState {
    val state = remember { MarkDragState() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestView by rememberUpdatedState(view)
    val latestMonth by rememberUpdatedState(month)
    val latestOnRange by rememberUpdatedState(onRangeDrag)
    var grid by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The drag in progress; the state's range only mirrors it for drawing.
    var current by remember { mutableStateOf<MarkDrag?>(null) }

    fun targetAt(point: Offset): DragTarget {
        val width = grid?.size?.width?.toFloat() ?: 1f
        return dragTargetAt(
            latestMonth,
            point.x,
            point.y,
            width,
            rowsTop = state.headerPx + with(density) { GridGap.toPx() },
            rowPitch = with(density) { (DayHeight + GridGap).toPx() },
        )
    }

    fun follow(point: Offset) {
        grid?.let { if (it.isAttached) state.finger.value = it.localToWindow(point) }
    }

    fun stop() {
        current = null
        state.range = null
    }
    state.modifier =
        Modifier
            .onGloballyPositioned { grid = it }
            .longPressDrag(
                onLongPress = { point ->
                    val target = targetAt(point)
                    when {
                        // A blank cell is no day: nothing to say.
                        target.pastEdge -> {
                            false
                        }

                        // A Saturday or a holiday costs nothing, so there is nothing to mark.
                        !latestView.canMark(target.date) -> {
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            false
                        }

                        else -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            current = MarkDrag(target.date).also { state.range = it.range }
                            follow(point)
                            true
                        }
                    }
                },
                onDrag = { point ->
                    current?.let { drag ->
                        when (drag.moveTo(targetAt(point))) {
                            DragFeedback.TICK -> {
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.SegmentTick,
                                )
                            }

                            DragFeedback.REJECT -> {
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.Reject,
                                )
                            }

                            DragFeedback.NONE -> {
                                Unit
                            }
                        }
                        state.range = drag.range
                        follow(point)
                    }
                },
                onEnd = { point ->
                    current?.let { drag ->
                        // The last move may not have reached the place where the finger lifted.
                        drag.moveTo(targetAt(point))
                        latestView.costlyBounds(drag.range)?.let { latestOnRange(it.start, it.end) }
                    }
                    stop()
                },
                onCancel = ::stop,
            )
    return state
}

@Composable
private fun DayCell(
    view: YearView,
    date: LocalDate,
    locale: Locale,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier,
    highlight: Boolean,
    onHighlightDone: () -> Unit,
    range: DayRange?,
) {
    val colors = dayColors()
    val scheme = MaterialTheme.colorScheme
    val clock = rememberHighlightClock(highlight, onHighlightDone)
    val look = view.look(date)
    // While a drag covers the day it looks as in the "Bis" picker, with the picker's own colours.
    val role = range?.let { rangeRole(date, it.start, it.end) }?.takeIf { it.inRange }
    val description = dayDescription(view, date, look, locale)
    // The theme's large step, the same as the cards (design review, the maintainer, 2026-10-25).
    val shape = MaterialTheme.shapes.large
    val planned = look.mark != null && !look.taken && role == null
    // Every day is a tile of its own, so the days stand out from the background (the maintainer,
    // 2026-10-25). The tile's colour is the strongest thing the day is; a school holiday under a
    // taken day or a holiday still shows as a bar along the bottom (in the text colour on a taken
    // day, since the wallpaper's colour can be close to the school holiday tile).
    val ownFill =
        when {
            look.taken -> colors.taken
            look.granted -> colors.granted
            look.holiday -> colors.holiday
            look.school -> colors.school
            else -> scheme.surfaceContainerLow
        }
    val ownText =
        when {
            look.taken -> colors.onTaken
            look.granted -> colors.onGranted
            look.holiday -> colors.onHoliday
            !look.workDay -> colors.weekend
            else -> scheme.onSurface
        }
    val fillTarget = if (role != null) rangeFill(role, look, colors, scheme, ownFill) else ownFill
    val textTarget = if (role != null) rangeText(role, look, colors, scheme, ownText) else ownText
    // Marking or removing a day fades the tile instead of flipping it. Compose follows Android's
    // "Remove animations" setting, so with it on the colours change at once.
    val fill by animateColorAsState(fillTarget, DayFade, label = "dayFill")
    val textColor by animateColorAsState(textTarget, DayFade, label = "dayText")
    val outline by animateColorAsState(
        if (planned) colors.planned else colors.planned.copy(alpha = 0f),
        DayFade,
        label = "dayOutline",
    )
    val schoolBar = look.school && (look.taken || look.holiday || planned)
    // The touch area is the whole cell; the tile is drawn 2 dp inside it, so a narrow phone does
    // not lose 4 dp of the 48 dp target. The press ripple is drawn on the tile, in its rounded
    // shape, not as a rectangle around it.
    val press = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .height(DayHeight)
                .zIndex(if (highlight) 1f else 0f)
                .highlightRings(clock, MaterialTheme.colorScheme.primary, shape, 2.dp)
                .clickable(interactionSource = press, indication = null) { onClick(date) }
                .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .graphicsLayer {
                        val scale = popScale(clock.elapsed)
                        scaleX = scale
                        scaleY = scale
                    }.clip(shape)
                    .background(fill)
                    .indication(press, ripple())
                    .then(
                        when {
                            role != null && rangeRing(role, look) -> {
                                Modifier.border(2.dp, scheme.primary, shape)
                            }

                            role != null -> {
                                Modifier
                            }

                            planned || outline.alpha > 0f -> {
                                Modifier.border(BorderStroke(2.dp, outline), shape)
                            }

                            look.bridge -> {
                                Modifier.dashedBorder(colors.planned, shape)
                            }

                            else -> {
                                Modifier
                            }
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = if (look.today) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (role != null) {
                RangeTileDetails(
                    look,
                    edge = role.isEdge,
                    schoolBar =
                        look.school &&
                            (
                                role.isEdge || role == RangeRole.INSIDE || look.holiday ||
                                    look.mark != null
                            ),
                )
            }
            if (role == null && look.mark == DayMark.HALF) {
                Text(
                    text = "½",
                    color =
                        when {
                            look.taken -> colors.onTaken
                            look.granted -> colors.grantedHalf
                            else -> colors.planned
                        },
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 2.dp),
                )
            }
            if (role == null && schoolBar) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(width = 22.dp, height = 3.dp)
                        .background(
                            if (look.taken) colors.onTaken else colors.schoolBar,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
            // The dot of a marked day in the range sits where the underline would.
            if (look.today && !(role != null && look.mark != null)) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (schoolBar) 9.dp else 5.dp)
                        .size(width = 14.dp, height = 2.dp)
                        .background(textColor),
                )
            }
        }
    }
}

/** What TalkBack says for a day: the date, then what is special about it. */
@Composable
fun dayDescription(
    view: YearView,
    date: LocalDate,
    look: DayLook,
    locale: Locale,
): String {
    val parts = mutableListOf(date.fullText(locale))
    view.holidays[date]?.let {
        parts +=
            stringResource(R.string.day_holiday, stringResource(it.labelRes()))
    }
    if (look.school) parts += stringResource(R.string.day_school_holiday)
    when {
        look.mark == null -> {
            Unit
        }

        look.mark == DayMark.HALF && look.taken -> {
            parts += stringResource(R.string.day_taken_half)
        }

        look.mark == DayMark.HALF && look.granted -> {
            parts +=
                stringResource(R.string.day_granted_half)
        }

        look.mark == DayMark.HALF -> {
            parts += stringResource(R.string.day_planned_half)
        }

        look.taken -> {
            parts += stringResource(R.string.day_taken)
        }

        look.granted -> {
            parts += stringResource(R.string.day_granted)
        }

        else -> {
            parts += stringResource(R.string.day_planned)
        }
    }
    if (look.bridge) parts += stringResource(R.string.day_bridge)
    if (look.today) parts += stringResource(R.string.day_today)
    return parts.joinToString(", ")
}

/** The colour key under the calendar, in the same shapes as the days. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(
    modifier: Modifier = Modifier,
    showBridge: Boolean = false,
    showGranted: Boolean = false,
    compact: Boolean = false,
) {
    val colors = dayColors()
    val shape = MaterialTheme.shapes.extraSmall
    val size = if (compact) 12.dp else 16.dp
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegendItem(R.string.legend_holiday, size, compact) {
            Modifier.background(colors.holiday, shape)
        }
        LegendItem(R.string.legend_school, size, compact) {
            Modifier.background(colors.school, shape)
        }
        LegendItem(R.string.legend_taken, size, compact) {
            Modifier.background(colors.taken, shape)
        }
        LegendItem(R.string.legend_planned, size, compact) {
            Modifier.border(BorderStroke(2.dp, colors.planned), shape)
        }
        if (showGranted) {
            // The same swatch as a granted day: the lighter fill inside the planned outline.
            LegendItem(R.string.legend_granted, size, compact) {
                Modifier
                    .background(colors.granted, shape)
                    .border(BorderStroke(2.dp, colors.planned), shape)
            }
        }
        if (showBridge) {
            LegendItem(R.string.legend_bridge, size, compact) {
                Modifier.dashedBorder(colors.planned, shape, dash = 4.dp, gap = 3.dp)
            }
        }
    }
}

/** A dashed outline in the shape of a tile: a day that could be marked, but is not yet. */
fun Modifier.dashedBorder(
    color: Color,
    shape: Shape,
    dash: Dp = 6.dp,
    gap: Dp = 4.dp,
    width: Dp = 2.dp,
): Modifier =
    drawWithContent {
        drawContent()
        val stroke =
            Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
            )
        // The line is centred on the edge, so it is drawn half its width inside.
        inset(width.toPx() / 2) {
            drawOutline(shape.createOutline(size, layoutDirection, this), color, style = stroke)
        }
    }

/**
 * Says so when the school holiday file does not reach all of [from] to [to]: no colour then means
 * "not known", not "no school holidays". Nothing is shown while the file covers the time.
 */
@Composable
fun SchoolHolidaysUnknownNote(
    view: YearView,
    from: LocalDate,
    to: LocalDate,
) {
    if (view.schoolHolidaysKnown(from, to)) return
    Text(
        stringResource(R.string.school_holidays_unknown),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LegendItem(
    label: Int,
    size: Dp,
    compact: Boolean,
    swatch: @Composable () -> Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size).then(swatch()))
        Text(
            text = stringResource(label),
            style =
                with(MaterialTheme.typography) { if (compact) labelSmall else bodySmall },
            modifier = Modifier.padding(start = if (compact) 4.dp else 6.dp),
        )
    }
}
