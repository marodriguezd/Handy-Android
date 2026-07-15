# AGENTS.md

This file provides guidance to AI coding assistants working with code in this repository.

## Project

**Handy Android** (`handy-android/`) — Android port of Handy (Rust JNI + Kotlin/Jetpack Compose). Fork del [repositorio original](https://github.com/cjpais/Handy) enfocado exclusivamente en Android.

## Development Commands

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

# Create dummy libpthread.a (Android NDK has pthread in libc, but linker still needs it)
mkdir -p /tmp/dummy-pthread
TOOLCHAIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64
echo "void pthread_dummy(void){}" > /tmp/dummy-pthread/dummy.c
$TOOLCHAIN/bin/aarch64-linux-android26-clang -c -o /tmp/dummy-pthread/dummy.o /tmp/dummy-pthread/dummy.c
$TOOLCHAIN/bin/llvm-ar crs /tmp/dummy-pthread/libpthread.a /tmp/dummy-pthread/dummy.o

# 1. Build Rust native library (ARM64)
# NOTE: CMAKE_TOOLCHAIN_FILE + CMAKE_ARGS are required to avoid -march=native errors
# with the cross-compiler, and to set the correct ABI (arm64-v8a) and platform level.
cd handy-android/handy-core
CMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
CMAKE_ARGS="-DGGML_NATIVE=OFF -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=26" \
RUSTFLAGS="-L /tmp/dummy-pthread" \
cargo ndk --target aarch64-linux-android --platform 26 build --release

# 2. Copy .so to jniLibs
cp target/aarch64-linux-android/release/libhandy_core.so ../app/src/main/jniLibs/arm64-v8a/

# 3. Build debug APK
cd ..
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version> ./gradlew assembleDebug

# 4. Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5. Clear app data (for clean test)
adb shell pm clear com.handy.app.debug
adb logcat -c
adb shell am start -n com.handy.app.debug/com.handy.app.MainActivity
```

**IMPORTANT:** The Gradle `buildRust` task (in `app/build.gradle.kts`) runs as a dependency of `assembleDebug` and always rebuilds Rust in **debug** mode (~131MB) with `--link-libcxx-shared`. It overwrites any release `.so` you manually placed in `jniLibs/`. If you want the 6MB release `.so` in the APK, you have two options:
- Run `./gradlew assembleRelease` (requires a release keystore)
- Manually build with `RUSTFLAGS="-L /tmp/dummy-pthread" cargo ndk ... build --release` and copy the .so to jniLibs AFTER the gradle build completes, then rebuild without `buildRust` or modify the gradle task to set `RUSTFLAGS`.

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
- `MainActivity.kt` — Bottom navigation with 4 tabs (General, Modelos, Historial, Acerca de), shared SettingsViewModel
- `viewmodel/EngineViewModel.kt` — State management for recording/transcription
- `bridge/EngineBridge.kt` — JNI external fun declarations
- `bridge/EngineCallback.kt` — Callback interface (Rust → Kotlin)
- `ime/HandyInputMethodService.kt` — IME keyboard with dictation UI
- `injection/` — Strategy pattern for text injection (Shizuku, IME, Clipboard)
- `service/RecordingService.kt` — Foreground service for persistent recording


## Current State (Checkpoint — July 16, 2026 — Sprint 11 — Preview Release)

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
- **IME Redesign (Sprint 9)** — Complete rewrite matching Wispr Flow floating pill design:
  - Floating pill UI with transparent window background, full rounded corners (28.dp), and elevation shadows instead of a full-width bottom sheet. Bottom padding increased to 56.dp to avoid overlap with system navigation bar.
  - **Auto-commit text** — Transcription auto-inserts via `InputConnection.commitText()` (no confirm step, like Wispr Flow). Fixed bug where multiple dictations in same field failed to reset `autoCommitted` flag.
  - **Flicker fix** — Removed eager UI state update to `STATE_LISTENING` in `startRecording()`. UI now waits for Rust engine callback to avoid IDLE -> LISTENING -> IDLE -> LISTENING visual bounce.
  - **4 visual states**: Idle (mic pill + keyboard switch), Recording (9-bar waveform + MM:SS timer + stop), Transcribing (Material3 spinner + cancel), Error (message + retry)
  - **Smooth animations**: Pop-in (460ms cubic-bezier), pulsing dot (1.9s), phase-offset waveform bars, timer
  - **Theme-aware colors** via `MaterialTheme.colorScheme` (light/dark mode support)
  - **Auto-commit guard** (`autoCommitted` flag) prevents infinite retry loops if injection fails
  - **Model availability check** in `startRecording()` — shows error if no model is downloaded
  - **confirmInsert failure handling** — Shows `STATE_ERROR` instead of silently resetting
  - **Keyboard switcher** via `InputMethodManager.showInputMethodPicker()` with try-catch fallback to settings
  - **IME injection priority** — IME InputConnection takes priority over Shizuku (`InjectorRouter.selectStrategy()`) for the most reliable text insertion path
- **IME ViewTreeLifecycleOwner CRITICAL FIX** — Jetpack Compose's `WindowRecomposer` searches for the `ViewTreeLifecycleOwner` starting from the window's `rootView` (the `parentPanel` LinearLayout in the IME). Setting the owner on the `ComposeView` is NOT ENOUGH and causes `IllegalStateException`. FIX: Extract the dialog's `DecorView` in `onCreateInputView()` (`this.window?.window?.decorView`) and explicitly call `setViewTreeLifecycleOwner`, `setViewTreeViewModelStoreOwner`, and `setViewTreeSavedStateRegistryOwner` on it before calling `setContent`. This is the definitive canonical fix for Compose in InputMethodService.
- **Auto-activate model after download** — `EngineViewModel.onDownloadComplete()` calls `nativeSetActiveModel()` automatically so the model is immediately usable
- **ModelCard languages in multi-row** — `FlowRow` with per-language chips instead of single truncated chip
- **ModelCard layout fixed** — Column-based layout with 3 rows (title, languages+size, action buttons)
- **Onboarding default model** — Parakeet TDT 0.6B v3 (485 MB) instead of Whisper Small
- **Cancel behavior** — Shows "Download canceled" with retry button instead of "Model Ready"
- **Retry download** — Fixed race condition where retry after cancel would not restart download
- **Skip download infinite loop** — Added `skipped` flag to `OnboardingViewModel` so clicking "Skip" doesn't immediately auto-trigger the download collector again.

### ❌ Known Limitations
- Whisper Tiny struggles with long phrases containing proper nouns (e.g., "Handy para Android" → "han de parandro")
- Some Whisper models (English-only variants) show duplicate entries alongside multilingual variants
- Moonshine Base models not yet verified to work with transcribe-cpp on Android
- Voxtral Small 24B (17 GB) is listed but impractical for most mobile devices
- IME uses hardcoded UI strings (not string resources) — i18n pending
- No streaming live text display during recording (batch transcription only)
- `onComputeInsets` removed — IME height may cause unexpected layout shifts in host apps
- Gradle `buildRust` task rebuilds Rust in debug mode without `RUSTFLAGS`, overwriting release `.so`
- `DictationScreen.kt` removed — no standalone dictation test UI (IME is the primary interface)

### 🔧 Critical Fixes Applied
| # | Round | Fix | Details |
|---|-------|-----|---------|
| 1 | 1 | **DIRECTION_INPUT bug** | `aaudio-sys v0.1.0` defines `DIRECTION_INPUT = 0` (should be 1). Replaced with raw constant `AAUDIO_DIRECTION_INPUT = 1` |
| 2 | 1 | **Input preset** | Added `setInputPreset(INPUT_PRESET_VOICE_RECOGNITION)` + `setContentType(CONTENT_TYPE_SPEECH)` + `setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)` |
| 3 | 1 | **Resampler buffer loss** | Accumulated `input_pending` + `output_pending` buffers in FrameResampler to prevent sample dropping |
| 4 | 1 | **Peak normalization** | Added `normalize_peak()` scaling audio to 0.95 peak before session.run() |
| 5 | 1 | **Spanish language** | Forced `language: Some("es")` in RunOptions |
| 6 | 1 | **NDK linker fixes** | Injected `CMAKE_ARGS` and dummy `libpthread.a` for transcribe-cpp build |
| 7 | 1 | **Cancel download UX** | Tokio task now calls `complete_cb(false, "Download cancelled")` on cancel; OnboardingViewModel cancel skip also cancels Rust download |
| 8 | 1 | **OOM limit removed** | Removed 1600MB limit from `set_active_model()` — user chooses freely |
| 9 | 1 | **IME ViewTreeLifecycleOwner (v3)** | CANONICAL FIX: Compose `WindowRecomposer` checks `view.rootView`. Extracted `dialogWindow.decorView` in `onCreate()` and `onCreateInputView()` and set standard AndroidX ViewTree extensions on it. Removes all reflection hacks and permanently fixes `IllegalStateException`. |
| 10 | 1 | **Auto-activate on download** | Added `nativeSetActiveModel(modelId)` call in `EngineViewModel.onDownloadComplete()` before `refreshModels()` |
| 11 | 1 | **ModelCard languages** | Changed from single `Surface` chip + ellipsis to `FlowRow` with per-language chips via `model.language.split(",")` |
| 12 | 1 | **IME auto-commit** | Transcription auto-inserts via `InputConnection.commitText()` — no confirm step needed |
| 13 | 1 | **IME model check** | `startRecording()` checks `nativeIsModelLoaded()` before starting; shows error if no model available |
| 14 | 1 | **IME injection failure** | `confirmInsert()` failure shows `STATE_ERROR` (not silent reset) so user gets feedback + retry option |
| 15 | 1 | **IME keyboard switch** | `showInputMethodPicker()` with try-catch fallback to `ACTION_INPUT_METHOD_SETTINGS` for OEM compatibility |
| 16 | 2 | **ModelsViewModel revert** | Removed `state.downloads - event.modelId` — entries stay in map so OnboardingViewModel can consume them |
| 17 | 2 | **Onboarding collector rework** | Changed from `activeDownloadId` lookup to scanning `modelState.downloads.entries` directly; handles success + failure |
| 18 | 2 | **Download error path** | OnboardingViewModel collector now auto-detects completion events with `event.error != null` and sets `downloadError` + `isDownloading = false` |
| 19 | 2 | **UI exclusivity (two buttons)** | Replaced 5 overlapping `if` blocks with `when` — exactly one UI state visible at a time |
| 20 | 2 | **ImeContainer init ordering** | Moved `ViewTreeLifecycleOwner` reflection from `onAttachedToWindow()` to `init{}` (before `addView()`) to fix depth-first timing |
| 21 | 2 | **ProGuard rules** | Added `-keep class androidx.lifecycle.ViewTreeLifecycleOwner { *; }` for release build reflection safety |
| 22 | 2 | **IME Bottom Padding** | Added `bottom = 56.dp` padding to `HandyVoiceBar` so the floating pill doesn't overlap the system nav bar / keyboard switcher |
| 23 | 2 | **IME Flicker Fix** | Removed eager `_state.value = STATE_LISTENING` in `startRecording()` to wait for native callback, eliminating visual bounce |
| 24 | 3 | **Loading state flicker** | Set `_state = STATE_LOADING` immediately in `startRecording()` + suppress `STATE_IDLE` during `STATE_LOADING` in `onStateChange()` to prevent native `nativeLoadModel` transient IDLE from flashing the idle bar |
| 25 | 3 | **Auto-commit StateFlow conflation** | Moved auto-insert from IME lifecycleScope collector into `EngineViewModel.onTranscription()` with `@Volatile _imeModeEnabled` flag. Fixes race where Rust `nativeFinalizeStream` dispatches CONFIRM then immediately IDLE on the same IO thread, causing StateFlow to conflate CONFIRM before the main-thread collector can process it |
| 26 | 3 | **Rust idle dispatch after transcription** | Modified `nativeFinalizeStream` to NOT dispatch `STATE_IDLE` after successful transcription — Kotlin manages state transition after auto-injection. Error/no-audio paths still dispatch IDLE for state reset |
| 27 | 3 | **IME clickable area** | Moved IdleBar clickable from inner Surface to outermost Box so the entire pill (including padding) is a valid touch target |
| 28 | 3 | **Shared SettingsViewModel** | Unificado en `MainActivity.setContent` — instancia única de SettingsViewModel para generalTabContent, advancedTabContent y postProcessTabContent. Elimina datos fuera de sincronía entre pestañas. |
| 29 | 3 | **menuAnchor() deprecation** | Reemplazado `Modifier.menuAnchor()` por `Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)` en SettingsScreen.kt. Cero warnings de compilación. |
| 30 | 3 | **DictationScreen eliminado** | Archivo `ui/dictation/DictationScreen.kt` y directorio removidos — sin ruta, sin referencias. |

### 🔧 Sprint 11 — IME Injection Priority & Reliability Fixes
| # | Round | Fix | Details |
|---|-------|-----|---------|
| 31 | 4 | **IME priority over Shizuku** | `InjectorRouter.selectStrategy()` now tries IME InputConnection before Shizuku. Shizuku's `KEYCODE_PASTE` reflection often fails silently, leaving text only in clipboard. IME `commitText()` is the most reliable path. |
| 32 | 4 | **`@Volatile` on `lastInputConnection`** | `HandyInputMethodService.lastInputConnection` was written on Main thread and read on IO thread via the `imeInjector` lambda. Without `@Volatile`, the IO thread could see stale `null`, making `ImeInjector.isAvailable()` return `false` and falling back to clipboard. |
| 33 | 4 | **TOCTOU race fix in ImeInjector** | `inputConnectionProvider()` was called twice (in `isAvailable()` on IO thread, then in `inject()` on Main thread). Between calls, `onFinishInput()` could nullify the connection. Now `isAvailable()` caches the connection in a `@Volatile` field, and `inject()` reuses it on Main. |

### ✅ Sprint 10 Completado — Rediseño de Interfaz de App

**Objetivo cumplido:** Interfaz premium alineada con el escritorio. Paleta oscura/crema + rosa pastel, BottomNavigationBar con 4 pestañas, TabRow para sub-secciones.

| Componente | Descripción |
|---|---|
| `Color.kt` | Nueva paleta: `#252422` fondo, `#2C2B29` surface, `#F48FB1` acento, `#F0EDE9` texto |
| `Theme.kt` | Tema oscuro fijo (darkColorScheme), sin dynamicColor |
| `Screen.kt` | 5 rutas: Onboarding, General, Models, History, About |
| `AppNavigation.kt` | Bottom nav 4 ítems (General ⚙️, Modelos 🔧, Historial 📅, Acerca de ℹ️) + TabRow |
| `MainActivity.kt` | SettingsViewModel compartido entre 3 pestañas |
| `SettingsScreen.kt` | 4 composables: GeneralSettingsContent, PostProcessContent, AboutContent (con GitHub link), AdvancedSettingsContent (con 4 secciones desktop) |
| `DictationScreen.kt` | **Eliminado** — código muerto |

**Settings añadidas (SharedPreferences):**
- `experimentalEnabled` (Boolean) — Funciones Experimentales
- `vadEnabled` (Boolean) — Voice Activity Detection
- `addFinalSpace` (Boolean) — Agregar Espacio Final
- `postProcessingEnabled` (Boolean) — Post Procesamiento toggle
- `autoSend` (String: "disabled"|"ime") — Envío automático

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

## Code Style

**Rust:** Run `cargo fmt` and `cargo clippy` before committing. Handle errors explicitly.

**Kotlin:** Jetpack Compose with Material3, StateFlow/collectAsState for reactive state.

## Commit Conventions

Use conventional commit prefixes: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
Focus the message on _why_, not _what_.
