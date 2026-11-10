package de.remph.urlaubsplaner.planner

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHolidayData
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.data.BackupStatusStore
import de.remph.urlaubsplaner.data.UrlaubsplanerDatabase
import de.remph.urlaubsplaner.data.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * An in-memory database, a fixed clock and a small school holiday file, for the tests of the
 * overview and the calendar. [today] is Wednesday 10 March 2027; the file covers 2027 only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlannerFixture(
    today: String = "2027-03-10",
) {
    val clock: Clock = Clock.fixed(Instant.parse("${today}T10:00:00Z"), ZoneOffset.UTC)
    val database: UrlaubsplanerDatabase

    // Room's reads run here, so close() can wait for one that is still running: a test that ends
    // right after a write (the undo) left a cancelled view model's read on the closed database.
    private val queries = DrainableExecutor()

    val repository: UserDataRepository
    val schoolHolidays =
        SchoolHolidayData.parse(
            """
            format: 1
            version: 2026-10-25
            covers: 2027-01-01..2027-12-31
            NW;2027-03-29;2027-04-10;Osterferien;Easter holidays
            NW;2027-07-19;2027-08-31;Sommerferien;Summer holidays
            """.trimIndent(),
        )

    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, UrlaubsplanerDatabase::class.java)
                .setQueryExecutor(queries)
                .build()
        repository = UserDataRepository(database)
    }

    // Every view model is cleared in close(), before the database goes: one that is left running
    // queries the closed database when Room's invalidation tracker fires, and the exception then
    // fails whichever test runs next in the same JVM.
    private val viewModels = ViewModelStore()

    /** Fresh preferences per fixture: the app's own would carry the date of one test into the next. */
    val backupStatus =
        BackupStatusStore(
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("backup-status-${System.nanoTime()}", Context.MODE_PRIVATE),
        )

    fun viewModel() =
        PlannerViewModel(repository, lazyOf(schoolHolidays), clock, backupStatus).also {
            viewModels.put("planner${viewModels.keys().size}", it)
        }

    /** Nordrhein-Westfalen, 30 days and 10 carried over until 31 March 2027, a few marks. */
    fun fill() =
        runBlocking {
            repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN))
            repository.saveEntitlement(
                YearEntitlement(2027, VacationDays.of(30), VacationDays.of(10), MonthDay.of(3, 31)),
            )
            // Taken: Monday and Tuesday before today. Planned: the week of 15 March.
            repository.markAll(listOf(date("2027-03-08"), date("2027-03-09")), DayMark.FULL)
            repository.markAll((15..19).map { date("2027-03-$it") }, DayMark.FULL)
        }

    fun date(text: String): LocalDate = LocalDate.parse(text)

    fun close() {
        viewModels.clear()
        queries.awaitIdle()
        database.close()
        queries.shutdown()
    }
}

/** A small thread pool that can wait until none of its tasks is running or waiting. */
private class DrainableExecutor : Executor {
    private val pool = Executors.newFixedThreadPool(4)
    private val pending = AtomicInteger()

    override fun execute(task: Runnable) {
        pending.incrementAndGet()
        try {
            pool.execute {
                try {
                    task.run()
                } finally {
                    pending.decrementAndGet()
                }
            }
        } catch (e: RejectedExecutionException) {
            pending.decrementAndGet()
            throw e
        }
    }

    fun awaitIdle(timeoutMillis: Long = 5_000) {
        val until = System.currentTimeMillis() + timeoutMillis
        while (pending.get() > 0 && System.currentTimeMillis() < until) Thread.sleep(5)
    }

    fun shutdown() = pool.shutdown()
}
