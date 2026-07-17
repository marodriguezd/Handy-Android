package com.handy.app.bridge

import java.nio.ByteBuffer

/**
 * JNI bridge to the Rust handy-core engine.
 * All functions are blocking on the calling thread unless noted otherwise.
 * For async operations, call from a coroutine dispatcher (Dispatchers.IO).
 */
object EngineBridge {

    init {
        try {
            System.loadLibrary("handy_core")
        } catch (e: UnsatisfiedLinkError) {
            // Native library is not available in JVM-only environments
            // (Robolectric tests, pure-JVM CI runners). Individual
            // `external fun` calls already throw UnsatisfiedLinkError at
            // invocation time. Without this try/catch, the
            // ExceptionInInitializerError poisons the whole class
            // (NoClassDefFoundError on every subsequent access).
            //
            // Logged at WARN so a real production packaging bug (missing
            // libhandy_core.so in APK `lib/`) surfaces in logcat instead
            // of being silently swallowed.
            android.util.Log.w(
                "EngineBridge",
                "handy_core native lib not loaded \u2014 engine will be non-functional",
                e,
            )
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────

    /**
     * Initialize the engine. Must be called once before any other function.
     * @param modelDir   Absolute path to directory containing model files (.gguf)
     * @param configDir  Absolute path to directory for settings/history SQLite DB
     * @param callback   Kotlin object implementing EngineCallback (GlobalRef stored by Rust)
     */
    external fun nativeInit(
        modelDir: String,
        configDir: String,
        callback: EngineCallback
    )

    /** Release all native resources. No further JNI calls are valid after this. */
    external fun nativeDestroy()

    // ── Engine Control ─────────────────────────────────────────

    /** Load the active model into memory. Triggers onStateChange(Loading). */
    external fun nativeLoadModel()

    /** Unload the model from memory. Triggers onStateChange(Idle). */
    external fun nativeUnloadModel()

    /** @return true if a model is currently loaded in memory */
    external fun nativeIsModelLoaded(): Boolean

    // ── Recording / Transcription ──────────────────────────────

    /**
     * Push audio frames into the engine for processing.
     * Must be a DirectByteBuffer allocated with ByteBuffer.allocateDirect()
     * and ByteOrder.nativeOrder().
     * @param buffer      DirectByteBuffer containing PCM float32 audio
     * @param frameCount  Number of float32 samples in the buffer
     */
    external fun nativePushAudio(buffer: ByteBuffer, frameCount: Int)

    /**
     * Start audio capture and streaming transcription.
     * @param sampleRate  Device native sample rate (e.g., 44100, 48000)
     * @param channelCount 1 (mono)
     */
    external fun nativeStartRecording(sampleRate: Int, channelCount: Int)

    /**
     * Attempt to start streaming transcription mid-recording.
     * Call after nativeLoadModel completes while recording is active.
     * Feeds any accumulated audio from the pipeline buffer into the stream.
     * @return true if streaming was successfully started
     */
    external fun nativeAttemptStreaming(): Boolean

    /** Finalize the stream. Triggers onTranscription(finalText, false). */
    external fun nativeFinalizeStream()

    /** Cancel recording and discard any in-progress transcription. */
    external fun nativeCancelRecording()

    /** @return true if the engine is currently recording/listening */
    external fun nativeIsRecording(): Boolean

    // ── Model Management ──────────────────────────────────────

    /** @return JSON array of ModelInfo objects */
    external fun nativeGetAvailableModels(): String

    /**
     * Start downloading a model from the catalog.
     * Progress is reported via [EngineCallback.onDownloadProgress].
     * @param modelId  Catalog model ID (e.g., "whisper-small-q5_0")
     */
    external fun nativeDownloadModel(modelId: String)

    /** Cancel an in-progress download. */
    external fun nativeCancelDownload()

    /** Delete a downloaded model and its files. */
    external fun nativeDeleteModel(modelId: String)

    /** Set the active model (must already be downloaded). */
    external fun nativeSetActiveModel(modelId: String)

    // ── Settings ───────────────────────────────────────────────

    /** @param idleTimeoutSeconds  Unload model after N seconds of inactivity */
    external fun nativeSetIdleTimeout(idleTimeoutSeconds: Int)

    /** @param endpoint  OpenAI-compatible API endpoint for post-processing */
    external fun nativeSetPostProcessEndpoint(endpoint: String)

    /** @param apiKey  API key for post-processing endpoint */
    external fun nativeSetPostProcessApiKey(apiKey: String)

    // ── History ────────────────────────────────────────────────

    /** Save a transcription entry. @param wavPath may be null. */
    external fun nativeSaveHistory(
        transcriptionText: String,
        postProcessedText: String?,
        wavPath: String?
    )

    /** @return JSON array of HistoryEntry objects, paginated */
    external fun nativeGetHistory(offset: Int, limit: Int): String

    /** Delete a history entry and its WAV file if present. */
    external fun nativeDeleteHistoryEntry(entryId: Long)

    /** Toggle saved/favorite status on a history entry. */
    external fun nativeToggleHistorySaved(entryId: Long)

    /**
     * Re-transcribe a recorded history entry from its persisted audio
     * file (if any). The Kotlin side writes the WAV to disk via
     * [com.handy.app.audio.RecordingRepository]; the Rust side reads
     * it and updates the entry's text in place.
     *
     * @param entryId  History id whose audioPath EngineBridge looks up.
     * @return `true` on success, `false` when the Rust side does not
     *         yet support the call (typical on an older
     *         `libhandy_core.so`). When `false`, the Kotlin caller
     *         ([com.handy.app.viewmodel.HistoryViewModel]) falls back
     *         to a simulated-delay stub so the spinner does not hang
     *         indefinitely.
     */
    external fun nativeRetryHistoryEntry(entryId: Long): Boolean
}
