package de.remph.urlaubsplaner.planner

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.backup.BackupHint
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlannerViewModelTest {
    private var fixture = PlannerFixture()
    private val viewModels = ViewModelStore()

    // Cleared first: that cancels the ViewModel's coroutines before the database closes.
    @After
    fun close() {
        viewModels.clear()
        fixture.close()
    }

    private fun viewModel(): PlannerViewModel {
        val model = fixture.viewModel()
        viewModels.put("planner", model)
        return model
    }

    private fun viewModelWith(clock: java.time.Clock) =
        PlannerViewModel(
            fixture.repository,
            lazyOf(fixture.schoolHolidays),
            clock,
            fixture.backupStatus,
        )

    /** Room reports from its own threads, so a value arrives a moment after the change. */
    private fun <T : Any> await(
        flow: kotlinx.coroutines.flow.Flow<T?>,
        until: (T) -> Boolean = { true },
    ): T = runBlocking { withTimeout(5_000) { flow.filterNotNull().first(until) } }

    @Test
    fun `the overview shows the current year with its balance and marks`() {
        fixture.fill()

        val view = await(viewModel().overview) { it.balance != null && it.marks.isNotEmpty() }

        assertEquals(2027, view.year)
        // 30 + 10 carried over, 2 days taken, 5 planned.
        assertEquals(VacationDays.of(33), view.balance?.remaining)
        assertEquals(fixture.date("2027-03-15"), view.nextTrip?.start)
    }

    @Test
    fun `the calendar is read once the overview is, before its tab opens`() {
        fixture.fill()
        val model = viewModel()
        await(model.overview) { it.balance != null }

        // Nothing collects the calendar here, as on the overview tab; its first value must come
        // anyway, or the calendar's first visit draws nothing and then pops in during the slide.
        // Read .value, not collect: collecting would itself start the calendar's flow.
        val view =
            runBlocking {
                withTimeout(5_000) {
                    var read = model.calendar.value
                    while (read == null) {
                        delay(10)
                        read = model.calendar.value
                    }
                    read
                }
            }

        assertEquals(2027, view?.year)
    }

    @Test
    fun `the numbers count up once per start of the app`() {
        val model = viewModel()

        assertTrue(model.takeIntro())
        assertFalse(model.takeIntro())
    }

    @Test
    fun `a number counts from zero to its value and shows exactly the value at the end`() {
        val thirtyThree = VacationDays.of(33)

        assertEquals(VacationDays.ZERO, thirtyThree.countedUp(0f))
        assertEquals(VacationDays.ofHalves(33), thirtyThree.countedUp(0.5f))
        assertEquals(thirtyThree, thirtyThree.countedUp(1f))
        assertEquals(VacationDays.ofHalves(-4), VacationDays.ofHalves(-9).countedUp(0.5f))
    }

    @Test
    fun `a fresh start has no Land and no balance`() {
        val view = await(viewModel().overview)

        assertFalse(view.hasLand)
        assertNull(view.balance)
    }

    @Test
    fun `marking days stores them and unmarking removes them`() {
        val model = viewModel()
        fixture.fill()
        val days = listOf(fixture.date("2027-03-22"), fixture.date("2027-03-23"))

        model.mark(days, DayMark.HALF)
        val marked = await(model.overview) { days.all { day -> it.markOn(day) == DayMark.HALF } }
        assertEquals(DayMark.HALF, marked.markOn(days[0]))

        model.unmark(days)
        val cleared = await(model.overview) { days.none { day -> it.markOn(day) != null } }
        assertNull(cleared.markOn(days[1]))
    }

    @Test
    fun `the calendar starts on this month in month mode`() {
        val model = viewModel()

        assertEquals(YearMonth.of(2027, 3), model.calendarMonth.value)
        assertEquals(CalendarMode.MONTH, model.calendarMode.value)
    }

    @Test
    fun `stepping moves a month in month mode and a year in year mode`() {
        val model = viewModel()

        model.step(1)
        assertEquals(YearMonth.of(2027, 4), model.calendarMonth.value)
        model.step(-1)
        model.step(-1)
        assertEquals(YearMonth.of(2027, 2), model.calendarMonth.value)

        model.showMode(CalendarMode.YEAR)
        model.step(1)
        assertEquals(YearMonth.of(2028, 2), model.calendarMonth.value)
    }

    @Test
    fun `stepping past the last year keeps the month instead of clamping it`() {
        val model = viewModel()
        model.showMode(CalendarMode.YEAR)
        model.showMonth(YearMonth.of(2028, 3))

        model.step(1)

        assertEquals(YearMonth.of(2028, 3), model.calendarMonth.value)
    }

    @Test
    fun `the calendar loads the year of the month it shows`() {
        fixture.fill()
        val model = viewModel()
        await(model.calendar) { it.year == 2027 && it.balance != null }

        model.showMonth(YearMonth.of(2028, 1))
        val next = await(model.calendar) { it.year == 2028 }

        assertEquals(2028, next.year)
        assertNull(next.balance)
    }

    @Test
    fun `the calendar never goes before 2019 or past next year`() {
        fixture.close()
        fixture = PlannerFixture(today = "2019-02-10")
        val model = viewModel()

        assertTrue(model.canStep(-1))
        model.step(-1)
        assertEquals(YearMonth.of(2019, 1), model.calendarMonth.value)
        assertFalse(model.canStep(-1))
        model.showMonth(YearMonth.of(2010, 5))
        assertEquals(YearMonth.of(2019, 1), model.calendarMonth.value)

        model.showMonth(YearMonth.of(2030, 5))
        assertEquals(YearMonth.of(2020, 12), model.calendarMonth.value)
        assertFalse(model.canStep(1))
        // The first year loads without asking the rules for 2018.
        model.showMonth(YearMonth.of(2019, 1))
        assertEquals(2019, await(model.calendar) { it.year == 2019 }.year)
    }

    @Test
    fun `no backup hint while nothing is marked, however long the app has run`() {
        val clock = SteppingClock(fixture.clock)
        val model = viewModelWith(clock)
        viewModels.put("planner", model)
        val seen = mutableListOf<Any?>()
        runBlocking {
            val job =
                kotlinx.coroutines.GlobalScope.launch {
                    model.backupHint.collect { seen += it }
                }
            clock.today = "2028-03-10"
            model.refreshToday()
            delay(200)
            job.cancel()
        }

        assertTrue(seen.all { it == null })
        assertNull(fixture.backupStatus.state.value.marksSince)
    }

    @Test
    fun `never saved days get the hint 14 days after the first look, and a backup ends it`() {
        fixture.fill()
        val clock = SteppingClock(fixture.clock)
        val model = viewModelWith(clock)
        viewModels.put("planner", model)
        val seen = mutableListOf<BackupHint?>()
        val job = kotlinx.coroutines.GlobalScope.launch { model.backupHint.collect { seen += it } }
        try {
            // The first look at the marked days only starts the wait.
            runBlocking {
                withTimeout(5_000) {
                    while (fixture.backupStatus.state.value.marksSince ==
                        null
                    ) {
                        delay(10)
                    }
                }
            }
            assertEquals(fixture.date("2027-03-10"), fixture.backupStatus.state.value.marksSince)
            assertNull(model.backupHint.value)

            clock.today = "2027-03-23"
            model.refreshToday()
            runBlocking { delay(200) }
            assertNull(model.backupHint.value)

            clock.today = "2027-03-24"
            model.refreshToday()
            assertEquals(BackupHint.Never, await(model.backupHint) { it == BackupHint.Never })

            fixture.backupStatus.recordBackup(fixture.date("2027-03-24"))
            runBlocking { withTimeout(5_000) { while (model.backupHint.value != null) delay(10) } }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `an old backup gives the months, with or without marked days`() {
        fixture.backupStatus.recordBackup(fixture.date("2026-10-01"))
        val model = viewModel()
        val job = kotlinx.coroutines.GlobalScope.launch { model.backupHint.collect {} }
        try {
            assertEquals(BackupHint.Old(5), await(model.backupHint))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `the overview follows the date after refreshToday`() {
        fixture.fill()
        val clock = SteppingClock(fixture.clock)
        val model = viewModelWith(clock)
        viewModels.put("planner", model)
        assertEquals(fixture.date("2027-03-10"), await(model.overview) { it.balance != null }.today)

        // Past Verfall day and into the next year while the app stayed in memory.
        clock.today = "2028-01-02"
        model.refreshToday()
        val next = await(model.overview) { it.year == 2028 }

        assertEquals(fixture.date("2028-01-02"), next.today)
        assertEquals(2028, model.currentYear)
        assertEquals(2029, model.lastYear)
    }

    @Test
    fun `marking with the switch stores the flag and without it keeps what the day has`() {
        val model = viewModel()
        fixture.fill()
        val day = fixture.date("2027-03-22")

        model.mark(listOf(day), DayMark.FULL, granted = true)
        await(model.overview) { it.markOn(day) == DayMark.FULL }
        assertEquals(setOf(day), runBlocking { fixture.repository.readAll() }.granted)

        // No switch (the setting is off): changing to a half day does not forget the approval.
        model.mark(listOf(day), DayMark.HALF)
        await(model.overview) { it.markOn(day) == DayMark.HALF }
        assertEquals(setOf(day), runBlocking { fixture.repository.readAll() }.granted)

        model.mark(listOf(day), DayMark.FULL, granted = false)
        await(model.overview) { it.markOn(day) == DayMark.FULL }
        assertEquals(emptySet<LocalDate>(), runBlocking { fixture.repository.readAll() }.granted)
    }

    @Test
    fun `showing a date opens its month and asks for the highlight`() {
        val model = viewModel()
        model.showMode(CalendarMode.YEAR)

        model.showDate(fixture.date("2027-03-26"))

        assertEquals(YearMonth.of(2027, 3), model.calendarMonth.value)
        assertEquals(CalendarMode.MONTH, model.calendarMode.value)
        assertEquals(fixture.date("2027-03-26"), model.highlight.value)

        model.highlightDone()

        assertNull(model.highlight.value)
    }

    @Test
    fun `a date outside the calendar opens nothing to highlight`() {
        val model = viewModel()

        model.showDate(fixture.date("2018-12-25"))

        assertNull(model.highlight.value)
    }

    @Test
    fun `removing days offers them for the undo, and the undo puts back mark and flag`() {
        val model = viewModel()
        fixture.fill()
        val granted = fixture.date("2027-03-22")
        val half = fixture.date("2027-03-23")
        runBlocking {
            fixture.repository.mark(granted, DayMark.FULL, granted = true)
            fixture.repository.mark(half, DayMark.HALF)
        }
        // The calendar shows another month than the removed days: the undo does not depend on it.
        model.showMonth(YearMonth.of(2027, 6))

        // A day that is not marked (the Wednesday) is not part of what is offered.
        model.unmark(listOf(granted, half, fixture.date("2027-03-24")))

        val removed = await(model.removed)
        assertEquals(mapOf(granted to DayMark.FULL, half to DayMark.HALF), removed.marks)
        assertEquals(setOf(granted), removed.granted)
        model.removalDone(removed)
        assertNull(model.removed.value)

        model.undoRemoval(removed)

        await(model.overview) { it.markOn(granted) != null && it.markOn(half) != null }
        val back =
            runBlocking {
                fixture.repository.observeMarkedDays(granted, half).first()
            }
        assertEquals(removed, back)
    }

    @Test
    fun `an older undo offer that ends leaves a newer removal waiting`() {
        val model = viewModel()
        fixture.fill()
        val first = fixture.date("2027-03-22")
        val second = fixture.date("2027-03-23")
        runBlocking {
            fixture.repository.mark(first, DayMark.FULL)
            fixture.repository.mark(second, DayMark.HALF)
        }
        model.unmark(listOf(first))
        val older = await(model.removed)

        // The second removal replaces the first offer; its snackbar ends only afterwards.
        model.unmark(listOf(second))
        val newer = await(model.removed) { it != older }
        model.removalDone(older)

        assertEquals(newer, model.removed.value)
        assertEquals(mapOf(second to DayMark.HALF), newer.marks)
    }

    @Test
    fun `removing days that are not marked offers no undo`() {
        val model = viewModel()
        fixture.fill()

        model.unmark(listOf(fixture.date("2027-03-24")))
        // Give the removal time to finish; nothing may arrive.
        runBlocking { delay(300) }

        assertNull(model.removed.value)
    }
}

/** A clock that tells the same time of day as [base] on a day the test can change. */
private class SteppingClock(
    private val base: java.time.Clock,
) : java.time.Clock() {
    var today: String =
        java.time.LocalDate
            .now(base)
            .toString()

    override fun getZone() = base.zone

    override fun withZone(zone: java.time.ZoneId) = SteppingClock(base.withZone(zone))

    override fun instant(): java.time.Instant =
        java.time.LocalDate
            .parse(today)
            .atTime(10, 0)
            .atZone(base.zone)
            .toInstant()
}
