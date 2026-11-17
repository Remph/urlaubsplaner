// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.planner.shortText
import java.time.DayOfWeek
import java.time.MonthDay
import java.time.format.TextStyle

/** Which weekdays count as working days, and what Heiligabend and Silvester are worth. */
@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (day in DayOfWeek.entries) {
                val works = day in schedule.workingDays
                FilterChip(
                    selected = works,
                    onClick = { viewModel.setWorkingDay(day, !works) },
                    label = { Text(day.shortText(locale)) },
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                            },
                )
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

@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((amount, text) in options) {
            val description = stringResource(R.string.special_options_description, label, text)
            FilterChip(
                selected = value == amount,
                onClick = { viewModel.setSpecialDay(day, amount) },
                label = { Text(text) },
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = description },
            )
        }
    }
}
