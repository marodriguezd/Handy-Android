package com.handy.app.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Storage abstraction for [RecordingRepository]. Splitting I/O behind an
 * interface lets JVM tests substitute an in-memory backend so the
 * WAV-header logic and ring-buffer eviction are testable without ever
 * touching real disk (see
 * `app/src/test/java/com/handy/app/audio/RecordingRepositoryTest.kt`).
 *
 * Production wires [FileAudioStorageBackend] (real filesystem, scoped
 * to `Context.getExternalFilesDir(null)/history_audio/`).
 */
interface AudioStorageBackend {
    fun createWavFile(filename: String): String
    fun writeBytes(path: String, bytes: ByteArray)
    fun finalizeHeader(path: String, totalAudioLen: Long, totalDataLen: Long)
    fun listWavFiles(): List<AudioFileDescriptor>
    fun deleteFile(path: String): Boolean
    fun fileSize(path: String): Long
}

/** Lightweight file descriptor returned by [AudioStorageBackend.listWavFiles]. */
data class AudioFileDescriptor(
    val path: String,
    val lastModified: Long,
    val size: Long,
)

/**
 * Filesystem-backed [AudioStorageBackend]. Stores WAV files under
 * `<getExternalFilesDir>/history_audio/`, which:
 *   - requires no manifest permission,
 *   - is auto-removed by Android if the user uninstalls the app,
 *   - is scoped-storage compatible on API 26+,
 *   - survives reboots.
 *
 * Blocks forever should never happen because we hold a Mutex in the
 * repository. Uncaught IOExceptions are surfaced to the caller via
 * `runCatching` chains — the repository converts them to "abandon
 * this file, keep the engine running" rather than crashing.
 */
class FileAudioStorageBackend(private val context: Context) : AudioStorageBackend {

    private val audioDir: File by lazy {
        File(context.getExternalFilesDir(null), "history_audio").apply { mkdirs() }
    }

    override fun createWavFile(filename: String): String {
        val file = File(audioDir, filename)
        // Pre-write a 44-byte placeholder header with zero sizes so
        // successive [writeBytes] calls can append raw PCM without
        // truncating the placeholder. [finalizeHeader] overwrites the
        // first 44 bytes with the real chunk sizes when the user stops
        // recording.
        val placeholderHeader = ByteArray(44)
        file.writeBytes(placeholderHeader)
        return file.absolutePath
    }

    override fun writeBytes(path: String, bytes: ByteArray) {
        File(path).appendBytes(bytes)
    }

    override fun finalizeHeader(path: String, totalAudioLen: Long, totalDataLen: Long) {
        RandomAccessFile(File(path), "rw").use { raf ->
            // RIFF chunk descriptor (12 bytes)
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeIntLE(totalDataLen.toInt() + 36)
            raf.writeBytes("WAVE")
            // fmt sub-chunk (24 bytes)
            raf.writeBytes("fmt ")
            raf.writeIntLE(16)                 // Subchunk1Size for PCM
            raf.writeShortLE(1.toShort())     // AudioFormat = PCM
            raf.writeShortLE(1.toShort())     // NumChannels = 1 (mono)
            raf.writeIntLE(SAMPLE_RATE_HZ)
            raf.writeIntLE(SAMPLE_RATE_HZ * BYTES_PER_SAMPLE * CHANNELS) // ByteRate
            raf.writeShortLE((BYTES_PER_SAMPLE * CHANNELS).toShort())    // BlockAlign
            raf.writeShortLE(16.toShort())    // BitsPerSample
            // data sub-chunk (8 bytes)
            raf.writeBytes("data")
            raf.writeIntLE(totalAudioLen.toInt())
        }
    }

    override fun listWavFiles(): List<AudioFileDescriptor> =
        audioDir.listFiles().orEmpty().map { f ->
            AudioFileDescriptor(
                path = f.absolutePath,
                lastModified = f.lastModified(),
                size = f.length(),
            )
        }

    override fun deleteFile(path: String): Boolean = File(path).delete()

