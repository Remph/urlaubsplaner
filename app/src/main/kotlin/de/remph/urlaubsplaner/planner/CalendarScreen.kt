package de.remph.urlaubsplaner.planner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.settings.daysWithUnit
import de.remph.urlaubsplaner.ui.rememberAnimationsRemoved
import de.remph.urlaubsplaner.ui.rememberSharedAxis
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The calendar: one month with every day tappable, or the whole year at a glance. Tapping a
 * day opens the day sheet to mark it. The month and the mode live in the ViewModel, so they
 * survive a tab change and a rotation.
 *
 * A step slides the title and the body a short way, like the tabs (a switch to the year or month
 * view swaps in place). Both are drawn from one
 * [CalendarPage], so the header and the body always belong to the same month.
 */
@Composable
fun CalendarScreen(
    viewModel: PlannerViewModel,
    outerPadding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    val view by viewModel.calendar.collectAsStateWithLifecycle()
    val month by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val mode by viewModel.calendarMode.collectAsStateWithLifecycle()
    val rowsExpanded by viewModel.calendarYearRowsExpanded.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    var sheetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    // A highlight is for the moment the calendar opens: it goes when another month is shown and
    // when the screen leaves, so a later visit does not light up an old day.
    LaunchedEffect(highlight, month) {
        if (highlight?.let { YearMonth.from(it) != month } == true) viewModel.highlightDone()
    }
    DisposableEffect(Unit) { onDispose { viewModel.highlightDone() } }
    // Only a swipe ticks: the arrow buttons have their own press feedback. At the end of the
    // calendar a swipe used to do nothing at all; now it says no.
    val haptic = LocalHapticFeedback.current
    val swipe = { steps: Long ->
        if (viewModel.canStep(steps)) {
            viewModel.step(steps)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }
    val page = rememberCalendarPage(view, month, mode) ?: return
    val shown = page.view
    val locale = currentLocale()
    val motion = rememberPageMotion()
    var headerPx by remember { mutableIntStateOf(0) }
    // What the year page takes besides its grid, kept for the page that slides in (it has no
    // measure of its own yet and would start at the least row height and grow) and over a tab change.
    var othersPx by rememberSaveable { mutableIntStateOf(-1) }

    BoxWithConstraints {
        // What the page can fill without scrolling: the screen without the bars and the frame. The
        // fit is only used while the year rows are expanded, and then the frame has no bottom
        // padding (see below), so only its top counts.
        val visible =
            maxHeight - outerPadding.calculateTopPadding() -
                outerPadding.calculateBottomPadding() - FramePadding
        // The expanded year ends in the handle, which has more than 20 dp of air under its bar,
        // so the frame's usual 16 dp under the page would only be a second strip of nothing.
        val bottomPadding by animateDpAsState(
            if (page.mode == CalendarMode.YEAR && rowsExpanded) 0.dp else FramePadding,
            label = "frameBottom",
        )
        ScreenFrame(outerPadding, scroll, Modifier.swipeToStep(swipe), bottomPadding) {
            if (!shown.hasLand) {
                EmptyState(
                    stringResource(R.string.overview_no_land),
                    R.string.go_to_settings,
                    onOpenSettings,
                )
                return@ScreenFrame
            }
            Header(
                viewModel,
                page,
                motion,
                locale,
                Modifier.onSizeChanged { headerPx = it.height },
            )
            AnimatedContent(
                page,
                transitionSpec = motion.spec,
                contentKey = motion.key,
                label = "calendarPage",
            ) { shownPage ->
                CalendarBody(
                    shownPage,
                    rowsExpanded,
                    // The header and the gap under it are above the body.
                    with(LocalDensity.current) { headerPx.toDp() } + FrameSpacing,
                    visible,
                    scroll,
                    locale,
                    viewModel,
                    highlight,
                    onOpenSettings,
                    savedOthersPx = othersPx,
                    // Only the page that stays reports: the one sliding out has another year's size.
                    onOthersPx = { if (shownPage.generation == page.generation) othersPx = it },
                    onDay = { sheetDate = it },
                )
            }
        }
    }

    val date = sheetDate
    if (date != null && date.year == shown.year) {
        DaySheet(shown, date, viewModel, onDismiss = { sheetDate = null })
    }
}

private typealias PageTransition =
    AnimatedContentTransitionScope<CalendarPage>.() -> ContentTransform

private val FramePadding = 16.dp
private val FrameSpacing = 16.dp

/**
 * The slide of a step, the direction follows the date. With Android's animations off nothing
 * slides and the key never changes, so the content swaps in place (see [CalendarPage.generation]).
 */
private class PageMotion(
    val spec: PageTransition,
    val key: (CalendarPage) -> Int,
)

@Composable
private fun rememberPageMotion(): PageMotion {
    val axis = rememberSharedAxis<CalendarPage>()
    val removed = rememberAnimationsRemoved()
    return PageMotion(
        spec = {
            axis(targetState.month > initialState.month).using(SizeTransform(clip = false))
        },
        key = { if (removed) 0 else it.generation },
    )
}

@Composable
private fun CalendarBody(
    page: CalendarPage,
    rowsExpanded: Boolean,
    above: Dp,
    visible: Dp,
    scroll: ScrollState,
    locale: Locale,
    viewModel: PlannerViewModel,
    highlight: LocalDate?,
    onOpenSettings: () -> Unit,
    savedOthersPx: Int,
    onOthersPx: (Int) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val shown = page.view
    val month = page.month
    val density = LocalDensity.current
    // The expanded rows are what is left of the screen once the rest of the page has its height.
    // That rest is measured (the page minus the grid) while the year is shown. Until then, and
    // after a switch from the month view (whose sizes are not the year's), the last one counts.
    var bodyPx by remember(page.mode) { mutableIntStateOf(0) }
    var gridPx by remember(page.mode) { mutableIntStateOf(0) }
    // The legend leaves while the rows are expanded, so it is not part of what the rows have to
    // leave room for. Its height as laid out right now is taken off the measure: the legend fades
    // and shrinks with the rows, and the room it still holds must not move the target of that
    // very animation (the rows would chase it and jump twice).
    var legendPx by remember(page.mode) { mutableIntStateOf(0) }
    val legendShown by animateFloatAsState(if (rowsExpanded) 0f else 1f, label = "legend")
    val measured =
        if (bodyPx > 0 && gridPx > 0) bodyPx - gridPx - legendPx else -1
    SideEffect { if (measured >= 0) onOthersPx(measured) }
    val othersPx = if (measured >= 0) measured else savedOthersPx
    val expanded =
        if (othersPx < 0) {
            YearRowsMin
        } else {
            val others = with(density) { othersPx.toDp() } + above
            // Whole pixels, rounded down, so the page never ends a pixel too long and scrolls.
            // (Whole dp would leave up to 12 dp of the screen unused: twelve rows.)
            with(density) { floor(expandedRowHeight(visible, others).toPx()).toDp() }
                .coerceAtLeast(YearRowsMin)
        }
    val rowHeight by animateDpAsState(
        if (rowsExpanded) expanded else YearRowsCompact,
        label = "yearRowHeight",
    )
    Column(
        Modifier.fillMaxWidth().onSizeChanged { bodyPx = it.height },
        verticalArrangement = Arrangement.spacedBy(FrameSpacing),
    ) {
        if (page.mode == CalendarMode.MONTH) {
            MonthGrid(shown, month, highlight, viewModel::highlightDone) { onDay(it) }
            MonthCard(shown, month, locale)
            Legend(
                showBridge = shown.bridgeDays.any { YearMonth.from(it) == month },
                showGranted = shown.grantedEnabled,
                compact = true,
            )
        } else {
            Column {
                YearGrid(
                    shown,
                    { viewModel.showMonth(it).also { viewModel.showMode(CalendarMode.MONTH) } },
                    Modifier
                        .onSizeChanged { gridPx = it.height }
                        .expandBySwipe(
                            rowsExpanded,
                            pageAtEnd = { scroll.value >= scroll.maxValue },
                            onChange = viewModel::expandYearRows,
                        ),
                    rowHeight = rowHeight,
                )
                YearRowsHandle(rowsExpanded) { viewModel.expandYearRows(!rowsExpanded) }
                // Under the handle in the same column, with its gap above it inside: a gap the
                // column added itself would stay behind, or appear in the last frame.
                FadingLegend(
                    legendShown,
                    onHeight = { legendPx = it },
                    showBridge = shown.bridgeDays.isNotEmpty(),
                    showGranted = shown.grantedEnabled,
                )
            }
            SchoolHolidaysUnknownNote(
                shown,
                LocalDate.of(shown.year, 1, 1),
                LocalDate.of(shown.year, 12, 31),
            )
        }
        if (shown.entitlement == null) {
            EmptyState(
                stringResource(R.string.overview_no_entitlement, shown.year),
                R.string.enter_vacation_days,
                onOpenSettings,
            )
        }
    }
}

/**
 * The year view's legend, gone while the rows are expanded. [shown] runs from 1 (there) to 0
 * (gone) with the rows' animation: it fades and takes less room, so the rows below grow into
 * the room it leaves. At 0 only an empty column is left (it measures 0 high in the same layout
 * pass as the rest of the page) and nothing for TalkBack to read.
 */
@Composable
private fun FadingLegend(
    shown: Float,
    onHeight: (Int) -> Unit,
    showBridge: Boolean,
    showGranted: Boolean,
) {
    Column(
        Modifier
            .onSizeChanged { onHeight(it.height) }
            .clipToBounds()
            .graphicsLayer { alpha = shown }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * shown).roundToInt()) {
                    placeable.placeRelative(0, 0)
                }
            },
    ) {
        if (shown > 0f) {
            Spacer(Modifier.height(FrameSpacing))
            Legend(showBridge = showBridge, showGranted = showGranted)
        }
    }
}

