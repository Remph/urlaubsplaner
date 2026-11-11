package de.remph.urlaubsplaner.core.backup

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What this phone knows about backups. It is a fact about the phone, not user data: it is kept
 * outside the database and is not part of a backup file (a restore does not count as a backup).
 *
 * [lastBackup] is the day "Sicherung speichern" last succeeded. [marksSince] is the first day the
 * app saw marked days; it starts the wait for the first hint, because the database does not
 * remember when a day was marked.
 */
data class BackupStatus(
    val lastBackup: LocalDate? = null,
    val marksSince: LocalDate? = null,
)

/** The one quiet line on the Übersicht. */
sealed interface BackupHint {
    /** Days are marked and no backup was ever saved. */
    data object Never : BackupHint

    /** The last backup is [months] whole months old (91 days can be just 2 months). */
    data class Old(
        val months: Int,
    ) : BackupHint
}

/**
 * When to remind the user to save a backup. There is no notification: the answer only decides
 * whether a line is shown. Android's own backup is off, so a lost or replaced phone loses the data.
 */
object BackupReminder {
    /** A backup older than this many days is "old". */
    const val OLD_AFTER_DAYS = 90L

    /** Days between the first marked day the app saw and the first hint, when never backed up. */
    const val FIRST_HINT_AFTER_DAYS = 14L

    /** The hint to show on [today], or null when there is nothing to remind of. */
    fun hint(
        status: BackupStatus,
        today: LocalDate,
        hasMarkedDays: Boolean,
    ): BackupHint? {
        val last = status.lastBackup
        if (last != null) {
            // A date in the future (the phone's clock was set back) is no reason to nag.
            if (ChronoUnit.DAYS.between(last, today) <= OLD_AFTER_DAYS) return null
            return BackupHint.Old(ChronoUnit.MONTHS.between(last, today).toInt())
        }
        // Nothing to lose yet, nothing to remind of.
        val since = status.marksSince
        if (!hasMarkedDays || since == null) return null
        return BackupHint.Never.takeIf {
            ChronoUnit.DAYS.between(since, today) >=
                FIRST_HINT_AFTER_DAYS
        }
    }
}
