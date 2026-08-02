# Project: Handy Android Wispr Flow Parity & Material Design 3 Redesign

## Architecture
- **UI & Material Design 3 Layer**: `com.handy.android.ui.theme` (`Theme.kt`, `Color.kt`, `Type.kt`), Compose scaffolds for `MainActivity`, `ModelsActivity`, `HistoryScreen`, `LlmSettingsActivity`, `PostProcessSettingsActivity`, `CustomWordsActivity`, `TranscriptionSettingsActivity`, and modern animated `AudioWaveformView`.
- **Wispr Flow Audio & Dictation Layer**: Silero VAD silence detector (`SileroVadDetector.kt`, `AudioRecorder.kt`), Tap vs Hold dual gesture (`FloatingButtonService.kt`, `HandyInputMethodService.kt`), 2-Tier Post-Processor (`LlmPostProcessor.kt`, `PostProcessor.kt`, `CustomWordsActivity.kt`).
- **System Integration & Insertion Layer**: 3-tier text insertion resilience (`AutoTypeAccessibilityService.kt`), Quick Settings Tile (`HandyTileService.kt`), Audio & Haptic Feedback (`AudioFeedbackManager.kt`).
- **Validation & Gauntlet Layer**: Gradle canonical targets (`checkModelCatalog`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`), test suite expansion, and Forensic Audit (`teamwork_preview_auditor`).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | MD3 Theme & Dynamic Colors | Central `com.handy.android.ui.theme` package with dynamic color scheme (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) & dark mode | M1 | survey_1 |
| 2 | MD3 Sub-Activity Modernization | Scaffolds and TopAppBar navigation across `ModelsActivity`, `HistoryScreen`, `LlmSettingsActivity`, `PostProcessSettingsActivity`, `CustomWordsActivity`, `TranscriptionSettingsActivity` | M1 | survey_1 |
| 3 | MD3 Component & Navigation Upgrade | MD3 vector icons for `NavigationBar`, `ElevatedCard` containers, `FilterChip`, `Switch`, `IconButton` | M1 | survey_1 |
| 4 | Animated Audio Waveform & Overlays | Smooth interpolation amplitude decay drawing on canvas and state-based color animation without flickering | M1 | survey_1 |
| 5 | Silero VAD Silence Auto-Stop | Silence detection in `SileroVadDetector` & `AudioRecorder` with 1200ms auto-stop threshold and fail-safe handling | M2 | survey_2 |
| 6 | Tap / Hold Dual Gesture | Tap to toggle ON/OFF vs Hold push-to-talk in `FloatingButtonService` and `HandyInputMethodService` with 300ms threshold and drag cancellation | M2 | survey_2 |
| 7 | LLM Post-Processor | `LlmPostProcessor` supporting OpenAI / Groq / OpenRouter / Ollama with 5s timeout guard | M2 | survey_2 |
| 8 | Rule-Based Fallback & Dictionary | Deterministic local `PostProcessor` fallback with filler removal, custom dictionary replacement, punctuation, and capitalization | M2 | survey_2 |
| 9 | Multi-level Text Insertion Resilience | `AutoTypeAccessibilityService` 3-tier insertion: `ACTION_SET_TEXT` -> `ClipboardManager` + `node.performAction(ACTION_PASTE)` -> Toast/Notification fallback | M3 | survey_3 |
| 10 | Quick Settings Tile Integration | Real-time status sync and toggle control in `HandyTileService` | M3 | survey_3 |
| 11 | Audio & Haptic Feedback | `AudioFeedbackManager` process-wide SoundPool/Vibrator feedback for start/stop/success | M3 | survey_3 |
| 12 | Dual Track E2E & Gauntlet Verification | Execute `./gradlew checkModelCatalog testDebugUnitTest lintDebug assembleDebug assembleRelease` and pass Forensic Audit | M4 | survey_3 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | MD3 Visual Redesign | UI theme, screens, MD3 components, animated waveform | None | COMPLETED |
| M2 | Wispr Flow Parity | Silero VAD, Tap/Hold gestures, LLM post-processing, fallback rules, dictionary | M1 | COMPLETED |
| M3 | Text Insertion & System Integration | AutoTypeAccessibilityService 3-tier resilience, HandyTileService, AudioFeedbackManager | M2 | COMPLETED |
| M4 | E2E Testing & Gauntlet Hardening | E2E testing tiers 1-5, canonical build/test execution, forensic audit | M3 | IN PROGRESS |

## Interface Contracts
### UI (M1) ↔ Audio/VAD (M2)
- State flow: Recording state (IDLE, RECORDING, PROCESSING) emitted by `AudioRecorder` / `FloatingButtonService` consumed by `AudioWaveformView` for dynamic color & animation.

### Audio/VAD (M2) ↔ Post-Processing (M2)
- Interface: `PostProcessor` interface with `process(text: String): String` implemented by `LlmPostProcessor` (Tier 1) delegating to `LocalRulePostProcessor` (Tier 2) on failure.

### Audio/VAD (M2) ↔ System Integration (M3)
- Interface: `AutoTypeAccessibilityService.insertText(text: String)` receives formatted text from post-processor pipeline.
- Interface: `HandyTileService` broadcasts intent `ACTION_TOGGLE` to `FloatingButtonService` and observes `isRecording` state.

## Code Layout
- Package: `com.handy.android`
- App module: `app/src/main/java/com/handy/android/`
- Theme (Compose): `app/src/main/java/com/handy/android/ui/theme/` (`Theme.kt`, `Color.kt`, `Type.kt`)
- Activities/Screens: `app/src/main/java/com/handy/android/` (planos en el paquete raíz)
- Audio/VAD: `app/src/main/java/com/handy/android/` (`AudioRecorder.kt`, `SileroVadDetector.kt`)
- Post-processing: `app/src/main/java/com/handy/android/` (`PostProcessor.kt`, `LlmPostProcessor.kt`)
- Service/Accessibility: `app/src/main/java/com/handy/android/` (`AutoTypeAccessibilityService.kt`, `HandyTileService.kt`, etc.)
- Recursos i18n: `app/src/main/res/values*/strings.xml` (en, es, de, fr, ja, zh, pt)
- Unit tests: `app/src/test/java/com/handy/android/`
