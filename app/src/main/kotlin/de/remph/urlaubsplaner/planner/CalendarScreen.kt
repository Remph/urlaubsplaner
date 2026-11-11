package de.remph.urlaubsplaner.planner

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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.filterNotNull
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
 * The pages sit in a pager: a swipe moves the month (or the year) with the finger and snaps to
 * the next one. The pager and the ViewModel follow each other: the arrows and "today" change the
 * ViewModel's month and the pager scrolls there, a swipe settles on a page and the ViewModel's
 * month follows. Every page reads the view of its own year, so the page next to the one in view
 * can be in another year.
 */
@Composable
fun CalendarScreen(
    viewModel: PlannerViewModel,
    outerPadding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    val month by viewModel.calendarMonth.collectAsStateWithLifecycle()
    // Any page's view would do for the setting that all of them share; the shown year's is the
    // one that is surely there (the ViewModel keeps it, so the first frame already has it).
    val view by viewModel.calendarView(month.year).collectAsStateWithLifecycle()
    val mode by viewModel.calendarMode.collectAsStateWithLifecycle()
    val rowsExpanded by viewModel.calendarYearRowsExpanded.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    var sheetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    // A highlight is for the moment the calendar opens: it goes when another month is shown and
    // when the screen leaves, so a later visit does not light up an old day.
    LaunchedEffect(highlight, month) {
        if (highlight?.let { YearMonth.from(it) != month } == true) viewModel.highlightDone()
    }
    DisposableEffect(Unit) { onDispose { viewModel.highlightDone() } }
    // Only a swipe ticks: the arrow buttons have their own press feedback. At the end of the
    // calendar a swipe stretches the page and says no.
    val haptic = LocalHapticFeedback.current
    val shown = view ?: return
    val locale = currentLocale()
    val pages = CalendarPages(mode, viewModel.calendarMonths)
    var headerPx by remember { mutableIntStateOf(0) }
    // What the year page takes besides its grid, kept for the page that is dragged in (it has no
    // measure of its own yet and would start at the least row height and grow) and over a tab change.
    var othersPx by rememberSaveable { mutableIntStateOf(-1) }

    if (!shown.hasLand) {
        ScreenFrame(outerPadding) {
            EmptyState(
                stringResource(R.string.overview_no_land),
                R.string.go_to_settings,
                onOpenSettings,
            )
        }
        return
    }
    BoxWithConstraints {
        // What a page can fill without scrolling: the screen without the bars and the frame.
        val visible =
            maxHeight - outerPadding.calculateTopPadding() -
                outerPadding.calculateBottomPadding() - FramePadding * 2
        // The header stays where it is and the pager takes the rest of the screen. Each page
        // scrolls on its own: a scrollable page inside a scrollable screen would make the pager
        // the nearest scrollable of everything on the page, and TalkBack, reaching for an item
        // below the screen, would scroll the pager to another month.
        Column(
            Modifier
                .fillMaxSize()
                .padding(outerPadding)
                .consumeWindowInsets(outerPadding)
                .padding(top = FramePadding),
        ) {
            // The pager is made again for the other mode: its pages are other pages.
            val pager = key(mode) { rememberPagerState(pages.indexOf(month)) { pages.count } }
            PagerFollowsViewModel(pager, pages, viewModel)
            Header(
                viewModel,
                mode,
                pager,
                pages,
                locale,
                // Read here, where the month is: the buttons follow it.
                canStepBack = viewModel.canStep(-1),
                canStepForward = viewModel.canStep(1),
                modifier =
                    Modifier
                        .padding(horizontal = FramePadding)
                        .onSizeChanged { headerPx = it.height },
            )
            // The header and the gap under it are above a page's body.
            val above = with(LocalDensity.current) { headerPx.toDp() } + FrameSpacing
            HorizontalPager(
                pager,
                Modifier
                    .weight(1f)
                    .rejectPastEnds(
                        canMove = { viewModel.canStep(if (it) 1 else -1) },
                        onReject = { haptic.performHapticFeedback(HapticFeedbackType.Reject) },
                    ),
                verticalAlignment = Alignment.Top,
            ) { index ->
                CalendarPage(
                    pages,
                    index,
                    month,
                    rowsExpanded,
                    above,
                    visible,
                    locale,
                    viewModel,
                    highlight,
                    onOpenSettings,
                    savedOthersPx = othersPx,
                    // Only the page that stays reports: the one dragged in has another year's size.
                    onOthersPx = { if (index == pager.settledPage) othersPx = it },
                    onDay = { sheetDate = it },
                )
            }
        }
    }

    val date = sheetDate
    if (date != null) {
        val dateView by viewModel.calendarView(date.year).collectAsStateWithLifecycle()
        dateView?.let { DaySheet(it, date, viewModel, onDismiss = { sheetDate = null }) }
    }
}

