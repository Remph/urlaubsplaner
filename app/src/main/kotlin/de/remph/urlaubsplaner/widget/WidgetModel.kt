// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.content.Context
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.AmountText
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.widget.WidgetStatus
import de.remph.urlaubsplaner.ui.Tab
import java.util.Locale

/**
 * Everything the widget shows, as finished text. The numbers come from [WidgetStatus]; this only
 * puts them into words in the app language, so the composables stay free of logic and the words
 * can be tested without a launcher.
 */
sealed interface WidgetModel {
    /** The tab a tap on the widget opens: the missing input when there is one, else the overview. */
    val opens: Tab

    /** Without a Bundesland or without this year's days there are no numbers to show. */
    data class Prompt(
        val title: String,
        val subtitle: String,
    ) : WidgetModel {
        override val opens: Tab get() = Tab.SETTINGS
    }

    data class Status(
        /** Days left; the minus sign is a real minus (U+2212). */
        val number: String,
        /** Overbooked: the number is drawn in the error colour. */
        val overbooked: Boolean,
        /** Under the number of the small widget: "Tage übrig" or "überbucht". */
        val unit: String,
        /** Under the number of the wide widget, with the year. */
        val unitWithYear: String,
        /** The one line of the small widget: "Nächster Urlaub 14.04.". */
        val summary: String,
        /** Wide: "Nächster Urlaub", "Du bist gerade im Urlaub". */
        val tripLabel: String,
        /** Wide: "14.–16. April", "bis 16. April", "Noch kein Urlaub geplant". */
        val tripWhen: String,
        /** Wide: "5 Urlaubstage"; null on vacation and without a trip. */
        val tripCost: String?,
        /** Wide: the three figures, or the warning in their place. */
        val figures: List<String>,
        val warning: String?,
    ) : WidgetModel {
        override val opens: Tab get() = Tab.OVERVIEW
    }

    companion object {
        /**
         * [context] must already be in the app language (see [inAppLanguage]); [locale] is that
         * language, for the number and date formats.
         */
        fun of(
            status: WidgetStatus,
            context: Context,
            locale: Locale,
        ): WidgetModel =
            when (status) {
                WidgetStatus.NoLand -> {
                    Prompt(
                        context.getString(R.string.land_choose),
                        context.getString(R.string.widget_no_land_hint),
                    )
                }

                is WidgetStatus.NoEntitlement -> {
                    Prompt(
                        context.getString(R.string.enter_vacation_days),
                        context.getString(R.string.widget_no_entitlement_hint, status.year),
                    )
                }

                is WidgetStatus.Ready -> {
                    status(status, context, locale)
                }
            }

        private fun status(
            status: WidgetStatus.Ready,
            context: Context,
            locale: Locale,
        ): Status {
            val year = status.year
            val remaining = status.balance.remaining
            val overbooked = remaining < VacationDays.ZERO
            val trip = status.nextTrip
            val dayPattern = context.getString(R.string.widget_day_pattern)
            val taken = text(status.balance.taken, locale)
            val planned = text(status.balance.planned, locale)
            val summary =
                when {
                    trip == null -> {
                        context.getString(R.string.widget_summary_no_trip)
                    }

                    status.onTrip -> {
                        context.getString(
                            R.string.widget_summary_on_trip,
                            WidgetDates.short(trip.end, year),
                        )
                    }

                    else -> {
                        context.getString(
                            R.string.widget_summary_next_trip,
                            WidgetDates.short(trip.start, year),
                        )
                    }
                }
            val (label, whenText, cost) =
                when {
                    trip == null -> {
                        Triple(
                            context.getString(R.string.next_trip),
                            context.getString(R.string.no_trip),
                            null,
                        )
                    }

                    status.onTrip -> {
                        Triple(
                            context.getString(R.string.widget_on_trip),
                            context.getString(
                                R.string.widget_until,
                                WidgetDates.range(trip.end, trip.end, year, locale, dayPattern),
                            ),
                            null,
                        )
                    }

                    else -> {
                        Triple(
                            context.getString(R.string.next_trip),
                            WidgetDates.range(trip.start, trip.end, year, locale, dayPattern),
                            context.resources.getQuantityString(
                                R.plurals.widget_trip_cost,
                                quantityOf(trip.cost),
                                text(trip.cost, locale),
                            ),
                        )
                    }
                }
            val warning =
                status.carryOverWarning?.let {
                    val expiry =
                        if (it.expiry.year == year) {
                            AmountText.formatDayMonth(java.time.MonthDay.from(it.expiry))
                        } else {
                            AmountText.formatDate(it.expiry)
                        }
                    context.resources.getQuantityString(
                        R.plurals.widget_carry_over_warning,
                        quantityOf(it.days),
                        text(it.days, locale),
                        expiry,
                    )
                }
            return Status(
                number = text(remaining, locale).replace('-', MINUS),
                overbooked = overbooked,
                unit =
                    context.getString(
                        if (overbooked) R.string.widget_overbooked else R.string.widget_days_left,
                    ),
                unitWithYear =
                    if (overbooked) {
                        context.getString(R.string.widget_overbooked)
                    } else {
                        context.getString(R.string.widget_days_left_year, year)
                    },
                summary = summary,
                tripLabel = label,
                tripWhen = whenText,
                tripCost = cost,
                figures =
                    listOf(
                        "${context.getString(R.string.taken)} $taken",
                        "${context.getString(R.string.planned)} $planned",
                        "${context.getString(R.string.of_total)} ${text(status.total, locale)}",
                    ),
                warning = warning,
            )
        }

        private fun text(
            days: VacationDays,
            locale: Locale,
        ) = AmountText.format(days, locale)

        private fun quantityOf(days: VacationDays) = if (days == VacationDays.ONE) 1 else 2

        private const val MINUS = '−'
    }
}
