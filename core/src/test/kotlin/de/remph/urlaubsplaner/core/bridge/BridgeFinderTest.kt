// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.bridge

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.PublicHolidays
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

// 2026 calendar used below: 1 Jan is a Thursday, 15 Jun a Monday, 7 Sep a Monday,
// 30 Dec a Wednesday.
class BridgeFinderTest {
    private fun clockAt(date: LocalDate) =
        Clock.fixed(date.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private fun finder(
        holidays: Set<LocalDate> = emptySet(),
        schedule: WorkSchedule = WorkSchedule(),
        today: LocalDate = LocalDate.of(2026, 1, 1),
    ) = BridgeFinder(clockAt(today), schedule) { it in holidays }

    private fun d(
        month: Int,
        day: Int,
        year: Int = 2026,
    ) = LocalDate.of(year, month, day)

    // --- the basic shapes ---

    @Test
    fun `a Thursday holiday is bridged by the Friday`() {
        val bridges = finder(setOf(d(5, 14))).find(2026)

        val bridge = bridges.single()
        assertEquals(listOf(d(5, 15)), bridge.leave)
        assertEquals(VacationDays.ONE, bridge.cost)
        assertEquals(d(5, 14), bridge.start)
        assertEquals(d(5, 17), bridge.end)
        assertEquals(4, bridge.daysOff)
    }

    @Test
    fun `a Tuesday holiday is bridged by the Monday`() {
        val bridge = finder(setOf(d(5, 12))).find(2026).single()

        assertEquals(listOf(d(5, 11)), bridge.leave)
        assertEquals(d(5, 9), bridge.start)
        assertEquals(d(5, 12), bridge.end)
        assertEquals(4, bridge.daysOff)
    }

    @Test
    fun `a Wednesday holiday has two bridges of two days`() {
        val bridges = finder(setOf(d(5, 13))).find(2026)

        assertEquals(
            listOf(listOf(d(5, 11), d(5, 12)), listOf(d(5, 14), d(5, 15))),
            bridges.map {
                it.leave
            },
        )
        assertTrue(bridges.all { it.cost == VacationDays.of(2) && it.daysOff == 5 })
    }

    @Test
    fun `a gap that costs more than the limit is not a bridge`() {
        val bridges = finder(setOf(d(5, 13))).find(2026, maxCost = VacationDays.ONE)

        assertTrue(bridges.isEmpty())
    }

    @Test
    fun `a bridge is named after the holiday it joins and knows its side`() {
        val bridges = finder(setOf(d(5, 13))).find(2026)

        assertTrue(bridges.all { it.anchor == d(5, 13) })
        assertEquals(listOf(true, false), bridges.map { it.isBeforeAnchor })
    }

    @Test
    fun `the anchor is the holiday nearest to the leave days`() {
        // Thursday 14 May and Monday 18 May are holidays; the Friday is next to the first one and
        // the weekend lies between. The stretch Thu to Mon has both, the Friday belongs to Thursday.
        val bridge = finder(setOf(d(5, 14), d(5, 18))).find(2026).single()

        assertEquals(listOf(d(5, 15)), bridge.leave)
        assertEquals(d(5, 14), bridge.anchor)
        assertEquals(false, bridge.isBeforeAnchor)
    }

    @Test
    fun `a special day off is the anchor when there is no holiday`() {
        // Heiligabend a day off on a Thursday (2026-12-24), no public holiday at all.
        val schedule =
            WorkSchedule(
                specialDays =
                    mapOf(WorkSchedule.HEILIGABEND to VacationDays.ZERO),
            )

        val bridge = finder(schedule = schedule).find(2026).single()

        assertEquals(d(12, 24), bridge.anchor)
        assertEquals(listOf(d(12, 25)), bridge.leave)
    }

    @Test
    fun `a gap is never cut into a smaller bridge`() {
        // Mon 11 to Thu 14 May cost 4 days before the Friday holiday. Taking only Wed and Thu
        // would leave Mon and Tue to work in the middle of the stretch, so there is no bridge.
        val bridges = finder(setOf(d(5, 15))).find(2026)

        assertTrue(bridges.isEmpty())
    }

    @Test
    fun `a limit of one day still finds the one day bridges`() {
        val bridges = finder(setOf(d(5, 14))).find(2026, maxCost = VacationDays.ONE)

        assertEquals(1, bridges.size)
    }

    @Test
    fun `a gap between two holidays is one bridge`() {
        // Tuesday and Thursday are holidays; the Wednesday in between joins them.
        val bridges = finder(setOf(d(5, 12), d(5, 14))).find(2026)

        val between = bridges.single { it.leave == listOf(d(5, 13)) }
        assertEquals(d(5, 12), between.start)
        assertEquals(d(5, 14), between.end)
        assertEquals(3, between.daysOff)
    }

    @Test
    fun `weekends alone are never bridges`() {
        assertTrue(finder().find(2026).isEmpty())
    }

    @Test
    fun `a holiday on a weekend has no bridge`() {
        assertTrue(finder(setOf(d(6, 6))).find(2026).isEmpty())
    }

    @Test
    fun `results are sorted by date`() {
        val bridges = finder(setOf(d(9, 8), d(5, 14))).find(2026)

        assertEquals(bridges.map { it.start }.sorted(), bridges.map { it.start })
    }

    // --- marked days ---

    @Test
    fun `a day that is already marked is not suggested again`() {
        val bridges = finder(setOf(d(5, 14))).find(2026, marks = mapOf(d(5, 15) to DayMark.FULL))

        assertTrue(bridges.isEmpty())
    }

    @Test
    fun `a half marked day leaves half a day to take`() {
        val bridge =
            finder(setOf(d(5, 14))).find(2026, marks = mapOf(d(5, 15) to DayMark.HALF)).single()

        assertEquals(listOf(d(5, 15)), bridge.leave)
        assertEquals(VacationDays.HALF, bridge.cost)
    }

    @Test
    fun `marked days beside the stretch make it longer`() {
        val bridge =
            finder(setOf(d(5, 14))).find(2026, marks = mapOf(d(5, 18) to DayMark.FULL)).single()

        assertEquals(d(5, 14), bridge.start)
        assertEquals(d(5, 18), bridge.end)
        assertEquals(5, bridge.daysOff)
    }

    @Test
    fun `a marked day alone does not make a bridge`() {
        // Marking only the Monday after a plain weekend is a normal vacation day, not a Brückentag.
        val bridges = finder().find(2026, marks = mapOf(d(5, 11) to DayMark.FULL))

        assertTrue(bridges.isEmpty())
    }

    // --- schedule ---

    @Test
    fun `a four day week reaches the free Friday`() {
        val schedule =
            WorkSchedule(
                workingDays =
                    setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                    ),
            )
        // Tuesday holiday: the Monday bridges to the free Friday, Saturday and Sunday. The two
        // days after the holiday are a second, dearer bridge.
        val bridge = finder(setOf(d(5, 12)), schedule).find(2026).first()

        assertEquals(listOf(d(5, 11)), bridge.leave)
        assertEquals(d(5, 8), bridge.start)
        assertEquals(d(5, 12), bridge.end)
        assertEquals(5, bridge.daysOff)
    }

