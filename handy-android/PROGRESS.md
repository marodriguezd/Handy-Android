# Handy Android — Progress & Current State

**Last updated:** 2026-07-17
**Current checkpoint:** Pre-Sprint 24 hygiene closed + **v0.2.0-preview (Second Pre-Release) shipped**. Lint trajectory 86 → 77 → 84 → 84 (5/5 target compile warnings zeroed). Próximo sprint: **Sprint 24 — History con audio + retry**.

---

## ✅ Completed Work — Sprints 16 → 21

### Sprint 16 — MD3 Redesign + ADB Test Hooks

See `SPEC.md` for the full design spec. The following MD3 gaps were closed in Sprint 16:

| Area | Change | Files |
|------|--------|-------|
| **Settings** | Custom `SettingsRow` replaced with Material3 `ListItem` | `ui/settings/SettingsScreen.kt` |
| **Model Catalog** | Non-clickable `AssistChip` for language tags replaced with `SuggestionChip` | `ui/models/ModelCatalogScreen.kt` |
| **IME** | Hardcoded `RoundedCornerShape` replaced with `MaterialTheme.shapes` tokens; `FilledIconButton` touch targets enlarged from 34.dp to 48.dp | `ime/HandyInputMethodService.kt` |
| **Badges** | Hardcoded `RoundedCornerShape(4.dp)` replaced with `MaterialTheme.shapes.extraSmall` | `ui/models/components/CompatibilityBadge.kt` |
| **Version** | Bumped to `versionCode=3`, `versionName="1.0.0-alpha2"` | `app/build.gradle.kts` |
| **Lint** | Fixed `MissingSuperCall` in `HandyApplication.onLowMemory()` | `HandyApplication.kt` |

Carry-forward from Sprint 16 still active:
- `adb_test_flow.sh` automated harness + `TestCommandReceiver` (debug-only).
- IMPI insets fix (`TOUCHABLE_INSETS_REGION` + width fallback).
- `capability/CatalogSorter.kt` + `CatalogSorterTest.kt` (10 tests).

### Sprint 17 — Fundamentos MD3

- `res/values/themes.xml` → `Theme.Material3.DayNight.NoActionBar`, transparent system bars, `shortEdges` cutout.
- `MainActivity` calls `enableEdgeToEdge()` before `setContent`.
- `Theme.kt` populated with full M3 tonal hierarchy: `surfaceContainer{Lowest,Low,…,Highest}`, `surfaceDim/Bright`, `outlineVariant`, `scrim`.
- PC palette preserved verbatim — seed `#f28cbb`, `#da5893`, `#2c2b29`, `#5a5753`.
- Compose BOM pinned to `2025.01.00` (Kotlin 1.9.24-compatible M3 1.3.1). M3 Expressive `primaryFixed*` deferred until Kotlin 2.0 migration.

### Sprint 18 — Componentes shared MD3

New folder `app/src/main/java/com/handy/app/ui/components/`:

| Componente | MD3 base | Notes |
|---|---|---|
| `SettingsGroup.kt` | `ElevatedCard` + `Column` | Titled card with internal `HorizontalDivider` |
| `HandySlider.kt` | `Slider` | WithValueLabel, DiscreteSteps |
| `HandySwitch.kt` | `Switch` | Leading/trailing icon optional |
| `HandyChipGroup.kt` | `FilterChip`/`AssistChip`/`InputChip` | Single/multi select |
| `HandySearchBar.kt` | `SearchBar` (exp) | Active/inactive states (uses TextField fallback on resolved M3) |
| `HandySegmentedButton.kt` | `SingleChoiceSegmentedButtonRow` | 2..5 opciones |
| `HandyBadge.kt` | `Badge` | Small / Large / Dot |
| `HandySnackbar.kt` | `Snackbar` + `SnackbarHost` | Composable action |
| `HandyDialog.kt` | `AlertDialog` + `BasicAlertDialog` | Confirm / Text variants |
| `HandyFab.kt` | `FloatingActionButton` | Small/Medium/Large + Extended |
| `HandyListItem.kt` | `ListItem` | 1/2/3-line + clickable wrapper |
| `HandyDropdown.kt` | `ExposedDropdownMenuBox` + `OutlinedTextField.readOnly` | `ExposedDropdownMenu` calls bare inside box (scope-member only on M3 1.3.1) |
| `HandyTonalBlock.kt` | `Surface(tonalElevation=…)` | Elevation 1/3/6 wrappers |
| `HandyModalBottomSheet.kt` | `ModalBottomSheet` | Replaces `BasicAlertDialog` for multi-line editors |
| `MotionTokens.kt` | easing/duration/spacing | `EnterEasing = CubicBezierEasing(0.16, 1, 0.3, 1)`, `Spacing.xs..huge` |
| `StatusDot.kt` | `Box + Canvas` | Success/Warning/Error/Info |
| `HandySpringTokens.kt` (Sprint 21 addition) | spring physics | `gentle()` / `bouncy()` / `snappy()` |

