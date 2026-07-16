# AGENTS.md — Handy Android Project Memory & Orchestration

> **This file is the canonical project memory.** Every AI session must read it first. It is kept up-to-date with the complete current state, conventions, commands, and open items so context persists across sessions.

---

## 🚀 Start Every Session Here

1. **Read this file** (`AGENTS.md`).
2. **Read `handy-android/PROGRESS.md`** for the latest checkpoint and next steps.
3. **Read `handy-android/LIMPIA.md`** for the clean-start checklist and ADB commands.
4. **Read `handy-android/SPEC.md`** for UI/UX specifications.
5. **Read `handy-android/ARCHITECTURE.md`** for system architecture.
6. Run `./gradlew :app:compileDebugKotlin` to verify the project builds.
7. Run `adb devices -l` to verify the test device is connected.

---

## 📋 Project Overview

**Handy Android** (`handy-android/`) is the Android port of the Handy desktop speech-to-text app. It combines:

- **`handy-core/`** — Rust `cdylib` exposing JNI bindings for audio capture (AAudio), resampling, VAD, model download, and Whisper/transcribe-cpp inference.
- **`app/`** — Kotlin/Jetpack Compose Android app with adaptive navigation, Material Design 3 UI, IME keyboard, foreground recording service, and text injection strategies.

