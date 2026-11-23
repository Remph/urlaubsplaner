// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.planner

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.balance.VacationDays
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** The overview warning says "1 Tag ... verfällt", not "1 Tage ... verfallen" (UI audit 01). */
@RunWith(AndroidJUnit4::class)
class CarryOverWarningTest {
    private fun warning(
        language: String,
        days: VacationDays,
    ): String {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        val resources = base.createConfigurationContext(configuration).resources
        return resources.getQuantityString(
            R.plurals.carry_over_warning,
            days.pluralQuantity(),
            days.text(Locale.forLanguageTag(language)),
            "31.03.2027",
        )
    }

    @Test
    fun `one day is singular in German`() {
        assertEquals(
            "1 Tag Resturlaub verfällt am 31.03.2027. Plane ihn noch ein.",
            warning("de", VacationDays.ONE),
        )
    }

    @Test
    fun `half a day and two days are plural in German`() {
        assertEquals(
            "0,5 Tage Resturlaub verfallen am 31.03.2027. Plane sie noch ein.",
            warning("de", VacationDays.HALF),
        )
        assertEquals(
            "2 Tage Resturlaub verfallen am 31.03.2027. Plane sie noch ein.",
            warning("de", VacationDays.of(2)),
        )
        assertEquals(
            "1,5 Tage Resturlaub verfallen am 31.03.2027. Plane sie noch ein.",
            warning("de", VacationDays.ofHalves(3)),
        )
    }

    @Test
    fun `English follows the same rule`() {
        assertEquals(
            "1 carried-over day expires on 31.03.2027. Plan it in soon.",
            warning("en", VacationDays.ONE),
        )
        assertEquals(
            "0.5 carried-over days expire on 31.03.2027. Plan them in soon.",
            warning("en", VacationDays.HALF),
        )
        assertEquals(
            "2 carried-over days expire on 31.03.2027. Plan them in soon.",
            warning("en", VacationDays.of(2)),
        )
    }
}
