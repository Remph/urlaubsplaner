// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.ContentResolver
import android.net.Uri
import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.backup.BackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.LocalDate

/**
 * Reads and writes the backup file at a [Uri] the user picked with the system file picker
 * (`CreateDocument` for export, `OpenDocument` for import, [BackupFile.MIME_TYPE]; the screens in
 * M3 launch them and suggest [BackupFile.fileName]). The picker grants access to that one file, so
 * the app needs no storage permission. The format is in `:core`; this only moves the bytes.
 *
 * Both calls run on the IO dispatcher. A file that is not a restorable backup throws a
 * `BackupException` from `:core`; an unreadable or unwritable one throws an `IOException`.
 */
class BackupFileStore(
    private val resolver: ContentResolver,
    private val packageName: String,
) {
    suspend fun write(
        uri: Uri,
        data: BackupData,
        exportedOn: LocalDate,
    ) = withContext(Dispatchers.IO) {
        // "wt" truncates: with plain "w" some providers keep the tail of a longer old file.
        requirePickerUri(uri, packageName)
        val output =
            resolver.openOutputStream(uri, "wt")
                ?: throw FileNotFoundException("No stream for $uri")
        output.use { BackupFile.writeTo(it, data, exportedOn) }
    }

    suspend fun read(uri: Uri): BackupData =
        withContext(Dispatchers.IO) {
            requirePickerUri(uri, packageName)
            val input =
                resolver.openInputStream(uri) ?: throw FileNotFoundException("No stream for $uri")
            input.use { BackupFile.readFrom(it) }
        }
}
