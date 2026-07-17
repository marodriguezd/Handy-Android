package com.handy.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Unit tests for [RecordingRepository] + [floatArrayToPcm16].
 *
 * Pure JVM (no Robolectric). The in-memory [InMemoryAudioStorageBackend]
 * stub satisfies the [AudioStorageBackend] contract without ever
 * touching the filesystem, so the WAV-header and ring-buffer eviction
 * logic is fully testable on CI runners.
 *
 * Coverage (11 tests):
 *  1. start → push → stop returns a finalized path with WAV magic intact.
 *  2. push without start is a silent no-op (does not crash, no file).
 *  3. start while already recording abandons the prior file.
 *  4. stop without start returns null gracefully.
 *  5. WAV header after finalize declares the correct data length in
 *     little-endian (RIFF chunk size = dataLen + 36).
 *  6. Sequential recordings produce uniquely-named files.
 *  7. Disk-full IO failure abandons the active file but does not crash.
 *  8. Dual-write disabled short-circuits start/push/stop to no-ops.
 *  9. floatArrayToPcm16 saturates + clamps out-of-range floats.
 * 10. Eviction drops oldest files when total size exceeds the cap.
 * 11. (Sprint 25a) Start → stop with ZERO frames produces a valid
 *     44-byte WAV; calling stopRecording twice in a row returns null
 *     on the second call (idempotency check).
 */
class RecordingRepositoryTest {

    @Test
    fun `start push stop returns a finalized path with WAV header intact`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        val repo = RecordingRepository(backend, isDualWriteEnabled = true, maxStorageBytes = 10_000_000L)

        val startPath = repo.startRecording(sessionTimestamp = 1_700_000_000L)
        assertNotNull("startRecording must return a path when dual-write enabled", startPath)
        repo.pushFloatArrayFrames(FloatArray(160) { 0.0f })
        repo.pushFloatArrayFrames(FloatArray(160) { 0.0f })
        val stopPath = repo.stopRecording()
        assertEquals(startPath, stopPath)

