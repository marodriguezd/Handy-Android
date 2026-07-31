# Handy Android

The Android port lives in the standalone Gradle module at the repository root. It shares the Handy product name and local Whisper/GGML model flow, but it does not build the Tauri desktop application.

## Requirements

- Python 3.10+ (used by the model catalog generation/check task)
- JDK 17
- Android SDK platform 35
- Android build-tools 35.0.0
- CMake 3.22.1
- Android NDK 27.0.12077973
- A connected Android device or emulator for instrumentation/smoke tests

The Gradle wrapper present in this working tree is the source of truth for the Gradle version. The Android source, wrapper, workflow, and documentation are intended to be versioned; do not commit `.gradle/`, `app/build/`, or `app/.cxx/`, because they are generated and ignored.

## Build and validation

The Android model storefront is generated from the desktop catalog. The three legacy `.bin` downloads that Android currently supports are kept in `scripts/android_model_catalog_overrides.json`; all other model metadata comes from `src-tauri/src/catalog/catalog.json`.

When the catalog or overrides change, regenerate the checked-in Kotlin source:

```bash
./gradlew generateModelCatalog
./gradlew checkModelCatalog
```

`checkModelCatalog` is also a `preBuild` dependency, so Android builds fail instead of silently using stale catalog data.

```bash
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

The GitHub Actions workflow at `.github/workflows/android.yml` installs the pinned SDK/CMake/NDK versions and runs all four checks on Android-related changes.

## Install a debug build

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.handy.android/.MainActivity
```

For a physical smoke test, grant only the permissions needed for the test device and verify the onboarding flow. The app requires microphone access for recording, notification access for Android 13+ foreground services, overlay access for the floating button/subtitles, and the user-enabled accessibility service for cross-app typing.

## Models

Models are kept in the app-private `files/models` directory. The UI can download the supported remote models or import a local GGML `.bin` file through the Android document picker. Downloads and imports are written to a temporary `.part` file, hashed with SHA-256, loaded through Whisper/GGML, and moved into place only after validation. A local `<model>.bin.sha256` sidecar prevents later silent use of modified content; it does not authenticate remote provenance.

The runtime currently uses the portable CPU GGML backend. GPU acceleration and optimized ARM dispatch are deliberately disabled until device coverage and packaging are validated.

## Current scope

Implemented flows include:

- runtime permissions and guided accessibility/overlay setup;
- floating foreground recording service;
- accessibility text insertion;
- Android speech recognition service;
- Handy input method service;
- local model download/import/selection;
- audio-file transcription through `ACTION_VIEW`/`ACTION_SEND`;
- local periodic live-subtitle transcription;
- native Whisper/GGML inference through JNI.

A signed release artifact, Play distribution configuration, authenticated remote model checksums, and full instrumentation coverage are still follow-up work. `assembleRelease` verifies compilation and R8/JNI configuration, but signing is intentionally left to the release environment.
