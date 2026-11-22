// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner

import org.junit.Assume.assumeFalse

/**
 * Skips the calling test on Windows. androidx.core 1.18.0 `FileProvider.belongsToRoot` checks
 * `rootPath + '/'`, which never matches Robolectric's temp paths with '\\' there, while the same
 * code works on a phone and on Linux (the cloud runs the test). Use it only when the test cannot
 * avoid the library; `MigrationTest` shows the workaround for androidx.sqlite's '/' check.
 */
fun assumeUnixPaths() {
    assumeFalse(
        "AndroidX assumes '/' as the path separator",
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
    )
}
