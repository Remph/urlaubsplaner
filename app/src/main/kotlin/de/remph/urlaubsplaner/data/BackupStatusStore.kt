package de.remph.urlaubsplaner.data

import android.content.SharedPreferences
import androidx.core.content.edit
import de.remph.urlaubsplaner.core.backup.BackupStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DateTimeException
import java.time.LocalDate

/**
 * What this phone knows about backups, in the app's private preferences (two dates, so no
 * database). It stays out of Room and out of the backup file on purpose: the date of the last
 * backup describes this phone, and a restore must not count as a backup. Reading never fails on a
 * value it does not understand; the date is then simply unknown.
 */
class BackupStatusStore(
    private val preferences: SharedPreferences,
) {
    private val current = MutableStateFlow(read())

    val state: StateFlow<BackupStatus> = current

    /** "Sicherung speichern" worked on [day]. */
    fun recordBackup(day: LocalDate) = update { it.copy(lastBackup = day) }

    /** Days are marked on [day]; only the first call counts. */
    fun noteMarkedDays(day: LocalDate) {
        if (current.value.marksSince == null) update { it.copy(marksSince = day) }
    }

    private fun update(change: (BackupStatus) -> BackupStatus) {
        val changed = change(current.value)
        if (changed == current.value) return
        preferences.edit {
            putString(LAST_BACKUP, changed.lastBackup?.toString())
            putString(MARKS_SINCE, changed.marksSince?.toString())
        }
        current.value = changed
    }

    private fun read() =
        BackupStatus(
            lastBackup = date(LAST_BACKUP),
            marksSince = date(MARKS_SINCE),
        )

    private fun date(key: String): LocalDate? =
        try {
            preferences.getString(key, null)?.let(LocalDate::parse)
        } catch (_: DateTimeException) {
            null
        } catch (_: ClassCastException) {
            null
        }

    companion object {
        const val FILE_NAME = "backup_status"
        private const val LAST_BACKUP = "last_backup"
        private const val MARKS_SINCE = "marks_since"
    }
}
