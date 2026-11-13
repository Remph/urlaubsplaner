package de.remph.urlaubsplaner.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** One widget that cannot be drawn must not stop the others, nor the watch that calls this. */
class UpdateEachTest {
    @Test
    fun `every id is updated`() =
        runTest {
            val done = mutableListOf<Int>()
            updateEach(listOf(3, 5), onError = {
                _,
                _,
                ->
                error("no error expected")
            }) { done += it }
            assertEquals(listOf(3, 5), done)
        }

    @Test
    fun `a failing id is reported and the others still run`() =
        runTest {
            val done = mutableListOf<Int>()
            val failed = mutableListOf<Int>()
            updateEach(listOf(1, 2, 3), onError = { id, _ -> failed += id }) {
                if (it == 2) throw IllegalStateException("no state")
                done += it
            }
            assertEquals(listOf(1, 3), done)
            assertEquals(listOf(2), failed)
        }

    @Test
    fun `cancellation passes through and is not reported`() {
        val failed = mutableListOf<Int>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                updateEach(listOf(1, 2), onError = { id, _ -> failed += id }) {
                    throw CancellationException("stop")
                }
            }
        }
        assertEquals(emptyList<Int>(), failed)
    }
}