        val bytes = backend.readAllBytes(stopPath!!)
        assertEquals(
            "WAV file must be 44 header bytes + 2 pushes of 160 frames each",
            44 + 160 * 2 + 160 * 2,
            bytes.size.toLong(),
        )
        // RIFF magic at offset 0
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        // WAVE at offset 8
        assertEquals('W'.code.toByte(), bytes[8])
        assertEquals('V'.code.toByte(), bytes[10])
        // fmt  at offset 12 (note trailing space)
        assertEquals('f'.code.toByte(), bytes[12])
        assertEquals('t'.code.toByte(), bytes[14])
        assertEquals(' '.code.toByte(), bytes[15])
        // data at offset 36
        assertEquals('d'.code.toByte(), bytes[36])
        assertEquals('a'.code.toByte(), bytes[37])
    }

    @Test
    fun `pushAudio without start is a silent no-op`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        val repo = RecordingRepository(backend, isDualWriteEnabled = true)
        // No startRecording call; push must not crash and must not create any file.
        repo.pushFloatArrayFrames(FloatArray(160) { 0.5f })
        assertTrue(
            "Backend must remain empty when pushAudio has no active recording",
            backend.listAll().isEmpty(),
        )
    }

    @Test
    fun `start while already recording abandons the prior file and replaces it`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        val repo = RecordingRepository(backend, isDualWriteEnabled = true)
        val firstPath = repo.startRecording(sessionTimestamp = 1)
        repo.pushFloatArrayFrames(FloatArray(160) { 0.0f })
        val secondPath = repo.startRecording(sessionTimestamp = 2)
        assertNotNull(firstPath)
        assertNotNull(secondPath)
        assertFalse("first recording must be abandoned after second start", firstPath == secondPath)
        // Backend should only contain the second recording now.
        val remaining = backend.listAll()
        assertEquals(1, remaining.size)
        assertTrue(remaining.containsKey(secondPath))
    }

    @Test
    fun `stop without start returns null gracefully`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        val repo = RecordingRepository(backend, isDualWriteEnabled = true)
        assertNull(repo.stopRecording())
    }

    @Test
    fun `WAV header after finalize declares the correct data length in little-endian`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        val repo = RecordingRepository(backend, isDualWriteEnabled = true)
        val path = repo.startRecording(sessionTimestamp = 99)
        repo.pushFloatArrayFrames(FloatArray(160) { 0.0f })  // 320 bytes PCM
        repo.stopRecording()

        val bytes = backend.readAllBytes(path!!)
        // Read dataLen from offset 40..43 (4-byte little-endian int).
        val intLE = (bytes[40].toInt() and 0xff) or
            ((bytes[41].toInt() and 0xff) shl 8) or
            ((bytes[42].toInt() and 0xff) shl 16) or
            ((bytes[43].toInt() and 0xff) shl 24)
        assertEquals(320, intLE)

        // RIFF chunk size = dataLen + 36.
        val riffLen = (bytes[4].toInt() and 0xff) or
            ((bytes[5].toInt() and 0xff) shl 8) or
            ((bytes[6].toInt() and 0xff) shl 16) or
            ((bytes[7].toInt() and 0xff) shl 24)
        assertEquals(320 + 36, riffLen)
    }

    @Test
    fun `sequential recordings produce uniquely-named files`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        val repo = RecordingRepository(backend, isDualWriteEnabled = true)
        val paths = (1..3).map { i ->
            val p = repo.startRecording(sessionTimestamp = 100L + i)
            repo.pushFloatArrayFrames(FloatArray(80) { 0.0f })
            repo.stopRecording()
            p
        }
        assertEquals(3, paths.toSet().size)
        assertEquals(3, backend.listAll().size)
    }

    @Test
    fun `disk full IO failure abandons the active file but does not crash`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        backend.failOnNextWrite = true
        val repo = RecordingRepository(backend, isDualWriteEnabled = true)

        val startPath = repo.startRecording(sessionTimestamp = 5)
        assertNotNull(startPath)

        // First push triggers the injected IOException; repo must
        // abandon the file (NOT propagate the exception).
        repo.pushFloatArrayFrames(FloatArray(160) { 0.0f })

        val newPath = repo.startRecording(sessionTimestamp = 6)
        assertNotNull(newPath)

        assertFalse(
            "Abandoned file must not remain after disk-full IO failure",
            backend.listAll().containsKey(startPath),
        )
        assertTrue(
            "Fresh recording slot must exist after the previous one was abandoned",
            backend.listAll().containsKey(newPath),
        )
    }

    @Test
    fun `dualWrite disabled short-circuits start push and stop to null noop`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        val repo = RecordingRepository(backend, isDualWriteEnabled = false, maxStorageBytes = 1024L)

        assertNull(repo.startRecording())
        repo.pushFloatArrayFrames(FloatArray(160) { 0.0f })
        assertNull(repo.stopRecording())

        assertTrue(
            "No file must exist when dual-write is disabled",
            backend.listAll().isEmpty(),
        )
    }

    @Test
    fun `floatArrayToPcm16 saturates and clamps and writes little-endian 16-bit PCM`() {
        val frames = floatArrayOf(-1.5f, -1.0f, 0.0f, 0.5f, 1.0f, 1.7f)
        val pcm = floatArrayToPcm16(frames)

        assertEquals(frames.size * 2, pcm.size)

        // Frame → byte-pair mapping (each Float32 → 2 little-endian bytes).
        // Each Float32 in [-1.0, +1.0] maps to the integer in
        // [-32767, +32767] via `floor(sample * 32767)`. The asymmetric
        // range convention drops one code on the negative side: -32768
        // (Short.MIN_VALUE) is unreachable from any Float input under
        // this formula. A symmetric mapping would need a separate
        // `(-sample * 32768)` branch which we deliberately omit to keep
        // the implementation single-pass.
        //
        //   frame 0 (-1.5) → bytes  0, 1 → 0x01, 0x80  (-32767 after clamp to -1.0)
        //   frame 1 (-1.0) → bytes  2, 3 → 0x01, 0x80  (-32767)
        //   frame 2 ( 0.0) → bytes  4, 5 → 0x00, 0x00  ( 0)
        //   frame 3 ( 0.5) → bytes  6, 7 → 0xff, 0x3f  (+16383)
        //   frame 4 ( 1.0) → bytes  8, 9 → 0xff, 0x7f  (+32767 Short.MAX_VALUE)
        //   frame 5 ( 1.7) → bytes 10,11 → 0xff, 0x7f  (clamped to +1.0 → MAX_VALUE)
        assertEquals(0x01.toByte(), pcm[0])    // -1.5 → -32767 low byte
        assertEquals(0x80.toByte(), pcm[1])    // -1.5 → -32767 high byte
        assertEquals(0x01.toByte(), pcm[2])    // -1.0 → -32767 low byte
        assertEquals(0x80.toByte(), pcm[3])    // -1.0 → -32767 high byte
        assertEquals(0x00.toByte(), pcm[4])    //  0.0 → 0 low byte
        assertEquals(0x00.toByte(), pcm[5])    //  0.0 → 0 high byte
        assertEquals(0xff.toByte(), pcm[8])    //  1.0 → MAX_VALUE low byte
        assertEquals(0x7f.toByte(), pcm[9])    //  1.0 → MAX_VALUE high byte
        assertEquals(0xff.toByte(), pcm[10])   //  1.7 saturated → MAX_VALUE low byte
        assertEquals(0x7f.toByte(), pcm[11])   //  1.7 saturated → MAX_VALUE high byte
    }

    @Test
    fun `eviction drops oldest files when total size exceeds the cap`() = runBlocking {
        val backend = InMemoryAudioStorageBackend()
        // Tight cap (1024 bytes) so the 360-byte recordings (44 + 320
        // of PCM data rounded up via finalize) eventually exceed it.
        val repo = RecordingRepository(backend, isDualWriteEnabled = true, maxStorageBytes = 1024L)
        repeat(6) { i ->
            repo.startRecording(sessionTimestamp = 1000L + i)
            repo.pushFloatArrayFrames(FloatArray(160) { 0.0f })
            repo.stopRecording()
        }
        val remaining = backend.listAll()
        // Eviction runs at push-time; the contract is at least one file
        // was removed given that we completed 6 recordings under a 1KB
        // cap with a single 1KB cap.
        assertTrue(
            "eviction must have removed at least 1 file (had 6, cap=1024)",
            remaining.size < 6,
        )
    }

    @Test
    fun `start then stop with zero frames produces a valid 44-byte WAV`() = runBlocking {
        // Sprint 25a contract: when `pushFloatArrayFrames` is NOT yet
        // wired (waiting on Sprint 25b's Kotlin frame-subscribe
        // callback or Rust-side dual-write), startRecording+stopRecording
        // must still produce a valid WAV file ready to be passed to
        // `nativeSaveHistory(...)`. The file is exactly 44 bytes (the
        // placeholder header) and remains playable by AudioPlayerBar
        // (silence in this case).
        val backend = InMemoryAudioStorageBackend()
        val repo = RecordingRepository(backend, isDualWriteEnabled = true)
        val startPath = repo.startRecording(sessionTimestamp = 1_700_000_000L)
        assertNotNull("startRecording must return a path when dual-write enabled", startPath)
        // No pushes whatsoever — simulating Sprint 25a's not-yet-wired
        // audio pipeline.
        val stopPath = repo.stopRecording()
        assertEquals(startPath, stopPath)
        assertEquals(
            "zero-frame recording must finalize to a valid 44-byte WAV placeholder",
            44L, backend.fileSize(startPath!!),
        )
        // Idempotency: a second stop with no session open must return
        // null (this is what protects us from double-finalize on the
        // race where `stopRecording` fires before the `startRecording`
        // coroutine has set `pendingRecordingPath`).
        assertNull(
            "second consecutive stopRecording must return null",
            repo.stopRecording(),
        )
    }

    /** Local alias so tests read like `@runBlocking`-style sequences. */
    private fun runBlocking(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
}

