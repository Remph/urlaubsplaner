// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.ics

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// 2027 calendar used below: 12 April is a Monday, 16 April a Friday, 19 April a Monday.
class IcsExportTest {
    private val clock = Clock.fixed(Instant.parse("2027-03-10T08:30:15Z"), ZoneOffset.UTC)
    private val calculator = VacationCalculator(clock) { false }
    private val export = IcsExport(clock, calculator)

    private fun day(
        month: Int,
        dayOfMonth: Int,
    ) = LocalDate.of(2027, month, dayOfMonth)

    private fun week(
        mark: DayMark = DayMark.FULL,
        from: LocalDate = day(4, 12),
    ): Map<LocalDate, DayMark> = (0L..4L).associate { from.plusDays(it) to mark }

    private fun lines(text: String) = text.split("\r\n")

    // Most tests do not care about granted days: a plain map means none are granted.
    private fun IcsExport.encode(
        marks: Map<LocalDate, DayMark>,
        language: IcsLanguage,
    ) = encode(MarkedDays(marks), language)

    private fun IcsExport.tripCount(marks: Map<LocalDate, DayMark>) = tripCount(MarkedDays(marks))

    private fun events(text: String) = text.split("BEGIN:VEVENT").drop(1).map { lines(it) }

    @Test
    fun `a week is one all-day event that ends the day after the last marked day`() {
        val text = export.encode(week(), IcsLanguage.GERMAN)
        val events = lines(text).filter { it == "BEGIN:VEVENT" }
        assertEquals(1, events.size)
        assertTrue("DTSTART;VALUE=DATE:20270412" in lines(text))
        // DTEND of an all-day event is exclusive: Friday 16 April ends on Saturday 17 April.
        assertTrue("DTEND;VALUE=DATE:20270417" in lines(text))
        assertTrue("SUMMARY:Urlaub" in lines(text))
        assertTrue("DESCRIPTION:5 Urlaubstage" in lines(text))
    }

    @Test
    fun `the calendar has the frame and CRLF line ends`() {
        val text = export.encode(week(), IcsLanguage.GERMAN)
        val all = lines(text)
        assertEquals("BEGIN:VCALENDAR", all.first())
        assertEquals("VERSION:2.0", all[1])
        assertTrue(all.any { it.startsWith("PRODID:") })
        assertEquals("END:VCALENDAR", all[all.size - 2])
        assertEquals("", all.last())
        assertFalse(text.replace("\r\n", "").contains('\n'))
    }

    @Test
    fun `the stamp comes from the clock`() {
        val text = export.encode(week(), IcsLanguage.GERMAN)
        assertTrue("DTSTAMP:20270310T083015Z" in lines(text))
    }

    @Test
    fun `the uid depends only on the first day, so importing again does not duplicate`() {
        val first = export.encode(week(), IcsLanguage.GERMAN)
        val later =
            IcsExport(
                Clock.fixed(Instant.parse("2027-06-01T00:00:00Z"), ZoneOffset.UTC),
                calculator,
            ).encode(week(), IcsLanguage.ENGLISH)
        val uid = "UID:urlaubsplaner-2027-04-12@de.remph.urlaubsplaner"
        assertTrue(uid in lines(first))
        assertTrue(uid in lines(later))
    }

    @Test
    fun `a weekend between two weeks keeps them in one trip`() {
        val marks = week() + week(from = day(4, 19))
        val text = export.encode(marks, IcsLanguage.GERMAN)
        assertEquals(1, lines(text).count { it == "BEGIN:VEVENT" })
        assertTrue("DTEND;VALUE=DATE:20270424" in lines(text))
        assertTrue("DESCRIPTION:10 Urlaubstage" in lines(text))
    }

    @Test
    fun `a gap of working days makes two events sorted by date`() {
        val marks = week(from = day(6, 7)) + week()
        val text = export.encode(marks, IcsLanguage.GERMAN)
        val starts = lines(text).filter { it.startsWith("DTSTART") }
        assertEquals(listOf("DTSTART;VALUE=DATE:20270412", "DTSTART;VALUE=DATE:20270607"), starts)
    }

    @Test
    fun `half days are counted and named`() {
        val marks = week().toMutableMap().apply { put(day(4, 16), DayMark.HALF) }
        val text = export.encode(marks, IcsLanguage.GERMAN)
        assertTrue("DESCRIPTION:4\\,5 Urlaubstage\\, davon 1 halber Tag" in lines(text))
    }

