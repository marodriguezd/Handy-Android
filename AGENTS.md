# AGENTS.md

This file provides guidance to AI coding assistants working with code in this repository.

## Project Structure

This repo contains TWO separate projects:

1. **Handy Desktop** (`src/`, `src-tauri/`) — Cross-platform desktop app (Tauri 2.x + React)
2. **Handy Android** (`handy-android/`) — Android port of Handy (Rust JNI + Kotlin/Jetpack Compose)

## Android Development Commands

**Prerequisites:**

- [Rust](https://rustup.rs/) (latest stable)
- [Android NDK](https://developer.android.com/ndk/) r26+ (via Android Studio SDK Manager)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`
- Rust Android target: `rustup target add aarch64-linux-android`
- Java 17+ JDK
- Android SDK (compileSdk 35)
- A device connected via ADB

**Quick Build & Install:**

```bash
# Set NDK path (required)
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version>

# 1. Build Rust native library (ARM64)
cd handy-android/handy-core
cargo ndk --target aarch64-linux-android --platform 26 build --release

# 2. Copy .so to jniLibs
cp target/aarch64-linux-android/release/libhandy_core.so ../app/src/main/jniLibs/arm64-v8a/

# 3. Build debug APK
cd ..
./gradlew assembleDebug

# 4. Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5. Clear app data (for clean test)
adb shell pm clear com.handy.app.debug
adb logcat -c
adb shell am start -n com.handy.app.debug/com.handy.app.MainActivity
```

**Logcat monitoring:**

```bash
# Watch all Handy-related logs in real-time
adb logcat | grep -E '(handy-core|HandyApp|EngineVM|HandyRecording)'

# Or just errors
adb logcat | grep -E '(ERROR|FATAL|Exception|handy-core)'
```

## Android Architecture Overview

The Android port consists of:

### Rust Core (`handy-android/handy-core/`)
- `cdylib` exposing 21 JNI functions via `libhandy_core.so`
- `jni_bridge.rs` — All `#[no_mangle]` JNI implementations
- `audio/` — AAudio capture, FrameResampler (rubato), EnergyVAD
- `transcription/` — Batch inference via `transcribe_cpp::session.run()` (NOT streaming)
- `model/` — Model catalog, download (HTTP via reqwest, GGUF from handy-computer)
- `engine.rs` — EngineState singleton with ENGINE OnceLock<Mutex<Option<EngineState>>>

### Kotlin App (`handy-android/app/`)
- `HandyApplication.kt` — Process-wide singleton for EngineViewModel
- `MainActivity.kt` — Compose navigation (Dictation, Models, Settings, History)
- `viewmodel/EngineViewModel.kt` — State management for recording/transcription
- `bridge/EngineBridge.kt` — JNI external fun declarations
- `bridge/EngineCallback.kt` — Callback interface (Rust → Kotlin)
- `ime/HandyInputMethodService.kt` — IME keyboard with dictation UI
- `injection/` — Strategy pattern for text injection (Shizuku, IME, Clipboard)
- `service/RecordingService.kt` — Foreground service for persistent recording
- `ui/dictation/DictationScreen.kt` — Dictation test screen

## Current State (Checkpoint — July 15, 2026)

### ✅ Working — Functional State
- Audio capture via AAudio with `DIRECTION_INPUT=1` (critical bugfix: aaudio-sys v0.1.0 had `DIRECTION_INPUT=0`, same as OUTPUT)
- Voice Recognition input preset (`INPUT_PRESET_VOICE_RECOGNITION`) + Speech content type
- Actual device sample rate query (no longer assumes 16kHz)
- FrameResampler (rubato FftFixedIn) with proper buffer management (no lost samples)
- Peak normalization (`normalize_peak` → target 0.95) + RMS logging before inference
- Forced Spanish language (`language="es"`) in RunOptions for better accuracy
- Batch transcription via `session.run()` with `Backend::Auto`
- **Transcription verified working** — dictation test results:
  - "Hola, mundo. Esta es una prueba." ✅ 95%
  - "El reconocimiento de voz funciona perfectamente en el dispositivo." ✅ 100%
  - ~85% for longer phrases (expected for Whisper Tiny)
- Model catalog with ALL 65 Handy PC models (34 originally + 31 added)
- 3 mobile recommendations: Parakeet TDT 0.6B v3, Canary 180M Flash, Nemotron Streaming 3.5
- VAD level meter visualization in UI
- Foreground service with persistent notification
- JNI callbacks (state changes, transcription, vad level, errors)
- Shizuku/IME/Clipboard injection strategies
- Cancel download now properly notifies UI via complete_cb
- Skip download (onboarding) now cancels Rust download
- No OOM limit — user can activate any model size
- **IME floating bubble overlay** — Compact 56dp pill at keyboard area, matching PC overlay style (AccentPink #E85D75), with idle/recording/confirm/error states
- **IME crash fixed** — Uses `ImeContainer` (FrameLayout + LifecycleOwner) with reflection to set `ViewTreeLifecycleOwner` tag via `Class.forName("androidx.lifecycle.R$id")`, fixing `IllegalStateException: ViewTreeLifecycleOwner not found`
- **Auto-activate model after download** — `EngineViewModel.onDownloadComplete()` calls `nativeSetActiveModel()` automatically so the model is immediately usable
- **ModelCard languages in multi-row** — `FlowRow` with per-language chips instead of single truncated chip
- **ModelCard layout fixed** — Column-based layout with 3 rows (title, languages+size, action buttons)
- **Onboarding default model** — Parakeet TDT 0.6B v3 (485 MB) instead of Whisper Small
- **Cancel behavior** — Shows "Download canceled" with retry button instead of "Model Ready"
- **Retry download** — Fixed race condition where retry after cancel would not restart download

### ❌ Known Limitations
- Whisper Tiny struggles with long phrases containing proper nouns (e.g., "Handy para Android" → "han de parandro")
- Some Whisper models (English-only variants) show duplicate entries alongside multilingual variants
- Moonshine Base models not yet verified to work with transcribe-cpp on Android
- Voxtral Small 24B (17 GB) is listed but impractical for most mobile devices
- IME bubble uses hardcoded strings (not string resources) for simplicity
- IME bubble has no recording timer (MM:SS) like the PC overlay
- IME bubble has no streaming live text display (partial + tentative with blinking caret)

### 🔧 Critical Fixes Applied
| # | Fix | Details |
|---|-----|---------|
| 1 | **DIRECTION_INPUT bug** | `aaudio-sys v0.1.0` defines `DIRECTION_INPUT = 0` (should be 1). Replaced with raw constant `AAUDIO_DIRECTION_INPUT = 1` |
| 2 | **Input preset** | Added `setInputPreset(INPUT_PRESET_VOICE_RECOGNITION)` + `setContentType(CONTENT_TYPE_SPEECH)` + `setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)` |
| 3 | **Resampler buffer loss** | Accumulated `input_pending` + `output_pending` buffers in FrameResampler to prevent sample dropping |
| 4 | **Peak normalization** | Added `normalize_peak()` scaling audio to 0.95 peak before session.run() |
| 5 | **Spanish language** | Forced `language: Some("es")` in RunOptions |
| 6 | **NDK linker fixes** | Injected `CMAKE_ARGS` and dummy `libpthread.a` for transcribe-cpp build |
| 7 | **Cancel download UX** | Tokio task now calls `complete_cb(false, "Download cancelled")` on cancel; OnboardingViewModel cancel skip also cancels Rust download |
| 8 | **OOM limit removed** | Removed 1600MB limit from `set_active_model()` — user chooses freely |
| 9 | **IME ViewTreeLifecycleOwner** | Replaced `resources.getIdentifier()` with reflection on `androidx.lifecycle.R$id.view_tree_lifecycle_owner` to correctly set the lifecycle owner tag; wrapped ComposeView in `ImeContainer` (FrameLayout + LifecycleOwner) |
| 10 | **Auto-activate on download** | Added `nativeSetActiveModel(modelId)` call in `EngineViewModel.onDownloadComplete()` before `refreshModels()` |
| 11 | **ModelCard languages** | Changed from single `Surface` chip + ellipsis to `FlowRow` with per-language chips via `model.language.split(",")` |

### 📋 Model Catalog — 65 Models (all from Handy PC)
| Priority | Model | Size | Why |
|----------|-------|------|-----|
| 🥇 | **Parakeet TDT 0.6B v3** (Q4_K_M) | 485 MB | 25 languages, fast, accurate — default mobile recommendation |
| 🥈 | **Canary 180M Flash** (Q4_K_M) | 139 MB | Ultra-light, 4 langs + translation |
| 🥉 | **Nemotron 3.5 Streaming** (Q4_K_M) | 496 MB | 28 languages, streaming, auto-detect |
| | Whisper family (Tiny/Base/Small/Medium + Large v2/v3/Turbo + English-only) | 46 MB–1.2 GB | 99 languages (multilingual) or English-only |
| | Canary family (180M/1B/1B Flash/1B v2/Qwen 2.5B) | 139 MB–1.7 GB | 4–25 languages with translation |
| | Parakeet family (v2, Unified EN, CTC, RNN-T, TDT-CTC, TDT 1.1B) | 135 MB–936 MB | English-only, various architectures |
| | Qwen3-ASR (0.6B, 1.7B) | 590 MB–1.5 GB | 30 languages, excellent multilingual |
| | Fun-ASR (Nano, Nano Multilingual) | 557 MB | 3–31 languages, Asian + European |
| | GigaAM v3 (CTC, E2E-CTC, RNN-T, E2E-RNN-T) | 182–184 MB | Russian speech-to-text |
| | Granite Speech (4.1 NAR, 4.0 1B, 4.1 2B, Plus) | 1.5–1.6 GB | 5–6 languages, high accuracy |
| | MedASR | 83 MB | English, ultra-light, token timestamps |
| | Moonshine (Tiny/Base/Streaming Tiny/Small/Medium + 6 languages) | 35–296 MB | Ultra-light, various languages |
| | Cohere Transcribe | 1.6 GB | 14 languages, highest accuracy (92) |
| | SenseVoice Small | 253 MB | 5 languages, auto-detect |
| | Voxtral (Mini 3B, Mini 4B, Small 24B) | 2.8–17 GB | 8–13 languages, streaming, massive |
| | Nemotron Speech Streaming EN | 730 MB | English streaming |
| | Breeze-ASR-25 | 1.2 GB | Taiwanese Mandarin + English

## Desktop Development Commands

```bash
# Install dependencies
bun install

# Run in development mode
bun run tauri dev

# Build for production
bun run tauri build

# Linting and formatting
bun run lint
bun run format
```

## Code Style

**Rust:** Run `cargo fmt` and `cargo clippy` before committing. Handle errors explicitly.

**Kotlin:** Jetpack Compose with Material3, StateFlow/collectAsState for reactive state.

## Commit Conventions

Use conventional commit prefixes: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
Focus the message on _why_, not _what_.
