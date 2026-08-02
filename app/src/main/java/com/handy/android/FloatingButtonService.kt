package com.handy.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var recorder: AudioRecorder
    private var overlay: View? = null
    private var waveform: AudioWaveformView? = null
    private var recording = false
    private var pushToTalk = false
    private var holdTriggered = false

    private val holdRunnable = Runnable {
        if (!recording) {
            holdTriggered = true
            startRecording(pushToTalk = true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        recorder = AudioRecorder(
            context = this,
            onSilenceAutoStop = { mainHandler.post { stopRecording() } },
            onAmplitude = { amplitude -> mainHandler.post { waveform?.setAmplitude(amplitude) } },
        )
        createNotificationChannel()
        runCatching {
            startForeground(NOTIFICATION_ID, notification(getString(R.string.floating_button_ready)))
        }.onFailure { error ->
            // RECORD_AUDIO may be revoked at runtime; starting a microphone FGS without it
            // throws SecurityException on Android 14+.
            AppLog.record(this, "E", TAG, "Unable to start foreground service", error)
            stopSelf()
            return
        }
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    private fun updateOverlayColors(isRecordingState: Boolean) {
        val accentColor = ContextCompat.getColor(
            this,
            if (isRecordingState) R.color.handy_tertiary else R.color.handy_primary,
        )
        waveform?.setWaveformColor(accentColor)
        overlayDot?.setTextColor(accentColor)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggleRecording()
            ACTION_CANCEL -> cancelRecording()
        }
        return START_STICKY
    }

    private fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private fun cancelRecording() {
        if (!recording) return
        recording = false
        isRecording = false
        pushToTalk = false
        mainHandler.removeCallbacks(holdRunnable)
        recorder.stop()
        waveform?.setAmplitude(0f)
        updateOverlayColors(false)
        overlay?.contentDescription = getString(R.string.floating_cd_ready)
    }

    private fun startRecording(pushToTalk: Boolean = false) {
        if (recording || !recorder.start(enableVoiceActivityDetection = true)) return
        this.pushToTalk = pushToTalk
        recording = true
        isRecording = true
        updateOverlayColors(true)
        if (pushToTalk) AudioFeedbackManager.onStartPushToTalk(this) else AudioFeedbackManager.onStartRecording(this)
        overlay?.contentDescription = getString(if (pushToTalk) R.string.floating_cd_push_to_talk else R.string.floating_cd_recording)
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        isRecording = false
        val wasPushToTalk = pushToTalk
        pushToTalk = false
        if (wasPushToTalk) AudioFeedbackManager.onStopPushToTalk(this) else AudioFeedbackManager.onStopRecording(this)
        waveform?.setAmplitude(0f)
        updateOverlayColors(false)
        val samples = recorder.stop()
        val audioPath = runCatching { AudioRecorder.writeWav(this@FloatingButtonService, samples).absolutePath }.getOrNull()
        overlay?.contentDescription = getString(R.string.floating_cd_processing)
        scope.launch(Dispatchers.Default) {
            val result = runCatching {
                TranscriptionEngine.transcribe(this@FloatingButtonService, samples)
            }.onFailure { error ->
                AppLog.record(this@FloatingButtonService, "E", TAG, "Transcription failed", error)
            }.getOrNull().orEmpty()
            if (result.isNotBlank()) {
                AudioFeedbackManager.onTranscriptionSuccess(this@FloatingButtonService)
                HistoryRepository.record(
                    context = this@FloatingButtonService,
                    text = result,
                    sourceType = HistorySource.FLOATING_BUTTON,
                    durationMs = AudioRecorder.durationMs(samples),
                    audioFilePath = audioPath,
                )
                AutoTypeAccessibilityService.instance?.insertText(result)
            }
            launch(Dispatchers.Main) { overlay?.contentDescription = getString(R.string.floating_cd_ready) }
        }
    }

    private fun showOverlay() {
        val primaryColor = ContextCompat.getColor(this, R.color.handy_primary)
        val containerColor = ContextCompat.getColor(this, R.color.handy_primary_container)
        val waveformView = AudioWaveformView(this).apply {
            setBackgroundColor(containerColor)
            setWaveformColor(primaryColor)
            contentDescription = getString(R.string.floating_cd_ready)
        }
        waveform = waveformView
        val container = FrameLayout(this)
        val touchTarget = TextView(this).apply {
            text = "●"
            textSize = 22f
            setTextColor(primaryColor)
            gravity = Gravity.CENTER
            setOnClickListener { toggleRecording() }
        }
        overlayDot = touchTarget
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
        touchTarget.setOnTouchListener(object : View.OnTouchListener {
            private var initialDownX = 0f
            private var initialDownY = 0f
            private var lastX = 0f
            private var lastY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialDownX = event.rawX
                        initialDownY = event.rawY
                        lastX = event.rawX
                        lastY = event.rawY
                        holdTriggered = false
                        mainHandler.postDelayed(holdRunnable, HOLD_DURATION_MS)
                    }
                    MotionEvent.ACTION_UP -> {
                        mainHandler.removeCallbacks(holdRunnable)
                        val isTap = kotlin.math.abs(event.rawX - initialDownX) < MOVE_TOLERANCE_PX &&
                            kotlin.math.abs(event.rawY - initialDownY) < MOVE_TOLERANCE_PX

                        if (holdTriggered) {
                            stopRecording()
                        } else if (isTap) {
                            view.performClick()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(holdRunnable)
                        if (holdTriggered) stopRecording()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawX - initialDownX) >= MOVE_TOLERANCE_PX ||
                            kotlin.math.abs(event.rawY - initialDownY) >= MOVE_TOLERANCE_PX
                        ) {
                            mainHandler.removeCallbacks(holdRunnable)
                        }
                        params.x -= (event.rawX - lastX).toInt()
                        params.y += (event.rawY - lastY).toInt()
                        lastX = event.rawX
                        lastY = event.rawY
                        getSystemService(WindowManager::class.java).updateViewLayout(container, params)
                    }
                }
                return true
            }
        })
        container.addView(waveformView, FrameLayout.LayoutParams(-1, -1))
        container.addView(touchTarget, FrameLayout.LayoutParams(-1, -1))
        getSystemService(WindowManager::class.java).addView(container, params)
        overlay = container
    }

    private var overlayDot: TextView? = null

    override fun onDestroy() {
        mainHandler.removeCallbacks(holdRunnable)
        if (recording) stopRecording()
        isRunning = false
        isRecording = false
        recorder.release()
        overlay?.let { view -> runCatching { getSystemService(WindowManager::class.java).removeView(view) } }
        overlay = null
        overlayDot = null
        waveform = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(message: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_handy)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(message)
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val TAG = "HandyFloating"
        private const val HOLD_DURATION_MS = 300L
        private const val MOVE_TOLERANCE_PX = 12f
        private const val CHANNEL_ID = "handy-recording"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isRecording: Boolean = false
            private set

        const val ACTION_TOGGLE = "com.handy.android.action.TOGGLE_RECORDING"
        const val ACTION_CANCEL = "com.handy.android.action.CANCEL_RECORDING"

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, FloatingButtonService::class.java))
        }
    }
}
