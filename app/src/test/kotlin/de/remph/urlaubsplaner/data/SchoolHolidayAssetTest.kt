// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.holidays.Bundesland
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The file the app really ships is in the assets and the loader can read it. */
@RunWith(AndroidJUnit4::class)
class SchoolHolidayAssetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the bundled file loads and has Bayern's summer holidays`() {
        val data = SchoolHolidayAsset.load(context)
        assertEquals(LocalDate.of(2026, 1, 1)..LocalDate.of(2029, 12, 31), data.coverage)
        val summer = data.on(Bundesland.BAYERN, LocalDate.of(2026, 8, 10)).single()
        assertEquals("Sommerferien", summer.nameDe)
        assertEquals(LocalDate.of(2026, 9, 14), summer.to)
    }

    @Test
    fun `the licence text and notice travel with the file`() {
        // ODbL 4.2: keep the licence and notices with the database in what is distributed.
        for (name in listOf("ODbL-1.0.md", "README.md")) {
            assertNotNull(name, context.assets.open(name).use { it.read() })
        }
    }
}
