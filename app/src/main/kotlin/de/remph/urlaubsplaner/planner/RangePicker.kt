package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** What a day is in the "Bis" picker, seen from the first day of the range. */
internal enum class RangeRole {
    /** Before the first day: the range cannot end there. */
    BEFORE_START,

    /** The first day, which is the day the sheet was opened for. */
    START,
    INSIDE,
    END,
    AFTER_END,
}

internal val RangeRole.isEdge: Boolean get() = this == RangeRole.START || this == RangeRole.END

/** Whether the day is part of the range, as opposed to before or after it. */
internal val RangeRole.inRange: Boolean get() =
    this != RangeRole.BEFORE_START &&
        this != RangeRole.AFTER_END

internal fun rangeRole(
    date: LocalDate,
    start: LocalDate,
    end: LocalDate,
): RangeRole =
    when {
        date.isBefore(start) -> RangeRole.BEFORE_START
        date == start -> RangeRole.START
        date == end -> RangeRole.END
        date.isBefore(end) -> RangeRole.INSIDE
        else -> RangeRole.AFTER_END
    }

/**
 * The tile of a day in a range, one choice for the picker and for the drag in the month view.
 * The ends are the strongest thing a day can be there, then a holiday (it stays gold inside the
 * range, with a ring, because it costs nothing), then the tint of the range. Anything else is
 * [otherwise]: the colour the caller's own tile has.
 */
internal fun rangeFill(
    role: RangeRole,
    look: DayLook,
    colors: DayColors,
    scheme: ColorScheme,
    otherwise: Color,
): Color =
    when {
        role.isEdge -> scheme.primary
        look.holiday -> colors.holiday
        role == RangeRole.INSIDE -> scheme.primaryContainer
        else -> otherwise
    }

/** The day number's colour on the tile [rangeFill] chose. */
internal fun rangeText(
    role: RangeRole,
    look: DayLook,
    colors: DayColors,
    scheme: ColorScheme,
    otherwise: Color,
): Color =
    when {
        role.isEdge -> scheme.onPrimary
        look.holiday -> colors.onHoliday
        role == RangeRole.INSIDE -> scheme.onPrimaryContainer
        else -> otherwise
    }

/** A holiday inside the range keeps its gold and gets a ring, to say it is in the range but costs nothing. */
internal fun rangeRing(
    role: RangeRole,
    look: DayLook,
): Boolean = role == RangeRole.INSIDE && look.holiday

/**
 * What is drawn on a tile in a range besides the number: the school holiday bar, and a dot for a
 * day that is marked already, because the tile's fill now says "in the range".
 */
@Composable
internal fun BoxScope.RangeTileDetails(
    look: DayLook,
    edge: Boolean,
    schoolBar: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = dayColors()
    if (schoolBar) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .size(width = 22.dp, height = 3.dp)
                .background(
                    if (edge) scheme.onPrimary else colors.schoolBar,
                    RoundedCornerShape(2.dp),
                ),
        )
    }
    if (look.mark != null) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (schoolBar) 9.dp else 5.dp)
                .size(5.dp)
                .background(if (edge) scheme.onPrimary else scheme.primary, CircleShape),
        )
    }
}

/** The months the picker can show: the first day's month to December, because the balance is per year. */
internal fun pickerMonths(start: LocalDate): ClosedRange<YearMonth> =
    YearMonth.from(start)..YearMonth.of(start.year, 12)

/** The picker's tag for tests: the day sheet and the calendar behind it hold the same day names. */
internal const val RANGE_PICKER_TAG = "rangePicker"

/** The height of a day in the picker; at least 48 dp so a fingertip finds it. */
private val PickerRow = 48.dp

