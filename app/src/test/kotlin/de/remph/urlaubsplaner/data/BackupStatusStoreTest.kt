package de.remph.urlaubsplaner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.backup.BackupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupStatusStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences =
        context.getSharedPreferences(
            "backup-status-store-test",
            Context.MODE_PRIVATE,
        )

    private fun store() = BackupStatusStore(preferences)

    @Test
    fun `a new phone knows nothing`() {
        assertEquals(BackupStatus(), store().state.value)
    }

    @Test
    fun `the dates are kept for the next start`() {
        val first = store()
        first.recordBackup(LocalDate.of(2026, 11, 3))
        first.noteMarkedDays(LocalDate.of(2026, 10, 1))

        val later = store().state.value

        assertEquals(LocalDate.of(2026, 11, 3), later.lastBackup)
        assertEquals(LocalDate.of(2026, 10, 1), later.marksSince)
    }

    @Test
    fun `only the first day with marked days counts`() {
        val store = store()

        store.noteMarkedDays(LocalDate.of(2026, 10, 1))
        store.noteMarkedDays(LocalDate.of(2026, 12, 1))

        assertEquals(LocalDate.of(2026, 10, 1), store.state.value.marksSince)
    }

    @Test
    fun `the last backup is replaced by a newer one`() {
        val store = store()

        store.recordBackup(LocalDate.of(2026, 5, 1))
        store.recordBackup(LocalDate.of(2026, 11, 3))

        assertEquals(LocalDate.of(2026, 11, 3), store().state.value.lastBackup)
    }

    @Test
    fun `a value that is not a date is read as unknown`() {
        preferences
            .edit()
            .putString(
                "last_backup",
                "yesterday",
            ).putString("marks_since", "2026-13-45")
            .commit()

        assertNull(store().state.value.lastBackup)
        assertNull(store().state.value.marksSince)
    }

    @Test
    fun `a value of the wrong kind is read as unknown too`() {
        preferences.edit().putInt("last_backup", 5).commit()

        assertNull(store().state.value.lastBackup)
    }
}
