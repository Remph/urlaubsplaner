package de.remph.urlaubsplaner.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.backup.BackupFile
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.data.BackupFileStore
import de.remph.urlaubsplaner.data.BackupStatusStore
import de.remph.urlaubsplaner.data.CalendarFileStore
import de.remph.urlaubsplaner.data.UrlaubsplanerDatabase
import de.remph.urlaubsplaner.data.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset

/** The settings page's logic against a real in-memory database, on the JVM through Robolectric. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    private lateinit var context: Context
    private lateinit var database: UrlaubsplanerDatabase
    private lateinit var repository: UserDataRepository
    private lateinit var file: File
    private lateinit var backupStatus: BackupStatusStore

    // 15 January 2027: the current year is 2027, so 2026 is the year before.
    private val clock = Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC)

    // Cleared after each test: that cancels the ViewModel's coroutines before the database closes.
    private val viewModels = ViewModelStore()

    @Before
    fun open() {
        // Not reset afterwards: resetting while a late Room callback still runs fails, and the
        // ViewModels are cleared after each test anyway.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, UrlaubsplanerDatabase::class.java).build()
        repository = UserDataRepository(database)
        file = File.createTempFile("backup", ".json")
        backupStatus = BackupStatusStore(context.getSharedPreferences("backup-status-test", 0))
    }

    @After
    fun close() {
        viewModels.clear()
        database.close()
        file.delete()
    }

    private var created = 0

    private fun viewModel(): SettingsViewModel {
        val viewModel =
            SettingsViewModel(
                repository,
                BackupFileStore(context.contentResolver),
                CalendarExporter(repository, CalendarFileStore(context), clock),
                clock,
                backupStatus,
            )
        // The store clears it after the test, which cancels its viewModelScope.
        viewModels.put("settings-${created++}", viewModel)
        return viewModel
    }

    /** Waits for the first state that [matches]; Room reports changes from its own threads. */
    private fun SettingsViewModel.await(matches: (SettingsUiState) -> Boolean = { true }) =
        runBlocking { withTimeout(5_000) { state.filterNotNull().first(matches) } }

    private fun SettingsViewModel.land(land: Bundesland = Bundesland.NORDRHEIN_WESTFALEN) {
        // Settings are changed from the loaded state, as the page only shows after loading.
        await()
        chooseLand(land)
        await { it.settings.land == land }
    }

    private val uri get() = Uri.fromFile(file)

    @Test
    fun `a fresh start has no Land, the current year and nothing to save`() {
        val state = viewModel().await()

        assertNull(state.settings.land)
        assertEquals(2027, state.year)
        assertNull(state.form.base)
        assertFalse(state.form.canSave)
        assertFalse(state.settings.suggestCarryOver)
    }

    @Test
    fun `choosing a Land saves it`() {
        val vm = viewModel()
        vm.await()
        vm.chooseLand(Bundesland.BAYERN)

        assertEquals(Bundesland.BAYERN, vm.await { it.settings.land != null }.settings.land)
        assertEquals(Bundesland.BAYERN, runBlocking { repository.readAll() }.settings.land)
    }

    @Test
    fun `the years run from 2019 to next year`() {
        val vm = viewModel()
        assertTrue(vm.await().canGoBack)
        assertTrue(vm.await().canGoForward)

        vm.selectYear(2028)
        assertTrue(vm.await { it.year == 2028 }.let { !it.canGoForward })

        vm.selectYear(2029)
        assertEquals(2028, vm.await().year)

        vm.selectYear(2019)
        assertFalse(vm.await { it.year == 2019 }.canGoBack)

        vm.selectYear(2018)
        assertEquals(2019, vm.await().year)
    }

    @Test
    fun `typed amounts are saved with the half days and the Verfall day`() {
        val vm = viewModel()
        vm.land()
        vm.editDays("28,5")
        vm.editCarryOver("2")
        vm.editExpiry("30.4.")

        // canSave is already true after the first edit, so wait until the last one has landed.
        assertTrue(vm.await { it.form.typedExpiry == "30.4." && it.form.canSave }.form.canSave)
        vm.saveEntitlement()

        val state = vm.await { it.notice == Notice.EntitlementSaved }
        assertEquals(
            YearEntitlement(2027, VacationDays.of(28.5), VacationDays.of(2), MonthDay.of(4, 30)),
            runBlocking { repository.observeEntitlement(2027).first() },
        )
        assertEquals(VacationDays.of(28.5), state.form.base?.days)
        assertNull(state.form.typedDays)
        assertFalse(state.form.canSave)
    }

    @Test
    fun `the Resturlaub can be set to expire in the year after`() {
        val vm = viewModel()
        vm.land()
        vm.editDays("30")
        vm.editCarryOver("5")
        vm.editExpiry("31.3.")
        vm.editExpiryYears(1)

        // Same as above: canSave is true before the year choice is applied.
        assertEquals(
            1,
            vm.await { it.form.typedExpiryYears == 1 && it.form.canSave }.form.typedExpiryYears,
        )
        vm.saveEntitlement()

        val state = vm.await { it.notice == Notice.EntitlementSaved }
        assertEquals(
            YearEntitlement(
                2027,
                VacationDays.of(30),
                VacationDays.of(5),
                MonthDay.of(3, 31),
                carryOverExpiryYears = 1,
            ),
            runBlocking { repository.observeEntitlement(2027).first() },
        )
        assertEquals(1, state.form.base?.expiryYears)
        assertNull(state.form.typedExpiryYears)
    }

    @Test
    fun `only the year choice alone is enough to save, and a year further away is ignored`() {
        runBlocking {
            repository.saveEntitlement(YearEntitlement(2027, VacationDays.of(30)))
        }
        val vm = viewModel()
        vm.land()
        vm.await { it.form.base != null && !it.form.canSave }

        vm.editExpiryYears(2)
        assertNull(vm.await().form.typedExpiryYears)

        vm.editExpiryYears(1)
        assertTrue(vm.await { it.form.canSave }.form.canSave)
        vm.saveEntitlement()
        vm.await { it.notice == Notice.EntitlementSaved }
        assertEquals(
            1,
            runBlocking { repository.observeEntitlement(2027).first() }?.carryOverExpiryYears,
        )
    }

    @Test
    fun `a new year is suggested with the expiry year of the year before`() {
        runBlocking {
            repository.saveEntitlement(
                YearEntitlement(2026, VacationDays.of(30), carryOverExpiryYears = 1),
            )
        }
        val vm = viewModel()
        vm.land()

        val form = vm.await { it.form.suggestedFrom != null }.form

        assertEquals(1, form.base?.expiryYears)
    }

    @Test
    fun `an empty Resturlaub field means none`() {
        val vm = viewModel()
        vm.land()
        vm.editDays("30")
        vm.editCarryOver("")
        vm.await { it.form.typedCarryOver == "" && it.form.canSave }
        vm.saveEntitlement()
        vm.await { it.notice == Notice.EntitlementSaved }

        val saved = runBlocking { repository.observeEntitlement(2027).first() }
        assertEquals(VacationDays.ZERO, saved?.carryOver)
    }

    @Test
    fun `text that is not an amount or a day blocks saving and names the field`() {
        val vm = viewModel()
        vm.land()
        vm.editDays("12,3")
        vm.editCarryOver("viel")
        vm.editExpiry("31.04.")

        // All three edits must have landed, or the state is read between two of them.
        val form =
            vm
                .await { it.form.daysError && it.form.carryOverError && it.form.expiryError }
                .form
        assertTrue(form.daysError)
        assertTrue(form.carryOverError)
        assertTrue(form.expiryError)
        assertFalse(form.canSave)
        vm.saveEntitlement()
        assertNull(runBlocking { repository.observeEntitlement(2027).first() })
    }

    @Test
    fun `days are required, a form with only a Resturlaub cannot be saved`() {
        val vm = viewModel()
        vm.land()
        vm.editCarryOver("3")

        assertFalse(vm.await { it.form.typedCarryOver != null }.form.canSave)
    }

    private fun fillYear2026WithFourMarkedDays() =
        runBlocking {
            repository.saveEntitlement(
                YearEntitlement(2026, VacationDays.of(30), VacationDays.of(1), MonthDay.of(6, 30)),
            )
            // Monday to Thursday in July, no holidays in Nordrhein-Westfalen: 4 days taken.
            for (day in 6..9) repository.mark(LocalDate.of(2026, 7, day), DayMark.FULL)
        }

    @Test
    fun `a year with nothing stored suggests the days and the Verfall day of the year before`() {
        fillYear2026WithFourMarkedDays()
        val vm = viewModel()
        vm.land()

        val form = vm.await { it.form.suggestedFrom != null }.form

        assertEquals(2026, form.suggestedFrom)
        assertEquals(VacationDays.of(30), form.base?.days)
        assertEquals(MonthDay.of(6, 30), form.base?.expiry)
        // Off by default: the Resturlaub starts at zero.
        assertEquals(VacationDays.ZERO, form.base?.carryOver)
        assertTrue(form.canSave)
        // Only a suggestion: nothing is stored until Speichern.
        assertNull(runBlocking { repository.observeEntitlement(2027).first() })
    }

    @Test
    fun `with the option on the unused days of the year before are suggested as Resturlaub`() {
        fillYear2026WithFourMarkedDays()
        val vm = viewModel()
        vm.land()

        vm.setSuggestCarryOver(true)
        val form = vm.await { it.settings.suggestCarryOver && it.form.suggestedFrom != null }.form

        // The 1 day of Resturlaub lapsed on 30 June unused, so what is left is 30 - 4 = 26.
        assertEquals(VacationDays.of(26), form.base?.carryOver)
        vm.saveEntitlement()
        vm.await { it.notice == Notice.EntitlementSaved }
        assertEquals(
            VacationDays.of(26),
            runBlocking { repository.observeEntitlement(2027).first() }?.carryOver,
        )
    }

    @Test
    fun `the carry-over option is saved at once, can be switched off again, and stores no year`() {
        fillYear2026WithFourMarkedDays()
        val vm = viewModel()
        vm.land()

        vm.setSuggestCarryOver(true)
        vm.await { it.settings.suggestCarryOver && it.form.suggestedFrom != null }

        // The suggestion with Resturlaub is shown, but nothing is stored for the new year yet.
        assertTrue(runBlocking { repository.observeSettings().first() }.suggestCarryOver)
        assertNull(runBlocking { repository.observeEntitlement(2027).first() })

        vm.setSuggestCarryOver(false)
        val form = vm.await { !it.settings.suggestCarryOver && it.form.suggestedFrom != null }.form

        assertFalse(runBlocking { repository.observeSettings().first() }.suggestCarryOver)
        assertEquals(VacationDays.ZERO, form.base?.carryOver)
    }

    @Test
    fun `a stored year is shown as it is, not replaced by a suggestion`() {
        fillYear2026WithFourMarkedDays()
        runBlocking {
            repository.saveEntitlement(YearEntitlement(2027, VacationDays.of(24)))
        }
        val vm = viewModel()
        vm.land()

        val form = vm.await { it.form.base != null }.form

        assertNull(form.suggestedFrom)
        assertEquals(VacationDays.of(24), form.base?.days)
    }

    @Test
    fun `typed text survives a change of the settings and goes away with a saved form`() {
        val vm = viewModel()
        vm.land()
        vm.editDays("26")

        vm.setSuggestCarryOver(true)
        val state = vm.await { it.settings.suggestCarryOver }

        assertEquals("26", state.form.typedDays)
    }

    @Test
    fun `the last working day cannot be switched off`() {
        val vm = viewModel()
        vm.land()
        for (day in DayOfWeek.entries) vm.setWorkingDay(day, false)

        val days =
            vm
                .await { it.settings.schedule.workingDays.size == 1 }
                .settings.schedule.workingDays
        assertEquals(1, days.size)

        vm.setWorkingDay(DayOfWeek.SUNDAY, true)
        assertTrue(
            DayOfWeek.SUNDAY in
                vm
                    .await { DayOfWeek.SUNDAY in it.settings.schedule.workingDays }
                    .settings.schedule.workingDays,
        )
    }

    @Test
    fun `Heiligabend can be a half day or a day off and back to a whole day`() {
        val vm = viewModel()
        vm.land()

        vm.setSpecialDay(WorkSchedule.HEILIGABEND, VacationDays.HALF)
        assertEquals(
            mapOf(WorkSchedule.HEILIGABEND to VacationDays.HALF),
            vm
                .await {
                    it.settings.schedule.specialDays
                        .isNotEmpty()
                }.settings.schedule.specialDays,
        )

        vm.setSpecialDay(WorkSchedule.HEILIGABEND, VacationDays.ZERO)
        vm.setSpecialDay(WorkSchedule.SILVESTER, VacationDays.HALF)
        val both =
            vm
                .await { it.settings.schedule.specialDays.size == 2 }
                .settings.schedule.specialDays
        assertEquals(VacationDays.ZERO, both[WorkSchedule.HEILIGABEND])

        vm.setSpecialDay(WorkSchedule.HEILIGABEND, VacationDays.ONE)
        assertEquals(
            setOf(WorkSchedule.SILVESTER),
            vm
                .await { WorkSchedule.HEILIGABEND !in it.settings.schedule.specialDays }
                .settings.schedule.specialDays.keys,
        )
    }

    @Test
    fun `the Bayern switch stores the opposite, an opt-out`() {
        val vm = viewModel()
        vm.land(Bundesland.BAYERN)

        vm.setHolidayCounts(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN, false)
        assertEquals(
            setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN),
            vm.await { it.settings.optOuts.isNotEmpty() }.settings.optOuts,
        )

        vm.setHolidayCounts(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN, true)
        assertTrue(
            vm
                .await { it.settings.optOuts.isEmpty() }
                .settings.optOuts
                .isEmpty(),
        )
    }

    @Test
    fun `regional extras are switched on and off`() {
        val vm = viewModel()
        vm.land(Bundesland.BAYERN)

        vm.setExtra(RegionalExtra.AUGSBURGER_FRIEDENSFEST, true)
        assertTrue(
            vm
                .await { it.settings.extras.isNotEmpty() }
                .settings.extras
                .isNotEmpty(),
        )

        vm.setExtra(RegionalExtra.AUGSBURGER_FRIEDENSFEST, false)
        assertTrue(
            vm
                .await { it.settings.extras.isEmpty() }
                .settings.extras
                .isEmpty(),
        )
    }

    // Backup.

    private val someData =
        BackupData(
            settings = UserSettings(land = Bundesland.HAMBURG),
            entitlements = listOf(YearEntitlement(2026, VacationDays.of(30))),
            marks = mapOf(LocalDate.of(2026, 7, 1) to DayMark.FULL),
        )

    private fun writeBackupFile(data: BackupData) =
        file.writeText(BackupFile.encode(data, LocalDate.of(2027, 1, 1)))

    @Test
    fun `the suggested backup name has today's date`() {
        assertEquals("urlaubsplaner-2027-01-15.json", viewModel().suggestedBackupName())
    }

    @Test
    fun `export writes a backup of everything`() {
        runBlocking { repository.replaceAll(someData) }
        val vm = viewModel()

        vm.export(uri)

        vm.await { it.notice == Notice.Exported }
        assertEquals(someData, BackupFile.decode(file.readText()))
    }

    @Test
    fun `a saved backup is remembered with today's date, a failed one is not`() {
        runBlocking { repository.replaceAll(someData) }
        val vm = viewModel()
        assertNull(vm.await().lastBackup)

        vm.export(Uri.fromFile(File(file.parentFile, "no-such-folder/backup.json")))
        vm.await { it.notice == Notice.ExportFailed }
        assertNull(backupStatus.state.value.lastBackup)

        vm.dismissNotice()
        vm.export(uri)

        assertEquals(LocalDate.of(2027, 1, 15), vm.await { it.lastBackup != null }.lastBackup)
        assertEquals(LocalDate.of(2027, 1, 15), backupStatus.state.value.lastBackup)
    }

    @Test
    fun `restoring a backup does not count as a backup`() {
        runBlocking { repository.replaceAll(someData) }
        writeBackupFile(someData)
        val vm = viewModel()

        vm.import(uri)
        vm.await { it.pendingImport != null }
        vm.confirmImport()
        vm.await { it.notice == Notice.Imported }

        assertNull(vm.await().lastBackup)
        assertNull(backupStatus.state.value.lastBackup)
    }

    @Test
    fun `an export to a place that cannot be written says so`() {
        val vm = viewModel()

        vm.export(Uri.fromFile(File(file.parentFile, "no-such-folder/backup.json")))

        vm.await { it.notice == Notice.ExportFailed }
    }

    @Test
    fun `import on a fresh install needs no question`() {
        writeBackupFile(someData)
        val vm = viewModel()

        vm.import(uri)

        val state = vm.await { it.notice == Notice.Imported }
        assertNull(state.pendingImport)
        assertEquals(someData, runBlocking { repository.readAll() })
    }

    @Test
    fun `import over existing data asks first and changes nothing until confirmed`() {
        val existing =
            BackupData(
                settings = UserSettings(land = Bundesland.BAYERN),
                marks = mapOf(LocalDate.of(2026, 8, 3) to DayMark.HALF),
            )
        runBlocking { repository.replaceAll(existing) }
        writeBackupFile(someData)
        val vm = viewModel()

        vm.import(uri)

        val plan = vm.await { it.pendingImport != null }.pendingImport
        assertEquals(existing.summary(), plan?.current)
        assertEquals(someData.summary(), plan?.file)
        assertEquals(existing, runBlocking { repository.readAll() })

        vm.confirmImport()
        val state = vm.await { it.notice == Notice.Imported }
        assertNull(state.pendingImport)
        assertEquals(someData, runBlocking { repository.readAll() })
    }

    @Test
    fun `cancelling an import keeps everything`() {
        runBlocking { repository.replaceAll(someData.copy(marks = emptyMap())) }
        writeBackupFile(someData)
        val vm = viewModel()
        vm.import(uri)
        vm.await { it.pendingImport != null }

        vm.cancelImport()

        assertNull(vm.await { it.pendingImport == null }.pendingImport)
        assertEquals(someData.copy(marks = emptyMap()), runBlocking { repository.readAll() })
    }

    @Test
    fun `a file that is not a backup is refused with its own message and nothing changes`() {
        runBlocking { repository.replaceAll(someData) }
        file.writeText("hello")
        val vm = viewModel()

        vm.import(uri)

        assertEquals(Notice.ImportNotABackup, vm.await { it.notice != null }.notice)
        assertEquals(someData, runBlocking { repository.readAll() })
    }

    @Test
    fun `a newer backup and a damaged one get their own messages`() {
        val vm = viewModel()

        file.writeText("""{"app": "urlaubsplaner", "formatVersion": 99}""")
        vm.import(uri)
        assertEquals(
            Notice.ImportNewerVersion(found = 99, supported = BackupFile.VERSION),
            vm.await { it.notice != null }.notice,
        )
        vm.dismissNotice()

        file.writeText("""{"app": "urlaubsplaner", "formatVersion": 1}""")
        vm.import(uri)
        val notice = vm.await { it.notice is Notice.ImportDamaged }.notice
        assertNotNull((notice as Notice.ImportDamaged).detail)
    }

    @Test
    fun `a file that cannot be opened is unreadable, not a crash`() {
        val vm = viewModel()

        vm.import(Uri.fromFile(File(file.parentFile, "gone.json")))

        assertEquals(Notice.ImportUnreadable, vm.await { it.notice != null }.notice)
    }

    @Test
    fun `the state counts the marked days of the shown year only`() {
        runBlocking {
            repository.mark(LocalDate.of(2026, 12, 31), DayMark.FULL)
            repository.mark(LocalDate.of(2027, 1, 4), DayMark.FULL)
            repository.mark(LocalDate.of(2027, 8, 2), DayMark.HALF)
        }
        val vm = viewModel()

        assertEquals(2, vm.await { it.year == 2027 }.markedDays)

        vm.selectYear(2026)
        assertEquals(1, vm.await { it.year == 2026 }.markedDays)
    }

    @Test
    fun `clearing a year removes only its marks and keeps the entitlement, then says so`() {
        runBlocking {
            repository.saveEntitlement(YearEntitlement(2027, VacationDays.of(30)))
            repository.saveEntitlement(YearEntitlement(2026, VacationDays.of(28)))
            repository.mark(LocalDate.of(2026, 8, 3), DayMark.FULL)
            repository.mark(LocalDate.of(2027, 1, 4), DayMark.FULL)
            repository.mark(LocalDate.of(2027, 8, 2), DayMark.HALF)
        }
        val vm = viewModel()
        vm.await { it.markedDays == 2 }

        vm.clearYear(2027)

        vm.await { it.notice == Notice.YearCleared(2027) && it.markedDays == 0 }
        val rest = runBlocking { repository.readAll() }
        assertEquals(mapOf(LocalDate.of(2026, 8, 3) to DayMark.FULL), rest.marks)
        assertEquals(setOf(2026, 2027), rest.entitlements.map { it.year }.toSet())
    }

    @Test
    fun `a year outside the supported range is not cleared`() {
        runBlocking { repository.mark(LocalDate.of(2018, 5, 1), DayMark.FULL) }
        val vm = viewModel()
        vm.await()

        vm.clearYear(2018)

        assertNull(vm.await().notice)
        assertEquals(1, runBlocking { repository.readAll() }.marks.size)
    }

    @Test
    fun `granted days are off until turned on, and the choice is stored`() {
        val vm = viewModel()
        assertFalse(vm.await { true }.settings.grantedDays)

        vm.setGrantedDays(true)
        vm.await { it.settings.grantedDays }
        assertTrue(runBlocking { repository.observeSettings().first() }.grantedDays)

        vm.setGrantedDays(false)
        vm.await { !it.settings.grantedDays }
        assertFalse(runBlocking { repository.observeSettings().first() }.grantedDays)
    }
}
