package com.handy.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HandyInputMethodService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var recorder: AudioRecorder
    private lateinit var status: TextView
    private lateinit var recordButton: Button
    private var recording = false
    private var pushToTalk = false
    private var holdTriggered = false
    private var transcriptionJob: Job? = null

    private val holdRunnable = Runnable {
        if (!recording) {
            holdTriggered = true
            startRecording(pushToTalk = true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        recorder = AudioRecorder(
            context = this,
            onSilenceAutoStop = { mainHandler.post { stopRecording() } },
        )
    }

    override fun onCreateInputView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(12, 8, 12, 8)
        status = TextView(context).apply {
            text = "Handy ready"
            setTextColor(Color.DKGRAY)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        recordButton = Button(context).apply {
            text = "Mic"
            setOnClickListener {
                if (!holdTriggered) toggleRecording()
            }
            setOnTouchListener(object : View.OnTouchListener {
                private var downX = 0f
                private var downY = 0f
                private var moved = false

                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            holdTriggered = false
                            moved = false
                            mainHandler.postDelayed(holdRunnable, HOLD_DURATION_MS)
                        }
                        MotionEvent.ACTION_UP -> {
                            mainHandler.removeCallbacks(holdRunnable)
                            val isTap = kotlin.math.abs(event.rawX - downX) < MOVE_TOLERANCE_PX &&
                                kotlin.math.abs(event.rawY - downY) < MOVE_TOLERANCE_PX
                            if (holdTriggered) {
                                stopRecording()
                            } else if (isTap && !moved) {
                                view.performClick()
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            mainHandler.removeCallbacks(holdRunnable)
                            if (holdTriggered) stopRecording()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (kotlin.math.abs(event.rawX - downX) >= MOVE_TOLERANCE_PX ||
                                kotlin.math.abs(event.rawY - downY) >= MOVE_TOLERANCE_PX
                            ) {
                                moved = true
                                mainHandler.removeCallbacks(holdRunnable)
                            }
                        }
                    }
                    return true
                }
            })
        }
        addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(recordButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        status.text = "Handy ready"
    }

    private fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording(pushToTalk: Boolean = false) {
        if (recording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Allow microphone in Handy"
            return
        }
        if (!recorder.start(enableVoiceActivityDetection = true)) {
            status.text = "Microphone unavailable"
            return
        }
        this.pushToTalk = pushToTalk
        recording = true
        if (pushToTalk) AudioFeedbackManager.onStartPushToTalk(this) else AudioFeedbackManager.onStartRecording(this)
        recordButton.text = "Stop"
        status.text = if (pushToTalk) "Push to talk…" else "Listening…"
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        val wasPushToTalk = pushToTalk
        pushToTalk = false
        if (wasPushToTalk) AudioFeedbackManager.onStopPushToTalk(this) else AudioFeedbackManager.onStopRecording(this)
        recordButton.text = "Mic"
        status.text = "Transcribing…"
        val samples = recorder.stop()
        val audioPath = runCatching { AudioRecorder.writeWav(this@HandyInputMethodService, samples).absolutePath }.getOrNull()
        transcriptionJob?.cancel()
        transcriptionJob = scope.launch {
            try {
                val text = TranscriptionEngine.transcribe(this@HandyInputMethodService, samples)
                if (text.isNotBlank()) {
                    AudioFeedbackManager.onTranscriptionSuccess(this@HandyInputMethodService)
                    HistoryRepository.record(
                        context = this@HandyInputMethodService,
                        text = text,
                        sourceType = HistorySource.INPUT_METHOD,
                        durationMs = AudioRecorder.durationMs(samples),
                        audioFilePath = audioPath,
                    )
                    currentInputConnection?.commitText(text, 1)
                    if (SettingsManager.autoSubmitEnabled(this@HandyInputMethodService)) {
                        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
                    }
                }
                status.text = if (text.isBlank()) "No speech" else "Handy ready"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text = error.message ?: "Transcription failed"
            } finally {
                holdTriggered = false
            }
        }
    }

    override fun onWindowHidden() {
        mainHandler.removeCallbacks(holdRunnable)
        if (recording) {
            recording = false
            if (pushToTalk) AudioFeedbackManager.onStopPushToTalk(this) else AudioFeedbackManager.onStopRecording(this)
            pushToTalk = false
            recorder.stop()
        }
        super.onWindowHidden()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(holdRunnable)
        transcriptionJob?.cancel()
        scope.cancel()
        recorder.release()
        super.onDestroy()
    }

    companion object {
        private const val HOLD_DURATION_MS = 300L
        private const val MOVE_TOLERANCE_PX = 12f
    }
}
