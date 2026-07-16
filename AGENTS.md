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

**Rust-only compilation check (no NDK needed):**

```bash
cd handy-android/handy-core && cargo check
```

**Kotlin-only compilation check (no NDK needed):**

```bash
cd handy-android && ./gradlew :app:compileDebugKotlin
```

## Android Architecture Overview

The Android port consists of:

### Rust Core (`handy-android/handy-core/`)
- `cdylib` exposing 21+ JNI functions via `libhandy_core.so`
- `jni_bridge.rs` — All `#[no_mangle]` JNI implementations
- `audio/` — AAudio capture, FrameResampler (rubato), EnergyVAD
- `transcription/` — StreamWorker (native streaming), PeriodicWorker (batch-periodic), batch `session.run()`
- `model/` — Model catalog, download (HTTP via reqwest, GGUF from handy-computer)
- `engine.rs` — EngineState singleton with ENGINE OnceLock<Mutex<Option<EngineState>>>

### Kotlin App (`handy-android/app/`)
- `HandyApplication.kt` — Process-wide singleton for EngineViewModel
- `MainActivity.kt` — Bottom navigation with 4 tabs (General, Modelos, Historial, Acerca de), shared SettingsViewModel
- `viewmodel/EngineViewModel.kt` — State management for recording/transcription (6 states: IDLE, LOADING, LISTENING, TRANSCRIBING, CONFIRM, ERROR)
- `bridge/EngineBridge.kt` — JNI external fun declarations
- `bridge/EngineCallback.kt` — Callback interface (Rust → Kotlin)
- `ime/HandyInputMethodService.kt` — IME keyboard with dictation UI (5 visual states, animated transitions)
- `injection/` — Strategy pattern for text injection (IME → Shizuku → Clipboard)
- `service/RecordingService.kt` — Foreground service for persistent recording


## Current State (Checkpoint — July 16, 2026 — Sprint 14 — Capability-Aware Model Catalog + Observability)

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
- Shizuku/IME/Clipboard injection strategies (IME priority)
- Cancel download now properly notifies UI via complete_cb
- Skip download (onboarding) now cancels Rust download
- No OOM limit — user can activate any model size

### ✅ Sprint 14 — New Implementations

#### Capability Core (`capability/`)
| # | Feature | Details |
|---|---------|---------|
| 1 | **`DeviceTier` enum** | LOW / MID / HIGH / FLAGSHIP / TABLET; cada tier declara `maxRecommendedModelCapability` (ULTRA_LIGHT / LIGHT / MEDIUM / HEAVY / EXTREME) |
| 2 | **`CapabilitySnapshot`** | Data class inmutable con `totalMemBytes`, `availMemBytes`, `maxMemoryProcessBytes`, `isLowRamDevice`, `memoryClassMb`, `largeMemoryClassMb`, `cpuCores`, `sdkInt`. Método `toTier()` resuelve el tier con bandas (≤1.5 LOW, ≤3.5 MID, ≤6.5 HIGH, ≤12.5 FLAGSHIP, >12.5 TABLET) |
| 3 | **`ModelCapability` enum** | ULTRA_LIGHT (≤100MB) / LIGHT (≤500MB) / MEDIUM (≤1.5GB) / HEAVY (≤3GB) / EXTREME (sin límite). Companion: `fromModel(model)`, `heavyGateIds` (3 Voxtral), `experimentalIds` (7 Moonshine Base monolingües) |
| 4 | **`DeviceCapabilityDetector`** | Singleton `detect(context)` que lee `ActivityManager.MemoryInfo` + `isLowRamDevice` + `Runtime.maxMemory()` y devuelve `CapabilitySnapshot` |
| 5 | **`CompatibilityResolver`** | Función pura `computeCompatibility(model, snapshot, showExperimental): ModelCompatibility`. Devuelve `status` (ACTIVE / TIER_RECOMMENDED / TIER_RECOMMENDED_DEEP / FIT / EXCEEDS / IMPOSSIBLE), `badges` (EXPERIMENTAL / HEAVY_GATE / EXCEEDS_RAM / LARGE_HEAP_REQUIRED), `requiresConsent`, `hidden` |

