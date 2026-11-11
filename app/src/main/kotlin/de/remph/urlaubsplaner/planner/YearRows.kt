package de.remph.urlaubsplaner.planner

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The least and the most a row of the expanded year grid gets (the maintainer, 2026-10-27). */
val YearRowsMin = 36.dp
val YearRowsMax = 64.dp

/**
 * The row height of the expanded year grid: what fits when the rest of the page takes [others] of
 * the [available] height, so the page does not scroll, but not under [YearRowsMin] (then it
 * scrolls) and not over [YearRowsMax].
 */
fun expandedRowHeight(
    available: Dp,
    others: Dp,
): Dp = ((available - others) / MONTHS).coerceIn(YearRowsMin, YearRowsMax)

internal const val MONTHS = 12
