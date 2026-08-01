package com.handy.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AudioRecorder(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 16_000

        fun durationMs(samples: FloatArray): Long = samples.size.toLong() * 1_000L / SAMPLE_RATE
    }

    private val recording = AtomicBoolean(false)
    private val stateLock = Any()
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    val buffer = AudioBuffer()

    val isRecording: Boolean
        get() = recording.get()

    fun start(): Boolean {
        if (recording.get()) return true
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
        synchronized(stateLock) {
            audioRecord = recorder
            recording.set(true)
        }
        recorder.startRecording()
        worker = thread(name = "handy-audio-recorder") {
            val chunk = ShortArray(SAMPLE_RATE / 10)
            try {
                while (recording.get()) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read > 0) buffer.append(chunk, read)
                }
            } finally {
                recorder.stopSafely()
                recorder.release()
                synchronized(stateLock) {
                    if (audioRecord === recorder) audioRecord = null
                }
            }
        }
        return true
    }

    fun stop(): FloatArray {
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
        return buffer.snapshot()
    }

    fun release() {
        stop()
    }

    private fun AudioRecord.stopSafely() {
        try {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
        } catch (_: IllegalStateException) {
            // The recorder may already have been stopped during service teardown.
        }
    }
}
