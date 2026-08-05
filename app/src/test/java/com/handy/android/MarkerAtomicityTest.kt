package com.handy.android

import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Plain-JVM concurrency coverage for the atomic marker-file writes that the
 * main process and the isolated IME process share: a reader must never observe
 * a partially-written marker — only a complete value (the old one, the new one,
 * or none on first creation).
 *
 * Port of `MarkerAtomicityTest.java` from android_transcribe_app. Large distinct
 * values make any torn read (truncated/mixed content) detectable: with 20 KB
 * payloads a partial read would be caught.
 */
class MarkerAtomicityTest {

    private lateinit var tempDirectory: File

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("marker-atomic").toFile()
    }

    @After
    fun tearDown() {
        if (tempDirectory.exists()) {
            tempDirectory.listFiles()?.forEach { it.delete() }
            tempDirectory.delete()
        }
    }

    @Test
    fun concurrentWritersNeverProducePartialReads() {
        val a = StringBuilder()
        val b = StringBuilder()
        for (i in 0 until 20000) {
            a.append(('a' + i % 26))
            b.append(('z' - i % 26))
        }
        val values = listOf(a.toString(), b.toString(), "value-c")
        val valid = values.toSet()

        val start = CountDownLatch(1)
        val violation = AtomicReference<String?>(null)

        val writers = values.map { value ->
            Thread({
                try {
                    start.await()
                } catch (_: InterruptedException) {
                }
                var i = 0
                while (i < 150 && violation.get() == null) {
                    MarkerFileHelper.writeStringToFile(tempDirectory, "marker.txt", value)
                    i++
                }
            }, "writer-${value[0]}").also { it.start() }
        }

        val reader = Thread({
            try {
                start.await()
            } catch (_: InterruptedException) {
            }
            var i = 0
            while (i < 10000 && violation.get() == null) {
                val s = MarkerFileHelper.readStringFromFile(tempDirectory, "marker.txt", "")
                // Empty is allowed only on first creation; anything else must be
                // one of the complete written values.
                if (s.isNotEmpty() && s !in valid) {
                    violation.set(if (s.length > 60) s.substring(0, 60) + "…" else s)
                    return@Thread
                }
                i++
            }
        }, "reader")
        reader.start()

        start.countDown()
        writers.forEach { it.join(30000) }
        reader.join(30000)

        assertNull("reader observed a partial/corrupt marker value: ${violation.get()}", violation.get())
        assertTrue("all writer threads must finish", writers.none { it.isAlive })
    }
}