private val FramePadding = 16.dp
private val FrameSpacing = 16.dp

/**
 * Keeps the pager and the ViewModel's month together. The ViewModel is the truth that survives
 * a tab change and a rotation: when its month changes (the arrows, "today") the pager scrolls
 * there, and when a swipe has settled on another page the ViewModel gets that page's month, with
 * the tick a swipe gives (the arrows have their own press feedback).
 */
@Composable
private fun PagerFollowsViewModel(
    pager: PagerState,
    pages: CalendarPages,
    viewModel: PlannerViewModel,
) {
    val month by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val latestMonth by rememberUpdatedState(month)
    val latestPages by rememberUpdatedState(pages)
    val removed = rememberAnimationsRemoved()
    val haptic = LocalHapticFeedback.current
    val index = pages.indexOf(month)
    LaunchedEffect(index) {
        // Not while a finger or a fling is already taking the pager there.
        if (pager.targetPage != index) {
            if (removed) pager.scrollToPage(index) else pager.animateScrollToPage(index)
        }
    }
    LaunchedEffect(pager) {
        // Only a page at rest counts: a scroll that was stopped half way (the arrow tapped twice)
        // must not send the ViewModel back to where it came from.
        snapshotFlow {
            pager.currentPage.takeIf {
                !pager.isScrollInProgress &&
                    pager.currentPageOffsetFraction == 0f
            }
        }.filterNotNull().collect { page ->
            val target = latestPages.monthAt(page, latestMonth)
            if (target != latestMonth) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                viewModel.showMonth(target)
            }
        }
    }
}

/** Page [index] of the pager: the month or year of that page from the view of its own year. */
@Composable
private fun CalendarPage(
    pages: CalendarPages,
    index: Int,
    current: YearMonth,
    rowsExpanded: Boolean,
    above: Dp,
    visible: Dp,
    locale: Locale,
    viewModel: PlannerViewModel,
    highlight: LocalDate?,
    onOpenSettings: () -> Unit,
    savedOthersPx: Int,
    onOthersPx: (Int) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    // Nothing until the year has been read: a page never draws another year's view.
    val view by viewModel.calendarView(pages.yearAt(index)).collectAsStateWithLifecycle()
    val shown = view ?: return
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = FramePadding, vertical = FramePadding),
    ) {
        CalendarBody(
            shown,
            pages.monthAt(index, current),
            pages.mode,
            rowsExpanded,
            above,
            visible,
            scroll,
            locale,
            viewModel,
            highlight,
            onOpenSettings,
            savedOthersPx,
            onOthersPx,
            onDay,
        )
    }
}

@Composable
private fun CalendarBody(
    shown: YearView,
    month: YearMonth,
    mode: CalendarMode,
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
    val density = LocalDensity.current
    // The expanded rows are what is left of the screen once the rest of the page has its height.
    // That rest is measured (the page minus the grid) while the year is shown. Until then, and
    // after a switch from the month view (whose sizes are not the year's), the last one counts.
    var bodyPx by remember(mode) { mutableIntStateOf(0) }
    var gridPx by remember(mode) { mutableIntStateOf(0) }
    // The legend leaves while the rows are expanded, so it is not part of what the rows have to
    // leave room for. Its height as laid out right now is taken off the measure: the legend fades
    // and shrinks with the rows, and the room it still holds must not move the target of that
    // very animation (the rows would chase it and jump twice).
    var legendPx by remember(mode) { mutableIntStateOf(0) }
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
            // Whole dp, rounded down, so the page never ends a pixel too long and scrolls.
            floor(expandedRowHeight(visible, others).value).dp
        }
    val rowHeight by animateDpAsState(
        if (rowsExpanded) expanded else YearRowsCompact,
        label = "yearRowHeight",
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = FramePadding).onSizeChanged {
            bodyPx =
                it.height
        },
        verticalArrangement = Arrangement.spacedBy(FrameSpacing),
    ) {
        if (mode == CalendarMode.MONTH) {
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
    mode: CalendarMode,
    pager: PagerState,
    pages: CalendarPages,
    locale: Locale,
    canStepBack: Boolean,
    canStepForward: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { viewModel.step(-1) }, enabled = canStepBack) {
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
            PagerTitle(pager, pages, locale)
            IconButton(onClick = { viewModel.step(1) }, enabled = canStepForward) {
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
