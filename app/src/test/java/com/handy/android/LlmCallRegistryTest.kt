package com.handy.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Plain-JVM coverage for [LlmCallRegistry] cancellation semantics (P0.1):
 * destroying/cancelling one surface must never cancel another surface's
 * in-flight post-processing call, while the global [LlmCallRegistry.cancelAll]
 * still aborts everything.
 *
 * Port of `CallRegistryTest.java` from android_transcribe_app, adapted to
 * Kotlin coroutines. Calls are modelled as coroutines that complete only when
 * released by a [CompletableDeferred], so cancellation deterministically finds
 * them still in flight.
 */
class LlmCallRegistryTest {

    private val scope = CoroutineScope(EmptyCoroutineContext)

    @After
    fun tearDown() {
        LlmCallRegistry.cancelAll()
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun cancellingOneOwnerLeavesTheOtherUntouched() = runBlocking {
        val ownerA = Any()
        val ownerB = Any()
        val releaseA = CompletableDeferred<Unit>()
        val releaseB = CompletableDeferred<Unit>()
        val resultA = CompletableDeferred<String>()
        val resultB = CompletableDeferred<String>()

        val jobA = scope.launch {
            LlmCallRegistry.register(coroutineContext[Job]!!, ownerA)
            try {
                releaseA.await()
                resultA.complete("ok")
            } finally {
                LlmCallRegistry.unregister(coroutineContext[Job]!!)
            }
        }
        val jobB = scope.launch {
            LlmCallRegistry.register(coroutineContext[Job]!!, ownerB)
            try {
                releaseB.await()
                resultB.complete("ok")
            } finally {
                LlmCallRegistry.unregister(coroutineContext[Job]!!)
            }
        }

        // Ensure both are registered and in flight.
        delay(50)
        LlmCallRegistry.cancelAllForOwner(ownerA)

        // A must be cancelled; B must still be completable.
        jobA.join()
        assertFalse("call A should be cancelled", resultA.isCompleted)
        assertTrue("call B (different owner) must still complete", jobB.isActive)

        releaseB.complete(Unit)
        assertEquals("ok", resultB.await())
        jobB.join()
    }

    @Test
    fun globalCancelAllAbortsEveryOwner() = runBlocking {
        val ownerA = Any()
        val ownerB = Any()
        val releaseA = CompletableDeferred<Unit>()
        val releaseB = CompletableDeferred<Unit>()
        val resultA = CompletableDeferred<String>()
        val resultB = CompletableDeferred<String>()

        val jobA = scope.launch {
            LlmCallRegistry.register(coroutineContext[Job]!!, ownerA)
            try {
                releaseA.await()
                resultA.complete("ok")
            } finally {
                LlmCallRegistry.unregister(coroutineContext[Job]!!)
            }
        }
        val jobB = scope.launch {
            LlmCallRegistry.register(coroutineContext[Job]!!, ownerB)
            try {
                releaseB.await()
                resultB.complete("ok")
            } finally {
                LlmCallRegistry.unregister(coroutineContext[Job]!!)
            }
        }

        delay(50)
        LlmCallRegistry.cancelAll()

        jobA.join()
        jobB.join()
        assertFalse("call A should be cancelled", resultA.isCompleted)
        assertFalse("call B should be cancelled", resultB.isCompleted)
    }

    @Test
    fun unregisterRemovesCallFromCancellationScope() = runBlocking {
        val owner = Any()
        val release = CompletableDeferred<Unit>()
        val result = CompletableDeferred<String>()

        val job = scope.launch {
            val self = coroutineContext[Job]!!
            LlmCallRegistry.register(self, owner)
            LlmCallRegistry.unregister(self)
            try {
                release.await()
                result.complete("ok")
            } finally {
                LlmCallRegistry.unregister(self)
            }
        }

        delay(50)
        // Nothing is registered for this owner anymore, so this must no-op.
        LlmCallRegistry.cancelAllForOwner(owner)

        release.complete(Unit)
        assertEquals("ok", result.await())
        job.join()
    }

    @Test
    fun ownerScopedCancelIgnoresNullOwner() = runBlocking {
        val owner = Any()
        val release = CompletableDeferred<Unit>()
        val result = CompletableDeferred<String>()

        val job = scope.launch {
            // An ownerless call (legacy/edge path) is registered with null.
            LlmCallRegistry.register(coroutineContext[Job]!!, null)
            try {
                release.await()
                result.complete("ok")
            } finally {
                LlmCallRegistry.unregister(coroutineContext[Job]!!)
            }
        }

        delay(50)
        LlmCallRegistry.cancelAllForOwner(owner)
        LlmCallRegistry.cancelAllForOwner(null)

        release.complete(Unit)
        assertEquals("ok", result.await())
        job.join()
    }
}
