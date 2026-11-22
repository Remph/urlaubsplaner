// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.widget

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.Trip
import de.remph.urlaubsplaner.core.balance.VacationBalance
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.settings.UserSettings
import java.time.Clock
import java.time.LocalDate

/**
 * What the home screen widget shows, worked out from the stored data and today's date (from the
 * clock, always this year). The widget only draws it. Without a Land or without this year's
 * numbers there is nothing to count, so the widget asks for them instead of showing zeros.
 */
sealed interface WidgetStatus {
    /** No Bundesland chosen: without holiday rules a marked day has no cost. */
    data object NoLand : WidgetStatus

    data class NoEntitlement(
        val year: Int,
    ) : WidgetStatus

    data class Ready(
        val year: Int,
        val balance: VacationBalance,
        /** The trip that includes today or the first one after it; null when none is left. */
        val nextTrip: Trip?,
        /** Today lies inside [nextTrip]. */
        val onTrip: Boolean,
        /** Resturlaub that is still unused, set only until its Verfall day (included). */
        val carryOverWarning: CarryOverWarning?,
    ) : WidgetStatus {
        /** Entitlement plus Resturlaub: what the year offered in all. */
        val total: VacationDays get() = balance.entitlement + balance.carryOver
    }

    data class CarryOverWarning(
        val days: VacationDays,
        val expiry: LocalDate,
    )

    companion object {
        fun of(
            clock: Clock,
            settings: UserSettings,
            entitlements: Collection<YearEntitlement>,
            marks: Map<LocalDate, DayMark>,
        ): WidgetStatus {
            val check = settings.publicHolidayCheck() ?: return NoLand
            val today = LocalDate.now(clock)
            val entitlement =
                entitlements.firstOrNull { it.year == today.year }
                    ?: return NoEntitlement(today.year)
            val calculator = VacationCalculator(clock, settings.schedule, check)
            val balance = calculator.balance(entitlement, marks)
            val trip = calculator.trips(marks).firstOrNull { !it.end.isBefore(today) }
            val open = balance.carryOverOpen
            return Ready(
                year = today.year,
                balance = balance,
                nextTrip = trip,
                onTrip = trip != null && today in trip,
                carryOverWarning =
                    if (open > VacationDays.ZERO && !today.isAfter(balance.carryOverExpiry)) {
                        CarryOverWarning(open, balance.carryOverExpiry)
                    } else {
                        null
                    },
            )
        }
    }
}
