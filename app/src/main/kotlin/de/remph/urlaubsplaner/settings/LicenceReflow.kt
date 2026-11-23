// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

/**
 * The licence files are hard-wrapped at about 70 characters for a terminal. On a phone those
 * breaks give a ragged edge, so the lines of one paragraph are joined into one and the screen
 * wraps it. Not a word changes (a test compares the words): only line ends and indentation.
 *
 * What stays a break: a blank line, a heavily indented line (a centred title such as
 * "GNU GENERAL PUBLIC LICENSE"), and a line that starts a bullet or a "<placeholder>".
 */
internal fun reflowLicence(text: String): String {
    val out = mutableListOf<String>()
    val paragraph = mutableListOf<String>()

    fun flush() {
        if (paragraph.isNotEmpty()) out += paragraph.joinToString(" ")
        paragraph.clear()
    }

    for (raw in text.replace("\r", "").split('\n')) {
        val line = raw.trimEnd()
        val body = line.trimStart()
        when {
            body.isEmpty() -> {
                flush()
                out += ""
            }

            line.length - body.length >= TITLE_INDENT -> {
                flush()
                out += body
            }

            body.startsWith("* ") || body.startsWith("<") -> {
                flush()
                paragraph += body
            }

            else -> {
                paragraph += body
            }
        }
    }
    flush()
    return out.joinToString("\n")
}

/** Centred titles in the licence files are indented from 12 columns; a wrapped item line at most 10. */
private const val TITLE_INDENT = 12