/**
 * In-memory [AudioStorageBackend] used by [RecordingRepositoryTest].
 * Keeps each named WAV file as a `ByteArrayOutputStream` keyed by
 * path. `finalizeHeader` rewrites the 44-byte placeholder with the
 * canonical RIFF/WAVE/fmt/data chunk descriptors and the supplied
 * little-endian sizes.
 *
 * `failOnNextWrite` is a one-shot, post-reset flag that lets tests
 * exercise the disk-full IO failure path without throwing an
 * `IOException` from the start.
 *
 * `listAll()` exposes the live map of path → ByteArrayOutputStream so
 * assertions can verify file creation/deletion without going through
 * the [AudioStorageBackend] interface (which only carries descriptors).
 */
internal class InMemoryAudioStorageBackend : AudioStorageBackend {
    private val files = mutableMapOf<String, ByteArrayOutputStream>()

    /** One-shot disk-full simulator. Checked on every [writeBytes]. */
    var failOnNextWrite: Boolean = false

    override fun createWavFile(filename: String): String {
        val path = "/mem/$filename"
        files[path] = ByteArrayOutputStream().apply { write(ByteArray(44)) }
        return path
    }

    override fun writeBytes(path: String, bytes: ByteArray) {
        if (failOnNextWrite) {
            failOnNextWrite = false
            throw IOException("simulated disk full")
        }
        files[path]?.write(bytes) ?: error("writeBytes on unknown path $path")
    }

