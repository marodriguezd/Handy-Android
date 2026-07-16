# Changelog

## v1.2.0-alpha (Sprint 15 — Curated Mobile Recommended Subset + Capability Tests)

### New Features
- **`mobile_recommended.json`** — Curated tier-aware model subset covering all 5 [DeviceTier]. 19 models total: 4 LOW + 5 MID + 4 HIGH + 3 FLAGSHIP + 3 TABLET. Each tier has one primary recommendation plus 1–4 alternatives. Asset is co-located with the catalog at `app/src/main/assets/`.
- **`MobileRecommendations`** — Thread-safe lazy loader singleton (double-checked `@Volatile cached` + `synchronized(this)`). Reads asset on first call, caches for process lifetime. Exposes `load(context)` for production callers and `@VisibleForTesting fun parseJson(raw: String)` as a pure-JVM testing seam.
- **`MobileRecommendationsFile.promotionBucket(tier, id)`** — Returns 0 (tier-primary), 1 (tier-alternative), 2 (not-promoted). Used by `ModelsViewModel.computeVisibleList` to float promoted models above global recommendations.
- **Tier-aware onboarding selection** — `OnboardingViewModel` now picks the device tier's primary model first, falls back to alternatives, then to the global catalog `recommended` flag, then `firstOrNull` matching `fitsAndSafe`. Log line extended with `promotion=tier-primary|tier-alternative|global-recommended|fallback`.
- **3 new i18n strings** — `badge_tier_primary`, `badge_tier_alternative`, `capability_recommended_for_your_device`.

### Bug Fixes
- **P0 — Latent bug: `heavyGateIds` / `experimentalIds` slug mismatch** — Sets were hardcoded with `-Q5_K_M` / `-Q8_0` quant suffixes, but `ModelInfo.id` arrives as `handy-computer/<slug>-gguf`. The match never worked in practice, leaving the 3 Voxtral (heavyGate) and 7 Moonshine Base (experimental) flag sets as no-ops. Renamed to `heavyGateSlugs` / `experimentalSlugs` with bare slugs, plus private `slugOf(modelId)` helper that strips prefix and suffix via explicit `CATALOG_ID_PREFIX` / `CATALOG_ID_SUFFIX` constants. Idempotent for already-slug strings.

### Testing
- **21 new JUnit 4 unit tests** added to `app/src/test/java/com/handy/app/capability/`:
  - `ModelCapabilityTest` (11 tests): 3 Voxtral heavyGate + 7 Moonshine Base experimental + 11 negative parity + 2 slug-idempotence positive tests.
  - `MobileRecommendationsTest` (10 tests): `parseJson` happy path / partial / no-alternatives / blank-primary / malformed / missing-tiers-key + `promotionBucket` for all 5 tiers × 3 buckets + cross-tier matrix.
- **Test deps** — `junit:4.13.2` and `org.json:json:20231013` added as `testImplementation` in `app/build.gradle.kts`. Pure JVM runnable with no Android SDK or Robolectric.

### Verified
- 21/21 tests pass in a manual JVM rig (kotlinc 1.9.24 + JUnit + org.json + Android stubs).
- Forward-compatible with AGP — `./gradlew :app:testDebugUnitTest --tests "com.handy.app.capability.*"` should run identically in the real build.

## v1.1.0-alpha4 (Sprint 9 — IME Redesign: PC-Overlay UI + Auto-Commit + Crash Fix)

### New Features
- **IME Complete Redesign**: Rewrote `HandyInputMethodService.kt` from scratch. The IME now renders a full-width voice panel matching the PC desktop overlay design:
  - **Idle state**: Mic pill with pulsing dot, "Dictate" label, and keyboard switch button
  - **Recording state**: 9-bar waveform animation (phase-offset, center-reactive), MM:SS timer, red stop button
  - **Transcribing state**: Material3 `CircularProgressIndicator` + "Transcribing…" label + cancel button
  - **Error state**: Error message + pink retry button
  - Pop-in animation (460ms cubic-bezier matching PC overlay)
  - Theme-aware colors via `MaterialTheme.colorScheme` (light/dark mode)
- **Auto-Commit Text**: Transcription auto-inserts into the active text field via `InputConnection.commitText()` — no confirm step needed (like Wispr Flow). The `autoCommitted` guard flag prevents infinite retry loops if injection fails.
- **Model Availability Check**: `startRecording()` now checks `nativeIsModelLoaded()` before starting recording. If no model is downloaded, shows error state instead of silently failing.
- **Injection Failure Feedback**: `confirmInsert()` failure now shows `STATE_ERROR` instead of silently resetting to IDLE — user gets error message + retry option.
- **Keyboard Switcher**: `showInputMethodPicker()` with try-catch fallback to `ACTION_INPUT_METHOD_SETTINGS` for OEM compatibility.

### Bug Fixes
- **IME Crash Fixed (ViewTreeLifecycleOwner v2)**: Replaced `Class.forName("androidx.lifecycle.R\$id")` (which caused `ClassNotFoundException` on some devices) with reflection on the stable public class name `androidx.lifecycle.ViewTreeLifecycleOwner` using `getMethod("set", View.class, LifecycleOwner.class)`. This fixes the `IllegalStateException: ViewTreeLifecycleOwner not found` crash.
- Fixed `autoCommitted` flag not resetting on retry — now resets in both `onStartInput` and the `onRetry` lambda.

### Documentation
- Updated AGENTS.md, SPEC.md, ARCHITECTURE.md, CHANGELOG.md with IME redesign details.

## v1.1.0-alpha3 (Sprint 8 — IME Fix + Onboarding Auto-Activate + ModelCard Languages)

### New Features
- **IME Crash Fix**: Replaced `ImeComposeView` (AbstractComposeView) with `ImeContainer` (FrameLayout wrapper + LifecycleOwner). Uses reflection (`Class.forName("androidx.lifecycle.R$id")`) to access lifecycle-runtime's internal `view_tree_lifecycle_owner` resource ID and set the ViewTreeLifecycleOwner tag. Fixes `IllegalStateException: ViewTreeLifecycleOwner not found` when ComposeView initializes inside InputMethodService.
- **Auto-Activate Model**: `EngineViewModel.onDownloadComplete()` now automatically calls `nativeSetActiveModel(modelId)` after successful download, so the model is immediately usable without manual activation.
- **ModelCard Multi-Row Languages**: Changed from single language chip with ellipsis to `FlowRow` with individual per-language chips that wrap naturally.

### Bug Fixes
- Fixed IME crash on startup (`ViewTreeLifecycleOwner not found`) via reflective tag setting on wrapper FrameLayout
- Fixed model not auto-activating after onboarding download (added `nativeSetActiveModel()` in `onDownloadComplete()`)
- Fixed model language display overflow by splitting into per-language chips in FlowRow

### Infrastructure
- Added explicit `lifecycle-runtime` dependency to compile classpath

### Documentation
- Updated AGENTS.md, SPEC.md, CHANGELOG.md with IME fix, onboarding auto-activation, and model card language changes

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