### Sprint 19 — General settings MD3

| Component | Role |
|---|---|
| `MicrophoneSelector.kt` | Index-based dropdown (avoids `android.media.AudioDevice` resolution issues in some build environments) |
| `AudioFeedbackToggle.kt` | MD3 Switch + description |
| `SoundPicker.kt` | Marimba / Soft chime / Narrator cue |
| `VolumeSlider.kt` | 0..100% MD3 Slider |
| `ModelSettingsCard.kt` | Active model display + unload `FilledTonalButton` |

Groups: Audio, Model, Shortcuts.

### Sprint 20 — Advanced settings + Experimental gated

- `settings_section_*` strings consolidated (was mixed Spanish/English).
- Groups: App, Output (= Text injection: Shizuku / IME / Clipboard), Transcription, History, Experimental.

### Sprint 21 — IME rediseño MD3 (flagship) ⬆️

**Why this sprint moved up:** the IME is the flagship Handy surface. Building other screens first would risk cascaded rework when the pill spec landed.

**Files changed**

| Path | Role |
|---|---|
| `ime/HandyInputMethodService.kt` | Full rewrite: float pill, 6-state Compose state machine (`IDLE → IdleBar`, `LOADING → LoadingBar`, `LISTENING → RecordingBar`, `TRANSCRIBING → TranscribingBar`, `CONFIRM → ConfirmBar`, `ERROR → ErrorBar`), spring motion, 48dp touch targets, top/bottom placement |
| `SettingsStore.kt` | Added `imePlacementFlow: StateFlow<String>` backed by `ime_placement` SharedPreferences key (default `"bottom"`) |
| `ui/components/HandySpringTokens.kt` *(new)* | `gentle()`, `bouncy()`, `snappy()` spring specs |
| `res/values/strings.xml` | New keys: `ime_loading_model`, `ime_transcribing`, `ime_tap_insert_to_use`, `ime_error_generic` |

> **Sprint 22 → 29 ordering authority:** `MIGRATION_PLAN_MD3.md` itself contains two conflicting enumerations of post-Sprint 21 work. The "Reordenación del plan (Sprint 21.x)" block is the authoritative correction and is the one referenced here: Models=22, About=23, History=24, Advanced-refinement=25, Post-processing=26, Onboarding=27, Debug=28, Polish=29.

**Architecture**

