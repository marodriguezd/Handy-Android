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

## Text Injection Strategy
Handy Android supports multiple mechanisms to insert recognized text into third-party apps:
1. **IME (Input Method Service):** Acts as a custom keyboard (Wispr Flow style). Auto-commits text directly to `InputConnection`.
2. **Shizuku:** Root-like injection using accessibility or input events via Shizuku (requires Shizuku runtime).
3. **Clipboard:** Fallback method that simply copies text to the Android clipboard.

The `InjectorRouter` automatically selects the best strategy (Shizuku > IME > Clipboard) and falls back to clipboard on failure.
