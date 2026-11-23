// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.AmountText
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import java.time.MonthDay

/** The vacation of one year: a year stepper, three fields, Speichern, and the carry-over option. */
@Composable
fun VacationPage(
    ui: SettingsUiState,
    viewModel: SettingsViewModel,
    outerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val form = ui.form
    SettingsPageFrame(stringResource(R.string.section_vacation), outerPadding, onBack) {
        val previous = stringResource(R.string.year_previous)
        val next = stringResource(R.string.year_next)
        val yearDescription = stringResource(R.string.year_description, ui.year)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The same arrows as the calendar header; a text glyph was small and tinted differently.
            IconButton(
                onClick = { viewModel.selectYear(ui.year - 1) },
                enabled = ui.canGoBack,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previous) }
            Text(
                text = ui.year.toString(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = yearDescription },
            )
            IconButton(
                onClick = { viewModel.selectYear(ui.year + 1) },
                enabled = ui.canGoForward,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = next) }
        }
        form.suggestedFrom?.let { from ->
            val carry = form.base?.carryOver
            val withCarryOver =
                ui.settings.suggestCarryOver && carry != null && carry > VacationDays.ZERO
            Text(
                text =
                    if (withCarryOver) {
                        stringResource(R.string.suggestion_from_year_with_carry_over, from)
                    } else {
                        stringResource(R.string.suggestion_from_year, from)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The year before is not over yet, so "left over" is only what is left today.
            if (from == viewModel.currentYear) {
                Text(
                    text = stringResource(R.string.suggestion_year_running, from),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AmountField(
            label = stringResource(R.string.field_days),
            value =
                form.typedDays ?: form.base
                    ?.days
                    ?.let { AmountText.format(it, locale) }
                    .orEmpty(),
            isError = form.daysError,
            error = stringResource(R.string.error_days),
            onChange = viewModel::editDays,
            modifier = Modifier.fillMaxWidth(),
        )
        AmountField(
            label = stringResource(R.string.field_carry_over),
            value =
                form.typedCarryOver
                    ?: AmountText.format(form.base?.carryOver ?: VacationDays.ZERO, locale),
            isError = form.carryOverError,
            error = stringResource(R.string.error_days),
            onChange = viewModel::editCarryOver,
            modifier = Modifier.fillMaxWidth(),
        )
        // Day and year share a line: the field is the shorter part, the year is a two-way choice.
        // At a large font the line is too narrow for the label, so each gets its own line.
        val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
        val expiryField: @Composable (Modifier) -> Unit = { fieldModifier ->
            AmountField(
                label = stringResource(R.string.field_carry_over_expiry),
                value =
                    form.typedExpiry
                        ?: AmountText.formatDayMonth(form.base?.expiry ?: MonthDay.of(3, 31)),
                isError = form.expiryError,
                error = stringResource(R.string.error_expiry),
                placeholder = stringResource(R.string.field_expiry_format),
                onChange = viewModel::editExpiry,
                modifier = fieldModifier,
            )
        }
        val yearChoice: @Composable (Modifier) -> Unit = { choiceModifier ->
            ExpiryYearChoice(
                year = ui.year,
                selected = form.typedExpiryYears ?: form.base?.expiryYears ?: 0,
                onSelect = viewModel::editExpiryYears,
                modifier = choiceModifier,
            )
        }
        if (stacked) {
            expiryField(Modifier.fillMaxWidth())
            yearChoice(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                expiryField(Modifier.weight(0.45f))
                yearChoice(Modifier.weight(0.55f))
            }
        }
        Button(
            onClick = viewModel::saveEntitlement,
            enabled = form.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save)) }
        SwitchRow(
            label = stringResource(R.string.suggest_carry_over),
            support = stringResource(R.string.suggest_carry_over_support),
            checked = ui.settings.suggestCarryOver,
            onChange = viewModel::setSuggestCarryOver,
        )
        SwitchRow(
            label = stringResource(R.string.granted_days),
            support = stringResource(R.string.granted_days_support),
            checked = ui.settings.grantedDays,
            onChange = viewModel::setGrantedDays,
        )
        ClearYearAction(ui.year, ui.markedDays, viewModel)
    }
}

/**
 * The one destructive action of the page, last and in the error colour. It only removes the
 * marked days; the dialog says so and points to the backup, because there is no undo.
 */
@Composable
private fun ClearYearAction(
    year: Int,
    markedDays: Int,
    viewModel: SettingsViewModel,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = { asking = true },
        enabled = markedDays > 0,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border =
            BorderStroke(
                1.dp,
                if (markedDays >
                    0
                ) {
                    error
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.clear_year_action, year)) }
    if (markedDays == 0) {
        Text(
            text = stringResource(R.string.clear_year_none, year),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (asking && markedDays > 0) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.clear_year_title, year)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.clear_year_body, markedDays, markedDays, year) +
                        "\n\n" + stringResource(R.string.clear_year_hint),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        viewModel.clearYear(year)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = error),
                ) { Text(stringResource(R.string.clear_year_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** In which year the Resturlaub expires: the entitlement's own, or the one after. */
@Composable
private fun ExpiryYearChoice(
    year: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // As high as the text field next to it (56dp, above the 48dp touch target). The field's box
    // starts 8dp below its top because the label sticks out there, so the buttons start there too.
    SingleChoiceSegmentedButtonRow(modifier = modifier.padding(top = 8.dp).height(56.dp)) {
        val count = YearEntitlement.MAX_EXPIRY_YEARS + 1
        for (offset in 0 until count) {
            val description = stringResource(R.string.expiry_in_year_description, year + offset)
            SegmentedButton(
                selected = selected == offset,
                onClick = { onSelect(offset) },
                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = offset,
                        count = count,
                        baseShape = MaterialTheme.shapes.large,
                    ),
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .semantics { contentDescription = description },
            ) { Text((year + offset).toString()) }
        }
    }
}

/** From this font scale on the expiry field and the year choice are stacked, not side by side. */
private const val STACK_FONT_SCALE = 1.3f
