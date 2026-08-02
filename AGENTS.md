# AGENTS.md

This file provides guidance to AI coding assistants working with code in this repository.

## Android Port

The repository also contains a standalone Android Gradle project at the root (`app/`, `gradle/`, `gradlew`). It is the Android port of Handy and is separate from the Tauri desktop application under `src/` and `src-tauri/`.

Before changing Android code, read [`ANDROID.md`](ANDROID.md), [`spec.md`](spec.md), [`plan.md`](plan.md), and [`TEST_HANDY.txt`](TEST_HANDY.txt). The Android package is `com.handy.android`; its local inference bridge is `app/src/main/cpp/native-lib.cpp`, and its model/audio flow is implemented in `app/src/main/java/com/handy/android/`.

Canonical Android validation commands:

```bash
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew generateModelCatalog
./gradlew checkModelCatalog
```

The Android model storefront is generated from `src-tauri/src/catalog/catalog.json` by `scripts/generate_android_model_catalog.py`.

When a stable Compose Material3 1.5.x is released, the Expressive migration (AUDIT.md §2.3) becomes viable: CI detects it automatically via `scripts/check_material3_stable.py` (fails on stable ≥ 1.5.0; run with `--selftest` to validate offline). Keep the three Android-compatible legacy `.bin` artifacts in `scripts/android_model_catalog_overrides.json`; do not edit `ModelCatalog.kt` manually. `checkModelCatalog` is part of `preBuild` and must fail when the generated source is stale. The current catalog is limited to 1.2 billion parameters and only the three Whisper `.bin` overrides are downloadable by the current JNI backend; other entries are displayed as coming soon.

Android generated outputs (`.gradle/`, `app/build/`, `app/.cxx/`) must remain ignored. Work directly on the current Android module. Do not modify desktop Tauri behavior to solve an Android-only issue unless explicitly requested.

### Multi-Architecture & Gauntlet Principles

