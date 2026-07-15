# Handy para Android

[![Build](https://github.com/anomalyco/Handy-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/anomalyco/Handy-Android/actions/workflows/android-ci.yml)

Offline, on-device speech-to-text dictation engine for Android 8.0+ (API 26).

## Prerequisites

- [Rust](https://rustup.rs/) (latest stable)
- [Android NDK](https://developer.android.com/ndk) r26+ (via Android Studio SDK Manager)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`
- Rust target: `rustup target add aarch64-linux-android`
- Java 17+ JDK
- Android SDK (compileSdk 35)
- A device connected via ADB

## Quick Start

```bash
cd handy-android
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version> ./gradlew assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Development

### Rust Core (`handy-core/`)

The Rust engine is a `cdylib` exposing 21 JNI functions. It handles:
- AAudio microphone capture
- Energy-based Voice Activity Detection (VAD)
- Audio resampling (via rubato)
- Batch transcription (via transcribe-cpp / GGML)
- Post-processing (filler word removal, stutter collapse)
- Model download and management (via reqwest)

### Kotlin App (`app/`)

The Android app provides:
- Input Method Service (IME) with floating pill UI
- Automatic text insertion via `InputConnection.commitText()`
- Jetpack Compose UI (4 tabs: General, Models, History, About)
- Shizuku power-user injection (UID 2000, optional)
- Foreground Service for persistent recording
- Onboarding flow with model download

### Architecture

See [ARCHITECTURE.md](../ARCHITECTURE.md) for the full technical specification.

## Building for Release

```bash
export HANDY_KEYSTORE_PATH=../handy-release.keystore
export HANDY_KEYSTORE_PASSWORD=<password>
export HANDY_KEY_ALIAS=handy
export HANDY_KEY_PASSWORD=<password>
./gradlew assembleRelease bundleRelease
```

The release build compiles Rust in `--release` mode (~6 MB `.so`).

## License

MIT
