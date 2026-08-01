package com.handy.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveSubtitleService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: TextView? = null
    private var recorder: AudioRecorder? = null
    private var transcriptionJob: Job? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Ready to listen"))
        recorder = AudioRecorder(this)
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopListening()
            ACTION_START -> startListening()
        }
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (listening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateOverlay("Microphone permission is required")
            return
        }
        if (Settings.canDrawOverlays(this).not()) {
            updateOverlay("Overlay permission is required")
            return
        }
        val activeRecorder = recorder ?: return
        if (!activeRecorder.start()) {
            updateOverlay("Microphone unavailable")
            return
        }

        listening = true
        AudioFeedbackManager.onStartRecording(this)
        updateOverlay("Listening…")
        transcriptionJob?.cancel()
        transcriptionJob = scope.launch {
            while (isActive && listening) {
                delay(CHUNK_INTERVAL_MS)
                val samples = activeRecorder.buffer.drain(MAX_SAMPLES)
                if (samples.size < MIN_SAMPLES) continue

                if (!isActive || !listening) return@launch
                updateOverlay("Transcribing…")
                val text = try {
                    withContext(Dispatchers.Default) {
                        TranscriptionEngine.transcribe(this@LiveSubtitleService, samples)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.util.Log.e(TAG, "Live subtitle transcription failed", error)
                    ""
                }
                if (!isActive || !listening) return@launch
                if (text.isNotBlank()) {
                    AudioFeedbackManager.onTranscriptionSuccess(this@LiveSubtitleService)
                    HistoryRepository.record(
                        context = this@LiveSubtitleService,
                        text = text,
                        sourceType = HistorySource.LIVE_SUBTITLE,
                        durationMs = AudioRecorder.durationMs(samples),
                    )
                    updateOverlay(text.trim())
                } else {
                    updateOverlay("Listening…")
                }
            }
        }
    }

    private fun stopListening() {
        if (!listening) {
            stopSelf()
            return
        }
        listening = false
        AudioFeedbackManager.onStopRecording(this)
        transcriptionJob?.cancel()
        transcriptionJob = null
        recorder?.stop()
        updateOverlay("Paused")
        stopSelf()
    }

    private fun updateOverlay(message: String) {
        overlay?.post {
            overlay?.text = message
        }
    }

    private fun showOverlay() {
        val text = TextView(this).apply {
            text = "Handy subtitles ready"
            textSize = 17f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC202124.toInt())
            setPadding(24, 14, 24, 14)
            gravity = Gravity.CENTER
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = 80
        }
        getSystemService(WindowManager::class.java).addView(text, params)
        overlay = text
    }

    override fun onDestroy() {
        stopListening()
        recorder?.release()
        recorder = null
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(message: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Handy subtitles")
        .setContentText(message)
        .setOngoing(true)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live subtitles", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val TAG = "HandySubtitles"
        private const val CHUNK_INTERVAL_MS = 4_000L
        private const val MIN_SAMPLES = AudioRecorder.SAMPLE_RATE / 2
        private const val MAX_SAMPLES = AudioRecorder.SAMPLE_RATE * 8
        const val ACTION_START = "com.handy.android.action.START_SUBTITLES"
        const val ACTION_STOP = "com.handy.android.action.STOP_SUBTITLES"
        private const val CHANNEL_ID = "handy-live-subtitles"
        private const val NOTIFICATION_ID = 1002

        fun start(context: android.content.Context) =
            ContextCompat.startForegroundService(
                context,
                Intent(context, LiveSubtitleService::class.java).setAction(ACTION_START),
            )
    }
}
