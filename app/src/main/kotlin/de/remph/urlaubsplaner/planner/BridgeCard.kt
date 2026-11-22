// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.bridge.Bridge
import de.remph.urlaubsplaner.ui.Card
import java.time.LocalDate
import java.time.MonthDay
import java.util.Locale

/**
 * The best Brückentage of the year, each public holiday with what to take before and after it.
 * Tapping a line asks before anything is stored (the maintainer, 2026-10-25). Nothing is shown when there
 * is none left this year.
 */
@Composable
fun BridgesCard(
    view: YearView,
    modifier: Modifier = Modifier,
    onBridge: (Bridge) -> Unit,
) {
    val groups = view.bridgeGroups
    if (groups.isEmpty()) return
    val locale = currentLocale()
    Card(modifier) {
        Text(
            stringResource(R.string.bridges_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        for (group in groups) {
            Column {
                Text(
                    stringResource(
                        R.string.bridge_anchor,
                        anchorName(view, group.anchor),
                        group.anchor.weekdayDateText(locale),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                group.before?.let { BridgeLine(R.string.bridge_before, it, locale, onBridge) }
                group.after?.let { BridgeLine(R.string.bridge_after, it, locale, onBridge) }
            }
        }
    }
}

@Composable
private fun BridgeLine(
    format: Int,
    bridge: Bridge,
    locale: Locale,
    onClick: (Bridge) -> Unit,
) {
    val line =
        stringResource(
            format,
            leaveText(bridge.leave, locale),
            pluralStringResource(R.plurals.bridge_days_off, bridge.daysOff, bridge.daysOff),
        )
    val action = stringResource(R.string.bridge_sheet_title)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = action, role = Role.Button) { onClick(bridge) }
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.mark_save),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Asks before the leave days of [bridge] are marked, and says what that costs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeSheet(
    view: YearView,
    bridge: Bridge,
    viewModel: PlannerViewModel,
    onDismiss: () -> Unit,
) {
    // While it slides away the sheet keeps its numbers as they were before the days were stored.
    var frozen by remember { mutableStateOf<YearView?>(null) }
    val shown = frozen ?: view
    val locale = currentLocale()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetClose(sheetState, onDismiss)
    val haptic = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(
                horizontal = 24.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.bridge_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    datesText(bridge.leave, locale),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    pluralStringResource(
                        R.plurals.bridge_stretch,
                        bridge.daysOff,
                        bridge.daysOff,
                        rangeText(bridge.start, bridge.end, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text =
                    costText(
                        bridge.cost,
                        shown.remainingAfter(bridge.leave, DayMark.FULL),
                        locale,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = {
                    close.after {
                        frozen = shown
                        viewModel.mark(bridge.leave, DayMark.FULL)
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.mark_save)) }
            TextButton(onClick = { close.after() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/** The holiday a bridge hangs on, or the special day off of the schedule when it is no holiday. */
@Composable
private fun anchorName(
    view: YearView,
    date: LocalDate,
): String {
    view.holidayOn(date)?.let { return stringResource(it.labelRes()) }
    return stringResource(
        when (MonthDay.from(date)) {
            WorkSchedule.HEILIGABEND -> R.string.bridge_christmas_eve
            WorkSchedule.SILVESTER -> R.string.bridge_new_years_eve
            else -> R.string.bridge_day_off
        },
    )
}

/** "Fr", "Mo und Di" or "Mo bis Mi": the weekdays to take. The days follow each other. */
@Composable
private fun leaveText(
    leave: List<LocalDate>,
    locale: Locale,
): String {
    val names = leave.map { it.weekdayText(locale) }
    return when (names.size) {
        1 -> names.first()
        2 -> stringResource(R.string.list_and, names.first(), names.last())
        else -> stringResource(R.string.date_range, names.first(), names.last())
    }
}

/** "Do 04.10. und Fr 05.10." for the sheet, with the dates. */
@Composable
private fun datesText(
    leave: List<LocalDate>,
    locale: Locale,
): String {
    val dates = leave.map { it.weekdayDateText(locale) }
    return when (dates.size) {
        1 -> dates.first()
        2 -> stringResource(R.string.list_and, dates.first(), dates.last())
        else -> stringResource(R.string.date_range, dates.first(), dates.last())
    }
}
