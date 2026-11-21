// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.data

import android.content.ContentResolver
import android.net.Uri
import java.io.FileNotFoundException

/**
 * Lets only an address through that the system file picker (or a document provider) hands out: a
 * `content://` address that is not served by our own [androidx.core.content.FileProvider].
 *
 * Why: [ContentResolver] also opens `file://` addresses. If an app other than the system picker
 * answered "create a file" with `file:///data/data/<our package>/databases/...`, the export would
 * overwrite the app's own database. The `.fileprovider` authority is ours and only meant for the
 * calendar file that other apps read; it is never a place to save to. Throws the same
 * [FileNotFoundException] as an address that has no stream, so the screens show "could not
 * save/read".
 */
internal fun requirePickerUri(
    uri: Uri,
    packageName: String,
) {
    val ownAuthority = packageName + CalendarFileStore.AUTHORITY_SUFFIX
    if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority == ownAuthority) {
        throw FileNotFoundException("Not an address from the file picker: ${uri.scheme}")
    }
}
