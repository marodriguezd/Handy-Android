package com.handy.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.bridge.EngineBridge
import com.handy.app.bridge.EngineCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class EngineViewModel(application: Application) : AndroidViewModel(application), EngineCallback {

    companion object {
        const val STATE_IDLE = 0
        const val STATE_LOADING = 1
        const val STATE_LISTENING = 2
        const val STATE_TRANSCRIBING = 3
        const val STATE_ERROR = 4
        const val STATE_CONFIRM = 5
    }

    private val _state = MutableStateFlow(STATE_IDLE)
    val state: StateFlow<Int> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _finalText = MutableStateFlow<String?>(null)
    val finalText: StateFlow<String?> = _finalText.asStateFlow()

    private val _vadLevel = MutableStateFlow(0f)
    val vadLevel: StateFlow<Float> = _vadLevel.asStateFlow()

    private val modelsDir: File = File(application.filesDir, "models")
    private val configDir: File = application.filesDir

    init {
        modelsDir.mkdirs()
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeInit(
                modelDir = modelsDir.absolutePath,
                configDir = configDir.absolutePath,
                callback = this@EngineViewModel
            )
        }
    }

    // ── Public API ─────────────────────────────────────────────

    fun startRecording() {
        _finalText.value = null
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeLoadModel()
            EngineBridge.nativeStartRecording(sampleRate = 16000, channelCount = 1)
        }
    }

    fun stopRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeFinalizeStream()
        }
    }

    fun cancelRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeCancelRecording()
        }
    }

    fun resetPartialText() {
        _partialText.value = ""
        _finalText.value = null
    }

    // ── EngineCallback Implementation ──────────────────────────

    override fun onStateChange(state: Int) {
        _state.value = state
    }

    override fun onTranscription(text: String, isPartial: Boolean) {
        if (isPartial) {
            _partialText.value = text
        } else {
            _finalText.value = text
            _state.value = STATE_CONFIRM
        }
    }

    override fun onVadLevel(level: Float) {
        _vadLevel.value = level
    }

    override fun onError(code: Int, message: String) {
        _state.value = STATE_ERROR
    }

    override fun onDownloadProgress(modelId: String, bytesSoFar: Long, totalBytes: Long) {
        // handled by UI layer
    }

    override fun onDownloadComplete(modelId: String, success: Boolean, errorMsg: String?) {
        // handled by UI layer
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeDestroy()
        }
    }
}
