package com.handy.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.handy.app.R
import com.handy.app.bridge.EngineBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class RecordingService : Service() {

    companion object {
        private const val TAG = "HandyRecording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.handy.app.STOP_RECORDING"
        const val CHANNEL_ID = "handy_recording"
        const val SAMPLERATE_KOTLIN = 16000

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var recordingThread: Thread? = null
    private var directBuffer: ByteBuffer? = null
    private var directBufferCapacity = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RecordingService created")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RecordingService onStartCommand: intent=$intent")

        if (intent?.action == ACTION_STOP) {
            stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        startRecording()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "RecordingService destroyed")
        stopRecording()
        super.onDestroy()
    }

    // ── Notification ───────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.recording_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.recording_stop_action),
                stopPendingIntent,
            )
            .build()
    }

    // ── Wake Lock ──────────────────────────────────────────────

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Handy:RecordingWakeLock",
        ).apply {
            acquire(30_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    // ── Audio Capture ──────────────────────────────────────────

    private fun startRecording() {
        if (isRecording) return
        isRecording = true

        val sampleRate = resolveSampleRate()
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize * 2, 4096)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            isRecording = false
            return
        }

        audioRecord = record

        val pcmBuffer = ShortArray(bufferSize / 2)
        val floatBuffer = FloatArray(bufferSize / 2)
        directBufferCapacity = bufferSize * 2
        directBuffer = ByteBuffer.allocateDirect(directBufferCapacity).order(ByteOrder.nativeOrder())

        record.startRecording()
        Log.d(TAG, "AudioRecord started: sampleRate=$sampleRate, bufferSize=$bufferSize")

        recordingThread = thread(name = "HandyRecordThread") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            readLoop(record, pcmBuffer, floatBuffer)
        }
    }

    private fun readLoop(
        record: AudioRecord,
        pcmBuffer: ShortArray,
        floatBuffer: FloatArray,
    ) {
        val buf = directBuffer ?: return

        while (isRecording) {
            val bytesRead = record.read(pcmBuffer, 0, pcmBuffer.size)
            if (bytesRead <= 0) {
                if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                }
                continue
            }

            val frameCount = bytesRead
            convertPcm16ToFloat32(pcmBuffer, floatBuffer, frameCount)

            buf.clear()
            buf.asFloatBuffer().put(floatBuffer, 0, frameCount)
            buf.position(frameCount * 4)
            buf.flip()

            try {
                EngineBridge.nativePushAudio(buf, frameCount)
            } catch (e: Exception) {
                Log.e(TAG, "nativePushAudio failed", e)
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        recordingThread?.join(2000)
        recordingThread = null

        audioRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }
            it.release()
        }
        audioRecord = null

        directBuffer = null

        releaseWakeLock()
        Log.d(TAG, "Recording stopped and cleaned up")
    }

    // ── PCM Conversion ─────────────────────────────────────────

    private fun convertPcm16ToFloat32(pcm: ShortArray, float: FloatArray, count: Int) {
        val invScale = 1f / 32768f
        for (i in 0 until count) {
            float[i] = pcm[i] * invScale
        }
    }

    private fun resolveSampleRate(): Int {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return nativeRate?.toIntOrNull() ?: SAMPLERATE_KOTLIN
    }
}
