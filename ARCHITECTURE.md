# Handy for Android — Master Technical Specification

**Status:** Definitive  
**Version:** 1.0.0  
**Target:** Android 8.0+ (API 26), `targetSdk 35`  
**Architecture:** `aarch64-linux-android` (arm64-v8a) mandatory; `x86_64-linux-android` for emulator only

---

## 1. Executive Summary and Scope

### 1.1 What This Is

Handy for Android is an **offline, on-device speech-to-text dictation engine**. It captures microphone audio, processes it through a local Whisper-family model (GGUF format via `transcribe-cpp`), and injects the transcribed text into the currently active application — without requiring an internet connection.

### 1.2 Core Dependencies

| Dependency | Version | Purpose | License |
|---|---|---|---|
| `transcribe-cpp` | ≥ 0.1.3 | GGUF/Whisper inference engine | MIT |
| `rubato` | ≥ 0.16 | Audio resampling (device rate → 16 kHz) | MIT |
| `vad-rs` | git (Silero V4) | Voice Activity Detection (ONNX) | MIT |
| `rusqlite` | ≥ 0.37 | History persistence (bundled SQLite) | MIT |
| `reqwest` | ≥ 0.12 | Model download + LLM post-processing HTTP | MIT/Apache 2.0 |
| `hound` | ≥ 3.5 | WAV file I/O for recording archival | MIT |
| Jetpack Compose | BOM 2025.x | Declarative native UI | Apache 2.0 |
| AAudio | Android NDK API | Low-latency audio capture | (Android SDK) |
| ONNX Runtime | ≥ 1.19 (optional) | Alternative inference backend | MIT |

### 1.3 What We Preserve from Desktop Handy

The following modules are ported with **zero or minimal code changes** from the original `src-tauri/src/` Rust codebase:

- `managers/transcription.rs` — Model loading, batch/streaming inference orchestration
- `managers/model.rs` — Model catalog, download, SHA-256 verification, discovery
- `managers/history.rs` — SQLite history with schema migrations
- `audio_toolkit/audio/resampler.rs` — `FrameResampler` via `rubato::FftFixedIn`
- `audio_toolkit/vad/silero.rs` + `smoothed.rs` — Silero VAD + onset/hangover state machine
- `actions.rs` — Record→Transcribe→Post-process pipeline logic (extracted from Tauri glue)

### 1.4 What Is Entirely Discarded

- Tauri framework (`lib.rs`, `main.rs`, `commands/`, all plugins)
- Entire React/TypeScript frontend (`src/`)
- `cpal` (replaced by AAudio/AudioRecord)
- `enigo` (replaced by IME `InputConnection.commitText()`)
- `rdev` (replaced by Foreground Service notification actions)
- `rodio` (replaced by Android AudioTrack)
- `tauri-plugin-store` (replaced by Android DataStore)

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
USER ACTION: Tap dictation button in IME / Tap notification action / Press volume key shortcut
    │
    ▼
[1] Kotlin: EngineViewModel.startRecording()
    │
    ▼
[2] JNI: Java_com_handy_bridge_EngineBridge_nativeStartRecording(env, obj)
    │
    ▼
[3] Rust: AudioEngine::start_capture()
    │   Opens AAudio stream: 16kHz, mono, Float32, low-latency
    │   Allocates DirectByteBuffer ring buffer (4 × 4096 bytes)
    │
    ▼
[4] AAudio Callback Thread (real-time priority, runs every ~10ms)
    │
    ├──[4a] AAudio fills DirectByteBuffer with PCM float32 samples
    │
    ├──[4b] FrameResampler::process(&input) → 30ms frames (480 samples @ 16kHz)
    │        (no-op if device native rate == 16kHz)
    │
    ├──[4c] SileroVad::predict(frame) → probability ∈ [0, 1]
    │       SmoothedVad state machine:
    │         Prefill:  15 frames buffered before any speech detection
    │         Onset:    2 consecutive frames > 0.3 threshold → speech start
    │         Hangover: 55 frames tail after last speech → speech end
    │
    ├──[4d] If VAD state == Speech:
    │         → Append frame to accumulated_samples: Vec<f32>
    │         → Send frame to StreamRouter::tx (mpsc::Sender)
    │
    └──[4e] JNI callback (every ~100ms):
              → Kotlin: callback.onVadLevel(probability)
              → Kotlin: callback.onStateChange(Listening)
    │
    ▼
