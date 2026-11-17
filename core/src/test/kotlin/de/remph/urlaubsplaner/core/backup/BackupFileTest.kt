// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.backup

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.settings.UserSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

class BackupFileTest {
    private val exportedOn = LocalDate.of(2026, 10, 25)

    private val everything =
        BackupData(
            settings =
                UserSettings(
                    land = Bundesland.BAYERN,
                    extras = setOf(RegionalExtra.AUGSBURGER_FRIEDENSFEST),
                    optOuts = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN),
                    schedule =
                        WorkSchedule(
                            workingDays =
                                setOf(
                                    DayOfWeek.MONDAY,
                                    DayOfWeek.TUESDAY,
                                    DayOfWeek.THURSDAY,
                                ),
                            specialDays =
                                mapOf(
                                    WorkSchedule.HEILIGABEND to VacationDays.HALF,
                                    WorkSchedule.SILVESTER to VacationDays.ZERO,
                                ),
                        ),
                    suggestCarryOver = true,
                ),
            entitlements =
                listOf(
                    YearEntitlement(
                        2026,
                        VacationDays.of(30),
                        VacationDays.of(2.5),
                        MonthDay.of(4, 30),
                    ),
                    YearEntitlement(
                        2027,
                        VacationDays.of(28),
                        VacationDays.of(3),
                        MonthDay.of(3, 31),
                        carryOverExpiryYears = 1,
                    ),
                ),
            marks =
                mapOf(
                    LocalDate.of(2026, 7, 1) to DayMark.FULL,
                    LocalDate.of(2026, 7, 2) to DayMark.HALF,
                    LocalDate.of(2027, 1, 4) to DayMark.FULL,
                ),
            granted = setOf(LocalDate.of(2026, 7, 2), LocalDate.of(2027, 1, 4)),
        )

    private fun roundTrip(data: BackupData) = BackupFile.decode(BackupFile.encode(data, exportedOn))

    /** A version 2 file written by hand: the names in it are the format, so renaming code must not change them. */
    private val fixture =
        """
        {
          "app": "urlaubsplaner",
          "formatVersion": 2,
          "exportedOn": "2026-10-25",
          "settings": {
            "land": "BY",
            "regionalExtras": ["augsburger-friedensfest"],
            "regionalOptOuts": ["mariae-himmelfahrt-bayern"],
            "workingDays": ["MONDAY", "TUESDAY", "THURSDAY"],
            "specialDays": [{"day": "--12-24", "halfDays": 1}, {"day": "--12-31", "halfDays": 0}],
            "suggestCarryOver": true
          },
          "entitlements": [
            {"year": 2026, "halfDays": 60, "carryOverHalfDays": 5, "carryOverExpiry": "--04-30"},
            {"year": 2027, "halfDays": 56, "carryOverHalfDays": 6, "carryOverExpiry": "--03-31",
             "carryOverExpiryYears": 1}
          ],
          "markedDays": [
            {"date": "2026-07-01", "mark": "full", "granted": false},
            {"date": "2026-07-02", "mark": "half", "granted": true},
            {"date": "2027-01-04", "mark": "full", "granted": true}
          ]
        }
        """.trimIndent()

    /** A version 1 file written by hand, as 0.9.x wrote it: it must keep reading, with every day not granted. */
    private val fixtureV1 =
        """
        {
          "app": "urlaubsplaner",
          "formatVersion": 1,
          "exportedOn": "2026-10-25",
          "settings": {
            "land": "BY",
            "regionalExtras": ["augsburger-friedensfest"],
            "regionalOptOuts": ["mariae-himmelfahrt-bayern"],
            "workingDays": ["MONDAY", "TUESDAY", "THURSDAY"],
            "specialDays": [{"day": "--12-24", "halfDays": 1}, {"day": "--12-31", "halfDays": 0}],
            "suggestCarryOver": true
          },
          "entitlements": [
            {"year": 2026, "halfDays": 60, "carryOverHalfDays": 5, "carryOverExpiry": "--04-30"},
            {"year": 2027, "halfDays": 56, "carryOverHalfDays": 6, "carryOverExpiry": "--03-31",
             "carryOverExpiryYears": 1}
          ],
          "markedDays": [
            {"date": "2026-07-01", "mark": "full"},
            {"date": "2026-07-02", "mark": "half"},
            {"date": "2027-01-04", "mark": "full"}
          ]
        }
        """.trimIndent()

    private fun withEdit(
        from: String,
        to: String,
    ): String {
        assertTrue(fixture.contains(from), "the fixture has no $from")
        return fixture.replace(from, to)
    }

    private inline fun <reified T : BackupException> refused(text: String): T =
        assertThrows<T> {
            BackupFile.decode(text)
        }

    @Test
    fun `everything survives a round trip`() {
        assertEquals(everything, roundTrip(everything))
    }

    @Test
    fun `an empty app round trips, with no Land chosen`() {
        val empty = BackupData()

        val back = roundTrip(empty)

        assertEquals(empty, back)
        assertEquals(null, back.settings.land)
        assertTrue(back.isEmpty)
    }

    @Test
    fun `the written file has the documented names`() {
        assertEquals(everything, BackupFile.decode(fixture))
    }

    @Test
    fun `the written file carries the version, the app marker and the export date`() {
        val text = BackupFile.encode(everything, exportedOn)

        assertTrue(text.contains("\"app\": \"urlaubsplaner\""))
        assertTrue(text.contains("\"formatVersion\": 2"))
        assertTrue(text.contains("\"exportedOn\": \"2026-10-25\""))
    }

    @Test
    fun `entries are written in date order so two exports of the same data are the same file`() {
        val shuffled =
            everything.copy(
                entitlements = everything.entitlements.reversed(),
                marks =
                    everything.marks.entries
                        .reversed()
                        .associate { it.key to it.value },
            )

        assertEquals(
            BackupFile.encode(everything, exportedOn),
            BackupFile.encode(shuffled, exportedOn),
        )
    }

    @Test
    fun `half days and Resturlaub keep their halves`() {
        val back = roundTrip(everything)

        assertEquals(VacationDays.of(2.5), back.entitlements.first().carryOver)
        assertEquals(DayMark.HALF, back.marks.getValue(LocalDate.of(2026, 7, 2)))
        assertEquals(
            VacationDays.HALF,
            back.settings.schedule.specialDays
                .getValue(WorkSchedule.HEILIGABEND),
        )
    }

    @Test
    fun `the file name has the export date`() {
        assertEquals("urlaubsplaner-2026-10-25.json", BackupFile.fileName(exportedOn))
    }

    @Test
    fun `every enum value has a name that reads back`() {
        val all =
            BackupData(
                settings =
                    UserSettings(
                        land = null,
                        extras = RegionalExtra.entries.toSet(),
                        optOuts = RegionalOptOut.entries.toSet(),
                        schedule = WorkSchedule(workingDays = DayOfWeek.entries.toSet()),
                    ),
                marks =
                    DayMark.entries.withIndex().associate { (i, mark) ->
                        LocalDate.of(
                            2026,
                            1,
                            1 + i,
                        ) to
                            mark
                    },
            )

        assertEquals(all, roundTrip(all))
        Bundesland.entries.forEach {
            val data = BackupData(settings = UserSettings(land = it))
            assertEquals(it, roundTrip(data).settings.land)
        }
    }

    @Test
    fun `a stream round trip works and a byte order mark in front is ignored`() {
        val out = ByteArrayOutputStream()
        BackupFile.writeTo(out, everything, exportedOn)
        val bytes = out.toByteArray()

        assertEquals(everything, BackupFile.readFrom(ByteArrayInputStream(bytes)))
        assertEquals(
            everything,
            BackupFile.readFrom(
                ByteArrayInputStream(
                    byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes,
                ),
            ),
        )
    }

    @Test
    fun `a stream is read as UTF-8`() {
        val text =
            fixture.replace(
                "\"exportedOn\"",
                "\"note\": \"Urlaub für Müller\", \"exportedOn\"",
            )

        assertEquals(
            everything,
            BackupFile.readFrom(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))),
        )
    }

    @Test
    fun `a file over the size limit is refused without reading all of it`() {
        var read = 0L
        val endless =
            object : InputStream() {
                override fun read(): Int = 'x'.code.also { read++ }

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int {
                    read += len
                    return len
                }
            }

        assertThrows<BackupException.NotABackup> { BackupFile.readFrom(endless) }
        assertTrue(read < BackupFile.MAX_BYTES + 16_384)
    }

    @Test
    fun `an empty file and text that is not JSON are not a backup`() {
        refused<BackupException.NotABackup>("")
        refused<BackupException.NotABackup>("Urlaub 2026")
        refused<BackupException.NotABackup>("{\"app\": ")
    }

    @Test
    fun `a deeply nested file is refused instead of crashing the app`() {
        // Within the size limit, but a recursive parser would overflow the stack.
        val nested = "[".repeat(1_000_000)

        refused<BackupException.NotABackup>(nested)
        refused<BackupException.NotABackup>("{\"a\":".repeat(500_000))
    }

    @Test
    fun `the size limit keeps the JSON tree well inside an app's memory`() {
        // 8 MB of tiny elements took about 360 MB as a tree in a measurement on 2026-10-26.
        assertTrue(BackupFile.MAX_BYTES <= 2 * 1024 * 1024)
        // And a real backup is far below it: five years of marks every day, written by the app.
        val days = generateSequence(LocalDate.of(2026, 1, 1)) { it.plusDays(1) }.take(5 * 366)
        val busy = BackupData(marks = days.associateWith { DayMark.FULL })

        assertTrue(BackupFile.encode(busy, exportedOn).length < BackupFile.MAX_BYTES / 2)
    }

    @Test
    fun `years that are not four digits are damaged, not stored`() {
        for (year in listOf("-1", "10000", "999999999", "2147483647")) {
            refused<BackupException.Damaged>(withEdit("\"year\": 2026", "\"year\": $year"))
        }
        for (date in listOf(
            "+999999999-12-31",
            "-999999999-01-01",
            "+10000-01-01",
            "-0001-12-31",
        )) {
            refused<BackupException.Damaged>(withEdit("\"2026-07-01\"", "\"$date\""))
        }
        // The edges of the range are fine.
        BackupFile.decode(withEdit("\"year\": 2026", "\"year\": 9999"))
        BackupFile.decode(withEdit("\"2026-07-01\"", "\"9999-12-31\""))
    }

    @Test
    fun `a message that quotes the file stays short enough for a dialog`() {
        val land = "A".repeat(1_000_000)

        val e = refused<BackupException.Damaged>(withEdit("\"BY\"", "\"$land\""))

        assertTrue(e.message!!.length < 500, "the message has ${e.message!!.length} characters")
    }

    @Test
    fun `JSON that is not an object, or not ours, is not a backup`() {
        refused<BackupException.NotABackup>("[1, 2]")
        refused<BackupException.NotABackup>("{}")
        refused<BackupException.NotABackup>(withEdit("\"urlaubsplaner\"", "\"other\""))
        refused<BackupException.NotABackup>(withEdit("\"urlaubsplaner\"", "42"))
    }

    @Test
    fun `a newer version is refused with both version numbers`() {
        val e =
            refused<BackupException.NewerVersion>(
                withEdit("\"formatVersion\": 2", "\"formatVersion\": 3"),
            )

        assertEquals(3, e.found)
        assertEquals(BackupFile.VERSION, e.supported)
    }

    @Test
    fun `a newer version is recognised even when its structure is different`() {
        val e =
            refused<BackupException.NewerVersion>(
                """{"app": "urlaubsplaner", "formatVersion": 7, "vacation": {"somethingNew": true}}""",
            )

        assertEquals(7, e.found)
    }

    @Test
    fun `a missing, zero or non-numeric version is damaged`() {
        refused<BackupException.Damaged>("""{"app": "urlaubsplaner"}""")
        refused<BackupException.Damaged>(withEdit("\"formatVersion\": 2", "\"formatVersion\": 0"))
        refused<BackupException.Damaged>(
            withEdit("\"formatVersion\": 2", "\"formatVersion\": \"2\""),
        )
        refused<BackupException.Damaged>(withEdit("\"formatVersion\": 2", "\"formatVersion\": 1.5"))
    }

    @Test
    fun `an unknown name is refused, not skipped`() {
        refused<BackupException.Damaged>(withEdit("\"BY\"", "\"XX\""))
        refused<BackupException.Damaged>(withEdit("augsburger-friedensfest", "from-the-future"))
        refused<BackupException.Damaged>(withEdit("mariae-himmelfahrt-bayern", "from-the-future"))
        refused<BackupException.Damaged>(withEdit("\"THURSDAY\"", "\"DONNERSTAG\""))
        refused<BackupException.Damaged>(withEdit("\"half\"", "\"quarter\""))
    }

    @Test
    fun `a bad date or day is refused`() {
        refused<BackupException.Damaged>(withEdit("2026-07-01", "2026-02-30"))
        refused<BackupException.Damaged>(withEdit("2026-07-01", "01.07.2026"))
        refused<BackupException.Damaged>(withEdit("--12-24", "12-24"))
        refused<BackupException.Damaged>(withEdit("--04-30", "--02-30"))
    }

    @Test
    fun `values outside the rules are refused`() {
        // Negative entitlement and Resturlaub, a year of more than 366 days, a special day above a full day.
        refused<BackupException.Damaged>(withEdit("\"halfDays\": 60", "\"halfDays\": -2"))
        refused<BackupException.Damaged>(
            withEdit("\"carryOverHalfDays\": 5", "\"carryOverHalfDays\": -1"),
        )
        refused<BackupException.Damaged>(withEdit("\"halfDays\": 60", "\"halfDays\": 2000000000"))
        refused<BackupException.Damaged>(withEdit("\"halfDays\": 1}", "\"halfDays\": 3}"))
    }

    @Test
    fun `a missing or wrongly typed part is refused and names the place`() {
        val missing =
            refused<BackupException.Damaged>(withEdit("\"markedDays\"", "\"somethingElse\""))
        assertTrue(missing.message!!.contains("markedDays"))

        refused<BackupException.Damaged>(withEdit("\"settings\"", "\"unrelated\""))
        refused<BackupException.Damaged>(withEdit("\"land\": \"BY\"", "\"land\": 7"))
        refused<BackupException.Damaged>(withEdit("\"year\": 2026", "\"year\": \"2026\""))
        refused<BackupException.Damaged>(
            """
            {"app": "urlaubsplaner", "formatVersion": 1,
             "settings": {"land": null, "regionalExtras": [], "regionalOptOuts": [], "workingDays": [], "specialDays": []},
             "entitlements": {}, "markedDays": []}
            """.trimIndent(),
        )
        refused<BackupException.Damaged>(
            withEdit(
                "{\"date\": \"2026-07-01\", \"mark\": \"full\", \"granted\": false}",
                "\"2026-07-01\"",
            ),
        )

        val bad =
            refused<BackupException.Damaged>(
                withEdit("\"mark\": \"half\"", "\"mark\": \"quarter\""),
            )
        assertTrue(bad.message!!.contains("markedDays[1]"))
    }

    @Test
    fun `listing the same year, date or special day twice is refused`() {
        refused<BackupException.Damaged>(withEdit("\"year\": 2027", "\"year\": 2026"))
        refused<BackupException.Damaged>(withEdit("2027-01-04", "2026-07-01"))
        refused<BackupException.Damaged>(withEdit("--12-31", "--12-24"))
    }

    @Test
    fun `a file without the carry-over key reads as off`() {
        val data = BackupFile.decode(withEdit(",\n    \"suggestCarryOver\": true", ""))

        assertEquals(false, data.settings.suggestCarryOver)
        assertEquals(
            everything.copy(settings = everything.settings.copy(suggestCarryOver = false)),
            data,
        )
    }

    @Test
    fun `the carry-over key must be true or false`() {
        refused<BackupException.Damaged>(
            withEdit("\"suggestCarryOver\": true", "\"suggestCarryOver\": 1"),
        )
        refused<BackupException.Damaged>(
            withEdit("\"suggestCarryOver\": true", "\"suggestCarryOver\": \"true\""),
        )
        refused<BackupException.Damaged>(
            withEdit("\"suggestCarryOver\": true", "\"suggestCarryOver\": null"),
        )
    }

    @Test
    fun `a file without the expiry year reads as the entitlement's own year`() {
        val data = BackupFile.decode(fixture)

        assertEquals(0, data.entitlements.first { it.year == 2026 }.carryOverExpiryYears)
        assertEquals(1, data.entitlements.first { it.year == 2027 }.carryOverExpiryYears)
    }

    @Test
    fun `the expiry year is written and must be 0 or 1`() {
        assertTrue(
            BackupFile.encode(everything, exportedOn).contains("\"carryOverExpiryYears\": 1"),
        )
        for (bad in listOf("2", "-1", "\"1\"", "null", "1.5")) {
            refused<BackupException.Damaged>(
                withEdit("\"carryOverExpiryYears\": 1", "\"carryOverExpiryYears\": $bad"),
            )
        }
    }

    @Test
    fun `no Land in the file means no Land chosen`() {
        val data = BackupFile.decode(withEdit("\"land\": \"BY\"", "\"land\": null"))

        assertEquals(null, data.settings.land)
    }

    @Test
    fun `a backup with a key this version does not know is still read`() {
        // Hand-edited or from a tool; a real format change would have a new version number.
        val data =
            BackupFile.decode(
                withEdit("\"exportedOn\"", "\"comment\": \"hi\", \"exportedOn\""),
            )

        assertEquals(everything, data)
    }

    @Test
    fun `a version 1 file still reads, with every marked day not granted`() {
        val data = BackupFile.decode(fixtureV1)

        assertEquals(everything.copy(granted = emptySet()), data)
        assertTrue(data.granted.isEmpty())
    }

    @Test
    fun `a version 1 file is not changed by a granted key it never had`() {
        val data =
            BackupFile.decode(
                fixtureV1.replace("\"mark\": \"half\"", "\"mark\": \"half\", \"granted\": true"),
            )

        assertTrue(data.granted.isEmpty())
    }

    @Test
    fun `granted is written for every marked day`() {
        val text = BackupFile.encode(everything, exportedOn)

        assertEquals(3, Regex("\"granted\": (true|false)").findAll(text).count())
        assertEquals(2, Regex("\"granted\": true").findAll(text).count())
    }

    @Test
    fun `in version 2 granted is required and must be true or false`() {
        refused<BackupException.Damaged>(withEdit(", \"granted\": false", ""))
        for (bad in listOf("1", "\"true\"", "null")) {
            refused<BackupException.Damaged>(withEdit("\"granted\": false", "\"granted\": $bad"))
        }
        val missing = refused<BackupException.Damaged>(withEdit(", \"granted\": false", ""))
        assertTrue(missing.message!!.contains("markedDays[0].granted"))
    }

    @Test
    fun `the summary counts what an import would replace`() {
        assertEquals(
            BackupSummary(landChosen = true, years = 2, markedDays = 3),
            everything.summary(),
        )
        assertEquals(
            BackupSummary(landChosen = false, years = 0, markedDays = 0),
            BackupData().summary(),
        )
        assertFalse(everything.isEmpty)
    }
}
