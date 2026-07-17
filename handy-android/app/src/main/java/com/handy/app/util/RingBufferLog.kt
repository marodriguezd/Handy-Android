package com.handy.app.util

/**
 * Sprint 28 — JVM-pure ring buffer for log capture.
 *
 * Used by the Debug panel's LiveLogViewer to tail the most recent N
 * log lines. Pure JVM (no Android dependency) so the test suite can
 * exercise eviction + tail semantics without Robolectric.
 *
 * Sprint 28b thread-safety hardening: the previous release used
 * per-method `@Synchronized`, which compiles to independent monitor
 * acquires per call. A concurrent [append] (two ops: `removeFirst`
 * then `addLast`) could transiently expose a half-evicted state to a
 * reader that called [snapshot]/[tail] between those two operations.
 *
 * Mitigation: a single private [lock] is acquired around the entire
 * [append] mutation. [snapshot], [tail], [clear], and [size] each
 * grab the same lock so observers see either the pre-append or
 * post-append state — never the in-between. The compose-side
 * [ReactiveRingBufferLog] wrapper adds a higher-level StateFlow tick
 * for LiveLogViewer observers; the JVM tests below verify the base
 * class invariants.
 *
 * @param maxLines maximum number of retained lines. Must be > 0.
 */
open class RingBufferLog(private val maxLines: Int = MAX_LINES_DEFAULT) {

    init {
        require(maxLines > 0) { "maxLines must be > 0; got $maxLines" }
    }

    protected val lock: Any = Any()
    private val buf: ArrayDeque<String> = ArrayDeque()

    /**
     * Append a single log line. Evicts the oldest when at capacity.
     * Atomic w.r.t. [snapshot]/[tail]/[clear]/[size] via [lock].
     *
     * Subclass contract: an override MUST continue to acquire [lock]
     * for the entire mutation + publish path, AND MUST call
     * `super.append(line)` so the ArrayDeque mutation remains
     * atomic. Bypassing [lock] or skipping `super` lets two threads
     * observe a transient state where the buffer has been half-evicted.
     *
     * @see com.handy.app.util.ReactiveRingBufferLog for the canonical
     *   sub-class implementation that spans the base ArrayDeque mutation
     *   and the subclass StateFlow publish under the same inherited lock.
     *   Subclasses MUST use `synchronized(lock)` (the inherited `protected`
     *   field) — declaring a private `Any()` and synchronizing on it
     *   silently defeats the contract.
     */
    open fun append(line: String) {
    open fun append(line: String) {
        synchronized(lock) {
            if (buf.size >= maxLines) buf.removeFirst()
            buf.addLast(line)
        }
    }

    /** Snapshot of the current contents, oldest first. */
    fun snapshot(): List<String> = synchronized(lock) { buf.toList() }

    /**
     * The most recent [n] lines, oldest-first within the slice.
     * If [n] is `>= size()` the entire buffer is returned. If [n] is
     * `<= 0` the empty list is returned.
     */
    fun tail(n: Int): List<String> {
        if (n <= 0) return emptyList()
        return synchronized(lock) {
            val size = buf.size
            if (n >= size) {
                buf.toList()
            } else {
                buf.toList().subList(size - n, size)
            }
        }
    }

    /**
     * Drop all entries.
     *
     * Subclass contract: an override MUST acquire [lock] AND call
     * `super.clear()` so the underlying ArrayDeque is mutated
     * atomically w.r.t. any concurrent [append]/[snapshot] call.
     * Same `synchronized(lock)` invariant as [append] — the inherited
     * `protected` lock MUST be reused; do NOT declare a private `Any()`
     * and synchronize on that, or the contract is silently defeated.
     */
    open fun clear() {
    open fun clear() {
        synchronized(lock) { buf.clear() }
    }

    /** Number of stored lines. */
    fun size(): Int = synchronized(lock) { buf.size }

    companion object {
        const val MAX_LINES_DEFAULT: Int = 500
    }
}
