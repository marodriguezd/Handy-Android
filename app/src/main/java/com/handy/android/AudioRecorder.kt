package com.handy.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
import kotlin.concurrent.thread

class AudioRecorder(
    private val context: Context,
    private val onSilenceAutoStop: (() -> Unit)? = null,
    private val onAmplitude: ((Float) -> Unit)? = null,
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val TAG = "HandyAudioRecorder"

        fun durationMs(samples: FloatArray): Long = samples.size.toLong() * 1_000L / SAMPLE_RATE
    }

    private val recording = AtomicBoolean(false)
    private val stateLock = Any()
    private val startLock = Any()
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private var vadDetector: SileroVadDetector? = null
    val buffer = AudioBuffer()

    val isRecording: Boolean
        get() = recording.get()

    fun start(enableVoiceActivityDetection: Boolean = false): Boolean = synchronized(startLock) {
        if (recording.get()) return@synchronized true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBuffer <= 0) return false

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minimumBuffer.coerceAtLeast(SAMPLE_RATE / 2),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        buffer.clear()
        vadDetector = if (enableVoiceActivityDetection) {
            runCatching { SileroVadDetector(context) }
                .onFailure { Log.w(TAG, "Voice activity detection unavailable", it) }
                .getOrNull()
        } else {
            null
        }
        synchronized(stateLock) {
            audioRecord = recorder
            recording.set(true)
        }
        recorder.startRecording()
        worker = thread(name = "handy-audio-recorder") {
            val chunk = ShortArray(SAMPLE_RATE / 10)
            val vadWindow = FloatArray(SileroVadDetector.WINDOW_SAMPLES)
            var vadWindowSize = 0
            var failedVadDetector: SileroVadDetector? = null
            try {
                while (recording.get()) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        buffer.append(chunk, read)
                        onAmplitude?.invoke(rms(chunk, read))
                        val vad = vadDetector
                        if (vad != null) {
                            for (index in 0 until read) {
                                vadWindow[vadWindowSize++] = chunk[index] / 32768.0f
                                if (vadWindowSize == vadWindow.size) {
                                    val shouldAutoStop = runCatching {
                                        vad.process(vadWindow).shouldAutoStop
                                    }.onFailure {
                                        Log.w(TAG, "Disabling voice activity detection after inference failure", it)
                                        failedVadDetector = vad
                                        vadDetector = null
                                    }.getOrDefault(false)
                                    if (shouldAutoStop) {
                                        recording.set(false)
                                        runCatching { onSilenceAutoStop?.invoke() }
                                        break
                                    }
                                    vadWindowSize = 0
                                }
                            }
                        }
                    }
                }
            } finally {
                recorder.stopSafely()
                recorder.release()
                vadDetector?.close()
                failedVadDetector?.close()
                vadDetector = null
                synchronized(stateLock) {
                    if (audioRecord === recorder) audioRecord = null
                }
            }
        }
        true
    }

    fun stop(): FloatArray = synchronized(startLock) {
        val recorderToStop: AudioRecord?
        val threadToJoin: Thread?
        synchronized(stateLock) {
            recording.set(false)
            recorderToStop = audioRecord
            threadToJoin = worker
        }
        try {
            recorderToStop?.stop()
        } catch (_: IllegalStateException) {
            // The worker may have completed between the state snapshot and stop().
        }
        if (threadToJoin !== Thread.currentThread()) {
            threadToJoin?.join(2_000)
        }
        synchronized(stateLock) {
            if (worker === threadToJoin) worker = null
        }
        buffer.snapshot()
    }

    fun release() {
        stop()
    }

    private fun rms(samples: ShortArray, count: Int): Float {
        var sum = 0.0
        for (index in 0 until count.coerceIn(0, samples.size)) {
            val normalized = samples[index] / 32768.0
            sum += normalized * normalized
        }
        return if (count > 0) kotlin.math.sqrt(sum / count).toFloat().coerceIn(0f, 1f) else 0f
    }

    private fun AudioRecord.stopSafely() {
        try {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
        } catch (_: IllegalStateException) {
            // The recorder may already have been stopped during service teardown.
        }
    }

}
