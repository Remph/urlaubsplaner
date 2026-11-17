// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

/**
 * What the form for a year nobody has filled in yet starts with. It is a suggestion only; the
 * employer's figures are the ones that count, so nothing is saved until the user says so.
 */
object EntitlementSuggestion {
    /**
     * The days and the Verfall day (with its year offset) come from the year before. The
     * Resturlaub is zero, unless the user switched on carrying over ([carryOverUnused], off by
     * default): then it is what [previousBalance] still has left, never below zero. That is the
     * state of today. Days that are still planned in the year before already count as used, days
     * not planned yet do not.
     *
     * Null when there is no year before to learn from.
     */
    fun forYear(
        year: Int,
        previous: YearEntitlement?,
        previousBalance: VacationBalance?,
        carryOverUnused: Boolean,
    ): YearEntitlement? {
        if (previous == null) return null
        val carryOver =
            if (carryOverUnused && previousBalance != null) {
                maxOf(VacationDays.ZERO, previousBalance.remaining)
            } else {
                VacationDays.ZERO
            }
        return YearEntitlement(
            year = year,
            days = previous.days,
            carryOver = carryOver,
            carryOverExpiry = previous.carryOverExpiry,
            carryOverExpiryYears = previous.carryOverExpiryYears,
        )
    }
}
