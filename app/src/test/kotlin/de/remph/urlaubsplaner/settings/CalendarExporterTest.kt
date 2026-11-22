// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.assumeUnixPaths
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.ics.IcsLanguage
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.data.CalendarFileStore
import de.remph.urlaubsplaner.data.TestDocuments
import de.remph.urlaubsplaner.data.UrlaubsplanerDatabase
import de.remph.urlaubsplaner.data.UserDataRepository
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Stored data to calendar file, against a real in-memory database (Robolectric on the JVM). */
@RunWith(AndroidJUnit4::class)
class CalendarExporterTest {
    private lateinit var context: Context
    private lateinit var database: UrlaubsplanerDatabase
    private lateinit var repository: UserDataRepository
    private lateinit var exporter: CalendarExporter
    private lateinit var file: File

    // Wednesday 10 March 2027.
    private val clock = Clock.fixed(Instant.parse("2027-03-10T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun open() {
        TestDocuments.register()
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, UrlaubsplanerDatabase::class.java).build()
        repository = UserDataRepository(database)
        exporter = CalendarExporter(repository, CalendarFileStore(context), clock)
        file = File.createTempFile("calendar", ".ics")
    }

    @After
    fun close() {
        database.close()
        file.delete()
    }

    private fun date(text: String) = LocalDate.parse(text)

    private fun land() =
        runBlocking { repository.saveSettings(UserSettings(land = Bundesland.BAYERN)) }

    /** Taken Monday and Tuesday of 8 March, planned the week of 15 March: two trips. */
    private fun twoTrips() =
        runBlocking {
            repository.markAll(listOf(date("2027-03-08"), date("2027-03-09")), DayMark.FULL)
            repository.markAll((15..19).map { date("2027-03-$it") }, DayMark.FULL)
        }

    @Test
    fun `without a Land nothing can be priced, so there is nothing to export`() {
        twoTrips()

        assertEquals(0, runBlocking { exporter.tripCount() })
        assertNull(runBlocking { exporter.openUri(IcsLanguage.GERMAN) })
    }

    @Test
    fun `the count is the number of trips`() {
        land()
        twoTrips()

        assertEquals(2, runBlocking { exporter.tripCount() })
    }

    @Test
    fun `a stored mark from before 2019 is exported like any other and does not crash`() {
        land()
        twoTrips()
        runBlocking { repository.mark(date("2018-06-04"), DayMark.FULL) }

        assertEquals(3, runBlocking { exporter.tripCount() })
    }

    @Test
    fun `saving writes the file the picker gave`() {
        land()
        twoTrips()

        runBlocking { exporter.save(TestDocuments.uriFor(file), IcsLanguage.GERMAN) }

        val text = file.readText()
        assertEquals(2, text.split("BEGIN:VEVENT").size - 1)
        assertTrue(text.contains("DTSTART;VALUE=DATE:20270308"))
        assertTrue(text.contains("DTEND;VALUE=DATE:20270310"))
        assertTrue(text.contains("SUMMARY:Urlaub"))
        assertTrue(text.contains("DTSTAMP:20270310T100000Z"))
    }

    @Test
    fun `saving a shorter file over a longer one leaves no tail`() {
        land()
        twoTrips()
        file.writeText("x".repeat(10_000))

        runBlocking { exporter.save(TestDocuments.uriFor(file), IcsLanguage.ENGLISH) }

        assertTrue(file.readText().endsWith("END:VCALENDAR\r\n"))
        assertTrue(file.readText().contains("SUMMARY:Vacation"))
    }

