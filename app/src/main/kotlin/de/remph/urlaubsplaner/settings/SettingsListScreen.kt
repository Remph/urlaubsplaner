// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.UrlaubsplanerApp
import de.remph.urlaubsplaner.appearance.AppearanceStore
import de.remph.urlaubsplaner.appearance.ThemeMode
import de.remph.urlaubsplaner.core.balance.AmountText
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import java.util.Locale

/**
 * The short list: one row per topic, each with what is set now. Bundesland and language are
 * answered in a small dialog, the rest open their own page. Until a Land is chosen only what is
 * needed to start shows: the Land, language, and restoring a backup.
 */
@Composable
fun SettingsListScreen(
    ui: SettingsUiState,
    viewModel: SettingsViewModel,
    appearanceStore: AppearanceStore,
    outerPadding: PaddingValues,
    open: (SettingsPage) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    val context = LocalContext.current
    val appearance by appearanceStore.state.collectAsStateWithLifecycle()
    val settings = ui.settings
    val land = settings.land
    var landDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }

    SettingsPageFrame(stringResource(R.string.nav_settings), outerPadding, onBack = null) {
        if (land == null) {
            Text(
                stringResource(R.string.land_first_start),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.section_land),
                value =
                    land?.let { stringResource(it.labelRes()) }
                        ?: stringResource(R.string.land_choose),
                onClick = { landDialog = true },
            )
            if (land != null) {
                SettingsRow(
                    title = stringResource(R.string.settings_vacation_title, ui.year),
                    value = vacationSummary(ui, locale),
                    onClick = { open(SettingsPage.VACATION) },
                )
                SettingsRow(
                    title = stringResource(R.string.section_work_days),
                    value =
                        workDaysSummary(settings.schedule.workingDays, locale) { first, last ->
                            resources.getString(R.string.work_days_range, first, last)
                        },
                    onClick = { open(SettingsPage.WORK_DAYS) },
                )
                if (RegionalExtra.entries.any { land in it.lands } ||
                    RegionalOptOut.entries.any { land in it.lands }
                ) {
                    SettingsRow(
                        title = stringResource(R.string.section_regional),
                        value = regionalSummary(ui),
                        onClick = { open(SettingsPage.REGIONAL) },
                    )
                }
                SettingsRow(
                    title = stringResource(R.string.section_appearance),
                    value = appearanceSummary(appearance.mode, appearance.oled),
                    onClick = { open(SettingsPage.APPEARANCE) },
                )
            }
            if (land != null) {
                SettingsRow(
                    title = stringResource(R.string.section_calendar_export),
                    value = stringResource(R.string.calendar_export_row_value),
                    onClick = { open(SettingsPage.CALENDAR_EXPORT) },
                )
            }
            SettingsRow(
                title = stringResource(R.string.section_language),
                value = stringResource(LanguageChoice.current().labelRes()),
                onClick = { languageDialog = true },
            )
            SettingsRow(
                title = stringResource(R.string.section_backup),
                value = stringResource(R.string.backup_row_value),
                onClick = { open(SettingsPage.BACKUP) },
            )
            SettingsRow(
                title = stringResource(R.string.section_about),
                value = stringResource(R.string.about_row_value, appVersion(context)),
                onClick = { open(SettingsPage.ABOUT) },
                showDivider = false,
            )
        }
    }

    if (landDialog) {
        LandDialog(
            selected = land,
            onSelect = {
                viewModel.chooseLand(it)
                landDialog = false
            },
            onDismiss = { landDialog = false },
        )
    }
    if (languageDialog) {
        LanguageDialog(
            selected = LanguageChoice.current(),
            onSelect = {
                languageDialog = false
                // Restarts the activity in the new language.
                LanguageChoice.apply(context, it)
                (context.applicationContext as UrlaubsplanerApp).widgetRefresher.refresh()
            },
            onDismiss = { languageDialog = false },
        )
    }
}

@Composable
private fun vacationSummary(
    ui: SettingsUiState,
    locale: Locale,
): String {
    val base = ui.form.base ?: return stringResource(R.string.vacation_not_entered)
    if (ui.form.isSuggestion) {
        return stringResource(R.string.vacation_suggested, ui.form.suggestedFrom ?: ui.year)
    }
    val resources = LocalResources.current
    val days =
        daysWithUnit(
            base.days,
            locale,
            one = { resources.getString(R.string.days_one, it) },
            other = { resources.getString(R.string.days_other, it) },
        )
    if (base.carryOver.days == 0.0) return days
    val carry = AmountText.format(base.carryOver, locale)
    return stringResource(
        R.string.vacation_with_carry_over,
        days,
        carry,
        if (base.expiryYears == 0) {
            AmountText.formatDayMonth(base.expiry)
        } else {
            AmountText.formatDate(base.expiry.atYear(ui.year + base.expiryYears))
        },
    )
}

@Composable
private fun regionalSummary(ui: SettingsUiState): String {
    val settings = ui.settings
    val land = settings.land ?: return ""
    val names = mutableListOf<String>()
    if (RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN.let {
            land in it.lands && it !in settings.optOuts
        }
    ) {
        names += stringResource(R.string.regional_short_mariae)
    }
    if (RegionalExtra.AUGSBURGER_FRIEDENSFEST in settings.extras &&
        land in RegionalExtra.AUGSBURGER_FRIEDENSFEST.lands
    ) {
        names += stringResource(R.string.regional_short_augsburg)
    }
    if (RegionalExtra.FRONLEICHNAM_IN_TEILEN in settings.extras &&
        land in RegionalExtra.FRONLEICHNAM_IN_TEILEN.lands
    ) {
        names += stringResource(R.string.regional_short_fronleichnam)
    }
    return if (names.isEmpty()) stringResource(R.string.regional_none) else names.joinToString(", ")
}

@Composable
private fun appearanceSummary(
    mode: ThemeMode,
    oled: Boolean,
): String {
    val name =
        stringResource(
            when (mode) {
                ThemeMode.SYSTEM -> R.string.theme_system
                ThemeMode.LIGHT -> R.string.theme_light
                ThemeMode.DARK -> R.string.theme_dark
            },
        )
    return if (oled &&
        mode != ThemeMode.LIGHT
    ) {
        name + ", " + stringResource(R.string.theme_oled_short)
    } else {
        name
    }
}