#### UI Capability-Aware (`ui/models/components/`)
| # | Feature | Details |
|---|---------|---------|
| 1 | **`DeviceCapabilityHeader`** | Card top del catálogo: muestra `totalMemGB` + tier, ícono Memory, botón Refresh, y un `Switch` para `showExperimentalModels` visible solo en MID+ |
| 2 | **`CompatibilityBadgeChip`** | Surface chip para 4 badges (EXPERIMENTAL / HEAVY_GATE / EXCEEDS_RAM / LARGE_HEAP_REQUIRED) con color accent + border. También `ActiveBadge` para el modelo activo |
| 3 | **`HeavyModelWarningDialog`** | `AlertDialog` que muestra `displayName` + `sizeGb` + `totalGb`. Diferencia title/body para HEAVY vs EXTREME. **Checkbox required** para habilitar el botón Confirm |

#### Integración en ViewModels
| # | Feature | Details |
|---|---------|---------|
| 1 | **`ModelsViewModel`** | UiState extendido con `snapshot / visibleModels / showExperimental / showLargeModelDialogFor`. Nuevo método `attemptDownload(model)` gatea vía `computeCompatibility` y puede mostrar el dialog; `confirmLargeModelDownload()` ejecuta el download post-ack; `cancelLargeModelDownload()` cierra; `downloadModel(modelId)` non-gating preservado para flows imperativos (onboarding). Sort tier-aware |
| 2 | **`OnboardingViewModel`** | `cachedSnapshot` (lazy) cachea el snapshot una sola vez. Filtro `fitsAndSafe` excluye heavyGate. Chain de selección: `recommended+fitsAndSafe` → `any+fitsAndSafe` → null-dead-end con `Log.w("OnboardingVM", ...)` + `isDownloadReady=true`. **11 logs de observability** (TAG="OnboardingVM") en transiciones de step, model load, target selection, download events |
| 3 | **`EngineViewModel`** | `Log.d(TAG, "EngineVM init; capabilityTier=${snapshot.toTier()}; totalMemGB=${snapshot.totalMemGbReport}; showExperimental=${...}")` justo después de `nativeInit(...)` |

#### Settings & i18n
| # | Feature | Details |
|---|---------|---------|
| 1 | **`SettingsStore.showExperimentalModels`** | Nuevo boolean persistido en `show_experimental_models` (default `false`) |
| 2 | **`strings.xml` +18 keys** | `header_tier_*` (5), `badge_*` (4), `heavy_dialog_*` (5), `capability_*` (2), `show_experimental_models`, `model_unavailable_on_device` |

#### Validación end-to-end (device A059, Android 16, 7.28 GB RAM → FLAGSHIP)
| # | Verificación | Resultado |
|---|--------------|-----------|
| 1 | `EngineVM init` log | `EngineVM init; capabilityTier=FLAGSHIP; totalMemGB=7.28413; showExperimental=false` |
| 2 | Onboarding wizard via ADB | Step 0→1→2→3→4→complete, todos los `nextStep: N -> N+1` logs emitidos en orden |
| 3 | Tier-aware target selection | `Selected target: parakeet-tdt-0.6b-v3-Q4_K_M (size=462MB, recommended=true, score=2)` — primer recomendado que fits + safe |
| 4 | Persistencia de onboarding | SharedPreferences `onboarding_completed=true` post-complete |
| 5 | Navegación final | Top activity = `MainActivity`, pantalla principal visible (Settings/General tab) con labels "Settings", "Avanzado", "Audio", "Battery", "Modelos" |

### ✅ Sprint 13 — New Implementations

#### Persistencia de modelo activo (`manager.rs`)
| # | Feature | Details |
|---|---------|---------|
| 1 | **Archivo .active_model** | El modelo activo se persiste en `model_dir/.active_model` entre reinicios de la app |
| 2 | **Carga automática** | `ModelManager::new()` lee `.active_model` y restaura el modelo activo si el `.gguf` existe |
| 3 | **Guardado en set_active_model()** | Cada activación persiste el ID automáticamente |
| 4 | **Limpieza en delete_model()** | Si se borra el modelo activo, se elimina el archivo `.active_model` |
| 5 | **Defensa contra borrados externos** | Si el `.gguf` no existe en disco, se limpia el archivo huérfano |

#### IME — onComputeInsets Restaurado (`HandyInputMethodService.kt`)
| # | Feature | Details |
|---|---------|---------|
| 1 | **Altura dinámica** | `contentHeightPx` medida vía `onGloballyPositioned` en Compose |
| 2 | **contentTopInsets** | Solo el área del pill flotante se reporta como contenido IME |
| 3 | **TOUCHABLE_INSETS_CONTENT** | Toques en el fondo transparente pasan directamente a la app host |
| 4 | **Sin layout shifts** | La app host ya no es empujada hacia arriba por la ventana completa del IME |

