// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.backup

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.settings.UserSettings
import java.time.LocalDate

/**
 * Everything the backup file holds, which is everything the user has entered. Not in it: the app
 * language (the system remembers it) and the school holiday file (it ships with the app).
 */
data class BackupData(
    val settings: UserSettings = UserSettings(),
    val entitlements: List<YearEntitlement> = emptyList(),
    val marks: Map<LocalDate, DayMark> = emptyMap(),
    /** The marked days the employer has granted; a subset of the keys of [marks]. */
    val granted: Set<LocalDate> = emptySet(),
) {
    init {
        require(marks.keys.containsAll(granted)) { "A granted day must be a marked day" }
    }

    fun summary() =
        BackupSummary(
            landChosen = settings.land != null,
            years = entitlements.size,
            markedDays = marks.size,
        )

    /** True for a fresh install, where an import has nothing to replace. */
    val isEmpty: Boolean get() = this == BackupData()
}

/**
 * What a confirm dialog needs to say "this replaces 12 marked days and 2 years with 20 marked days
 * and 3 years": build one from the current data and one from the file.
 */
data class BackupSummary(
    val landChosen: Boolean,
    val years: Int,
    val markedDays: Int,
)
