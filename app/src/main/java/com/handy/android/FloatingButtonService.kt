package com.handy.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingButtonService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recorder: AudioRecorder
    private var overlay: View? = null
    private var recording = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        recorder = AudioRecorder(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Ready to record"))
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) toggleRecording()
        return START_STICKY
    }

    private fun toggleRecording() {
        if (recording) {
            recording = false
            AudioFeedbackManager.onStopRecording(this)
            val samples = recorder.stop()
            overlay?.contentDescription = "Handy: processing"
            scope.launch(Dispatchers.Default) {
                val result = runCatching {
                    TranscriptionEngine.transcribe(this@FloatingButtonService, samples)
                }.onFailure { error ->
                    android.util.Log.e("Handy", "Whisper transcription failed", error)
                }.getOrNull().orEmpty()
                if (result.isNotBlank()) {
                    AudioFeedbackManager.onTranscriptionSuccess(this@FloatingButtonService)
                    HistoryRepository.record(
                        context = this@FloatingButtonService,
                        text = result,
                        sourceType = HistorySource.FLOATING_BUTTON,
                        durationMs = AudioRecorder.durationMs(samples),
                    )
                }
                AutoTypeAccessibilityService.instance?.insertText(result)
                launch(Dispatchers.Main) { overlay?.contentDescription = "Handy: ready" }
            }
        } else if (recorder.start()) {
            recording = true
            AudioFeedbackManager.onStartRecording(this)
            overlay?.contentDescription = "Handy: recording"
        }
    }

    private fun showOverlay() {
        val label = TextView(this).apply {
            text = "●"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(76, 56, 150))
            gravity = Gravity.CENTER
            contentDescription = "Handy: ready"
            setOnClickListener { toggleRecording() }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            64,
            64,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }
        label.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                    }
                    MotionEvent.ACTION_UP -> {
                        if (kotlin.math.abs(event.rawX - downX) < 12 && kotlin.math.abs(event.rawY - downY) < 12) view.performClick()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x -= (event.rawX - downX).toInt()
                        params.y += (event.rawY - downY).toInt()
                        downX = event.rawX
                        downY = event.rawY
                        getSystemService(WindowManager::class.java).updateViewLayout(view, params)
                    }
                }
                return true
            }
        })
        getSystemService(WindowManager::class.java).addView(label, params)
        overlay = label
    }

    override fun onDestroy() {
        isRunning = false
        if (recording) AudioFeedbackManager.onStopRecording(this)
        recording = false
        recorder.release()
        overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(message: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("Handy")
        .setContentText(message)
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Handy recording", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_TOGGLE = "com.handy.android.action.TOGGLE_RECORDING"
        private const val CHANNEL_ID = "handy-recording"
        private const val NOTIFICATION_ID = 1001

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, FloatingButtonService::class.java))
        }
    }
}
