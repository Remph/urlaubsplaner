// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities =
        [
            SettingsEntity::class,
            YearEntitlementEntity::class,
            MarkedDayEntity::class,
        ],
    version = UrlaubsplanerDatabase.VERSION,
    exportSchema = true,
)
abstract class UrlaubsplanerDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    abstract fun entitlementDao(): EntitlementDao

    abstract fun markedDayDao(): MarkedDayDao

    companion object {
        /** Version 2: the setting that suggests the unused days as next year's Resturlaub. */
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE settings ADD COLUMN suggestCarryOver INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        /** Version 3: Resturlaub that expires in a later year than the entitlement's. */
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE year_entitlement " +
                            "ADD COLUMN carryOverExpiryYears INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        /**
         * Version 4: the employer has granted a marked day (shown apart from a planned one), and
         * the setting that turns this on.
         */
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE marked_day ADD COLUMN granted INTEGER NOT NULL DEFAULT 0",
                    )
                    connection.execSQL(
                        "ALTER TABLE settings ADD COLUMN grantedDays INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        const val VERSION = 4
        const val FILE_NAME = "urlaubsplaner.db"

        /**
         * One entry per version step, in order. Add the new `Migration` here whenever [VERSION]
         * goes up; a test fails if one is missing. There is deliberately no fallback to
         * destructive migration: this is the user's vacation data.
         */
        val MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun create(context: Context): UrlaubsplanerDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    UrlaubsplanerDatabase::class.java,
                    FILE_NAME,
                ).addMigrations(*MIGRATIONS.toTypedArray())
                .build()
    }
}
