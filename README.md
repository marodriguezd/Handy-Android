# Handy for Android

Offline, on-device speech-to-text for Android 8.0+ (API 26). No internet connection required, no data sent to the cloud.

## Features

- **Integrated IME** — Dictate in any text field by activating the Handy keyboard
- **Direct insertion** — Transcription is pasted automatically via `InputConnection.commitText()`
- **Fully offline** — All processing happens on-device
- **65+ models** — Whisper, Parakeet, Canary, Moonshine, Qwen3-ASR, and more
- **Multi-language** — Up to 99 languages depending on the model
- **Foreground Service** — Reliable recording with persistent notification and actions
- **Voice Activity Detection (VAD)** — Lightweight energy-based VAD
- **Audio normalization** — Peak normalization for better accuracy

## Requirements

- Android 8.0+ (API 26)
- Internet connection only for downloading models (works offline afterwards)

## Build & Install

### Prerequisites

- [Rust](https://rustup.rs/) (latest stable)
- [Android NDK](https://developer.android.com/ndk/) r26+ (via Android Studio SDK Manager)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`
- Rust Android target: `rustup target add aarch64-linux-android`
- Java 17+ JDK
- Android SDK (compileSdk 35)

### Build and install

```bash
cd handy-android
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version> ./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Note:** The debug APK includes the Rust library compiled in debug mode (~131 MB). For a smaller APK, see the release instructions in [handy-android/README.md](handy-android/README.md).

### Logs

```bash
adb logcat | grep -E '(handy-core|HandyApp|EngineVM|HandyRecording)'
```

## Architecture

```
handy-android/
├── handy-core/          # Rust engine (cdylib JNI)
│   ├── src/
│   │   ├── jni_bridge.rs    # 22 JNI #[no_mangle] functions
│   │   ├── audio/           # AAudio capture + resampler + VAD
│   │   ├── transcription/   # Batch inference via transcribe-cpp
│   │   └── model/           # Model catalog + download
│   └── Cargo.toml
└── app/                 # Android app (Kotlin + Jetpack Compose)
    └── src/main/java/com/handy/app/
        ├── ime/              # Input Method Service + floating UI
        ├── injection/        # Strategies: IME, Shizuku, Clipboard
        ├── viewmodel/        # EngineViewModel + SettingsViewModel
        ├── bridge/           # JNI bindings + callback interface
        └── service/          # RecordingService foreground
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full technical specification.

## Insertion Strategies

| Strategy | Priority | Description |
|---|---|---|
| **IME InputConnection** | 1st | Direct insertion via `commitText()` — most reliable |
| **Shizuku** | 2nd | Injection via `KEYCODE_PASTE` with UID 2000 permissions |
| **Clipboard** | 3rd | Copy to clipboard (fallback) |

## Available Models

| Priority | Model | Size | Languages |
|---|---|---|---|
| 🥇 | **Parakeet TDT 0.6B v3** (Q4_K_M) | 485 MB | 25 languages |
| 🥈 | **Canary 180M Flash** (Q4_K_M) | 139 MB | 4 languages + translation |
| 🥉 | **Nemotron 3.5 Streaming** (Q4_K_M) | 496 MB | 28 languages, auto-detect |
| | Whisper family | 46 MB–1.2 GB | 99 languages |
| | Canary 1B/1B Flash/Qwen 2.5B | 139 MB–1.7 GB | 4–25 languages |
| | Moonshine family | 35–296 MB | Ultra-lightweight |
| | +30 more models | Various | Various |

## Known Limitations

- No streaming transcription (batch only)
- No i18n (strings hardcoded in Spanish/English)
- Whisper Tiny has low accuracy with long phrases
- The IME may cause unexpected layout shifts in some apps
- Very large models (Voxtral 24B, 17 GB) are impractical on mobile

## License

MIT License — see [LICENSE](LICENSE).

*Handy for Android is a fork of [Handy](https://github.com/cjpais/Handy) by cjpais. The name, logo, and trademarks are not open-source. Unofficial forks must use their own branding.*
