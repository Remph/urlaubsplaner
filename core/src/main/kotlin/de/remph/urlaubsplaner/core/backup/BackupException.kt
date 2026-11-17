// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.backup

/**
 * Why a file could not be imported. The subclass tells the screen which text to show (the
 * messages here are English and for logs); in every case nothing has been changed.
 */
sealed class BackupException(
    message: String,
) : Exception(message) {
    /** Not JSON, not ours, or too big to be a backup. */
    class NotABackup(
        detail: String,
    ) : BackupException("This is not an Urlaubsplaner backup: $detail")

    /** Made by a newer version of the app, which this one cannot read without losing data. */
    class NewerVersion(
        val found: Int,
        val supported: Int,
    ) : BackupException("The backup has format version $found, this app reads up to $supported")

    /**
     * A backup of ours with a missing, wrong or unknown value. The detail can quote text from the
     * file, which the screen shows in a dialog, so it is cut to [MAX_DETAIL] characters.
     */
    class Damaged(
        detail: String,
    ) : BackupException("The backup is damaged: ${detail.take(MAX_DETAIL)}") {
        companion object {
            const val MAX_DETAIL = 300
        }
    }
}
