package com.handy.app.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import com.handy.app.HandyApplication
import com.handy.app.viewmodel.EngineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Exposes Handy-Android as a system speech-to-text provider via [RecognitionService].
 *
 * Third-party keyboards (Gboard, SwiftKey, etc.) and system apps use
 * [android.speech.SpeechRecognizer] to drive this service. Instead of
 * talking directly to the Rust engine, this service delegates to the
 * process-wide [EngineViewModel] so it reuses the same state machine,
 * custom-word correction, filler-word filtering, and optional LLM
 * post-processing that the rest of the app uses.
 */
class HandyVoiceRecognitionService : RecognitionService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentCallback: Callback? = null

    private val serviceScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var collectorJob: Job? = null

    private lateinit var engineViewModel: EngineViewModel

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as HandyApplication
        engineViewModel = app.engineViewModel
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel("HandyVoiceRecognitionService destroyed")
    }

    override fun onStartListening(recognizerIntent: Intent?, callback: Callback?) {
        currentCallback = callback
        clearStaleResults()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted for HandyVoiceRecognitionService")
            safeError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        startCollectors()

        safeReadyForSpeech(Bundle())

        try {
            // Avoid piling up multiple recording requests if the system calls
            // onStartListening while we are already listening. The VM will
            // ignore a duplicate, but this keeps the service state clean.
            if (engineViewModel.state.value != EngineViewModel.STATE_LISTENING &&
                engineViewModel.state.value != EngineViewModel.STATE_TRANSCRIBING
            ) {
                engineViewModel.startRecording()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start recording in HandyVoiceRecognitionService", t)
            safeError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun onStopListening(callback: Callback?) {
        try {
            engineViewModel.stopRecording()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to stop recording in HandyVoiceRecognitionService", t)
        }
    }

    override fun onCancel(callback: Callback?) {
        try {
            engineViewModel.cancelRecording()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to cancel recording in HandyVoiceRecognitionService", t)
        }
        collectorJob?.cancel()
        currentCallback = null
    }

    private fun startCollectors() {
        collectorJob?.cancel()
        collectorJob = serviceScope.launch {
            launch {
                engineViewModel.state.collectLatest { state ->
                    when (state) {
                        EngineViewModel.STATE_LISTENING -> safeBeginningOfSpeech()
                        EngineViewModel.STATE_TRANSCRIBING -> safeEndOfSpeech()
                        EngineViewModel.STATE_ERROR -> safeError(SpeechRecognizer.ERROR_CLIENT)
                        else -> { /* no-op */ }
                    }
                }
            }

            launch {
                engineViewModel.partialText.collectLatest { text ->
                    if (text.isNotBlank()) {
                        safePartialResults(text)
                    }
                }
            }

            launch {
                engineViewModel.finalText.collectLatest { text ->
                    if (!text.isNullOrBlank()) {
                        val bundle = Bundle().apply {
                            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                        }
                        safeResults(bundle)
                        engineViewModel.clearPartialText()
                    }
                }
            }
        }
    }

    private fun clearStaleResults() {
        engineViewModel.clearPartialText()
    }

    private fun safeReadyForSpeech(params: Bundle) {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            try {
                cb.readyForSpeech(params)
            } catch (ignored: RemoteException) {
            }
        }
    }

    private fun safeBeginningOfSpeech() {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            try {
                cb.beginningOfSpeech()
            } catch (ignored: RemoteException) {
            }
        }
    }

    private fun safeEndOfSpeech() {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            try {
                cb.endOfSpeech()
            } catch (ignored: RemoteException) {
            }
        }
    }

    private fun safePartialResults(partial: String) {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            val bundle = Bundle().apply {
                putStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION,
                    arrayListOf(partial),
                )
            }
            try {
                cb.partialResults(bundle)
            } catch (ignored: RemoteException) {
            }
        }
    }

    private fun safeResults(bundle: Bundle) {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            try {
                cb.results(bundle)
            } catch (ignored: RemoteException) {
            }
            currentCallback = null
        }
    }

    private fun safeError(errorCode: Int) {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            try {
                cb.error(errorCode)
            } catch (ignored: RemoteException) {
            }
            currentCallback = null
        }
    }

    companion object {
        private const val TAG = "HandyVoiceRecService"
    }
}
