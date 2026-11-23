// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The licence dialogs join the hard-wrapped lines of a paragraph; the licences stay word for word. */
class LicenceReflowTest {
    private fun asset(name: String) = File("src/main/assets/licenses/$name").readText()

    private fun words(text: String) = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

    @Test
    fun `no word is added, dropped or changed in any bundled licence`() {
        for (name in listOf("GPL-3.0.txt", "Apache-2.0.txt", "BSD-3-Clause-protobuf.txt")) {
            val original = asset(name)

            assertEquals(name, words(original), words(reflowLicence(original)))
        }
    }

    @Test
    fun `a paragraph of the GPL becomes one line and blank lines stay`() {
        val text = reflowLicence(asset("GPL-3.0.txt"))

        val first = "The GNU General Public License is a free, copyleft license for software"
        assertTrue(text.lines().any { it.startsWith(first) && it.endsWith("kinds of works.") })
        assertTrue("" in text.lines())
        // The terminal wrap was at about 70 characters; no paragraph line is that short any more.
        val preamble = text.lines().first { it.startsWith("The licenses for most software") }
        assertTrue(preamble.length > 200)
    }

    @Test
    fun `centred titles and bullets keep their own line`() {
        val gpl = reflowLicence(asset("GPL-3.0.txt")).lines().dropWhile { it.isEmpty() }
        assertEquals("GNU GENERAL PUBLIC LICENSE", gpl[0])
        assertEquals("Version 3, 29 June 2007", gpl[1])

        val apache = reflowLicence(asset("Apache-2.0.txt")).lines().dropWhile { it.isEmpty() }
        assertEquals("Apache License", apache[0])
        assertEquals("Version 2.0, January 2004", apache[1])

        val bsd = reflowLicence(asset("BSD-3-Clause-protobuf.txt")).lines()
        assertEquals(3, bsd.count { it.startsWith("* ") })
    }

    @Test
    fun `numbered sections start their own paragraph`() {
        val gpl = reflowLicence(asset("GPL-3.0.txt")).lines()

        assertTrue("0. Definitions." in gpl)
        assertFalse(gpl.any { it.contains("0. Definitions. “This License”") })
    }
}