    override fun fileSize(path: String): Long = File(path).length()

    companion object {
        /** Sample rate written into the WAV header. Must match the
         *  resampler output in `handy-core/audio/frame_resampler.rs`. */
        const val SAMPLE_RATE_HZ: Int = 16_000
        const val BYTES_PER_SAMPLE: Int = 2   // 16-bit signed PCM
        const val CHANNELS: Int = 1           // mono
    }
}

/**
 * Persistent ring-buffer recorder that mirrors every audio frame fed by
 * the engine into a WAV file under
 * [FileAudioStorageBackend]'s directory. The written path is then
 * passed to [com.handy.app.bridge.EngineBridge.nativeSaveHistory] when
 * the user finalizes a dictation, and to `nativeRetryHistoryEntry`
 * when they hit Retry on a history card.
 *
 * Concurrency: a process-wide Mutex guards the (currentWavPath,
 * totalBytesWritten) tuple so multiple audio frames appended from
 * different coroutine contexts cannot interleave their byte writes.
 *
 * Migration: the [isDualWriteEnabled] flag gates the actual disk
 * writes. The flag exists so the Kotlin side can stop dual-writing
 * once the Rust side takes over persistence in a future Sprint 25+.
 * Production callers should read
 * [com.handy.app.SettingsStore.recordingDualWriteMode] at the moment
 * the repository is constructed (or via a thin factory in
 * `HandyApplication`); the repository does NOT pull from
 * `SettingsStore` directly so it stays JVM-testable without an
 * Android-context dependency.
 */
