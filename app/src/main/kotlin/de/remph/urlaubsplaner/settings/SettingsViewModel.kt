package de.remph.urlaubsplaner.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.remph.urlaubsplaner.core.backup.BackupException
import de.remph.urlaubsplaner.core.backup.BackupFile
import de.remph.urlaubsplaner.core.balance.AmountText
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.EntitlementSuggestion
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.ics.IcsLanguage
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.data.BackupFileStore
import de.remph.urlaubsplaner.data.BackupStatusStore
import de.remph.urlaubsplaner.data.UserDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/**
 * The settings page: the user's settings, the entitlement of one year, and the backup. Changes to
 * the settings are saved at once; the year form is saved with [saveEntitlement], because its
 * first content can be a suggestion that must not be stored without the user's say.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val repository: UserDataRepository,
    private val store: BackupFileStore,
    private val calendar: CalendarExporter,
    private val clock: Clock,
    private val backupStatus: BackupStatusStore,
    /** Called after a backup replaced all data, so the home screen widgets are drawn again. */
    private val onDataReplaced: () -> Unit = {},
) : ViewModel() {
    /** The year the page starts on, and the one the settings list shows. */
    val currentYear = LocalDate.now(clock).year
    private val firstYear = PublicHolidays.FIRST_SUPPORTED_YEAR
    private val lastYear = currentYear + 1

    private val selectedYear = MutableStateFlow(currentYear)
    private val edits = MutableStateFlow(Edits(currentYear))
    private val notice = MutableStateFlow<Notice?>(null)
    private val pendingImport = MutableStateFlow<ImportPlan?>(null)
    private val settingsLock = Mutex()

    /** Null until the stored settings are read, so the page does not flash the first-start view. */
    val state: StateFlow<SettingsUiState?> =
        selectedYear
            .flatMapLatest { year ->
                combine(
                    repository.observeSettings(),
                    repository.observeEntitlement(year),
                    repository.observeEntitlement(year - 1),
                    repository.observeMarks(
                        LocalDate.of(year - 1, 1, 1),
                        LocalDate.of(year - 1, 12, 31),
                    ),
                    repository.observeMarks(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)),
                ) { settings, stored, previous, previousMarks, yearMarks ->
                    val suggestion =
                        if (stored ==
                            null
                        ) {
                            suggestion(settings, year, previous, previousMarks)
                        } else {
                            null
                        }
                    YearBase(
                        year,
                        settings,
                        stored ?: suggestion,
                        isSuggestion =
                            stored == null && suggestion != null,
                        markedDays = yearMarks.size,
                    )
                }
            }.combine(edits) { base, typed -> base to typed.takeIf { it.year == base.year } }
            .combine(notice) { (base, edits), notice -> Triple(base, edits, notice) }
            .combine(pendingImport) { (base, edits, notice), plan ->
                buildState(base, edits ?: Edits(base.year), notice, plan)
            }.combine(backupStatus.state) { ui, status -> ui.copy(lastBackup = status.lastBackup) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** What the year before suggests for a year with nothing stored. */
    private fun suggestion(
        settings: UserSettings,
        year: Int,
        previous: YearEntitlement?,
        previousMarks: Map<LocalDate, DayMark>,
    ): YearEntitlement? {
        val check = settings.publicHolidayCheck()
        // The rules only know years from 2019; a year before that has nothing to suggest from.
        if (previous == null || check == null || year - 1 < firstYear) return null
        val balance =
            VacationCalculator(clock, settings.schedule, check).balance(previous, previousMarks)
        return EntitlementSuggestion.forYear(year, previous, balance, settings.suggestCarryOver)
    }

    private fun buildState(
        base: YearBase,
        edits: Edits,
        notice: Notice?,
        plan: ImportPlan?,
    ): SettingsUiState {
        val days = edits.days?.let { AmountText.parseDays(it) }
        // An empty Resturlaub field means none; only text that is not an amount is wrong.
        val carryOver =
            edits.carryOver?.let {
                if (it.isBlank()) {
                    VacationDays.ZERO
                } else {
                    AmountText.parseDays(
                        it,
                    )
                }
            }
        val expiry = edits.expiry?.let { AmountText.parseDayMonth(it) }
        val daysError = edits.days != null && days == null
        val carryOverError = edits.carryOver != null && carryOver == null
        val expiryError = edits.expiry != null && expiry == null
        val entitlement = base.entitlement
        val form =
            YearForm(
                base =
                    entitlement?.let {
                        YearForm.Base(
                            it.days,
                            it.carryOver,
                            it.carryOverExpiry,
                            it.carryOverExpiryYears,
                        )
                    },
                suggestedFrom = if (base.isSuggestion) base.year - 1 else null,
                typedDays = edits.days,
                typedCarryOver = edits.carryOver,
                typedExpiry = edits.expiry,
                typedExpiryYears = edits.expiryYears,
                daysError = daysError,
                carryOverError = carryOverError,
                expiryError = expiryError,
                // Days must exist: from the form, from what is stored, or typed in.
                canSave =
                    !daysError && !carryOverError && !expiryError &&
                        (edits.dirty || base.isSuggestion) &&
                        (days != null || entitlement != null),
            )
        return SettingsUiState(
            settings = base.settings,
            year = base.year,
            canGoBack = base.year > firstYear,
            canGoForward = base.year < lastYear,
            form = form,
            markedDays = base.markedDays,
            pendingImport = plan,
            notice = notice,
        )
    }

    // Settings: saved at once.

    fun chooseLand(land: Bundesland) = changeSettings { it.copy(land = land) }

    /** At least one working day must stay, a week without any is not a schedule. */
    fun setWorkingDay(
        day: DayOfWeek,
        works: Boolean,
    ) = changeSettings { settings ->
        val days = settings.schedule.workingDays.let { if (works) it + day else it - day }
        if (days.isEmpty()) {
            settings
        } else {
            settings.copy(
                schedule = settings.schedule.copy(workingDays = days),
            )
        }
    }

    /** [value] is none for a day off, half or one for a (half) working day. */
    fun setSpecialDay(
        day: MonthDay,
        value: VacationDays,
    ) = changeSettings { settings ->
        val specialDays =
            if (value == VacationDays.ONE) {
                settings.schedule.specialDays - day
            } else {
                settings.schedule.specialDays + (day to value)
            }
        settings.copy(schedule = settings.schedule.copy(specialDays = specialDays))
    }

    fun setExtra(
        extra: RegionalExtra,
        on: Boolean,
    ) = changeSettings { it.copy(extras = if (on) it.extras + extra else it.extras - extra) }

    /** [counts] false switches the holiday off for this user, which is what the opt-out stores. */
    fun setHolidayCounts(
        optOut: RegionalOptOut,
        counts: Boolean,
    ) = changeSettings {
        it.copy(
            optOuts = if (counts) it.optOuts - optOut else it.optOuts + optOut,
        )
    }

    fun setSuggestCarryOver(on: Boolean) = changeSettings { it.copy(suggestCarryOver = on) }

    fun setGrantedDays(on: Boolean) = changeSettings { it.copy(grantedDays = on) }

    /**
     * Reads the settings from the database, not from [state], which can be a moment behind: two
     * quick taps must both count. The lock keeps one change from overwriting another.
     */
    private fun changeSettings(change: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            settingsLock.withLock {
                val current = repository.observeSettings().first()
                val changed = change(current)
                if (changed != current) repository.saveSettings(changed)
            }
        }
    }

    // The year form.

    fun selectYear(year: Int) {
        if (year in firstYear..lastYear) selectedYear.value = year
    }

    fun editDays(text: String) = edit { it.copy(days = text) }

    fun editCarryOver(text: String) = edit { it.copy(carryOver = text) }

    fun editExpiry(text: String) = edit { it.copy(expiry = text) }

    /** 0 is the entitlement's own year, 1 the year after. */
    fun editExpiryYears(years: Int) {
        if (years in 0..YearEntitlement.MAX_EXPIRY_YEARS) edit { it.copy(expiryYears = years) }
    }

    private fun edit(change: (Edits) -> Edits) =
        edits.update { current ->
            val own = if (current.year == selectedYear.value) current else Edits(selectedYear.value)
            change(own)
        }

    fun saveEntitlement() {
        val current = state.value ?: return
        if (!current.form.canSave) return
        val form = current.form
        val edits = edits.value.takeIf { it.year == current.year } ?: Edits(current.year)
        val days = edits.days?.let { AmountText.parseDays(it) } ?: form.base?.days ?: return
        val carryOver =
            edits.carryOver?.let {
                if (it.isBlank()) {
                    VacationDays.ZERO
                } else {
                    AmountText.parseDays(
                        it,
                    )
                }
            }
                ?: form.base?.carryOver
                ?: VacationDays.ZERO
        val expiry =
            edits.expiry?.let { AmountText.parseDayMonth(it) }
                ?: form.base?.expiry
                ?: MonthDay.of(3, 31)
        val expiryYears = edits.expiryYears ?: form.base?.expiryYears ?: 0
        val saved = YearEntitlement(current.year, days, carryOver, expiry, expiryYears)
        viewModelScope.launch {
            repository.saveEntitlement(saved)
            // The typed text goes only once the stored values arrive, or the fields would be
            // empty for a moment. The timeout is a safety net, the wait normally takes a blink.
            withTimeoutOrNull(STORED_VALUE_WAIT_MS) {
                state.filterNotNull().first {
                    it.year == saved.year && !it.form.isSuggestion &&
                        it.form.base == YearForm.Base(days, carryOver, expiry, expiryYears)
                }
            }
            this@SettingsViewModel.edits.value = Edits(current.year)
            notice.value = Notice.EntitlementSaved
        }
    }

    /**
     * Removes the marked days of [year]. The screen has asked before; the entitlement stays. The
     * widget redraws by itself, it watches the table.
     */
    fun clearYear(year: Int) {
        if (year !in firstYear..lastYear) return
        viewModelScope.launch {
            repository.clearMarks(year)
            notice.value = Notice.YearCleared(year)
        }
    }

    // Calendar file (ICS).

    private val openRequests = Channel<Uri>(Channel.BUFFERED)

    /** Each address is a calendar file ready to open; the screen hands it to a calendar app. */
    val opens: Flow<Uri> = openRequests.receiveAsFlow()

    /** The number of events the file would hold, read when the page opens. */
    suspend fun calendarTripCount(): Int = calendar.tripCount()

    fun suggestedCalendarName(): String = calendar.suggestedFileName()

    fun saveCalendar(
        uri: Uri,
        language: IcsLanguage,
    ) {
        viewModelScope.launch {
            notice.value =
                try {
                    calendar.save(uri, language)
                    Notice.CalendarSaved
                } catch (_: IOException) {
                    Notice.ExportFailed
                } catch (_: SecurityException) {
                    Notice.ExportFailed
                }
        }
    }

    fun openCalendar(language: IcsLanguage) {
        viewModelScope.launch {
            try {
                calendar.openUri(language)?.let { openRequests.send(it) }
            } catch (_: IOException) {
                notice.value = Notice.ExportFailed
            }
        }
    }

    // Backup.

    fun suggestedBackupName(): String = BackupFile.fileName(LocalDate.now(clock))

    fun export(uri: Uri) {
        viewModelScope.launch {
            notice.value =
                try {
                    val today = LocalDate.now(clock)
                    store.write(uri, repository.readAll(), today)
                    // Only a file that was written counts; a restore never gets here.
                    backupStatus.recordBackup(today)
                    Notice.Exported
                } catch (_: IOException) {
                    Notice.ExportFailed
                } catch (_: SecurityException) {
                    Notice.ExportFailed
                }
        }
    }

    /**
     * Reads the file first and changes nothing. A fresh install has nothing to replace, so it is
     * imported at once; otherwise the screen asks, using [SettingsUiState.pendingImport].
     */
    fun import(uri: Uri) {
        viewModelScope.launch {
            val data =
                try {
                    store.read(uri)
                } catch (e: BackupException) {
                    notice.value = e.toNotice()
                    return@launch
                } catch (_: IOException) {
                    notice.value = Notice.ImportUnreadable
                    return@launch
                } catch (_: SecurityException) {
                    notice.value = Notice.ImportUnreadable
                    return@launch
                }
            val current = repository.readAll()
            if (current.isEmpty) {
                repository.replaceAll(data)
                onDataReplaced()
                notice.value = Notice.Imported
            } else {
                pendingImport.value = ImportPlan(current.summary(), data.summary(), data)
            }
        }
    }

    fun confirmImport() {
        val plan = pendingImport.value ?: return
        pendingImport.value = null
        viewModelScope.launch {
            repository.replaceAll(plan.data)
            onDataReplaced()
            edits.value = Edits(selectedYear.value)
            notice.value = Notice.Imported
        }
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    fun dismissNotice() {
        notice.value = null
    }

    private fun BackupException.toNotice(): Notice =
        when (this) {
            is BackupException.NotABackup -> Notice.ImportNotABackup
            is BackupException.NewerVersion -> Notice.ImportNewerVersion(found, supported)
            is BackupException.Damaged -> Notice.ImportDamaged(message.orEmpty())
        }

    private companion object {
        const val STORED_VALUE_WAIT_MS = 2_000L
    }

    /** The stored entitlement of [year], or the suggestion for it when nothing is stored. */
    private data class YearBase(
        val year: Int,
        val settings: UserSettings,
        val entitlement: YearEntitlement?,
        val isSuggestion: Boolean,
        val markedDays: Int,
    )

    /** What the user has typed for [year] and not saved; null means the field is untouched. */
    private data class Edits(
        val year: Int,
        val days: String? = null,
        val carryOver: String? = null,
        val expiry: String? = null,
        val expiryYears: Int? = null,
    ) {
        val dirty: Boolean
            get() = days != null || carryOver != null || expiry != null || expiryYears != null
    }
}
