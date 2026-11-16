// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.bridge

import de.remph.urlaubsplaner.core.balance.WorkSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

// 2026: 13 May is a Wednesday, 14 May a Thursday.
class BridgeGroupTest {
    private fun d(
        month: Int,
        day: Int,
    ) = LocalDate.of(2026, month, day)

    private fun finder(vararg holidays: LocalDate) =
        BridgeFinder(
            Clock.fixed(d(1, 1).atTime(12, 0).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            WorkSchedule(),
        ) { it in holidays }

    @Test
    fun `a Wednesday holiday gives one group with a bridge before and one after`() {
        val groups = finder(d(5, 13)).find(2026).groupedByAnchor()

        val group = groups.single()
        assertEquals(d(5, 13), group.anchor)
        assertEquals(listOf(d(5, 11), d(5, 12)), group.before?.leave)
        assertEquals(listOf(d(5, 14), d(5, 15)), group.after?.leave)
        assertEquals(2, group.bridges.size)
    }

    @Test
    fun `a Thursday holiday has only a bridge after it`() {
        val group = finder(d(5, 14)).find(2026).groupedByAnchor().single()

        assertNull(group.before)
        assertEquals(listOf(d(5, 15)), group.after?.leave)
    }

    @Test
    fun `groups are sorted by the holiday and an empty list gives no groups`() {
        val groups = finder(d(5, 14), d(9, 2)).find(2026).groupedByAnchor()

        assertEquals(listOf(d(5, 14), d(9, 2)), groups.map { it.anchor })
        assertEquals(emptyList<BridgeGroup>(), emptyList<Bridge>().groupedByAnchor())
    }

    @Test
    fun `a group needs a bridge`() {
        assertThrows(IllegalArgumentException::class.java) { BridgeGroup(d(5, 13), null, null) }
    }
}
