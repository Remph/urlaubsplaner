// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Closes a bottom sheet from one of its buttons the way a swipe or a tap beside it does: the sheet
 * slides down first and only then leaves the screen. Calling the sheet's onDismiss at once took it
 * away from one frame to the next (performance and feel review, 2026-10-31).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class SheetClose internal constructor(
    private val state: SheetState,
    private val scope: CoroutineScope,
    private val onDismiss: () -> Unit,
) {
    // From the first button tap on, so a second tap while it slides does not store twice.
    private var closing = false

    /** Runs [action] (store the days, say so) once, then slides the sheet away. */
    fun after(action: () -> Unit = {}) {
        if (closing) return
        closing = true
        action()
        // Also when the slide is cut short (the sheet leaves the screen some other way): the
        // action has run, so the sheet must not stay open showing what it was.
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetClose(
    state: SheetState,
    onDismiss: () -> Unit,
): SheetClose {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    return remember(state, scope) { SheetClose(state, scope) { dismiss() } }
}
