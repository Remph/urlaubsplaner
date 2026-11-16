// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.holidays

import de.remph.urlaubsplaner.core.holidays.Bundesland.BADEN_WUERTTEMBERG
import de.remph.urlaubsplaner.core.holidays.Bundesland.BAYERN
import de.remph.urlaubsplaner.core.holidays.Bundesland.BERLIN
import de.remph.urlaubsplaner.core.holidays.Bundesland.BRANDENBURG
import de.remph.urlaubsplaner.core.holidays.Bundesland.BREMEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.HAMBURG
import de.remph.urlaubsplaner.core.holidays.Bundesland.HESSEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.MECKLENBURG_VORPOMMERN
import de.remph.urlaubsplaner.core.holidays.Bundesland.NIEDERSACHSEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.NORDRHEIN_WESTFALEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.RHEINLAND_PFALZ
import de.remph.urlaubsplaner.core.holidays.Bundesland.SAARLAND
import de.remph.urlaubsplaner.core.holidays.Bundesland.SACHSEN
import de.remph.urlaubsplaner.core.holidays.Bundesland.SACHSEN_ANHALT
import de.remph.urlaubsplaner.core.holidays.Bundesland.SCHLESWIG_HOLSTEIN
import de.remph.urlaubsplaner.core.holidays.Bundesland.THUERINGEN
import de.remph.urlaubsplaner.core.holidays.Holiday.ALLERHEILIGEN
import de.remph.urlaubsplaner.core.holidays.Holiday.AUGSBURGER_FRIEDENSFEST
import de.remph.urlaubsplaner.core.holidays.Holiday.BUSS_UND_BETTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.CHRISTI_HIMMELFAHRT
import de.remph.urlaubsplaner.core.holidays.Holiday.ERSTER_WEIHNACHTSTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.FRONLEICHNAM
import de.remph.urlaubsplaner.core.holidays.Holiday.HEILIGE_DREI_KOENIGE
import de.remph.urlaubsplaner.core.holidays.Holiday.INTERNATIONALER_FRAUENTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.KARFREITAG
import de.remph.urlaubsplaner.core.holidays.Holiday.MARIAE_HIMMELFAHRT
import de.remph.urlaubsplaner.core.holidays.Holiday.NEUJAHR
import de.remph.urlaubsplaner.core.holidays.Holiday.OSTERMONTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.OSTERSONNTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.PFINGSTMONTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.PFINGSTSONNTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.REFORMATIONSTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.TAG_DER_ARBEIT
import de.remph.urlaubsplaner.core.holidays.Holiday.TAG_DER_BEFREIUNG
import de.remph.urlaubsplaner.core.holidays.Holiday.TAG_DER_DEUTSCHEN_EINHEIT
import de.remph.urlaubsplaner.core.holidays.Holiday.VOLKSAUFSTAND_17_JUNI
import de.remph.urlaubsplaner.core.holidays.Holiday.WELTKINDERTAG
import de.remph.urlaubsplaner.core.holidays.Holiday.ZWEITER_WEIHNACHTSTAG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

// The expected lists are written by hand from the Länder holiday laws (Feiertagsgesetze), in
// date order for 2026. They are not generated from the code under test.
class PublicHolidaysTest {
    private fun holidays(
        land: Bundesland,
        year: Int = 2026,
        extras: Set<RegionalExtra> = emptySet(),
        optOuts: Set<RegionalOptOut> = emptySet(),
    ) = PublicHolidays.of(land, year, extras, optOuts).map { it.holiday }

    @Test
    fun `there are 16 Bundeslaender with unique codes`() {
        assertEquals(16, Bundesland.entries.size)
        assertEquals(
            16,
            Bundesland.entries
                .map { it.code }
                .toSet()
                .size,
        )
    }

    @Test
    fun `a Bundesland is found by its code`() {
        assertEquals(SACHSEN_ANHALT, Bundesland.fromCode("ST"))
        assertEquals(null, Bundesland.fromCode("XX"))
    }

