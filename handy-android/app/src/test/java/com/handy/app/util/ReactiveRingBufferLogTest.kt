package com.handy.app.util

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ReactiveRingBufferLog].
 *
 * Coverage:
 * - append emits updated snapshot and tail
 * - clear emits empty lists
 * - state flow emissions reflect atomic mutations
 */
class ReactiveRingBufferLogTest {

    @Test
    fun `append emits updated snapshot and tail`(): Unit = runBlocking {
        val log = ReactiveRingBufferLog(maxLines = 3)
        log.append("alpha")
        log.append("beta")

        assertEquals(listOf("alpha", "beta"), log.snapshotFlow.first())
        assertEquals(listOf("alpha", "beta"), log.tailFlow.first())
    }

    @Test
    fun `append beyond capacity evicts oldest in snapshot`(): Unit = runBlocking {
        val log = ReactiveRingBufferLog(maxLines = 2)
        log.append("alpha")
        log.append("beta")
        log.append("gamma")

        assertEquals(listOf("beta", "gamma"), log.snapshotFlow.first())
    }

    @Test
    fun `tailFlow only exposes last 50 lines`(): Unit = runBlocking {
        val log = ReactiveRingBufferLog(maxLines = 100)
        repeat(60) { i ->
            log.append("line-$i")
        }

        val tail = log.tailFlow.first()
        assertEquals(50, tail.size)
        assertEquals("line-10", tail.first())
        assertEquals("line-59", tail.last())
    }

    @Test
    fun `clear emits empty snapshot and tail`(): Unit = runBlocking {
        val log = ReactiveRingBufferLog(maxLines = 3)
        log.append("alpha")
        log.clear()

        assertEquals(emptyList<String>(), log.snapshotFlow.first())
        assertEquals(emptyList<String>(), log.tailFlow.first())
    }
}
