// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.net.Uri
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import de.remph.urlaubsplaner.core.ics.IcsExport
import de.remph.urlaubsplaner.core.ics.IcsLanguage
import de.remph.urlaubsplaner.data.CalendarFileStore
import de.remph.urlaubsplaner.data.UserDataRepository
import java.time.Clock
import java.time.LocalDate

/**
 * Puts what is stored through [IcsExport] and out through [CalendarFileStore]. Everything is read
 * fresh for each call, so the file always matches what the screens show.
 */
class CalendarExporter(
    private val repository: UserDataRepository,
    private val store: CalendarFileStore,
    private val clock: Clock,
) {
    fun suggestedFileName(): String = IcsExport.fileName(LocalDate.now(clock))

    /** How many events the file would hold; 0 also when no Bundesland is chosen yet. */
    suspend fun tripCount(): Int =
        prepared()?.let { it.export.tripCount(it.days, it.withStatus) } ?: 0

    suspend fun save(
        uri: Uri,
        language: IcsLanguage,
    ) {
        val prepared = prepared() ?: return
        store.write(uri, prepared.encode(language))
    }

    /** The address of a freshly written file for a calendar app, or null when there is nothing to open. */
    suspend fun openUri(language: IcsLanguage): Uri? {
        val prepared = prepared() ?: return null
        if (prepared.export.tripCount(prepared.days, prepared.withStatus) == 0) return null
        return store.writeForOpening(suggestedFileName(), prepared.encode(language))
    }

    /** Without a Land there are no public holidays, so what a marked day costs is not known. */
    private suspend fun prepared(): Prepared? {
        val data = repository.readAll()
        val settings = data.settings
        val check = settings.publicHolidayCheck() ?: return null
        val calculator = VacationCalculator(clock, settings.schedule, check)
        // The holiday rules start in 2019; an older mark (from a hand-made backup) cannot be priced.
        val usable = data.marks.filterKeys { it.year >= PublicHolidays.FIRST_SUPPORTED_YEAR }
        val days = MarkedDays(usable, data.granted.filter { it in usable }.toSet())
        return Prepared(IcsExport(clock, calculator), days, withStatus = settings.grantedDays)
    }

    /** Confirmed and tentative events only while the granted days setting is on. */
    private class Prepared(
        val export: IcsExport,
        val days: MarkedDays,
        val withStatus: Boolean,
    ) {
        fun encode(language: IcsLanguage) = export.encode(days, language, withStatus)
    }
}
