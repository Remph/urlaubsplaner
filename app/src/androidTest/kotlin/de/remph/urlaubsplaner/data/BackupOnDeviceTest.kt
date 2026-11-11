package de.remph.urlaubsplaner.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.remph.urlaubsplaner.FreshApp
import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.backup.BackupFile
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.settings.Notice
import de.remph.urlaubsplaner.settings.SettingsViewModel
import de.remph.urlaubsplaner.targetApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.MonthDay

/**
 * Export and import through a real [ContentResolver] and the real settings ViewModel, as the
 * system file picker would hand them a file. The file lives in the Downloads collection of
 * MediaStore: a provider of the system, no permission needed for the app's own files, and (like
 * the picker's providers) one that does not follow file semantics blindly, which is what the
 * "wt" mode of [BackupFileStore] is for.
 *
 * Not covered: the picker screen itself, which belongs to the system and cannot be driven
 * reliably from a test.
 */
@RunWith(AndroidJUnit4::class)
class BackupOnDeviceTest {
    @get:Rule
    val fresh = FreshApp()

    private val app = targetApp
    private val resolver: ContentResolver = app.contentResolver
    private val repository = app.repository
    private val created = mutableListOf<Uri>()
    private val viewModels = mutableListOf<SettingsViewModel>()

    private val data =
        BackupData(
            settings =
                UserSettings(
                    land = Bundesland.BAYERN,
                    optOuts = setOf(RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN),
                ),
            entitlements =
                listOf(
                    YearEntitlement(
                        2026,
                        VacationDays.of(30),
                        VacationDays.of(2.5),
                        MonthDay.of(4, 30),
                    ),
                    YearEntitlement(2027, VacationDays.of(28)),
                ),
            marks =
                mapOf(
                    LocalDate.of(2026, 7, 1) to DayMark.FULL,
                    LocalDate.of(2026, 7, 2) to DayMark.HALF,
                ),
        )

    @After
    fun cleanUp() {
        viewModels.forEach { it.viewModelScope.cancel() }
        created.forEach { runCatching { resolver.delete(it, null, null) } }
    }

    /** An empty file in Downloads, like the one the picker's "save as" creates. */
    private fun newFile(): Uri {
        val values =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    "urlaubsplaner-test-${System.nanoTime()}.json",
                )
                put(MediaStore.MediaColumns.MIME_TYPE, BackupFile.MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        assertNotNull("MediaStore refused to create a file in Downloads", uri)
        return uri!!.also { created += it }
    }

    private fun viewModel(): SettingsViewModel {
        var viewModel: SettingsViewModel? = null
        // A ViewModel belongs to the main thread; the tests then wait for it from this one.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel =
                SettingsViewModel(
                    repository,
                    app.backupStore,
                    app.calendarExporter,
                    app.clock,
                    app.backupStatusStore,
                )
        }
        return viewModel!!.also { viewModels += it }
    }

    private fun SettingsViewModel.awaitNotice(expected: Notice) {
        val end = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (state.value?.notice != expected) {
            check(System.currentTimeMillis() < end) {
                "Expected $expected, the notice is ${state.value?.notice}"
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun SettingsViewModel.awaitPendingImport() {
        val end = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (state.value?.pendingImport == null) {
            check(System.currentTimeMillis() < end) { "No import is waiting for the user's OK" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun fill() = runBlocking { repository.replaceAll(data) }

    private fun empty() = runBlocking(Dispatchers.IO) { app.database.clearAllTables() }

    private fun stored() = runBlocking { repository.readAll() }

    @Test
    fun `an export can be imported again on an empty app`() {
        val uri = newFile()
        val viewModel = viewModel()
        fill()

        viewModel.export(uri)
        viewModel.awaitNotice(Notice.Exported)
        viewModel.dismissNotice()
        empty()
        viewModel.import(uri)

        // Nothing to replace on an empty app, so there is no question and it is imported at once.
        viewModel.awaitNotice(Notice.Imported)
        assertEquals(data, stored())
    }

    @Test
    fun `importing over existing data asks first and replaces everything after the OK`() {
        val uri = newFile()
        val viewModel = viewModel()
        fill()
        viewModel.export(uri)
        viewModel.awaitNotice(Notice.Exported)
        viewModel.dismissNotice()
        // Something else is in the app now: this must be gone after the import.
        runBlocking {
            repository.saveSettings(UserSettings(land = Bundesland.HAMBURG))
            repository.deleteEntitlement(2026)
            repository.mark(LocalDate.of(2026, 8, 3), DayMark.FULL)
        }

        viewModel.import(uri)
        viewModel.awaitPendingImport()
        assertEquals(Bundesland.HAMBURG, stored().settings.land)

        viewModel.confirmImport()
        viewModel.awaitNotice(Notice.Imported)
        assertEquals(data, stored())
    }

    @Test
    fun `an export over a longer old file leaves no tail behind`() {
        val uri = newFile()
        resolver
            .openOutputStream(
                uri,
                "w",
            )!!
            .use { it.write(ByteArray(64 * 1024) { '#'.code.toByte() }) }

        runBlocking { app.backupStore.write(uri, data, LocalDate.of(2026, 10, 26)) }

        assertEquals(data, runBlocking { app.backupStore.read(uri) })
    }

    @Test
    fun `a file that is not a backup is refused and nothing changes`() {
        val uri = newFile()
        resolver.openOutputStream(uri, "wt")!!.use { it.write("shopping list: milk".toByteArray()) }
        val viewModel = viewModel()
        fill()

        viewModel.import(uri)

        viewModel.awaitNotice(Notice.ImportNotABackup)
        assertNull(viewModel.state.value?.pendingImport)
        assertEquals(data, stored())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 100L
    }
}
