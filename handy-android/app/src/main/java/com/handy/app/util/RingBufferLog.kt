package com.handy.app.util

/**
 * Sprint 28 — JVM-pure ring buffer for log capture.
 *
 * Used by the Debug panel's LiveLogViewer to tail the most recent N
 * log lines. Pure JVM (no Android dependency) so the test suite can
 * exercise eviction + tail semantics without Robolectric.
 *
 * Thread-safety: [append], [snapshot], [tail], [clear], and [size] are
 * conservative — each is `@Synchronized` on `this`. This is sufficient
 * for the Debug panel use case (lines logged at verbosity level, capped
 * at [MAX_LINES_DEFAULT] = 500). For production-grade throughput
 * (>10k lines/sec) the buffer would need a SPSC queue + epoch sequence
 * counter; that's deferred to a future Sprint if needed.
 *
 * @param maxLines maximum number of retained lines. Must be > 0.
 */
class RingBufferLog(private val maxLines: Int = MAX_LINES_DEFAULT) {

    init {
        require(maxLines > 0) { "maxLines must be > 0; got $maxLines" }
    }

    private val buf: ArrayDeque<String> = ArrayDeque()

    /** Append a single log line. Evicts the oldest when at capacity. */
    @Synchronized
    fun append(line: String) {
        if (buf.size >= maxLines) buf.removeFirst()
        buf.addLast(line)
    }

    /** Snapshot of the current contents, oldest first. */
    @Synchronized
    fun snapshot(): List<String> = buf.toList()

    /**
     * The most recent [n] lines, oldest-first within the slice.
     * If [n] is `>= size()` the entire buffer is returned. If [n] is
     * `<= 0` the empty list is returned.
     */
    @Synchronized
    fun tail(n: Int): List<String> {
        if (n <= 0) return emptyList()
        val size = buf.size
        return if (n >= size) {
            buf.toList()
        } else {
            buf.toList().subList(size - n, size)
        }
    }

    /** Drop all entries. */
    @Synchronized
    fun clear() {
        buf.clear()
    }

    /** Number of stored lines. */
    @Synchronized
    fun size(): Int = buf.size

    companion object {
        const val MAX_LINES_DEFAULT: Int = 500
    }
}
