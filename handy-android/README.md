# Handy for Android

**Offline, on-device speech-to-text for Android 8.0+ (API 26).**  
No internet connection required, no data sent to the cloud. Total privacy.

---

## Features

- **Integrated IME** — Dictate in any text field using the Handy keyboard with a floating pill UI
- **Smart insertion** — 3 cascading strategies: IME `InputConnection` → Shizuku `KEYCODE_PASTE` → Clipboard
- **Fully offline** — All processing (VAD + transcription) happens on-device
- **65+ models** — Whisper, Parakeet, Canary, Moonshine, Nemotron, Qwen3-ASR, Cohere, Granite, and more
- **Multi-language** — Up to 99 languages depending on the model
- **Device-aware** — Automatic hardware classification (LOW/MID/HIGH/FLAGSHIP/TABLET) with tier-based recommendations
- **Material Design 3** — Adaptive UI with NavigationRail on tablets, PC-aligned dark theme
- **Foreground Service** — Reliable recording with persistent notification and actions (Stop, Switch Keyboard)
- **Energy VAD** — Lightweight voice activity detection with fast noise adaptation
- **Audio normalization** — Peak normalization for better transcription accuracy

---

## Requirements

- Android 8.0+ (API 26)
- Internet connection only for downloading models (works offline afterwards)
- ~500 MB free storage for lightweight models

---

## Quick Start

### Prerequisites