/**
 * The "Bis" choice of the day sheet: a month grid with the calendar's tiles in which the range
 * from [start] is tinted, so holidays and days off inside it show up before anything is stored.
 * The cost is worked out for [mark] and shown under the grid as the sheet will show it.
 * Days before [start] and other years cannot be picked: the range stays in one year, and the
 * sheet's first day stays the day that was tapped (design for 0.11.0, item 24).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RangePickerDialog(
    view: YearView,
    start: LocalDate,
    current: LocalDate,
    mark: DayMark,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    val months = remember(start) { pickerMonths(start) }
    var end by remember { mutableStateOf(current) }
    var month by remember { mutableStateOf(YearMonth.from(current)) }
    val haptic = LocalHapticFeedback.current
    val days = view.costlyDays(start, end)
    val cost = days.fold(VacationDays.ZERO) { sum, day -> sum + view.cost(day, mark) }
    val remaining = view.remainingAfter(days, mark)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .testTag(RANGE_PICKER_TAG),
        ) {
            // Six rows, a title and a summary are about 590 dp: more than a small phone or a large
            // font leaves, so the dialog scrolls rather than cutting off the buttons.
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            ) {
                Text(
                    stringResource(R.string.range_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 12.dp).semantics { heading() },
                )
                Text(
                    stringResource(R.string.range_from, start.fullText(locale)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                MonthBar(
                    month,
                    locale,
                    canGoBack = month > months.start,
                    canGoOn = month < months.endInclusive,
                    onStep = { month = month.plusMonths(it.toLong()) },
                )
                WeekdayHeader(locale)
                PickerGrid(view, month, start, end, locale) { day ->
                    if (day != end) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    end = day
                }
                Summary(cost, remaining, locale, multiple = end != start)
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onPick(end) }) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

@Composable
private fun MonthBar(
    month: YearMonth,
    locale: Locale,
    canGoBack: Boolean,
    canGoOn: Boolean,
    onStep: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            month.titleText(locale),
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Row {
            IconButton(onClick = { onStep(-1) }, enabled = canGoBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.month_previous),
                )
            }
            IconButton(onClick = { onStep(1) }, enabled = canGoOn) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.month_next),
                )
            }
        }
    }
}

/** Six rows always, so the dialog does not change its height between a five and a six row month. */
@Composable
private fun PickerGrid(
    view: YearView,
    month: YearMonth,
    start: LocalDate,
    end: LocalDate,
    locale: Locale,
    onPick: (LocalDate) -> Unit,
) {
    val blanks = month.atDay(1).dayOfWeek.value - 1
    Column {
        for (row in 0 until ROWS) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val day = row * 7 + column - blanks + 1
                    if (day < 1 || day > month.lengthOfMonth()) {
                        Box(Modifier.weight(1f).height(PickerRow))
                    } else {
                        val date = month.atDay(day)
                        PickerCell(
                            view,
                            date,
                            rangeRole(date, start, end),
                            locale,
                            onPick,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private const val ROWS = 6

@Composable
private fun PickerCell(
    view: YearView,
    date: LocalDate,
    role: RangeRole,
    locale: Locale,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val colors = dayColors()
    val scheme = MaterialTheme.colorScheme
    val look = view.look(date)
    val edge = role.isEdge
    val inside = role == RangeRole.INSIDE
    val fill =
        rangeFill(
            role,
            look,
            colors,
            scheme,
            otherwise = if (look.school) colors.school else scheme.surfaceContainerLow,
        )
    val textColor =
        rangeText(
            role,
            look,
            colors,
            scheme,
            otherwise = if (!look.workDay) colors.weekend else scheme.onSurface,
        )
    val shape = MaterialTheme.shapes.large
    val schoolBar = look.school && (edge || inside || look.holiday || look.mark != null)
    val description = dayDescription(view, date, look, locale)
    val state =
        when (role) {
            RangeRole.START -> stringResource(R.string.range_first_day)
            RangeRole.END -> stringResource(R.string.range_last_day)
            RangeRole.INSIDE -> stringResource(R.string.range_inside)
            else -> null
        }
    Box(
        modifier =
            modifier
                .height(PickerRow)
                .alpha(if (role == RangeRole.BEFORE_START) DISABLED_ALPHA else 1f)
                .clickable(enabled = role != RangeRole.BEFORE_START) { onPick(date) }
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    selected = edge
                    state?.let { stateDescription = it }
                },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(shape)
                .background(fill)
                .then(
                    if (rangeRing(role, look)) {
                        Modifier.border(2.dp, scheme.primary, shape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = if (edge) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
            )
            RangeTileDetails(look, edge, schoolBar)
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

/** What the range costs, in the words of the sheet, with a note once it holds days that cost nothing. */
@Composable
private fun Summary(
    cost: VacationDays,
    remaining: VacationDays?,
    locale: Locale,
    multiple: Boolean,
) {
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            // The picker stays open while the range changes, so TalkBack reads the new cost.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(costText(cost, remaining, locale), style = MaterialTheme.typography.bodyLarge)
        if (multiple) {
            Text(
                stringResource(R.string.range_free_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
