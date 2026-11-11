package de.remph.urlaubsplaner.planner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.backup.BackupHint
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.ui.rememberAnimationsRemoved
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Status first: how many days are left, what was taken and planned, a warning when Resturlaub
 * is about to expire, the next trip and the next holiday, and the year at a glance.
 */
@Composable
fun OverviewScreen(
    viewModel: PlannerViewModel,
    outerPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onShowDate: (LocalDate) -> Unit,
) {
    val view by viewModel.overview.collectAsStateWithLifecycle()
    val shown = view ?: return
    ScreenFrame(outerPadding) {
        when {
            !shown.hasLand -> {
                EmptyState(
                    stringResource(R.string.overview_no_land),
                    R.string.go_to_settings,
                    onOpenSettings,
                )
            }

            shown.balance == null -> {
                EmptyState(
                    stringResource(R.string.overview_no_entitlement, shown.year),
                    R.string.enter_vacation_days,
                    onOpenSettings,
                )
                BackupHintLine(viewModel, onOpenBackup)
            }

            else -> {
                Status(shown, viewModel, onOpenBackup, onOpenMonth, onShowDate)
            }
        }
    }
}

@Composable
fun ScreenFrame(
    outerPadding: PaddingValues,
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(outerPadding)
                .consumeWindowInsets(outerPadding)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
fun EmptyState(
    text: String,
    buttonText: Int,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(buttonText))
        }
    }
}

