// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Gets the calendar file out of the app, two ways (the maintainer, 2026-10-26): written to a file the user
 * picked with the system file picker ([write], needs no storage permission), or to a cache file
 * that a calendar app is given to open ([writeForOpening], through a [FileProvider] so the
 * other app can read exactly that one file). The text itself is made in `:core`.
 */
class CalendarFileStore(
    private val context: Context,
) {
    suspend fun write(
        uri: Uri,
        text: String,
    ) = withContext(Dispatchers.IO) {
        // "wt" truncates: with plain "w" some providers keep the tail of a longer old file.
        requirePickerUri(uri, context.packageName)
        val output =
            context.contentResolver.openOutputStream(uri, "wt")
                ?: throw FileNotFoundException("No stream for $uri")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Writes [text] to the cache and returns the address of that file for another app. */
    suspend fun writeForOpening(
        fileName: String,
        text: String,
    ): Uri =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            // Only the newest export is kept; the cache is not a place for a pile of old files.
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, fileName)
            file.writeText(text, Charsets.UTF_8)
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        }

    companion object {
        /** Must match `file_paths.xml`. */
        const val DIRECTORY = "calendar"
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
