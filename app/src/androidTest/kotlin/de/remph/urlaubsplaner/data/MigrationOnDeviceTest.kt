// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.holidays.Bundesland
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The migrations on the SQLite of the device or emulator itself. The JVM tests (`MigrationTest`)
 * run on Robolectric's SQLite; Android 11 and Android 17 ship different SQLite versions, and the
 * schema files from `app/schemas` are the ones the app is built with.
 */
@RunWith(AndroidJUnit4::class)
class MigrationOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, UrlaubsplanerDatabase::class.java)

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `the schema of the current version is what the code expects`() {
        helper.createDatabase(TEST_DB, UrlaubsplanerDatabase.VERSION).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            UrlaubsplanerDatabase.VERSION,
            true,
            *UrlaubsplanerDatabase.MIGRATIONS.toTypedArray(),
        )
    }

    @Test
    fun `a database from version 1 migrates through every step and keeps its rows`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO marked_day (date, mark) VALUES ('2026-08-14', 'HALF')")
            execSQL(
                "INSERT INTO settings " +
                    "(id, land, workingDays, regionalExtras, optOuts, specialDays) " +
                    "VALUES (1, 'BY', 'MONDAY,TUESDAY', '', '', '')",
            )
            execSQL(
                "INSERT INTO year_entitlement (year, halves, carryOverHalves, carryOverExpiry) " +
                    "VALUES (2026, 60, 5, '--03-31')",
            )
            close()
        }

        // The real builder, on a file: what the app does at the first start after an update.
        val database =
            Room
                .databaseBuilder(context, UrlaubsplanerDatabase::class.java, TEST_DB)
                .addMigrations(*UrlaubsplanerDatabase.MIGRATIONS.toTypedArray())
                .build()
        try {
            val repository = UserDataRepository(database)
            runBlocking {
                assertEquals(
                    mapOf(LocalDate.of(2026, 8, 14) to DayMark.HALF),
                    repository
                        .observeMarks(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                        .first(),
                )
                assertEquals(Bundesland.BAYERN, repository.observeSettings().first().land)
                val entitlement = repository.observeEntitlement(2026).first()
                assertEquals(VacationDays.of(30), entitlement?.days)
                assertEquals(VacationDays.of(2.5), entitlement?.carryOver)
                assertEquals(0, entitlement?.carryOverExpiryYears)
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-on-device-test"
    }
}