    @Test
    fun `two half days use the plural`() {
        val marks =
            week().toMutableMap().apply {
                put(day(4, 15), DayMark.HALF)
                put(day(4, 16), DayMark.HALF)
            }
        val text = export.encode(marks, IcsLanguage.GERMAN)
        assertTrue("DESCRIPTION:4 Urlaubstage\\, davon 2 halbe Tage" in lines(text))
    }

    @Test
    fun `one day is singular`() {
        val text = export.encode(mapOf(day(4, 12) to DayMark.FULL), IcsLanguage.GERMAN)
        assertTrue("DESCRIPTION:1 Urlaubstag" in lines(text))
    }

    @Test
    fun `a single half day`() {
        val text = export.encode(mapOf(day(4, 12) to DayMark.HALF), IcsLanguage.GERMAN)
        assertTrue("DESCRIPTION:0\\,5 Urlaubstage\\, davon 1 halber Tag" in lines(text))
    }

    @Test
    fun `english text uses the decimal point and english words`() {
        val marks = week().toMutableMap().apply { put(day(4, 16), DayMark.HALF) }
        val text = export.encode(marks, IcsLanguage.ENGLISH)
        assertTrue("SUMMARY:Vacation" in lines(text))
        assertTrue("DESCRIPTION:4.5 vacation days\\, 1 of them a half day" in lines(text))
        assertTrue(
            "DESCRIPTION:1 vacation day" in
                lines(export.encode(mapOf(day(4, 12) to DayMark.FULL), IcsLanguage.ENGLISH)),
        )
    }

