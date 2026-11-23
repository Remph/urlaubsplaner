// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.remph.urlaubsplaner.R
import kotlin.math.roundToInt

private const val STEP_PERCENT = 5

/** What to set per widget: how see-through the background is, and the colours. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WidgetSetupScreen(
    style: WidgetStyle,
    onChange: (WidgetStyle) -> Unit,
    onDone: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.widget_setup_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            val percent = stringResource(R.string.widget_percent, style.transparencyPercent)
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.widget_transparency),
                        style = MaterialTheme.typography.bodyLarge,
                        // The label gives way, so the value keeps its width at a large font.
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Text(
                        percent,
                        style = MaterialTheme.typography.titleMedium,
                        softWrap = false,
                    )
                }
                Slider(
                    value = style.transparencyPercent.toFloat(),
                    onValueChange = {
                        onChange(style.copy(transparencyPercent = it.roundToInt()))
                    },
                    valueRange = 0f..100f,
                    steps = 100 / STEP_PERCENT - 1,
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            stateDescription = percent
                        },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.widget_colors),
                    style = MaterialTheme.typography.bodyLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (option in WidgetColors.entries) {
                        FilterChip(
                            selected = style.colors == option,
                            onClick = { onChange(style.copy(colors = option)) },
                            label = { Text(stringResource(option.labelRes())) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.widget_setup_done))
            }
        }
    }
}

private fun WidgetColors.labelRes() =
    when (this) {
        WidgetColors.PHONE -> R.string.widget_colors_phone
        WidgetColors.LIGHT -> R.string.theme_light
        WidgetColors.DARK -> R.string.theme_dark
    }
