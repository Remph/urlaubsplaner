// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.bridge

import java.time.LocalDate

/**
 * The bridges around one public holiday: what to take before it and what to take after it. A
 * holiday in the middle of the week has both (Wednesday: Monday and Tuesday, Thursday and
 * Friday), so a screen shows them under the holiday's name instead of as two unrelated entries.
 */
data class BridgeGroup(
    val anchor: LocalDate,
    val before: Bridge?,
    val after: Bridge?,
) {
    init {
        require(before != null || after != null) { "A group needs at least one bridge" }
    }

    /** Before first, then after. */
    val bridges: List<Bridge> get() = listOfNotNull(before, after)
}

/** The bridges grouped by their [Bridge.anchor], earliest holiday first. */
fun List<Bridge>.groupedByAnchor(): List<BridgeGroup> =
    groupBy { it.anchor }
        .toSortedMap()
        .map { (anchor, bridges) ->
            // One side holds at most one bridge: a second gap on the same side would have a day of
            // the first one between it and the holiday. The first by date wins if it ever did.
            val sorted = bridges.sortedBy { it.leave.first() }
            BridgeGroup(
                anchor = anchor,
                before = sorted.firstOrNull { it.isBeforeAnchor },
                after = sorted.firstOrNull { !it.isBeforeAnchor },
            )
        }
