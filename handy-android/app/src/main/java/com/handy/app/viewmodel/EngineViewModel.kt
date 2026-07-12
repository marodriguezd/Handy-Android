package com.handy.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.bridge.EngineBridge
import com.handy.app.bridge.EngineCallback
import com.handy.app.injection.InjectorRouter
import com.handy.app.model.AppSettings
import com.handy.app.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class EngineViewModel(
    application: Application,
    val injectorRouter: InjectorRouter,
) : AndroidViewModel(application), EngineCallback {

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

    // ── Model Download Events ─────────────────────────────────

    data class DownloadProgressEvent(
        val modelId: String,
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isComplete: Boolean,
        val error: String?,
    )

    private val _downloadEvents = MutableSharedFlow<DownloadProgressEvent>(replay = 0, extraBufferCapacity = 16)
    val downloadEvents: SharedFlow<DownloadProgressEvent> = _downloadEvents.asSharedFlow()

    // ── Available Models ──────────────────────────────────────

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    // ── Paths ─────────────────────────────────────────────────

    private val modelsDir: File = File(application.filesDir, "models")
    private val configDir: File = application.filesDir
    private var cleanedUp = false

    init {
        modelsDir.mkdirs()
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeInit(
                modelDir = modelsDir.absolutePath,
                configDir = configDir.absolutePath,
                callback = this@EngineViewModel,
            )
        }
    }

    fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        EngineBridge.nativeDestroy()
    }

    // ── Public API ─────────────────────────────────────────────

    fun startRecording() {
        _finalText.value = null
        _partialText.value = ""
        _state.value = STATE_LISTENING
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeLoadModel()
            EngineBridge.nativeStartRecording(sampleRate = 16000, channelCount = 1)
        }
    }

    fun stopRecording() {
        _state.value = STATE_TRANSCRIBING
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeFinalizeStream()
        }
    }

    fun cancelRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeCancelRecording()
        }
        _state.value = STATE_IDLE
        _partialText.value = ""
        _finalText.value = null
    }

    fun resetPartialText() {
        _partialText.value = ""
        _finalText.value = null
    }

    fun testInject(text: String) {
        _finalText.value = text
        _state.value = STATE_CONFIRM
        viewModelScope.launch(Dispatchers.IO) {
            val result = injectorRouter.inject(text)
            if (result.isSuccess) {
                _state.value = STATE_IDLE
                _finalText.value = null
                _partialText.value = ""
            }
        }
    }

    // ── Model Management ──────────────────────────────────────

    fun refreshModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = EngineBridge.nativeGetAvailableModels()
            _availableModels.value = ModelInfo.fromJsonArray(json)
        }
    }

    // ── Settings Sync ─────────────────────────────────────────

    fun applySettings(settings: AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeSetIdleTimeout(settings.idleTimeout)
            EngineBridge.nativeSetPostProcessEndpoint(settings.postProcessEndpoint)
            EngineBridge.nativeSetPostProcessApiKey(settings.postProcessApiKey)
        }
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
            viewModelScope.launch(Dispatchers.IO) {
                val result = injectorRouter.inject(text)
                if (result.isSuccess) {
                    _state.value = STATE_IDLE
                    _finalText.value = null
                    _partialText.value = ""
                }
            }
        }
    }

    override fun onVadLevel(level: Float) {
        _vadLevel.value = level
    }

    override fun onError(code: Int, message: String) {
        _state.value = STATE_ERROR
    }

    override fun onDownloadProgress(modelId: String, bytesSoFar: Long, totalBytes: Long) {
        val progress = if (totalBytes > 0) bytesSoFar.toFloat() / totalBytes.toFloat() else -1f
        _downloadEvents.tryEmit(
            DownloadProgressEvent(
                modelId = modelId,
                progress = progress,
                bytesDownloaded = bytesSoFar,
                totalBytes = totalBytes,
                isComplete = false,
                error = null,
            )
        )
    }

    override fun onDownloadComplete(modelId: String, success: Boolean, errorMsg: String?) {
        _downloadEvents.tryEmit(
            DownloadProgressEvent(
                modelId = modelId,
                progress = if (success) 1f else 0f,
                bytesDownloaded = 0,
                totalBytes = 0,
                isComplete = true,
                error = errorMsg.takeUnless { success },
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        if (cleanedUp) return
        cleanedUp = true
        EngineBridge.nativeDestroy()
    }
}