**Repository:** Fork of [github.com/cjpais/Handy](https://github.com/cjpais/Handy), focused exclusively on Android.

---

## 🗓️ Current State — Sprint 16 (July 16, 2026)

### ✅ Completed

#### 1. Material Design 3 Redesign
- **Settings** — `SettingsRow` replaced with Material 3 `ListItem`.
- **Model Catalog** — `AssistChip` replaced with `SuggestionChip` for language tags.
- **IME** — Hardcoded `RoundedCornerShape` replaced with `MaterialTheme.shapes` tokens; touch targets enlarged to 48dp.
- **Badges** — `RoundedCornerShape(4.dp)` replaced with `MaterialTheme.shapes.extraSmall`.
- **Adaptive Navigation** — `NavigationRail` on large screens (`screenWidthDp >= 600`), `NavigationBar` on phones.
- **Version bump** — `versionCode=3`, `versionName="1.0.0-alpha2"`.

#### 2. ADB Test Automation Hooks (Debug Builds Only)
- **`TestCommandReceiver`** — Manifest receiver handling `DOWNLOAD_MODEL` and `SET_ACTIVE_MODEL` broadcasts with `model_id` extra.
- **Security** — Disabled in release via manifest placeholder; runtime-guarded by `BuildConfig.DEBUG`; model IDs validated against the catalog; native operations serialized with separate mutexes.
- **Shizuku disabled in debug** — Prevents permission/security exceptions during automated runs.
- **Skip onboarding intent** — `MainActivity` reads `skip_onboarding=true`.
- **ADB test script** — `handy-android/scripts/adb_test_flow.sh`.

#### 3. IME Touch Fix
- Switched `onComputeInsets` from `TOUCHABLE_INSETS_CONTENT` to `TOUCHABLE_INSETS_REGION` with an explicit `touchableRegion` Rect.
- Added fallback to `resources.displayMetrics.widthPixels` if decor view width is unavailable.
- Goal: ensure the floating pill receives touches while transparent background passes through to the host app.

#### 4. Catalog Sort Tests
- Extracted sort logic into pure `capability/CatalogSorter.kt` (`computeVisibleCatalog`).
- Added `CatalogSorterTest.kt` (10 tests) covering status ordering, promotion buckets, size tie-breaker, experimental filtering, and Voxtral regression guard.

#### 5. GPU/NPU Backend Investigation
- Documented options in `handy-android/BACKENDS.md`.
- **CPU** — Stable baseline, default for debug.
- **Vulkan** — Partially wired; disabled in debug, enabled in release with vendored headers; needs include-path fixes.
- **QNN/Hexagon** — Future, high maintenance cost.
- **NNAPI** — Not recommended (deprecated in Android 15, not supported by ggml).

---

## 🏗️ Architecture at a Glance

### Rust Core (`handy-android/handy-core/`)
- `jni_bridge.rs` — All `#[no_mangle]` JNI implementations.
- `audio/` — AAudio capture, FrameResampler (rubato), EnergyVAD.
- `transcription/` — `StreamWorker` (native streaming), `PeriodicWorker` (batch-periodic), batch `session.run()`.
- `model/` — Model catalog, download (HTTP via reqwest, GGUF from handy-computer).
- `engine.rs` — `EngineState` singleton with `ENGINE OnceLock<Mutex<Option<EngineState>>>`.

### Kotlin App (`handy-android/app/`)
- `HandyApplication.kt` — Process-wide singleton for `EngineViewModel`.
- `MainActivity.kt` — Adaptive navigation with 4 destinations.
- `viewmodel/EngineViewModel.kt` — Central state machine (IDLE, LOADING, LISTENING, TRANSCRIBING, CONFIRM, ERROR).
- `viewmodel/ModelsViewModel.kt` — Capability-tier-aware catalog.
- `viewmodel/OnboardingViewModel.kt` — Tier-aware download selection.
- `bridge/EngineBridge.kt` / `EngineCallback.kt` — JNI declarations and callbacks.
- `ime/HandyInputMethodService.kt` — Floating pill IME.
- `injection/` — Text injection strategy (IME → Shizuku → Clipboard).
- `service/RecordingService.kt` — Foreground service.
- `capability/` — DeviceTier, CapabilitySnapshot, ModelCapability, CompatibilityResolver, MobileRecommendations.
- `assets/mobile_recommended.json` — Curated tier-aware model subset.

---

## 🛠️ Development Commands

### Prerequisites
- Rust (latest stable)
- Android NDK r26+
- `cargo-ndk`
- Rust Android target: `aarch64-linux-android`
- Java 17+ JDK
- Android SDK (compileSdk 35)
- ADB device connected

### Quick Build & Install
```bash
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version>
cd handy-android
./gradlew clean assembleDebug
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
```

### Kotlin Checks
```bash
cd handy-android
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

### Rust Checks
```bash
cd handy-android/handy-core
cargo check
```

### Logcat Filters
```bash
adb -s <device> logcat | grep -E '(handy-core|HandyApp|EngineVM|HandyRecording|TestCommandReceiver)'
```

---

## 📡 ADB Test Automation

### Automated Flow
```bash
./handy-android/scripts/adb_test_flow.sh <device_serial> <model_id>
```

Example:
```bash
./handy-android/scripts/adb_test_flow.sh adb-00143154F001971-AbAnvz._adb-tls-connect._tcp canary-180m-flash-Q4_K_M
```

### Manual Commands
```bash
DEVICE="adb-00143154F001971-AbAnvz._adb-tls-connect._tcp"

# Install / grant / launch (skip onboarding)
adb -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEVICE" shell pm grant com.handy.app.debug android.permission.RECORD_AUDIO
adb -s "$DEVICE" shell am start -n com.handy.app.debug/com.handy.app.MainActivity --ez skip_onboarding true

# Download and activate a model
adb -s "$DEVICE" shell am broadcast -a com.handy.app.action.DOWNLOAD_MODEL -n com.handy.app.debug/.TestCommandReceiver --es model_id canary-180m-flash-Q4_K_M
adb -s "$DEVICE" shell am broadcast -a com.handy.app.action.SET_ACTIVE_MODEL -n com.handy.app.debug/.TestCommandReceiver --es model_id canary-180m-flash-Q4_K_M
```

---

## 🎨 Conventions

### Rust
- Run `cargo fmt` and `cargo clippy` before committing.
- Handle errors explicitly.
- Use `cargo check` for fast verification.

### Kotlin
- Jetpack Compose with Material 3.
- StateFlow + `collectAsState()` for reactive state.
- `stringResource(R.string.xxx)` for all user-facing strings; never hardcode.

### Commits
- Use conventional commit prefixes: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
- Focus the message on *why*, not *what*.

---

## 🔁 Important Patterns

### IME State Machine
`HandyVoiceBar` shows 6 states from `EngineViewModel`: `STATE_IDLE(0)`, `STATE_LOADING(1)`, `STATE_LISTENING(2)`, `STATE_TRANSCRIBING(3)`, `STATE_ERROR(4)`, `STATE_CONFIRM(5)`.
- Transitions use `AnimatedContent` with slide + fade.
- Buttons use `rememberPressScaleClickable` / `pressScaleClickable`.
- All text uses `stringResource()`.

### Streaming vs Periodic Transcription
- `nativeAttemptStreaming()` tries `start_stream()` first.
- Falls back to `start_periodic()` for models that don't support streaming.
- Both use the `StreamRouter` channel protocol (Feed/Finalize/Cancel).
- `drain_buffer()` feeds pre-streaming audio before connecting the router.
- `streaming_active` is set atomically in `set_stream_router()`.

### Pipeline Audio Buffer
- Pre-allocated with `reserve(262144)` (~16s at 16kHz).
- During streaming: audio goes only to the router.
- During batch: audio accumulates in the buffer for `session.run()`.

---

## 📂 Document Map

| File | Purpose |
|------|---------|
| `AGENTS.md` (this file) | Canonical project memory and orchestration. Read first. |
| `handy-android/PROGRESS.md` | Latest checkpoint, completed work, open items. |
| `handy-android/LIMPIA.md` | Clean-start checklist and ADB commands. |
| `handy-android/SPEC.md` | UI/UX specification. |
| `handy-android/ARCHITECTURE.md` | System architecture details. |
| `handy-android/BACKENDS.md` | GPU/NPU backend investigation. |

---

## ❌ Known Limitations

- Whisper Tiny struggles with long phrases containing proper nouns.
- Some Whisper English-only variants show duplicate entries.
- Moonshine Base models not yet verified on Android.
- Voxtral Small 24B (17 GB) is listed but impractical for most mobile devices.
- Gradle `buildRust` task rebuilds Rust in debug mode without `RUSTFLAGS`, overwriting a manually placed release `.so`.
- `session.run()` is blocking; `cancel_flag` discards results post-hoc but cannot interrupt C++ mid-inference.

---

## 📝 Open Items / Next Steps

1. **Re-enable Vulkan GPU backend**
   - Add a Cargo feature in `handy-core` to toggle `transcribe-cpp/vulkan`.
   - Fix include-path quoting for vendored headers.
   - Verify release build and test on a Vulkan-capable device.

2. **Investigate QNN/Hexagon NPU**
   - Evaluate `zhouwg/ggml-hexagon` fork feasibility.
   - Determine if maintenance cost is justified.

3. **IME Visual Verification**
   - Capture screenshots of IME in each state (idle, loading, recording, transcribing, confirm, error).
   - Verify 48dp touch targets and MD3 shapes.

4. **Onboarding Visual Verification**
   - Capture screenshots of each onboarding step after clean install.

5. **TestCommandReceiver Hardening (if needed)**
   - Consider additional sender verification or a custom permission if the current debug-only guard is insufficient.

---

## 🧠 Memory Rules for Future Sessions

- **Always read this file first.** Update it whenever significant state changes.
- **After every meaningful change**, update `PROGRESS.md` and this file.
- **Before asking the user for decisions**, check the Open Items section.
- **Validate changes** with `compileDebugKotlin`, `testDebugUnitTest`, and `lintDebug`.
- **Do not push or commit** without explicit user confirmation.
