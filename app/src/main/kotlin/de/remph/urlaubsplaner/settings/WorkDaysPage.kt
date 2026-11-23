// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.planner.shortText
import java.time.DayOfWeek
import java.time.MonthDay
import java.time.format.TextStyle

/** Which weekdays count as working days, and what Heiligabend and Silvester are worth. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDaysPage(
    ui: SettingsUiState,
    viewModel: SettingsViewModel,
    outerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val schedule = ui.settings.schedule
    SettingsPageFrame(stringResource(R.string.section_work_days), outerPadding, onBack) {
        // One row for the week, the same on every phone and at every font size: chips wrapped
        // differently on each (the UI audit's finding 08). No tick icons and no side padding, so
        // seven short names fit one line.
        MultiChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEachIndexed { index, day ->
                val works = day in schedule.workingDays
                val description = day.getDisplayName(TextStyle.FULL, locale)
                SegmentedButton(
                    checked = works,
                    onCheckedChange = { viewModel.setWorkingDay(day, !works) },
                    shape = SegmentedButtonDefaults.itemShape(index, DayOfWeek.entries.size),
                    icon = {},
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.semantics { contentDescription = description },
                ) { Text(day.shortText(locale), maxLines = 1) }
            }
        }
        SpecialDay(
            WorkSchedule.HEILIGABEND,
            stringResource(R.string.special_christmas_eve),
            schedule,
            viewModel,
        )
        SpecialDay(
            WorkSchedule.SILVESTER,
            stringResource(R.string.special_new_years_eve),
            schedule,
            viewModel,
        )
    }
}

@Composable
private fun SpecialDay(
    day: MonthDay,
    label: String,
    schedule: WorkSchedule,
    viewModel: SettingsViewModel,
) {
    val value = schedule.specialDays[day] ?: VacationDays.ONE
    val options =
        listOf(
            VacationDays.ONE to stringResource(R.string.special_full),
            VacationDays.HALF to stringResource(R.string.special_half),
            VacationDays.ZERO to stringResource(R.string.special_off),
        )
    Text(label, style = MaterialTheme.typography.bodyLarge)
    // The same segmented row as the day sheet's "Ganzer Tag / Halber Tag". The three buttons
    // are as high as the tallest one, so a label that wraps at a large font does not leave the
    // others short.
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        options.forEachIndexed { index, (amount, text) ->
            val description = stringResource(R.string.special_options_description, label, text)
            SegmentedButton(
                selected = value == amount,
                onClick = { viewModel.setSpecialDay(day, amount) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .semantics { contentDescription = description },
            ) { Text(text, textAlign = TextAlign.Center) }
        }
    }
}
