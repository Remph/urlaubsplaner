package de.remph.urlaubsplaner.core.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BackupReminderTest {
    private val today = LocalDate.of(2027, 3, 10)

    private fun hint(
        last: LocalDate? = null,
        since: LocalDate? = null,
        marked: Boolean = true,
        on: LocalDate = today,
    ) = BackupReminder.hint(BackupStatus(last, since), on, marked)

    @Test
    fun `a backup of 90 days ago is not old yet, 91 days ago is`() {
        assertNull(hint(last = today.minusDays(90)))
        assertEquals(BackupHint.Old(3), hint(last = today.minusDays(91)))
    }

    @Test
    fun `a recent backup says nothing`() {
        assertNull(hint(last = today))
        assertNull(hint(last = today.minusDays(30)))
    }

    @Test
    fun `an old backup says how many whole months it is`() {
        assertEquals(BackupHint.Old(4), hint(last = LocalDate.of(2026, 11, 9)))
        assertEquals(BackupHint.Old(14), hint(last = LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun `91 days can be only two whole months and the hint says so`() {
        // 31 May to 30 August is 91 days; 31 August would be the third month.
        assertEquals(
            BackupHint.Old(2),
            hint(last = LocalDate.of(2026, 5, 31), on = LocalDate.of(2026, 8, 30)),
        )
    }

    @Test
    fun `a backup dated in the future says nothing`() {
        assertNull(hint(last = today.plusDays(400)))
    }

    @Test
    fun `never backed up, the hint comes 14 days after the first marked days`() {
        assertNull(hint(since = today.minusDays(13)))
        assertEquals(BackupHint.Never, hint(since = today.minusDays(14)))
        assertEquals(BackupHint.Never, hint(since = today.minusDays(200)))
    }

    @Test
    fun `never backed up and nothing marked says nothing`() {
        assertNull(hint(since = today.minusDays(200), marked = false))
        assertNull(hint(since = null, marked = false))
    }

    @Test
    fun `marked days the app has not seen yet start the wait instead of showing a hint`() {
        assertNull(hint(since = null, marked = true))
    }

    @Test
    fun `a saved backup replaces the first wait`() {
        // marksSince is old, but a backup of yesterday is what counts.
        assertNull(hint(last = today.minusDays(1), since = today.minusDays(500)))
    }

    @Test
    fun `the first-hint wait follows the clock across a year end`() {
        assertEquals(
            BackupHint.Never,
            hint(since = LocalDate.of(2026, 12, 25), on = LocalDate.of(2027, 1, 8)),
        )
        assertNull(hint(since = LocalDate.of(2026, 12, 26), on = LocalDate.of(2027, 1, 8)))
    }
}
