# Handy for Android — Master Technical Specification

**Status:** Implementation in progress (Sprint 5 complete)  
**Version:** 1.5.0  
**Target:** Android 8.0+ (API 26), `targetSdk 35`  
**Architecture:** `aarch64-linux-android` (arm64-v8a) mandatory; `x86_64-linux-android` for emulator only

---

## 1. Executive Summary and Scope

### 1.1 What This Is

Handy for Android is an **offline, on-device speech-to-text dictation engine**. It captures microphone audio, processes it through a local Whisper-family model (GGUF format via `transcribe-cpp`), and injects the transcribed text into the currently active application — without requiring an internet connection.

### 1.2 Core Dependencies

| Dependency | Version | Purpose | License | Status |
|---|---|---|---|---|
| `transcribe-cpp` | 0.1.3 | GGUF/Whisper inference engine | MIT | ✅ Integrated (Sprint 5) |
| `aaudio-sys` | ≥ 0.1.0 | Low-latency audio capture FFI (AAudio NDK API) | (Android SDK) | ✅ Integrated |
| `rubato` | ≥ 0.16 | Audio resampling (device rate → 16 kHz) | MIT | ✅ Integrated |
| EnergyVAD (custom) | — | Energy-based Voice Activity Detection (no ONNX dep) | MIT | ✅ Built |
| `rusqlite` | ≥ 0.37 | History persistence (bundled SQLite) | MIT | ✅ Integrated |
| `reqwest` | ≥ 0.12 | Model download + LLM post-processing HTTP | MIT/Apache 2.0 | ✅ Integrated |
| `hound` | ≥ 3.5 | WAV file I/O for recording archival | MIT | ✅ Available |
| Jetpack Compose | BOM 2025.x | Declarative native UI | Apache 2.0 | ✅ |
| `vad-rs` | git (Silero V4) | ONNX-based VAD (replaced by EnergyVAD — deferred) | MIT | ⏳ Optional |
| ONNX Runtime | ≥ 1.19 (optional) | Alternative inference backend | MIT | ⏳ Optional |

### 1.3 What We Preserve from Desktop Handy

The following modules are ported with **minimal code changes** from the original `src-tauri/src/` Rust codebase:

- `managers/transcription.rs` — Model loading, batch/streaming inference orchestration (adapter pattern)
- `managers/model.rs` — Model catalog, download, SHA-256 verification, discovery (with HTTP via reqwest)
- `managers/history.rs` — SQLite history with schema migrations
- `audio_toolkit/audio/resampler.rs` — `FrameResampler` via `rubato::FftFixedIn`
- `audio_toolkit/vad/smoothed.rs` — Onset/hangover state machine (reimplemented with EnergyVAD backend)
- `actions.rs` — Record→Transcribe→Post-process pipeline logic (extracted from Tauri glue)

### 1.4 What Is Entirely Discarded

- Tauri framework (`lib.rs`, `main.rs`, `commands/`, all plugins)
- Entire React/TypeScript frontend (`src/`)
- `cpal` (replaced by AAudio/AudioRecord)
- `enigo` (replaced by IME `InputConnection.commitText()`)
- `rdev` (replaced by Foreground Service notification actions)
- `rodio` (replaced by Android AudioTrack)
- `tauri-plugin-store` (replaced by Android DataStore)

### 1.5 What Is Added for Android

- **Engine singleton** via `HandyApplication` — `nativeInit` called exactly once, shared by all consumers (IME, MainActivity)
- **IME lifecycle independence** — The engine lives beyond IME `onDestroy`; switching keyboards does not kill the Rust core
- **ProGuard rules** (`app/proguard-rules.pro`) — JNI class/method name preservation for release builds
- **RecordingService** — Foreground Service with notification, `FOREGROUND_SERVICE_TYPE_MICROPHONE`, AudioRecord fallback path, WakeLock
- **DictationScreen** — E2E test screen with start/stop button, VAD level bar, partial/final transcription display, state indicator
- **AAudio capture** — Low-latency microphone capture via `aaudio-sys` FFI, callback-based, 16kHz float32 mono, shared/exclusive mode fallback
- **EnergyVAD** — Lightweight energy-based VAD (no ONNX dependency), adaptive noise floor tracking, RMS threshold detection
- **SmoothedVad** — State machine with pre-roll (15 frames), onset (2 frames), hangover (55/15 frames)
- **String resources** — All IME and app UI strings mapped in `res/values/strings.xml` (110 resources)
- **Power-User injection system** — Strategy pattern (`InjectorRouter`) with three strategies: `ShizukuInjector` (UID 2000 shell-level injection via AIDL IPC), `ImeInjector` (InputConnection.commitText()), and `ClipboardInjector` (clipboard fallback with Toast). Strategy selection is automatic based on Shizuku availability and user preference (`SettingsStore.shizukuEnabled`).
- **AIDL IPC bridge** — `IHandyUserService.aidl` defining `getInputServiceBinder()` method. `HandyUserService` runs in process `:shizuku` (UID 2000) via `Shizuku.bindUserService()`, bypassing Android 14/15 Core Platform API reflection restrictions. The `getInputServiceBinder()` call uses `ServiceManager.getService("input")` without hidden API errors.
- **ProGuard rules for AIDL** — Explicit keep rules for `IHandyUserService`, `IHandyUserService$Stub`, `IHandyUserService$Proxy`, and `HandyUserService` to prevent R8 obfuscation of the generated IPC deserialization code.

---

## 2. System Architecture

