// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.ui

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Material's "shared axis" on the x axis: a short slide with a fade, the old screen leaves first.
// Compose follows Android's "Remove animations" setting, so nothing extra is needed for it.
private const val OUT_MILLIS = 90
private const val IN_MILLIS = 210
private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Builds the transition for [AnimatedContentTransitionScope]; [forward] slides in from the right. */
@Composable
internal fun <S> rememberSharedAxis(): AnimatedContentTransitionScope<S>.(
    Boolean,
) -> ContentTransform {
    val offset = with(LocalDensity.current) { 40.dp.roundToPx() }
    return { forward ->
        val sign = if (forward) 1 else -1
        (
            slideInHorizontally(tween(OUT_MILLIS + IN_MILLIS, easing = Decelerate)) {
                sign * offset
            } +
                fadeIn(tween(IN_MILLIS, delayMillis = OUT_MILLIS, easing = Decelerate))
        ) togetherWith (
            slideOutHorizontally(tween(OUT_MILLIS, easing = Accelerate)) { -sign * offset } +
                fadeOut(tween(OUT_MILLIS, easing = Accelerate))
        )
    }
}

/**
 * True when Android's "Remove animations" is on. Compose then ends every animation in its first
 * frame, but a number that starts at zero would still show that zero for that one frame.
 */
@Composable
internal fun rememberAnimationsRemoved(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { animationsRemoved(resolver) }
}

internal fun animationsRemoved(resolver: ContentResolver) =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
