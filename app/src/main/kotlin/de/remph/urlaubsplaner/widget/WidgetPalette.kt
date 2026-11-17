// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import de.remph.urlaubsplaner.appearance.FixedDark
import de.remph.urlaubsplaner.appearance.FixedLight

/** The colours the widget draws with in one mode, from a Material colour scheme. */
class WidgetSwatch(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val error: Color,
    // The warning strip is gold like a holiday in the calendar (DayColors), in both modes.
    val warning: Color,
    val onWarning: Color,
) {
    companion object {
        fun of(
            scheme: ColorScheme,
            dark: Boolean,
        ) = WidgetSwatch(
            background = if (dark) scheme.surfaceContainer else scheme.surface,
            ink = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            primary = scheme.primary,
            error = scheme.error,
            warning = if (dark) Color(0xFF6B5320) else Color(0xFFF7E3B5),
            onWarning = if (dark) Color(0xFFF3D9A0) else Color(0xFF5A3C0A),
        )
    }
}

/**
 * The widget's colours for light and dark, each as a [ColorProvider] that carries both, so the
 * launcher switches between them with the phone. "Light" and "Dark" give the same colours for
 * both. Like the phone means the wallpaper colours on Android 12 and up and the app's own teal
 * scheme on Android 11, the same as the app, but never the app's own Darstellung setting (the maintainer).
 */
class WidgetPalette(
    private val light: WidgetSwatch,
    private val dark: WidgetSwatch,
) {
    fun provider(
        alpha: Float = 1f,
        pick: (WidgetSwatch) -> Color,
    ): ColorProvider =
        ColorProvider(
            day = pick(light).copy(alpha = alpha),
            night = pick(dark).copy(alpha = alpha),
        )

    companion object {
        fun of(
            context: Context,
            colors: WidgetColors,
        ): WidgetPalette {
            val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val light =
                WidgetSwatch.of(
                    if (dynamic) dynamicLightColorScheme(context) else FixedLight,
                    false,
                )
            val dark =
                WidgetSwatch.of(
                    if (dynamic) dynamicDarkColorScheme(context) else FixedDark,
                    true,
                )
            return when (colors) {
                WidgetColors.PHONE -> WidgetPalette(light, dark)
                WidgetColors.LIGHT -> WidgetPalette(light, light)
                WidgetColors.DARK -> WidgetPalette(dark, dark)
            }
        }
    }
}
