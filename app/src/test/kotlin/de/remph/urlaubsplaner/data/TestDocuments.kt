// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException

/**
 * Stands in for the system file picker in unit tests: a `content://` address for a real file, as
 * the picker hands out. The stores accept nothing else (see [requirePickerUri]). Call
 * [register] once per test, before the first [uriFor].
 */
class TestDocumentsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        // A file that cannot be opened (a missing folder, a gone file) must fail the way a
        // provider does, with a FileNotFoundException.
        val file = File(uri.lastPathSegment ?: throw FileNotFoundException("No path in $uri"))
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ) = 0
}

object TestDocuments {
    const val AUTHORITY = "de.remph.urlaubsplaner.test.documents"

    fun register() {
        Robolectric.setupContentProvider(TestDocumentsProvider::class.java, AUTHORITY)
    }

    fun uriFor(file: File): Uri =
        Uri
            .Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(file.absolutePath)
            .build()
}
