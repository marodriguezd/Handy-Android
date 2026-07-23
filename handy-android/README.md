# Handy for Android

Offline, on-device speech-to-text engine and application for Android (8.0+ / API 26).

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](../LICENSE)
[![Android MinSDK](https://img.shields.io/badge/Android-8.0%2B%20%28API%2026%29-brightgreen.svg)](https://developer.android.com)

---

## Features

- **Offline Inference**: On-device VAD and speech recognition using `handy-core` (Rust JNI with `whisper.cpp` / GGML).
- **IME Custom Keyboard**: Floating pill overlay (`HandyInputMethodService`) for direct dictation in any app.
- **System Speech Service**: Implements `android.speech.RecognitionService` for apps triggering `RECOGNIZE_SPEECH` intents.
- **Cascading Text Insertion**: Automatic fallbacks: IME `InputConnection` → Shizuku (`KEYCODE_PASTE`) → Clipboard.
- **Phonetic Dictionary**: Soundex + Levenshtein correction (`WordCorrector`) with filler word filtering and custom user terms.
- **LLM Post-Processing**: Optional text formatting, grammar correction, or translation via local (Ollama) or cloud (OpenAI, MiniMax, Cohere) APIs.
- **Hardware-Aware Model Selection**: Categorizes device performance into tiers (LOW to TABLET) to recommend compatible GGUF models.
- **Material Design 3**: Responsive UI adapting across phone navigation bars and tablet side rails.

---

## Requirements & Building

### Prerequisites

- Java 17+ JDK
- Android SDK (compileSdk 35) & NDK r27+
- Rust stable with `aarch64-linux-android` target
- `cargo-ndk` (`cargo install cargo-ndk`)

### Quick Start

```bash
# Build Rust native library and debug APK
./gradlew :app:assembleDebug

# Install on a connected Android device
./gradlew :app:installDebug
```

For release configuration, Sentry setup, and APK optimization, refer to [BUILD.md](./BUILD.md).

---

## Architecture Overview

```
handy-android/
├── handy-core/                # Rust cdylib (Whisper.cpp, Silero VAD, JNI bindings)
│   ├── src/
│   │   ├── audio/             # AAudio capture, Rubato resampler, Energy VAD
│   │   ├── transcription/     # Whisper batch inference
│   │   └── model/             # Model catalog & GGUF download logic
│   └── Cargo.toml
└── app/                       # Android App (Kotlin & Jetpack Compose)
    └── src/main/java/com/handy/app/
        ├── audio/             # WAV recording repository & storage backend
        ├── bridge/            # Native JNI binding interface
        ├── corrector/         # Phonetic dictionary & Levenshtein matching
        ├── ime/               # Input Method Service & floating overlay UI
        ├── injection/         # Cascading text injection router
        ├── postprocessing/    # LLM API client & prompt manager
        ├── service/           # Voice recognition service & floating overlay
        └── ui/                # Compose screens (Catalog, History, Settings)
```

See [ARCHITECTURE.md](./ARCHITECTURE.md) for full technical documentation.

---

## License

MIT License — see [LICENSE](../LICENSE).
