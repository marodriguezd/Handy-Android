package com.handy.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
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

        /** Stores normalized mono PCM as a standard 16-bit WAV in app-private storage. */
        fun writeWav(context: Context, samples: FloatArray): File {
            val directory = File(context.filesDir, "recordings").apply { mkdirs() }
            val target = File(directory, "recording-${System.currentTimeMillis()}.wav")
            FileOutputStream(target).use { output ->
                val dataSize = samples.size * Short.SIZE_BYTES
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(36 + dataSize)
                header.put("WAVEfmt ".toByteArray())
                header.putInt(16).putShort(1).putShort(1)
                header.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
                header.put("data".toByteArray()).putInt(dataSize).flip()
                output.write(header.array())
                val pcm = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
                samples.forEach { sample ->
                    pcm.putShort((sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
                }
                output.write(pcm.array())
            }
            return target
        }
    }

    private val recording = AtomicBoolean(false)
    private val stateLock = Any()
    private val startLock = Any()
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private var vadDetector: SileroVadDetector? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var stopDeadlineNanos = 0L
    val buffer = AudioBuffer()

    val isRecording: Boolean get() = recording.get()

    fun start(enableVoiceActivityDetection: Boolean = false): Boolean = synchronized(startLock) {
        if (recording.get()) return@synchronized true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false

        val minimumBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager = context.getSystemService(AudioManager::class.java)
            val deviceId = SettingsManager.inputDeviceId(context)
            if (deviceId != null) {
                recorder.setPreferredDevice(findInputDevice(deviceId))
            }
        }
        requestAudioFocus()
        buffer.clear()
        vadDetector = if (enableVoiceActivityDetection) {
            runCatching { SileroVadDetector(context) }
                .onFailure { Log.w(TAG, "Voice activity detection unavailable", it) }
                .getOrNull()
        } else null
        synchronized(stateLock) {
            audioRecord = recorder
            stopDeadlineNanos = 0L
            recording.set(true)
        }
        recorder.startRecording()
        worker = thread(name = "handy-audio-recorder") {
            val chunk = ShortArray(SAMPLE_RATE / 10)
            val vadWindow = FloatArray(SileroVadDetector.WINDOW_SAMPLES)
            var vadWindowSize = 0
            var failedVadDetector: SileroVadDetector? = null
            try {
                while (recording.get() || System.nanoTime() < synchronized(stateLock) { stopDeadlineNanos }) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read <= 0) continue
                    buffer.append(chunk, read)
                    onAmplitude?.invoke(rms(chunk, read))
                    val vad = vadDetector
                    if (vad != null && recording.get()) {
                        for (index in 0 until read) {
                            vadWindow[vadWindowSize++] = chunk[index] / 32768.0f
                            if (vadWindowSize == vadWindow.size) {
                                val shouldAutoStop = runCatching { vad.process(vadWindow).shouldAutoStop }
                                    .onFailure {
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
            } finally {
                recorder.stopSafely()
                recorder.release()
                vadDetector?.close()
                failedVadDetector?.close()
                vadDetector = null
                abandonAudioFocus()
                synchronized(stateLock) { if (audioRecord === recorder) audioRecord = null }
            }
        }
        true
    }

    fun stop(): FloatArray = synchronized(startLock) {
        val recorderToStop: AudioRecord?
        val threadToJoin: Thread?
        val waitMs = (SettingsManager.extraRecordingBufferMs(context) + 1_000L).coerceAtLeast(2_000L)
        synchronized(stateLock) {
            stopDeadlineNanos = System.nanoTime() + SettingsManager.extraRecordingBufferMs(context) * 1_000_000L
            recording.set(false)
            recorderToStop = audioRecord
            threadToJoin = worker
        }
        if (threadToJoin !== Thread.currentThread()) threadToJoin?.join(waitMs)
        if (threadToJoin?.isAlive == true) {
            runCatching { recorderToStop?.stop() }
            threadToJoin.join(1_000L)
        }
        synchronized(stateLock) { if (worker === threadToJoin) worker = null }
        buffer.snapshot()
    }

    fun release() = stop()

    /** A warm microphone is represented by keeping the recorder lifecycle ready; actual capture still starts on demand. */
    fun prepareWarmMicrophone() {
        if (SettingsManager.alwaysOnMicrophoneEnabled(context)) {
            audioManager = context.getSystemService(AudioManager::class.java)
            // Resolve the audio manager/device route early. AudioRecord itself is
            // still opened only for an active capture to avoid holding the mic.
            // This removes route discovery latency without keeping a live input.
        }
    }

    private fun requestAudioFocus() {
        if (!SettingsManager.muteWhileRecording(context)) return
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        audioManager = manager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                ).build()
            manager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioFocusRequest?.let(manager::abandonAudioFocusRequest)
        else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
        audioFocusRequest = null
        audioManager = null
    }

    private fun findInputDevice(id: Int): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.firstOrNull { it.id == id }
        } else null

    fun availableInputDevices(): List<AudioDeviceInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_INPUTS)).orEmpty().toList()
        } else emptyList()

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
        } catch (_: IllegalStateException) { }
    }
}
