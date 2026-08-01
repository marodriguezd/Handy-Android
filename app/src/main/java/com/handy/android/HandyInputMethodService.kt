package com.handy.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
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
    private lateinit var recorder: AudioRecorder
    private lateinit var status: TextView
    private lateinit var recordButton: Button
    private var recording = false
    private var transcriptionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        recorder = AudioRecorder(this)
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
            setOnClickListener { toggleRecording() }
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

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Allow microphone in Handy"
            return
        }
        if (!recorder.start()) {
            status.text = "Microphone unavailable"
            return
        }
        recording = true
        AudioFeedbackManager.onStartRecording(this)
        recordButton.text = "Stop"
        status.text = "Listening…"
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        AudioFeedbackManager.onStopRecording(this)
        recordButton.text = "Mic"
        status.text = "Transcribing…"
        val samples = recorder.stop()
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
                    )
                    currentInputConnection?.commitText(text, 1)
                }
                status.text = if (text.isBlank()) "No speech" else "Handy ready"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text = error.message ?: "Transcription failed"
            }
        }
    }

    override fun onWindowHidden() {
        if (recording) {
            recording = false
            AudioFeedbackManager.onStopRecording(this)
            recorder.stop()
        }
        super.onWindowHidden()
    }

    override fun onDestroy() {
        transcriptionJob?.cancel()
        scope.cancel()
        recorder.release()
        super.onDestroy()
    }
}