[5] Streaming Inference (parallel, on rayon thread pool)
    │
    │   StreamWorker receives frames from StreamRouter::rx
    │
    ├──[5a] transcribe_cpp::Session::stream(
    │           samples: &[f32],
    │           options: StreamOptions { commit_policy: Auto }
    │       ) → StreamResult { committed_text, tentative_text }
    │
    ├──[5b] JNI callback (on committed/tentative change):
    │         → Kotlin: callback.onTranscription(
    │               text = committed + tentative,
    │               isPartial = true
    │           )
    │         → IME display updates live
    │
    └──[5c] If VAD hangover expires → Finalize stream
    │
    ▼
[6] USER ACTION: Release dictation button / VAD silence timeout
    │
    ▼
[7] Kotlin: EngineViewModel.stopRecording()
    │
    ▼
[8] JNI: nativeFinalizeStream(env, obj)
    │
    ▼
[9] Rust: StreamRouter::finalize()
    │   Flushes remaining frames → gets final text
    │
    ├──[9a] Post-processing (optional, configurable):
    │         OpenCC: Chinese variant conversion
    │         Custom words: fuzzy string matching correction
    │         LLM polish: HTTP POST to configurable OpenAI-compatible endpoint
    │
    └──[9b] JNI callback:
              → Kotlin: callback.onTranscription(finalText, isPartial = false)
    │
    ▼
[10] Kotlin: IME text injection
    │
    │   HandyInputMethodService:
    │     currentInputConnection?.commitText(finalText, 1)
    │     currentInputConnection?.finishComposingText()
    │
    │   Fallback (IME not active):
    │     clipboardManager.setPrimaryClip(ClipData.newPlainText("", finalText))
    │     Show notification: "Text copied — switch to Handy IME to paste"
    │
    ▼
[11] Kotlin: Save to history
    │
    │   JNI: nativeSaveHistory(finalText, wavPath)
    │   Rust: HistoryManager::save_entry(...) → SQLite
    │   Optional: Save WAV file to app internal storage
    │
    ▼
[12] Engine returns to Idle state
    │
    ▼
[13] Idle Timer: 30 seconds after last transcription
        → Rust: ModelManager::unload_model() → frees whisper model memory
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
│   ├── jni_bridge.rs             # All #[no_mangle] JNI function implementations
│   └── jni_callback.rs           # JNIEnv::call_method helpers for Rust → Kotlin
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
// handy-core/src/jni_bridge.rs
// All functions follow JNI naming convention:
// Java_com_handy_app_bridge_EngineBridge_<methodName>

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, GlobalRef};
use jni::sys::{jboolean, jint, jlong, jfloat, jstring};