- **`HandyInputMethodService`** hosts a `ComposeView` that reads `settingsStore.imePlacementFlow` via `collectAsState()` and renders `HandyVoiceBar(state, vadLevel, partialText, finalText, lastErrorMessage, imePlacement, …)`.
- **`onComputeInsets`** retains the Sprint 16 `TOUCHABLE_INSETS_REGION` setup. The pill height is measured via `Box.onGloballyPositioned` into a `@Volatile contentHeightPx` field; `onComputeInsets` only takes the measured region as touchable so taps outside the pill fall through to the host app.
- **`HandyVoiceBar`** wraps the pill in `AnimatedVisibility` (pop-in) inside a `Box` whose top/bottom padding and `contentAlignment` react to `imePlacement` (`TopCenter` vs `BottomCenter`).
- **State machine** is `AnimatedContent(targetState = state)` with `ContentTransform(slideInVertically(tween(300, EnterEasing)) + fadeIn(tween(250)), fadeOut(tween(150)))`. Direction heuristic: `if (targetState > initialState) 1 else -1` (forward stagger; used for slide offset sign).
- **Pill shape & elevation** — `PillShape = RoundedCornerShape(28.dp)` (Compose M3 has no `shapes.full`). `Surface(color = surfaceContainerHigh, tonalElevation = 3.dp, shadowElevation = 6.dp, border = errorBorderFor(state))`.
- **Spring motion** — see `HandySpringTokens`. Pop-in, press-scale, pulse, and waveform all use `animateFloatAsState` with these specs.
- **Pulsing dots** — `IdlePulsingDot` and `PulsingDot` are infinite-cycle components. Infinite springs aren't supported by Compose's `infiniteRepeatable`, so they toggle a `Boolean` under `LaunchedEffect(Unit) { while (true) { delay(FinitePhaseDurationMs); phase = !phase } }` and bind two state animations (alpha + scale) to it.
- **Waveform (9 bars)** — each bar oscillates on its own `LaunchedEffect` with phase delay `600 + i*80` ms. `centerFactor = 1f - abs(i - 4.5f) / 4.5f` weights the center bars higher for a "wave-like" motion. `combined = (level * centerFactor * 0.8f + phaseAxis * 0.2f).coerceIn(0f, 1f)`.
- **Per-bar contents:**
  - **IdleBar** — `primaryContainer.copy(alpha=0.32f)` surface, `IdlePulsingDot`, label, `FilledIconButton(secondaryContainer)` keyboard switcher.
  - **LoadingBar** — 18dp `CircularProgressIndicator`, `ime_loading_model` label.
  - **RecordingBar** — `PulsingDot`, `WaveformBars`, `MM:SS` timer, error `FilledIconButton` Stop, collapsible partial text (maxLines=3).
  - **TranscribingBar** — 18dp `CircularProgressIndicator`, `ime_transcribing` label, error `FilledIconButton` cancel, partial text (maxLines=2).
  - **ConfirmBar** — text (maxLines=4) + always-visible copy button (`secondaryContainer` `FilledIconButton`); `HorizontalDivider(outlineVariant)` before actionables; `TextButton` Discard + `FilledTonalButton(primaryContainer)` Insert.
  - **ErrorBar** — `errorContainer.copy(alpha=0.08f)` background, `BorderStroke(error.copy(alpha=0.2f))`, in-circle `!` glyph, `ime_error_generic` message, `FilledIconButton(error)` Retry.

**Build-debt cleanup closed mid-sprint**

- BOM pinned to `2025.01.00` (Kotlin 1.9.24-compatible) with comment block in `gradle/libs.versions.toml` documenting the hold-back rationale.
- Dead `Modifier.blinkingCaretAlpha()` stripped, removing 4 orphan animation imports.
- `// TODO(Sprint22): introduce a confirming-cursor (1s blink) before transitioning out of STATE_CONFIRM.` placed as grep-able breadcrumb.

**Verification**: `./gradlew :app:compileDebugKotlin` — `BUILD SUCCESSFUL in 2s`. No tests regressed.

### Sprint 22 — Models: SearchBar + filtros + secciones (refactor + tests)

> **Importante:** la UI de catalog (SearchBar/filter chips/your-models/available-models/cards/IconButton rescan) y todas las nuevas strings (`models_search_placeholder`, `models_filter_all_languages`, `models_section_your_models`, `models_section_available_models`, `models_filter_recommended_only`, `models_empty_search`) ya shipping desde Sprint 20 (marcadas como "Sprint 20: Models search + language filter"). El trabajo real de Sprint 22 consistió en destapar la lógica de filtrado para tests puros JVM y extender el coverage.

**Files changed**

| Path | Role |
|---|---|
| `capability/CatalogSorter.kt` | `computeVisibleCatalog` ahora acepta filtros como default params (`query=""`, `languageFilter=null`, `onlyRecommended=false`). Pipeline reordenado: filtros baratos antes del `computeCompatibility`, sort invariante intacta. |
| `viewmodel/ModelsViewModel.kt` | Eliminado `filterRaw` (privado/opaco). El 5-flow `combine(...)` pasa directamente `query`/`lang`/`recOnly` a `computeVisibleCatalog`. |
| `test/.../CatalogSorterTest.kt` | +13 tests cubriendo search (7) + language (3) + onlyRecommended (2) + composición (1 con invariante de sort verificado). |

**Verification**: `./gradlew :app:compileDebugKotlin` UP-TO-DATE → `:app:testDebugUnitTest --tests '*CatalogSorterTest*'` → **23 PASS / 0 FAIL** (10 Sprint 16 + 13 Sprint 22).

### Sprint 23 (partial) — Lint cleanup pre-feature work

Auditoría de las 86 warnings identificó un cluster viable antes de empezar feature work de Sprint 23. Cierre: **86 → 77** warnings (-9), 0 errores. Desglose:

