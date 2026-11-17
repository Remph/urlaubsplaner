// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.balance

/**
 * An amount of vacation in half-day steps. Stored as a count of half days so sums never pick up
 * floating point errors. Can be negative: an overbooked year has fewer than zero days left.
 */
@JvmInline
value class VacationDays private constructor(
    private val halves: Int,
) : Comparable<VacationDays> {
    val days: Double get() = halves / 2.0

    operator fun plus(other: VacationDays) = VacationDays(halves + other.halves)

    operator fun minus(other: VacationDays) = VacationDays(halves - other.halves)

    operator fun unaryMinus() = VacationDays(-halves)

    override fun compareTo(other: VacationDays) = halves.compareTo(other.halves)

    override fun toString() = if (halves % 2 == 0) (halves / 2).toString() else days.toString()

    companion object {
        val ZERO = VacationDays(0)
        val HALF = VacationDays(1)
        val ONE = VacationDays(2)

        fun ofHalves(halves: Int) = VacationDays(halves)

        fun of(days: Int) = VacationDays(Math.multiplyExact(days, 2))

        /** [days] must be a multiple of 0.5. */
        fun of(days: Double): VacationDays {
            val halves = days * 2
            require(Math.abs(halves) <= Int.MAX_VALUE && halves == Math.rint(halves)) {
                "Vacation is counted in half days, got $days"
            }
            return VacationDays(halves.toInt())
        }
    }
}
