# Changelog

## v0.3.0-preview (Handy Android — Feature Porting from android_transcribe_app, 22 Julio 2026)

Integración y porteo de los módulos funcionales clave desarrollados en `android_transcribe_app` hacia `Handy-Android`:

### 🎤 Servicio de Voz del Sistema (System SpeechRecognizer)
- **`HandyVoiceRecognitionService.kt`** — Implementación de `android.speech.RecognitionService` que permite a teclados del sistema (Gboard, SwiftKey) y aplicaciones externas solicitar dictado offline mediante `SpeechRecognizer`.
- **`RecognizeActivity.kt`** — Interfaz nativa en Jetpack Compose (`ModalBottomSheet`) activada por el Intent `android.speech.action.RECOGNIZE_SPEECH`, con indicador VAD en tiempo real, estado de procesamiento y vista previa.
- **`AndroidManifest.xml`** — Registro oficial de servicios e intents para integración transparente en todo el SO Android.

### 📚 Diccionario Personalizado & Corrector Fonético (WordCorrector)
- **`WordCorrector.kt`** — Motor de corrección fonética en Kotlin que combina el algoritmo **Soundex** y la distancia **Levenshtein** normalizada para reemplazar transcripciones erróneas por palabras del usuario, preservando puntuación y mayúsculas/minúsculas. Incluye filtro dinámico de muletillas ("uh", "um", "ehm", "este", "o sea") y contracción de tartamudeos.
- **`DictionaryScreen.kt` & `SettingsScreen.kt`** — Pantalla visual en Jetpack Compose para gestionar palabras y **Switch para activar/desactivar el filtro de muletillas** en los Ajustes Generales (`upstream/toggle-filler-cleanup`).
- **`SettingsStore.kt`** — Persistencia de la preferencia `fillerWordsEnabled` y palabras personalizadas en `SharedPreferences`.

### 🤖 Post-Procesamiento Multi-Prompt LLM
- **`PromptsRepository.kt`** — Repositorio de prompts de sistema personalizables persistido en JSON atómico (`prompts.json`).
- **`PostProcessor.kt`** — Cliente HTTP para refinamiento de texto ASR con servicios LLM compatibles con OpenAI/Ollama (Ollama, LM Studio, Groq, OpenAI).

---

## v0.2.0-preview (Handy Android — Second Pre-Release, 17 Julio 2026)

> **Nota sobre versionado:** el codebase interno `versionName="1.0.0-alpha2"` estuvo en uso desde Sprint 16 (cuando se bumpó de alpha1 a alpha2 para reflejar el started-MD3 work) pero **nunca llegó a publicarse** como CHANGELOG entry ni como GitHub release. `v0.2.0-preview` es el segundo pre-release público bajo este naming scheme (mismo esquema `-preview` que v0.1.0-preview). Los `v1.x.0-alpha*` entries arriba (v1.2.0-alpha, v1.1.0-alpha4, etc.) corresponden al fork de PC sincronizado antes de la separación Android-only en Julio 2026 y NO están en línea semver directa con este.

Second pre-release del fork Handy Android. Cierra la mitad del plan MD3 (Sprints 17–23) más una pasada final de hygiene pre-Sprint 24. Esta versión está *lista para side-load* por testers externos que quieran probar la app en un device Android real antes de que llegue el siguiente release público.

### Material Design 3 backbone (completo)
- **Sprint 17 — Fundamentos MD3.** Tema migrado a `Theme.Material3.DayNight.NoActionBar`, transparent system bars, `shortEdges` cutout mode. `MainActivity` ahora llama `enableEdgeToEdge()` antes de `setContent`. `Theme.kt` poblado con jerarquía tonal completa: `surfaceContainer{Lowest … Highest}`, `surfaceDim/Bright`, `outlineVariant`, `scrim`. Paleta Handy PC preservada verbatim (seeds `#f28cbb`, `#da5893`, `#2c2b29`, `#5a5753`).
- **Sprint 18 — Shared MD3 components** en `ui/components/`: `SettingsGroup`, `HandySlider`, `HandySwitch`, `HandyChipGroup`, `HandySearchBar`, `HandySegmentedButton`, `HandyBadge`, `HandySnackbar`, `HandyDialog`, `HandyFab`, `HandyListItem`, `HandyDropdown`, `HandyTonalBlock`, `HandyModalBottomSheet`, `MotionTokens`, `StatusDot`, `HandySpringTokens`. Tokens centralizados: `Spacing.xs..huge`, springs `gentle/bouncy/snappy`.
- **Sprint 19 — General Settings MD3** con `MicrophoneSelector` index-based (fallback para entornos sin `AudioDevice`), `AudioFeedbackToggle`, `SoundPicker`, `VolumeSlider`, `ModelSettingsCard`. Grupos Audio / Model / Shortcuts.
- **Sprint 20 — Advanced Settings + Experimental gated.** Groups App / Output / Transcription / History / Experimental. Strings `settings_section_*` consolidadas.
- **Sprint 21 — IME rediseño MD3** (flagship surface). Pill shape `RoundedCornerShape(28.dp)`, `Surface(tonalElevation=3.dp, shadowElevation=6.dp)`, spring motion, IconButtons 48dp, 6-bar state machine (`IDLE / LOADING / LISTENING / TRANSCRIBING / CONFIRM / ERROR`), placement top/bottom via `SettingsStore.imePlacementFlow`. `// TODO(Sprint24): confirming-cursor 1s blink before transitioning out of STATE_CONFIRM` placed as breadcrumb.
- **Sprint 22 — Models search + language filter (refactor + tests).** `CatalogSorter.computeVisibleCatalog` ahora acepta `searchQuery/languageFilter/onlyRecommended` como default params. 13 nuevos tests puros JVM (10 pre-existentes + 13 nuevos = 23 PASS / 0 FAIL).
- **Sprint 23 — About + ThemeSelector + LocaleSelector (feature complete).** Tres `SettingsGroup`s (APPEARANCE / LANGUAGE / ABOUT). `ThemeSelector` SegmentedButton 3-way (SYSTEM/LIGHT/DARK). `LocaleSelector` HandyDropdown BCP-47 (`null` / `en` / `es`). Persistencia vía `SettingsStore.themeMode/appLanguage`. `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`. AndroidManifest declara `configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode|locale|layoutDirection|density|fontScale"` en MainActivity → Compose recompone strings sin destroy/recreate. Recording state survives automatic.

