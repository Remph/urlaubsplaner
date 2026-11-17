// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

/**
 * Holidays that apply only in some communities of a Land. They are off by default, because
 * counting them for the whole Land would overcount for most people. An extra for a Land it does
 * not belong to is ignored.
 */
enum class RegionalExtra(
    val holiday: Holiday,
    val lands: Set<Bundesland>,
) {
    /** Only in the city of Augsburg. */
    AUGSBURGER_FRIEDENSFEST(Holiday.AUGSBURGER_FRIEDENSFEST, setOf(Bundesland.BAYERN)),

    /** Only in some communities (Sachsen: around Bautzen and Kamenz; Thüringen: Eichsfeld and others). */
    FRONLEICHNAM_IN_TEILEN(Holiday.FRONLEICHNAM, setOf(Bundesland.SACHSEN, Bundesland.THUERINGEN)),
}
