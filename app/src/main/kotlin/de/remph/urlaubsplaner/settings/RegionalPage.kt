// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut

/**
 * Holidays that only some communities have. Only what the chosen Land can have is listed, and the
 * list row that opens this page is not there for the other Länder.
 */
@Composable
fun RegionalPage(
    ui: SettingsUiState,
    viewModel: SettingsViewModel,
    outerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val land = ui.settings.land
    SettingsPageFrame(stringResource(R.string.section_regional), outerPadding, onBack) {
        if (land == null) return@SettingsPageFrame
        for (extra in RegionalExtra.entries.filter { land in it.lands }) {
            val (label, support) =
                when (extra) {
                    RegionalExtra.AUGSBURGER_FRIEDENSFEST -> {
                        R.string.extra_augsburg to R.string.extra_augsburg_support
                    }

                    RegionalExtra.FRONLEICHNAM_IN_TEILEN -> {
                        R.string.extra_fronleichnam to R.string.extra_fronleichnam_support
                    }
                }
            SwitchRow(
                label = stringResource(label),
                support = stringResource(support),
                checked = extra in ui.settings.extras,
                onChange = { viewModel.setExtra(extra, it) },
            )
        }
        for (optOut in RegionalOptOut.entries.filter { land in it.lands }) {
            val (label, support) =
                when (optOut) {
                    RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN -> {
                        R.string.mariae_himmelfahrt_bayern to
                            R.string.mariae_himmelfahrt_bayern_support
                    }
                }
            // The switch says "holiday", the stored opt-out the opposite: on = not opted out.
            SwitchRow(
                label = stringResource(label),
                support = stringResource(support),
                checked = optOut !in ui.settings.optOuts,
                onChange = { viewModel.setHolidayCounts(optOut, it) },
            )
        }
    }
}
