// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

/**
 * A statewide holiday that some communities of a Land do not have. The Land counts it by default,
 * because most communities have it; an opt-out switches it off for the people who live elsewhere.
 * An opt-out for a Land it does not belong to is ignored.
 */
enum class RegionalOptOut(
    val holiday: Holiday,
    val lands: Set<Bundesland>,
) {
    /**
     * Bayern counts Mariä Himmelfahrt for all of the Land (about 1,700 communities with a mostly
     * Catholic population have it, the others work). Saarland has it everywhere and is not listed.
     */
    MARIAE_HIMMELFAHRT_BAYERN(Holiday.MARIAE_HIMMELFAHRT, setOf(Bundesland.BAYERN)),
}
