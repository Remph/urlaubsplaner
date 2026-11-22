// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.net.Uri
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.ics.IcsExport
import de.remph.urlaubsplaner.core.ics.IcsLanguage
import de.remph.urlaubsplaner.data.CalendarFileStore
import de.remph.urlaubsplaner.data.UserDataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/**
 * Puts what is stored through [IcsExport] and out through [CalendarFileStore]. Everything is read
 * fresh for each call, so the file always matches what the screens show. The text is made on
 * [compute] (a thread of its own, not the main thread: a backup with tens of thousands of trips
 * takes a while even in linear time); tests pass their own dispatcher.
 */
class CalendarExporter(
    private val repository: UserDataRepository,
    private val store: CalendarFileStore,
    private val clock: Clock,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    fun suggestedFileName(): String = IcsExport.fileName(LocalDate.now(clock))

    /** How many events the file would hold; 0 also when no Bundesland is chosen yet. */
    suspend fun tripCount(): Int =
        withContext(compute) {
            prepared()?.let { it.export.tripCount(it.days, it.withStatus) } ?: 0
        }

    suspend fun save(
        uri: Uri,
        language: IcsLanguage,
    ) {
        val text = withContext(compute) { prepared()?.encode(language) } ?: return
        store.write(uri, text)
    }

    /** The address of a freshly written file for a calendar app, or null when there is nothing to open. */
    suspend fun openUri(language: IcsLanguage): Uri? {
        val text =
            withContext(compute) {
                val prepared = prepared() ?: return@withContext null
                if (prepared.export.tripCount(prepared.days, prepared.withStatus) ==
                    0
                ) {
                    return@withContext null
                }
                prepared.encode(language)
            } ?: return null
        return store.writeForOpening(suggestedFileName(), text)
    }

    /** Without a Land there are no public holidays, so what a marked day costs is not known. */
    private suspend fun prepared(): Prepared? {
        val data = repository.readAll()
        val settings = data.settings
        val check = settings.publicHolidayCheck() ?: return null
        val calculator = VacationCalculator(clock, settings.schedule, check)
        val days = MarkedDays(data.marks, data.granted.filter { it in data.marks }.toSet())
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
