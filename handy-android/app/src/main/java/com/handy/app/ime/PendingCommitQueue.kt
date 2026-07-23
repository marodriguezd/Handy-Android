package com.handy.app.ime

import android.util.Log

/**
 * Hold-and-forward buffer for transcribed text waiting on a live input connection.
 * Retains text when focus is temporarily lost or input connection is busy/rejected,
 * and flushes automatically on focus gain or input view start.
 */
class PendingCommitQueue {

    companion object {
        private const val TAG = "PendingCommitQueue"
        const val PENDING_TEXT_MAX_AGE_MS = 30_000L
    }

    @Volatile
    private var text: String? = null
    @Volatile
    private var timestampMs: Long = 0L

    @Synchronized
    fun set(text: String) {
        this.text = text
        this.timestampMs = System.currentTimeMillis()
    }

    @Synchronized
    fun isEmpty(): Boolean = text == null

    @Synchronized
    fun peek(): String? = text

    @Synchronized
    fun timestampMs(): Long = timestampMs

    @Synchronized
    fun ageMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (text == null) return 0L
        return nowMs - timestampMs
    }

    @Synchronized
    fun clear() {
        this.text = null
        this.timestampMs = 0L
    }

    enum class Result {
        SUCCESS,
        NO_INPUT,
        FAILED_KEEP
    }

    fun interface CommitFn {
        fun apply(text: String): Result
    }

    enum class FlushOutcome {
        EMPTY,
        EXPIRED,
        SUCCESS,
        NO_INPUT,
        FAILED_KEEP
    }

    @Synchronized
    fun flush(
        committer: CommitFn,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = PENDING_TEXT_MAX_AGE_MS
    ): FlushOutcome {
        val currentText = text ?: return FlushOutcome.EMPTY
        val age = nowMs - timestampMs
        if (age > maxAgeMs) {
            Log.w(TAG, "flush: dropping ${age}ms-old pending text (TTL ${maxAgeMs}ms exceeded)")
            clear()
            return FlushOutcome.EXPIRED
        }
        return when (val r = committer.apply(currentText)) {
            Result.SUCCESS -> {
                clear()
                FlushOutcome.SUCCESS
            }
            Result.NO_INPUT -> FlushOutcome.NO_INPUT
            Result.FAILED_KEEP -> {
                Log.w(TAG, "flush: commit returned FAILED_KEEP (age=${age}ms); preserving queue without advancing TTL")
                FlushOutcome.FAILED_KEEP
            }
        }
    }
}
