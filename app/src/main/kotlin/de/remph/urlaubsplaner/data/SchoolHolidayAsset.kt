// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.Context
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHolidayData

/**
 * Reads the bundled school holiday file. The file is `data/school-holidays/school-holidays.txt`
 * in the repo, added to the app's assets in `app/build.gradle.kts`; the parsing is in `:core`.
 *
 * It reads and parses on every call (a few hundred lines), so whoever shows school holidays
 * loads it once and keeps the result. A damaged file throws; the unit tests make sure the
 * released one is not.
 */
object SchoolHolidayAsset {
    const val FILE_NAME = "school-holidays.txt"

    fun load(context: Context): SchoolHolidayData =
        context.assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).use {
            SchoolHolidayData.parse(it.readText())
        }
}
