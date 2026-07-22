package com.handy.app.service

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import com.handy.app.corrector.WordCorrector
import com.handy.app.bridge.EngineBridge

/**
 * Exposes Handy-Android as a system speech-to-text provider via [RecognitionService].
 * Keyboards (Gboard, SwiftKey, etc.) and system apps use [SpeechRecognizer] to drive this service.
 */
class HandyVoiceRecognitionService : RecognitionService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentCallback: Callback? = null

    override fun onStartListening(recognizerIntent: Intent?, callback: Callback?) {
        currentCallback = callback

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted for HandyVoiceRecognitionService")
            safeError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        try {
            if (!EngineBridge.nativeIsRecording()) {
                if (!EngineBridge.nativeIsModelLoaded()) {
                    EngineBridge.nativeLoadModel()
                }
                EngineBridge.nativeStartRecording(16000, 1)
            }
            onReadyForSpeech()
            onBeginningOfSpeech()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start listening in HandyVoiceRecognitionService", t)
            safeError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun onStopListening(callback: Callback?) {
        try {
            if (EngineBridge.nativeIsRecording()) {
                EngineBridge.nativeFinalizeStream()
            }
            onEndOfSpeech()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to stop listening in HandyVoiceRecognitionService", t)
        }
    }

    override fun onCancel(callback: Callback?) {
        try {
            if (EngineBridge.nativeIsRecording()) {
                EngineBridge.nativeCancelRecording()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to cancel in HandyVoiceRecognitionService", t)
        }
        currentCallback = null
    }

    fun onReadyForSpeech() {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            try {
                cb.readyForSpeech(Bundle())
            } catch (ignored: RemoteException) {
            }
        }
    }

    fun onBeginningOfSpeech() {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            try {
                cb.beginningOfSpeech()
            } catch (ignored: RemoteException) {
            }
        }
    }

    fun onEndOfSpeech() {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            try {
                cb.endOfSpeech()
            } catch (ignored: RemoteException) {
            }
        }
    }

    fun onResults(rawText: String, lang: String? = null, customWords: List<String> = emptyList()) {
        mainHandler.post {
            val cb = currentCallback ?: return@post
            val app = applicationContext as? com.handy.app.HandyApplication
            val fillerEnabled = app?.settingsStore?.fillerWordsEnabled ?: true
            val filtered = WordCorrector.filterTranscriptionOutput(rawText, lang, enableFillerRemoval = fillerEnabled)
            val corrector = WordCorrector(customWords)
            val processed = corrector.applyCustomWords(filtered)

            val hypotheses = arrayListOf(processed)
            val bundle = Bundle().apply {
                putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, hypotheses)
            }
            try {
                cb.results(bundle)
            } catch (ignored: RemoteException) {
            }
            currentCallback = null
        }
    }

    fun onError(errorCode: Int) {
        mainHandler.post {
            safeError(errorCode)
        }
    }

    private fun safeError(errorCode: Int) {
        val cb = currentCallback ?: return
        try {
            cb.error(errorCode)
        } catch (ignored: RemoteException) {
        }
        currentCallback = null
    }

    companion object {
        private const val TAG = "HandyVoiceRecService"
    }
}
