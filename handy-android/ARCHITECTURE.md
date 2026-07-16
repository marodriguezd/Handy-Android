# Handy Android Architecture

## Overview
Handy Android is an Android port of the Handy desktop app. It is divided into two primary modules:
1. `handy-core`: A Rust library exposing JNI bindings. It handles heavy lifting like audio capture (AAudio), resampling, Voice Activity Detection (VAD), and Whisper/transcribe-cpp batch inference.
2. `app`: A Kotlin/Jetpack Compose Android application that provides the user interface, background services, and text injection logic (IME, Shizuku, Clipboard).

## User Interface (Jetpack Compose)

### Navigation Hierarchy (Sprint 10)
- **MainActivity**: The single activity hosting the Compose tree. Uses a `Scaffold` with a `NavigationBar` (Material 3).
- **Bottom Nav Items (4):**
  1. **General** — `TabRow` with "General" and "Avanzado" tabs
  2. **Modelos** — `TabRow` with "Modelos" and "Post Proceso" tabs
  3. **Historial** — Direct, no tabs
  4. **Acerca de** — Direct, no tabs
- **Onboarding** — Full-screen flow, shown on first launch (no bottom bar)

### Screen Composables
| Composable | Location | Contents |
|---|---|---|
| `GeneralSettingsContent` | `ui/settings/SettingsScreen.kt` | Audio (idle timeout), Text Injection (Shizuku toggle, keyboard switch), Battery Optimization |
| `AdvancedSettingsContent` | `ui/settings/SettingsScreen.kt` | APLICACIÓN (Funciones Experimentales), SALIDA (Envío automático), TRANSCRIPCIÓN (VAD, Espacio Final), EXPERIMENTAL (Post Procesamiento toggle) |
| `PostProcessContent` | `ui/settings/SettingsScreen.kt` | LLM Endpoint, API Key (with visibility toggle) |
| `AboutContent` | `ui/settings/SettingsScreen.kt` | Version, Licenses (dialog), GitHub link |
| `ModelCatalogScreen` | `ui/models/ModelCatalogScreen.kt` | Model list with download/activate/delete, language chips |
| `HistoryScreen` | `ui/history/HistoryScreen.kt` | Paginated transcription list, delete/save |
| `OnboardingScreen` | `ui/onboarding/OnboardingScreen.kt` | First-launch setup with model download/skip |

### State Management
- `EngineViewModel`: The central, process-wide singleton that holds the engine state (recording status, transcription results). It lives in the `HandyApplication` class and is shared between `MainActivity` and `HandyInputMethodService`.
- `SettingsViewModel`: Created once at the Activity scope and shared across `GeneralSettingsContent`, `AdvancedSettingsContent`, and `PostProcessContent`. All settings are persisted to `SharedPreferences` via `SettingsStore`.
- Other ViewModels (`ModelsViewModel`, `OnboardingViewModel`, `HistoryViewModel`) manage specific screen states.
- State is exposed as `StateFlow` and consumed via `collectAsState()` in Compose.

### Settings Infrastructure
- `SettingsStore`: SharedPreferences wrapper with typed getters/setters for all settings.
- `AppSettings`: Data class for bulk passing settings to the Rust engine via `EngineViewModel.applySettings()`.
- `SettingsViewModel`: UI state holder that syncs bidirectionally with `SettingsStore` and calls `engineViewModel.applySettings()` with debouncing for network-related settings.

## Native Layer (JNI)
The JNI bridge acts as the communication layer between Kotlin and Rust.
- `EngineBridge`: All `external fun` declarations (22 functions) for native operations.
- `EngineCallback`: Interface implemented by `EngineViewModel` for Rust → Kotlin callbacks (state changes, transcription, VAD level, errors, download progress).
- UI state naturally updates when JNI callbacks alter the `EngineViewModel`.

### Active Model Persistence (Sprint 13)
The active model ID is persisted to `model_dir/.active_model` between app restarts:
- `ModelManager::new()` loads the persisted ID on startup, verifying the `.gguf` exists
- `set_active_model()` writes the ID to the file on each activation
- `delete_model()` removes the file when the active model is deleted
- Stale files (`.gguf` missing) are cleaned up automatically

### Batch Transcription Cancellation (Sprint 13)
A shared `cancel_flag: Arc<AtomicBool>` allows cancelling in-progress batch transcriptions:
- `TranscriptionEngine.run()` checks before/after `session.run()`, discarding results when cancelled
- `PeriodicWorker` checks before each partial `session.run()` (~3s intervals) and exits the loop
- Flag is reset in `start_stream()`/`start_periodic()` at the start of each new recording
- `cancel()`, `cancel_stream()`, `cancel_periodic()` all set the flag

