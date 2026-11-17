// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Opens the committed schema of each version and checks that migrating to the current one keeps
 * the data. When [UrlaubsplanerDatabase.VERSION] goes up, add a test that starts at the old
 * version, fills it with rows, runs the migration and reads the rows back.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            UrlaubsplanerDatabase::class.java,
            emptyList(),
            AbsoluteNameOpenHelperFactory(),
        )

    @Test
    fun `there is one migration for every version step`() {
        assertEquals(UrlaubsplanerDatabase.VERSION - 1, UrlaubsplanerDatabase.MIGRATIONS.size)
        UrlaubsplanerDatabase.MIGRATIONS.forEachIndexed { index, migration ->
            assertEquals(index + 1, migration.startVersion)
            assertEquals(index + 2, migration.endVersion)
        }
    }

    @Test
    fun `the exported schema of the current version matches the code`() {
        helper.createDatabase(TEST_DB, UrlaubsplanerDatabase.VERSION).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            UrlaubsplanerDatabase.VERSION,
            true,
            *UrlaubsplanerDatabase.MIGRATIONS.toTypedArray(),
        )
    }

    @Test
    fun `a database from version 1 opens with the real builder and keeps its rows`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO marked_day (date, mark) VALUES ('2026-08-14', 'HALF')")
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database =
            Room
                .databaseBuilder(context, UrlaubsplanerDatabase::class.java, TEST_DB)
                .addMigrations(*UrlaubsplanerDatabase.MIGRATIONS.toTypedArray())
                .build()
        try {
            val marks =
                runBlocking {
                    UserDataRepository(database)
                        .observeMarkedDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                        .first()
                }
            assertEquals(mapOf(LocalDate.of(2026, 8, 14) to DayMark.HALF), marks.marks)
            assertTrue(marks.granted.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 1 settings migrate with the carry-over setting off`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO settings " +
                    "(id, land, workingDays, regionalExtras, optOuts, specialDays) " +
                    "VALUES (1, 'BY', 'MONDAY,TUESDAY', 'AUGSBURGER_FRIEDENSFEST', " +
                    "'MARIAE_HIMMELFAHRT_BAYERN', '--12-24:1')",
            )
            execSQL(
                "INSERT INTO year_entitlement (year, halves, carryOverHalves, carryOverExpiry) " +
                    "VALUES (2026, 60, 5, '--03-31')",
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database =
            Room
                .databaseBuilder(context, UrlaubsplanerDatabase::class.java, TEST_DB)
                .addMigrations(*UrlaubsplanerDatabase.MIGRATIONS.toTypedArray())
                .build()
        try {
            val repository = UserDataRepository(database)
            val settings = runBlocking { repository.observeSettings().first() }
            assertEquals(Bundesland.BAYERN, settings.land)
            assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), settings.schedule.workingDays)
            assertEquals(setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST), settings.extras)
            assertEquals(setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN), settings.optOuts)
            assertEquals(
                mapOf(WorkSchedule.HEILIGABEND to VacationDays.HALF),
                settings.schedule.specialDays,
            )
            assertFalse(settings.suggestCarryOver)

            val entitlement = runBlocking { repository.observeEntitlement(2026).first() }
            assertEquals(VacationDays.of(30), entitlement?.days)
            assertEquals(VacationDays.of(2.5), entitlement?.carryOver)

            runBlocking { repository.saveSettings(settings.copy(suggestCarryOver = true)) }
            assertTrue(runBlocking { repository.observeSettings().first() }.suggestCarryOver)
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 2 entitlements migrate with the expiry in their own year`() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO year_entitlement (year, halves, carryOverHalves, carryOverExpiry) " +
                    "VALUES (2026, 60, 5, '--03-31')",
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database =
            Room
                .databaseBuilder(context, UrlaubsplanerDatabase::class.java, TEST_DB)
                .addMigrations(*UrlaubsplanerDatabase.MIGRATIONS.toTypedArray())
                .build()
        try {
            val repository = UserDataRepository(database)
            val entitlement = runBlocking { repository.observeEntitlement(2026).first() }
            assertEquals(0, entitlement?.carryOverExpiryYears)
            assertEquals(LocalDate.of(2026, 3, 31), entitlement?.carryOverExpiryDate)

            runBlocking { repository.saveEntitlement(entitlement!!.copy(carryOverExpiryYears = 1)) }
            val later = runBlocking { repository.observeEntitlement(2026).first() }
            assertEquals(1, later?.carryOverExpiryYears)
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 3 marks migrate as planned and the granted days setting as off`() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO marked_day (date, mark) VALUES ('2026-08-14', 'HALF')")
            execSQL("INSERT INTO marked_day (date, mark) VALUES ('2026-08-17', 'FULL')")
            execSQL(
                "INSERT INTO settings " +
                    "(id, land, workingDays, regionalExtras, optOuts, specialDays, " +
                    "suggestCarryOver) VALUES (1, 'BY', 'MONDAY', '', '', '', 1)",
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database =
            Room
                .databaseBuilder(context, UrlaubsplanerDatabase::class.java, TEST_DB)
                .addMigrations(*UrlaubsplanerDatabase.MIGRATIONS.toTypedArray())
                .build()
        try {
            val repository = UserDataRepository(database)
            val days =
                runBlocking {
                    repository
                        .observeMarkedDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                        .first()
                }
            assertEquals(
                mapOf(
                    LocalDate.of(2026, 8, 14) to DayMark.HALF,
                    LocalDate.of(2026, 8, 17) to DayMark.FULL,
                ),
                days.marks,
            )
            assertTrue(days.granted.isEmpty())

            val settings = runBlocking { repository.observeSettings().first() }
            assertFalse(settings.grantedDays)
            assertTrue(settings.suggestCarryOver)

            runBlocking {
                repository.setGranted(listOf(LocalDate.of(2026, 8, 14)), true)
                repository.saveSettings(settings.copy(grantedDays = true))
            }
            assertEquals(
                setOf(LocalDate.of(2026, 8, 14)),
                runBlocking { repository.readAll() }.granted,
            )
            assertTrue(runBlocking { repository.observeSettings().first() }.grantedDays)
        } finally {
            database.close()
        }
    }

    /**
     * [MigrationTestHelper] opens the database by its absolute path, while androidx.sqlite 2.6.2
     * (`SupportSQLiteDriver`) compares that path with the helper's name only after the last '/'.
     * That never matches a Windows path, so the helper reports the absolute path as its name.
     */
    private class AbsoluteNameOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
        private val framework = FrameworkSQLiteOpenHelperFactory()

        override fun create(
            configuration: SupportSQLiteOpenHelper.Configuration,
        ): SupportSQLiteOpenHelper {
            val helper = framework.create(configuration)
            val name = configuration.name
            val path = name?.let { configuration.context.getDatabasePath(it).absolutePath }
            return object : SupportSQLiteOpenHelper by helper {
                override val databaseName: String? = path
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