    @Test
    fun `Baden-Wuerttemberg 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                HEILIGE_DREI_KOENIGE,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                FRONLEICHNAM,
                TAG_DER_DEUTSCHEN_EINHEIT,
                ALLERHEILIGEN,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(BADEN_WUERTTEMBERG),
        )

    // Mariä Himmelfahrt counts for the whole Land by decision (most Bavarian communities).
    @Test
    fun `Bayern 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                HEILIGE_DREI_KOENIGE,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                FRONLEICHNAM,
                MARIAE_HIMMELFAHRT,
                TAG_DER_DEUTSCHEN_EINHEIT,
                ALLERHEILIGEN,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(BAYERN),
        )

    @Test
    fun `Berlin 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                INTERNATIONALER_FRAUENTAG,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(BERLIN),
        )

    @Test
    fun `Brandenburg 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERSONNTAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTSONNTAG,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(BRANDENBURG),
        )

    @Test
    fun `Bremen 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(BREMEN),
        )

    @Test
    fun `Hamburg 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(HAMBURG),
        )

    @Test
    fun `Hessen 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                FRONLEICHNAM,
                TAG_DER_DEUTSCHEN_EINHEIT,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(HESSEN),
        )

    @Test
    fun `Mecklenburg-Vorpommern 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                INTERNATIONALER_FRAUENTAG,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(MECKLENBURG_VORPOMMERN),
        )

    @Test
    fun `Niedersachsen 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(NIEDERSACHSEN),
        )

    @Test
    fun `Nordrhein-Westfalen 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                FRONLEICHNAM,
                TAG_DER_DEUTSCHEN_EINHEIT,
                ALLERHEILIGEN,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(NORDRHEIN_WESTFALEN),
        )

    @Test
    fun `Rheinland-Pfalz 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                FRONLEICHNAM,
                TAG_DER_DEUTSCHEN_EINHEIT,
                ALLERHEILIGEN,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(RHEINLAND_PFALZ),
        )

    @Test
    fun `Saarland 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                FRONLEICHNAM,
                MARIAE_HIMMELFAHRT,
                TAG_DER_DEUTSCHEN_EINHEIT,
                ALLERHEILIGEN,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(SAARLAND),
        )

    @Test
    fun `Sachsen 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                BUSS_UND_BETTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(SACHSEN),
        )

    @Test
    fun `Sachsen-Anhalt 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                HEILIGE_DREI_KOENIGE,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(SACHSEN_ANHALT),
        )

    @Test
    fun `Schleswig-Holstein 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(SCHLESWIG_HOLSTEIN),
        )

    @Test
    fun `Thueringen 2026`() =
        assertEquals(
            listOf(
                NEUJAHR,
                KARFREITAG,
                OSTERMONTAG,
                TAG_DER_ARBEIT,
                CHRISTI_HIMMELFAHRT,
                PFINGSTMONTAG,
                WELTKINDERTAG,
                TAG_DER_DEUTSCHEN_EINHEIT,
                REFORMATIONSTAG,
                ERSTER_WEIHNACHTSTAG,
                ZWEITER_WEIHNACHTSTAG,
            ),
            holidays(THUERINGEN),
        )

    @Test
    fun `the list is sorted by date and carries the real dates`() {
        val bayern = PublicHolidays.of(BAYERN, 2027)

        assertEquals(bayern.map { it.date }.sorted(), bayern.map { it.date })
        assertEquals(LocalDate.of(2027, 5, 27), bayern.single { it.holiday == FRONLEICHNAM }.date)
    }

    @Test
    fun `Frauentag in Mecklenburg-Vorpommern starts in 2023`() {
        assertFalse(INTERNATIONALER_FRAUENTAG in holidays(MECKLENBURG_VORPOMMERN, 2022))
        assertTrue(INTERNATIONALER_FRAUENTAG in holidays(MECKLENBURG_VORPOMMERN, 2023))
    }

    @Test
    fun `Frauentag in Berlin is there in every supported year`() {
        assertTrue(INTERNATIONALER_FRAUENTAG in holidays(BERLIN, 2019))
    }

    @Test
    fun `8 May is a holiday in Berlin only in 2020 and 2025`() {
        assertTrue(TAG_DER_BEFREIUNG in holidays(BERLIN, 2020))
        assertTrue(TAG_DER_BEFREIUNG in holidays(BERLIN, 2025))
        assertFalse(TAG_DER_BEFREIUNG in holidays(BERLIN, 2021))
        assertFalse(TAG_DER_BEFREIUNG in holidays(BERLIN, 2026))
        assertFalse(TAG_DER_BEFREIUNG in holidays(BRANDENBURG, 2025))
    }

    @Test
    fun `17 June is a holiday in Berlin only in 2028`() {
        assertTrue(VOLKSAUFSTAND_17_JUNI in holidays(BERLIN, 2028))
        assertFalse(VOLKSAUFSTAND_17_JUNI in holidays(BERLIN, 2027))
        assertFalse(VOLKSAUFSTAND_17_JUNI in holidays(BERLIN, 2029))
        assertFalse(VOLKSAUFSTAND_17_JUNI in holidays(BRANDENBURG, 2028))
    }

    @Test
    fun `Augsburger Friedensfest is only there when asked for in Bayern`() {
        assertFalse(AUGSBURGER_FRIEDENSFEST in holidays(BAYERN))
        val withExtra = holidays(BAYERN, extras = setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST))
        assertTrue(AUGSBURGER_FRIEDENSFEST in withExtra)
        assertFalse(
            AUGSBURGER_FRIEDENSFEST in
                holidays(BERLIN, extras = setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST)),
        )
    }

    @Test
    fun `Fronleichnam in parts of Sachsen and Thueringen is only there when asked for`() {
        val extra = setOf(RegionalExtra.FRONLEICHNAM_IN_TEILEN)

        assertFalse(FRONLEICHNAM in holidays(SACHSEN))
        assertTrue(FRONLEICHNAM in holidays(SACHSEN, extras = extra))
        assertFalse(FRONLEICHNAM in holidays(THUERINGEN))
        assertTrue(FRONLEICHNAM in holidays(THUERINGEN, extras = extra))
        assertFalse(FRONLEICHNAM in holidays(BERLIN, extras = extra))
    }

    @Test
    fun `an extra does not double a holiday the Land already has`() {
        val extra =
            setOf(RegionalExtra.FRONLEICHNAM_IN_TEILEN, RegionalExtra.AUGSBURGER_FRIEDENSFEST)

        assertEquals(1, holidays(HESSEN, extras = extra).count { it == FRONLEICHNAM })
    }

    @Test
    fun `isHoliday answers for a single day`() {
        assertTrue(PublicHolidays.isHoliday(BAYERN, LocalDate.of(2026, 8, 15)))
        assertFalse(PublicHolidays.isHoliday(BERLIN, LocalDate.of(2026, 8, 15)))
        assertFalse(PublicHolidays.isHoliday(BAYERN, LocalDate.of(2026, 8, 14)))
    }

    @Test
    fun `isHoliday takes extras into account`() {
        val day = LocalDate.of(2026, 8, 8)

        assertFalse(PublicHolidays.isHoliday(BAYERN, day))
        assertTrue(
            PublicHolidays.isHoliday(BAYERN, day, setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST)),
        )
    }

    @Test
    fun `Bayern keeps Mariae Himmelfahrt unless the community opts out`() {
        val optOut = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN)

        assertTrue(MARIAE_HIMMELFAHRT in holidays(BAYERN))
        assertFalse(MARIAE_HIMMELFAHRT in holidays(BAYERN, optOuts = optOut))
    }

    @Test
    fun `the Bayern opt-out removes only Mariae Himmelfahrt`() {
        val optOut = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN)

        assertEquals(
            holidays(BAYERN).filter { it != MARIAE_HIMMELFAHRT },
            holidays(BAYERN, optOuts = optOut),
        )
    }

    @Test
    fun `the Bayern opt-out does nothing in other Laender`() {
        val optOut = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN)

        // Saarland has the holiday in all communities, so the Bayern setting must not reach it.
        assertTrue(MARIAE_HIMMELFAHRT in holidays(SAARLAND, optOuts = optOut))
        for (land in Bundesland.entries - BAYERN) {
            assertEquals(holidays(land), holidays(land, optOuts = optOut), land.toString())
        }
    }

    @Test
    fun `the opt-out works with extras and in every supported year`() {
        val optOut = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN)
        val extra = setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST)

        val result = holidays(BAYERN, 2030, extras = extra, optOuts = optOut)

        assertTrue(AUGSBURGER_FRIEDENSFEST in result)
        assertFalse(MARIAE_HIMMELFAHRT in result)
        for (year in PublicHolidays.FIRST_SUPPORTED_YEAR..2035) {
            assertFalse(MARIAE_HIMMELFAHRT in holidays(BAYERN, year, optOuts = optOut), "$year")
        }
    }

    @Test
    fun `isHoliday takes the opt-out into account`() {
        val day = LocalDate.of(2026, 8, 15)
        val optOut = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN)

        assertTrue(PublicHolidays.isHoliday(BAYERN, day))
        assertFalse(PublicHolidays.isHoliday(BAYERN, day, optOuts = optOut))
        assertTrue(PublicHolidays.isHoliday(SAARLAND, day, optOuts = optOut))
        assertTrue(
            PublicHolidays.isHoliday(BAYERN, LocalDate.of(2026, 12, 25), optOuts = optOut),
        )
    }

    @Test
    fun `years before 2019 are not supported`() {
        assertThrows(IllegalArgumentException::class.java) { PublicHolidays.of(BERLIN, 2018) }
    }

    @Test
    fun `the first supported year works`() {
        assertEquals(LocalDate.of(2019, 1, 1), PublicHolidays.of(BERLIN, 2019).first().date)
    }
}
