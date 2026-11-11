package de.remph.urlaubsplaner.planner

import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals
import org.junit.Test

class YearRowsTest {
    @Test
    fun `the expanded rows are what fits, between the minimum and the maximum`() {
        val screen = Dp(800f)
        val rest = Dp(368f)

        // 432 dp left for 12 rows.
        assertEquals(36f, expandedRowHeight(screen, rest).value, 0.001f)
        assertEquals(YearRowsMin, expandedRowHeight(Dp(600f), rest))
        assertEquals(YearRowsMax, expandedRowHeight(Dp(2000f), rest))
        assertEquals(50f, expandedRowHeight(Dp(968f), rest).value, 0.001f)
    }
}