#### Cancelación de Batch Transcription (`engine.rs` + `periodic.rs`)
| # | Feature | Details |
|---|---------|---------|
| 1 | **cancel_flag (Arc\<AtomicBool\>)** | Flag compartido entre `TranscriptionEngine` y `PeriodicWorker` |
| 2 | **Triple verificación en run()** | Antes de crear session, antes de `session.run()`, y después (descarta resultado) |
| 3 | **Verificación en PeriodicWorker** | Antes y después de cada `session.run()` parcial (~3s); sale del loop si está activo |
| 4 | **Reset entre grabaciones** | `start_stream()` y `start_periodic()` resetean el flag al iniciar nueva sesión |
| 5 | **cancel_stream() también activa el flag** | Seguridad adicional: el Cancel del canal + el flag atómico |

### ✅ Sprint 12 — New Implementations (previous)

#### Rendimiento Rust (pipeline + VAD optimizations)
| # | Optimization | Details |
|---|-------------|---------|
| 1 | **Eliminated redundant clone in hot path** | Removed `frame.to_vec()` in `process_samples()` — `frame` is already `&[f32]`, saving 1 allocation per audio frame (~33 allocs/sec) |
| 2 | **No double buffering during streaming** | Added `streaming_active` flag — when streaming is active, audio is NOT stored in `audio_buffer` (already goes to router), saving up to ~19MB for a 60s recording |
| 3 | **Pre-allocated audio buffer** | `reserve(262144)` ~16 seconds of 16kHz audio — avoids repeated reallocations |
| 4 | **EnergyVAD fast initial adaptation** | First 10 frames (~300ms) use alpha×10 (0.1) to adapt to room noise 10x faster, then normal alpha (0.01) |
| 5 | **Race condition FIX** 🔴 | `streaming_active` was set in `drain_buffer()` before `set_stream_router()`, creating a window where frames could be lost. MOVED: `streaming_active = router.is_some()` is now set atomically inside `set_stream_router()` |

#### UI IME Redesign (HandyInputMethodService.kt)
| # | Improvement | Details |
|---|-------------|---------|
| 1 | **AnimatedContent transitions** | Smooth slide + fade animations when switching between Idle/Recording/Transcribing/Confirm/Error states (300ms, eased) |
| 2 | **ConfirmBar redesign** | Added Copy-to-clipboard button, "Tap Insert to use" hint text, green Insert button with checkmark icon + shadow, Discard outline style |
| 3 | **Press scale animations** | All buttons animate to 0.85-0.97x scale on press (100ms tween), using reusable `rememberPressScaleClickable` + `pressScaleClickable` pattern |
| 4 | **LoadingBar** | Separate `LoadingBar` composable for STATE_LOADING (spinner + "Loading model…") — previously showed RecordingBar during model load |
| 5 | **RecordingBar** | Timer in Surface badge, stop button with red glow shadow, partial text in separate Surface with rounded corners |
| 6 | **ErrorBar** | Soft red background + border, circular error icon, better spacing |
| 7 | **IdlePulsingDot** | More subtle idle animation (2500ms cycle, lower alpha) vs active PulsingDot (1900ms) |
| 8 | **FIX: context/LocalContext** | Used `LocalContext.current` instead of bare `context` in ConfirmBar copy button |
| 9 | **FIX: roundToPx** | Replaced `dp.roundToPx()` with `fullHeight / 4` lambda in AnimatedContent transitionSpec |
| 10 | **Dead code removed** | Eliminated unused `pressScale()` extension, `errorShownAt` state, `TextAlign` import, `rememberUpdatedState`, `slideOutVertically`, `kotlinx.coroutines.launch` |