    @Test
    fun `a Thursday holiday needs no bridge in a four day week`() {
        val schedule =
            WorkSchedule(
                workingDays =
                    setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                    ),
            )

        assertTrue(finder(setOf(d(5, 14)), schedule).find(2026).isEmpty())
    }

    @Test
    fun `a holiday on a day that is never worked is no reason for a bridge`() {
        val schedule =
            WorkSchedule(
                workingDays =
                    setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                    ),
            )

        assertTrue(finder(setOf(d(5, 13)), schedule).find(2026).isEmpty())
    }

    @Test
    fun `no working days means no bridges`() {
        assertTrue(
            finder(setOf(d(5, 14)), WorkSchedule(workingDays = emptySet())).find(2026).isEmpty(),
        )
    }

    @Test
    fun `a half Silvester makes the gap cost less`() {
        val schedule =
            WorkSchedule(specialDays = mapOf(WorkSchedule.SILVESTER to VacationDays.HALF))
        // 29 Dec and 1 Jan are holidays; 30 Dec is a full day and 31 Dec a half day.
        val bridge =
            finder(setOf(d(12, 29), d(1, 1, 2027)), schedule)
                .find(2026)
                .single { it.leave == listOf(d(12, 30), d(12, 31)) }

        assertEquals(VacationDays.of(1.5), bridge.cost)
        assertEquals(d(12, 29), bridge.start)
        assertEquals(d(1, 3, 2027), bridge.end)
        assertEquals(6, bridge.daysOff)
    }

    @Test
    fun `a Silvester that is a day off needs no vacation`() {
        val schedule =
            WorkSchedule(specialDays = mapOf(WorkSchedule.SILVESTER to VacationDays.ZERO))
        val bridge =
            finder(setOf(d(12, 29), d(1, 1, 2027)), schedule)
                .find(2026)
                .single { it.leave == listOf(d(12, 30)) }

        assertEquals(VacationDays.ONE, bridge.cost)
        assertEquals(d(12, 29), bridge.start)
        assertEquals(d(1, 3, 2027), bridge.end)
    }

    // --- years ---

    @Test
    fun `a stretch running into the next year belongs to the year it starts in`() {
        // 30 Dec and 4 Jan are holidays; 31 Dec and 1 Jan are the gap, and 1 Jan is a Friday.
        val holidays = setOf(d(12, 30), d(1, 4, 2027))
        val bridge = finder(holidays).find(2026).single { it.leave.first() == d(12, 31) }

        assertEquals(listOf(d(12, 31), d(1, 1, 2027)), bridge.leave)
        assertEquals(d(1, 4, 2027), bridge.end)
        assertTrue(finder(holidays).find(2027).none { d(12, 31) in it.leave })
    }

    @Test
    fun `New Year on a Thursday is bridged by the Friday`() {
        // 1 Jan 2026 is a Thursday: the Friday bridges back over the New Year holiday.
        val bridge = finder(setOf(d(1, 1))).find(2026).single()

        assertEquals(listOf(d(1, 2)), bridge.leave)
        assertEquals(d(1, 1), bridge.start)
        assertEquals(d(1, 4), bridge.end)
    }

    @Test
    fun `a holiday on the last day of the year before starts the stretch`() {
        // 31 Dec 2025 (Wednesday) is a holiday here, 1 Jan 2026 is not: the gap is 1 and 2 Jan.
        val bridge = finder(setOf(d(12, 31, 2025))).find(2026).single()

        assertEquals(d(12, 31, 2025), bridge.start)
        assertEquals(listOf(d(1, 1), d(1, 2)), bridge.leave)
    }

    // --- efficiency and best ---

    @Test
    fun `days off per vacation day`() {
        val bridge = finder(setOf(d(5, 13))).find(2026).first()

        assertEquals(2.5, bridge.daysOffPerVacationDay, 0.0)
    }

    private val holidays2026 = setOf(d(1, 1), d(6, 4), d(6, 17), d(9, 8))

    @Test
    fun `best takes the most days off per vacation day and leaves out the past`() {
        val best = finder(holidays2026, today = d(6, 1)).best(2026, limit = 3)

        // New Year is in the past. Two bridges give 4 days off for 1; the 2 day gaps of 17 Jun give 2.5.
        assertEquals(listOf(d(6, 4), d(9, 5), d(6, 13)), best.map { it.start })
    }

    @Test
    fun `a bridge that starts today counts as taken, not as a suggestion`() {
        val best = finder(setOf(d(6, 4)), today = d(6, 5)).best(2026)

        assertTrue(best.isEmpty())
    }

    @Test
    fun `best honours the limit`() {
        assertEquals(1, finder(holidays2026).best(2026, limit = 1).size)
        assertTrue(finder(holidays2026).best(2026, limit = 0).isEmpty())
    }

    @Test
    fun `rank orders a list that is already found like best does`() {
        val finder = finder(holidays2026, today = d(6, 1))
        val upcoming = finder.find(2026).filter { it.leave.first() > d(6, 1) }

        assertEquals(finder.best(2026, limit = 3), finder.rank(upcoming, limit = 3))
        assertTrue(finder.rank(upcoming, limit = 0).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { finder.rank(upcoming, limit = -1) }
    }

    // --- bad input ---

    @Test
    fun `the limit must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            finder().find(2026, maxCost = VacationDays.ZERO)
        }
    }

    @Test
    fun `best needs a limit of zero or more`() {
        assertThrows(IllegalArgumentException::class.java) { finder().best(2026, limit = -1) }
    }

    // --- with the real public holidays ---

    @Test
    fun `Bayern 2026 has the Friday after Christi Himmelfahrt as a bridge`() {
        val finder =
            BridgeFinder(clockAt(d(1, 1)), WorkSchedule()) {
                PublicHolidays.isHoliday(Bundesland.BAYERN, it)
            }
        val leaves = finder.find(2026).map { it.leave }

        assertTrue(listOf(d(5, 15)) in leaves)
        // Dreikönig is a Tuesday in 2026, so the Monday before it is a bridge in Bayern.
        assertTrue(listOf(d(1, 5)) in leaves)
    }

    @Test
    fun `a free stretch that reaches back does not look before the first supported year`() {
        // Every day before 29 February 2024 is a day off, so the stretch before the one working day
        // reaches back past 2019. The holiday rules throw there, like PublicHolidays does.
        val finder =
            BridgeFinder(clockAt(d(1, 1)), WorkSchedule()) {
                require(it.year >= PublicHolidays.FIRST_SUPPORTED_YEAR) { "no holidays for $it" }
                it < d(2, 29, 2024)
            }

        val bridge = finder.find(2024).first()

        assertEquals(d(2, 29, 2024), bridge.leave.first())
        assertEquals(d(1, 1, PublicHolidays.FIRST_SUPPORTED_YEAR), bridge.start)
    }
}