    @Test
    fun `no marks give a valid calendar without events`() {
        val text = export.encode(emptyMap(), IcsLanguage.GERMAN)
        assertFalse("BEGIN:VEVENT" in text)
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun `marks that cost nothing are not exported`() {
        // A marked weekend day costs no vacation, so it is no trip.
        val text = export.encode(mapOf(day(4, 17) to DayMark.FULL), IcsLanguage.GERMAN)
        assertFalse("BEGIN:VEVENT" in text)
        assertEquals(0, export.tripCount(mapOf(day(4, 17) to DayMark.FULL)))
        assertEquals(1, export.tripCount(week()))
    }

    @Test
    fun `a holiday inside a trip does not count as a vacation day`() {
        val holiday = day(4, 14)
        val withHoliday = IcsExport(clock, VacationCalculator(clock) { it == holiday })
        val text = withHoliday.encode(week(), IcsLanguage.GERMAN)
        assertTrue("DESCRIPTION:4 Urlaubstage" in lines(text))
    }

    @Test
    fun `planned days are tentative and granted days are confirmed`() {
        val planned = export.encode(week(), IcsLanguage.GERMAN)
        assertTrue("STATUS:TENTATIVE" in lines(planned))
        assertFalse("STATUS:CONFIRMED" in lines(planned))

        val granted = export.encode(MarkedDays(week(), week().keys), IcsLanguage.GERMAN)
        assertTrue("STATUS:CONFIRMED" in lines(granted))
        assertFalse("STATUS:TENTATIVE" in lines(granted))
    }

    @Test
    fun `with the granted days setting off the file has no status and no split`() {
        val days = MarkedDays(week() + week(from = day(4, 19)), week().keys)

        val text = export.encode(days, IcsLanguage.GERMAN, withStatus = false)

        assertFalse("STATUS" in text)
        assertEquals(1, events(text).size)
        assertEquals(1, export.tripCount(days, withStatus = false))
    }

    @Test
    fun `days up to today are confirmed whatever the granted flag says`() {
        // Today is Wednesday 10 March 2027; the week from Monday 8 March is taken only in part.
        val marks = week(from = day(3, 8))
        val events = events(export.encode(MarkedDays(marks), IcsLanguage.GERMAN))

        assertEquals(2, events.size)
        assertTrue("STATUS:CONFIRMED" in events[0])
        assertTrue("DTSTART;VALUE=DATE:20270308" in events[0])
        assertTrue("DTEND;VALUE=DATE:20270311" in events[0])
        assertTrue("STATUS:TENTATIVE" in events[1])
        assertTrue("DTSTART;VALUE=DATE:20270311" in events[1])
        assertTrue("DTEND;VALUE=DATE:20270313" in events[1])
    }

    @Test
    fun `a trip splits where the status changes and each part counts its own days`() {
        // Monday to Wednesday granted (the Wednesday half), Thursday and Friday only planned.
        val marks = week().toMutableMap().apply { put(day(4, 14), DayMark.HALF) }
        val days = MarkedDays(marks, setOf(day(4, 12), day(4, 13), day(4, 14)))
        val events = events(export.encode(days, IcsLanguage.GERMAN))

        assertEquals(2, events.size)
        assertEquals(2, export.tripCount(days))
        assertTrue("STATUS:CONFIRMED" in events[0])
        assertTrue("DTEND;VALUE=DATE:20270415" in events[0])
        assertTrue("DESCRIPTION:2\\,5 Urlaubstage\\, davon 1 halber Tag" in events[0])
        assertTrue("UID:urlaubsplaner-2027-04-12@de.remph.urlaubsplaner" in events[0])
        assertTrue("STATUS:TENTATIVE" in events[1])
        assertTrue("DTSTART;VALUE=DATE:20270415" in events[1])
        assertTrue("DESCRIPTION:2 Urlaubstage" in events[1])
        assertTrue("UID:urlaubsplaner-2027-04-15@de.remph.urlaubsplaner" in events[1])
    }

    @Test
    fun `a weekend between runs of different status belongs to neither`() {
        val days = MarkedDays(week() + week(from = day(4, 19)), week().keys)
        val events = events(export.encode(days, IcsLanguage.GERMAN))

        assertEquals(2, events.size)
        assertTrue("DTEND;VALUE=DATE:20270417" in events[0])
        assertTrue("DTSTART;VALUE=DATE:20270419" in events[1])
    }

    @Test
    fun `a trip of one status stays one event over its weekend`() {
        val days =
            MarkedDays(
                week() + week(from = day(4, 19)),
                week().keys + week(from = day(4, 19)).keys,
            )

        assertEquals(1, export.tripCount(days))
    }

    @Test
    fun `file name and type`() {
        assertEquals("urlaubsplaner-2027-03-10.ics", IcsExport.fileName(day(3, 10)))
        assertEquals("text/calendar", IcsExport.MIME_TYPE)
    }

    @Test
    fun `english only for an english locale, German for everything else`() {
        assertEquals(IcsLanguage.ENGLISH, IcsLanguage.of(java.util.Locale.UK))
        assertEquals(IcsLanguage.GERMAN, IcsLanguage.of(java.util.Locale.GERMANY))
        // The app falls back to German for a system language it has no texts for.
        assertEquals(IcsLanguage.GERMAN, IcsLanguage.of(java.util.Locale.FRANCE))
    }

    @Test
    fun `the first language the app has texts for decides`() {
        val turkish = java.util.Locale.forLanguageTag("tr-TR")
        val english = java.util.Locale.UK
        val german = java.util.Locale.GERMANY
        // The screens show English here, so the file does too.
        assertEquals(IcsLanguage.ENGLISH, IcsLanguage.of(listOf(turkish, english, german)))
        assertEquals(IcsLanguage.GERMAN, IcsLanguage.of(listOf(turkish, german, english)))
        assertEquals(IcsLanguage.GERMAN, IcsLanguage.of(listOf(turkish)))
        assertEquals(IcsLanguage.GERMAN, IcsLanguage.of(emptyList()))
    }

    @Test
    fun `text values are escaped`() {
        assertEquals("a\\,b\\;c\\\\d\\ne", IcsText.escape("a,b;c\\d\ne"))
    }

    @Test
    fun `lines over 75 bytes are folded and nothing is lost`() {
        val long = "DESCRIPTION:" + "x".repeat(200)
        val folded = IcsText.fold(long)
        val parts = folded.split("\r\n")
        assertTrue(parts.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertTrue(parts.drop(1).all { it.startsWith(" ") })
        assertEquals(long, parts.joinToString("") { it.removePrefix(" ") })
    }

    @Test
    fun `folding never cuts a multi-byte character`() {
        val long = "SUMMARY:" + "ä".repeat(100)
        val parts = IcsText.fold(long).split("\r\n")
        assertTrue(parts.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertEquals(long, parts.joinToString("") { it.removePrefix(" ") })
    }

    @Test
    fun `a short line is left alone`() {
        assertEquals("SUMMARY:Urlaub", IcsText.fold("SUMMARY:Urlaub"))
    }
}