### 2.1 Layer Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ANDROID APPLICATION LAYER                         │
│                                                                     │
│  ┌──────────────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │ MainActivity         │  │ HandyIME         │  │ Recording     │ │
│  │ ┌──────────────────┐ │  │ (InputMethod     │  │ Foreground    │ │
│  │ │ Settings Screen  │ │  │  Service)        │  │ Service       │ │
│  │ │ Model Management │ │  │ ┌──────────────┐ │  │ ┌───────────┐ │ │
│  │ │ History Browser  │ │  │ │ Dictation    │ │  │ │Persistent │ │ │
│  │ │ Onboarding Flow  │ │  │ │ Button +     │ │  │ │Notif.     │ │ │
│  │ └──────────────────┘ │  │ │ Live Preview │ │  │ │(Start/Stop│ │ │
│  │ Compose Navigation   │  │ │ └────────────┘ │  │ │ dictation)│ │ │
│  └──────────┬───────────┘  │ │ InputConnection│ │  │ └───────────┘ │ │
│             │              │ │ .commitText()  │ │  └───────┬───────┘ │
│             │              │ └────────────────┘ │          │         │
│  ┌──────────┴──────────────┴────────────────────┴──────────┴───────┐ │
│  │                   ViewModel / State Layer                        │ │
│  │                                                                  │ │
│  │  EngineViewModel:                                                │ │
│  │    StateFlow<EngineState>        → Idle | Loading | Listening    │ │
│  │                                   | Transcribing | Error         │ │
│  │    StateFlow<String>             → partialText (live streaming)  │ │
│  │    StateFlow<String>             → finalText (committed)         │ │
│  │    StateFlow<Float>              → vadLevel (voice meter)        │ │
│  │    StateFlow<DownloadProgress>   → model download progress       │ │
│  │    StateFlow<List<ModelInfo>>    → available models              │ │
│  │    StateFlow<List<HistoryEntry>> → transcription history         │ │
│  └─────────────────────────────┬────────────────────────────────────┘ │
│                                │  JNI Boundary                       │
├────────────────────────────────┼──────────────────────────────────────┤
│                      JNI BRIDGE LAYER                                │
│                                                                      │
│  Kotlin: EngineBridge.kt (external fun declarations)                 │
│  Rust:   handy-core/src/jni_bridge.rs (#[no_mangle] implementations) │
│                                                                      │
│  Audio path: Kotlin → DirectByteBuffer → JNI → Rust (zero-copy)     │
│  Text path:  Rust → JNIEnv::call_method → Kotlin callback            │
│  Control:    Kotlin → JNI → Rust (synchronous + async commands)      │
│                                                                      │
├────────────────────────────────┼──────────────────────────────────────┤
│                      RUST CORE LAYER                                 │
│                                                                      │
│  Crate: handy-core (cdylib → libhandy_core.so)                       │
│  Target: aarch64-linux-android                                       │
│                                                                      │
│  ┌───────────────────┐  ┌────────────────┐  ┌────────────────────┐  │
│  │ AudioEngine        │  │ STTEngine      │  │ ModelManager      │  │
│  │                    │  │                │  │                    │  │
│  │ AudioCaptureThread │  │ Session::stream│  │ Catalog + Download│  │
│  │ (AAudio callback)  │  │  (transcribe-  │  │ SHA-256 verify     │  │
│  │     ↓              │  │   cpp)         │  │ HF Hub + HTTPS     │  │
│  │ FrameResampler     │  │     ↓          │  │ gguf_meta probing │  │
│  │ (rubato FftFixedIn)│  │ StreamRouter   │  └────────────────────┘  │
│  │     ↓              │  │ (mpsc channel) │                          │
│  │ SileroVad+         │  │     ↓          │  ┌────────────────────┐  │
│  │ SmoothedVad        │  │ on_partial()   │  │ HistoryManager     │  │
│  │ (onset/hangover)   │  │ on_final()     │  │ SQLite + WAV       │  │
│  │     ↓              │  │     ↓          │  └────────────────────┘  │
│  │ VoiceFrames        │  │ JNI callback   │                          │
│  │ (Vec<f32>)         │  │ → Kotlin       │                          │
│  └───────────────────┘  └────────────────┘                          │
│                                                                      │
│  Dependencies: transcribe-cpp, transcribe-rs, rubato, vad-rs,        │
│                rusqlite, reqwest, hound                               │
│                                                                      │
│  Internal threading:                                                 │
│    audio_thread:    AAudio callback → resample → VAD → speech buffer │
│    inference_pool:  rayon ThreadPool (2 threads max for mobile)      │
│    callback_thread: JNI callbacks dispatched to JVM-attached thread   │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 End-to-End Data Flow

```
USER ACTION: Tap dictation button in DictationScreen / IME / Notification action
    │
    ▼
[1] Kotlin: EngineViewModel.startRecording()
    │   _state = STATE_LISTENING
    │   viewModelScope.launch(IO) {
    │       EngineBridge.nativeLoadModel()
    │       EngineBridge.nativeStartRecording(16000, 1)
    │   }
    │
    ▼
[2] JNI: nativeStartRecording(env, sample_rate=16000, channel_count=1)
    │
    ▼
[3] Rust: transcription_engine.start_stream(on_partial, on_final)
    │   → Creates mpsc channel via StreamRouter::open_channel()
    │   → Spawns StreamWorker thread (partial ~500ms, final on Finalize cmd)
    │
    ▼
[4] Rust: audio_pipeline.start(16000)
    │   → AudioCapture::start() → AAudio stream opened: 16kHz, mono, float32
    │
    ▼
[5] AAudio Callback (runs on AAudio-managed thread)
    │
    ├──[5a] AudioCapture callback delivers &[f32] samples to PipelineInner
    │
    ├──[5b] FrameResampler::push(samples) → 480-sample frames @ 16kHz
    │        (no-op if device native rate == 16kHz)
    │
    ├──[5c] SmoothedVad::push_frame(frame)
    │         EnergyVad: RMS energy > noise_floor × threshold_factor → voice
    │         State machine:
    │           Prefill:  15 frames buffered, returned as Noise
    │           Onset:    2 consecutive voice frames → Speech(combined pre-roll + current)
    │           Hangover: 55 frames tail after last voice → Noise
    │
    ├──[5d] VadFrame::Speech(data) → accumulated_samples.extend(data)
    │                                  StreamRouter::feed(data)
    │                                  on_vad_level(energy)
    │
    └──[5e] VadFrame::Noise → on_vad_level(energy × 0.5)
    │
    ▼
[6] StreamWorker (background thread)
    │   Receives frames from StreamRouter::rx (mpsc::Receiver)
    │   Every ~500ms: on_partial(stream.text().display()) → JNI callback
    │   When VAD hangover expires → (pipeline continues until stop)
    │
    ▼
[7] USER ACTION: Tap stop button in DictationScreen / IME
    │
    ▼
[8] Kotlin: EngineViewModel.stopRecording()
    │   _state = STATE_TRANSCRIBING
    │   viewModelScope.launch(IO) {
    │       EngineBridge.nativeFinalizeStream()
    │   }
    │
    ▼
[9] JNI: nativeFinalizeStream(env)
    │
    ▼
[10] Rust: nativeFinalizeStream()
    │   → audio_pipeline.stop():
    │       Stop AAudio, close stream, flush resampler,
    │       push remaining frames through SmoothedVad
    │       → return accumulated Vec<f32> (VAD-filtered audio)
    │   → router.feed(accumulated) for final processing
    │
    ▼
[11] Rust: transcription_engine.finalize_stream(worker_id)
    │   → router.send(StreamCmd::Finalize)
    │   → StreamWorker thread:
    │       Finalize received → stream.finalize() → stream.text().display()
    │   → worker.join()
    │   → Returns committed text from transcribe-cpp Stream
    │
    ▼
[12] Rust: post_process(&text)
    │   → remove_filler_words(): removes "um", "uh", "like", etc. (word boundaries, case-preserving)
    │   → collapse_stutters(): collapses "the the the" → "the"
    │   → Always applied (not gated by endpoint config)
    │
    ▼
[13] JNI callback: dispatch_transcription(processed, false)
    │
    ▼
[14] Kotlin: EngineViewModel.onTranscription(text, false)
    │   → _finalText.value = text
    │   → _state.value = STATE_CONFIRM
    │   → IME shows ConfirmMode with Insert / Retry buttons
    │   → User taps Insert → engineViewModel.confirmInsert(text)
    │       → injectorRouter.inject(text)
    │           ├── ShizukuInjector (if enabled + available): UID 2000 paste
    │           ├── ImeInjector (if IME active): commitText()
    │           └── ClipboardInjector (fallback): clipboard + Toast
    │       → On success: _state = STATE_IDLE, clear texts
    │
    ▼
[15] Rust: nativeSaveHistory(processed, post_processed, null)
    │   → HistoryManager::save_entry(...) → SQLite INSERT
    │
    ▼
[16] Engine returns to Idle state
        → Dispatch STATE_IDLE via JNI callback
```

---

## 3. JNI Bridge Specification (The Contract)

### 3.1 Module Organization

```
handy-android/
├── app/src/main/java/com/handy/app/
│   └── bridge/
│       ├── EngineBridge.kt       # external fun declarations (Kotlin → Rust)
│       └── EngineCallback.kt     # Interface implemented by Kotlin (Rust → Kotlin)
│
├── handy-core/src/
│   ├── lib.rs                    # JNI_OnLoad, crate init
│   ├── engine.rs                 # EngineState struct, ENGINE/JAVA_VM OnceLock singletons
│   ├── jni_bridge.rs             # All #[no_mangle] JNI function implementations
│   └── jni_callback.rs           # JNIEnv::call_method helpers for Rust → Kotlin
│
├── app/src/main/java/com/handy/app/
│   ├── HandyApplication.kt       # Process-scoped singleton holder for EngineViewModel
│   ├── service/RecordingService.kt  # Foreground Service (AudioRecord fallback)
│   ├── ime/HandyInputMethodService.kt  # IME with shared EngineViewModel
│   └── ui/dictation/
│       └── DictationScreen.kt    # E2E dictation test screen (Sprint 4)
```

### 3.2 EngineBridge.kt — Kotlin Side

```kotlin
package com.handy.app.bridge

import java.nio.ByteBuffer

/**
 * JNI bridge to the Rust handy-core engine.
 * All functions are blocking on the calling thread unless noted otherwise.
 * For async operations, call from a coroutine dispatcher (Dispatchers.IO).
 */
object EngineBridge {

    init {
        System.loadLibrary("handy_core")
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
     * Start audio capture and streaming transcription.
     * The engine will call [EngineCallback.onAudioBufferNeeded] on the
     * AAudio callback thread to request the next chunk of audio.
     *
     * @param sampleRate  Device native sample rate (e.g., 44100, 48000)
     * @param channelCount 1 (mono)
     */
    external fun nativeStartRecording(sampleRate: Int, channelCount: Int)

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
}
```

### 3.3 EngineCallback.kt — Callback Interface

```kotlin
package com.handy.app.bridge

/**
 * Callbacks invoked by the Rust engine via JNI.
 * IMPLEMENTATION NOTE: All callbacks are invoked from a Rust-managed thread
 * that has been attached to the JVM via JavaVM::attach_current_thread().
 * Implementations must post to the main thread if updating UI.
 */
interface EngineCallback {

    /**
     * Engine state transition.
     * @param state  0=Idle, 1=Loading, 2=Listening, 3=Transcribing, 4=Error
     */
    fun onStateChange(state: Int)

    /**
     * Transcription result.
     * @param text       The transcribed text
     * @param isPartial  true = intermediate streaming result, false = final committed text
     */
    fun onTranscription(text: String, isPartial: Boolean)

    /**
     * Voice activity level for the audio level meter.
     * @param level  Probability [0.0, 1.0], updated ~10 times/sec
     */
    fun onVadLevel(level: Float)

    /**
     * Error callback.
     * @param code    Machine-readable error code
     * @param message Human-readable error description
     */
    fun onError(code: Int, message: String)

    /**
     * Model download progress.
     * @param modelId      The model being downloaded
     * @param bytesSoFar   Bytes downloaded
     * @param totalBytes   Total expected bytes (-1 if unknown)
     */
    fun onDownloadProgress(modelId: String, bytesSoFar: Long, totalBytes: Long)

    /**
     * Model download completed (or failed).
     * @param modelId  The model ID
     * @param success  true if download and verification succeeded
     * @param errorMsg null on success, error description on failure
     */
    fun onDownloadComplete(modelId: String, success: Boolean, errorMsg: String?)
}
```

### 3.4 jni_bridge.rs — Rust Side Implementation (Signatures)

```rust
// handy-core/src/jni_bridge.rs (695 lines)
// All functions follow JNI naming convention:
// Java_com_handy_app_bridge_EngineBridge_<methodName>
//
// ALL 21 FUNCTIONS FULLY IMPLEMENTED (695 lines).

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, JByteBuffer, GlobalRef};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;

// ── Lifecycle ──────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeInit<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_dir: JString<'local>,
    config_dir: JString<'local>,
    callback: JObject<'local>,
) {
    // 1. Extract paths from JString → Rust String
    // 2. Create GlobalRef to callback object
    // 3. Initialize Engine singleton (ENGINE OnceLock<Mutex<Option<EngineState>>>)
    // 4. Dispatch initial onStateChange(Idle) callback
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDestroy<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    // 1. Cancel any in-progress recording
    // 2. Set ENGINE Mutex to None → drops EngineState → drops GlobalRef
    //    (jni crate v0.21 handles DeleteGlobalRef in GlobalRef::drop via internal JavaVM)
}

// ── Recording / Audio ──────────────────────────────────────────

/// Zero-copy audio feed from Kotlin's DirectByteBuffer (fallback path).
/// CONSTRAINT: buffer pointer is valid ONLY within this JNI call.
/// Samples are copied to Rust-owned Vec<f32> via audio_pipeline.push_audio().
#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativePushAudio<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    buffer: JObject<'local>,     // java.nio.ByteBuffer (must be direct)
    frame_count: jint,
) {
    let ptr = env.get_direct_buffer_address(&buffer.into()).expect("DirectByteBuffer required");
    let capacity = env.get_direct_buffer_capacity(&buffer.into());
    // Validate frame_count * 4 <= capacity
    unsafe {
        let samples: &[f32] = std::slice::from_raw_parts(ptr as *const f32, frame_count as usize);
        with_engine(|state| {
            let _ = state.audio_pipeline.push_audio(samples, frame_count as usize);
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeStartRecording<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    sample_rate: jint,
    channel_count: jint,
) {
    // 1. Attach JVM thread for callbacks
    // 2. Create on_partial + on_final callbacks → JNI dispatch
    // 3. transcription_engine.start_stream(on_partial, on_final)
    // 4. audio_pipeline.start(sample_rate)  → AAudio capture
    // 5. Set is_recording=true, dispatch onStateChange(Listening)
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeFinalizeStream<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    // 1. audio_pipeline.stop() → returns accumulated VAD-filtered audio Vec<f32>
    // 2. router.feed(accumulated) → send remaining frames to stream worker
    // 3. transcription_engine.finalize_stream(worker_id) → final text
    // 4. post_process(&text) → filler removal + stutter collapse
    // 5. dispatch_transcription(processed, false)
    // 6. router.close(), is_recording=false, dispatch onStateChange(Idle)
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeCancelRecording<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    // 1. audio_pipeline.cancel() → discard audio
    // 2. transcription_engine.cancel_stream() → discard partial transcription
    // 3. Set worker_id=None, is_recording=false, dispatch onStateChange(Idle)
}

// ── History (nullable-safe parameters) ─────────────────────────

/// Kotlin passes postProcessedText: String? and wavPath: String?.
/// Rust MUST accept JObject and check is_null() before casting to JString.
/// DO NOT declare nullable Kotlin params as JString — this is Undefined Behavior.
#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSaveHistory<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    transcription_text: JString<'local>,    // String  (non-null)
    post_processed_text: JObject<'local>,    // String? (nullable → JObject)
    wav_path: JObject<'local>,              // String? (nullable → JObject)
) {
    let text: String = env.get_string(&transcription_text).unwrap().into();

    let _post_processed: Option<String> = if post_processed_text.is_null() {
        None
    } else {
        Some(env.get_string(&JString::from(post_processed_text)).unwrap().into())
    };

    let _wav_path: Option<String> = if wav_path.is_null() {
        None
    } else {
        Some(env.get_string(&JString::from(wav_path)).unwrap().into())
    };

    info!("nativeSaveHistory: {text} (stub)");
}
```

### 3.5 Nullable Safety Pattern (JNI)

**Problem:** Kotlin `external fun` parameters typed as `String?` (nullable) can be `null`.
JNI `JString<'local>` in the `jni` crate assumes non-null — dereferencing null → undefined behavior → native crash.

**Rule:** Any Kotlin parameter declared `String?` or `Any?` MUST be received as `JObject<'local>` in Rust.
Check `.is_null()` before calling `.into()` to cast to the concrete subtype.

```rust
// CORRECT — nullable-safe
fn handle_nullable(env: &mut JNIEnv, maybe_string: JObject<'local>) -> Option<String> {
    if maybe_string.is_null() {
        None
    } else {
        Some(env.get_string(&JString::from(maybe_string)).ok()?.into())
    }
}

// WRONG — will crash if Kotlin passes null
fn handle_nullable_bad(env: &mut JNIEnv, maybe_string: JString<'local>) -> Option<String> {
    env.get_string(&maybe_string).ok().map(|s| s.into())
}
```

This pattern applies to: `nativeSaveHistory::post_processed_text`, `nativeSaveHistory::wav_path`,
and `dispatch_download_complete::error_msg` callback parameter.

### 3.6 Threading Contract

| Thread | Owner | Purpose | JVM Attached |
|---|---|---|---|---|
| `main` | Android | UI thread, Compose recomposition | Yes (always) |
| `audio_callback` | AAudio | Real-time audio capture, resampling, VAD | Stored in AudioCapture callback |
| `stream_worker` | Rust (`std::thread`) | StreamRouter receiver, partial/final transcription | JNI callbacks via `get_env_attached()` |
| `download_worker` | Rust (tokio/reqwest) | Model download via HTTPS/HF Hub | `attach_current_thread_as_daemon()` |
| `RecordingService` | Android | AudioRecord fallback capture (optional) | Not needed (Kotlin side) |

**Rule:** Any Rust thread that must invoke JNI callbacks MUST call `JavaVM::attach_current_thread()` at thread start (or `attach_current_thread_as_daemon()`). The `JavaVM` pointer is obtained during `JNI_OnLoad` and stored in a `OnceLock<JavaVM>`.

### 3.7 Audio Buffer Zero-Copy Contract

```rust
// Kotlin side: allocates a DirectByteBuffer once, reuses it
// val audioBuffer: ByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES)

// Kotlin side: AAudio reads into audioBuffer, then passes to Rust
// EngineBridge.nativePushAudio(audioBuffer, frameCount)

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativePushAudio(
    env: JNIEnv,
    _class: JClass,
    buffer: JObject,     // java.nio.ByteBuffer (must be direct)
    frame_count: jint,
) {
    unsafe {
        // Zero-copy: get pointer directly from DirectByteBuffer
        let ptr = env.get_direct_buffer_address(&buffer.into())
            .expect("Buffer must be a DirectByteBuffer");
        let samples: &[f32] = std::slice::from_raw_parts(
            ptr as *const f32,
            frame_count as usize
        );
        // Feed samples into audio pipeline (resampler → VAD → stream router)
        engine.audio_engine.push_samples(samples);
    }
}
```

**Constraint:** The `DirectByteBuffer` must be allocated with `ByteOrder.nativeOrder()` and remain valid for the duration of the `nativePushAudio` call. Do not mutate the buffer from Kotlin while Rust holds the pointer.

---

## 4. Audio Pipeline and Memory Management

### 4.0 Engine Lifecycle & Build Configuration

The Rust engine is a **process-wide singleton** owned by `HandyApplication`:

```
AndroidManifest.xml
    └── <application android:name=".HandyApplication">
            │
            ├── HandyApplication.kt
            │   └── val engineViewModel = EngineViewModel(this)  // lazy singleton
            │       └── init { nativeInit(...) }                  // called exactly ONCE
            │
            ├── MainActivity
            │   └── accesses (application as HandyApplication).engineViewModel
            │
            └── HandyInputMethodService
                └── accesses (application as HandyApplication).engineViewModel
                    └── onDestroy() does NOT call engineViewModel.cleanup()
```

**Critical lifecycle rule:** The IME's `onDestroy()` MUST NOT call `nativeDestroy()`.
When the user switches from Handy IME to Gboard, the `InputMethodService` is destroyed.
If `nativeDestroy()` were called, the Rust engine would be torn down for the entire app process,
breaking any in-progress or queued transcriptions and leaving `MainActivity` with a dead engine.

Instead:
- `EngineViewModel.cleanup()` is explicit and idempotent (guarded by `cleanedUp: Boolean` flag)
- It is reserved for process-level teardown (`Application.onTerminate()`) or explicit user action
- The OS will clean up native resources when the process is killed

**ProGuard / R8 Release Protection:**

```proguard
# app/proguard-rules.pro — CRITICAL for release builds
#
# JNI classes (Kotlin ↔ Rust)
-keep class com.handy.app.bridge.EngineBridge {
    native <methods>;
    <init>();
}
-keep interface com.handy.app.bridge.EngineCallback { *; }
-keep class * implements com.handy.app.bridge.EngineCallback { *; }

# Shizuku — suppress hidden API warnings in our code
-dontwarn android.os.ServiceManager
-dontwarn android.hardware.input.IInputManager

# Shizuku SDK API
-keep class moe.shizuku.api.** { *; }

# AIDL IPC inner classes (ShizukuUserService deserialization)
-keep class com.handy.app.injection.IHandyUserService { *; }
-keep class com.handy.app.injection.IHandyUserService$Stub { *; }
-keep class com.handy.app.injection.IHandyUserService$Proxy { *; }
-keep class com.handy.app.injection.HandyUserService { *; }
```

Without these rules, R8 obfuscation renames JNI callbacks and AIDL inner classes,
causing `JNIEnv::call_method` to fail with `NoSuchMethodError` at runtime,
and `IHandyUserService.Stub.asInterface()` to crash with `ClassNotFoundException`.

### 4.1 Audio Pipeline Specification

```
Device Microphone (native rate, mono)
    │
    ▼
AAudio stream (shared mode, exclusive fallback)
    │ FFI callback delivers float32 PCM frames
    │ 16kHz requested; resampler adapts if actual rate differs
    ▼
AudioCapture (Rust via aaudio-sys)
    │ data_callback_thunk(stream, user_data, audio_data, num_frames)
    │ Converts raw pointer to &[f32] → PipelineInner callback
    ▼
FrameResampler (rubato::FftFixedIn<f32>)
    │ Input:  device_rate Hz, mono, 1024-sample chunks
    │ Output: 16000 Hz, mono, 30ms frames (480 samples/frame)
    │ Passthrough when input_rate == output_rate
    ▼
EnergyVad (energy-based, no ONNX dependency)
    │ RMS energy > noise_floor × threshold_factor (0.3) → voice
    │ Adaptive noise floor: noise_floor *= (1 - alpha) + energy * alpha, α = 0.01
    │ Input:  480 samples @ 16kHz
    │ Output: bool (voice / noise)
    ▼
SmoothedVad (state machine)
    │ States: Prefill → Onset → Speech → Hangover → Silence
    │ Prefill:  15 frames buffered (all returned as Noise)
    │ Onset:    2 consecutive voice frames → Speech(15 pre-roll + 2 onset frames)
    │           Pre-roll buffer (VecDeque) drained into combined Speech output
    │ Hangover: 55 frames (~1.65s) after last voice → Noise
    ▼
PipelineInner (process_samples)
    │ resampler.push() emits 480-frame chunks → SmoothedVad → match VadFrame
    │
    ├── VadFrame::Speech(data) → audio_buffer.extend(data)
    │                             on_audio_frame callback → StreamRouter::feed()
    │                             on_vad_level(RMS energy × 5.0, min with 1.0)
    │
    └── VadFrame::Noise → on_vad_level(RMS energy × 5.0 × 0.5)
                            │
                            ▼
                    EngineViewModel._vadLevel (graduated 0.0–1.0)
```

### 4.2 AAudio Stream Configuration (audio/capture.rs)

```rust
// Implementation: audio/capture.rs via aaudio-sys FFI
// Builder pattern:
//   1. AAudio_createStreamBuilder(&mut builder_ptr)
//   2. AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT)
//   3. AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_SHARED)
//   4. AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT)
//   5. AAudioStreamBuilder_setSampleRate(builder, 16000)
//   6. AAudioStreamBuilder_setChannelCount(builder, 1)
//   7. AAudioStreamBuilder_setDataCallback(builder, data_callback_thunk, user_data)
//   8. AAudioStreamBuilder_setErrorCallback(builder, error_callback_thunk, user_data)
//   9. AAudioStreamBuilder_openStream(builder, &mut stream_ptr)
//   10. AAudioStream_requestStart(stream_ptr)
```

**Fallback path:** Stored in `RecordingService.kt` (AudioRecord-based capture via `nativePushAudio`). The RecordingService is NOT started by default — AAudio is the sole capture source. RecordingService serves as a fallback for devices where AAudio is unavailable and can be enabled for troubleshooting.

### 4.3 Model Lifecycle and Memory Protection

```
                    ┌─────────────────────────────────┐
                    │         MODEL STATES             │
                    └─────────────────────────────────┘

   ┌──────────┐   load_model()   ┌──────────┐
   │ UNLOADED │ ───────────────→ │  LOADED  │
   │ (0 MB)   │                  │ (~500 MB │
   └──────────┘                  │  whisper  │
        ↑                        │  small Q5)│
        │                        └─────┬─────┘
        │                              │
        │   unload_model()             │ startRecording()
        │   OR idle_timeout            │
        │                              ▼
        │                        ┌──────────────┐
        │                        │  STREAMING   │
        │                        │ (model held  │
        │                        │  for duration│
        │                        │  of session) │
        │                        └──────┬───────┘
        │                               │
        │                    finalizeStream() / cancelRecording()
        │                               │
        │                               ▼
        │                        ┌──────────────┐
        │                        │  LOADED      │
        │                        │ (idle timer  │
        │                        │  starts)     │
        │                        └──────┬───────┘
        │                               │
        │               idle_timeout (default 30s, configurable)
        │                               │
        └───────────────────────────────┘
```

**Idle timeout rules:**
1. Timer starts when `finalizeStream()` or `cancelRecording()` completes.
2. If `startRecording()` is called before the timer expires, the timer is reset.
3. If the timer expires, `unload_model()` is called synchronously on the idle_watcher thread.
4. The user can configure the timeout: 10s (aggressive battery save), 30s (default), 60s, 120s, or "Never" (only unload on explicit request).
5. App going to background does NOT trigger unload (Foreground Service keeps the process alive).

**OOM Protection Strategy:**
1. **Model size budget:** Limit loaded model to ≤ 1.5 GB. Warn the user if they try to load a model larger than 50% of `Runtime.getRuntime().maxMemory()`.
2. **Streaming buffer cap:** The `accumulated_samples: Vec<f32>` for a single recording session is capped at 300 seconds × 16000 samples/sec × 4 bytes = ~19.2 MB. If exceeded, force-finalize the stream.
3. **Memory pressure callback:** Register `ComponentCallbacks2.onTrimMemory()`. On `TRIM_MEMORY_RUNNING_CRITICAL` (level ≥ 15), immediately unload the model and cancel any active recording.
4. **Model quantisation preference:** Default catalog prioritizes Q5_0 or Q4_0 quantized GGUF models for mobile. Full FP16 models are not offered unless the user explicitly enables "Experimental / High Quality" mode.

### 4.4 Battery Optimization Rules

| Scenario | Action |
|---|---|
| VAD in Silence state | CPU sleep between frames (AAudio callback is ~10ms period → negligible) |
| VAD in Speech state | Inference active on 2 rayon threads |
| After finalizeStream | Idle timer starts; if user doesn't speak again, model unloads |
| Screen off + no recording | Process may be killed by Android Doze; Foreground Service + `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` mitigates |
| Streaming mode | `transcribe_cpp` processes frames incrementally (lower peak power than batch) |

---

## 5. Text Injection Strategy (IME)

### 5.1 Why IME

The `InputMethodService` is Android's first-class API for injecting text into other applications. Unlike Accessibility Services (restricted on Play Store) or clipboard (manual user paste required), the IME approach:
- Requires no special runtime permissions beyond user enabling the IME in Settings.
- Works in every `EditText` and `WebView` text field.
- Is the same mechanism used by Gboard voice typing, SwiftKey, FUTO Keyboard.
- Passes Play Store review without special justification.

### 5.2 IME Lifecycle Integration

The IME does NOT own its own `EngineViewModel` instance. Instead, it accesses the process-wide
singleton from `HandyApplication`. This ensures the engine survives IME destruction
(keyboard switches) and that `nativeInit` is called exactly once.

```kotlin
class HandyInputMethodService : InputMethodService() {

    // Singleton — shared with MainActivity via HandyApplication
    private val engineViewModel: EngineViewModel
        get() = (application as HandyApplication).engineViewModel

    override fun onDestroy() {
        super.onDestroy()
        // IMPORTANT: Do NOT call engineViewModel.cleanup() here.
        // The engine is process-wide; destroying it on IME switch
        // would kill the Rust core for the entire app.
    }
```
(Full implementation at `app/src/main/java/com/handy/app/ime/HandyInputMethodService.kt`)

### 5.3 IME Visual Contract

The IME Compose view has three modes:

**Mode A — Idle (no dictation active):**
- Large dictation microphone button (filled, primary color).
- Small keyboard switch button (icon-only, bottom-right).
- No text preview area.

**Mode B — Dictating:**
- Animated audio level meter (vertical bar reacting to `vadLevel`).
- Live transcription text area (scrollable, monospace, showing `partialText`).
- Stop button (filled red, replaces microphone).
- Keyboard switch button hidden.

**Mode C — Post-dictation confirmation:**
- Final transcribed text in a scrollable, monospace preview.
- "Insert" commits text via `InputConnection.commitText(text, 1)`.
- "Retry" discards text and returns to Mode A.
- Keyboard switch button visible.

### 5.4 IME ↔ Engine Integration

```
HandyInputMethodService (IME Process)
    │
    ├── Observes EngineViewModel.state (StateFlow<Int>)
    │     0 (Idle) → Mode A
    │     1 (Loading), 2 (Listening), 3 (Transcribing) → Mode B
    │     4 (Error) → Error display
    │     5 (Confirm) → Mode C
    │
    ├── Observes EngineViewModel.partialText (StateFlow<String>)
    │     Updates live transcription display in Mode B
    │
    ├── Observes EngineViewModel.finalText (StateFlow<String?>)
    │     Triggers commitTranscription(text) → InputConnection.commitText()
    │
    ├── Observes EngineViewModel.vadLevel (StateFlow<Float>)
    │     Animates audio level meter in Mode B
    │
    └── Calls EngineViewModel.startRecording() / stopRecording()
          → JNI → Rust Engine
```

### 5.5 String Resources (IME)

All IME user-facing strings are defined in `res/values/strings.xml` (16 resources):

```xml
<string name="app_name">Handy</string>
<string name="dictation_button">Dictate</string>
<string name="stop_dictation">Stop</string>
<string name="switch_keyboard">Switch Keyboard</string>
<string name="text_copied_to_clipboard">Text copied to clipboard</string>
<string name="insert_text">Insert</string>
<string name="retry">Retry</string>
<string name="ime_label">Handy</string>
<string name="ime_subtype">Handy Dictation</string>
<string name="ime_settings">Handy Settings</string>
<string name="ime_enable_title">Enable Handy Keyboard</string>
<string name="ime_enable_message">To start dictating, enable Handy in your keyboard settings.</string>
<string name="ime_enable_action">Open Settings</string>
<string name="recording_notification_title">Handy Dictation</string>
<string name="recording_notification_text">Listening…</string>
```

References in code:
| Resource ID | Used by | Purpose |
|---|---|---|
| `R.string.dictation_button` | `HandyInputMethodService.kt`, `ImeContent` composable | Idle mode button label |
| `R.string.insert_text` | `ConfirmMode` composable | Insert button |
| `R.string.retry` | `ConfirmMode`, `ErrorMode` | Retry button |
| `R.string.text_copied_to_clipboard` | `HandyInputMethodService.fallbackToClipboard()` | Toast message |

### 5.6 Notification-Based Dictation Trigger

When the IME is not the active keyboard, the user can start dictation from the persistent notification:

```
┌─────────────────────────────────────────────┐
│  Handy Dictation                     [⚙️]   │
│  Ready — tap to start dictating              │
│                                              │
│  [🎤 Start Dictation]    [⌨️ Switch Keyboard] │
└─────────────────────────────────────────────┘
```

Actions:
- **Start Dictation:** Calls `RecordingService.startRecording()`. The text appears in the notification until the user switches to Handy IME to commit it.
- **Switch Keyboard:** Opens `Settings.ACTION_INPUT_METHOD_SETTINGS` so the user can enable Handy IME.

### 5.7 Power-User Injection: Strategy Pattern

The `InjectorRouter` (in `com.handy.app.injection`) implements a Strategy pattern to
automatically select the best text injection method based on Shizuku availability,
IME state, and user preferences:

```kotlin
// InjectorRouter.selectStrategy()
private fun selectStrategy(): InjectorStrategy = when {
    settingsStore.shizukuEnabled && shizukuInjector.isAvailable() -> shizukuInjector
    imeInjector.isAvailable() -> imeInjector
    else -> clipboardInjector
}
```

**Strategy hierarchy:**

| Priority | Strategy | Class | Mechanism | Requirements |
|----------|----------|-------|-----------|-------------|
| 1 (best) | Shizuku (UID 2000) | `ShizukuInjector` | Clipboard copy + KEYCODE_PASTE (279) via `IInputManager.injectInputEvent()` through Shizuku IPC | Shizuku APK installed + service running + permission granted + `shizukuEnabled` setting |
| 2 | IME | `ImeInjector` | `InputConnection.commitText(text, 1)` + `finishComposingText()` on `Dispatchers.Main` | Handy is the active IME + `currentInputConnection != null` |
| 3 (fallback) | Clipboard | `ClipboardInjector` | `ClipboardManager.setPrimaryClip()` + Toast | Always available |

**Cascade on failure:**
```
Shizuku fails (DeadObjectException, SecurityException, timeout)
  → Router logs error + exception, falls through
  → IME fails (InputConnection null)
    → Router logs error, falls through
    → ClipboardInjector (always succeeds)
```

All strategies communicate results via `Result<Unit>`. Failed strategies do not
mask successful fallbacks: if Shizuku fails but clipboard succeeds, the caller
receives `Result.success`.

#### 5.7.1 InjectorRouter Integration

The `InjectorRouter` is created in `HandyApplication` as a process-wide singleton
and injected into `EngineViewModel`:

```kotlin
// HandyApplication.kt
val injectorRouter: InjectorRouter by lazy {
    InjectorRouter(
        shizukuInjector = shizukuInjector,
        clipboardInjector = clipboardInjector,
        settingsStore = settingsStore,
    )
}

val engineViewModel: EngineViewModel by lazy {
    EngineViewModel(this, injectorRouter)
}
```

The `HandyInputMethodService` registers an `ImeInjector` at `onCreate()` via
`injectorRouter.setImeInjector(ImeInjector { currentInputConnection })`, replacing
the default stub (`ImeInjector { null }`).

#### 5.7.2 Text Delivery via ConfirmMode

When `EngineViewModel.onTranscription(text, false)` fires:
1. `_finalText.value = text` and `_state.value = STATE_CONFIRM` are set
2. IME shows ConfirmMode with Insert and Retry buttons
3. User taps **Insert** → `engineViewModel.confirmInsert(text)` is called:
   - `injectorRouter.inject(text)` runs on `Dispatchers.IO`
   - If success: `_state = STATE_IDLE`, `_finalText = null`, `_partialText = ""`
   - IME returns to IdleMode (mic button)
4. User taps **Retry** → `engineViewModel.resetPartialText()`:
   - Sets `_state = STATE_IDLE`, clears all texts
   - IME returns to IdleMode for a new dictation
5. If injection fails, state stays at CONFIRM — user can tap Insert again or Retry

### 5.8 ShizukuUserService — AIDL IPC Architecture

The hidden API restriction in Android 14+ (Core Platform API blockade) prevents
calling `ServiceManager.getService("input")` from app process. To bypass this,
the reflection call is delegated to a `HandyUserService` running in Shizuku's
process (UID 2000).

#### 5.8.1 AIDL Interface

```
// IHandyUserService.aidl
package com.handy.app.injection;

interface IHandyUserService {
    IBinder getInputServiceBinder();
}
```

The AIDL compiler generates `IHandyUserService.java` with `Stub` (extends `Binder`,
used by the service) and `Proxy` (used by the client via `asInterface()`).

#### 5.8.2 Service Implementation

```kotlin
// HandyUserService.kt — runs in process :shizuku (UID 2000)
class HandyUserService : Service() {
    private val binder = object : IHandyUserService.Stub() {
        override fun getInputServiceBinder(): IBinder {
            // Shizuku UID 2000 context — no hidden API restriction
            val smClass = Class.forName("android.os.ServiceManager")
            val getSvc = smClass.getDeclaredMethod("getService", String::class.java)
            return getSvc.invoke(null, "input") as IBinder
        }
    }
    override fun onBind(intent: Intent?): IBinder? = binder
}
```

#### 5.8.3 Manifest Declaration

```xml
<service
    android:name=".injection.HandyUserService"
    android:process=":shizuku"
    android:exported="true"
    android:permission="moe.shizuku.manager.permission.API_V23" />
```

- `process=":shizuku"` — runs in a process managed by Shizuku server (UID 2000)
- `permission="moe.shizuku.manager.permission.API_V23"` — only apps holding
  Shizuku's signature-level permission can bind to this service

#### 5.8.4 Client-Side Binding

```kotlin
// ShizukuInjector.kt
@Volatile
private var userService: IHandyUserService? = null

private val serviceConnection = object : Shizuku.UserServiceConnection {
    override fun onServiceConnected(component: ComponentName, binder: IBinder) {
        userService = IHandyUserService.Stub.asInterface(binder)
    }
    override fun onServiceDisconnected(component: ComponentName) {
        userService = null
    }
}

fun bindService() {
    val component = ComponentName(context, HandyUserService::class.java)
    Shizuku.bindUserService(component, serviceConnection)
}
```

#### 5.8.5 Injection Pipeline

```
ShizukuInjector.inject(text):
  1. clipboard.setPrimaryClip(text)
  2. delay(50ms)                  ← clipboard propagation
  3. val inputBinder = userService.getInputServiceBinder()
       └── IPC → HandyUserService (UID 2000)
             └── ServiceManager.getService("input")
  4. val wrapper = ShizukuBinderWrapper(inputBinder)
       └── All transact() calls forwarded through Shizuku (UID 2000)
  5. val inputManager = IInputManager.Stub.asInterface(wrapper)
  6. injectMethod.invoke(inputManager, KEYCODE_PASTE DOWN, 0)
  7. delay(10ms)
  8. injectMethod.invoke(inputManager, KEYCODE_PASTE UP, 0)
```

#### 5.8.6 Thread Safety

| Component | Thread | Protection |
|-----------|--------|------------|
| `userService` (AIDL proxy) | Written on binder thread (`onServiceConnected`), read on IO dispatcher (`inject`) | `@Volatile` annotation ensures happens-before across threads |
| `inject()` body | `withContext(Dispatchers.IO)` prevents blocking the main thread | Coroutine-safe; `Thread.sleep(10)` replaced by `delay(10)` |
| `ImeInjector.commitText()` | `withContext(Dispatchers.Main)` ensures `InputConnection` operations on UI thread | Prevents ANR/jank from binder call on IO thread |
| Clipboard `setPrimaryClip()` | Runs on IO dispatcher | `setPrimaryClip()` is unrestricted on all Android API levels |

### 5.9 Settings Persistence

```kotlin
// SettingsStore.kt
class SettingsStore(context: Context) {
    var shizukuEnabled: Boolean
        get() = prefs.getBoolean("shizuku_enabled", false)
        set(value) = prefs.edit().putBoolean("shizuku_enabled", value).apply()
}
```

Gated behind a developer toggle (default `false`). When enabled AND Shizuku is
available, the `InjectorRouter` selects `ShizukuInjector` with highest priority.
When disabled or unavailable, the standard IME/clipboard flow is used.

---

## 6. Execution Roadmap

### Phase 0 — Foundation ✅ COMPLETED

**Goal:** Rust compiles for Android ARM64. JNI round-trip works. Model loads. Infrastructure ready.

| # | Task | Status | Deliverable |
|---|---|---|---|
| 0.1 | Set up Rust cross-compilation: `rustup target add aarch64-linux-android`, install `cargo-ndk`, configure `ANDROID_NDK_HOME` | ✅ | `cargo check --target aarch64-linux-android` succeeds |
| 0.2 | Create Android project: Kotlin + Compose, `minSdk 26`, `targetSdk 35`, single Activity | ✅ | Project structure with settings.gradle.kts, build.gradle.kts (root + app) |
| 0.3 | Create `handy-core` Rust crate (cdylib). Integrate into Android project via Gradle `buildRust` + `copyRustLib` tasks | ✅ | `libhandy_core.so` loaded via `System.loadLibrary()` |
| 0.4 | JNI Hello World: `nativeInit()` / `nativeDestroy()` with full 21-function bridge | ✅ | Round-trip: Kotlin → Rust → Kotlin callbacks (6 dispatch helpers) |
| 0.5 | Compile ggml + `transcribe-cpp` for Android NDK | ⏳ Deferred to Sprint 1 | `transcribe_cpp::Model::load_with()` (needs CMake toolchain for ARM64 NEON) |
| 0.6 | Bundle test model: `whisper-tiny-q5_0.gguf` | ⏳ Deferred to Sprint 1 | Model download + batch transcription test |
| 0.7 | `EngineCallback` interface + `GlobalRef` storage in Rust | ✅ | All 6 callbacks fire from Rust → Kotlin |
| 0.8 | `EngineViewModel` as process-wide singleton via `HandyApplication` | ✅ | Single `nativeInit` call, shared between IME and MainActivity |
| 0.9 | `HandyInputMethodService` with 3-mode Compose UI | ✅ | Idle / Dictating / Confirm modes, falling back to clipboard |
| 0.10 | `RecordingService` stub | ✅ | Extends `Service`, placeholder for Foreground Service logic |
| 0.11 | String resources for IME | ✅ | 16 strings in `res/values/strings.xml` |
| 0.12 | ProGuard rules for JNI class preservation | ✅ | `app/proguard-rules.pro` with `-keep` for EngineBridge, EngineCallback |
| 0.13 | Nullable safety pattern in JNI bridge | ✅ | `JObject` + `is_null()` for all `String?` parameters |

**Milestone achieved:** 23 source files, 1,635+ lines of code. Rust compiles clean (`cargo check`),
Gradle project structure valid, JNI bridge functional (stubs ready for real implementation in Sprint 1).

### Sprint 2 — Power-User Shizuku Injection ✅ COMPLETED

**Goal:** Direct text injection via Shizuku (UID 2000) with automatic fallback chain.
Bypass Android 14/15 hidden API restrictions via AIDL IPC bridge.

| # | Task | Status | Deliverable |
|---|---|---|---|
| 2.0 | `InjectorStrategy` interface + 3 implementations (Shizuku, IME, Clipboard) | ✅ | Strategy pattern in `com.handy.app.injection` |
| 2.1 | `InjectorRouter` with automatic strategy selection and cascading fallback | ✅ | Router integrated into `EngineViewModel` |
| 2.2 | `ShizukuInjector`: clipboard copy + `KeyEvent.KEYCODE_PASTE` via `IInputManager.injectInputEvent()` through Shizuku UID 2000 | ✅ | Text injected into active app via paste shortcut |
| 2.3 | `ImeInjector`: wraps `InputConnection.commitText()` inside `withContext(Dispatchers.Main)` | ✅ | Thread-safe IME injection |
| 2.4 | `ClipboardInjector`: clipboard fallback with Toast | ✅ | Last-resort delivery always works |
| 2.5 | `IHandyUserService.aidl` + `HandyUserService` (AIDL IPC running in process `:shizuku`, UID 2000) | ✅ | ServiceManager reflection moved to UID 2000, bypassing API 34+ hidden API restrictions |
| 2.6 | `Shizuku.bindUserService()` async binding with `@Volatile` proxy reference | ✅ | Safe race-condition handling (null check + fallback) |
| 2.7 | Binder death recovery: `onServiceDisconnected` → `userService = null` → `isAvailable()` false → router falls back | ✅ | Graceful recovery from Shizuku process death |
| 2.8 | Manifest security: `android:permission="moe.shizuku.manager.permission.API_V23"` on `HandyUserService` | ✅ | No unauthorized app can bind to the service |
| 2.9 | ProGuard rules for AIDL `$Stub`/`$Proxy` inner classes | ✅ | Release builds protected against R8 obfuscation of IPC deserialization |
| 2.10 | State reset on successful injection: auto-return to `STATE_IDLE` after injection completes | ✅ | No double-insertion via stale ConfirmMode button |
| 2.11 | `SettingsStore.shizukuEnabled` gate (developer toggle, default false) | ✅ | No Shizuku dependency for core dictation flow |

**Milestone achieved:** 6 new files (AIDL interface, 4 injection strategies, router, user service, settings store).
11 modified files (build config, manifest, ProGuard, DI, ViewModel, IME, MainActivity, strings).
Hidden API reflection blockade on API 34+ bypassed via ShizukuUserService AIDL IPC.
Full cascade recovery path: Shizuku → IME → Clipboard, with no data loss.

### Sprint 3 — UI Completa y Gestión de Modelos ✅ COMPLETED

**Goal:** Complete Jetpack Compose UI with Compose Navigation, model manager, settings, history, and onboarding.

| # | Task | Status | Deliverable |
|---|---|---|---|
| 3.0 | Compose Theme (Material3 + Material You dynamic colors) | ✅ | `HandyTheme` with `Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt` — respects system dark/light, API 31+ dynamic colors |
| 3.1 | Compose Navigation scaffold with bottom tabs | ✅ | `Screen.kt` (4 routes sealed class), `AppNavigation.kt` (NavHost + `NavigationBar` + tab state restoration) |
| 3.2 | Data model classes for JSON deserialization from Rust | ✅ | `ModelInfo.kt`, `HistoryEntry.kt`, `AppSettings.kt` — all use `org.json` (no kotlinx.serialization) |
| 3.3 | SettingsStore expansion | ✅ | 5 properties: `shizukuEnabled`, `idleTimeout`, `postProcessEndpoint`, `postProcessApiKey`, `onboardingCompleted` |
| 3.4 | ViewModel layer (5 VMs total) | ✅ | `EngineViewModel` expanded (download SharedFlow, models StateFlow, `refreshModels()`, `applySettings()`), plus `ModelsViewModel`, `SettingsViewModel`, `HistoryViewModel`, `OnboardingViewModel` |
| 3.5 | Manual DI factory | ✅ | `ViewModelFactory.kt` — no Hilt/Koin, constructor injection via `HandyApplication` |
| 3.6 | Model catalog screen | ✅ | LazyColumn + ModelCard with download progress, delete confirmation, OOM warning, active badge |
| 3.7 | Settings screen | ✅ | 4 sections: Audio (timeout dropdown), Text Injection (Shizuku toggle + guard, IME picker), Post-Processing (endpoint/api key), About |
| 3.8 | History screen | ✅ | LazyColumn + auto-pagination via snapshotFlow, expandable cards, star toggle, delete confirmation |
| 3.9 | Onboarding flow | ✅ | 5-step AnimatedContent: Welcome → Mic Permission → IME Setup → Model Download → Ready, with "Skip All" |
| 3.10 | String resources expansion | ✅ | 19 → 82 entries covering all 4 screens + common dialogs |
| 3.11 | MainActivity wiring | ✅ | Placeholders replaced with real screens via ViewModelFactory; CameraRoll navigation: Onboarding → Models tab |

**Milestone achieved:** 21 new .kt files created, 4 existing files modified (EngineViewModel, SettingsStore, MainActivity, strings.xml). Compose Navigation with bottom bar (3 tabs) + onboarding. 5 ViewModels with manual DI. 82 string resources. Full Material 3 theme with dark mode and dynamic colors. 4 UX rules verified: Shizuku AlertDialog guard, InputMethodPicker integration, 100% Rust catalog, simple pagination.

### Sprint 4 — Audio Capture y STT Pipeline ✅ COMPLETED

**Goal:** Real-time microphone capture with VAD and streaming transcription. End-to-end dictation flow from button press to text output.

| # | Task | Status | Deliverable |
|---|---|---|---|
| 4.1 | AAudio capture in Rust via `aaudio-sys`. Shared mode with exclusive fallback. Callback-based float32. | ✅ | `audio/capture.rs` (195 loc) — stream builder, data callback thunk, error callback, cleanup |
| 4.2 | `FrameResampler` (rubato FFT) + `EnergyVad` (adaptive noise floor) + `SmoothedVad` (prefill/onset/hangover) | ✅ | `audio/` module (707 loc total) — resampler → VAD → pipeline orchestrator |
| 4.3 | `StreamRouter` (mpsc channel, zero-cost atomic when inactive) + `StreamWorker` thread (partial ~500ms, final) | ✅ | `transcription/` module (348 loc) — router, worker, engine, post-process |
| 4.4 | Real JNI implementations: `nativeStartRecording`, `nativeFinalizeStream`, `nativeCancelRecording`, `nativeLoadModel`, `nativePushAudio` + all 21 functions | ✅ | `jni_bridge.rs` (695 loc) — full pipeline wired, callbacks attached at `nativeInit` |
| 4.5 | `ModelManager` — catalog (5 models), HTTP download via reqwest+tokio, progress callbacks, cancellation | ✅ | `model/` module (456 loc) — catalog, download, file management |
| 4.6 | `HistoryManager` — SQLite CRUD with schema, indexes, paginated queries | ✅ | `history/manager.rs` (158 loc) — save, get, delete, toggle saved |
| 4.7 | `RecordingService` — Foreground Service with notification, `FOREGROUND_SERVICE_TYPE_MICROPHONE`, AudioRecord fallback | ✅ | `RecordingService.kt` (262 loc) — not started by default; AAudio is sole capture source |
| 4.8 | `DictationScreen` — E2E test screen with start/stop button, VAD level bar, partial/final text, state indicator | ✅ | `DictationScreen.kt` (311 loc) — connected to EngineViewModel StateFlows |
| 4.9 | Navigation — Dictation as first tab, `Screen.Dictation` route, `AppNavigation` dictationContent | ✅ | Dictation tab added as first item in bottom nav |
| 4.10 | Post-processing — `remove_filler_words` (19 fillers, word boundary detection, case-preserving) + `collapse_stutters` (3+ identical words) — always applied | ✅ | `transcription/engine.rs` (243 loc) — filler removal, stutter collapse, cleanup |
| 4.11 | JSON contracts — `ModelInfo` (9 fields) + `HistoryEntry` (6 fields) verified Rust ↔ Kotlin | ✅ | All field names match serialization |
| 4.12 | JNI callbacks — 6 dispatch helpers verified against `EngineCallback` interface | ✅ | `jni_callback.rs` (132 loc) — state, transcription, VAD, error, download progress, download complete |

**Milestone achieved:** 2,648 lines Rust + 904 lines Kotlin added/modified across 18 Rust files and 5 Kotlin files. Build passes clean (`cargo ndk --target aarch64-linux-android --platform 26 -- check` — 0 errors, 0 warnings). Audio pipeline: AAudio → FrameResampler → EnergyVAD + SmoothedVad → audio_buffer + StreamRouter → StreamWorker → JNI callback → EngineViewModel. Post-processing: filler word removal + stutter collapse applied unconditionally.

### Sprint 5 — IME y Text Injection ✅ COMPLETED

**Goal:** Real transcription via transcribe-cpp NDK. Text delivered to any app via IME + notification.

| # | Task | Status | Deliverable |
|---|---|---|---|
| 5.0 | `transcribe-cpp` NDK integration: add crate dep, configure CMAKE_ARGS for ARM64, wire real `Model::load_with()` + `session.stream()` into engine | ✅ | `Cargo.toml` + `build-rust.sh` + `engine.rs` + `worker.rs` — real inference instead of stub |
| 5.1 | Replace stub `StreamWorker` with real whisper streaming: `stream.feed()` → `stream.text().display()` partials, `stream.finalize()` for committed | ✅ | `worker.rs` — partial text every ~500ms, final text on stop |
| 5.2 | JNI_OnLoad: `transcribe_cpp::init_logging()` + `init_backends_default()` for ggml CPU backend | ✅ | `lib.rs` — backends initialised at native lib load |
| 5.3 | IME Polish: `method.xml` subtype metadata, `InputConnection` edge case buffer, Cancel button in DictatingMode | ✅ | `method.xml` + `HandyInputMethodService.kt` — robust IME connection handling |
| 5.4 | IME VAD visual polish: smooth `animateFloatAsState` + color gradient (green/yellow/red) + animated percentage | ✅ | `HandyInputMethodService.kt` — VAD level bar with smooth animation |
| 5.5 | IME ErrorMode: error message via `lastErrorMessage` StateFlow + i18n string resources for generic error + retry hint | ✅ | `EngineViewModel.kt` + `strings.xml` — contextual error display |
| 5.6 | IME Cancel: discard partial text, stop engine, return to IdleMode | ✅ | `cancelRecording()` wired to IME Cancel button |
| 5.7 | ConfirmMode: Insert button calls `engineViewModel.confirmInsert(text)` → injection + state reset. Retry calls `resetPartialText()` → back to IdleMode | ✅ | No auto-inject, no double-paste, no stale ConfirmMode |
| 5.8 | `resetPartialText()` includes `_state = STATE_IDLE` for correct ErrorMode/ConfirmMode Retry | ✅ | `EngineViewModel.kt` — Retry from Error or Confirm correctly returns to IdleMode |
| 5.9 | `clearPartialText()` for `onStartInput` — clears texts without resetting state (preserves active dictation mid-field-switch) | ✅ | `EngineViewModel.kt` — no interruption of active recording |
| 5.10 | Quick Dictate persistent notification: "Dictate" + "Switch Keyboard" actions, ongoing notification from `HandyApplication` | ✅ | `HandyApplication.kt` + `MainActivity.kt` — dictation triggerable from outside the IME |
| 5.11 | RecordingService cleaned: notification-only (no AudioRecord), actions: Stop + Switch Keyboard | ✅ | `RecordingService.kt` — simplified to notification host with WakeLock |
| 5.12 | Settings live sync: debounced (500ms) push of endpoint + API key to Rust engine | ✅ | `SettingsViewModel.kt` — no burst calls on every keystroke |
| 5.13 | Shizuku auto-reconnect: exponential backoff (1s→2s→...→30s) on service disconnect | ✅ | `ShizukuInjector.kt` — automatic recovery from Shizuku process death |
| 5.14 | Shizuku status dot in settings: colored circle (green/orange/red) that updates on recomposition | ✅ | `SettingsScreen.kt` — visual feedback of Shizuku connectivity |
| 5.15 | Test matrix document: 8 categories, 142 test cases for IME + injection | ✅ | `TEST_MATRIX.md` — systematic testing guide |

**Milestone:** User enables Handy as their keyboard, opens any app with a text field, taps the dictation button in the IME, speaks, and the transcribed text (via real transcribe-cpp inference) appears in the text field. The user can confirm/retry/cancel via ConfirmMode. Dictation also triggerable from persistent notification. Shizuku auto-reconnects on service death. Settings sync live with debounce.

### Sprint 6 — Polish, Performance y Testing (Next)

**Goal:** Production-quality stability, performance, and edge case handling.

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 4.1 | Idle model unloading + timer configuration. Verify memory drops after timeout. | Rust | Memory freed within 30s of idle |
| 4.2 | Battery profiling: measure mAh/minute in each state (Idle, Listening, Streaming, Downloading). Optimize hot paths. | All | Battery report with target: < 5% drain per 30 min of dictation |
| 4.3 | Edge cases: screen rotation during recording, incoming call interrupts recording, Bluetooth headset connect/disconnect, app process killed by system. | All | Graceful recovery from all interruptions |
| 4.4 | Performance: measure end-to-end latency (last spoken word → text on screen). Target: < 500ms for streaming partial results. | Rust | Latency measurements with benchmarks |
| 4.5 | Crash reporting: `catch_unwind` around all JNI entry points. Integrate Sentry or Crashlytics. | All | Crashes reported with stack traces |
| 4.6 | Device testing matrix: Pixel 8 (Tensor G3), Galaxy S24 (Snapdragon 8 Gen 3), OnePlus 12, Xiaomi 14, Nothing Phone 2. Android 12, 13, 14, 15. | QA | Test report with pass/fail per device |
| 4.7 | First alpha release: signed APK + AAB, version code, changelog. | Infra | Alpha distributed to testers |

**Milestone:** Alpha release with known device compatibility matrix.

### Sprint 7 — Distribution y Open Source

**Goal:** Public release and community ready.

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 5.1 | GitHub Actions CI: build on push (Rust cross-compile + Android Gradle build). Matrix: debug + release. | Infra | CI green on `main` |
| 5.2 | Signing: release keystore generation, secure storage in GitHub Secrets, automated signing in CI. | Infra | Signed release APK/AAB from CI |
| 5.3 | Documentation: `BUILD.md` for Android, architecture diagram, contribution guide, translation guide. | All | Developer docs complete |
| 5.4 | License audit: all Rust crates, Kotlin dependencies, model files. Confirm Apache 2.0 / MIT compliance. | Infra | `licenses.md` generated |
| 5.5 | GitHub Release: upload APK + AAB, write release notes, tag `v1.0.0-alpha`. | Infra | Release published |
| 5.6 | F-Droid metadata: prepare `com.handy.app.yml` recipe. Submit to F-Droid. | Infra | F-Droid submission started |
| 5.7 | Community: update README, open issues for known limitations, create Discussions category. | All | Repository ready for contributors |

**Milestone:** Public alpha release on GitHub Releases. F-Droid submission in progress.

---

## Appendix A: Project Structure

```
handy-android/
├── app/                                  # Android application module
│   ├── build.gradle.kts
│   ├── proguard-rules.pro                # R8/ProGuard keep rules for JNI classes ✅
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/handy/app/
│       │   ├── HandyApplication.kt       # Process-wide ViewModel singleton + DI container ✅
│       │   ├── MainActivity.kt           # Single Activity, Compose NavHost host ✅
│       │   ├── bridge/
│       │   │   ├── EngineBridge.kt       # JNI external declarations ✅
│       │   │   └── EngineCallback.kt     # Callback interface ✅
│       │   ├── model/                    # Sprint 3
│       │   │   ├── ModelInfo.kt          # Model metadata + JSON parser ✅
│       │   │   ├── HistoryEntry.kt       # History entry + JSON parser + relative date ✅
│       │   │   └── AppSettings.kt        # Settings data class ✅
│       │   ├── di/                       # Sprint 3
│       │   │   └── ViewModelFactory.kt   # Manual DI factory (no Hilt/Koin) ✅
│       │   ├── navigation/               # Sprint 3
│       │   │   ├── Screen.kt             # Sealed class with 4 routes ✅
│       │   │   └── AppNavigation.kt      # NavHost + Bottom Nav + tab state restoration ✅
│       │   ├── ime/
│       │   │   └── HandyInputMethodService.kt  # IME + Compose 3-mode UI ✅
│   │   ├── service/
│   │   │   └── RecordingService.kt   # Foreground Service (full, AudioRecord fallback) ✅
│   │   ├── viewmodel/
│   │   │   ├── EngineViewModel.kt    # Shared state + download events + models + settings ✅
│   │   │   ├── ModelsViewModel.kt    # Model catalog state, download actions ✅
│   │   │   ├── SettingsViewModel.kt  # Settings state ↔ SharedPreferences ↔ Rust ✅
│   │   │   ├── HistoryViewModel.kt   # Paginated history load, delete, toggle saved ✅
│   │   │   └── OnboardingViewModel.kt # 5-step flow, lazy model download ✅
│   │   ├── injection/
│   │   │   ├── InjectorStrategy.kt   # Strategy interface ✅
│   │   │   ├── ShizukuInjector.kt    # UID 2000 key-event injection ✅
│   │   │   ├── ImeInjector.kt        # InputConnection.commitText() ✅
│   │   │   ├── ClipboardInjector.kt  # Clipboard fallback ✅
│   │   │   ├── InjectorRouter.kt     # Strategy selector + cascade ✅
│   │   │   └── HandyUserService.kt   # AIDL IPC service (UID 2000) ✅
│   │   ├── SettingsStore.kt          # SharedPreferences (5 properties) ✅
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   │   ├── Color.kt          # Light/dark M3 palette + YellowStar ✅
│       │   │   │   ├── Type.kt           # HandyTypography ✅
│       │   │   │   ├── Shape.kt          # HandyShapes ✅
│       │   │   │   └── Theme.kt          # HandyTheme + Material You dynamic colors ✅
│       │   │   ├── models/
│       │   │   │   └── ModelCatalogScreen.kt  # LazyColumn + ModelCard + download/delete ✅
│       │   │   ├── settings/
│       │   │   │   └── SettingsScreen.kt # 4 sections with dropdowns/textfields/toggles ✅
│       │   │   ├── history/
│       │   │   │   └── HistoryScreen.kt  # LazyColumn + auto-pagination + cards ✅
│       │   │   ├── onboarding/
│       │   │   │   └── OnboardingScreen.kt # 5-step AnimatedContent flow ✅
│       │   │   └── dictation/
│       │   │       └── DictationScreen.kt # E2E dictation test screen ✅ Sprint 4
│   ├── aidl/com/handy/app/injection/
│   │   └── IHandyUserService.aidl    # AIDL interface for UID 2000 IPC ✅
│       ├── res/
│       │   ├── values/
│       │   │   ├── strings.xml          # 121 IME + app + Sprint 3–5 strings ✅
│       │   │   ├── themes.xml           # Material3 NoActionBar ✅
│       │   │   └── colors.xml
│       │   └── xml/
│       │       └── method.xml           # IME metadata (subtype en_US + voice mode) ✅ Sprint 5
│   ├── jniLibs/                          # cargo-ndk output target
│       │   └── arm64-v8a/
│       │       └── libhandy_core.so      # Built by buildRust Gradle task
│       └── assets/
│           └── models/                   # Reserved for future bundled test model
│
├── handy-core/                           # Rust library (cdylib)
│   ├── Cargo.toml                        # ✅ jni, log, serde, aaudio-sys, rubato, rusqlite, reqwest, tokio, hound, chrono, uuid, transcribe-cpp 0.1.3
│   ├── build.rs                          # Links OpenSLES on Android
│   ├── Cargo.lock
│   └── src/                              # 3,125 lines total (18 files) ✅ Sprint 5
│       ├── lib.rs                        # JNI_OnLoad, JavaVM storage ✅
│       ├── engine.rs                     # EngineState (77 loc) — 4 managers ✅ Sprint 4
│       ├── jni_bridge.rs                 # 21 JNI functions (695 loc) — ALL REAL ✅ Sprint 4
│       ├── jni_callback.rs               # 6 dispatch helpers (132 loc) ✅
│       ├── audio/                        # ✅ Sprint 4 (711 loc)
│       │   ├── mod.rs                    # Module declarations
│       │   ├── capture.rs                # AAudio wrapper via aaudio-sys FFI
│       │   ├── resampler.rs              # FrameResampler via rubato FftFixedIn
│       │   ├── vad.rs                    # EnergyVad + SmoothedVad state machine
│       │   └── pipeline.rs               # AudioPipeline orchestrator
│       ├── transcription/                # ✅ Sprint 4 (398 loc)
│       │   ├── mod.rs                    # Module declarations
│       │   ├── engine.rs                 # TranscriptionEngine + post_process()
│       │   ├── router.rs                 # StreamRouter (mpsc, atomic zero-cost)
│       │   └── worker.rs                 # StreamWorker (background thread)
│       ├── model/                        # ✅ Sprint 4 (456 loc)
│       │   ├── mod.rs                    # Module declarations
│       │   ├── info.rs                   # ModelInfo + catalog (5 models)
│       │   └── manager.rs                # HTTP download, file mgmt, active model
│       └── history/                      # ✅ Sprint 4 (159 loc)
│           ├── mod.rs                    # Module declarations
│           └── manager.rs                # SQLite CRUD with schema + indexes
│
├── scripts/
│   └── build-rust.sh                     # cargo ndk invocation for arm64 + x86_64 ✅
├── build.gradle.kts                      # Root build file ✅
├── settings.gradle.kts                   # ✅
├── gradle.properties                     # ✅
├── gradle/
│   ├── wrapper/
│   │   └── gradle-wrapper.properties     # ✅
│   └── libs.versions.toml                # TODO (dependencies hardcoded in build.gradle.kts)
├── ARCHITECTURE.md                       # This document
└── README.md                             # TODO
```

## Appendix B: Key Constraints and Non-Negotiables

1. **Offline first.** No internet required for dictation. Model downloads and LLM post-processing are the only network operations and must fail gracefully (no blocking the app).
2. **`transcribe-cpp` with GGUF is the primary engine.** ONNX-based models (Parakeet, Moonshine) via `transcribe-rs` are secondary and may be deferred to post-MVP.
3. **Model must live in internal storage** (`context.filesDir/models/`), never on external/shared storage (security + performance).
4. **No WebView.** All UI is Compose-native. The React frontend is fully discarded.
5. **No root required.** The app works on stock Android. Shizuku/ADB integration for advanced text injection is optional and gated behind a developer setting, not required for core functionality.
6. **Single Activity architecture.** One `MainActivity` hosts all screens via Compose Navigation. The `RecordingService` and `HandyInputMethodService` are independent components.
7. **All user-facing strings in `strings.xml`** (Kotlin side) or i18n JSON (Rust side if any UI strings originate there). Prepared for i18n from day one.
8. **ProGuard rules mandatory for release.** `-keep` rules for `EngineBridge` (all native methods), `EngineCallback` (all methods), and all `EngineCallback` implementors must be present in `proguard-rules.pro`. Without them, R8 obfuscation breaks JNI method dispatch at runtime.
9. **Engine is process-wide singleton.** `nativeInit` called exactly once from `HandyApplication`. `nativeDestroy` never called on IME destruction — reserved for process teardown. Multiple `nativeInit` calls are prevented by guard flag.