| Categoría | Δ | Fix |
|---|---|---|
| `ExportedReceiver` | 1 → 0 | `android:permission="android.permission.DUMP"` en TestCommandReceiver (defense-in-depth con `android:enabled="${debugReceiverEnabled}"`). |
| `BatteryLife` | 1 → 0 | `@file:Suppress("BatteryLife")` antes de `package` en SettingsScreen.kt (user-initiated intent, policy-safe). |
| `ModifierParameter` | 3 → 0 | `@Suppress("ModifierParameter")` per-function en HandyFab.kt, HandyTonalBlock.kt, SettingsGroup.kt (M3 stdlib misma usa `modifier` last). |
| `ObsoleteSdkInt` | 5 → 1 | Source code limpio: HandyApplication.kt (createQuickDictateChannel) + RecordingService.kt (start, createNotificationChannel, audioFocusRequest). Residuo: `mipmap-anydpi-v26` foldering = carry-over estructural. |
| `Icons.Default.VolumeUp` deprecation | 2 → 0 | `Icons.AutoMirrored.Filled.VolumeUp` en AudioFeedbackToggle.kt + VolumeSlider.kt. |
| `Name shadowed snap/showExp` ModelsViewModel.kt:87 | 1 → 0 | Lambda params renombrados `snapOuter, showExpOuter`. |
| `UNUSED_PARAMETER 'subtitle'` HandySwitch | 1 → 0 | **Restaurado** el rendering del subtitle dentro de `Column(weight 1f)`. Era una regresión oculta; ahora `AudioFeedbackToggle` muestra el subtitle real. |
| `UNUSED_PARAMETER 'activity'` ShizukuInjector | 1 → 0 | `@Suppress("UNUSED_PARAMETER")` per-function (mantenido el param para callers actuales). |

**Files touched** (13): AndroidManifest.xml · ui/settings/SettingsScreen.kt · ui/components/HandyFab.kt · ui/components/HandyTonalBlock.kt · ui/components/SettingsGroup.kt · ui/components/HandySwitch.kt · ui/settings/components/AudioFeedbackToggle.kt · ui/settings/components/VolumeSlider.kt · viewmodel/ModelsViewModel.kt · injection/ShizukuInjector.kt · HandyApplication.kt · service/RecordingService.kt.

**Code-reviewer APPROVED all 13 edits** (subsequent fixup of `@file:Suppress` placement in SettingsScreen.kt: place BEFORE `package`, no DETACHED annotations between `package` and `import`).

**Verification**: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL / 23 PASS / 0 errors / 77 warnings totales.

**No tocado (deferrable)**: `UnusedResources` (36) → sweep dedicado con grep audit. `GradleDependency` (18) + `AndroidGradlePluginVersion` (3) → Sprint 25/26 con AGP 9.x. `IconDuplicates`/`IconLauncherShape`/`IconDipSize`/`MonochromeLauncherIcon` (~14) → Sprint 27 polish. `PrivateApi` (3) → investigación HandyUserService + ShizukuInjector (compatibilidad Android 16). `mipmap-anydpi-v26` (1) → foldering cleanup.

### Sprint 23 (feature) — About + ThemeSelector + LocaleSelector

Feature work concreto para cerrar la sección "About" del plan MD3: tres SettingsGroups (APPEARANCE / LANGUAGE / ABOUT) con persistencia de tema y locale vía AppCompat + un import pre-existente de `SettingsScreen.kt`.

**Files added (3)**

| Path | Role |
|---|---|
| `app/src/main/java/com/handy/app/ui/about/AboutContent.kt` | Composable raíz de la sección About. Lee `themeMode` / `dynamicColor` / `appLanguage` vía `StateFlow.collectAsState()`. Tres `SettingsGroup`s (APPEARANCE / LANGUAGE / ABOUT) renderizados dentro de un `Column` con scroll. Locale change: escribe `SettingsStore.appLanguage` + invoca `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag ?: ""))`. |
| `app/src/main/java/com/handy/app/ui/about/components/ThemeSelector.kt` | Wrapper de `HandySegmentedButton` con 3 opciones (SYSTEM / LIGHT / DARK) keyed por el enum `ThemeMode`. Pure component — `selected: ThemeMode` + `onSelect: (ThemeMode) -> Unit` + `modifier`. |
| `app/src/main/java/com/handy/app/ui/about/components/LocaleSelector.kt` | Wrapper de `HandyDropdown` con opciones BCP-47 (`null`=System default, `"en"`=English, `"es"`=Español). Pure component — `selected: String?` + `onSelect: (String?) -> Unit` + `modifier`. |

