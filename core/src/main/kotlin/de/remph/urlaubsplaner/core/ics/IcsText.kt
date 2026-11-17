// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.ics

/** The two text rules of RFC 5545 that a calendar file needs: escaping values and folding lines. */
internal object IcsText {
    /** A content line may hold at most 75 octets before it must be folded. */
    private const val MAX_OCTETS = 75

    const val NEWLINE = "\r\n"

    /** Backslash, semicolon, comma and line breaks are special in a TEXT value. */
    fun escape(value: String): String =
        buildString {
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    ';', ',' -> append('\\').append(c)
                    '\n' -> append("\\n")
                    '\r' -> Unit
                    else -> append(c)
                }
            }
        }

    /**
     * Splits [line] after at most 75 octets (UTF-8) and continues the next piece with one space.
     * Counts octets, not characters, and never cuts inside a character.
     */
    fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_OCTETS) return line
        val out = StringBuilder()
        var octets = 0
        var index = 0
        var limit = MAX_OCTETS
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val text = String(Character.toChars(codePoint))
            val size = text.toByteArray(Charsets.UTF_8).size
            if (octets + size > limit) {
                out.append(NEWLINE).append(' ')
                octets = 0
                // The space that starts a continuation line is one octet of its limit.
                limit = MAX_OCTETS - 1
            }
            out.append(text)
            octets += size
            index += text.length
        }
        return out.toString()
    }
}
