// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.settings.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Runs Room on the JVM through Robolectric, with a real SQLite database in memory. */
@RunWith(AndroidJUnit4::class)
class UserDataRepositoryTest {
    private lateinit var database: UrlaubsplanerDatabase
    private lateinit var repository: UserDataRepository
    private val year2026 = LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UrlaubsplanerDatabase::class.java).build()
        repository = UserDataRepository(database)
    }

    @After
    fun close() = database.close()

    private suspend fun marks() =
        repository.observeMarks(year2026.start, year2026.endInclusive).first()

    @Test
    fun `settings are the defaults until saved`() =
        runBlocking {
            assertEquals(UserSettings(), repository.observeSettings().first())
        }

    @Test
    fun `saved settings come back, and saving again replaces them`() =
        runBlocking {
            val first =
                UserSettings(
                    land = Bundesland.BAYERN,
                    optOuts = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN),
                    schedule =
                        WorkSchedule(
                            specialDays = mapOf(WorkSchedule.SILVESTER to VacationDays.HALF),
                        ),
                )
            repository.saveSettings(first)
            assertEquals(first, repository.observeSettings().first())

            val second = UserSettings(land = Bundesland.HAMBURG)
            repository.saveSettings(second)
            assertEquals(second, repository.observeSettings().first())
        }

    @Test
    fun `an entitlement is per year and can be replaced and deleted`() =
        runBlocking {
            assertNull(repository.observeEntitlement(2026).first())

            val entitlement =
                YearEntitlement(2026, VacationDays.of(30), carryOver = VacationDays.of(3))
            repository.saveEntitlement(entitlement)
            repository.saveEntitlement(YearEntitlement(2027, VacationDays.of(28)))
            assertEquals(entitlement, repository.observeEntitlement(2026).first())

            val changed = entitlement.copy(days = VacationDays.of(29.5))
            repository.saveEntitlement(changed)
            assertEquals(changed, repository.observeEntitlement(2026).first())

            repository.deleteEntitlement(2026)
            assertNull(repository.observeEntitlement(2026).first())
            assertEquals(VacationDays.of(28), repository.observeEntitlement(2027).first()?.days)
        }

    @Test
    fun `marks come back for the asked range only, ends included`() =
        runBlocking {
            val dates =
                listOf(
                    LocalDate.of(2025, 12, 31),
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31),
                    LocalDate.of(2027, 1, 1),
                )
            dates.forEach { repository.mark(it, DayMark.FULL) }

            assertEquals(setOf(dates[1], dates[2]), marks().keys)
        }

    @Test
    fun `marking again changes full to half, unmarking removes it`() =
        runBlocking {
            val day = LocalDate.of(2026, 8, 14)
            repository.mark(day, DayMark.FULL)
            repository.mark(day, DayMark.HALF)
            assertEquals(mapOf(day to DayMark.HALF), marks())

            repository.unmark(day)
            repository.unmark(day)
            assertEquals(emptyMap<LocalDate, DayMark>(), marks())
        }

    @Test
    fun `clearing a year removes its marks, ends included, and nothing else`() =
        runBlocking {
            val kept =
                listOf(
                    LocalDate.of(2025, 12, 31),
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 6, 1),
                )
            kept.forEach { repository.mark(it, DayMark.FULL) }
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 7), LocalDate.of(2026, 12, 31))
                .forEach { repository.mark(it, DayMark.HALF) }
            repository.saveEntitlement(YearEntitlement(2026, VacationDays.of(30)))
            repository.saveSettings(UserSettings(land = Bundesland.BAYERN))

            repository.clearMarks(2026)

            assertEquals(emptyMap<LocalDate, DayMark>(), marks())
            val rest =
                repository
                    .observeMarks(LocalDate.of(2025, 1, 1), LocalDate.of(2027, 12, 31))
                    .first()
            assertEquals(kept.toSet(), rest.keys)
            assertEquals(VacationDays.of(30), repository.observeEntitlement(2026).first()?.days)
            assertEquals(Bundesland.BAYERN, repository.observeSettings().first().land)
        }

    @Test
    fun `clearing a year with no marks is harmless`() =
        runBlocking {
            repository.mark(LocalDate.of(2027, 3, 3), DayMark.FULL)

            repository.clearMarks(2026)

            assertEquals(1, repository.readAll().marks.size)
        }

    @Test
    fun `clearing a year is seen by whoever watches the table, like the widget`() =
        runBlocking {
            repository.mark(LocalDate.of(2026, 5, 5), DayMark.FULL)
            // The widget refresher listens to the table through the invalidation tracker.
            val seen = Channel<Set<String>>(Channel.UNLIMITED)
            val watcher =
                launch(Dispatchers.Default) {
                    database.invalidationTracker.createFlow("marked_day").collect { seen.send(it) }
                }
            withTimeout(5_000) { seen.receive() }

            repository.clearMarks(2026)

            assertEquals(setOf("marked_day"), withTimeout(5_000) { seen.receive() })
            watcher.cancel()
        }

    private suspend fun granted() =
        repository.observeMarkedDays(year2026.start, year2026.endInclusive).first().granted

    @Test
    fun `a new mark is not granted and a day can be granted and un-granted`() =
        runBlocking {
            val day = LocalDate.of(2026, 7, 6)
            repository.mark(day, DayMark.FULL)
            assertEquals(emptySet<LocalDate>(), granted())

            repository.setGranted(listOf(day), true)
            assertEquals(setOf(day), granted())

            repository.setGranted(listOf(day), false)
            assertEquals(emptySet<LocalDate>(), granted())
        }

    @Test
    fun `granting a day that is not marked marks nothing`() =
        runBlocking {
            repository.setGranted(listOf(LocalDate.of(2026, 7, 6)), true)

            assertEquals(BackupData().marks, repository.readAll().marks)
            assertEquals(emptySet<LocalDate>(), granted())
        }

    @Test
    fun `marking again replaces the flag, unless the caller keeps it`() =
        runBlocking {
            val monday = LocalDate.of(2026, 7, 6)
            val tuesday = LocalDate.of(2026, 7, 7)
            repository.markAll(listOf(monday, tuesday), DayMark.FULL, granted = true)

            repository.markAllKeepingGranted(listOf(monday, LocalDate.of(2026, 7, 8)), DayMark.HALF)

            // Monday keeps its flag with the new mark, a new day starts as planned.
            assertEquals(setOf(monday, tuesday), granted())
            assertEquals(DayMark.HALF, marks().getValue(monday))
            assertEquals(DayMark.FULL, marks().getValue(tuesday))

            repository.markAll(listOf(monday), DayMark.FULL, granted = false)
            assertEquals(setOf(tuesday), granted())
        }

    @Test
    fun `removing a marked day removes its flag with it`() =
        runBlocking {
            val day = LocalDate.of(2026, 7, 6)
            repository.mark(day, DayMark.FULL, granted = true)

            repository.unmark(day)
            repository.mark(day, DayMark.FULL)

            assertEquals(emptySet<LocalDate>(), granted())
        }

    @Test
    fun `the granted days setting is saved with the other settings`() =
        runBlocking {
            repository.saveSettings(UserSettings(land = Bundesland.HAMBURG, grantedDays = true))

            assertEquals(true, repository.observeSettings().first().grantedDays)
        }

    @Test
    fun `removing days returns what they were and restoring puts exactly that back`() =
        runBlocking {
            val monday = LocalDate.of(2026, 7, 6)
            val tuesday = LocalDate.of(2026, 7, 7)
            val wednesday = LocalDate.of(2026, 7, 8)
            val other = LocalDate.of(2026, 7, 20)
            repository.mark(monday, DayMark.FULL, granted = true)
            repository.mark(tuesday, DayMark.HALF)
            repository.mark(other, DayMark.FULL, granted = true)
            val before = repository.readAll()

            // Wednesday is not marked: it is not in what was removed and not marked by the undo.
            val removed = repository.removeAll(listOf(monday, tuesday, wednesday))

            assertEquals(mapOf(monday to DayMark.FULL, tuesday to DayMark.HALF), removed.marks)
            assertEquals(setOf(monday), removed.granted)
            assertEquals(mapOf(other to DayMark.FULL), marks())

            repository.restore(removed)

            assertEquals(before, repository.readAll())
        }
}
