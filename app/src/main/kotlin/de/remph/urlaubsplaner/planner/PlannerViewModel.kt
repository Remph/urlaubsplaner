package de.remph.urlaubsplaner.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.remph.urlaubsplaner.core.backup.BackupHint
import de.remph.urlaubsplaner.core.backup.BackupReminder
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.bridge.BridgeFinder
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHolidayData
import de.remph.urlaubsplaner.data.BackupStatusStore
import de.remph.urlaubsplaner.data.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarMode { MONTH, YEAR }

/**
 * The overview (always the current year) and the calendar (the year the user steps to), and the
 * actions on marked days. Both are a [YearView]; null until the stored data has been read, so a
 * screen does not flash "nothing entered" first.
 *
 * [schoolHolidays] is the bundled file, read once and only when the first view is built, off the
 * main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModel(
    private val repository: UserDataRepository,
    private val schoolHolidays: Lazy<SchoolHolidayData>,
    private val clock: Clock,
    private val backupStatus: BackupStatusStore,
) : ViewModel() {
    // The app can stay in memory for days, so "today" is read again when it comes back to the
    // front (refreshToday), not only once when this ViewModel is made.
    private val today = MutableStateFlow(LocalDate.now(clock))

    private var introTaken = false

    /** True once per start of the app: the overview's numbers count up only then. */
    fun takeIntro(): Boolean = !introTaken.also { introTaken = true }

    val currentYear: Int get() = today.value.year
    val firstYear = PublicHolidays.FIRST_SUPPORTED_YEAR
    val lastYear: Int get() = currentYear + 1

    private val firstMonth = YearMonth.of(firstYear, 1)
    private val lastMonth: YearMonth get() = YearMonth.of(lastYear, 12)

    private val shownMonth = MutableStateFlow(YearMonth.from(today.value))
    private val shownMode = MutableStateFlow(CalendarMode.MONTH)

    val overview: StateFlow<YearView?> = yearView(today.map { it.year }.distinctUntilChanged())

    val calendar: StateFlow<YearView?> =
        yearView(shownMonth.map { it.year }.distinctUntilChanged())

    /**
     * The quiet line about backups on the overview, null when there is nothing to remind of.
     * The first time marked days are seen it also starts the wait for the first hint (see
     * [BackupReminder]); the store keeps that day, so it happens once.
     */
    val backupHint: StateFlow<BackupHint?> =
        combine(
            backupStatus.state,
            repository.observeAnyMarkedDay(),
            today,
        ) { status, hasMarkedDays, now ->
            if (hasMarkedDays) backupStatus.noteMarkedDays(now)
            BackupReminder.hint(status, now, hasMarkedDays)
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The month the calendar shows, also while it shows the whole year. */
    val calendarMonth: StateFlow<YearMonth> = shownMonth

    val calendarMode: StateFlow<CalendarMode> = shownMode

    init {
        // Only a screen that collects the calendar starts reading its year, so the first visit to
        // the calendar tab drew nothing, then built the whole page in the middle of the tab slide.
        // Once the overview has its data (so the start of the app is not slowed) the calendar's
        // year is read too and kept; it follows the month the user steps to.
        viewModelScope.launch {
            overview.first { it != null }
            calendar.collect {}
        }
    }

    private val highlighted = MutableStateFlow<LocalDate?>(null)

    /**
     * The day whose tile plays its highlight once, null when there is none (or it has played).
     * Set by [showDate]; the tile clears it with [highlightDone], and so does the calendar when it
     * leaves the screen, so a later visit does not play it again.
     */
    val highlight: StateFlow<LocalDate?> = highlighted

    private val yearRowsExpanded = MutableStateFlow(false)

    /** Whether the year grid shows tall rows (48 dp) instead of the compact ones (28 dp). */
    val calendarYearRowsExpanded: StateFlow<Boolean> = yearRowsExpanded

    fun expandYearRows(expanded: Boolean) {
        yearRowsExpanded.value = expanded
    }

    /** Reads the date again, so the status is right after midnight or a new year. */
    fun refreshToday() {
        today.value = LocalDate.now(clock)
    }

    /** The calendar never goes below 2019 (the first year with holiday rules) or past next year. */
    fun showMonth(month: YearMonth) {
        shownMonth.value = month.coerceIn(firstMonth, lastMonth)
    }

    fun showMode(mode: CalendarMode) {
        shownMode.value = mode
    }

    /** Opens the month of [date] in the month view and asks its tile for the highlight. */
    fun showDate(date: LocalDate) {
        showMode(CalendarMode.MONTH)
        showMonth(YearMonth.from(date))
        // Out of the calendar's range there is no tile to light up.
        highlighted.value = date.takeIf { YearMonth.from(it) == shownMonth.value }
    }

    fun highlightDone() {
        highlighted.value = null
    }

    /**
     * One month in month mode, one year in year mode; [steps] is -1 or 1. Does nothing at the end
     * of the calendar: clamping would still move the month (March 2028 to December 2028 in year
     * mode), and a swipe, unlike the disabled arrow, can reach this.
     */
    fun step(steps: Long) {
        if (!canStep(steps)) return
        val month = shownMonth.value
        showMonth(
            if (shownMode.value ==
                CalendarMode.MONTH
            ) {
                month.plusMonths(steps)
            } else {
                month.plusYears(steps)
            },
        )
    }

    fun canStep(steps: Long): Boolean {
        val month = shownMonth.value
        val target =
            if (shownMode.value ==
                CalendarMode.MONTH
            ) {
                month.plusMonths(steps)
            } else {
                month.plusYears(steps)
            }
        return target in firstMonth..lastMonth
    }

    /**
     * Marks every day in [dates]; a range is turned into its costly days by the caller. [granted]
     * is what the day sheet's switch says; null (the switch is not shown: the setting is off, or
     * the days are not in the future) keeps the flag a day already has.
     */
    fun mark(
        dates: Collection<LocalDate>,
        mark: DayMark,
        granted: Boolean? = null,
    ) {
        if (dates.isEmpty()) return
        viewModelScope.launch {
            if (granted == null) {
                repository.markAllKeepingGranted(dates, mark)
            } else {
                repository.markAll(dates, mark, granted)
            }
        }
    }

    private val lastRemoved = MutableStateFlow<MarkedDays?>(null)

    /**
     * What the last [unmark] removed, until the offer to undo it is over ([removalDone]). Null when
     * nothing is waiting, also when the days were not marked at all.
     */
    val removed: StateFlow<MarkedDays?> = lastRemoved

    fun unmark(dates: Collection<LocalDate>) {
        if (dates.isEmpty()) return
        viewModelScope.launch {
            val days = repository.removeAll(dates)
            if (days.marks.isNotEmpty()) lastRemoved.value = days
        }
    }

    /**
     * The offer to undo [days] is over (taken, timed out or replaced). Until then it stays here, so a
     * rotation, which takes the snackbar with the old screen, offers it again. An older offer that
     * ends after a newer removal leaves the newer one waiting.
     */
    fun removalDone(days: MarkedDays) {
        lastRemoved.compareAndSet(days, null)
    }

    /** Puts back what an [unmark] removed: each day with its mark and its granted flag. */
    fun undoRemoval(days: MarkedDays) {
        if (days.marks.isEmpty()) return
        viewModelScope.launch { repository.restore(days) }
    }

    private fun yearView(years: Flow<Int>): StateFlow<YearView?> =
        years
            .flatMapLatest { year ->
                combine(
                    repository.observeSettings(),
                    repository.observeEntitlement(year),
                    repository.observeMarkedDays(
                        LocalDate.of(year - 1, 12, 1).coerceAtLeast(LocalDate.of(firstYear, 1, 1)),
                        LocalDate.of(year + 1, 12, 31),
                    ),
                    today,
                ) { settings, entitlement, marked, now ->
                    val check = settings.publicHolidayCheck()
                    YearView(
                        year = year,
                        today = now,
                        settings = settings,
                        entitlement = entitlement,
                        marks = marked.marks,
                        granted = marked.granted,
                        calculator =
                            check?.let {
                                VacationCalculator(
                                    clock,
                                    settings.schedule,
                                    it,
                                )
                            },
                        schoolData = schoolHolidays.value,
                        bridgeFinder = check?.let { BridgeFinder(clock, settings.schedule, it) },
                    )
                }
            }.flowOn(Dispatchers.Default)
            .map<YearView, YearView?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