class RecordingRepository(
    private val storage: AudioStorageBackend,
    private val isDualWriteEnabled: Boolean = true,
    private val maxStorageBytes: Long = DEFAULT_MAX_STORAGE_BYTES,
) {

    private val mutex = Mutex()

    // Guarded by mutex.
    private var currentWavPath: String? = null
    private var totalBytesWritten: Long = 0L

    /**
     * Start a new recording session. Writes the WAV header placeholder
     * and returns the absolute path the engine should reference next
     * time it calls `EngineBridge.nativeSaveHistory`. If a previous
     * session was still open, the abandoned file is deleted locally —
     * the engine never sees its path because no finalize completes.
     *
     * Returns `null` when dual-write is disabled or the underlying
     * file creation fails (e.g. ENOSPC during write).
     */
    suspend fun startRecording(sessionTimestamp: Long = System.currentTimeMillis()): String? {
        if (!isDualWriteEnabled) return null
        return mutex.withLock {
            currentWavPath?.let { oldPath ->
                runCatching { storage.deleteFile(oldPath) }
            }
            val filename = "history_${sessionTimestamp}.wav"
            val path = runCatching { storage.createWavFile(filename) }
                .getOrElse {
                    Log.e(TAG, "createWavFile failed for $filename", it)
                    return@withLock null
                }
            currentWavPath = path
            totalBytesWritten = 44L  // placeholder header bytes
            path
        }
    }

    /**
     * Append PCM Float32 frames to the active recording. Frames are
     * converted to 16-bit signed little-endian PCM in-place. Conversions
     * exceeding [-1, 1] are clamped via `coerceIn` so a single out-of-
     * range microphone spike cannot wrap the PCM sample to garbage.
     *
     * Disk-full IOException: the file is abandoned (`currentWavPath`
     * set to null, MVCC check guards stale paths) so the engine keeps
     * going. Silent loss of one recording beats a crash inside the
     * realtime audio loop.
     */
    suspend fun pushFloatArrayFrames(frames: FloatArray) {
        if (!isDualWriteEnabled || frames.isEmpty()) return
        val snapshotPath = mutex.withLock { currentWavPath } ?: return
        val pcm = floatArrayToPcm16(frames)
        runCatching {
            storage.writeBytes(snapshotPath, pcm)
            mutex.withLock {
                // Defensive: only update if the path is still the
                // active recording (a concurrent stopRecording would
                // have changed `currentWavPath`). Recompute byte count
                // from the on-disk size rather than trusting a stale
                // priorBytes capture so the header-offset invariant
                // — `totalBytesWritten == fileSize` — is preserved
                // across the path-equality check.
                if (currentWavPath == snapshotPath) {
                    totalBytesWritten = storage.fileSize(snapshotPath)
                }
            }
            evictIfOverCap()
        }.onFailure { e ->
            Log.w(TAG, "pushAudio write failed for $snapshotPath; abandoning file", e)
            mutex.withLock {
                if (currentWavPath == snapshotPath) {
                    storage.deleteFile(snapshotPath)
                    currentWavPath = null
                    totalBytesWritten = 0L
                }
            }
        }
    }

    /**
     * Finalize the active recording: rewrite the WAV header with the
     * correct sizes and return the file path so the caller can pass
     * it to `EngineBridge.nativeSaveHistory`. Returns `null` when no
     * session was open or `finalizeHeader` failed.
     */
    suspend fun stopRecording(): String? {
        return mutex.withLock {
            val path = currentWavPath ?: return@withLock null
            val dataLen = (totalBytesWritten - 44L).coerceAtLeast(0L)
            runCatching { storage.finalizeHeader(path, dataLen, dataLen) }
                .onFailure {
                    Log.e(TAG, "finalizeHeader failed for $path", it)
                    storage.deleteFile(path)
                    currentWavPath = null
                    totalBytesWritten = 0L
                    return@withLock null
                }
            currentWavPath = null
            totalBytesWritten = 0L
            path
        }
    }

    /**
     * Drop the oldest WAV files when the directory's total size
     * exceeds [maxStorageBytes]. Called from [pushFloatArrayFrames]
     * so disk pressure is detected as data accumulates. O(N) walk of
     * the directory — fine because the recording count is bounded by
     * `maxStorageBytes / ~50MB/recording` ≈ 10 entries for the default
     * 500MB cap.
     */
    private fun evictIfOverCap() {
        val entries = storage.listWavFiles()
        val total = entries.sumOf { it.size }
        if (total <= maxStorageBytes) return
        val oldestFirst = entries.sortedBy { it.lastModified }
        var remaining = total
        for (file in oldestFirst) {
            if (remaining <= maxStorageBytes) return
            if (storage.deleteFile(file.path)) {
                remaining -= file.size
            }
        }
    }

    companion object {
        private const val TAG = "RecordingRepository"

        /** ~500 MB cap — covers ~10 typical 50MB Whisper recordings
         *  before eviction kicks in. */
        const val DEFAULT_MAX_STORAGE_BYTES: Long = 500L * 1024 * 1024
    }
}


/**
 * Pure helper: convert Float32 [-1, 1] frames to 16-bit signed
 * little-endian PCM bytes. Lives at file scope so the PCM-conversion
 * math is JVM-testable without instantiating the repository.
 *
 * Clamping is done implicitly by `coerceIn(-1f, 1f)` before the int
 * conversion — a single out-of-range microphone spike cannot wrap to
 * a different negative value when the *operator runs on the int range.
 */
internal fun floatArrayToPcm16(frames: FloatArray): ByteArray {
    val out = ByteArray(frames.size * 2)
    val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
    for (sample in frames) {
        val clamped = sample.coerceIn(-1f, 1f)
        val pcm = (clamped * Short.MAX_VALUE).toInt().toShort()
        bb.putShort(pcm)
    }
    return out
}

/**
 * Adds a little-endian int write to [RandomAccessFile]. We use this
 * to fill the WAV header in [FileAudioStorageBackend.finalizeHeader]
 * so the chunks render correctly when consumed by a standard
 * MediaPlayer (Sprint 24 [com.handy.app.ui.history.components.AudioPlayerBar]).
 */
private fun RandomAccessFile.writeIntLE(value: Int) {
    writeInt(Integer.reverseBytes(value))
}

private fun RandomAccessFile.writeShortLE(value: Short) {
    writeShort(java.lang.Short.reverseBytes(value).toInt())
}