#### i18n — String Resources Migration
| File | Strings Migrated |
|------|-----------------|
| `strings.xml` | +18 new string resources added across all categories |
| `HandyInputMethodService.kt` | 8 strings: "Dictate", "Loading model…", "Transcribing…", "Tap Insert to use", "Discard", "Insert", "Something went wrong", clipboard "Handy" label |
| `EngineViewModel.kt` | 2 error messages: "No model downloaded…", "Insertion failed" (via `getApplication().getString()`) |
| `SettingsScreen.kt` | 12 hardcoded Spanish strings: section headers (APLICACIÓN/SALIDA/TRANSCRIPCIÓN/EXPERIMENTAL), toggle labels+descriptions, GitHub link |
| `ModelCatalogScreen.kt` | "Downloading X%" → `download_progress_percent` with `%1$d%%` format |
| `OnboardingScreen.kt` | "Downloading X%" → same `download_progress_percent` format string |
| `AppNavigation.kt` | Bottom nav labels ("General", "Modelos", "Historial", "Acerca de") + tab labels ("Modelos", "Post Proceso", "Avanzado") |
| **FIXES** | `contentDescription = { Text() }` type error → `item.label`, `remember { stringResource() }` composable context → direct calls |

#### Streaming en Vivo — Texto Parcial Durante Grabación
| # | Feature | Details |
|---|---------|---------|
| 1 | **PeriodicWorker** (`transcription/periodic.rs`) | NEW file — worker thread that accumulates audio frames and runs `session.run()` every ~3 seconds, dispatching partial text via JNI callback |
| 2 | **TranscriptionEngine** | Added `start_periodic()`, `finalize_periodic()`, `cancel_periodic()` methods + `StreamWorkerOrPeriodic` enum for type-safe worker storage |
| 3 | **nativeAttemptStreaming** | Now tries native streaming first (`start_stream`). If model doesn't support it → falls back to `start_periodic` with `drain_buffer()` + `set_stream_router()` |
| 4 | **Model compatibility** | **ALL models work** — native streaming for models that support `session.stream()` (Nemotron Streaming), periodic batch for everything else (Whisper, Parakeet, Canary, etc.) |
| 5 | **UX** | User sees partial text updating every ~3s during recording. On stop, full final transcription is dispatched as before |

### ✅ IME Features (Sprint 9-11)
- Floating pill UI with transparent window background, full rounded corners (28.dp), elevation shadows
- **5 visual states**: Idle, Loading, Recording (waveform + timer + stop), Transcribing (spinner + cancel), Confirm (Insert/Discard), Error (message + retry)
- **Animated state transitions** with slide + fade (AnimatedContent, 300ms)
- **Press scale animations** on all interactive elements
- Auto-activate model after download
- IME injection priority: IME InputConnection → Shizuku → Clipboard
- ConfirmBar with Insert/Discard/Copy actions