## IME — onComputeInsets (Sprint 13)
The IME now properly reports its content area to the Android framework:
- Content height measured dynamically via `onGloballyPositioned` in Compose
- `contentTopInsets` set to the pill's measured height — only the floating pill is "IME content"
- `TOUCHABLE_INSETS_CONTENT` — touches in the transparent background pass through to the host app
- Prevents the host app from being pushed up by the full IME window height

## Capability-Aware Model Catalog (Sprint 14)
Prevents OOM fatal crashes and guarantees model integrity across fragmented Android hardware by evaluating device capabilities before inference/download.

### Detection Layer (`capability/`)
- **`DeviceCapabilityDetector.detect(context)`** queries `ActivityManager.MemoryInfo` + `isLowRamDevice` + `Runtime.maxMemory()` → returns an immutable `CapabilitySnapshot`
- **`CapabilitySnapshot.toTier()`** resolves the tier using fixed RAM bands: ≤1.5GB → LOW, ≤3.5GB → MID, ≤6.5GB → HIGH, ≤12.5GB → FLAGSHIP, >12.5GB → TABLET
- **`DeviceTier.maxRecommendedModelCapability`** maps each tier to the heaviest model class it can safely run: LOW→ULTRA_LIGHT, MID→LIGHT, HIGH→MEDIUM, FLAGSHIP→HEAVY, TABLET→EXTREME

### Resolution Layer
- **`ModelCapability.fromModel(model)`** classifies a model by `sizeBytes` into ULTRA_LIGHT / LIGHT / MEDIUM / HEAVY / EXTREME
- **`ModelCapability.heavyGateIds`** = {Voxtral Small 24B, Voxtral Mini 4B Realtime, Voxtral Mini 3B} — always require user consent
- **`ModelCapability.experimentalIds`** = 7 Moonshine Base monolingual variants — hidden unless `showExperimentalModels=true`
- **`CompatibilityResolver.computeCompatibility(model, snapshot, showExperimental)`** — pure function returning `ModelCompatibility(tier, status, badges, requiresConsent, hidden)`

### UI Gating
- **`DeviceCapabilityHeader`** (Card top of catalog) shows total GB, tier, refresh icon, and a conditional Switch for experimental models (visible only at MID+)
- **`CompatibilityBadgeChip`** visualizes 4 badges (EXPERIMENTAL, HEAVY_GATE, EXCEEDS_RAM, LARGE_HEAP_REQUIRED)
- **`HeavyModelWarningDialog`** gates HEAVY/EXTREME downloads behind a required Checkbox consent (title/body differentiate HEAVY vs EXTREME)

### ViewModel Integration
- **`ModelsViewModel.attemptDownload(model)`** routes through `computeCompatibility`; sets `showLargeModelDialogFor` when `requiresConsent=true`. The non-gating `downloadModel(modelId)` is preserved for imperative flows (e.g., onboarding auto-download)
- **`OnboardingViewModel.fitsAndSafe`** filter excludes heavyGate models. Selection chain: `recommended+safe` → `any+safe` → null-dead-end fallback (logs warning + advances wizard with `isDownloadReady=true`). Snapshot is cached via `by lazy` to avoid re-detection on every state emission

### Persistence
- **`SettingsStore.showExperimentalModels`** (boolean, default `false`) persisted to `show_experimental_models` SharedPreferences key

### Observability
- **`EngineVM init` log** emitted after `nativeInit(...)`: `EngineVM init; capabilityTier=${tier}; totalMemGB=${gb}; showExperimental=${flag}`
- **`OnboardingVM`** emits 11 distinct log lines (TAG="OnboardingVM") covering step transitions, model load, target selection, and download events. Failure logs de-dup via a separate `lastLoggedFailureId` sentinel (avoiding collision with `downloadTargetId`)

## Text Injection Strategy
Handy Android supports multiple mechanisms to insert recognized text into third-party apps:
1. **IME (Input Method Service):** Acts as a custom keyboard (Wispr Flow style). Auto-commits text directly to `InputConnection`.
2. **Shizuku:** Root-like injection using accessibility or input events via Shizuku (requires Shizuku runtime).
3. **Clipboard:** Fallback method that simply copies text to the Android clipboard.

The `InjectorRouter` automatically selects the best strategy (Shizuku > IME > Clipboard) and falls back to clipboard on failure.
