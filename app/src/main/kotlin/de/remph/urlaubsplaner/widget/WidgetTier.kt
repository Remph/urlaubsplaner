// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * What the wide widget (4x2) shows at the size it really has. The layout was made for 320 x 150 dp
 * at the normal font; at its declared minimum (250 x 110) and at a larger font the bottom strip
 * was cut off (UI audit 06), so it steps down: FULL has a two-line warning strip, COMPACT a
 * one-line short one, MINIMAL only the number and the next trip.
 */
internal enum class WideTier { FULL, COMPACT, MINIMAL }

/** The small widget (2x1): number, unit and the next-trip line, or at a low cell the number and unit only. */
internal enum class SmallTier { FULL, NUMBER }

// Heights in dp at the normal font that each tier needs; the text part grows with the font scale,
// the padding and gaps do not (measured with the renderer, see WidgetTierTest).
private const val WIDE_FULL_FIXED = 22f
private const val WIDE_FULL_TEXT = 128f
private const val WIDE_COMPACT_FIXED = 20f
private const val WIDE_COMPACT_TEXT = 85f
private const val SMALL_FULL_FIXED = 12f
private const val SMALL_FULL_TEXT = 54f

/** A font scale below 1 does not make the text smaller than the layout was made for. */
private fun textScale(fontScale: Float) = maxOf(1f, fontScale)

/** No size at all (a test, or a launcher that gives none): the full layout, as before. */
private fun DpSize.known() =
    width.value > 0f && height.value > 0f && width.isSpecified && height.isSpecified

internal fun wideTier(
    size: DpSize,
    fontScale: Float,
): WideTier {
    if (!size.known()) return WideTier.FULL
    val scale = textScale(fontScale)
    return when {
        size.height.value >= WIDE_FULL_FIXED + WIDE_FULL_TEXT * scale -> WideTier.FULL
        size.height.value >= WIDE_COMPACT_FIXED + WIDE_COMPACT_TEXT * scale -> WideTier.COMPACT
        else -> WideTier.MINIMAL
    }
}

internal fun smallTier(
    size: DpSize,
    fontScale: Float,
): SmallTier {
    if (!size.known()) return SmallTier.FULL
    val scale = textScale(fontScale)
    return if (size.height.value >= SMALL_FULL_FIXED + SMALL_FULL_TEXT * scale) {
        SmallTier.FULL
    } else {
        SmallTier.NUMBER
    }
}

/**
 * The number of the lowest small widget, as big as the cell allows in both directions: a line is
 * about 1.4 times the size (font padding included), and the number plus its unit must fit the
 * width (a digit is about 0.58 em wide, a comma 0.3 em, a letter of the unit 0.52 em at 10 sp).
 * A larger font scale makes the same sp bigger, so the sp goes down by it.
 */
internal fun numberSize(
    size: DpSize,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    fontScale: Float,
    number: String,
    unit: String,
): TextUnit {
    val scale = textScale(fontScale)
    val byHeight = (size.height.value - 2 * verticalPadding.value) / (LINE_FACTOR * scale)
    val em = number.sumOf { if (it.isDigit()) DIGIT_EM else COMMA_EM.toDouble() }.toFloat()
    val room = size.width.value - 2 * horizontalPadding.value - UNIT_GAP_DP - unitWidth(unit, scale)
    val byWidth = room / (em.coerceAtLeast(1f) * scale)
    return minOf(byHeight, byWidth).coerceIn(MIN_NUMBER_SP, MAX_NUMBER_SP).sp
}

/** The unit's text size and its width in dp, at the given font scale. */
internal const val UNIT_SP = 10f

private fun unitWidth(
    unit: String,
    scale: Float,
) = UNIT_SP * UNIT_EM * unit.length * scale

private const val LINE_FACTOR = 1.4f
private const val DIGIT_EM = 0.58
private const val COMMA_EM = 0.3f
private const val UNIT_EM = 0.52f
internal const val UNIT_GAP_DP = 3f
private const val MIN_NUMBER_SP = 12f
private const val MAX_NUMBER_SP = 26f

/** In the lowest tier the cost line under the trip goes too when even the trip alone is a tight fit. */
internal fun showTripCost(
    size: DpSize,
    fontScale: Float,
): Boolean {
    if (!size.known()) return true
    return size.height.value >= WIDE_COST_FIXED + WIDE_COST_TEXT * textScale(fontScale)
}

private const val WIDE_COST_FIXED = 10f
private const val WIDE_COST_TEXT = 88f

/** The year fits next to the number ("Tage übrig 2027") only when the column is wide enough. */
internal fun showYear(
    size: DpSize,
    fontScale: Float,
): Boolean {
    if (!size.known()) return true
    // The text column is half of the width less the padding and the divider.
    val column = (size.width.value - 49f) / 2f
    return column >= YEAR_LINE_DP * textScale(fontScale)
}

private const val YEAR_LINE_DP = 85f