    @Test
    fun `opening writes a cache file a calendar app can read`() {
        assumeUnixPaths()
        land()
        twoTrips()

        // The same exporter that computes on its own thread: the count and the text are made
        // there; the file name is asked for on the caller's thread, that is all.
        var uri: Uri? = null
        val threads = threadsAskingTheClock { uri = it.openUri(IcsLanguage.GERMAN) }

        assertTrue("compute-test" in threads)
        assertNotNull(uri)
        // The debug build has the application ID suffix ".debug", the release build has none.
        assertEquals(context.packageName + ".fileprovider", uri!!.authority)
        assertEquals("urlaubsplaner-2027-03-10.ics", uri!!.lastPathSegment)
        val text =
            context.contentResolver
                .openInputStream(
                    uri!!,
                )!!
                .use { it.readBytes().decodeToString() }
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertEquals(2, text.split("BEGIN:VEVENT").size - 1)
    }

    @Test
    fun `opening with no vacation hands over nothing`() {
        land()

        assertNull(runBlocking { exporter.openUri(IcsLanguage.GERMAN) })
    }

    /** The threads that asked the clock while [block] ran with an exporter that computes on "compute-test". */
    private fun threadsAskingTheClock(block: suspend (CalendarExporter) -> Unit): Set<String> {
        val threads = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val spy =
            object : Clock() {
                override fun getZone() = clock.zone

                override fun withZone(zone: java.time.ZoneId) = clock.withZone(zone)

                override fun instant(): Instant {
                    threads += Thread.currentThread().name.substringBefore(" @")
                    return clock.instant()
                }
            }
        val executor =
            java.util.concurrent.Executors
                .newSingleThreadExecutor { Thread(it, "compute-test") }
        try {
            val own =
                CalendarExporter(
                    repository,
                    CalendarFileStore(context),
                    spy,
                    executor.asCoroutineDispatcher(),
                )
            runBlocking { block(own) }
        } finally {
            executor.shutdown()
        }
        return threads
    }

    @Test
    fun `the count and the saved file are made on the compute dispatcher`() {
        land()
        twoTrips()

        val threads =
            threadsAskingTheClock {
                assertEquals(2, it.tripCount())
                it.save(TestDocuments.uriFor(file), IcsLanguage.GERMAN)
            }

        // The clock is asked while the days are counted and when the stamp is written.
        assertEquals(setOf("compute-test"), threads)
        assertTrue(file.readText().contains("BEGIN:VEVENT"))
    }

    @Test
    fun `the suggested name has the date`() {
        assertEquals("urlaubsplaner-2027-03-10.ics", exporter.suggestedFileName())
    }

    @Test
    fun `with granted days on planned parts are tentative and granted parts confirmed`() {
        runBlocking {
            repository.saveSettings(
                UserSettings(land = Bundesland.BAYERN, grantedDays = true),
            )
        }
        twoTrips()
        // Monday to Wednesday of the planned week are granted, Thursday and Friday are not.
        runBlocking {
            repository.setGranted((15..17).map { date("2027-03-$it") }, true)
        }

        runBlocking { exporter.save(TestDocuments.uriFor(file), IcsLanguage.GERMAN) }

        val text = file.readText()
        // The taken trip and the granted part are confirmed, the rest of the week is tentative.
        assertEquals(3, text.split("BEGIN:VEVENT").size - 1)
        assertEquals(2, text.split("STATUS:CONFIRMED").size - 1)
        assertEquals(1, text.split("STATUS:TENTATIVE").size - 1)
        assertEquals(3, runBlocking { exporter.tripCount() })
    }

    @Test
    fun `with granted days off the file is as before whatever is stored`() {
        land()
        twoTrips()
        runBlocking { repository.setGranted((15..17).map { date("2027-03-$it") }, true) }

        runBlocking { exporter.save(TestDocuments.uriFor(file), IcsLanguage.GERMAN) }

        val text = file.readText()
        assertEquals(2, text.split("BEGIN:VEVENT").size - 1)
        assertTrue(!text.contains("STATUS"))
    }

    @Test
    fun `a file address from the picker slot is refused and the file is untouched`() {
        land()
        twoTrips()
        file.writeText("keep me")

        assertThrows(java.io.FileNotFoundException::class.java) {
            runBlocking { exporter.save(Uri.fromFile(file), IcsLanguage.GERMAN) }
        }

        assertEquals("keep me", file.readText())
    }
}
