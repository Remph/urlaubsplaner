// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.schoolholidays

import de.remph.urlaubsplaner.core.holidays.Bundesland
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Thrown when the school holiday file is damaged; the message names the line. */
class SchoolHolidayFormatException(
    message: String,
) : IllegalArgumentException(message)

/**
 * The bundled school holiday file (`data/school-holidays/school-holidays.txt`), parsed.
 *
 * School holidays are not computed: the Länder set them, mostly a few years ahead. The file lists
 * what is known and says which dates it [covers]. Outside that range the answer is "unknown",
 * not "no holidays", so callers check [covers] before they show an empty result.
 *
 * The file is ours, not user data, so a damaged line fails loudly; a test reads the real file.
 */
class SchoolHolidayData private constructor(
    /** The day the file was exported from OpenHolidays. */
    val version: LocalDate,
    /** First and last day the file has data for. */
    val coverage: ClosedRange<LocalDate>,
    private val byLand: Map<Bundesland, List<SchoolHoliday>>,
) {
    fun covers(date: LocalDate): Boolean = date in coverage

    /** Whether the whole range from [start] to [end] (inclusive) lies inside the [coverage]. */
    fun covers(
        start: LocalDate,
        end: LocalDate,
    ): Boolean = start in coverage && end in coverage

    /** The school holidays of [land] that include [date], in file order (earliest start first). */
    fun on(
        land: Bundesland,
        date: LocalDate,
    ): List<SchoolHoliday> = holidaysOf(land).filter { date in it }

    /** The school holidays of [land] that overlap the days from [start] to [end] (inclusive). */
    fun between(
        land: Bundesland,
        start: LocalDate,
        end: LocalDate,
    ): List<SchoolHoliday> {
        require(!end.isBefore(start)) { "Range ends before it starts: $start..$end" }
        return holidaysOf(land).filter { it.overlaps(start, end) }
    }

    private fun holidaysOf(land: Bundesland): List<SchoolHoliday> = byLand[land].orEmpty()

    companion object {
        /** Format version this parser reads; a different one is refused. */
        const val FORMAT = 1

        /**
         * The credit the ODbL asks for. Show it in the about screen and in an ICS export that
         * contains school holidays (docs/data-sources.md).
         */
        const val CREDIT =
            "Contains information from OpenHolidays API (https://www.openholidaysapi.org), " +
                "which is made available here under the Open Database License (ODbL)."

        private val metaLine = Regex("""(format|version|covers):\s*(.+)""")

        fun parse(text: String): SchoolHolidayData {
            val meta = mutableMapOf<String, String>()
            val holidays = mutableListOf<SchoolHoliday>()
            // A Windows editor may save the file with a byte order mark; it would break line 1.
            text.removePrefix("﻿").lines().forEachIndexed { index, raw ->
                val line = raw.trim()
                val number = index + 1
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val metaMatch = metaLine.matchEntire(line)
                if (metaMatch != null) {
                    val key = metaMatch.groupValues[1]
                    if (key in meta) fail(number, "'$key' appears twice")
                    meta[key] = metaMatch.groupValues[2].trim()
                } else {
                    holidays += parseHolidayLine(number, line)
                }
            }
            if (meta["format"] != FORMAT.toString()) {
                throw SchoolHolidayFormatException(
                    "Unsupported or missing format: ${meta["format"]}",
                )
            }
            val version = parseDate("header", meta["version"] ?: throw missing("version"))
            val coverage = parseCoverage(meta["covers"] ?: throw missing("covers"))
            val sorted = holidays.sortedWith(compareBy({ it.from }, { it.to }, { it.nameDe }))
            return SchoolHolidayData(version, coverage, sorted.groupBy { it.land })
        }

        private fun parseHolidayLine(
            number: Int,
            line: String,
        ): SchoolHoliday {
            val parts = line.split(';')
            if (parts.size != 5) {
                fail(number, "expected 5 fields separated by ';', got ${parts.size}")
            }
            val land = Bundesland.fromCode(parts[0]) ?: fail(number, "unknown Land '${parts[0]}'")
            val from = parseDate("line $number", parts[1])
            val to = parseDate("line $number", parts[2])
            if (to.isBefore(from)) fail(number, "ends before it starts")
            if (parts[3].isBlank() || parts[4].isBlank()) fail(number, "empty name")
            return SchoolHoliday(land, from, to, parts[3], parts[4])
        }

        private fun parseCoverage(value: String): ClosedRange<LocalDate> {
            val ends = value.split("..")
            if (ends.size != 2) {
                throw SchoolHolidayFormatException("Bad 'covers' (want from..to): $value")
            }
            val from = parseDate("header", ends[0])
            val to = parseDate("header", ends[1])
            if (to.isBefore(
                    from,
                )
            ) {
                throw SchoolHolidayFormatException("'covers' ends before it starts: $value")
            }
            return from..to
        }

        private fun parseDate(
            where: String,
            value: String,
        ): LocalDate =
            try {
                LocalDate.parse(value.trim())
            } catch (e: DateTimeParseException) {
                throw SchoolHolidayFormatException("School holiday file, $where: bad date '$value'")
            }

        private fun missing(key: String) = SchoolHolidayFormatException("Missing '$key' line")

        private fun fail(
            number: Int,
            reason: String,
        ): Nothing =
            throw SchoolHolidayFormatException("School holiday file, line $number: $reason")
    }
}