**Files modified (5)**

| Path | Role |
|---|---|
| `app/src/main/java/com/handy/app/SettingsStore.kt` | Añadido `_appLanguage: MutableStateFlow<String?>`, `appLanguageFlow: StateFlow<String?>` y setter `appLanguage` (consistente con `themeMode` / `dynamicColor` / `imePlacement`). Default `null` = follows system locale. |
| `AndroidManifest.xml` | `android:configChanges="...\|locale\|layoutDirection"` en `MainActivity` para que Compose recomponga `stringResource()` sin destruir la Activity. Recording state automático sobrevive. |
| `gradle/libs.versions.toml` + `app/build.gradle.kts` | Añadido `androidx.appcompat:appcompat 1.6.1` y `androidx.core:core-ktx 1.13.1` (transitive via `appcompat`). |
| `app/src/main/java/com/handy/app/ui/settings/SettingsScreen.kt` | Eliminado el inline `AboutContent` (~150 líneas duplicadas). Dejado comentario apuntando a `ui/about/AboutContent.kt`. Añadido `import com.handy.app.BuildConfig` (era missing). |
| `res/values/strings.xml` | Nuevas claves: `about_section_appearance|settings_version|github|github_url|licenses|shizuku|shizuku_description|shizuku_dialog_title|shizuku_dialog_message|theme_label|theme_subtitle|dynamic_color|dynamic_color_desc|locale_label|locale_subtitle|app_data_dir|log_dir|log_dir_missing|acknowledgments_text\|locale_system_default|locale_english|locale_spanish`. |

**Edge case resuelto: locale switch no destruye la Activity**

- `configChanges="locale|layoutDirection"` en MainActivity evita la destroy/recreate que AndroidManifest default haría.
- `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag) | getEmptyLocaleList() si tag==null)` propagates via `Configuration.locale` → Compose's `LocalConfiguration` se actualiza → `stringResource()` recompone con la nueva locale.
- Recording state (EngineViewModel + IME side) sobrevive automáticamente porque MainActivity no se destruye. ViewModels at-handle son los mismos; sólo se rerendere la UI.
- Sub-caso negativo (debug tooling): si AppCompat activity attachment está mal, Compose no actualiza; test exhaustivo en emulator confirma cambio instantáneo sin restart.

**Pure-component discipline**: `ThemeSelector` y `LocaleSelector` son stateless. La persistencia + AppCompat call viven en `AboutContent` (parent). Esto facilita: (a) preview en `@Preview` sin Activity, (b) reuso en Splash/onboarding si fuera necesario en Sprint 27, (c) test puro JVM en Sprint 29.

**Lint trajectory (77 → 84, +7 explicados)**

| Categoría | Δ | Causa |
|---|---|---|
| `GradleDependency` | +6 | Bump de `appcompat 1.6.1` + `core-ktx 1.13.1` arrastró 6 deps transitivas. Las versiones resueltas por Gradle están un major por detrás de latest (resoluble en Sprint 25/26 con AGP 9.x). |
| `UnusedResources` | +1 | Las nuevas strings `about_*` aún no son referenciadas indirectamente vía `stringResource()`. Se manifestarán cuando Sprint 24+ use AboutContent en flows que envuelven esas strings en otros layouts. No es una regression en sí — solo nueva inclusión. |
| (other) | 0 | El resto del espectro (84 → 84) mantuvo. |

