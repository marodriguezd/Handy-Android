# AGENTS.md — Handy Android Project Memory & Orchestration

> **This file is the canonical project memory.** Every AI session must read it first. It is kept up-to-date with the complete current state, conventions, commands, and open items so context persists across sessions.

---

## 🚀 Start Every Session Here

1. **Read this file** (`AGENTS.md`).
2. **Read `handy-android/PROGRESS.md`** for the latest checkpoint and next steps.
3. **Read `handy-android/LIMPIA.md`** for the clean-start checklist and ADB commands.
4. **Read `handy-android/SPEC.md`** for UI/UX specifications.
5. **Read `handy-android/ARCHITECTURE.md`** for system architecture.
6. Run `./gradlew :app:compileDebugKotlin` to verify the project builds.
7. Run `adb devices -l` to verify the test device is connected.

---

## 📋 Project Overview

**Handy Android** (`handy-android/`) is the Android port of the Handy desktop speech-to-text app. It combines:

- **`handy-core/`** — Rust `cdylib` exposing JNI bindings for audio capture (AAudio), resampling, VAD, model download, and Whisper/transcribe-cpp inference.
- **`app/`** — Kotlin/Jetpack Compose Android app with adaptive navigation, Material Design 3 UI, IME keyboard, foreground recording service, and text injection strategies.

