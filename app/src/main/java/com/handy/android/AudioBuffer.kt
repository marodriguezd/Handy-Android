package com.handy.android

import kotlin.math.max

/** Thread-safe PCM buffer containing normalized mono samples in [-1, 1]. */
class AudioBuffer {
    private val samples = ArrayList<Float>()

    @Synchronized
    fun append(values: ShortArray, count: Int) {
        val safeCount = count.coerceIn(0, values.size)
        for (index in 0 until safeCount) {
            samples += values[index] / 32768.0f
        }
    }

    @Synchronized
    fun append(values: FloatArray) {
        samples.ensureCapacity(samples.size + values.size)
        values.forEach { samples += it.coerceIn(-1.0f, 1.0f) }
    }

    @Synchronized
    fun snapshot(): FloatArray = samples.toFloatArray()

    /** Returns buffered samples and clears them atomically for streaming consumers. */
    @Synchronized
    fun drain(maxSamples: Int = Int.MAX_VALUE): FloatArray {
        val limit = maxSamples.coerceAtLeast(1)
        val start = (samples.size - limit).coerceAtLeast(0)
        val result = samples.subList(start, samples.size).toFloatArray()
        samples.clear()
        return result
    }

    @Synchronized
    fun clear() {
        samples.clear()
    }

    @Synchronized
    fun size(): Int = samples.size

    @Synchronized
    fun durationSeconds(sampleRate: Int = AudioRecorder.SAMPLE_RATE): Float =
        samples.size.toFloat() / max(sampleRate, 1)
}
