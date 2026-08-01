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

### ARM64 host toolchain notes

- **SIGILL (Exit code 132) on ARM64 Linux hosts**:
  When running builds on ARM64/`aarch64` Linux host environments, prepackaged Android SDK `cmake` and `ninja` binaries may fail with illegal instruction errors. Resolve this by installing `ninja-build` and symlinking system binaries:
  ```bash
  apt-get install -y ninja-build
  ln -sf /usr/bin/cmake $ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake
  ln -sf /usr/bin/ninja $ANDROID_SDK_ROOT/cmake/3.22.1/bin/ninja
  ln -sf $ANDROID_SDK_ROOT/build-tools/35.0.0/aapt2 /usr/bin/aapt2
  ```

- **JVM-only Test Harness Execution**:
  Canonical verification tasks (`./gradlew checkModelCatalog testDebugUnitTest lintDebug`) run on the host JVM and do not require native NDK compilation when using the `IWhisperEngine` interface abstraction for unit testing.

## Private Telegram debug-build bot

The repository also contains an optional, private build bridge:

- `.github/workflows/telegram-poll.yml` polls Telegram every five minutes;
- `scripts/telegram_build_poller.py` accepts only `/build` from one configured private chat;
- `.github/workflows/telegram-build.yml` always checks out `main`, runs the debug validation/build, and sends `app-debug.apk` back through Telegram.

This design does not compile on the mobile Linux environment and does not accept a branch, Gradle command, or uploaded source from Telegram. To activate it, create a bot with BotFather, send it one message from the intended private chat, determine that chat's numeric ID, and add these **repository Actions secrets**:

- `TELEGRAM_BOT_TOKEN`: the token from BotFather;
- `TELEGRAM_ALLOWED_CHAT_ID`: the numeric ID of the one authorized private chat.

Then enable Actions for the repository and run `Telegram build poller` once manually. Send `/build` to the bot. The scheduled poll normally starts the build within five minutes; the bot sends a success APK or a failure/log link. Telegram's standard Bot API has an upload-size limit, so oversized APKs are left as workflow artifacts and the bot sends the Actions run link instead. Never commit either secret or paste the bot token into source files or issues.

### Find the private chat ID locally

If you do not know `TELEGRAM_ALLOWED_CHAT_ID`, run the read-only diagnostic helper from a machine with Internet access:

```bash
python3 scripts/telegram_inspect.py
```

The helper asks for `TELEGRAM_BOT_TOKEN` without echoing it, reads pending messages through Telegram's `getUpdates`, and prints the numeric `chat_id`. It does not send messages, trigger builds, delete webhooks, or save the token. Send `/start` to the bot before running it. Copy only the numeric ID of the private chat into the `TELEGRAM_ALLOWED_CHAT_ID` GitHub secret. Do not share the full output if it contains your name or messages.

If it reports `409 Conflict`, another poller or webhook is already consuming updates. Temporarily disable the scheduled `Telegram build poller` workflow, then retry the inspector. Do not run two Telegram pollers at the same time.

The GitHub token used by the poller is the short-lived workflow token (`github.token`) and is limited to `actions: write` and `contents: read`.

For a production setup with immediate responses, move the poller to a small always-on VPS and use long polling; the current scheduled GitHub Actions version is intentionally inexpensive, but has up to five minutes of latency.

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
