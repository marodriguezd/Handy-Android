package com.handy.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

class VoiceRecognitionService : RecognitionService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var recorder: AudioRecorder
    private var callback: Callback? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        recorder = AudioRecorder(this)
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Cancel any in-flight transcription from a previous session so it cannot post
        // results/errors to a now-stale callback.
        scope.coroutineContext.cancelChildren()
        callback = listener
        if (listener == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            callback = null
            return
        }
        if (!recorder.start()) {
            listener.error(SpeechRecognizer.ERROR_AUDIO)
            callback = null
            return
        }
        listening = true
        AudioFeedbackManager.onStartRecording(this)
        listener.readyForSpeech(Bundle())
        listener.beginningOfSpeech()
    }

    override fun onStopListening(listener: Callback?) {
        if (!listening) return
        listening = false
        AudioFeedbackManager.onStopRecording(this)
        val samples = recorder.stop()
        val audioPath = runCatching { AudioRecorder.writeWav(this@VoiceRecognitionService, samples).absolutePath }.getOrNull()
        val activeCallback = callback ?: listener ?: return
        scope.launch {
            try {
                mainHandler.post { activeCallback.endOfSpeech() }
                val text = TranscriptionEngine.transcribe(this@VoiceRecognitionService, samples)
                if (text.isBlank()) {
                    mainHandler.post { activeCallback.error(SpeechRecognizer.ERROR_NO_MATCH) }
                } else {
                    AudioFeedbackManager.onTranscriptionSuccess(this@VoiceRecognitionService)
                    HistoryRepository.record(
                        context = this@VoiceRecognitionService,
                        text = text,
                        sourceType = HistorySource.VOICE_RECOGNITION,
                        durationMs = AudioRecorder.durationMs(samples),
                        audioFilePath = audioPath,
                    )
                    val results = Bundle().apply {
                        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                    }
                    mainHandler.post { activeCallback.results(results) }
                }
            } catch (_: NoModelException) {
                mainHandler.post { activeCallback.error(SpeechRecognizer.ERROR_SERVER) }
            } catch (_: CancellationException) {
                // Cancellation is intentionally silent.
            } catch (_: Exception) {
                mainHandler.post { activeCallback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            } finally {
                mainHandler.post { if (callback === activeCallback) callback = null }
            }
        }
    }

    override fun onCancel(listener: Callback?) {
        if (listening) AudioFeedbackManager.onStopRecording(this)
        listening = false
        recorder.stop()
        callback = null
        scope.coroutineContext.cancelChildren()
    }

    override fun onDestroy() {
        recorder.release()
        scope.cancel()
        callback = null
        super.onDestroy()
    }
}
