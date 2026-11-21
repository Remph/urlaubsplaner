// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.backup.BackupException
import de.remph.urlaubsplaner.core.backup.BackupFile
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.settings.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.MonthDay

/** The backup against a real SQLite database and real streams, on the JVM through Robolectric. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private lateinit var context: Context
    private lateinit var database: UrlaubsplanerDatabase
    private lateinit var repository: UserDataRepository
    private lateinit var file: File
    private val exportedOn = LocalDate.of(2026, 10, 25)

    private val data =
        BackupData(
            settings =
                UserSettings(
                    land = Bundesland.BAYERN,
                    optOuts = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN),
                    schedule =
                        WorkSchedule(
                            specialDays =
                                mapOf(WorkSchedule.SILVESTER to VacationDays.HALF),
                        ),
                ),
            entitlements =
                listOf(
                    YearEntitlement(
                        2026,
                        VacationDays.of(30),
                        VacationDays.of(2.5),
                        MonthDay.of(4, 30),
                    ),
                    YearEntitlement(2027, VacationDays.of(28)),
                ),
            marks =
                mapOf(
                    LocalDate.of(2026, 7, 1) to DayMark.FULL,
                    LocalDate.of(2026, 7, 2) to DayMark.HALF,
                ),
            granted = setOf(LocalDate.of(2026, 7, 2)),
        )

    @Before
    fun open() {
        TestDocuments.register()
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, UrlaubsplanerDatabase::class.java).build()
        repository = UserDataRepository(database)
        file = File.createTempFile("backup", ".json")
    }

    @After
    fun close() {
        database.close()
        file.delete()
    }

    private val store get() = BackupFileStore(context.contentResolver, context.packageName)
    private val uri get() = TestDocuments.uriFor(file)

    @Test
    fun `a new database reads as empty`() =
        runBlocking {
            assertEquals(BackupData(), repository.readAll())
        }

    @Test
    fun `what was replaced in reads back the same`() =
        runBlocking {
            repository.replaceAll(data)

            assertEquals(data, repository.readAll())
        }

    @Test
    fun `replacing removes what was there before`() =
        runBlocking {
            repository.replaceAll(data)
            repository.mark(LocalDate.of(2026, 12, 28), DayMark.FULL)
            repository.saveEntitlement(YearEntitlement(2030, VacationDays.of(25)))
            val other =
                BackupData(
                    settings = UserSettings(land = Bundesland.HAMBURG),
                    marks = mapOf(LocalDate.of(2026, 8, 3) to DayMark.HALF),
                )

            repository.replaceAll(other)

            assertEquals(other, repository.readAll())
        }

    @Test
    fun `replacing with an empty backup empties the app`() =
        runBlocking {
            repository.replaceAll(data)

            repository.replaceAll(BackupData())

            assertEquals(BackupData(), repository.readAll())
        }

    @Test
    fun `a replace that fails halfway leaves the old data as it was`() =
        runBlocking {
            repository.replaceAll(data)
            // Fails on the first marked day, after settings and years were already replaced and the
            // old marks deleted: only a rolled-back transaction brings all of that back.
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_mark BEFORE INSERT ON marked_day " +
                    "BEGIN SELECT RAISE(ABORT, 'test failure'); END",
            )
            val other =
                BackupData(
                    settings = UserSettings(land = Bundesland.HAMBURG),
                    entitlements = listOf(YearEntitlement(2031, VacationDays.of(25))),
                    marks = mapOf(LocalDate.of(2026, 8, 3) to DayMark.HALF),
                )

            assertThrows(Exception::class.java) { runBlocking { repository.replaceAll(other) } }
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_mark")

            assertEquals(data, repository.readAll())
        }

    @Test
    fun `export to a file and import it into a fresh database gives the same data`() =
        runBlocking {
            repository.replaceAll(data)
            store.write(uri, repository.readAll(), exportedOn)

            val fresh =
                Room
                    .inMemoryDatabaseBuilder(
                        context,
                        UrlaubsplanerDatabase::class.java,
                    ).build()
            try {
                val target = UserDataRepository(fresh)
                target.replaceAll(store.read(uri))

                assertEquals(data, target.readAll())
            } finally {
                fresh.close()
            }
        }

    @Test
    fun `a shorter export over a longer file leaves no tail`() =
        runBlocking {
            file.writeText("x".repeat(100_000))

            store.write(uri, BackupData(), exportedOn)

            assertEquals(BackupData(), store.read(uri))
            assertEquals(BackupFile.encode(BackupData(), exportedOn), file.readText())
        }

    @Test
    fun `a file that is not a backup is refused and the data stays`() =
        runBlocking {
            repository.replaceAll(data)
            file.writeText("Urlaub 2026")

            assertThrows(BackupException.NotABackup::class.java) { runBlocking { store.read(uri) } }

            assertEquals(data, repository.readAll())
        }

    @Test
    fun `a backup from a newer version is refused and the data stays`() =
        runBlocking {
            repository.replaceAll(data)
            file.writeText("""{"app": "urlaubsplaner", "formatVersion": 3}""")

            val e =
                assertThrows(
                    BackupException.NewerVersion::class.java,
                ) { runBlocking { store.read(uri) } }

            assertEquals(3, e.found)
            assertEquals(data, repository.readAll())
        }

    @Test
    fun `a missing file is an IO error, not a backup error`() {
        file.delete()

        assertThrows(java.io.IOException::class.java) { runBlocking { store.read(uri) } }
    }

    // The picker gives content:// addresses. ContentResolver also opens file:// ones, and a file
    // manager that answers "create a file" with the app's own database path would make the export
    // overwrite it.

    @Test
    fun `a file address is refused for writing and the file is untouched`() {
        file.writeText("keep me")

        assertThrows(java.io.FileNotFoundException::class.java) {
            runBlocking { store.write(Uri.fromFile(file), data, exportedOn) }
        }

        assertEquals("keep me", file.readText())
    }

    @Test
    fun `a file address is refused for reading`() {
        runBlocking { store.write(uri, data, exportedOn) }

        assertThrows(java.io.FileNotFoundException::class.java) {
            runBlocking { store.read(Uri.fromFile(file)) }
        }
    }

    @Test
    fun `the address of our own file provider is refused for writing and reading`() {
        val own =
            Uri
                .Builder()
                .scheme("content")
                .authority(context.packageName + CalendarFileStore.AUTHORITY_SUFFIX)
                .appendPath("calendar")
                .appendPath("urlaubsplaner.ics")
                .build()

        assertThrows(java.io.FileNotFoundException::class.java) {
            runBlocking { store.write(own, data, exportedOn) }
        }
        assertThrows(java.io.FileNotFoundException::class.java) {
            runBlocking { store.read(own) }
        }
    }

    @Test
    fun `an address with no scheme is refused`() {
        assertThrows(java.io.FileNotFoundException::class.java) {
            runBlocking { store.read(Uri.parse("/data/data/some/file.json")) }
        }
    }
}
