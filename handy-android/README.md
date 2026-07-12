# Handy for Android

[![Android CI](https://github.com/cjpais/Handy/actions/workflows/android-ci.yml/badge.svg)](https://github.com/cjpais/Handy/actions/workflows/android-ci.yml)

Offline, on-device speech-to-text dictation engine for Android 8.0+ (API 26).

## Prerequisites

- [Rust](https://rustup.rs/) (latest stable)
- [Android NDK](https://developer.android.com/ndk) (via Android Studio or SDK Manager)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`
- Rust target: `rustup target add aarch64-linux-android x86_64-linux-android`
- [Bun](https://bun.sh/) (for desktop build, if developing the full monorepo)

## Quick Start

```bash
# Build the Rust native library
cd handy-android/handy-core
cargo ndk --target aarch64-linux-android --platform 26 -- build --release

# Build the Android app
cd ..
./gradlew assembleDebug
```

The signed APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Development

### Rust Core (handy-core/)

The Rust engine is a `cdylib` that exposes 21 JNI functions. It handles:
- AAudio microphone capture
- Energy-based Voice Activity Detection (VAD)
- Audio resampling (via rubato)
- Streaming transcription (via transcribe-cpp / GGML)
- Post-processing (filler word removal, stutter collapse)
- Model download and management (via reqwest)
- SQLite history persistence

### Kotlin App (app/)

The Android app provides:
- Input Method Service (IME) for text injection
- Jetpack Compose UI with 4 tabs (Dictation, Models, Settings, History)
- Shizuku-based power-user injection (UID 2000)
- Foreground Service for persistent recording
- Onboarding flow

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

## License

MIT
