# Changelog

## v1.1.0-alpha2 (Sprint 7 — UI + IME Bubble)

### New Features
- **IME Floating Bubble Overlay**: Complete rewrite of HandyInputMethodService as a compact 56dp pill matching the PC desktop overlay. States: Idle (pulsing mic), Recording (9-bar waveform + partial text + stop), Confirm (text + insert/retry), Error (error + retry). Uses AccentPink #E85D75.
- **ModelCard Layout Fix**: Restructured from Row-based to Column-based with 3 clear rows. Language chip truncation prevents overflow.
- **Onboarding Default Model**: Changed from Whisper Small to Parakeet TDT 0.6B v3 (485 MB).
- **Cancel Behavior**: Shows "Download canceled" with retry button instead of "Model Ready".

### Bug Fixes
- Fixed retry download after cancel (race condition in OnboardingViewModel)
- Fixed ModelCard UI misalignment (languages, sizes, buttons)
- Fixed onboarding model description string

### Documentation
- Updated AGENTS.md, SPEC.md, ARCHITECTURE.md with IME bubble and UI fixes

## v1.0.0-alpha1 (Sprint 6)

### New Features
- **Idle Model Unloading**: The Whisper model is automatically unloaded after a configurable idle timeout (default 30s), freeing ~500MB of RAM
- **OOM Protection**: Model size limits (1.5GB max), audio buffer cap (19.2MB), and memory pressure callbacks prevent out-of-memory crashes
- **Crash Reporting**: Panic-safe JNI wrappers (`catch_unwind` around all 21 entry points) plus Sentry SDK integration
- **Edge Case Handling**: Audio focus handling (incoming calls pause recording), Bluetooth device notifications, screen rotation state preservation
- **Battery Optimization**: Doze exemption request, `ComponentCallbacks2.onTrimMemory` integration
- **Performance Benchmarks**: Latency instrumentation for partial and final transcription results
- **Shizuku Auto-Reconnect**: Exponential backoff with 1s→2s→...→30s max delay

### Infrastructure
- Version catalog (`libs.versions.toml`) for centralized dependency management
- Release signing configuration via environment variables
- BuildConfig integration for Sentry DSN
- Debug application ID suffix (`.debug`) for parallel installation

### Documentation
- Android-specific README with build instructions
- CHANGELOG for release tracking

### Known Limitations
- Only arm64-v8a architecture supported
- No ONNX runtime backend (Parakeet/Moonshine) – deferred to post-MVP
- No x86_64-linux-android emulator build (script ready but untested)
- Battery optimization exemption must be manually enabled in Settings