- [Rust](https://rustup.rs/) (latest stable)
- [Android NDK](https://developer.android.com/ndk/) r26+ (via Android Studio SDK Manager)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`
- Rust Android target: `rustup target add aarch64-linux-android`
- Java 17+ JDK
- Android SDK (compileSdk 35)
- A device with USB debugging enabled

### Build and install

```bash
cd handy-android
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version> ./gradlew assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Note:** The debug APK includes the Rust library compiled in debug mode (~131 MB). For a release APK (~6 MB), see [Building for Release](#building-for-release).

### Logs

```bash
adb logcat | grep -E '(handy-core|HandyApp|EngineVM|HandyRecording|TestCommandReceiver)'
```

---

## Architecture

```
handy-android/
├── handy-core/                    # Rust engine (cdylib JNI)
│   ├── src/
│   │   ├── jni_bridge.rs          # 22 JNI #[no_mangle] functions
│   │   ├── audio/                 # AAudio capture + resampler (rubato) + energy VAD
│   │   ├── transcription/         # Batch inference via transcribe-cpp (GGML)
│   │   └── model/                 # Model catalog + download (GGUF)
│   └── Cargo.toml
├── app/                           # Android app (Kotlin + Jetpack Compose)
│   └── src/main/java/com/handy/app/
│       ├── ime/                   # Input Method Service + floating pill UI
│       ├── injection/             # Insertion strategies: IME → Shizuku → Clipboard
│       ├── viewmodel/             # EngineViewModel + SettingsViewModel + ModelsViewModel
│       ├── bridge/                # JNI bindings + callback interface
│       ├── capability/            # DeviceTier, CapabilitySnapshot, CompatibilityResolver
│       ├── service/               # RecordingService foreground
│       └── ui/                    # Compose screens (Settings, Models, History, Onboarding)
│           └── theme/             # MD3 Color, Type, Shape tokens
├── scripts/
│   ├── build-rust.sh              # Rust compilation with --vulkan support
│   └── adb_test_flow.sh           # Automated ADB flow (build → install → download → activate)
```

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full technical specification.

---

## Text Insertion Strategies

| Strategy | Priority | Description |
|---|---|---|
| **IME InputConnection** | 1st | Direct insertion via `commitText()` — most reliable |
| **Shizuku** | 2nd | Injection via `KEYCODE_PASTE` with UID 2000 permissions (requires Shizuku runtime) |
| **Clipboard** | 3rd | Copy to clipboard with "Handy Dictation" label (automatic fallback) |

The `InjectorRouter` automatically selects the best available strategy and cascades down on failure.

---

## Available Models

65+ models in the full catalog. The [Capability-Aware](./ARCHITECTURE.md#capability-aware-model-catalog-sprint-14) system classifies the device and recommends models based on available RAM.

### Tier-Based Recommendations

| Tier | Primary Model | Alternatives |
|---|---|---|
| **LOW** (≤1.5 GB) | Whisper Base | Whisper Tiny, Moonshine Streaming Tiny, MedASR |
| **MID** (≤3.5 GB) | Nemotron 3.5 ASR Streaming | Canary 180M Flash, Parakeet TDT 0.6B, Whisper Medium, Whisper Small |
| **HIGH** (≤6.5 GB) | Whisper Large V3 Turbo | Qwen3-ASR 1.7B, Canary 1B V2, Whisper Large V3 |
| **FLAGSHIP** (≤12.5 GB) | Whisper Large V3 | Granite Speech 4.1 2B+, Canary Qwen 2.5B |
| **TABLET** (>12.5 GB) | Cohere Transcribe | Granite Speech 4.1 2B, Granite 4.0 1B Speech |

### Popular Lightweight Models

| Model | Size | Languages | Best for |
|---|---|---|---|
| Canary 180M Flash (Q4_K_M) | 139 MB | EN/ES/FR/DE + translation | MID+ |
| Parakeet TDT 0.6B V3 (Q4_K_M) | 485 MB | 25 languages | HIGH+ |
| Nemotron 3.5 Streaming (Q4_K_M) | 496 MB | 28 languages + auto-detect | HIGH+ |

---

## Feature Flags & Gating

The system includes protections against OOM and poor user experience:

| Feature | Description |
|---|---|
| **DeviceTier** | Automatic hardware classification into 5 bands based on RAM |
| **HeavyGate** | XXL models (Voxtral 24B/4B/3B) require explicit checkbox consent |
| **Experimental Gate** | Unstable models (Moonshine Base monolingual) hidden by default |
| **Compatibility Badges** | Visual chips: HEAVY_GATE, EXCEEDS_RAM, LARGE_HEAP, EXPERIMENTAL |
| **Mobile Recommendations** | 19 curated models organized by tier with UI promotion |

---

## ADB Test Automation

For automated testing via ADB (debug builds only):

```bash
# Full flow: build → install → grant → launch → download → activate
./scripts/adb_test_flow.sh <device_serial> <model_id>

# Example:
./scripts/adb_test_flow.sh adb-00143154F001971-AbAnvz._adb-tls-connect._tcp canary-180m-flash-Q4_K_M
```

### Available Hooks (Debug)

| Action | Command |
|---|---|
| Skip onboarding | `am start ... --ez skip_onboarding true` |
| Download model | `am broadcast -a com.handy.app.action.DOWNLOAD_MODEL --es model_id <id>` |
| Activate model | `am broadcast -a com.handy.app.action.SET_ACTIVE_MODEL --es model_id <id>` |

---

## Building for Release

```bash
export HANDY_KEYSTORE_PATH=../handy-release.keystore
export HANDY_KEYSTORE_PASSWORD=<password>
export HANDY_KEY_ALIAS=handy
export HANDY_KEY_PASSWORD=<password>
export SENTRY_DSN=<your-sentry-dsn>

./gradlew assembleRelease bundleRelease
```

The release build compiles Rust in `--release` mode (~6 MB `.so`) and enables the Vulkan backend.  
See [BUILD.md](./BUILD.md) for detailed instructions.

---

## Known Limitations

- No native streaming transcription (batch only via `session.run()`)
- Some Whisper English-only variants show duplicate entries in the catalog
- Whisper Tiny has low accuracy with long phrases containing proper nouns
- `session.run()` is blocking; `cancel_flag` discards results post-hoc but cannot interrupt C++ mid-inference
- Moonshine Base not yet verified on Android
- Voxtral Small 24B (17 GB) is listed but impractical for most mobile devices
- The IME may cause minor layout shifts in some host apps

---

## License

MIT License — see [LICENSE](../LICENSE).

*Handy for Android is a fork of [Handy](https://github.com/cjpais/Handy) by cjpais, adapted exclusively for Android devices. The name, logo, and trademarks are not open-source. Unofficial forks must use their own branding.*
