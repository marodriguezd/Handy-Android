package com.handy.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.R
import com.handy.app.SettingsStore
import com.handy.app.bridge.EngineBridge
import com.handy.app.bridge.EngineCallback
import com.handy.app.capability.DeviceCapabilityDetector
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

    private val _downloadEvents = MutableSharedFlow<DownloadProgressEvent>(replay = 0, extraBufferCapacity = 64)
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
            // Capability + settings snapshot for logcat observability
            val snapshot = DeviceCapabilityDetector.detect(getApplication())
            val settings = SettingsStore(getApplication())
            Log.d(
                TAG,
                "EngineVM init; capabilityTier=${snapshot.toTier()}; " +
                    "totalMemGB=${snapshot.totalMemGbReport}; " +
                    "showExperimental=${settings.showExperimentalModels}",
            )
        }
    }

    fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        EngineBridge.nativeDestroy()
    }

    // ── Public API ─────────────────────────────────────────────

    @Volatile
    private var _imeModeEnabled = false

    /** Enable auto-insert mode (IME). When true, final transcriptions are auto-injected. */
    fun setImeModeEnabled(enabled: Boolean) {
        _imeModeEnabled = enabled
    }

    fun startRecording() {
        val current = _state.value
        Log.d(TAG, "startRecording: currentState=$current")
        if (current == STATE_LISTENING || current == STATE_TRANSCRIBING) {
            Log.w(TAG, "startRecording: already recording, ignoring")
            return
        }
        _state.value = STATE_LOADING
        _finalText.value = null
        _partialText.value = ""
        _vadLevel.value = 0f
        _lastErrorMessage.value = null
        RecordingService.start(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            // Start mic immediately, no model needed
            Log.d(TAG, "nativeStartRecording starting immediately...")
            EngineBridge.nativeStartRecording(sampleRate = 16000, channelCount = 1)
            Log.d(TAG, "Mic started, now loading model in parallel...")

            // Model loads while audio accumulates in pipeline buffer
            EngineBridge.nativeLoadModel()
            val loaded = EngineBridge.nativeIsModelLoaded()
            if (!loaded) {
                Log.w(TAG, "nativeLoadModel reported model NOT loaded")
                val models = EngineBridge.nativeGetAvailableModels()
                val parsed = ModelInfo.fromJsonArray(models)
                val active = parsed.firstOrNull { it.isActive }
                val downloaded = parsed.firstOrNull { it.isDownloaded }
                Log.w(TAG, "active=$active downloaded=$downloaded")
                if (active == null && downloaded == null) {
                    _lastErrorMessage.value = getApplication<android.app.Application>().getString(R.string.error_no_model_downloaded)
                    _state.value = STATE_ERROR
                    RecordingService.stop(getApplication())
                    EngineBridge.nativeCancelRecording()
                    return@launch
                }
            }

            // Attempt streaming with accumulated audio
            val streaming = EngineBridge.nativeAttemptStreaming()
            Log.d(TAG, "nativeAttemptStreaming: streaming=$streaming")
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
        viewModelScope.launch(Dispatchers.Main) {
            val result = injectorRouter.inject(text)
            if (result.isSuccess) {
                _state.value = STATE_IDLE
                _finalText.value = null
                _partialText.value = ""
            } else {
                // Injection failed — show error so user knows and can retry
                Log.w(TAG, "confirmInsert: injection failed: ${result.exceptionOrNull()}")
                _lastErrorMessage.value = result.exceptionOrNull()?.message ?: getApplication<android.app.Application>().getString(R.string.error_insertion_failed)
                _state.value = STATE_ERROR
            }
        }
    }

    fun testInject(text: String) {
        _finalText.value = text
        _state.value = STATE_CONFIRM
        viewModelScope.launch(Dispatchers.Main) {
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
        // Prevent flicker: suppress native STATE_IDLE while we're loading
        // (nativeLoadModel fires LOADING(1) then IDLE(0) before nativeStartRecording fires LISTENING(2))
        if (state == STATE_IDLE && _state.value == STATE_LOADING) {
            Log.d(TAG, "onStateChange: suppressing IDLE during LOADING (model loaded, will start recording)")
            return
        }
        _state.value = state
    }

    override fun onTranscription(text: String, isPartial: Boolean) {
        if (isPartial) {
            _partialText.value = text
        } else {
            // Show final text in ConfirmBar for user to Insert or Discard.
            // No auto-insert — the ConfirmBar composable handles insertion
            // via onConfirmInsert/onDiscard callbacks.
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
        val emitted = _downloadEvents.tryEmit(
            DownloadProgressEvent(
                modelId = modelId,
                progress = progress,
                bytesDownloaded = bytesSoFar,
                totalBytes = totalBytes,
                isComplete = false,
                error = null,
            )
        )
        if (!emitted) {
            Log.w(TAG, "onDownloadProgress: event dropped for $modelId (buffer full)")
        }
    }

    override fun onDownloadComplete(modelId: String, success: Boolean, errorMsg: String?) {
        val emitted = _downloadEvents.tryEmit(
            DownloadProgressEvent(
                modelId = modelId,
                progress = if (success) 1f else 0f,
                bytesDownloaded = 0,
                totalBytes = 0,
                isComplete = true,
                error = errorMsg.takeUnless { success },
            )
        )
        if (!emitted) {
            Log.w(TAG, "onDownloadComplete: event dropped for $modelId (buffer full)")
        }
        if (success) {
            // Auto-activate the newly downloaded model so it's immediately usable
            EngineBridge.nativeSetActiveModel(modelId)
            refreshModels()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (cleanedUp) return
        cleanedUp = true
        EngineBridge.nativeDestroy()
    }
}
