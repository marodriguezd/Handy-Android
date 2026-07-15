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

### ✅ Working
- App launches, model catalog displays GGUF models from handy-computer
- Model download from HuggingFace (handy-computer org) — GGUF format
- Model loading into memory via `transcribe_cpp` v0.1.3
- Audio capture via AAudio (16kHz float32 mono, device native rate)
- Audio resampling from device rate → 16kHz (rubato FftFixedIn)
- Batch transcription via `session.run()` (correct API for Whisper)
- VAD level meter visualization in UI
- Foreground service with persistent notification
- JNI callbacks (state changes, transcription, vad level, errors)
- Shizuku/IME/Clipboard injection strategies

### ❌ Issues Found (Current Checkpoint)
- **Transcription accuracy**: Still failing after recent fixes. The user reports it "no va" (does not work) during live testing on device.
  - AGC (`normalize_audio`) was removed because it boosted background noise, but the issue persists.
  - `Backend::Auto` is now used.
  - Needs further ADB logcat analysis to check if audio capture is working or if the model is failing to transcribe.

### ✅ Issues Resolved
- **Backend Optimization**: `Backend::Cpu` was forcing slow CPU execution. Changed to `Backend::Auto` to leverage hardware acceleration if available.
- **Linker Errors on NDK**: Resolved missing `libpthread` and NDK CMake errors by passing `CMAKE_ARGS` to the build script.

### 🔧 Changes Applied (this session)
1. Model URLs changed from `ggerganov/whisper.cpp` (GGML .bin) to `handy-computer` (GGUF)
2. Model IDs updated: `whisper-tiny-Q5_K_M`, `whisper-base-Q5_K_M`, etc.
3. Added Parakeet EN 0.6B model to catalog
4. `gpu_device: -1` → `gpu_device: 0` (whisper.cpp asserts >= 0)
5. Changed `Backend::Cpu` to `Backend::Auto` for better inference performance.
6. Removed streaming (`session.stream()` not supported for Whisper) → batch `session.run()`
7. Removed StreamWorker/StreamRouter architecture (no longer needed)
8. Removed `worker_id` from `EngineState`
9. Bypassed VAD filtering in audio accumulation (all frames saved)
10. Removed AGC (`normalize_audio`) because it amplified background noise and broke transcription.
11. AAudio now queries actual device sample rate instead of assuming 16kHz
12. Resampler configured with device's actual sample rate
13. Fixed NDK linkage issues by injecting `CMAKE_ARGS` and a dummy `libpthread.a`.

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
