package com.handy.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.R
import com.handy.app.SettingsStore
import com.handy.app.audio.RecordingRepository
import com.handy.app.bridge.EngineBridge
import com.handy.app.bridge.EngineCallback
import com.handy.app.capability.DeviceCapabilityDetector
import com.handy.app.injection.InjectorRouter
import com.handy.app.corrector.DictionaryManager
import com.handy.app.corrector.WordCorrector
import com.handy.app.model.AppSettings
import com.handy.app.postprocess.PostProcessor
import com.handy.app.postprocess.PromptsRepository
import com.handy.app.ui.postprocess.PostProcessProvider
import com.handy.app.model.ModelInfo
import com.handy.app.service.RecordingService
import kotlinx.coroutines.CancellationException
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
    private val recordingRepository: RecordingRepository,
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

    // ── Audio persistence (Sprint 25a factory binding + Sprint 25b wire) ─────────
    //
    // Sprint 25a opened the factory binding: the Kotlin side now sees
    // start/stop/cancel coroutines that prime + finalize the
    // 44-byte WAV placeholder. Sprint 25b closes the loop by
    // implementing [`onAudioFrames`] below — the Rust pipeline's
    // per-frame Float32 buffer is now pumped into
    // [com.handy.app.audio.RecordingRepository.pushFloatArrayFrames]
    // on `Dispatchers.IO`. Real PCM bytes now end up on disk
    // alongside the empty header, so `HistoryCard.audioPath` can be
    // played back and Retry can re-transcribe from a real source.
    //
    // The breadcrumb below was the Sprint 25a leftover; it is
    // intentionally deleted in Sprint 25b because the consumer
    // contract is now bound.

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
            try {
                EngineBridge.nativeInit(
                    modelDir = modelsDir.absolutePath,
                    configDir = configDir.absolutePath,
                    callback = this@EngineViewModel,
                )
                val settings = SettingsStore(getApplication())
                EngineBridge.nativeSetLanguage(settings.selectedLanguage)
                EngineBridge.nativeSetAccelerationBackend(settings.accelerationBackend.name)
            } catch (t: Throwable) {
                // Preserve structured-concurrency cancellation semantics
                // (mirrors the HistoryViewModel.retry pattern from Sprint 24).
                if (t is CancellationException) throw t
                // Native library unavailable (Robolectric test / pure-JVM CI).
                // EngineBridge.init already catches UnsatisfiedLinkError at
                // class-load; this catches invocation-time failures so the
                // VM remains usable for layout-only / pure-Compose tests.
                Log.w(TAG, "nativeInit unavailable; engine will be non-functional", t)
            }
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
            // Sprint 25b Phase C — lazy retention sweep. Runs at
            // recording-start so the LRU is deterministic without a
            // background scheduler. No-op when retentionPeriod is
            // [com.handy.app.settings.RetentionPeriod.Never]. The
            // wrap in runCatching is defensive — `evictByRetention`
            // already swallows per-file delete failures, but a top-
            // level try guards against the helper itself throwing.
            // Sprint 24 rule — structured-concurrency cancellation must
            // propagate to the parent launcher; never swallow. The
            // kotlin stdlib's `runCatching` catches Throwable and would
            // convert a CancellationException at the suspension point
            // inside `evictByRetention` into a Result.failure that never
            // re-throws, silently breaking the surrounding coroutine
            // contract. Explicit try/catch with the re-throw above any
            // logging is the canonical defense-in-depth pattern.
            try {
                recordingRepository.evictByRetention(
                    nowMillis = System.currentTimeMillis(),
                    period = SettingsStore(getApplication()).retentionPeriod,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "evictByRetention: sweep failed", e)
            }

            // Sprint 25a factory binding: prime the WAV file in lockstep
            // with AAudio capture start. The repository creates the
            // 44-byte placeholder header; `pushFloatArrayFrames` will
            // fill in PCM bytes once Sprint 25b wires the audio
            // pipeline. For Sprint 25a the recorded file is header-only
            // and the on-device verification asserts exactly that.
            val startPath = recordingRepository.startRecording(System.currentTimeMillis())
            Log.d(TAG, "startRecording: repo path=$startPath")
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
            // Sprint 25a factory binding: finalize the WAV header so the
            // path is ready to be passed to `nativeSaveHistory` in a
            // future sprint. The file size is currently exactly 44
            // bytes (header placeholder only) because
            // `pushFloatArrayFrames` is not yet wired — Sprint 25b
            // TODO. Concurrent-stop null safety: `stopRecording()`
            // returns null when no `startRecording` call has yet run;
            // we just propagate the null to the local val.
            val stopPath = recordingRepository.stopRecording()
            Log.d(TAG, "stopRecording: finalized repo path=$stopPath")
            EngineBridge.nativeFinalizeStream()
        }
    }

    fun cancelRecording() {
        // Sprint 25a factory binding: best-effort pre-finalize on
        // user-cancel. We call `RecordingRepository.stopRecording()`
        // (rather than a dedicated discard method) which finalizes
        // the WAV header and leaves a 44-byte placeholder WAV on disk.
        // The orphan is small and the directory-eviction LRU clears it
        // eventually. Sprint 25b will add an explicit
        // `RecordingRepository.cancelRecording()` discards-path method
        // once frame pumping is wired and a cancelled WAV could
        // carry partial PCM we don't want to leave behind.
        RecordingService.stop(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            val cancelPath = recordingRepository.stopRecording()
            Log.d(TAG, "cancelRecording: finalized repo path=$cancelPath")
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
            viewModelScope.launch(Dispatchers.IO) {
                val dictManager = DictionaryManager(getApplication())
                val customWords = dictManager.getActiveWordsList()
                val corrector = WordCorrector(customWords)
                val filtered = WordCorrector.filterTranscriptionOutput(text, null)
                var processedText = corrector.applyCustomWords(filtered)

                val app = getApplication() as com.handy.app.HandyApplication
                val settings = app.settingsStore
                if (settings.postProcessingEnabled) {
                    val provider = PostProcessProvider.fromToken(settings.postProcessProviderId)
                    val baseUrl = settings.postProcessEndpoint.ifBlank { provider.defaultBaseUrl }
                    val promptsRepo = PromptsRepository(getApplication())
                    val activePrompt = promptsRepo.getActivePrompt()

                    val postProcessor = PostProcessor(
                        apiUrl = baseUrl,
                        apiKey = settings.postProcessApiKey.takeIf { it.isNotBlank() },
                        modelName = settings.postProcessModel,
                    )
                    val result = postProcessor.process(
                        rawText = processedText,
                        promptTemplate = activePrompt.body,
                        hotwordHints = customWords,
                    )
                    processedText = result.getOrElse { processedText }
                }

                _finalText.value = processedText

                if (_imeModeEnabled) {
                    confirmInsert(processedText)
                } else {
                    _state.value = STATE_CONFIRM
                }
            }
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

    override fun onAudioFrames(samples: FloatArray) {
        // Sprint 25b — receive a Float32 frame from the Rust pipeline
        // consumer thread. We hop to `Dispatchers.IO` so the JNI-side
        // consumer thread is never blocked on the disk write that
        // RecordingRepository.pushFloatArrayFrames performs.
        //
        // Empty frames are silently dropped (the Rust side may emit
        // one if a partial resampler input chunk happens during
        // teardown). The repository's own guard short-circuits when
        // there is no active recording, so we don't need to gate here.
        //
        // Implementation note: RecordingRepository.pushFloatArrayFrames
        // is a suspend function (uses an internal Mutex on the write
        // path). Code-reviewer #1 caught an earlier non-compiling
        // variant that called a non-existent `pushFloatArrayFramesAsync`
        // — the correct shape is to wrap the suspend call in
        // viewModelScope.launch on Dispatchers.IO so the JNI consumer
        // thread only sees a synchronous fire-and-forget hop.
        if (samples.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            recordingRepository.pushFloatArrayFrames(samples)
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