### ❌ Known Limitations
- Whisper Tiny struggles with long phrases containing proper nouns (e.g., "Handy para Android" → "han de parandro")
- Some Whisper models (English-only variants) show duplicate entries alongside multilingual variants
- Moonshine Base models not yet verified to work with transcribe-cpp on Android
- Voxtral Small 24B (17 GB) is listed but impractical for most mobile devices
- ~~`onComputeInsets` removed~~ ✅ RESTORED (Sprint 13) — IME height now properly reported to host apps
- Gradle `buildRust` task rebuilds Rust in debug mode without `RUSTFLAGS`, overwriting release `.so`
- Periodic transcription (~3s intervals) is slower than native streaming for partial updates
- No Gradle wrapper committed — requires `gradle` command directly
- `session.run()` is still blocking during batch transcription (cancel_flag discards result post-hoc but can't interrupt C++ mid-inference)

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
| 9 | 1 | **IME ViewTreeLifecycleOwner (v3)** | CANONICAL FIX: Compose `WindowRecomposer` checks `view.rootView`. Extracted `dialogWindow.decorView` in `onCreate()` and `onCreateInputView()` and set standard AndroidX ViewTree extensions on it |
| 10 | 1 | **Auto-activate on download** | Added `nativeSetActiveModel(modelId)` call in `EngineViewModel.onDownloadComplete()` before `refreshModels()` |
| 11 | 1 | **ModelCard languages** | Changed from single `Surface` chip + ellipsis to `FlowRow` with per-language chips via `model.language.split(",")` |
| 12 | 2 | **Onboarding collector rework** | Changed from `activeDownloadId` lookup to scanning `modelState.downloads.entries` directly |
| 13 | 2 | **ImeContainer init ordering** | Moved `ViewTreeLifecycleOwner` setup from `onAttachedToWindow()` to `init{}` |
| 14 | 2 | **ProGuard rules** | Added keep rules for ViewTreeLifecycleOwner, AIDL, JNI classes |
| 15 | 2 | **IME Flicker Fix** | Removed eager `_state = STATE_LISTENING` in `startRecording()` |
| 16 | 3 | **Loading state flicker** | Suppress native STATE_IDLE during STATE_LOADING |
| 17 | 3 | **Auto-commit StateFlow conflation** | Moved auto-insert into `EngineViewModel.onTranscription()` with `@Volatile _imeModeEnabled` |
| 18 | 3 | **Rust idle dispatch** | `nativeFinalizeStream` no longer dispatches STATE_IDLE after successful transcription |
| 19 | 3 | **IME clickable area** | Moved IdleBar clickable from inner Surface to outermost Box |
| 20 | 4 | **IME priority over Shizuku** | `InjectorRouter.selectStrategy()` tries IME InputConnection first |
| 21 | 4 | **`@Volatile` on `lastInputConnection`** | Prevents stale null reads from IO thread |
| 22 | 4 | **TOCTOU race fix in ImeInjector** | `isAvailable()` caches connection, `inject()` reuses it |
| **23** | **5** | **Streaming race condition** 🔴 | `streaming_active` moved to `set_stream_router()` — prevents frame loss between `drain_buffer()` and router attachment |
| **24** | **5** | **Periodic worker no audio** 🔴 | Added `set_stream_router()` in periodic fallback path — PeriodicWorker was never receiving audio frames |
| **25** | **5** | **Periodic loses initial audio** 🔴 | Added `drain_buffer()` + `router.feed(acc)` in periodic path — pre-streaming audio was discarded |
| **26** | **5** | **AppNavigation composable context** 🔴 | Fixed `remember { stringResource() }` — composable calls inside non-composable lambda → direct calls |
| **27** | **5** | **IME context/roundToPx** 🔴 | `context` not available in Composable → `LocalContext.current`; `dp.roundToPx()` not available → `fullHeight / 4` lambda |
| **28** | **6** | **Cancel flag stuck between recordings** 🔴 | `cancel_flag` was only reset in `run()`, not in `start_stream()`/`start_periodic()` — new recording would start with stale `true` flag → fixed by resetting in both start methods |

### 📋 Model Catalog — 65 Models (all from Handy PC)

| Priority | Model | Size | Why |
|----------|-------|------|-----|
| 🥇 | **Parakeet TDT 0.6B v3** (Q4_K_M) | 485 MB | 25 languages, fast, accurate — default mobile recommendation |
| 🥈 | **Canary 180M Flash** (Q4_K_M) | 139 MB | Ultra-light, 4 langs + translation |
| 🥉 | **Nemotron 3.5 Streaming** (Q4_K_M) | 496 MB | 28 languages, native streaming + auto-detect |
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

**Rust:** Run `cargo fmt` and `cargo clippy` before committing. Handle errors explicitly. Use `cargo check` for fast compilation verification.

**Kotlin:** Jetpack Compose with Material3, StateFlow/collectAsState for reactive state. Use `stringResource(R.string.xxx)` for all user-facing strings. Never hardcode strings.

## Commit Conventions

Use conventional commit prefixes: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
Focus the message on _why_, not _what_.

## Important Patterns

### IME State Machine
The `HandyVoiceBar` has 6 states from `EngineViewModel`: `STATE_IDLE(0)`, `STATE_LOADING(1)`, `STATE_LISTENING(2)`, `STATE_TRANSCRIBING(3)`, `STATE_ERROR(4)`, `STATE_CONFIRM(5)`.
- State transitions use `AnimatedContent` with slide + fade
- Each button uses `rememberPressScaleClickable` + `pressScaleClickable` for press animations
- All user-facing text uses `stringResource()` from `strings.xml`

### Streaming vs Periodic Transcription
- `nativeAttemptStreaming()` tries `start_stream()` first (for models supporting `session.stream()`)
- If streaming is not supported, falls back to `start_periodic()` (works with ALL models)
- Both use the same `StreamRouter` channel protocol (Feed/Finalize/Cancel commands)
- Periodic worker runs `session.run()` every ~3 seconds on accumulated audio
- `drain_buffer()` feeds pre-streaming audio before connecting the router
- `streaming_active` flag prevents double-buffering when router is connected

### Pipeline Audio Buffer
- Pre-allocated with `reserve(262144)` (~16s at 16kHz) to avoid reallocations
- During streaming: audio goes ONLY to stream router (not buffer) via `streaming_active` flag
- During batch: audio accumulates in buffer, returned by `stop()` for `session.run()`
- `streaming_active` is set atomically in `set_stream_router()` to prevent race conditions
