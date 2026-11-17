// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The debug build has its own copy of res/xml/shortcuts.xml (its application ID differs, and the
 * launcher reads the target package raw). The copy must stay the same file apart from that ID, or
 * a change to the shortcuts would reach the release build and not the debug one. Plain files, read
 * from the module directory that Gradle runs the tests in.
 */
class ShortcutsDebugCopyTest {
    private fun read(path: String): String {
        val file = File(path)
        assertTrue("$path is missing", file.isFile)
        return file.readText()
    }

    @Test
    fun `the debug copy of the shortcut file differs from the main one only in the package`() {
        val main = read("src/main/res/xml/shortcuts.xml")
        val debug = read("src/debug/res/xml/shortcuts.xml")

        assertEquals(
            main.replace(
                "targetPackage=\"de.remph.urlaubsplaner\"",
                "targetPackage=\"de.remph.urlaubsplaner.debug\"",
            ),
            debug,
        )
    }
}
