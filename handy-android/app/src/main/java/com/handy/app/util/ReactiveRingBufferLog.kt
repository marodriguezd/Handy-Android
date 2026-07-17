package com.handy.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sprint 28b — Compose-reactive wrapper around [RingBufferLog].
 *
 * The base [RingBufferLog] is JVM-pure (no Android dependency) so its
 * eviction + tail semantics can be tested with plain JUnit on the
 * JVM. This subclass adds a [MutableStateFlow] that ticks whenever
 * the buffer's snapshot changes, so a Compose observer can:
 *
 *   val log = (LocalContext.current.applicationContext as HandyApplication)
 *       .reactiveRingBuffer
 *   val lines by log.snapshotFlow.collectAsState()
 *
 * without polling. The wrapper emits the post-mutation snapshot via
 * [tick], so consumers see ordered, oldest-first content.
 *
 * Thread-safety: the [lock] `synchronized(lock) { ... }` block keeps
 * [RingBufferLog.append]/[RingBufferLog.clear] mutation + the StateFlow
 * publish atomic from the caller's perspective. The base
 * [RingBufferLog] is already internally synchronized for the JVM
 * ArrayDeque, but we re-lock here to publish the snapshot in the same
 * critical section so a reader can never observe a tick whose value is
 * later than the buffer.
 *
 * @param maxLines forwarded to [RingBufferLog.maxLines].
 */
/**
 * Invariant: Do NOT add a private `Any()` lock field to this class —
 * the subclass must share the inherited [com.handy.app.util.RingBufferLog.lock]
 * via `synchronized(lock)`. Drift here is the failure mode the
 * single-monitor refactor exists to prevent.
 */
final class ReactiveRingBufferLog(maxLines: Int = RingBufferLog.MAX_LINES_DEFAULT) :
    RingBufferLog(maxLines) {

    /** Snapshot/tail mutators reuse the SAME monitor as [RingBufferLog.lock] —
     *  this collapses the prior two-monitor dance into a single acquire,
     *  which means a concurrent [snapshot] or [tail] read can NEVER observe a
     *  transient half-state because both writes (base ArrayDeque mutation +
     *  subclass StateFlow publish) happen under the same lock.
     *  See also [RingBufferLog.lock]. */
    private val _snapshotFlow: MutableStateFlow<List<String>> = MutableStateFlow(
        snapshot(),
    )
    /** Compose observer hook. Emits the full oldest-first snapshot on
     *  every append / clear. Read-mostly callers should use [tailFlow]
     *  if they only care about the newest N lines. */
    val snapshotFlow: StateFlow<List<String>> = _snapshotFlow.asStateFlow()

    private val _tailFlow: MutableStateFlow<List<String>> = MutableStateFlow(
        tail(50),
    )
    /** Last 50 lines, oldest-first. Updates in lockstep with
     *  [snapshotFlow]. */
    val tailFlow: StateFlow<List<String>> = _tailFlow.asStateFlow()

    /**
     * Append [line] and emit the new snapshot + tail atomically.
     *
     * Single-monitor inherited from [com.handy.app.util.RingBufferLog.lock].
     * Re-entrant because all callers enter the same `Any()` mutex; a single
     * `synchronized(lock)` block spans the ArrayDeque mutation and the
     * `_snapshotFlow` / `_tailFlow` publish, so observers see either
     * pre- or post-mutation state — never a torn read. This replaces
     * the Sprint 28 v3 reviewer's two-monitor dance concern: the
     * subclass used to capture its own monitor and re-enter the base
     * monitor via `super.append`, which left a window where a direct
     * read of [RingBufferLog.snapshot]/[RingBufferLog.tail] could
     * observe a post-mutation, pre-publish state. With the inherited
     * mutex, that gap is closed.
     */
    override fun append(line: String) {
        synchronized(lock) {
            super.append(line)
            _snapshotFlow.value = snapshot()
            _tailFlow.value = tail(50)
        }
    }

    /** Drop all entries and emit the empty state atomically. */
    override fun clear() {
        synchronized(lock) {
            super.clear()
            _snapshotFlow.value = emptyList()
            _tailFlow.value = emptyList()
        }
    }

    companion object {
        /** Tail depth for [tailFlow]. Mirrors the most common Debug
         *  panel use case (last 50 lines). LiveLogViewer can call
         *  [RingBufferLog.tail] directly if it wants a different depth. */
        const val TAIL_DEPTH: Int = 50
    }
}
