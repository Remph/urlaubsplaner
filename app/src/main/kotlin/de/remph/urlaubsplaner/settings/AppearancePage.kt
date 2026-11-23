// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.appearance.AppearanceStore
import de.remph.urlaubsplaner.appearance.ThemeMode

/**
 * Light, dark or the system's choice, pure black for the dark theme, and the colours from the
 * wallpaper (Android 12 and up only, so the row is not there on Android 11).
 */
@Composable
fun AppearancePage(
    store: AppearanceStore,
    outerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val appearance by store.state.collectAsStateWithLifecycle()
    SettingsPageFrame(stringResource(R.string.section_appearance), outerPadding, onBack) {
        // The three modes sit on a card like the switches below, and are one group for TalkBack.
        SettingsGroup {
            Column(Modifier.selectableGroup().padding(horizontal = 16.dp, vertical = 4.dp)) {
                for (
                (mode, label) in
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                )
                ) {
                    ChoiceRow(stringResource(label), appearance.mode == mode) {
                        store.update { it.copy(mode = mode) }
                    }
                }
            }
        }
        SwitchRow(
            label = stringResource(R.string.theme_oled),
            support = stringResource(R.string.theme_oled_support),
            checked = appearance.oled,
            onChange = { on -> store.update { it.copy(oled = on) } },
            // "Im dunklen Modus": with Hell there is nothing to turn black. The value is kept,
            // so choosing Dunkel again brings the black back.
            enabled = appearance.mode != ThemeMode.LIGHT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                label = stringResource(R.string.system_colors),
                support = stringResource(R.string.system_colors_support),
                checked = appearance.systemColors,
                onChange = { on -> store.update { it.copy(systemColors = on) } },
            )
        }
    }
}