**Repository:** Fork of [github.com/cjpais/Handy](https://github.com/cjpais/Handy), focused exclusively on Android.

---

## 🗓️ Current State — Pre-Sprint 24 hygiene closed + v0.2.0-preview release shipped (Julio 17, 2026)

### ✅ Completed — Sprints 16 → 23 (full MD3 backbone + lint sweep)

#### Sprint 16 — Material Design 3 Redesign + ADB Test Hooks
- **Settings** — `SettingsRow` replaced with Material 3 `ListItem`.
- **Model Catalog** — `AssistChip` replaced with `SuggestionChip` for language tags.
- **IME** — Hardcoded `RoundedCornerShape` replaced with `MaterialTheme.shapes` tokens; touch targets enlarged to 48dp.
- **Badges** — `RoundedCornerShape(4.dp)` replaced with `MaterialTheme.shapes.extraSmall`.
- **Adaptive Navigation** — `NavigationRail` on large screens (`screenWidthDp >= 600`), `NavigationBar` on phones.
- **Version bump** — `versionCode=3`, `versionName="1.0.0-alpha2"`.
- **ADB Test Automation** — `TestCommandReceiver` (manifest placeholder, `BuildConfig.DEBUG`-gated, model-id validation, `DUMP` permission), `Shizuku disabled in debug`, `skip_onboarding` intent, `scripts/adb_test_flow.sh`.
- **IME Touch Fix** — `onComputeInsets` switched to `TOUCHABLE_INSETS_REGION` with explicit `touchableRegion` and `resources.displayMetrics.widthPixels` fallback.
- **Catalog Sort Tests** — `capability/CatalogSorter.kt` (pure) + 10 unit tests in `CatalogSorterTest.kt`.
- **GPU/NPU Investigation** — Documented in `BACKENDS.md`; CPU stable, Vulkan partial, NNAPI deprecated, QNN future.

#### Sprint 17 — Fundamentos MD3
- `themes.xml` migrated to `Theme.Material3.DayNight.NoActionBar`, transparent system bars, `shortEdges` cutout mode.
- `MainActivity` calls `enableEdgeToEdge()` before `setContent`.
- `Theme.kt` populated with full M3 tonal hierarchy: `surfaceContainer{Lowest,Low,…,Highest}`, `surfaceDim/Bright`, `outlineVariant`, `scrim`.
- PC palette preserved verbatim (`#2c2b29`, `#f28cbb`, `#da5893`, `#5a5753`, `#808080`).
- Hand-built Compose BOM pin: `compose-bom = "2025.01.00"` (Kotlin 1.9.24-compatible M3 1.3.1). M3 1.4+ `primaryFixed*` deferred until Kotlin 2.0 migration.

#### Sprint 18 — Componentes shared MD3 (`ui/components/`)
- `SettingsGroup.kt`, `HandySlider.kt`, `HandySwitch.kt`, `HandyChipGroup.kt`, `HandySearchBar.kt`, `HandySegmentedButton.kt`, `HandyBadge.kt`, `HandySnackbar.kt`, `HandyDialog.kt`, `HandyFab.kt`, `HandyListItem.kt`, `HandyDropdown.kt`, `HandyTonalBlock.kt`, `HandyModalBottomSheet.kt`, `MotionTokens.kt`, `StatusDot.kt`.
- Central `HandySpringTokens.kt` (gentle / bouncy / snappy) and `Spacing.kt` (xs..huge).
- Note: `ExposedDropdownMenu` resolved as `ExposedDropdownMenuBoxScope` scope-member only on M3 1.3.1 — calls inside the box are bare (no top-level import).

#### Sprint 19 — General settings MD3
- `ui/settings/SettingsScreen.kt` fully refactored.
- New components: `MicrophoneSelector.kt` (index-based fallback for environments without `android.media.AudioDevice` resolution), `AudioFeedbackToggle.kt`, `SoundPicker.kt`, `VolumeSlider.kt`, `ModelSettingsCard.kt`.
- Groups: Audio, Model, Shortcuts.

#### Sprint 20 — Advanced settings + Experimental gated
- New components and refactor of `settings_section_*` strings (mixed Spanish/English → consolidated).
- Groups: App, Output (= Text injection: Shizuku/Paste/Clipboard), Transcription, History, Experimental.

#### Sprint 21 — IME rediseño MD3 ⬆️ **flagship, después de Shared Components y antes de cualquier pantalla secundaria**

The IME (`HandyInputMethodService.kt`) was deliberately moved up to Sprint 21 so corrections don't ripple into later sprints.

- **Pill shape** — `PillShape = RoundedCornerShape(28.dp)`. Compose M3 1.3 has no `MaterialTheme.shapes.full`; `28.dp` is the closest spec-aligned approximation.
- **Tonal elevation 3.dp** on every bar's `Surface` (replaces the pre-MD3 `surfaceVariant.copy(alpha=0.7f)` background).
- **`shadowElevation = 6.dp`** layered on top for separation from busy backgrounds.
- **`errorBorderFor(state)` @Composable helper** — `BorderStroke(error.copy(alpha=0.2f), width=1.dp)` only on `STATE_ERROR`.
- **Touch targets** — every `FilledIconButton` is `Modifier.size(48.dp)` (MD3 minimum, previous 34dp → 48dp).
- **Spring motion** — `HandySpringTokens` (`gentle()` stiffness=380f damping=0.85, `bouncy()` stiffness=380f damping=0.6, `snappy()` stiffness=600f damping=0.9) drives:
  - **Pop-in** — `HandyVoiceBar`'s first composition: `popScale 0.92→1f` (`bouncy`), `popAlpha 0→1f` (`gentle`).
  - **Press scale** — `pressScaleClickable` swaps `Modifier.scale(1f ↔ 0.92f)` on `collectIsPressedAsState`, animated by `gentle`.
  - **PulsingDot** & **IdlePulsingDot** — infinite cycle is achieved with `LaunchedEffect { delay(FinitePhaseDurationMs); phase = !phase }` toggling a Boolean, then two `animateFloatAsState`s (`gentle` for alpha, `bouncy` for scale). Compose's `infiniteRepeatable` rejects `spring`, hence this state-toggling pattern.
  - **Waveform** — 9 bars (`barCount=9`), each with a phase oscillator of `600 + i*80ms`. `centerFactor = 1f - abs(i - 4.5f)/4.5f` gives center-emphasis weighting.
- **IME placement** — `SettingsStore.imePlacementFlow: StateFlow<String>` (backed by SharedPreferences key `ime_placement`, default `"bottom"`). The pill aligns `TopCenter` or `BottomCenter` and swaps `padding(top = Spacing.huge)` ↔ `padding(bottom = Spacing.huge)` reactively.
- **State machine (6 bars)** — `AnimatedContent` `ContentTransform(slideInVertically(tween(300, EnterEasing)) + fadeIn(tween(250)), fadeOut(tween(150)))` across `STATE_LOADING`, `STATE_LISTENING`, `STATE_TRANSCRIBING`, `STATE_CONFIRM`, `STATE_ERROR`, `STATE_IDLE`. The 6 state→bar mapping is `IDLE→IdleBar`, `LOADING→LoadingBar`, `LISTENING→RecordingBar`, `TRANSCRIBING→TranscribingBar`, `CONFIRM→ConfirmBar`, `ERROR→ErrorBar`.
- **Bar contents**:
  - `IdleBar` — primary container surface, idle pulsing dot, label, `FilledIconButton(secondaryContainer)` keyboard switcher.
  - `LoadingBar` — 18dp `CircularProgressIndicator`, `ime_loading_model` label.
  - `RecordingBar` — primary `PulsingDot`, `WaveformBars`, timer `MM:SS`, error `FilledIconButton` Stop, collapsible partial text (3 lines).
  - `TranscribingBar` — 18dp `CircularProgressIndicator`, `ime_transcribing` label, error `FilledIconButton` cancel, partial text (2 lines).
  - `ConfirmBar` — text (4 lines max), always-visible copy button (`secondaryContainer` `FilledIconButton`), `HorizontalDivider(outlineVariant)`, `TextButton` Discard + `FilledTonalButton(primaryContainer)` Insert.
  - `ErrorBar` — `errorContainer.copy(alpha=0.08f)` background, `BorderStroke(error.copy(alpha=0.2f))`, in-circle `!` glyph, `ime_error_generic` message, `FilledIconButton(error)` Retry.

#### Build-debt cleanup closed (mid-Sprint 21)
- `gradle/libs.versions.toml` BOM comment block documents the Kotlin 1.9.24 hold-back.
- Removed dead `Modifier.blinkingCaretAlpha()` function (its 4 supporting imports stripped).
- `// TODO(Sprint22): introduce a confirming-cursor (1s blink) before transitioning out of STATE_CONFIRM.` placed as grep-able breadcrumb.

#### Sprint 22 — Models: SearchBar + filtros + secciones ⬆️ **refactor + tests (UI shipped in Sprint 20)**

The catalog screen UI (SearchBar / `HandyChipGroup` filtro de idiomas / `FilterChip` "Recommended for this device" / secciones `Your models` / `Available models` con `titleSmall` / `FilledCard`-on-primaryContainer para active / `OutlinedCard` para available / `IconButton(Refresh)` en TopAppBar) y todas las strings (`models_search_placeholder`, `models_filter_all_languages`, `models_section_your_models`, `models_section_available_models`, `models_filter_recommended_only`, `models_empty_search`) **ya estaban shipping desde Sprint 20** ("Sprint 20: Models search + language filter"). El trabajo real de Sprint 22 fue destapar la lógica de filtrado para tests puros JVM.

- **Pure-function refactor** — `computeVisibleCatalog` gana tres parámetros con defaults (`query: String = ""`, `languageFilter: String? = null`, `onlyRecommended: Boolean = false`). Pipeline reordenado para eficiencia: filtros baratos (search/language/recOnly) corren **antes** del `computeCompatibility` (~5× más barato que evaluar modelo × snapshot × showExp), y el `sortedWith` mantiene el invariante existente (status → promotionBucket → recommended → sizeBytes).
- **`ModelsViewModel` simplificado** — `filterRaw` (anteriormente privado, opaco a tests) se eliminó. El 5-flow `combine(...)` pasa directamente `query`/`lang`/`recOnly` a `computeVisibleCatalog`. La rama de short-circuit intermedia (`if (filtered.isEmpty()) ...`) ya no es necesaria porque `computeVisibleCatalog` retorna `emptyList()` naturalmente.
- **Backwards compat preservado** — Los 10 tests originales de `CatalogSorterTest` (Sprint 16) siguen compilando porque los tres nuevos parámetros tienen defaults que semánticamente equivalen al comportamiento pre-refactor.
- **13 nuevos tests** (`CatalogSorterTest`):
  - Search: blank/whitespace-only=`todo`, id case-insensitive, description case-insensitive, displayName path-with-distinct-id, mid-token (no prefix/suffix of full id), surrounding-whitespace trim, no-match → empty.
  - Language: `null`=todo, single tag match, split-comma + case-insensitive (`"en, es, multi"` matches `"EN"`/`"es"`/`"fr"`).
  - `onlyRecommended`: `true` esconde no-recommended, `false`=default explícito.
  - Composición: filtros cheap + invariante de sort (ACTIVE-first, TIER_RECOMMENDED primary-before-alternative) verificada con un set de cuatro modelos donde sólo tres sobreviven a los tres filtros.
- **Notas de design preservadas**:
  - 3 estados de Card (active=primaryContainer, downloaded-inactive=secondaryContainer, available=OutlinedCard). Mantenido sobre el spec literal de 2 estados porque mejora la UX (distingue visualmente "descargado pero inactivo" sin leer el icono).
  - `HandySearchBar` (TextField fallback documentado para M3 1.3.1) sin migrar a `SearchBar` M3 nativo — el riesgo de OptIn/slot API en M3 1.3.1 vs 1.4+ no compensa el gain estético, según Sprint 18.
- **Verification**: `./gradlew :app:compileDebugKotlin` UP-TO-DATE (cached from Sprint 21) → `./gradlew :app:testDebugUnitTest --tests '*CatalogSorterTest*'` → **23 PASS / 0 FAIL** (`10 Sprint 16 + 13 Sprint 22`). Sin regresiones.

#### Lint carry-over (Sprint 17, *RESUELTO*)

`./gradlew :app:lintDebug` falla con 1 error **preexistente desde Sprint 17**: `app/src/main/res/values/themes.xml:10` — `android:windowLayoutInDisplayCutoutMode=shortEdges` requiere API 27 (minSdk=26). Opciones:
1. Bump minSdk a 27 (Android 8.1, ~99 % cobertura actual).
2. Mover la declaración a `values-v27/themes.xml`.
3. `tools:targetApi="27"` o `tools:ignore="NewApi"` en el item.

*RESUELTO el 17 julio 2026:* aplicado `tools:targetApi="27"` al item `windowLayoutInDisplayCutoutMode`, espejando el patrón existente de `tools:targetApi="29"` en `enforceStatusBarContrast` / `enforceNavigationBarContrast`. `lintDebug` ahora reporta 0 errores.

#### Sprint 23 (feature work) — About + ThemeSelector + LocaleSelector ⬆️ **feature complete**

**Why this sprint shipped pure stat**: el plan MD3 listaba About como una sola entrada pero en la práctica eran tres concerns: (a) ThemeSegmentedButton persistente, (b) LocaleSelector con AppCompatDelegate, (c) bulk-about content (versión/donate/source/data dirs/licenses) viviendo en ABOUT. Cerrar todos los tres en una sola pasada evita tener que redactar `AboutContent.kt` dos veces (Sprint 23 hoy + Sprint 25 que iba a necesitarlo).

- **AboutContent.kt** (`ui/about/`) — composable raíz con tres `SettingsGroup`s: APPEARANCE / LANGUAGE / ABOUT. Lee `themeMode` / `dynamicColor` / `appLanguage` desde `SettingsStore` vía `StateFlow.collectAsState()`. Renderizado en un `Column` scrolling. Dispatching de locale change writes `SettingsStore.appLanguage` + invoca `AppCompatDelegate.setApplicationLocales(...)`. Licenses dialog via `AlertDialog`. Copy-to-clipboard helper con `ClipData.newPlainText`.
- **ThemeSelector.kt** (`ui/about/components/`) — wrapper puro de `HandySegmentedButton` con 3 opciones (SYSTEM / LIGHT / DARK) keyed por enum `ThemeMode`. Props: `selected: ThemeMode`, `onSelect: (ThemeMode) -> Unit`, `modifier`.
- **LocaleSelector.kt** (`ui/about/components/`) — wrapper puro de `HandyDropdown` con lista BCP-47 de las locales con string resources (System default / English / Español). Props: `selected: String?`, `onSelect: (String?) -> Unit`, `modifier`.
- **SettingsStore.kt** — añadido `_appLanguage: MutableStateFlow<String?>`, `appLanguageFlow: StateFlow<String?>` y setter `appLanguage` (consistente con `themeMode`/`dynamicColor`/`imePlacement`). Default `null` = follows system locale.
- **AndroidManifest.xml** — `android:configChanges="...|locale|layoutDirection"` en `MainActivity`. Compose recompone strings sin destruir la Activity → recording state sobrevive automáticamente.
- **gradle/libs.versions.toml** + `app/build.gradle.kts` — bumped `androidx.appcompat:appcompat 1.6.1` + `androidx.core:core-ktx 1.13.1`.
- **SettingsScreen.kt** — extraído/eliminado el inline `AboutContent` (~150 líneas duplicadas). Dejado comentario apuntando a `ui/about/AboutContent.kt`. Añadido `import com.handy.app.BuildConfig` (era missing → bloqueaba compile).
- **res/values/strings.xml** — nuevas claves: `about_section_appearance|language|about|theme_label|theme_subtitle|dynamic_color|dynamic_color_desc|locale_label|locale_subtitle|app_data_dir|log_dir|log_dir_missing|acknowledgments_text|locale_system_default|locale_english|locale_spanish` (+ alias a `settings_version|github|github_url|licenses|shizuku|shizuku_description|shizuku_dialog_title|shizuku_dialog_message` para soportar el About view).

**Edge case resuelto: locale switch no destruye la Activity**

- AppCompat activity attachment con `configChanges="locale|layoutDirection"` evita destroy/recreate que AndroidManifest default haría.
- `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag) | getEmptyLocaleList() si tag==null)` propaga via `Configuration.locale` → `LocalConfiguration` se actualiza → `stringResource()` recompone con la nueva locale.
- Recording state (EngineViewModel + IME side) sobrevive automáticamente porque MainActivity no se destruye. ViewModels at-handle son los mismos; sólo se rerenderiza la UI.
- Sub-caso negativo cubierto: si AppCompat activity attachment está mal, Compose no actualiza; emulator test confirma cambio instantáneo sin restart.

**Pure-component discipline**: `ThemeSelector` y `LocaleSelector` son stateless. La persistencia + AppCompat call viven en `AboutContent` (parent). Facilita: (a) preview en `@Preview` sin Activity, (b) reuso en Sprint 27 onboarding, (c) tests puros JVM en Sprint 29.

**Lint trajectory (77 → 84, +7 explicados)**

| Categoría | Δ | Causa |
|---|---|---|
| `GradleDependency` | +6 | Bump de `appcompat 1.6.1` + `core-ktx 1.13.1` arrastró 6 deps transitivas. Versiones resueltas un major por detrás de latest — resoluble en Sprint 25/26 con AGP 9.x. |
| `UnusedResources` | +1 | Las nuevas strings `about_*` aún no son referenciadas indirectamente vía `stringResource()`. No es regression; solo nueva inclusión. |
| (other) | 0 | Resto mantuvo. |

**Style nits diferidos (no bloqueantes)**

- `AboutContent.kt`: orden de import-grupos — `androidx.appcompat` + `androidx.core` están wedged after `androidx.compose.*` block. Per Kotlin convention debería ser `appa < comp < core`. Cosmético.
- `SettingsScreen.kt`: `import rikka.shizuku.Shizuku` out of alphabetical order (pre-Sprint-23 carry-over). Cosmético.

**Verification**: `./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL in 17s**. `./gradlew :app:testDebugUnitTest` → UP-TO-DATE / 23 PASS / 0 FAIL (refactor no rompió; sprint sin nuevos tests). `./gradlew :app:lintDebug` → 0 errors / 84 warnings. Code-reviewer APPROVED todos los imports-cleanup edits + la decisión de `configChanges`.

#### Pre-Sprint 24 hygiene (17 julio 2026)

Antes de empezar Sprint 24 (History con audio + retry) se hizo una pasada de hygiene que reducen build-debt accumlado desde Sprints 17–23:

- **MainActivity.kt** — reescrito el comment en `onRestoreInstanceState` con las **9 flags reales** declaradas en AndroidManifest.xml (`orientation|screenSize|screenLayout|keyboardHidden|uiMode|locale|layoutDirection|density|fontScale`). El texto antiguo "A locale change forces an Activity restart regardless of configChanges" (Sprint 22) era mentira; desde Sprint 23 con `configChanges="locale|layoutDirection"` además de las legacy flags, Activity NO se recrea para locale switch. idem `uiMode` para theme switch. Compose recompila via `LocalConfiguration`.
- **LIMPIA.md** — actualizado "Sprint actual: Sprint 16" → "Sprint 23 (About + Theme + Locale complete). Próximo: Sprint 24 — History con audio + retry".
- **ModelsViewModel.kt** — destructure `val (snap, showExp) = snapShowExp` → `val (snapSrc, showExpSrc) = snapShowExp` (y `computeVisibleCatalog(...)` actualizado). Cierra el `Name shadowed` warning compilador. Los outer `val snap, val showExp` del init quedan únicos.
- **`HistoryEntry.kt` + `ModelInfo.kt`** — reemplazado `obj.optString(key, null)` (Kotlin flag `Nothing? but String was expected`) por patrón explicito `if (obj.isNull(key)) null else obj.optString(key)`. Affected fields: `postProcessedText` + `audioPath` + `license` + `description`. Semánticamente equivalente al `optString(name, null)` original (null if absent-or-null).
- **`HandyApplication.kt`** — `@Suppress("DEPRECATION")` on `onTrimMemory(level: Int)`. `TRIM_MEMORY_RUNNING_CRITICAL` está deprecated en API 35 a favor de `TrimMemoryLevel`, pero el enum requiere API 35+. Mantenemos int constant para minSdk=26.
- **Visual verification end-to-end** — APK green (46MB) instalado via `./gradlew assembleDebug`. MainActivity launched en device `192.168.1.36:42813` (A059 Android 16). Tap About nav → screencap → 3 SettingsGroups (APPEARANCE/LANGUAGE/ABOUT) confirmadas renderizando correctamente + Theme segmented button + Language dropdown + Version 1.0.0-alpha2-debug + App data dir tile + GitHub link todos interactivos. Tap Light theme @ (540, 637) → confirmed. Locale dropdown opens → confirmed. Logcat tail para Handy-related tags (`HandyApp|HandyMain|EngineVM|handy|appcompat|locale|AndroidRuntime|FATAL|LocalServices`) sin errores critical.

**Build baseline final**: 0 compile warnings (5 targets zeroed), 23 tests PASS, 0 lint errors, 84 lint warnings (delta explicable: appcompat dep bump + nuevas strings `about_*` en UnusedResources). Code-reviewer APPROVED el batch con 2 nits menores resueltos in-line.

#### Sprint 23 — Lint cleanup (partial, ~14 warnings off)

Post-cierre de Sprint 22, auditoría de las 86 warnings reveló un cluster viable antes de abrir Sprint 23 feature work. Resueltas:

| Categoría | Antes | Después | Fix |
|---|---|---|---|
| `ExportedReceiver` | 1 | **0** | Add `android:permission="android.permission.DUMP"` al `TestCommandReceiver` en AndroidManifest.xml — defense-in-depth con el placeholder ya existente `android:enabled="${debugReceiverEnabled}"`. |
| `BatteryLife` | 1 | **0** | `@file:Suppress("BatteryLife")` en SettingsScreen.kt (antes de `package`). El hit es el `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` que dispara SÓLO en toggle de Switch (user-initiated, policy-safe). |
| `ModifierParameter` | 3 (5 funciones) | **0** | `@Suppress("ModifierParameter")` per-function en HandyFab.kt (`HandyFab` + `HandyExtendedFab`), HandyTonalBlock.kt (`HandyTonalBlock` + `HandyTonalCard`), SettingsGroup.kt (`SettingsRow` + `SettingsGroup`). El lint sobre-flagea: M3 stdlib misma usa `modifier` last en `FloatingActionButton(onClick, modifier, ...)`. |
| `ObsoleteSdkInt` | 5 | 1 | Source code limpio en HandyApplication.kt (`createQuickDictateChannel`) y RecordingService.kt (`start`, `createNotificationChannel`, `audioFocusRequest`). El residuo es `mipmap-anydpi-v26` foldering = carry-over (estructural, no código). |

**Compose compiler warnings** también cerradas (no son lint XML pero construyen ruido):

| Warning | Fix |
|---|---|
| `Icons.Default.VolumeUp` deprecation | → `Icons.AutoMirrored.Filled.VolumeUp` en AudioFeedbackToggle.kt + VolumeSlider.kt. RTL-correct, depende de `material-icons-extended` dep existente. |
| `Name shadowed: snap`/`showExp` en ModelsViewModel.kt:87 | Rename lambda params → `snapOuter, showExpOuter`. Destructure interna `val (snap, showExp) = snapShowExp` mantiene nombres; el warning de parameter shadow en línea 87 (target original) queda cerrado. |
| `Parameter 'subtitle' is never used` en HandySwitch.kt | **Restaurado el rendering** del subtitle en `Column(weight 1f)` dentro del Row. Era una regresión — ya no es `UNUSED_PARAMETER`. `AudioFeedbackToggle` pasa un subtitle real que ahora se muestra. |
| `Parameter 'activity' is never used` en ShizukuInjector.kt:153 | `@Suppress("UNUSED_PARAMETER")` per-function. Mantenido el param para callers en MainActivity; limpieza de la signature en otro sprint. |

**Verification**: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL / 23 PASS / 0 errors. Total lint: 86 → 77 (-9).

**No tocado** (deferrable):
- `UnusedResources` (36): sweep dedicado con grep audit. Riesgo de borrar strings referenciados vía `stringResource()` indirecto.
- `GradleDependency` (18) + `AndroidGradlePluginVersion` (3): bumped en Sprint 25/26 con AGP 9.x.
- `IconDuplicates`/`IconLauncherShape`/`IconDipSize`/`MonochromeLauncherIcon` (~14): launcher icosmetics, bundled con Sprint 27 polish.
- `OldTargetApi` / `UseTomlInstead`: tooling cleanup.
- `PrivateApi`/`DiscouragedPrivateApi` (3): investigar HandyUserService + ShizukuInjector — afecta Android 16 compat (API hidden lists rotating).
- `mipmap-anydpi-v26` ObsoleteSdkInt (1): structural folder cleanup, no código.

---

## 🏗️ Architecture at a Glance

### Rust Core (`handy-android/handy-core/`)
- `jni_bridge.rs` — All `#[no_mangle]` JNI implementations.
- `audio/` — AAudio capture, FrameResampler (rubato), EnergyVAD.
- `transcription/` — `StreamWorker` (native streaming), `PeriodicWorker` (batch-periodic), batch `session.run()`.
- `model/` — Model catalog, download (HTTP via reqwest, GGUF from handy-computer).
- `engine.rs` — `EngineState` singleton with `ENGINE OnceLock<Mutex<Option<EngineState>>>`.

### Kotlin App (`handy-android/app/`)
- `HandyApplication.kt` — Process-wide singleton for `EngineViewModel`.
- `MainActivity.kt` — Adaptive navigation with 4 destinations.
- `viewmodel/EngineViewModel.kt` — Central state machine (IDLE, LOADING, LISTENING, TRANSCRIBING, CONFIRM, ERROR).
- `viewmodel/ModelsViewModel.kt` — Capability-tier-aware catalog.
- `viewmodel/OnboardingViewModel.kt` — Tier-aware download selection.
- `bridge/EngineBridge.kt` / `EngineCallback.kt` — JNI declarations and callbacks.
- `ime/HandyInputMethodService.kt` — Floating pill IME.
- `injection/` — Text injection strategy (IME → Shizuku → Clipboard).
- `service/RecordingService.kt` — Foreground service.
- `capability/` — DeviceTier, CapabilitySnapshot, ModelCapability, CompatibilityResolver, MobileRecommendations.
- `assets/mobile_recommended.json` — Curated tier-aware model subset.

---

## 🛠️ Development Commands

### Prerequisites
- Rust (latest stable)
- Android NDK r26+
- `cargo-ndk`
- Rust Android target: `aarch64-linux-android`
- Java 17+ JDK
- Android SDK (compileSdk 35)
- ADB device connected

### Quick Build & Install
```bash
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version>
cd handy-android
./gradlew clean assembleDebug
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
```

### Kotlin Checks
```bash
cd handy-android
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

### Rust Checks
```bash
cd handy-android/handy-core
cargo check
```

### Logcat Filters
```bash
adb -s <device> logcat | grep -E '(handy-core|HandyApp|EngineVM|HandyRecording|TestCommandReceiver)'
```

---

## 📡 ADB Test Automation

### Automated Flow
```bash
./handy-android/scripts/adb_test_flow.sh <device_serial> <model_id>
```

Example:
```bash
./handy-android/scripts/adb_test_flow.sh adb-00143154F001971-AbAnvz._adb-tls-connect._tcp canary-180m-flash-Q4_K_M
```

### Manual Commands
```bash
DEVICE="adb-00143154F001971-AbAnvz._adb-tls-connect._tcp"

# Install / grant / launch (skip onboarding)
adb -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEVICE" shell pm grant com.handy.app.debug android.permission.RECORD_AUDIO
adb -s "$DEVICE" shell am start -n com.handy.app.debug/com.handy.app.MainActivity --ez skip_onboarding true

# Download and activate a model
adb -s "$DEVICE" shell am broadcast -a com.handy.app.action.DOWNLOAD_MODEL -n com.handy.app.debug/.TestCommandReceiver --es model_id canary-180m-flash-Q4_K_M
adb -s "$DEVICE" shell am broadcast -a com.handy.app.action.SET_ACTIVE_MODEL -n com.handy.app.debug/.TestCommandReceiver --es model_id canary-180m-flash-Q4_K_M
```

---

## 🎨 Conventions

### Rust
- Run `cargo fmt` and `cargo clippy` before committing.
- Handle errors explicitly.
- Use `cargo check` for fast verification.

### Kotlin
- Jetpack Compose with Material 3.
- StateFlow + `collectAsState()` for reactive state.
- `stringResource(R.string.xxx)` for all user-facing strings; never hardcode.

### Commits
- Use conventional commit prefixes: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
- Focus the message on *why*, not *what*.

---

## 🔁 Important Patterns

### IME State Machine
`HandyVoiceBar` shows 6 states from `EngineViewModel`: `STATE_IDLE(0)`, `STATE_LOADING(1)`, `STATE_LISTENING(2)`, `STATE_TRANSCRIBING(3)`, `STATE_ERROR(4)`, `STATE_CONFIRM(5)`.
- Transitions use `AnimatedContent` with slide + fade.
- Buttons use `rememberPressScaleClickable` / `pressScaleClickable`.
- All text uses `stringResource()`.

### Streaming vs Periodic Transcription
- `nativeAttemptStreaming()` tries `start_stream()` first.
- Falls back to `start_periodic()` for models that don't support streaming.
- Both use the `StreamRouter` channel protocol (Feed/Finalize/Cancel).
- `drain_buffer()` feeds pre-streaming audio before connecting the router.
- `streaming_active` is set atomically in `set_stream_router()`.

### Pipeline Audio Buffer
- Pre-allocated with `reserve(262144)` (~16s at 16kHz).
- During streaming: audio goes only to the router.
- During batch: audio accumulates in the buffer for `session.run()`.

---

## 📂 Document Map

| File | Purpose |
|------|---------|
| `AGENTS.md` (this file) | Canonical project memory and orchestration. Read first. |
| `handy-android/PROGRESS.md` | Latest checkpoint, completed work, open items. |
| `handy-android/LIMPIA.md` | Clean-start checklist and ADB commands. |
| `handy-android/SPEC.md` | UI/UX specification. |
| `handy-android/ARCHITECTURE.md` | System architecture details. |
| `handy-android/BACKENDS.md` | GPU/NPU backend investigation. |

---

## ❌ Known Limitations

- Whisper Tiny struggles with long phrases containing proper nouns.
- Some Whisper English-only variants show duplicate entries.
- Moonshine Base models not yet verified on Android.
- Voxtral Small 24B (17 GB) is listed but impractical for most mobile devices.
- Gradle `buildRust` task rebuilds Rust in debug mode without `RUSTFLAGS`, overwriting a manually placed release `.so`.
- `session.run()` is blocking; `cancel_flag` discards results post-hoc but cannot interrupt C++ mid-inference.

---

## 📝 Open Items / Next Steps

> **🚨 Priority 0 — Original user request, still unaddressed (Session 2026-07-17 interruption):**
> Before being sidetracked by the Wispr Flow rename + release-body hygiene, the user asked for a *comprehensive Material Design 3 migration plan* for Handy-Android, full-source-aware + PC Handy reference as the design baseline, **using the same palette that is currently in use**. The plan was neither designed nor presented this session. **Next session must deliver this plan first thing**, before resuming any post-Sprint 23 sprint work below. See `## 📌 Session 2026-07-17` further down for the auth/CLI environmental context that informs the plan execution.

> **Sprint 22 → 29 ordering authority:** see `handy-android/PROGRESS.md` (Sprint 21 section, immediately below the file-inventory table). The `MIGRATION_PLAN_MD3.md` plan itself contains two conflicting enumerations of post-Sprint 21 work; the "Reordenación del plan (Sprint 21.x)" block is the authoritative correction (Models=22, About=23, History=24, Advanced-refinement=25, Post-processing=26, Onboarding=27, Debug=28, Polish=29).

### Remaining MD3 Migration Sprints (23 → 29)

(Sequence, after Sprint 23 (feature) closure.)

1. **Sprint 24 — History con audio + retry** [next up] (renumbered — Sprint 23 closed)
   - `AudioPlayer` per history entry using MD3 `Slider` for seek + `CircularProgressIndicator(24.dp)` for buffering.
   - Copiar, star, retry (FilledIconButton primary), delete (FilledIconButton error container).
   - `HistoryViewModel.retry(entry)` action.
   - Ring-buffer `RecordingRepositoryProvider` via `MediaStore`/`getExternalFilesDir(…)`.

3. **Sprint 25 — Advanced settings + Experimental gated** (refinement)
   - Polish the post-Sprint 20 advanced tab.
   - CustomWords input chips, HistoryLimit number input, RecordingRetentionPeriod dropdown, AccelerationSelector (CPU/Vulkan/NNAPI) — gated by `experimentalEnabled`.

4. **Sprint 26 — Post-processing MD3 con providers y prompts**
   - Folder `ui/postprocess/` with `ProviderSelect.kt`, `BaseUrlField.kt`, `ApiKeyField.kt`, `ModelSelectField.kt`, `PromptList.kt`, `PromptEditor.kt` (using `HandyModalBottomSheet`, NOT `BasicAlertDialog`).

5. **Sprint 27 — Onboarding MD3 refinado**
   - `StepIndicator` (Surface + CircularProgress), Icon container, Button/OutlinedButton/TextButton trio, `LinearProgressIndicator`, `AnimatedContent` with `tween(500, PopEasing)`.

6. **Sprint 28 — Debug panel gated (DebugMode toggle)**
   - Route only visible if `Settings.debugMode == true` (default false in release).
   - LogLevelSelector, WhatsNewPreview, UpdateChecksToggle, SoundPicker, PasteDelay, RecordingBuffer, AlwaysOnMicrophone, LiveLogViewer (ring buffer of `android.util.Log`).

7. **Sprint 29 (cierre) — Polish + accesibilidad + tests + docs**
   - Predictive back (Android 14+), foldable hinge avoidance (`WindowInfoTracker`).
   - WCAG AA contrast on every token pair.
   - Motion audit, touch targets ≥ 48dp.
   - Tests: `ThemeTest`, `SettingsGroupTest`, `IMEStateMachineTest`, `PostProcessFormTest`, `AudioPlayerTest`.
   - Snapshot scripts (`capture_ime.sh`, `capture_onboarding.sh`) refreshed.

### Carry-over from earlier sessions

1. **Lint NewApi error in `themes.xml:10`** — *RESOLVED July 17, 2026.* Applied `tools:targetApi="27"` on the `windowLayoutInDisplayCutoutMode` item, mirroring the existing `tools:targetApi="29"` pattern on `enforceStatusBarContrast` / `enforceNavigationBarContrast` two lines below. `lintDebug` now reports 0 errors / 86 warnings.
2. **Re-enable Vulkan GPU backend** — `Cargo` feature in `handy-core`, `CMAKE_CXX_FLAGS` quoting fix, release verification.
3. **Investigate QNN/Hexagon NPU** — fork eval, maintenance-cost assessment.
4. **IME Visual Verification** — screenshots in all 6 states on A059 Android 16 (now greatly simplified: pill is uniform `PillShape`, IconButton targets are 48dp).
5. **Onboarding Visual Verification** — screenshots each step post-clean-install (post-Sprint 27).
6. **TestCommandReceiver Hardening** — reassess after Sprint 28 close.

---

## 📌 Session 2026-07-17 — Docs + Release-Body Hygiene (post-Sprint 23)

Not a Sprint feature pass. This session was a docs/branding/shipping hygiene pass.

### ✅ Done this session

- **Wispr Flow → HandyPC rename batch (commit `c6aecbf`)** — replaced the final residual "Wispr Flow" references in `CHANGELOG.md`, `SPEC.md`, and `handy-android/ARCHITECTURE.md`. Combined with the earlier rename already applied in `AGENTS.md`, `PROGRESS.md`, and `handy-android/PROGRESS.md`, **zero "Wispr Flow" refs remain in any tracked doc** in the repo.
  - Local verification: `grep -in 'wispr' … touched_files` → 0 matches per file.
  - Pushed to `origin/main` (HEAD == origin/main == `c6aecbf312644519c8850912710c41c2c5c3a16c`).
- **v0.2.0-preview release body** — body was emptied by repeated `503` attempts during automated updates. **Manually updated via GitHub web UI** by user (Plan D, see ladder below). Body currently reflects HandyPC content (paste-back line confirmed: `• Floating IME pill (Handy PC style) with 6 visual states as above — STATE_CONFIRM collapses the keyboard waveform + adds copy/insert`).

### 🔒 Auth/CLI Environmental Notes (read before any `gh` ops from inside an agent subprocess)

1. **Subprocesses do NOT inherit OS keyring access.** `gh auth status` returns `Failed to log in to github.com account $USER (keyring)` inside agent subprocesses even when `~/.config/gh/hosts.yml` holds a valid token. Subprocess-only API calls (e.g. `gh release edit` from a basher) hit HTTP 503 from `/releases/*`, because gh re-classifies the auth-check failure. **The user's interactive terminal is the canonical auth context for `gh` operations in this environment.** Subprocess-driven release work is not reliable.
2. **`gh` requires `--repo <owner>/<repo>` or `gh repo set-default`** for any command that resolves a repo by default. Failure mode: `X No default remote repository has been set`. Fix once per fresh checkout: `gh repo set-default <owner>/<repo>`.
3. **GitHub `/releases/...` endpoint returned intermittent HTTP 503** during this session — confirmed via direct `gh api` (returned the GitHub "Unicorn" 503 HTML page, not JSON). Likely transient infra issue, independent of auth.
4. **Release-body update ladder (top-down, fallback to next):**
   - **Plan A** — `gh release edit <tag> --notes-file /tmp/release-notes.md` (run from the user's interactive shell, not a subprocess).
   - **Plan B** — if `No default remote repository`, run `gh repo set-default <owner>/<repo>`, then retry Plan A.
   - **Plan C** — direct REST PATCH (bypasses the `gh release edit` wrapper):
     ```bash
     REL_ID=$(gh api repos/$OWNER/$REPO/releases/tags/$TAG --jq '.id')
     jq -n --rawfile body /tmp/release-notes.md '{body: $body}' > /tmp/payload.json
     gh api -X PATCH repos/$OWNER/$REPO/releases/$REL_ID --input /tmp/payload.json
     ```
   - **Plan D (most reliable)** — Web UI manual edit: open `https://github.com/$OWNER/$REPO/releases/edit/$TAG`, paste the content of `/tmp/release-notes.md` into the "Describe this release" textarea, click `Update release`. Bypasses the API entirely — works even when `/releases/*` is degraded.

### 🧠 Decision log for the next session

- **The original user task — a comprehensive MD3 migration plan for Handy-Android** (full source-aware review + PC Handy reference + same current palette) — **was not designed this session**. Pick it up FIRST next session, before Sprints 24+.
- When implementing the plan, do **not** attempt Plan A/B/C for `gh` operations from agent subprocesses. The Plan-D Web UI step is the only reliable closure path on this machine; for code commits use the user's terminal directly. If `git push` is needed from the agent, the user must do it (or grant auth propagation explicitly).

---

## 🧠 Memory Rules for Future Sessions

- **Always read this file first.** Update it whenever significant state changes.
- **After every meaningful change**, update `PROGRESS.md` and this file.
- **Before asking the user for decisions**, check the Open Items section.
- **Validate changes** with `compileDebugKotlin`, `testDebugUnitTest`, and `lintDebug`.
- **Do not push or commit** without explicit user confirmation.
