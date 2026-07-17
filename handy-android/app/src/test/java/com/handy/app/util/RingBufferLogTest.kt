package com.handy.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sprint 28 — unit coverage for the JVM-pure ring buffer used by
 * the Debug panel's LiveLogViewer. Five tests target the surface
 * operations: append (order preservation), eviction (FIFO when at
 * capacity), tail (bounds and ordering), tail-edge cases (n <= 0,
 * n >= size), and clear (state reset).
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
}
