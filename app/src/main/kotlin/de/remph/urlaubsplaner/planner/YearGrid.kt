// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.remph.urlaubsplaner.R
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * The whole year at a glance: twelve rows, one per month, 31 columns for the days. Too small to
 * tap a single day (a phone is about 10 dp per column), so a tap opens the tapped month.
 */
@Composable
fun YearGrid(
    view: YearView,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 20.dp,
) {
    val colors = dayColors()
    val locale = currentLocale()
    val labelStyle = MaterialTheme.typography.labelSmall
    val monthNames =
        remember(locale) {
            (1..12).map {
                Month.of(it).getDisplayName(TextStyle.SHORT, locale).trimEnd('.')
            }
        }
    val labelBox = monthLabelBox(monthNames, labelStyle)
    // At a large font the label is taller than the row: the row grows with it, and the canvas
    // with it, so the tiles stay level with their month name.
    val rowHeight = maxOf(rowHeight, labelBox.height)
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = ink.copy(alpha = 0.07f)
    val smallCorner = MaterialTheme.shapes.extraSmall.topStart
    val looks =
        remember(view) {
            (1..12).map { month ->
                val first = YearMonth.of(view.year, month)
                (1..first.lengthOfMonth()).map { view.look(first.atDay(it)) }
            }
        }
    val description = stringResource(R.string.year_grid_description, view.year)
    // The canvas is one touch area, so TalkBack gets one action per month instead.
    val monthActions =
        (1..12).map { month ->
            val name = Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
            CustomAccessibilityAction(name) {
                onMonthClick(YearMonth.of(view.year, month))
                true
            }
        }
    Row(
        modifier.fillMaxWidth().semantics {
            contentDescription = description
            customActions = monthActions
        },
    ) {
        Column {
            for (name in monthNames) {
                Text(
                    text = name,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(labelBox.width).height(rowHeight),
                    maxLines = 1,
                )
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(rowHeight * 12)
                .pointerInput(view.year) {
                    detectTapGestures { tap ->
                        val row = (tap.y / (size.height / 12f)).toInt().coerceIn(0, 11)
                        onMonthClick(YearMonth.of(view.year, row + 1))
                    }
                },
        ) {
            val column = size.width / 31f
            val row = size.height / 12f
            val gap = 1.dp.toPx()
            // Tiles are about 11 dp wide; the theme's smallest step keeps them tiles, not pills.
            looks.forEachIndexed { monthIndex, days ->
                days.forEachIndexed { dayIndex, look ->
                    val topLeft = Offset(dayIndex * column + gap / 2, monthIndex * row + gap / 2)
                    val cell = Size(column - gap, row - gap)
                    val corner = CornerRadius(smallCorner.toPx(cell, this))
                    val fill =
                        when {
                            look.taken -> colors.taken
                            look.granted -> colors.granted
                            look.holiday -> colors.holiday
                            look.school -> colors.school
                            !look.workDay -> faint
                            else -> null
                        }
                    if (fill != null) drawRoundRect(fill, topLeft, cell, corner)
                    if (look.mark != null && !look.taken) {
                        drawRoundRect(
                            colors.planned,
                            topLeft,
                            cell,
                            corner,
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                    // The month view's dashed outline (the maintainer chose it over a dot, 2026-10-26),
                    // with shorter dashes to fit a tile of about 10 dp.
                    if (look.bridge) {
                        drawRoundRect(
                            colors.planned,
                            topLeft,
                            cell,
                            corner,
                            style =
                                Stroke(
                                    1.5.dp.toPx(),
                                    pathEffect =
                                        PathEffect.dashPathEffect(
                                            floatArrayOf(3.dp.toPx(), 2.dp.toPx()),
                                        ),
                                ),
                        )
                    }
                    if (look.today) {
                        drawRoundRect(ink, topLeft, cell, corner, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
    }
}

/**
 * The size of the month label column: as wide as the widest month name really is, and as high as
 * one line of the label style at the current font scale.
 */
@Composable
private fun monthLabelBox(
    names: List<String>,
    style: androidx.compose.ui.text.TextStyle,
): DpSize {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(names, style, density, measurer) {
        val sizes = names.map { measurer.measure(it, style, maxLines = 1).size }
        with(density) {
            DpSize(
                // The label is never cut and never wider than a seventh of a phone's width.
                minOf(
                    maxOf(MONTH_LABEL_MIN, sizes.maxOf { it.width }.toDp() + MONTH_LABEL_GAP),
                    MONTH_LABEL_MAX,
                ),
                // One line of the style, scaled linearly with the font (sp.toDp() follows the
                // system's flatter curve at 200 % and came out 4 dp short of the drawn line).
                if (style.lineHeight.isSp) {
                    (style.lineHeight.value * density.fontScale).dp
                } else {
                    sizes
                        .maxOf { it.height }
                        .toDp()
                },
            )
        }
    }
}

private val MONTH_LABEL_MIN = 32.dp
private val MONTH_LABEL_GAP = 4.dp
private val MONTH_LABEL_MAX = 56.dp

/** The row height of the year grid when it is compact; expanded it fits the screen, see [expandedRowHeight]. */
val YearRowsCompact = 28.dp

/**
 * Swipe down on the grid to expand its rows, swipe up to make them compact again, like the
 * stock calendar app of some phones. The swipe is only watched, never consumed, so the page still scrolls: when
 * the rows are expanded and the page can scroll, a swipe up scrolls it first, and only a swipe
 * that starts with the page at its end makes the rows compact. [pageAtEnd] says that.
 */
fun Modifier.expandBySwipe(
    expanded: Boolean,
    pageAtEnd: () -> Boolean,
    onChange: (Boolean) -> Unit,
): Modifier =
    pointerInput(expanded) {
        val threshold = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val canCollapse = pageAtEnd()
            var moved = 0f
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                moved += change.positionChange().y
            } while (change.pressed)
            if (moved > threshold && !expanded) {
                onChange(true)
            } else if (moved < -threshold && expanded && canCollapse) {
                onChange(false)
            }
        }
    }

/** A grab handle under the grid: the same as the swipe, for a tap and for TalkBack. */
@Composable
fun YearRowsHandle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val label =
        stringResource(if (expanded) R.string.year_rows_collapse else R.string.year_rows_expand)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
        )
    }
}