@Composable
private fun Header(
    viewModel: PlannerViewModel,
    page: CalendarPage,
    motion: PageMotion,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val mode = page.mode
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { viewModel.step(-1) }, enabled = viewModel.canStep(-1)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription =
                        stringResource(
                            if (mode == CalendarMode.MONTH) {
                                R.string.month_previous
                            } else {
                                R.string.year_previous
                            },
                        ),
                )
            }
            AnimatedContent(
                page,
                transitionSpec = motion.spec,
                contentKey = motion.key,
                label = "calendarTitle",
            ) { shownPage ->
                val month = shownPage.month
                Text(
                    text =
                        if (shownPage.mode == CalendarMode.MONTH) {
                            month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) +
                                " " + month.year
                        } else {
                            month.year.toString()
                        },
                    style = MaterialTheme.typography.titleLarge,
                    modifier =
                        Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            IconButton(onClick = { viewModel.step(1) }, enabled = viewModel.canStep(1)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription =
                        stringResource(
                            if (mode == CalendarMode.MONTH) {
                                R.string.month_next
                            } else {
                                R.string.year_next
                            },
                        ),
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes =
                listOf(
                    CalendarMode.MONTH to R.string.mode_month,
                    CalendarMode.YEAR to R.string.mode_year,
                )
            modes.forEachIndexed { index, (entry, label) ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { viewModel.showMode(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

/**
 * Under the month: the vacation days of the month, then its holidays and school holidays by
 * name, since colours alone say little (design review, the maintainer, 2026-10-25).
 */
@Composable
private fun MonthCard(
    view: YearView,
    month: YearMonth,
    locale: Locale,
) {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val holidays = view.holidays.filterKeys { it.year == month.year && it.month == month.month }
    val school = view.schoolHolidaysBetween(first, last)
    val colors = dayColors()
    val vacation = view.vacationIn(month)
    val resources = LocalResources.current

    fun amount(days: VacationDays) =
        daysWithUnit(
            days,
            locale,
            one = { resources.getString(R.string.days_one, it) },
            other = { resources.getString(R.string.days_other, it) },
        )
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountRow(stringResource(R.string.legend_taken), amount(vacation.taken))
            AmountRow(stringResource(R.string.legend_planned), amount(vacation.planned))
        }
        if (holidays.isNotEmpty() || school.isNotEmpty()) {
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((date, holiday) in holidays.toSortedMap()) {
                    NoteRow(
                        colors.holiday,
                        date.mediumText(locale),
                        stringResource(holiday.labelRes()),
                    )
                }
                for (entry in school) {
                    NoteRow(
                        colors.school,
                        rangeText(entry.from, entry.to, locale),
                        entry.nameIn(locale),
                    )
                }
            }
        }
    }
    SchoolHolidaysUnknownNote(view, first, last)
}

@Composable
private fun AmountRow(
    label: String,
    amount: String,
) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            amount,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteRow(
    swatch: androidx.compose.ui.graphics.Color,
    date: String,
    name: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).background(swatch, MaterialTheme.shapes.extraSmall))
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(
                date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