@Composable
private fun Status(
    view: YearView,
    viewModel: PlannerViewModel,
    onOpenBackup: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onShowDate: (LocalDate) -> Unit,
) {
    // The bridge is found again by its first day, so the sheet survives a turn of the phone.
    var sheetStart by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val locale = currentLocale()
    val balance = view.balance ?: return
    val overbooked = balance.remaining < VacationDays.ZERO
    val intro = rememberIntro(viewModel)
    val probe = LocalCompositionProbe.current
    SideEffect { probe?.invoke("Status") }

    // The one number that matters, first.
    Column(Modifier.riseIn(intro, 0).semantics(mergeDescendants = true) {}) {
        Text(
            text = stringResource(R.string.days_left_label, view.year),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        CountUpText(
            balance.remaining,
            intro,
            locale,
            MaterialTheme.typography.displayLarge,
            if (overbooked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        if (overbooked) {
            Text(
                text = stringResource(R.string.overbooked),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Row(Modifier.riseIn(intro, 1), Arrangement.spacedBy(12.dp)) {
        Figure(R.string.taken, balance.taken, intro, locale, Modifier.weight(1f))
        Figure(R.string.planned, balance.planned, intro, locale, Modifier.weight(1f))
        val total = balance.entitlement + balance.carryOver
        Figure(R.string.of_total, total, intro, locale, Modifier.weight(1f))
    }
    // Only a share of "Geplant", so the numbers above still add up; hidden until a day is granted.
    if (view.grantedPlanned > VacationDays.ZERO) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.overview_granted,
                    if (view.grantedPlanned == VacationDays.ONE) 1 else 2,
                    view.grantedPlanned.text(locale),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.riseIn(intro, 1),
        )
    }

    val open = balance.carryOverOpen
    if (open > VacationDays.ZERO && !view.today.isAfter(balance.carryOverExpiry)) {
        CarryOverWarning(
            open,
            balance.carryOverExpiry.mediumText(locale),
            locale,
            Modifier.riseIn(intro, 2),
        )
    }

    Card(Modifier.riseIn(intro, 3)) {
        NextTrip(view, locale)
        NextHoliday(view, locale, onShowDate)
    }

    BridgesCard(view, Modifier.riseIn(intro, 4)) { sheetStart = it.leave.first() }

    Column(Modifier.riseIn(intro, 5), Arrangement.spacedBy(8.dp)) {
        Text(
            text = view.year.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        YearGrid(view, { onOpenMonth(it) })
        Legend(showBridge = view.bridgeDays.isNotEmpty(), showGranted = view.grantedEnabled)
        SchoolHolidaysUnknownNote(
            view,
            LocalDate.of(view.year, 1, 1),
            LocalDate.of(view.year, 12, 31),
        )
    }

    // The very bottom, the quietest place (the maintainer's choice, 2026-11-10).
    BackupHintLine(viewModel, onOpenBackup, Modifier.riseIn(intro, 6))

    // Found once when the sheet opens: once its days are marked the bridge leaves the list, and the
    // sheet must stay until it has slid away.
    val chosen =
        remember(sheetStart) {
            view.bridgeGroups.flatMap { it.bridges }.firstOrNull { it.leave.first() == sheetStart }
        }
    if (chosen != null) {
        BridgeSheet(view, chosen, viewModel, onDismiss = { sheetStart = null })
    }
}

/**
 * Milliseconds since the app started counting up and sliding in the panels; [INTRO_MILLIS] at every
 * other time. A screen reader gets the final numbers at once (see the semantics on the texts), and
 * "Remove animations" makes the whole intro jump to the end.
 *
 * A lambda, read only where the intro is drawn (the panels' layers and the counting numbers):
 * reading the value here made the whole overview compose again in every frame of the intro
 * (performance and feel review, 2026-10-31).
 */
@Composable
private fun rememberIntro(viewModel: PlannerViewModel): () -> Float {
    val animationsRemoved = rememberAnimationsRemoved()
    // takeIntro() first: the start counts as played even when there is nothing to play.
    val elapsed =
        remember {
            Animatable(
                if (viewModel.takeIntro() &&
                    !animationsRemoved
                ) {
                    0f
                } else {
                    INTRO_MILLIS
                },
            )
        }
    LaunchedEffect(Unit) {
        if (elapsed.value < INTRO_MILLIS) {
            elapsed.animateTo(INTRO_MILLIS, tween(INTRO_MILLIS.toInt(), easing = LinearEasing))
        }
    }
    return remember(elapsed) { { elapsed.value } }
}

private fun countFraction(elapsed: Float) =
    Decelerate.transform((elapsed / COUNT_UP_MILLIS).coerceIn(0f, 1f))

/**
 * Each panel rises a little and fades in, one after the other (the [index] sets the order). Only
 * the layer is drawn again in each frame; after the intro it stays at full alpha and in place.
 */
private fun Modifier.riseIn(
    intro: () -> Float,
    index: Int,
): Modifier =
    graphicsLayer {
        val elapsed = intro()
        if (elapsed < INTRO_MILLIS) {
            val t =
                Decelerate.transform(
                    ((elapsed - index * RISE_STAGGER_MILLIS) / RISE_MILLIS).coerceIn(0f, 1f),
                )
            alpha = t
            translationY = (1 - t) * RISE_DISTANCE.toPx()
        }
    }

/**
 * A number that counts up during the intro. It composes again only when the shown text changes,
 * and TalkBack reads the final number from the start.
 */
@Composable
private fun CountUpText(
    value: VacationDays,
    intro: () -> Float,
    locale: Locale,
    style: TextStyle,
    color: Color = Color.Unspecified,
) {
    val counted by remember(value, locale, intro) {
        derivedStateOf { value.countedUp(countFraction(intro())).text(locale) }
    }
    val final = value.text(locale)
    val probe = LocalCompositionProbe.current
    SideEffect { probe?.invoke("CountUpText") }
    Text(
        counted,
        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(final) },
        style = style,
        color = color,
    )
}

/**
 * For tests: called with a screen's name each time it composes, so a test can count how often the
 * overview composes during the intro. Null (and nothing called) in the app.
 */
internal val LocalCompositionProbe = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal fun VacationDays.countedUp(progress: Float) =
    if (progress >= 1f) this else VacationDays.ofHalves((days * 2 * progress).roundToInt())

private const val COUNT_UP_MILLIS = 700f
private const val RISE_MILLIS = 380f
private const val RISE_STAGGER_MILLIS = 70f
private const val INTRO_MILLIS = 1000f
private val RISE_DISTANCE = 22.dp
private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@Composable
private fun Figure(
    label: Int,
    value: VacationDays,
    intro: () -> Float,
    locale: Locale,
    modifier: Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp).semantics(mergeDescendants = true) {}) {
            CountUpText(value, intro, locale, MaterialTheme.typography.headlineSmall)
            // One line, shrunk when the word does not fit: at a large system font "Genommen"
            // broke in the middle of the word.
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun CarryOverWarning(
    open: VacationDays,
    expiry: String,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = dayColors().holiday,
        contentColor = dayColors().onHoliday,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(
                stringResource(R.string.carry_over_warning, open.text(locale), expiry),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
internal fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

/**
 * One quiet line, only when a backup is overdue (see BackupReminder): no colour, no icon, the whole
 * line opens the backup page. Nothing is drawn, and no space is kept, when there is no hint.
 */
@Composable
private fun BackupHintLine(
    viewModel: PlannerViewModel,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint by viewModel.backupHint.collectAsStateWithLifecycle()
    val shown = hint ?: return
    val text =
        when (shown) {
            is BackupHint.Old -> {
                pluralStringResource(R.plurals.backup_hint_old, shown.months, shown.months)
            }

            BackupHint.Never -> {
                stringResource(R.string.backup_hint_never)
            }
        }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(
                    onClickLabel = stringResource(R.string.backup_hint_open),
                    role = Role.Button,
                    onClick = onOpenBackup,
                ).heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextTrip(
    view: YearView,
    locale: Locale,
) {
    val trip = view.nextTrip
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            stringResource(R.string.next_trip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trip == null) {
            Text(stringResource(R.string.no_trip), style = MaterialTheme.typography.bodyLarge)
        } else {
            val range =
                if (trip.start == trip.end) {
                    trip.start.mediumText(locale)
                } else {
                    rangeText(trip.start, trip.end, locale)
                }
            Text(range, style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    if (view.today in trip) {
                        stringResource(R.string.trip_now)
                    } else {
                        pluralStringResource(
                            R.plurals.trip_costs,
                            if (trip.cost == VacationDays.ONE) 1 else 2,
                            trip.cost.text(locale),
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextHoliday(
    view: YearView,
    locale: Locale,
    onShow: (LocalDate) -> Unit,
) {
    val next = view.settings.nextPublicHoliday(view.today) ?: return
    val days = ChronoUnit.DAYS.between(view.today, next.date).toInt()
    // The whole block is the button (opens the calendar on the holiday); the chevron says so.
    Row(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                onClickLabel = stringResource(R.string.next_holiday_show),
                role = Role.Button,
            ) { onShow(next.date) }
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.next_holiday),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(next.holiday.labelRes()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    if (days == 0) {
                        stringResource(R.string.holiday_today)
                    } else {
                        val date = next.date.mediumText(locale)
                        pluralStringResource(R.plurals.holiday_in_days, days, date, days)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