// ── Lifecycle ──────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeInit(
    mut env: JNIEnv,
    _class: JClass,
    model_dir: JString,
    config_dir: JString,
    callback: JObject,
) {
    // 1. Extract paths from JString → Rust String
    // 2. Create GlobalRef to callback object
    // 3. Initialize Engine singleton with paths + callback
    // 4. Spawn idle watcher thread
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDestroy(
    mut env: JNIEnv,
    _class: JClass,
) {
    // 1. Cancel any in-progress recording/transcription
    // 2. Unload model
    // 3. Stop idle watcher thread
    // 4. Delete GlobalRef to callback
    // 5. Drop Engine singleton
}

// ── Recording ──────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeStartRecording(
    mut env: JNIEnv,
    _class: JClass,
    sample_rate: jint,
    channel_count: jint,
) {
    // 1. Configure FrameResampler: input_rate=sample_rate, output_rate=16000
    // 2. Open AAudio stream: 16kHz mono float32 low-latency
    // 3. Spawn audio capture thread:
    //    - AAudio callback → DirectByteBuffer
    //    - Resample → VAD → StreamRouter
    // 4. Send onStateChange(Listening)
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeFinalizeStream(
    mut env: JNIEnv,
    _class: JClass,
) {
    // 1. Stop AAudio stream
    // 2. Flush remaining VAD frames to StreamRouter
    // 3. StreamRouter::finalize() → blocks until final text received
    // 4. Post-processing (OpenCC, custom words, LLM)
    // 5. Send onStateChange(Idle)
    // 6. Send onTranscription(finalText, isPartial=false)
    // 7. Reset idle timer
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeCancelRecording(
    mut env: JNIEnv,
    _class: JClass,
) {
    // 1. Stop AAudio stream
    // 2. StreamRouter::cancel() → discards partial transcription
    // 3. Send onStateChange(Idle)
}

// ── Model ──────────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeLoadModel(
    mut env: JNIEnv,
    _class: JClass,
) {
    // 1. Read active model ID from settings
    // 2. ModelManager::load_model_with_device(...) or TranscriptionManager equivalent
    // 3. If engine_type == TranscribeCpp:
    //      transcribe_cpp::Model::load_with(gguf_path, Backend::Cpu)
    // 4. Send onStateChange(Idle) on success, onError on failure
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeUnloadModel(
    mut env: JNIEnv,
    _class: JClass,
) {
    // Drop LoadedEngine → frees model memory
}

// ── Callback Dispatch Helpers (internal) ───────────────────────

/// Sends a state change to the Kotlin callback object.
/// Must be called from a thread attached to the JVM.
fn dispatch_state_change(env: &mut JNIEnv, callback: &GlobalRef, state: i32) {
    // env.call_method(callback, "onStateChange", "(I)V", &[JValue::Int(state)])
}

/// Sends a transcription result (partial or final) to the Kotlin callback.
fn dispatch_transcription(env: &mut JNIEnv, callback: &GlobalRef, text: &str, is_partial: bool) {
    // let jtext = env.new_string(text)?;
    // env.call_method(callback, "onTranscription",
    //     "(Ljava/lang/String;Z)V",
    //     &[JValue::Object(&jtext), JValue::Bool(is_partial as u8)])
}
```

### 3.5 Threading Contract

| Thread | Owner | Purpose | JVM Attached |
|---|---|---|---|
| `main` | Android | UI thread, Compose recomposition | Yes (always) |
| `audio_callback` | AAudio | Real-time audio capture, resampling, VAD | Yes (attached at stream start) |
| `inference_worker` | Rust (rayon pool) | `transcribe_cpp::Session::stream()` | Yes (attached at pool init) |
| `idle_watcher` | Rust | Monitors last-transcription time, triggers unload | Yes (attached at spawn) |
| `download_worker` | Rust (tokio/reqwest) | Model download via HTTPS/HF Hub | Yes (attached at spawn) |

**Rule:** Any Rust thread that must invoke JNI callbacks MUST call `JavaVM::attach_current_thread()` at thread start (or `attach_current_thread_as_daemon()`). The `JavaVM` pointer is obtained during `JNI_OnLoad` and stored in a `OnceLock<JavaVM>`.

### 3.6 Audio Buffer Zero-Copy Contract

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

### 4.1 Audio Pipeline Specification

```
Device Microphone (native rate, mono)
    │
    ▼
AAudio MMAP stream (exclusive mode, low-latency)
    │ Callback invoked every ~10ms (adjustable via setFramesPerDataCallback)
    │ Each callback delivers N frames of float32 PCM
    ▼
DirectByteBuffer (zero-copy handoff to Rust)
    │
    ▼
FrameResampler (rubato::FftFixedIn<f32>)
    │ Input:  device_rate Hz, mono, 30ms chunking
    │ Output: 16000 Hz, mono, 30ms frames (480 samples/frame)
    │ Config:  resample_ratio = 16000.0 / device_rate,
    │          chunk_size = (device_rate * 30 / 1000) input samples,
    │          sub_chunks = 1, num_threads = 1
    ▼
SileroVad (vad-rs, Silero V4 ONNX model)
    │ Input:  480 sample frame @ 16kHz
    │ Output: probability ∈ [0.0, 1.0]
    │ Threshold: 0.3
    ▼
SmoothedVad (state machine)
    │ States: Silence → Prefill → Onset → Speech → Hangover → Silence
    │ Prefill:  buffer first 15 frames (450ms) before any decision
    │ Onset:    require 2 consecutive frames > 0.3 to enter Speech
    │ Hangover: remain in Speech for 55 frames (~1.65s) after last detection
    │           (streaming mode; batch mode uses 15 frames / 450ms)
    ▼
Speech Frame Accumulator (Vec<f32>)
    │ Appends all frames from Speech state
    │ Shared between batch and streaming paths
    ├──→ StreamRouter::tx (mpsc::Sender<StreamCmd>)
    │      StreamCmd::Feed(Vec<f32>)  → streaming inference worker
    │      StreamCmd::Finalize(tx)     → signal end of stream
    │      StreamCmd::Cancel           → abort
    └──→ [on finalize] → complete Vec<f32> for batch fallback
```

### 4.2 AAudio Stream Configuration

```rust
// Rust-side AAudio configuration (via aaudio-sys FFI or NDK bindings)
struct AaudioStreamConfig {
    direction:       AAUDIO_DIRECTION_INPUT,
    sharing_mode:    AAUDIO_SHARING_MODE_EXCLUSIVE,  // low latency, fallback to SHARED
    performance_mode: AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
    sample_rate:     16000,    // requested; actual may differ → resampler adapts
    channel_count:   1,        // mono
    format:          AAUDIO_FORMAT_PCM_FLOAT,        // float32, native for ML
    frames_per_data_callback: 480,  // 30ms at 16kHz
    usage:           AAUDIO_USAGE_VOICE_COMMUNICATION, // acoustic echo cancellation
    input_preset:    AAUDIO_INPUT_PRESET_VOICE_RECOGNITION, // noise suppression
}
```

**Fallback path:** If AAudio exclusive mode fails (device doesn't support it or stream can't open), retry with `AAUDIO_SHARING_MODE_SHARED`. If AAudio itself fails (API < 26), fall back to JVM-side `AudioRecord` with same config, passing frames through JNI `nativePushAudio`.

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

```kotlin
class HandyInputMethodService : InputMethodService() {

    private lateinit var composeView: View
    private val engineViewModel: EngineViewModel by inject()

    // ── Lifecycle ──────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        // Initialize Compose view for the IME window
        // Connect to EngineViewModel (shared with MainActivity)
    }

    override fun onCreateInputView(): View {
        // Return the Compose view hosting:
        // ┌──────────────────────────────────────┐
        // │  "Di algo..."                        │
        // │  (live transcription text appears    │
        // │   here as user speaks)               │
        // │                                      │
        // │  [🎤 Mantén para dictar]  [⌨️ ABC]   │
        // └──────────────────────────────────────┘
        return composeView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // A new text field is focused. Reset any partial transcription state.
        engineViewModel.resetPartialText()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // Text field lost focus. If there's uncommitted text,
        // commit it before leaving.
        engineViewModel.finalText.value?.let { text ->
            currentInputConnection?.commitText(text, 1)
            currentInputConnection?.finishComposingText()
        }
    }

    // ── Text Commitment ────────────────────────────────────────

    fun commitTranscription(text: String) {
        val ic = currentInputConnection ?: run {
            // Fallback: IME not connected to an input field
            fallbackToClipboard(text)
            return
        }
        // Use COMMIT_TEXT flag (1) so the text is committed and
        // the cursor is placed after it, not composing.
        ic.commitText(text, 1)
        ic.finishComposingText()
    }

    private fun fallbackToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Handy Dictation", text))
        // Show a toast or notification informing the user
        Toast.makeText(this, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    // ── IME Switch ─────────────────────────────────────────────

    fun switchToPreviousKeyboard() {
        // Returns the user to their normal keyboard (Gboard, etc.)
        switchToPreviousInputMethod()
    }

    // ── Dictation Trigger ──────────────────────────────────────

    fun startDictation() {
        // Ensure recording permission is granted (RECORD_AUDIO)
        // If IME is connected to a text field, Start RecordingService
        engineViewModel.startRecording()
    }

    fun stopDictation() {
        engineViewModel.stopRecording()
    }
}
```

### 5.3 IME Visual Contract

The IME Compose view has exactly two "modes":

**Mode A — Idle (no dictation active):**
- Large dictation microphone button (filled, prominent color).
- Small keyboard switch button (icon-only, bottom-right).
- No text preview area.

**Mode B — Dictating:**
- Animated audio level meter (vertical bar reacting to `vadLevel`).
- Live transcription text area (scrollable, monospace, showing `partialText`).
- The dictation button changes to "Stop" / filled red.
- Keyboard switch button hidden (to avoid accidental IME switch mid-dictation).

**Mode C — Post-dictation confirmation (transient, optional):**
- Shows the final transcribed text in an editable text field.
- Two action buttons: "Insert" (commits text) and "Retry" (discards and restarts).
- The "Insert" button auto-commits after 3 seconds if the user doesn't interact.
- Keyboard switch button visible.

### 5.4 IME ↔ Engine Integration

```
HandyInputMethodService (IME Process)
    │
    ├── Observes EngineViewModel.state (SharedFlow)
    │     Idle → Show Mode A
    │     Listening → Show Mode B
    │     Transcribing → Show Mode B (text appearing)
    │
    ├── Observes EngineViewModel.partialText (StateFlow<String>)
    │     Updates Compose TextField in Mode B / C
    │
    ├── Observes EngineViewModel.finalText (StateFlow<String>)
    │     Triggers commitTranscription(text) → InputConnection.commitText()
    │
    └── Calls EngineViewModel.startRecording() / stopRecording()
          → JNI → Rust Engine
```

### 5.5 Notification-Based Dictation Trigger

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

---

## 6. Execution Roadmap

### Phase 0 — Foundation (Weeks 1-2)

**Goal:** Rust compiles for Android ARM64. JNI round-trip works. Model loads.

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 0.1 | Set up Rust cross-compilation: `rustup target add aarch64-linux-android`, install `cargo-ndk`, configure `ANDROID_NDK_HOME` | Infra | `cargo ndk --target aarch64-linux-android --platform 26 build` succeeds |
| 0.2 | Create Android project: Kotlin + Compose, `minSdk 26`, `targetSdk 35`, single Activity | Android | Empty app launches on device/emulator |
| 0.3 | Create `handy-core` Rust crate (cdylib). Integrate into Android project via `build.gradle.kts` task that runs `cargo ndk` and copies `.so` to `jniLibs/arm64-v8a/` | Infra | `libhandy_core.so` loaded via `System.loadLibrary()` |
| 0.4 | JNI Hello World: `nativeInit()` / `nativeDestroy()` with `#[no_mangle]` functions. Verify `jni` crate works. | Rust | Round-trip: Kotlin calls `nativeInit()` → Rust logs → Kotlin callback fires |
| 0.5 | Compile ggml + `transcribe-cpp` for Android NDK. Create `CMakeLists.txt` toolchain file. Resolve NEON intrinsics. | Rust | `transcribe_cpp::Model::load_with()` succeeds on device |
| 0.6 | Bundle test model: `whisper-tiny-q5_0.gguf` (~75 MB) in `app/src/main/assets/`. Copy to internal storage on first launch. | All | Model loads in Rust, batch transcription of a test WAV returns text |
| 0.7 | Set up `EngineCallback` interface + `GlobalRef` storage in Rust. Verify `onTranscription()` and `onError()` callbacks fire. | All | Callback from Rust thread back to Kotlin works |

**Milestone:** APK installable on a physical ARM64 device. On app launch, the Rust engine initializes, downloads (or extracts) the test model, and transcribes a bundled WAV file, showing the result in a Compose `Text` composable.

### Phase 1 — Audio Capture and STT Pipeline (Weeks 3-5)

**Goal:** Real-time microphone capture with VAD and streaming transcription.

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 1.1 | Implement AAudio capture in Rust via `aaudio-sys`. Handle `AAUDIO_SHARING_MODE_EXCLUSIVE` with shared mode fallback. | Rust | AAudio callback delivers PCM float32 frames to Rust |
| 1.2 | Port `FrameResampler` + `SileroVad` + `SmoothedVad` from original codebase. Connect to AAudio output. | Rust | Audio frames flow: AAudio → Resampler → VAD → Vec<f32> |
| 1.3 | Implement `StreamRouter` (mpsc channel) and streaming inference worker thread. | Rust | Partial transcription callbacks fire during recording |
| 1.4 | Implement `nativeStartRecording()` / `nativeFinalizeStream()` / `nativeCancelRecording()` JNI functions. | Rust | Full record→transcribe cycle works from Kotlin |
| 1.5 | Create `RecordingService` (Foreground Service with `FOREGROUND_SERVICE_TYPE_MICROPHONE`). Start/stop recording lifecycle. | Android | Mic recording works while app is in background |
| 1.6 | Create `EngineViewModel` with `StateFlow` properties. Connect JNI callbacks to ViewModel state. | Android | UI reacts to engine state reactively |
| 1.7 | Build minimal dictation test screen: one "Start/Stop" button + one "Transcription Output" text field. | Android | Tap button → speak → see live partial text → tap stop → see final text |

**Milestone:** Minimal dictation works end-to-end: tap a button, speak into the microphone, see live transcription appearing character by character, tap stop, see the final text.

### Phase 2 — IME and Text Injection (Weeks 6-7)

**Goal:** Text delivered to any app via IME.

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 2.1 | Implement `HandyInputMethodService` skeleton. Register in `AndroidManifest.xml` with `<service android:permission="android.permission.BIND_INPUT_METHOD">`. | Android | IME appears in system keyboard list |
| 2.2 | Build IME Compose UI: dictation button, live text preview, keyboard switch button. Three modes (Idle/Dictating/Confirmation). | Android | IME has functional UI |
| 2.3 | Connect IME to `EngineViewModel`. IME triggers `startRecording()` / `stopRecording()`. IME observes `partialText` and `finalText`. | Android | Dictation from IME works within Handy app |
| 2.4 | Implement `commitTranscription()` via `InputConnection.commitText(text, 1)`. Test in Google Keep, WhatsApp, Chrome. | Android | Text appears in target app's text field |
| 2.5 | Implement clipboard fallback with Toast notification when IME is not the active keyboard. | Android | Fallback works when Handy is not the active IME |
| 2.6 | IME onboarding flow: `Settings.ACTION_INPUT_METHOD_SETTINGS` intent, step-by-step enable guide. | Android | User can enable Handy IME from onboarding |
| 2.7 | `Foreground Service` notification actions: "Start Dictation", "Switch Keyboard". | Android | Dictation triggerable from notification |

**Milestone:** User enables Handy as their keyboard, opens any app with a text field, taps the dictation button in the IME, speaks, and the transcribed text appears in the text field. The user can switch back to their normal keyboard with one tap.

### Phase 3 — Full App UI (Weeks 8-10)

**Goal:** Complete settings, model management, history, and onboarding.

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 3.1 | Model catalog screen: list available models, download with progress bar, delete, set active. All backed by JNI → `ModelManager`. | All | User can browse, download, switch models |
| 3.2 | Settings screen: idle timeout, post-processing endpoint/API key, VAD sensitivity, audio output device. Persist via Android DataStore (Kotlin) + sync to Rust settings. | All | Settings persisted and applied |
| 3.3 | History screen: paginated list, search, copy to clipboard, delete, retry transcription. Backed by JNI → `HistoryManager`. | All | User can browse past transcriptions |
| 3.4 | Onboarding flow: welcome → model selection → permissions (microphone) → IME enable guide → ready. | Android | First-run experience complete |
| 3.5 | Dark/light theme support. Reuse i18n JSON files from original codebase (adapt keys where needed). | Android | App respects system theme, supports Spanish + English initially |
| 3.6 | Post-processing configuration UI: LLM endpoint, custom words list, Chinese OpenCC toggle. | Android + Rust | Post-processing configurable from UI |
| 3.7 | Error handling UX: model fails to load, microphone permission denied, download failed. Dialogs and retry flows. | All | Every error path has user-facing recovery |

**Milestone:** Feature-complete app with polished UI equivalent to the desktop version's essential functionality.

### Phase 4 — Polish, Performance, and Testing (Weeks 11-12)

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

### Phase 5 — Distribution and Open Source (Weeks 13-14)

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
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/handy/app/
│       │   ├── HandyApplication.kt       # Application subclass, DI init
│       │   ├── MainActivity.kt           # Single Activity, Compose host
│       │   ├── bridge/
│       │   │   ├── EngineBridge.kt       # JNI external declarations
│       │   │   └── EngineCallback.kt     # Callback interface
│       │   ├── ime/
│       │   │   └── HandyInputMethodService.kt
│       │   ├── service/
│       │   │   └── RecordingService.kt   # Foreground Service
│       │   ├── viewmodel/
│       │   │   └── EngineViewModel.kt    # Shared state
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   ├── settings/
│       │   │   ├── models/
│       │   │   ├── history/
│       │   │   └── onboarding/
│       │   └── di/
│       │       └── AppModule.kt          # Koin/Hilt DI module
│       ├── res/
│       └── assets/
│           └── models/                   # Bundled model files (tiny for demo)
│
├── handy-core/                           # Rust library (cdylib)
│   ├── Cargo.toml
│   ├── build.rs                          # CMake integration for ggml
│   └── src/
│       ├── lib.rs                        # JNI_OnLoad, crate root
│       ├── jni_bridge.rs                 # All JNI function implementations
│       ├── jni_callback.rs               # Callback dispatch helpers
│       ├── engine.rs                     # Top-level orchestrator
│       ├── audio/
│       │   ├── mod.rs
│       │   ├── capture.rs                # AAudio wrapper
│       │   ├── resampler.rs              # Port from audio_toolkit
│       │   └── vad.rs                    # Port from audio_toolkit
│       ├── stt/
│       │   ├── mod.rs
│       │   ├── manager.rs                # Port from managers/transcription.rs
│       │   └── stream.rs                 # StreamRouter + StreamWorker
│       ├── model/
│       │   ├── mod.rs
│       │   ├── manager.rs                # Port from managers/model.rs
│       │   ├── catalog.rs                # Port from catalog/
│       │   └── gguf_meta.rs              # Port from managers/gguf_meta.rs
│       ├── history/
│       │   └── manager.rs                # Port from managers/history.rs
│       ├── postproc.rs                   # Port from actions.rs post-processing
│       ├── settings.rs                   # Adapted from settings.rs
│       └── util.rs                       # Platform helpers
│
├── scripts/
│   └── build-rust.sh                     # cargo ndk invocation
├── build.gradle.kts                      # Root build file
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml                # Version catalog
├── ARCHITECTURE.md                       # This document
└── README.md
```

## Appendix B: Key Constraints and Non-Negotiables

1. **Offline first.** No internet required for dictation. Model downloads and LLM post-processing are the only network operations and must fail gracefully (no blocking the app).
2. **`transcribe-cpp` with GGUF is the primary engine.** ONNX-based models (Parakeet, Moonshine) via `transcribe-rs` are secondary and may be deferred to post-MVP.
3. **Model must live in internal storage** (`context.filesDir/models/`), never on external/shared storage (security + performance).
4. **No WebView.** All UI is Compose-native. The React frontend is fully discarded.
5. **No root required.** The app works on stock Android. Shizuku/ADB integration for advanced text injection is optional and gated behind a developer setting, not required for core functionality.
6. **Single Activity architecture.** One `MainActivity` hosts all screens via Compose Navigation. The `RecordingService` and `HandyInputMethodService` are independent components.
7. **All user-facing strings in `strings.xml`** (Kotlin side) or i18n JSON (Rust side if any UI strings originate there). Prepared for i18n from day one.
