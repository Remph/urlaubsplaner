// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.ics

import de.remph.urlaubsplaner.core.balance.AmountText
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkStatus
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.Trip
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The vacation as an iCalendar file (RFC 5545) for a calendar app or to send to someone. Decided
 * with the maintainer on 2026-10-26: only the vacation, no public or school holidays, so the file needs no
 * ODbL credit; one all-day event per trip (the marked days of [VacationCalculator.trips], a
 * weekend between two weeks stays inside it), taken and planned days alike.
 *
 * Decided with the maintainer on 2026-10-27 (0.10.0): an event is `STATUS:CONFIRMED` when its days
 * are taken or granted and `STATUS:TENTATIVE` when they are only planned, so a trip that mixes
 * both is split into one event per run of days with the same status. A weekend between two runs
 * belongs to neither. This is only done while the granted days setting is on (`withStatus`);
 * otherwise the file is as in 0.9.x: one event per trip, no status.
 *
 * The UID depends only on the first day of the trip, so importing a newer file again updates
 * the event in most calendar apps instead of adding a second one. "Now" for the stamp comes from
 * [clock].
 */
class IcsExport(
    private val clock: Clock,
    private val calculator: VacationCalculator,
) {
    /** How many events [encode] writes, for a screen that disables the export when it is zero. */
    fun tripCount(
        days: MarkedDays,
        withStatus: Boolean = true,
    ): Int = events(days, withStatus).size

    fun encode(
        days: MarkedDays,
        language: IcsLanguage,
        withStatus: Boolean = true,
    ): String {
        val stamp = STAMP_FORMAT.format(clock.instant().atOffset(ZoneOffset.UTC))
        val lines =
            mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:$PRODID", "CALSCALE:GREGORIAN")
        for (event in events(days, withStatus)) {
            lines += event(event, days.marks, language, stamp)
        }
        lines += "END:VCALENDAR"
        return lines.joinToString(separator = "") { IcsText.fold(it) + IcsText.NEWLINE }
    }

    fun writeTo(
        output: OutputStream,
        days: MarkedDays,
        language: IcsLanguage,
        withStatus: Boolean = true,
    ) {
        output.write(encode(days, language, withStatus).toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /** A trip, or the part of one whose days share a [confirmed] status; null: no status. */
    private class Event(
        val trip: Trip,
        val confirmed: Boolean?,
    )

    /**
     * The events the file can hold. The ICS date has four digits and the end is the day after the
     * trip, so a trip on 9999-12-31 cannot be written; leaving it out beats a crash in the export.
     * The backup reader never lets such days in, this is the second line of defence.
     */
    private fun events(
        days: MarkedDays,
        withStatus: Boolean,
    ): List<Event> =
        allEvents(days, withStatus).filter {
            it.trip.start.year >= 0 && it.trip.end
                .plusDays(1)
                .year <= MAX_WRITABLE_YEAR
        }

    private fun allEvents(
        days: MarkedDays,
        withStatus: Boolean,
    ): List<Event> {
        if (!withStatus) return calculator.trips(days.marks).map { Event(it, null) }
        val today = LocalDate.now(clock)
        return calculator.trips(days.marks).flatMap { trip ->
            val costly =
                days.marks
                    .filter { (date, mark) ->
                        date in trip &&
                            calculator.cost(date, mark) > VacationDays.ZERO
                    }.toSortedMap()
            val runs = mutableListOf<Event>()
            var runDays = mutableListOf<LocalDate>()
            var runConfirmed = false

            fun closeRun() {
                if (runDays.isEmpty()) return
                val cost =
                    runDays.fold(VacationDays.ZERO) { sum, day ->
                        sum +
                            calculator.cost(day, costly.getValue(day))
                    }
                runs += Event(Trip(runDays.first(), runDays.last(), cost), runConfirmed)
                runDays = mutableListOf()
            }
            for (date in costly.keys) {
                val confirmed = days.statusOn(date, today) != MarkStatus.PLANNED
                if (runDays.isNotEmpty() && confirmed != runConfirmed) closeRun()
                runConfirmed = confirmed
                runDays += date
            }
            closeRun()
            runs
        }
    }

    private fun event(
        event: Event,
        marks: Map<LocalDate, DayMark>,
        language: IcsLanguage,
        stamp: String,
    ): List<String> {
        val trip = event.trip
        val halfDays =
            marks.count { (date, mark) ->
                date in trip && calculator.cost(date, mark) == VacationDays.HALF
            }
        return buildList {
            add("BEGIN:VEVENT")
            add("UID:urlaubsplaner-${trip.start}@$UID_DOMAIN")
            add("DTSTAMP:$stamp")
            // The end of an all-day event is exclusive, so it is the day after the last day.
            add("DTSTART;VALUE=DATE:${DATE_FORMAT.format(trip.start)}")
            add("DTEND;VALUE=DATE:${DATE_FORMAT.format(trip.end.plusDays(1))}")
            event.confirmed?.let { add("STATUS:${if (it) "CONFIRMED" else "TENTATIVE"}") }
            add("SUMMARY:${IcsText.escape(summary(language))}")
            add("DESCRIPTION:${IcsText.escape(description(trip.cost, halfDays, language))}")
            add("END:VEVENT")
        }
    }

    private fun summary(language: IcsLanguage) =
        when (language) {
            IcsLanguage.GERMAN -> "Urlaub"
            IcsLanguage.ENGLISH -> "Vacation"
        }

    /** "4,5 Urlaubstage, davon 1 halber Tag" or "4.5 vacation days, 1 of them a half day". */
    private fun description(
        cost: VacationDays,
        halfDays: Int,
        language: IcsLanguage,
    ): String {
        val one = cost == VacationDays.ONE
        return when (language) {
            IcsLanguage.GERMAN -> {
                val days =
                    AmountText.format(cost, Locale.GERMANY) +
                        if (one) " Urlaubstag" else " Urlaubstage"
                when (halfDays) {
                    0 -> days
                    1 -> "$days, davon 1 halber Tag"
                    else -> "$days, davon $halfDays halbe Tage"
                }
            }

            IcsLanguage.ENGLISH -> {
                val days =
                    AmountText.format(cost, Locale.ENGLISH) +
                        if (one) " vacation day" else " vacation days"
                when (halfDays) {
                    0 -> days
                    1 -> "$days, 1 of them a half day"
                    else -> "$days, $halfDays of them half days"
                }
            }
        }
    }

    companion object {
        const val MIME_TYPE = "text/calendar"

        private const val UID_DOMAIN = "de.remph.urlaubsplaner"
        private const val PRODID = "-//de.remph.urlaubsplaner//Urlaubsplaner//EN"
        private const val MAX_WRITABLE_YEAR = 9999
        private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
        private val STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        /** The name suggested to the file picker and used for the file that is shared. */
        fun fileName(exportedOn: LocalDate) = "urlaubsplaner-$exportedOn.ics"
    }
}