### Lint cleanup (pre-Sprint 24 hygiene)
- **`ExportedReceiver` 1→0**: `android:permission="android.permission.DUMP"` añadido al `TestCommandReceiver`.
- **`BatteryLife` 1→0**: `@file:Suppress("BatteryLife")` en `SettingsScreen.kt` (user-initiated intent, policy-safe).
- **`ModifierParameter` 3→0**: `@Suppress("ModifierParameter")` per-function en HandyFab.kt, HandyTonalBlock.kt, SettingsGroup.kt.
- **`ObsoleteSdkInt` 5→1**: Source code limpio (HandyApplication.kt `createQuickDictateChannel` + RecordingService.kt `start/createNotificationChannel/audioFocusRequest`). Residuo 1: `mipmap-anydpi-v26` foldering (estructural).
- **`Icons.Default.VolumeUp` deprecation 2→0**: `Icons.AutoMirrored.Filled.VolumeUp` en AudioFeedbackToggle.kt + VolumeSlider.kt (RTL-correct).
- **`Name shadowed: snap/showExp` ModelsViewModel.kt:87 1→0**: Destructure `val (snapSrc, showExpSrc) = snapShowExp` evita shadow contra los outer init `val snap/showExp`.
- **`optString fallback null` warning 4→0**: `obj.optString("...", null)` → patrón explícito `if (obj.isNull("...")) null else obj.optString("...")` en HistoryEntry.kt (post_processed_text, audio_path) + ModelInfo.kt (license, description).
- **`UNUSED_PARAMETER 'subtitle'` HandySwitch 1→0**: Restaurado el rendering del subtitle en `Column(weight 1f)`.
- **`UNUSED_PARAMETER 'activity'` ShizukuInjector 1→0**: `@Suppress("UNUSED_PARAMETER")` per-function.
- **`'TRIM_MEMORY_RUNNING_CRITICAL' deprecated` 1→0**: `@Suppress("DEPRECATION")` en `HandyApplication.onTrimMemory()` (int constant retenido para minSdk=26 compat con `TrimMemoryLevel` enum API 35+).

### Documentación
- **AGENTS.md** — Current State actualizado con Sprint 23 cierre + pre-Sprint 24 hygiene + versión `0.2.0-preview`. Sprint ordering authority reaffirmed.
- **PROGRESS.md** — Sprints 17–23 detallados + pre-Sprint 24 hygiene batch. Visual verification end-to-end (3 screencaps en `/tmp/handy_shots/`).
- **LIMPIA.md** — "Sprint actual: Sprint 23 — About + ThemeSelector + LocaleSelector (feature complete). Próximo: Sprint 24 — History con audio + retry".
- **MainActivity.kt** — Comment en `onRestoreInstanceState` reescrito enumerando las 9 flags reales del manifest (no más "Activity restart" stale).

### Visual verification end-to-end en A059 Android 16 (192.168.1.36:42813)
- Home → About: 3 SettingsGroups + Theme segmented + Locale dropdown + Version 0.2.0-preview-debug + GitHub + App data dir todos renderizan correctamente.
- Light theme tap @ (540, 637): UI state actualizado.
- Locale dropdown: abre.

### Build baseline final
- `compileDebugKotlin`: BUILD SUCCESSFUL.
- `testDebugUnitTest`: 23 PASS / 0 FAIL.
- `lintDebug`: 0 errors / 84 warnings. Carry-over deferred a Sprints 25–29 (UnusedResources sweep, AGP 9.x bump, IconDuplicates polish, Onboarding visual verification).

### Known issue
- Locale switch → Spanish strings render NO verificado end-to-end (script bash con Python inline falló por escape syntax al tap-acción). El dropdown abre correctamente lo cual valida que `LocaleSelector` está wired a `AppCompatDelegate.setApplicationLocales`. Funcionalmente completo pero la verificación visual del string-switch quedó incompleta.

### Próximo sprint
- **Sprint 24 — History con audio + retry**. AudioPlayer per history entry (Slider + CircularProgressIndicator 24dp buffering), retry action, ring-buffer RecordingRepositoryProvider via MediaStore.

### Notas para testers
- Esta es una **debug build** (side-loadable). Los usuarios que vienen de v0.1.0-preview **deberían uninstall primero** antes de instalar v0.2.0-preview — el cambio de debug-debug-id (`com.handy.app.debug` vs el viejo) puede provocar signature mismatch.
- Permission grant: `adb shell pm grant com.handy.app.debug android.permission.RECORD_AUDIO`.
- Skip-onboarding intent: `adb shell am start -n com.handy.app.debug/com.handy.app.MainActivity --ez skip_onboarding true`.

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
- **Auto-Commit Text**: Transcription auto-inserts into the active text field via `InputConnection.commitText()` — no confirm step needed (HandyPC-style auto-commit). The `autoCommitted` guard flag prevents infinite retry loops if injection fails.
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
