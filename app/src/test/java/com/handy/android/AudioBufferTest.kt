package com.handy.android

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioBufferTest {
    @Test
    fun drainReturnsSamplesInOrderAndClearsBuffer() {
        val buffer = AudioBuffer()
        buffer.append(floatArrayOf(0.1f, 0.2f, 0.3f))

        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), buffer.drain(), 0.0001f)
        assertEquals(0, buffer.size())
    }

    @Test
    fun boundedDrainKeepsNewestSamples() {
        val buffer = AudioBuffer()
        buffer.append(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))

        assertArrayEquals(floatArrayOf(0.3f, 0.4f), buffer.drain(2), 0.0001f)
        assertEquals(0, buffer.size())
    }
}
