// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.settings.UserSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeParseException

/*
 * Reading never throws on a value it does not understand (a name from a newer app version, a
 * damaged row): it skips that value, because an app that crashes at start-up is worse than one
 * that forgets a setting. The enum names are stored, so renaming one needs a migration.
 */

private fun splitList(text: String) = text.split(',').filter { it.isNotEmpty() }

private inline fun <T> parseOrNull(block: () -> T): T? =
    try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: DateTimeParseException) {
        null
    }

internal fun UserSettings.toEntity() =
    SettingsEntity(
        land = land?.code,
        workingDays = schedule.workingDays.sorted().joinToString(",") { it.name },
        regionalExtras = extras.map { it.name }.sorted().joinToString(","),
        optOuts = optOuts.map { it.name }.sorted().joinToString(","),
        specialDays =
            schedule.specialDays.entries
                .sortedBy { it.key }
                .joinToString(",") { (day, value) -> "$day:${value.halves}" },
        suggestCarryOver = suggestCarryOver,
        grantedDays = grantedDays,
    )

/** No row yet means the defaults: no Land chosen, Monday to Friday, nothing else. */
internal fun SettingsEntity?.toUserSettings(): UserSettings {
    if (this == null) return UserSettings()
    return UserSettings(
        land = land?.let(Bundesland::fromCode),
        extras = enumSet<RegionalExtra>(regionalExtras),
        optOuts = enumSet<RegionalOptOut>(optOuts),
        schedule =
            WorkSchedule(
                workingDays = enumSet<DayOfWeek>(workingDays),
                specialDays = parseSpecialDays(specialDays),
            ),
        suggestCarryOver = suggestCarryOver,
        grantedDays = grantedDays,
    )
}

private inline fun <reified E : Enum<E>> enumSet(text: String): Set<E> =
    splitList(text).mapNotNull { name -> enumValues<E>().find { it.name == name } }.toSet()

private fun parseSpecialDays(text: String): Map<MonthDay, VacationDays> =
    splitList(text)
        .mapNotNull { pair ->
            parseOrNull {
                val parts = pair.split(':')
                require(parts.size == 2)
                val (day, halves) = parts
                val value = VacationDays.ofHalves(halves.toInt())
                // WorkSchedule rejects anything outside this range, which must not crash a read.
                if (value < VacationDays.ZERO ||
                    value > VacationDays.ONE
                ) {
                    null
                } else {
                    MonthDay.parse(day) to value
                }
            }
        }.toMap()

internal fun YearEntitlement.toEntity() =
    YearEntitlementEntity(
        year = year,
        halves = days.halves,
        carryOverHalves = carryOver.halves,
        carryOverExpiry = carryOverExpiry.toString(),
        carryOverExpiryYears = carryOverExpiryYears,
    )

internal fun YearEntitlementEntity.toYearEntitlement(): YearEntitlement? =
    parseOrNull {
        YearEntitlement(
            year = year,
            days = VacationDays.ofHalves(halves),
            carryOver = VacationDays.ofHalves(carryOverHalves),
            carryOverExpiry = MonthDay.parse(carryOverExpiry),
            carryOverExpiryYears = carryOverExpiryYears,
        )
    }

internal fun markedDayEntity(
    date: LocalDate,
    mark: DayMark,
    granted: Boolean = false,
) = MarkedDayEntity(date = date.toString(), mark = mark.name, granted = granted)

internal fun List<MarkedDayEntity>.toMarkedDays(): MarkedDays {
    val rows =
        mapNotNull { row ->
            parseOrNull {
                Triple(
                    LocalDate.parse(row.date),
                    DayMark.valueOf(row.mark),
                    row.granted,
                )
            }
        }
    return MarkedDays(
        marks = rows.associate { (date, mark, _) -> date to mark },
        granted = rows.filter { it.third }.map { it.first }.toSet(),
    )
}
