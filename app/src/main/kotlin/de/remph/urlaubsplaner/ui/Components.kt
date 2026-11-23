// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Pieces that several screens share, moved here from the overview's file.

@Composable
fun ScreenFrame(
    outerPadding: PaddingValues,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(outerPadding)
                .consumeWindowInsets(outerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
 * For tests: called with a screen's name each time it composes, so a test can count how often the
 * overview composes during the intro. Null (and nothing called) in the app.
 */
internal val LocalCompositionProbe = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * Auto size for a one-line label: 12 sp down to a real 9 sp. The floor follows the font scale
 * (TextAutoSize counts in sp, so a plain 9 sp floor is 18 sp at 200 % and the label stayed cut).
 */
@Composable
fun labelAutoSize(): TextAutoSize {
    val scale = LocalDensity.current.fontScale
    return TextAutoSize.StepBased(
        minFontSize = (LABEL_FLOOR_SP / scale).coerceAtMost(LABEL_CEILING_SP).sp,
        maxFontSize = LABEL_CEILING_SP.sp,
    )
}

private const val LABEL_FLOOR_SP = 9f
private const val LABEL_CEILING_SP = 12f