    override fun finalizeHeader(path: String, totalAudioLen: Long, totalDataLen: Long) {
        if (!files.containsKey(path)) error("finalizeHeader on unknown path $path")
        val current = files[path]!!.toByteArray()
        val updated = ByteArray(44)
        writeRiffHeader(updated, totalDataLen)
        files[path] = ByteArrayOutputStream().apply {
            write(updated)
            if (current.size > 44) write(current, 44, current.size - 44)
        }
    }

    override fun listWavFiles(): List<AudioFileDescriptor> = files.map { (p, baos) ->
        AudioFileDescriptor(path = p, lastModified = 0L, size = baos.size().toLong())
    }

    override fun deleteFile(path: String): Boolean = files.remove(path) != null

    override fun fileSize(path: String): Long = files[path]?.size()?.toLong() ?: 0L

    fun listAll(): Map<String, ByteArrayOutputStream> = files.toMap()

    fun readAllBytes(path: String): ByteArray =
        files[path]?.toByteArray() ?: error("readAllBytes on unknown path $path")

    private fun writeRiffHeader(dest: ByteArray, totalDataLen: Long) {
        fun putIntLE(b: ByteArray, off: Int, v: Int) {
            b[off] = (v and 0xff).toByte()
            b[off + 1] = ((v shr 8) and 0xff).toByte()
            b[off + 2] = ((v shr 16) and 0xff).toByte()
            b[off + 3] = ((v shr 24) and 0xff).toByte()
        }
        putShortString(dest, 0, "RIFF")
        putIntLE(dest, 4, totalDataLen.toInt() + 36)
        putShortString(dest, 8, "WAVE")
        putShortString(dest, 12, "fmt ")
        putIntLE(dest, 16, 16)        // Subchunk1Size for PCM
        putIntLE(dest, 20, 1)         // AudioFormat = PCM
        putIntLE(dest, 24, 1)         // NumChannels (placeholder; tests only check RIFF/WAVE/data magic)
        putIntLE(dest, 28, 16_000)    // SampleRate placeholder
        putShortString(dest, 36, "data")
        putIntLE(dest, 40, totalDataLen.toInt())
    }

    private fun putShortString(b: ByteArray, off: Int, s: String) {
        for ((i, c) in s.withIndex()) b[off + i] = c.code.toByte()
    }
}
