package com.handy.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Sprint 28 — unit coverage for the JVM-pure ring buffer used by
 * the Debug panel's LiveLogViewer. Five tests target the surface
 * operations: append (order preservation), eviction (FIFO when at
 * capacity), tail (bounds and ordering), tail-edge cases (n <= 0,
 * n >= size), and clear (state reset).
 *
 * Sprint 28b: adds three carry-over edge tests from the Sprint 28
 * v3 code-review pass — `[append on empty buffer keeps ordering]`,
 * `[append of empty string is allowed and observable]`, and
 * `[maxLines=1 boundary evict-on-second-append]` — plus a guard test
 * for the `init { require(maxLines > 0) }` precondition.
 */
class RingBufferLogTest {

    @Test
    fun `append then snapshot returns appended lines in oldest-first order`() {
        val log = RingBufferLog(maxLines = 5)
        log.append("a")
        log.append("b")
        log.append("c")
        assertEquals(listOf("a", "b", "c"), log.snapshot())
        assertEquals(3, log.size())
    }

    @Test
    fun `append evicts the oldest entry once the capacity is reached`() {
        val log = RingBufferLog(maxLines = 3)
        log.append("1")
        log.append("2")
        log.append("3")
        log.append("4") // evicts "1"
        log.append("5") // evicts "2"
        assertEquals(listOf("3", "4", "5"), log.snapshot())
        assertEquals(3, log.size())
    }

    @Test
    fun `tail returns at most n lines from the newest end, oldest-first within the slice`() {
        val log = RingBufferLog(maxLines = 10)
        repeat(7) { i -> log.append("line_$i") }
        assertEquals(listOf("line_4", "line_5", "line_6"), log.tail(3))
        // n >= size returns the full buffer
        assertEquals(
            listOf("line_0", "line_1", "line_2", "line_3", "line_4", "line_5", "line_6"),
            log.tail(99),
        )
    }

    @Test
    fun `tail with n_leq_0 returns empty list and tail with n_eq_1 returns the last line`() {
        val log = RingBufferLog()
        log.append("x")
        log.append("y")
        log.append("z")
        assertEquals(emptyList<String>(), log.tail(0))
        assertEquals(emptyList<String>(), log.tail(-1))
        assertEquals(emptyList<String>(), log.tail(-999))
        assertEquals(listOf("z"), log.tail(1))
    }

    @Test
    fun `clear empties the buffer and size drops to zero`() {
        val log = RingBufferLog()
        log.append("a")
        log.append("b")
        log.append("c")
        assertEquals(3, log.size())
        log.clear()
        assertEquals(0, log.size())
        assertEquals(emptyList<String>(), log.snapshot())
        assertEquals(emptyList<String>(), log.tail(10))
        // After clear, fresh appends work as expected
        log.append("d")
        assertEquals(listOf("d"), log.snapshot())
    }

    // ---------- Sprint 28b: code-reviewer carry-over edge tests ----------

    @Test
    fun `append on empty buffer produces size 1 and that line is the snapshot`() {
        val log = RingBufferLog(maxLines = 5)
        assertEquals(0, log.size())
        assertEquals(emptyList<String>(), log.snapshot())
        log.append("first")
        assertEquals(1, log.size())
        assertEquals(listOf("first"), log.snapshot())
        assertEquals(listOf("first"), log.tail(1))
    }

    @Test
    fun `append of empty string is allowed and observable`() {
        val log = RingBufferLog(maxLines = 3)
        log.append("")
        log.append("a")
        log.append("")
        // All three entries present, oldest-first, and the empty
        // strings are explicitly preserved (NOT collapsed).
        assertEquals(3, log.size())
        assertEquals(listOf("", "a", ""), log.snapshot())
    }

    @Test
    fun `maxLines=1 boundary keeps only the most recent append`() {
        val log = RingBufferLog(maxLines = 1)
        log.append("a")
        log.append("b")
        log.append("c")
        // Capacity 1 means every prior append is evicted on the next.
        assertEquals(1, log.size())
        assertEquals(listOf("c"), log.snapshot())
        assertEquals(listOf("c"), log.tail(1))
        assertEquals(listOf("c"), log.tail(99))
    }

    @Test
    fun `init rejects maxLines=0 with IllegalArgumentException`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            RingBufferLog(maxLines = 0)
        }
        assertEquals("maxLines must be > 0; got 0", ex.message)
    }
}