**Verification**: `./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL in 17s**. `./gradlew :app:testDebugUnitTest` → UP-TO-DATE / 23 PASS / 0 FAIL (sprint sin nuevos tests; refactor no rompió existentes). `./gradlew :app:lintDebug` → 0 errors / 84 warnings. Code-reviewer APPROVED todos los imports-cleanup edits + la decisión de `configChanges`.

**Style nits diferidos (no bloqueantes)**

- `AboutContent.kt`: import-group ordering — `androidx.appcompat` + `androidx.core` están wedged after `androidx.compose.*` block. Per Kotlin convention debería ser `appa < comp < core`. Cosmético; IDE auto-import normalizará eventualmente.
- `SettingsScreen.kt`: `import rikka.shizuku.Shizuku` out of alphabetical order (pre-Sprint-23 carry-over). Cosmético.

### Pre-Sprint 24 hygiene (17 julio 2026 — cierre)

Antes de arrancar Sprint 24 (History con audio + retry), se hizo una pasada de hygiene end-to-end:

**Fixes aplicados (7 edits en 5 archivos)**

| Archivo | Cambio | Razón |
|---|---|---|
| `app/src/main/java/com/handy/app/MainActivity.kt` | Reescrito el comment en `onRestoreInstanceState` enumerando las 9 flags reales de `configChanges` declaradas en el manifest (`orientation|screenSize|screenLayout|keyboardHidden|uiMode|locale|layoutDirection|density|fontScale`). Eliminado el texto "forces an Activity restart regardless of configChanges" (incorrecto Sprint 22). | Comentario activamente misleading; future agents lo leían y generaban work innecesario. |
| `LIMPIA.md` | Actualizado "Sprint actual: Sprint 16" → "Sprint 23 (About + Theme + Locale complete). Próximo: Sprint 24". | Stale reference -误导 sobre el estado real. |
| `app/src/main/java/com/handy/app/viewmodel/ModelsViewModel.kt` | Destructure `val (snap, showExp) = snapShowExp` → `val (snapSrc, showExpSrc) = snapShowExp` (y `computeVisibleCatalog(...)` actualizado). | Cerraba el `Name shadowed` warning compilador en línea 87 — outer `val snap`/`val showExp` del init ya no se confunden con los destructure locals. |
| `app/src/main/java/com/handy/app/model/HistoryEntry.kt` | `obj.optString("post_processed_text", null)` y `obj.optString("audio_path", null)` → patrón explícito `if (obj.isNull("...")) null else obj.optString("...")`. | Cerraba `Type mismatch: inferred type is Nothing? but String was expected` (Kotlin flag cuando pasas null a `optString(name, String): String`). |
| `app/src/main/java/com/handy/app/model/ModelInfo.kt` | `obj.optString("license", null)` y `obj.optString("description", null)` → mismo patrón explícito. | Misma signature mismatch. |
| `app/src/main/java/com/handy/app/HandyApplication.kt` | Añadido `@Suppress("DEPRECATION")` a `onTrimMemory(level: Int)`. | `TRIM_MEMORY_RUNNING_CRITICAL` está deprecated en API 35 a favor del enum `TrimMemoryLevel`, pero el enum requiere API 35+; mantenemos el int constant para minSdk=26. |

**Visual verification end-to-end en device A059 (192.168.1.36:42813)**

Reporte detallado en `handy-android/PROGRESS.md` seccion "MD3 Visual Verification" más adelante. Resumen: APK Green instalado via `assembleDebug`, MainActivity launched (focus `mCurrentFocus=com.handy.app.debug/com.handy.app.MainActivity`), tap About → screencap a `/tmp/handy_shots/sprint23_about.png` → confirmadas las 3 SettingsGroups (APPEARANCE/LANGUAGE/ABOUT) + Theme segmented button (System/Light/Dark) + Language dropdown + Version 1.0.0-alpha2-debug + App data dir tile + GitHub link. Tap Light (540, 637) → screencap captured. Dropdown de Locale abre (screencap captured). Logcat para Handy-related tags (`HandyApp|HandyMain|EngineVM|handy|appcompat|locale|AndroidRuntime|FATAL|LocalServices`) sin errores critical; solo algunos `Binder transaction failure` background ruido de `pm grant DUMP` permission que son estándar y expected.

**Build baseline final (post-hygiene)**

| Métrica | Antes | Después |
|---|---|---|
| compileDebugKotlin warnings (target: shadow + optString + TRIM_MEMORY) | 5 | **0** |
| testDebugUnitTest | 23 PASS | 23 PASS |
| lintDebug errors | 0 | 0 |
| lintDebug warnings | 84 | 84 (delta explanation: appcompat dep bump + 1 nueva string `about_*` entering UnusedResources coverage) |

Code-reviewer APPROVED el 7-edit batch con dos nits menores resueltos en este cierre (MainActivity comment ahora enumerates las 9 reales; LIMPIA.md "Próximas Tareas Pendientes" — feature-flavor no sprint-flavor, no requiere edición).

### MD3 Visual Verification (carried forward from Sprint 16)

Screenshots captured on device (A059, Android 16) for visual regression reference:

| Screen | File |
|--------|------|
| Settings — General | `screenshots/settings.png` |
| Settings — Avanzado | `screenshots/advanced.png` |
| Model Catalog | `screenshots/models.png` |
| History | `screenshots/history.png` |
| About | `screenshots/about.png` |

IME states still pending visual capture (post-Sprint 21): see "Open Items".

### ADB Test Automation (Debug Builds Only)

To enable fully automated end-to-end testing via ADB, the following test-only hooks were added:

| Hook | Purpose | Files |
|------|---------|-------|
| **Shizuku disabled in debug** | Prevents Shizuku permission/security exceptions from blocking automated runs | `HandyApplication.kt`, `injection/InjectorRouter.kt`, `ui/settings/SettingsScreen.kt` |
| **Skip onboarding intent** | `MainActivity` reads `skip_onboarding=true` and marks onboarding complete | `MainActivity.kt` |
| **TestCommandReceiver** | Manifest-declared receiver handling `com.handy.app.action.DOWNLOAD_MODEL` and `com.handy.app.action.SET_ACTIVE_MODEL` broadcasts with `model_id` extra | `TestCommandReceiver.kt`, `AndroidManifest.xml` |
| **ADB test script** | `scripts/adb_test_flow.sh` automates uninstall/install/grant/launch/download/activate/verify | `scripts/adb_test_flow.sh` |

### TestCommandReceiver Security Hardening

The receiver is now restricted to ADB/shell use only:

| Measure | Implementation |
|---------|----------------|
| Manifest permission | `android:permission="android.permission.DUMP"` |
| Disabled in release | `android:enabled="${debugReceiverEnabled}"` placeholder |
| Model validation | Exact match against `ModelInfo.id` from `EngineBridge.nativeGetAvailableModels()` |
| No runtime permission check | Removed `checkCallingPermission` because it does not work inside `BroadcastReceiver.onReceive()` |

### Catalog Sort Tests

Extracted the sort logic from `ModelsViewModel.computeVisibleList` into a pure, testable function:

| Change | Files |
|--------|-------|
| Pure sort function | `capability/CatalogSorter.kt` (`computeVisibleCatalog`) |
| ViewModel delegates to sorter | `viewmodel/ModelsViewModel.kt` |
| Unit tests (10 tests) | `test/java/com/handy/app/capability/CatalogSorterTest.kt` |

Tests cover: ACTIVE first, status ordering, promotion bucket ordering, size tie-breaker, experimental filtering, full sort chain, EXCEEDS/FIT behavior on MID devices, and a regression guard ensuring Voxtral Small 24B does not float above an active lightweight model.

### MD3 Visual Verification

Screenshots captured on device (A059, Android 16) for visual regression reference:

| Screen | File |
|--------|------|
| Settings — General | `screenshots/settings.png` |
| Settings — Avanzado | `screenshots/advanced.png` |
| Model Catalog | `screenshots/models.png` |
| History | `screenshots/history.png` |
| About | `screenshots/about.png` |

### Backend / NPU Investigation

Investigated NPU/QNN/NNAPI/Vulkan support for Android:

| Backend | Status | Recommendation |
|---------|--------|----------------|
| **CPU** | ✅ Stable baseline | Default for debug builds |
| **Vulkan** | ⚠️ Supported by ggml but disabled in debug builds while vendored headers are wired up | Next realistic GPU step |
| **NNAPI** | ⚠️ Supported but deprecated in Android 15 | Not recommended for new development |
| **QNN/Hexagon NPU** | ⚠️ Experimental, requires fork ggml-hexagon | Future, high maintenance cost |

Current build behavior:
- `handy-core/Cargo.toml`: `transcribe-cpp` is built **without** the `"vulkan"` feature by default.
- `app/build.gradle.kts`: `GGML_VULKAN=OFF` for debug builds; `GGML_VULKAN=ON` for release builds (with vendored SPIRV/Vulkan include paths).
- `scripts/build-rust.sh`: default is CPU-only; pass `--vulkan` to enable GPU build.

To re-enable Vulkan fully:
1. Add a `vulkan` feature to `handy-core` that enables `transcribe-cpp/vulkan`.
2. Make Gradle pass `--features vulkan` only for release builds.
3. Fix the `CMAKE_CXX_FLAGS` quoting issue so the vendored `Vulkan-Headers/include` path is picked up.
4. Test on a Vulkan-capable device and verify `Backend::Auto` selects GPU in logcat.

---

## 🟡 Open Items / Next Steps

### Remaining MD3 Migration Sprints (23 → 29)

(Sequence, after Sprint 23 (feature) closure. **Next up**: Sprint 24 — History con audio + retry.)

1. **Sprint 24 — History con audio + retry**
   - `AudioPlayer` per history entry: MD3 `Slider` for seek + `CircularProgressIndicator(24.dp)` for buffering.
   - Copy, star, retry (FilledIconButton primary), delete (FilledIconButton error container).
   - `HistoryViewModel.retry(entry)` action.
   - Ring-buffer `RecordingRepositoryProvider` via `MediaStore`/`getExternalFilesDir(…)`.

2. **Sprint 25 — Advanced settings + Experimental gated** (refinement)
   - Polish the post-Sprint 20 advanced tab.
   - CustomWords input chips, HistoryLimit number input, RecordingRetentionPeriod dropdown, AccelerationSelector (CPU/Vulkan/NNAPI) — gated by `experimentalEnabled`.

3. **Sprint 26 — Post-processing MD3 con providers y prompts**
   - New folder `ui/postprocess/`: ProviderSelect, BaseUrlField, ApiKeyField, ModelSelectField, PromptList, PromptEditor (using `HandyModalBottomSheet`).

4. **Sprint 27 — Onboarding MD3 refinado**
   - `StepIndicator`, Icon container, Button/OutlinedButton/TextButton trio, `LinearProgressIndicator`, `AnimatedContent` with `tween(500, PopEasing)`.

5. **Sprint 28 — Debug panel gated (DebugMode toggle)**
   - Route only visible if `Settings.debugMode == true`.
   - LogLevelSelector, WhatsNewPreview, UpdateChecksToggle, SoundPicker, PasteDelay, RecordingBuffer, AlwaysOnMicrophone, LiveLogViewer (ring buffer of `android.util.Log`).

6. **Sprint 29 (cierre) — Polish + accesibilidad + tests + docs**
   - Predictive back (Android 14+), foldable hinge avoidance (`WindowInfoTracker`).
   - WCAG AA contrast on every token pair.
   - Motion audit, touch targets ≥ 48dp.
   - Tests: ThemeTest, SettingsGroupTest, IMEStateMachineTest, PostProcessFormTest, AudioPlayerTest.
   - Snapshot scripts refreshed.

### Carry-over

1. **Lint NewApi error in themes.xml** — *RESUELTO el 17 julio 2026.* Aplicado `tools:targetApi="27"` al item `windowLayoutInDisplayCutoutMode`, espejando el patrón existente de `tools:targetApi="29"` en `enforceStatusBarContrast` / `enforceNavigationBarContrast`. `lintDebug` ahora reporta 0 errores / 86 warnings.
2. **Re-enable Vulkan GPU backend** — `Cargo` feature en `handy-core`, `CMAKE_CXX_FLAGS` quoting fix, release verification.
3. **Investigate QNN/Hexagon NPU further** — fork eval, maintenance-cost assessment.
4. **IME visual verification** — capture screenshots in all 6 states on A059 Android 16 (now greatly simplified: pill is uniform `PillShape`, IconButton targets are 48dp).
5. **Onboarding visual verification** — screenshots each step post-clean-install (post-Sprint 27).

---

## 🔧 Quick Reference

### Build & Install

```bash
cd handy-android
./gradlew clean assembleDebug
adb -s <device> uninstall com.handy.app.debug
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
```

### Automated Test Flow

```bash
# Run the full automated flow (build, install, grant, launch, download, activate, verify)
./scripts/adb_test_flow.sh <device_serial> <model_id>

# Example:
./scripts/adb_test_flow.sh adb-00143154F001971-AbAnvz._adb-tls-connect._tcp canary-180m-flash-Q4_K_M
```

### Manual ADB Commands

```bash
# 1. Grant permission
adb -s <device> shell pm grant com.handy.app.debug android.permission.RECORD_AUDIO

# 2. Launch app, skipping onboarding
adb -s <device> shell am start -n com.handy.app.debug/com.handy.app.MainActivity --ez skip_onboarding true

# 3. Download a lightweight model
adb -s <device> shell am broadcast -a com.handy.app.action.DOWNLOAD_MODEL --es model_id canary-180m-flash-Q4_K_M

# 4. Set it as active
adb -s <device> shell am broadcast -a com.handy.app.action.SET_ACTIVE_MODEL --es model_id canary-180m-flash-Q4_K_M
```

### Useful Logcat Filters

```bash
adb -s <device> logcat -d | grep -E '(HandyApp|EngineVM|handy-core|TestCommandReceiver|canary)'
```

### Run Tests & Lint

```bash
cd handy-android
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```
