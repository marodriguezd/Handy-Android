# Project: Quality Assurance Ecosystem for com.handy.android

## Architecture
- **Target Package**: `com.handy.android` (Android Gradle module under `app/`)
- **Layers**:
  - Audio Pipeline: `AudioBuffer`, `AudioRecorder`, `AudioFileDecoder` (MediaCodec / resampler)
  - Model Management: `ModelCatalog`, `ModelDownloader` (HTTPS download, `.part` file), `ModelValidator` (SHA-256 sidecars)
  - Inference Abstraction: `IWhisperEngine` interface, `WhisperLib` (JNI native wrapper), `TranscriptionEngine`
  - System Integration: `SettingsManager`, `PermissionState` / `PermissionChecker`, Android services (`FloatingButtonService`, `AutoTypeAccessibilityService`, `HandyInputMethodService`, `LiveSubtitleService`, `VoiceRecognitionService`)
- **Shared Interfaces**:
  - `IWhisperEngine`: Decouples native `libhandy_whisper_jni.so` loading from host JVM test runners.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Test Infra & Gradle Lint Setup | Gradle test dependencies, strict lint rules (`warningsAsErrors`), `checkModelCatalog` task binding | M1 | survey |
| 2 | Whisper Engine Interface Abstraction | `IWhisperEngine` interface, refactored `WhisperLib`, `ModelValidator`, and `TranscriptionEngine` | M1 | survey |
| 3 | ModelDownloader Unit Test Suite | HTTPS validation, redirect security, HTTP errors, `.part` file lifecycle, progress callbacks, SHA-256 replacement | M2 | survey |
| 4 | TranscriptionEngine Unit Test Suite | Model extension check, sidecar requirement, active model fallback, `NoModelException`, `ModelValidationException`, mock delegation | M2 | survey |
| 5 | SettingsManager Unit Test Suite | Path traversal check, model setting hash validation, thread count normalization, custom words serialization, API key Base64 | M2 | survey |
| 6 | PermissionChecker Unit Test Suite | `PermissionState.ready` evaluation, SDK 33+ `POST_NOTIFICATIONS`, colon-separated accessibility service string parsing | M2 | survey |
| 7 | AudioFileDecoder Resampler Test Suite | Linear interpolation sample rate resampler (44.1k/48k -> 16k), mono downmixing, empty/same rate handling | M3 | survey |
| 8 | AudioBuffer Concurrency Test Suite | Multi-threaded concurrent `append` and `drain` thread-safety, zero-sample drain, snapshot retention | M3 | survey |
| 9 | Autonomous Gauntlet & E2E Validation | Comprehensive execution of `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew checkModelCatalog`, `./gradlew assembleDebug` | M4 | survey |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Test Infra & JNI Decoupling | `app/build.gradle.kts` dependencies & lint settings, `IWhisperEngine` interface creation & component refactoring | none | PLANNED |
| 2 | M2: Core Business Logic Test Gauntlet | `ModelDownloaderTest`, `TranscriptionEngineTest`, `SettingsManagerTest`, `PermissionCheckerTest` | M1 | PLANNED |
| 3 | M3: Audio Pipeline & Concurrency Gauntlet | `AudioFileDecoderTest`, `AudioBufferConcurrencyTest` | M1 | PLANNED |
| 4 | M4: E2E Verification & Gauntlet Hardening | Validation of canonical commands (`testDebugUnitTest`, `lintDebug`, `checkModelCatalog`, `assembleDebug`), zero lint warnings, fast JVM execution | M1, M2, M3 | PLANNED |

## Interface Contracts
### `WhisperLib` ↔ `TranscriptionEngine` / `ModelValidator`
- Interface: `IWhisperEngine : AutoCloseable`
- Signatures:
  - `fun init(modelPath: String): Boolean`
  - `fun transcribe(audioData: FloatArray, numThreads: Int = 4, translate: Boolean = false, language: String = "auto"): String`
- Behavior: `TranscriptionEngine` and `ModelValidator` accept an `engineFactory: () -> IWhisperEngine = { WhisperLib() }`.

## Code Layout
- Source files: `app/src/main/java/com/handy/android/`
- Test files: `app/src/test/java/com/handy/android/`
- Build script: `app/build.gradle.kts`