- **Multi-ABI Parity**: The Android module supports both ARM (mobile `arm64-v8a`, `armeabi-v7a`) and x86 (laptop emulators `x86_64`, `x86`). Always keep `abiFilters` aligned across all 4 architectures in `app/build.gradle.kts`.
- **The Gauntlet Validation Rule (Uncle Bob's Test Harness Strategy)**: AI assistants working on code must adhere to the Gauntlet Strategy: do not request line-by-line manual code reviews. Instead, rely on automated test gates (`./gradlew checkModelCatalog testDebugUnitTest lintDebug`) and present empirical command/task execution evidence (`BUILD SUCCESSFUL`) before declaring any task complete.
- **Mobile Thermal & CPU Protection**: When executing local verification on mobile host devices, ensure unit test tasks (`testDebugUnitTest`) do not trigger heavy native C++/Rust re-compilations. Reserve full APK/AAB packaging for CI/CD pipelines (GitHub Actions).
- **AAPT2 Host Override**: For ARM64 Linux environments, ensure `/usr/bin/aapt2` points to the native SDK 35 binary (`ln -sf $ANDROID_SDK_ROOT/build-tools/35.0.0/aapt2 /usr/bin/aapt2`) and `android.aapt2FromMavenOverride=/usr/bin/aapt2` is set in `gradle.properties` to avoid ARSC table load failures on `android-35/android.jar`.
- **AAPT2 on pure ARM64 hosts**: the official SDK `aapt2` is an x86-64 binary and crashes with `Illegal instruction` (exit 132) on ARM64-only hosts (no binfmt_misc/x86-64 emulation). If the machine has `qemu-x86_64` and the amd64 cross libc (`libc6-amd64-cross`, usually under `/usr/x86_64-linux-gnu`), replace the `/usr/bin/aapt2` symlink with a wrapper script:
  ```bash
  #!/bin/bash
  exec qemu-x86_64 -L /usr/x86_64-linux-gnu /root/android-sdk/build-tools/35.0.0/aapt2 "$@"
  ```
  CI (`android.yml`) runs on x86_64 runners and does not need this workaround.
- **Accessibility Text Actions**: When triggering text paste in `AutoTypeAccessibilityService`, use `node.performAction(AccessibilityNodeInfo.ACTION_PASTE)` on the target `AccessibilityNodeInfo`. Do not use `performGlobalAction` with invalid paste constants.

## Desktop/Tauri Reference Commands (not the active Android workflow)

The commands in this section document the existing desktop application only. They are not required for Android development and should not be run for Android-only tasks.

**Prerequisites:**

- [Rust](https://rustup.rs/) (latest stable)
- [Bun](https://bun.sh/) package manager

**Desktop/Tauri reference development:**

```bash
# Install dependencies
bun install

# Run in development mode
bun run tauri dev
# If cmake error on macOS:
CMAKE_POLICY_VERSION_MINIMUM=3.5 bun run tauri dev

# Build for production
bun run tauri build

# Frontend only development
bun run dev        # Start Vite dev server
bun run build      # Build frontend (TypeScript + Vite)
bun run preview    # Preview built frontend
```

**Linting and Formatting (run before committing):**

```bash
bun run lint              # ESLint for frontend
bun run lint:fix          # ESLint with auto-fix
bun run format            # Prettier + cargo fmt
bun run format:check      # Check formatting without changes
bun run format:frontend   # Prettier only
bun run format:backend    # cargo fmt only
```

**Model Setup (Required for Development):**

```bash
mkdir -p src-tauri/resources/models
curl -o src-tauri/resources/models/silero_vad_v4.onnx https://blob.handy.computer/silero_vad_v4.onnx
```

For detailed platform-specific build setup, see [BUILD.md](BUILD.md).

## Android Handoff & Execution State

The Android port contains all foundational features (local Whisper/GGML JNI, storefront, SQLite history, sound/haptics, post-processing) plus the MD3/Wispr Flow redesign and the audit fixes F1–F8. The canonical status and open items live in [`AUDIT.md`](AUDIT.md); the specification is in [`spec.md`](spec.md) and the task list in [`plan.md`](plan.md). New agents should read [`AUDIT.md`](AUDIT.md) first, then [`AGENTS.md`](AGENTS.md), [`spec.md`](spec.md) and [`plan.md`](plan.md), and confirm the checkout state with `git status --short` and `git log -1 --oneline`.

## Architecture Overview

The active development target in this repository is the Handy Android port, built as the standalone Gradle/Kotlin/C++ module described above. The existing Tauri 2.x desktop application (`src/` and `src-tauri/`) is retained as product and architecture reference only; Android work must not depend on running or rebuilding Tauri.

### Backend Structure (src-tauri/src/)

The repository also contains the Android port described in the `Android Port` section above. It is intentionally a separate Gradle/Kotlin/C++ build and is not initialized by the Tauri runtime.

- `lib.rs` - Main entry point, Tauri setup, manager initialization
- `managers/` - Core business logic:
  - `audio.rs` - Audio recording and device management
  - `model.rs` - Model downloading and management
  - `transcription.rs` - Speech-to-text processing pipeline
  - `history.rs` - Transcription history storage
- `audio_toolkit/` - Low-level audio processing:
  - `audio/` - Device enumeration, recording, resampling
  - `vad/` - Voice Activity Detection (Silero VAD)
- `commands/` - Tauri command handlers for frontend communication
- `cli.rs` - CLI argument definitions (clap derive)
- `shortcut.rs` - Global keyboard shortcut handling
- `settings.rs` - Application settings management
- `overlay.rs` - Recording overlay window (platform-specific)
- `signal_handle.rs` - `send_transcription_input()` reusable function
- `utils.rs` - Platform detection helpers

### Frontend Structure (src/)

- `App.tsx` - Main component with onboarding flow
- `components/` - React UI components:
  - `settings/` - Settings UI
  - `model-selector/` - Model management interface
  - `onboarding/` - First-run experience
  - `overlay/` - Recording overlay UI
  - `update-checker/` - App update notifications
  - `shared/`, `ui/`, `icons/`, `footer/` - Shared components
- `hooks/useSettings.ts` - Settings state management hook
- `stores/settingsStore.ts` - Zustand store for settings
- `bindings.ts` - Auto-generated Tauri type bindings (via tauri-specta)
- `overlay/` - Recording overlay window entry point
- `lib/types.ts` - Shared TypeScript type definitions

### Key Architecture Patterns

**Manager Pattern:** Core functionality organized into managers (Audio, Model, Transcription) initialized at startup and managed via Tauri state.

**Command-Event Architecture:** Frontend → Backend via Tauri commands; Backend → Frontend via events.

**Pipeline Processing:** Audio → VAD → Whisper/Parakeet → Text output → Clipboard/Paste

**State Flow:** Zustand → Tauri Command → Rust State → Persistence (tauri-plugin-store)

### Technology Stack

**Core Libraries:**

- `transcribe-cpp` - Local Whisper-family inference (GGML/GGUF) with GPU acceleration
- `transcribe-rs` - ONNX speech recognition (Parakeet, Moonshine, SenseVoice, etc.)
- `cpal` - Cross-platform audio I/O
- `vad-rs` - Voice Activity Detection
- `rdev` - Global keyboard shortcuts
- `rubato` - Audio resampling
- `rodio` - Audio playback for feedback sounds

### Application Flow

1. **Initialization:** App starts minimized to tray, loads settings, initializes managers
2. **Model Setup:** First-run downloads preferred Whisper model (Small/Medium/Turbo/Large)
3. **Recording:** Global shortcut triggers audio recording with VAD filtering
4. **Processing:** Audio sent to Whisper model for transcription
5. **Output:** Text pasted to active application via system clipboard

### Settings System

Settings are stored using Tauri's store plugin with reactive updates:

- Keyboard shortcuts (configurable, supports push-to-talk)
- Audio devices (microphone/output selection)
- Model preferences (Small/Medium/Turbo/Large Whisper variants)
- Audio feedback and translation options

### Single Instance Architecture

The app enforces single instance behavior — launching when already running brings the settings window to front rather than creating a new process. Remote control flags (`--toggle-transcription`, etc.) work by launching a second instance that sends args to the running instance via `tauri_plugin_single_instance`, then exits.

## Internationalization (i18n)

All desktop/web user-facing strings must use i18next translations. ESLint enforces this (no hardcoded strings in JSX). Android currently has hardcoded Kotlin UI/service strings; Android internationalization is a tracked follow-up and should use Android string resources when implemented.

**Adding new text:**

1. Add key to `src/i18n/locales/en/translation.json`
2. Use in component: `const { t } = useTranslation(); t('key.path')`

**File structure:**

```
src/i18n/
├── index.ts           # i18n setup
├── languages.ts       # Language metadata
└── locales/
    ├── en/translation.json  # English (source)
    ├── de/, es/, fr/, ja/, ru/, zh/, ...
    └── ...
```

For translation contribution guidelines, see [CONTRIBUTING_TRANSLATIONS.md](CONTRIBUTING_TRANSLATIONS.md).

## Code Style

**Rust:**

- Run `cargo fmt` and `cargo clippy` before committing
- Handle errors explicitly (avoid unwrap in production)
- Use descriptive names, add doc comments for public APIs

**TypeScript/React:**

- Strict TypeScript, avoid `any` types
- Functional components with hooks
- Tailwind CSS for styling
- Path aliases: `@/` → `./src/`

## CLI Parameters

Handy supports command-line parameters on all platforms for integration with scripts, window managers, and autostart configurations.

**Implementation:** `cli.rs` (definitions), `main.rs` (parsing), `lib.rs` (applying), `signal_handle.rs` (shared logic)

| Flag                     | Description                                                |
| ------------------------ | ---------------------------------------------------------- |
| `--toggle-transcription` | Toggle recording on/off on a running instance              |
| `--toggle-post-process`  | Toggle recording with post-processing on/off               |
| `--cancel`               | Cancel the current operation on a running instance         |
| `--start-hidden`         | Launch without showing the main window (tray icon visible) |
| `--no-tray`              | Launch without system tray (closing window quits the app)  |
| `--debug`                | Enable debug mode with verbose (Trace) logging             |

**Key design decisions:**

- CLI flags are runtime-only overrides — they do NOT modify persisted settings
- Remote control flags work via `tauri_plugin_single_instance`: second instance sends args, then exits
- `send_transcription_input()` in `signal_handle.rs` is shared between signal handlers and CLI

## Debug Mode

Access debug features: `Cmd+Shift+D` (macOS) or `Ctrl+Shift+D` (Windows/Linux)

## Platform Notes

- **macOS**: Metal acceleration, accessibility permissions required for keyboard shortcuts
- **Windows**: Vulkan acceleration, code signing
- **Linux**: OpenBLAS + Vulkan, limited Wayland support, overlay uses GTK layer shell (disable with `HANDY_NO_GTK_LAYER_SHELL=1`)

## Troubleshooting

See the [Troubleshooting](README.md#troubleshooting) section in README.md.

## GitHub workflow for AI coding assistants

**MANDATORY. Before opening any PR, issue, or discussion in this repo: you MUST read the relevant template file and follow it strictly.** That includes sections that look "ceremonial" — checklists, AI Assistance disclosures, "Human Written Description". A generic Summary/Test-plan layout is not acceptable.

- **Opening a PR:** Read [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md). Every section listed there is mandatory. If a section requires a human-written paragraph (e.g. "Human Written Description"), leave a clear TODO placeholder and ask the human contributor to fill it in — do not invent their voice.
- **Opening an issue:** Read [`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE/). Blank issues are disabled; pick the right template (`bug_report.md` for bugs). Feature requests do not belong in issues — they go to [Discussions](https://github.com/cjpais/Handy/discussions) (see `.github/ISSUE_TEMPLATE/config.yml`).
- **Proposing a feature:** Handy is under a feature freeze. New features require community support gathered in [Discussions](https://github.com/cjpais/Handy/discussions) before any PR is opened — see the PR template's "Community Feedback" section.
- **Translations:** Follow [CONTRIBUTING_TRANSLATIONS.md](CONTRIBUTING_TRANSLATIONS.md).
- **Full contributor workflow:** [CONTRIBUTING.md](CONTRIBUTING.md).

**Commits:** Use conventional commit prefixes (`feat:`, `fix:`, `docs:`, `refactor:`, `chore:`). Focus the message on _why_, not _what_.
