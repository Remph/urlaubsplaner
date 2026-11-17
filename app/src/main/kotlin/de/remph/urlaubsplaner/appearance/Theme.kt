// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.appearance

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * The app's colours. Whether it is dark comes from the activity's configuration, which
 * [AppearanceStore] steers through AppCompat, so this reads the same for "system" and for a
 * choice made in the settings. Dynamic colours need Android 12 (API 31); minSdk is 30.
 */
@Composable
fun UrlaubsplanerTheme(
    appearance: Appearance,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val canUseSystemColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors =
        remember(dark, appearance, canUseSystemColors) {
            val system =
                if (!canUseSystemColors) {
                    null
                } else if (dark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            appColorScheme(dark, appearance, system)
        }
    // The window behind Compose follows the XML theme (dark grey in the dark theme); without this
    // it shows through as a grey flash while the activity restarts, also in pure black.
    SideEffect {
        context.findActivity()?.window?.setBackgroundDrawable(
            ColorDrawable(colors.background.toArgb()),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * [system] is the scheme from the wallpaper, or null where there is none (Android 11). It is used
 * when [Appearance.systemColors] is on; otherwise the fixed teal scheme is.
 */
fun appColorScheme(
    dark: Boolean,
    appearance: Appearance,
    system: ColorScheme?,
): ColorScheme {
    val base = system?.takeIf { appearance.systemColors } ?: if (dark) FixedDark else FixedLight
    return when {
        !dark -> base
        appearance.oled -> base.onPureBlack()
        else -> base.liftedFromBlack()
    }
}

/**
 * Some phones (seen 2026-10-25) hand out a wallpaper palette whose dark background is
 * already almost black, so "Dunkel" would look like pure black and the pure black switch would do
 * nothing. A background that dark is moved halfway towards the card colour: a soft dark grey,
 * with the pure black choice still a step darker. Palettes with a normal dark grey stay as is.
 */
private fun ColorScheme.liftedFromBlack(): ColorScheme {
    if (background.luminance() >= NEAR_BLACK) return this
    val lifted = lerp(background, surfaceContainerLow, 0.5f)
    return copy(background = lifted, surface = lifted, surfaceDim = lifted)
}

private const val NEAR_BLACK = 0.003f

/** Black background, and surfaces that rise from black in small steps so cards stay visible. */
private fun ColorScheme.onPureBlack() =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0E11),
        surfaceContainer = Color(0xFF10151A),
        surfaceContainerHigh = Color(0xFF161C21),
        surfaceContainerHighest = Color(0xFF1B2228),
        surfaceBright = Color(0xFF1B2228),
    )

/** The app's own colours: teal for vacation, gold for holidays (used from the calendar on). */
internal val FixedLight =
    lightColorScheme(
        primary = Color(0xFF0E6F72),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCFEBEA),
        onPrimaryContainer = Color(0xFF07474A),
        secondary = Color(0xFF4A6363),
        secondaryContainer = Color(0xFFCFEBEA),
        onSecondaryContainer = Color(0xFF07474A),
        tertiary = Color(0xFFB7791F),
        tertiaryContainer = Color(0xFFF7E3B5),
        onTertiaryContainer = Color(0xFF6B4710),
        background = Color(0xFFF4F6F8),
        surface = Color(0xFFF4F6F8),
        onBackground = Color(0xFF16202A),
        onSurface = Color(0xFF16202A),
        onSurfaceVariant = Color(0xFF5C6B78),
        outline = Color(0xFF7D8B97),
        outlineVariant = Color(0xFFDDE3E9),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color(0xFFEEF1F4),
        surfaceContainerHigh = Color(0xFFE8ECF0),
        surfaceContainerHighest = Color(0xFFE3E8ED),
    )

internal val FixedDark =
    darkColorScheme(
        primary = Color(0xFF5CC6C9),
        onPrimary = Color(0xFF05393B),
        primaryContainer = Color(0xFF1F4A4C),
        onPrimaryContainer = Color(0xFFBFEFF0),
        secondary = Color(0xFFA9C8C8),
        secondaryContainer = Color(0xFF1F4A4C),
        onSecondaryContainer = Color(0xFFBFEFF0),
        tertiary = Color(0xFFE0AC4E),
        tertiaryContainer = Color(0xFF4A3A16),
        onTertiaryContainer = Color(0xFFF3D9A0),
        background = Color(0xFF12171B),
        surface = Color(0xFF12171B),
        onBackground = Color(0xFFE3E8EC),
        onSurface = Color(0xFFE3E8EC),
        onSurfaceVariant = Color(0xFF98A6B2),
        outline = Color(0xFF7D8B97),
        outlineVariant = Color(0xFF2B363F),
        surfaceContainerLowest = Color(0xFF0E1317),
        surfaceContainerLow = Color(0xFF161C21),
        surfaceContainer = Color(0xFF1B2228),
        surfaceContainerHigh = Color(0xFF222B32),
        surfaceContainerHighest = Color(0xFF2B363F),
    )
