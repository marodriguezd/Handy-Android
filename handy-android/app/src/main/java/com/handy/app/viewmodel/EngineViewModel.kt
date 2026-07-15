package com.handy.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.bridge.EngineBridge
import com.handy.app.bridge.EngineCallback
import com.handy.app.injection.InjectorRouter
import com.handy.app.model.AppSettings
import com.handy.app.model.ModelInfo
import com.handy.app.service.RecordingService
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
        private const val TAG = "EngineVM"
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

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

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
        val current = _state.value
        Log.d(TAG, "startRecording: currentState=$current")
        if (current == STATE_LISTENING || current == STATE_TRANSCRIBING) {
            Log.w(TAG, "startRecording: already recording, ignoring")
            return
        }
        _finalText.value = null
        _partialText.value = ""
        _lastErrorMessage.value = null
        _state.value = STATE_LISTENING
        RecordingService.start(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "nativeLoadModel starting...")
            EngineBridge.nativeLoadModel()
            Log.d(TAG, "nativeLoadModel done, checking if model loaded...")
            val loaded = EngineBridge.nativeIsModelLoaded()
            if (!loaded) {
                Log.w(TAG, "nativeLoadModel reported model NOT loaded, checking active model...")
                val models = EngineBridge.nativeGetAvailableModels()
                val parsed = ModelInfo.fromJsonArray(models)
                val active = parsed.firstOrNull { it.isActive }
                val downloaded = parsed.firstOrNull { it.isDownloaded }
                Log.w(TAG, "active=$active downloaded=$downloaded models=$models")
            }
            Log.d(TAG, "nativeStartRecording starting...")
            EngineBridge.nativeStartRecording(sampleRate = 16000, channelCount = 1)
            Log.d(TAG, "nativeStartRecording done, isRecording=${EngineBridge.nativeIsRecording()}")
        }
    }

    fun stopRecording() {
        _state.value = STATE_TRANSCRIBING
        RecordingService.stop(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeFinalizeStream()
        }
    }

    fun cancelRecording() {
        RecordingService.stop(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeCancelRecording()
        }
        _state.value = STATE_IDLE
        _partialText.value = ""
        _finalText.value = null
    }

    fun resetPartialText() {
        _state.value = STATE_IDLE
        _partialText.value = ""
        _finalText.value = null
        _lastErrorMessage.value = null
    }

    /** Clears texts only — does NOT reset state. Used when IME starts in a new field mid-dictation. */
    fun clearPartialText() {
        _partialText.value = ""
        _finalText.value = null
        _lastErrorMessage.value = null
    }

    fun confirmInsert(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = injectorRouter.inject(text)
            if (result.isSuccess) {
                _state.value = STATE_IDLE
                _finalText.value = null
                _partialText.value = ""
            }
        }
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
            var json: String
            var attempts = 0
            do {
                json = EngineBridge.nativeGetAvailableModels()
                if (json.isBlank() && attempts < 10) {
                    kotlinx.coroutines.delay(500)
                    attempts++
                }
            } while (json.isBlank() && attempts < 10)
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
        }
    }

    override fun onVadLevel(level: Float) {
        _vadLevel.value = level
    }

    override fun onError(code: Int, message: String) {
        _lastErrorMessage.value = message
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
        if (success) refreshModels()
    }

    override fun onCleared() {
        super.onCleared()
        if (cleanedUp) return
        cleanedUp = true
        EngineBridge.nativeDestroy()
    }
}
