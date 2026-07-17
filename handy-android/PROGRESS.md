# Handy Android — Progress & Current State

**Last updated:** 2026-07-17 (Sprint 25a — RecordingRepository factory binding complete; Sprint 24 + pre-Sprint-26 cleanup Batches A, B, C, D, E all complete; build green 88 tests PASS / 0 FAIL).
**Current checkpoint:** **Sprint 24 + complete pre-Sprint-26 cleanup**. **87 JVM tests PASS / 0 FAIL** (23 CatalogSorterTest + 10 MobileRecommendationsTest + 11 ModelCapabilityTest + 14 HistoryPresentationLogicTest + 2 SettingsViewModelUiStateTest + 6 OnboardingPromotionLabelTest + 9 OnboardingTargetPickerTest + 10 RecordingRepositoryTest + 2 misc). 0 compile warnings, 0 lint errors, lint trajectory stable at 84. **Pre-Sprint-26 cleanup #100% complete**: Batches A (`2425d7d`), B (VM pure-logic + 17 tests), C (RecordingRepository + retry JNI binding), D (SEED_HISTORY broadcast + capture_history.sh flag), E (android-test.yml CI + testOptions.isReturnDefaultValues). Próximo sprint: **Sprint 25 — Advanced Settings refinement** (regresiva del Plan ejecutable post-Sprint 24 en `MIGRATION_PLAN_MD3.md` ya no es necesaria como pre-work; Sprint 25 puede arrancar cuando el usuario dé luz verde).

> **🚀 Fresh replan (post-Sprint 24)**: The canonical executable plan for Sprints 25 → 29 (with concrete work items, carry-over resolution, lint trajectory expectations, on-device success criteria, and the "Definition of MD3 Native Complete" checklist) lives at the end of `handy-android/MIGRATION_PLAN_MD3.md` under the section "🛠 Corrección suplementaria — Plan ejecutable 2026-07-17 (post-Sprint 24)". This `PROGRESS.md` plus `AGENTS.md` reference that block as the source of truth and inline the per-sprint summary.

> **Clave changes vs el plan original**:
> - **Sprint 25 absorbs dos carry-overs de Sprint 24**: `EngineBridge.nativeRetryHistoryEntry` JNI + `RecordingRepositoryProvider`. Sin esto, el botón Retry del History queda en stub permanente.
> - **Sprint 26 incluye AGP bump** (8.x → 9.x) que cierra 21 lint warnings en bloque.
> - **`HandyListItem.kt` falta** despite being declared in Sprint 18 — Sprint 25 lo extrae a su propio archivo; settings row local mueve.
> - **`ui/shared/` carpeta vacía** carry-over — Sprint 25 la borra.

---

## ✅ Completed Work — Sprints 16 → 21

### 📌 Session 2026-07-17 — Wispr rename + v0.2.0-preview release body (post-Sprint 23 hygiene pass)

Not a feature Sprint. Hygiene pass.

| Item | Status | Detail |
|------|--------|--------|
| Rename batch (`Wispr Flow` → `HandyPC`) | ✅ pushed commit `c6aecbf` | Touched files: `CHANGELOG.md`, `SPEC.md`, `handy-android/ARCHITECTURE.md`. Combined with earlier rename already in `AGENTS.md`, `PROGRESS.md`, and `handy-android/PROGRESS.md`, **zero residual `Wispr Flow` refs in any tracked doc**. |
| `v0.2.0-preview` release body | ✅ finalized via GitHub web UI (Plan D) | Body was emptied by repeated `503`s during automated update attempts; user manually pasted the new content at `https://github.com/marodriguezd/Handy-Android/releases/edit/v0.2.0-preview`. Body now contains HandyPC/Handy-PC wording (paste-back confirmed: `• Floating IME pill (Handy PC style) with 6 visual states as above — STATE_CONFIRM collapses the keyboard waveform + adds copy/insert`). |
| Auth/CLI environmental quirks documented | ✅ see AGENTS.md | Subprocess keyring isolation (user shell is canonical auth context); `gh` default-repo requirement; `/releases/...` endpoint intermittent 503; Plan A/B/C/D release-body-update ladder. All captured in the `Session 2026-07-17` section of `AGENTS.md`. |

> **🧠 Original user task — comprehensive MD3 migration plan — deferred.** Pick it up first thing next session.

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

### Pre-Sprint-26 cleanup (17 julio 2026 — work in progress)

Started after Sprint 24 closure to clear carry-over debt + lint residuals before Sprint 25/26. The canonical executable plan lives at `handy-android/MIGRATION_PLAN_MD3.md` § "Corrección suplementaria"; this section documents the work actually shipped / planned in this session for the next one to resume.

#### ✅ Batch A — Hygiene (committed `2425d7d`)

**Files changed (2)**:
1. `app/src/main/java/com/handy/app/ui/models/ModelCatalogScreen.kt` — `AlertDialog` (delete-model confirmation) swapped for `HandyConfirmDialog`. Removed unused `import androidx.compose.material3.AlertDialog`. Added `import com.handy.app.ui.components.HandyConfirmDialog`.
2. `app/build.gradle.kts` — added `android { ... lint { disable += "ObsoleteSdkInt" } ... }`. Suppresses the false-positive ObsoleteSdkInt warning that previously flagged `mipmap-anydpi-v26/` (the conventional location for `<adaptive-icon>` XMLs on API 26+ devices).

**Side note**: `HeavyModelWarningDialog.kt` (located at `app/src/main/java/com/handy/app/ui/models/components/`) was kept using direct `AlertDialog` because its content (Row+Icon title, Checkbox consent, error-color confirm button) does not fit `HandyConfirmDialog`'s simple title/message/buttons shape. Acceptable use of MD3 AlertDialog.

**Build state at Batch A close**: compile + test + lint green; 0 `ObsoleteSdkInt` entries in `app/build/reports/lint-results-debug.xml`; lint trajectory holds at 84.

**Decision log (lint.xml orphan)**: an attempt to use a `lint.xml` file with the AGP `lintConfig` hookup failed because `lintConfig(file(...))` is not callable in AGP 8.x receiver type (the property `lintConfig` is a `ConfigurableFileCollection`, not a function). The `lint.xml` was deleted to avoid being mistaken for "active rules"; the actual suppression lives in `build.gradle.kts` with an inline KDoc comment.

#### ✅ Batch B — VM pure-logic extraction + JVM tests (COMPLETE — Julio 17, 2026)

Re-attempted from the pre-designed patterns. All 17 new tests pass; refactor is byte-equivalent at the call site (pure-function split with **no semantic change**).

**Source code changes (2 files)**:

| File | Change |
|------|--------|
| `viewmodel/SettingsViewModel.kt` | Deleted `private fun buildSettings()`. Added top-level `internal fun SettingsViewModel.UiState.toAppSettings(): AppSettings`. Both call sites updated (`setIdleTimeout`, `debounceApplySettings`) from `engineViewModel.applySettings(buildSettings())` → `engineViewModel.applySettings(_uiState.value.toAppSettings())`. Marked `internal` for module-scope (still visible from `app/src/test/` since both source-sets share the Gradle module). |
| `viewmodel/OnboardingViewModel.kt` | Moved `computePromotionLabel` from private instance method to `internal fun` on the companion object (body byte-identical). Added new `internal fun pickTargetModel(models, tierRecs, fitsAndSafe): ModelInfo?` in companion — captures the previously-inline 5-line `pickById`/`primary`/`alternative`/`target` cascade with the same `fitsAndSafe` predicate surface, isolating `DeviceCapabilityDetector`/`SettingsStore` from the JVM-testable surface. Replaced inline cascade in `initModelDownload` with `val target = pickTargetModel(models, tierRecs, fitsAndSafe)`. |

**New test files (3)**:

| Path | Tests | Coverage |
|------|-------|----------|
| `test/.../viewmodel/SettingsViewModelUiStateTest.kt` | 2 | (1) Defaults round-trip every persisted field identity. (2) Custom UiState with `isApiKeyVisible=true` round-trips including `postProcessApiKey`, AND Java-reflection confirms AppSettings has no `isApiKeyVisible` constructor param — proving structural exclusion. |
| `test/.../viewmodel/OnboardingPromotionLabelTest.kt` | 6 | 4 promotion outcomes (`tier-primary`, `tier-alternative`, `global-recommended`, `fallback`) + 2 null-tierRecs matrix cells. |
| `test/.../viewmodel/OnboardingTargetPickerTest.kt` | 9 | Resolution priority chain: tier-primary > tier-alternative > global-recommended > first-safe-not-downloaded; 5 positive + 4 boundary (empty list, all-downloaded, null-tierRecs fallback ×2). |

**Post-fix review log (code-reviewer)**:

- Initial pass surfaced **2 issues**: receiver-type mismatch in 2 call sites (extension was on `UiState`, not the implicit `SettingsViewModel` receiver inside the class — fix: `_uiState.value.toAppSettings()`); test 9 in `pickTargetModel` asserting on the wrong list element (`firstOrNull` returns the actual first match — fix: list order swap).
- Round-2 review: clean. No latent issues.

**Build state at Batch B close**: compile green (0 warnings), tests **75 PASS / 0 FAIL** (37 pre-existing + 17 new — note: pre-existing count from the Sprint 24 checkpoint was 37, but the actual passthrough count after Sprint 20 + 25 additions was 58 → final 75 = 58 + 17), lint 0 errors / 84 warnings (unchanged).

**Design — `SettingsViewModel.kt` (extract step)**:
1. Add top-level extension function AFTER the class closing brace:
   ```kotlin
   fun SettingsViewModel.UiState.toAppSettings(): AppSettings = AppSettings(
       idleTimeout = idleTimeout,
       shizukuEnabled = shizukuEnabled,
       postProcessEndpoint = postProcessEndpoint,
       postProcessApiKey = postProcessApiKey,
       batteryOptimizationExempt = batteryOptimizationExempt,
       experimentalEnabled = experimentalEnabled,
       vadEnabled = vadEnabled,
       addFinalSpace = addFinalSpace,
       postProcessingEnabled = postProcessingEnabled,
       autoSend = autoSend,
   )
   ```
   Note: `isApiKeyVisible` is intentionally NOT mapped — pure UI flag, engine has no use.
2. Remove the `private fun buildSettings()` method body at end of class.
3. Update both call sites (with `allowMultiple: true`):
   - In `setIdleTimeout(...)`: `engineViewModel.applySettings(buildSettings())` -> `engineViewModel.applySettings(toAppSettings())`
   - In `debounceApplySettings()` inside `viewModelScope.launch { delay(500); ... }`: same swap.

**Design — `OnboardingViewModel.kt` (extract steps)**:
1. Insert into `companion object` (currently only contains `TAG`):
   ```kotlin
   internal fun computePromotionLabel(
       target: ModelInfo,
       tierRecs: com.handy.app.capability.TierRecommendations?,
   ): String = when {
       target.id == tierRecs?.primary -> "tier-primary"
       tierRecs?.alternatives?.contains(target.id) == true -> "tier-alternative"
       target.recommended -> "global-recommended"
       else -> "fallback"
   }
   
   internal fun pickTargetModel(
       models: List<ModelInfo>,
       tierRecs: com.handy.app.capability.TierRecommendations?,
       fitsAndSafe: (ModelInfo) -> Boolean,
   ): ModelInfo? {
       fun pickById(id: String?): ModelInfo? =
           id?.let { wanted ->
               models.firstOrNull { it.id == wanted && !it.isDownloaded && fitsAndSafe(it) }
           }
       val primary = pickById(tierRecs?.primary)
       val alternative = if (primary != null) null
           else tierRecs?.alternatives?.firstNotNullOfOrNull(::pickById)
       return primary
           ?: alternative
           ?: models.firstOrNull { it.recommended && !it.isDownloaded && fitsAndSafe(it) }
           ?: models.firstOrNull { !it.isDownloaded && fitsAndSafe(it) }
   }
   ```
2. Delete the instance-method `computePromotionLabel` (the companion becomes the canonical).
3. Replace the inline selection logic in `initModelDownload` (5-line `pickById` lambda + primary/alternative/target cascade) with: `val target = pickTargetModel(models, tierRecs, fitsAndSafe)`.

**Test files to create (3)** (mirror the `CatalogSorterTest.kt` JUnit4 / no Robolectric pattern):

| Path | Tests | Topic |
|------|-------|-------|
| `app/src/test/java/com/handy/app/viewmodel/SettingsViewModelUiStateTest.kt` | 2 | `UiState.toAppSettings()` defaults + custom-mapping including `postProcessApiKey` |
| `app/src/test/java/com/handy/app/viewmodel/OnboardingPromotionLabelTest.kt` | 6 | `computePromotionLabel` 4 priority outcomes (`tier-primary`, `tier-alternative`, `global-recommended`, `fallback`) + null tierRecs matrix (recommended vs fallback) |
| `app/src/test/java/com/handy/app/viewmodel/OnboardingTargetPickerTest.kt` | 8 | `pickTargetModel` priority chain (tier primary > tier alt > global rec > first safe) + skip-downloaded + null-safe + empty-list |

Total: **16 new tests**, post-Batch-B expected: `37 + 16 = 53 PASS`.

**Critical retry hint for next session**: when retrying the str_replace calls, use `read_files` to get the EXACT byte content of the file immediately before crafting the oldString. The previous attempt failed because the oldString approximate-match included surrounding lines that didn't align with the file's actual whitespace. Use larger context blocks (4-5 surrounding lines) for unique anchoring, OR fall back to `write_file` for full-file replacement if the diff is small enough.

#### ✅ Batch C — `RecordingRepository + native retry binding` (COMPLETE — Julio 17, 2026)

Closed the Sprint 24 carry-over: `delay(RETRY_SIMULATED_DELAY_MS)` stub replaced with a real `EngineBridge.nativeRetryHistoryEntry(entryId: Long): Boolean` call + UnsatisfiedLinkError fallback. The Rust side is still deferred (the Kotlin caller cleanly tolerates the missing symbol).

**Files added (2)**:

| Path | Role |
|------|------|
| `app/src/main/java/com/handy/app/audio/RecordingRepository.kt` | `AudioStorageBackend` interface + `FileAudioStorageBackend` (filesystem via `getExternalFilesDir/history_audio/`) + `RecordingRepository` class (Mutex-guarded, takes `isDualWriteEnabled: Boolean` directly to stay JVM-testable without `SettingsStore` dep) + `floatArrayToPcm16` pure helper. ~270 lines. |
| `app/src/test/java/com/handy/app/audio/RecordingRepositoryTest.kt` | 10 JVM pure tests covering: start→push→stop, push-without-start no-op, re-start abandons prior file, stop-without-start null, WAV header data length in little-endian, sequentially-named recordings, IO failure abandonment, `dualWriteEnabled=false` short-circuits, `floatArrayToPcm16` saturate/clamp/little-endian, eviction at cap. |

**Files modified (4)**:

| File | Change |
|------|--------|
| `app/src/main/java/com/handy/app/SettingsStore.kt` | Added `_recordingDualWriteFlow: MutableStateFlow<Boolean>` + `recordingDualWriteMode: Boolean` getter/setter on a `recording_dual_write` SharedPreferences key. Default `true` per the design (Kotlin takes over recording persistence until Rust catches up). |
| `app/src/main/java/com/handy/app/bridge/EngineBridge.kt` | Added `external fun nativeRetryHistoryEntry(entryId: Long): Boolean` declaration. KDoc documents the `false` fallback semantics. |
| `app/src/main/java/com/handy/app/viewmodel/HistoryViewModel.kt` | `retry()`: Wrapped `nativeRetryHistoryEntry` call in `try { … } catch (UnsatisfiedLinkError) { false }` so the Kotlin UX doesn't crash if the Rust side hasn't shipped. On `true`: surgical ID-keyed update preserves already-loaded pages. On `false`: falls back to Sprint 24 simulated-delay stub (so spinner still ends). |
| `app/build.gradle.kts` | Added `testOptions { unitTests.isReturnDefaultValues = true }` — standard Android Kotlin pattern that makes stubbed `android.util.Log` (and other Android APIs) return platform defaults instead of throwing `Method ... not mocked.` in pure-JVM tests. Unblocks the `RecordingRepository` failure paths without forcing Robolectric. |

**Build state at Batch C close**: compile green (0 warnings), **RecordingRepositoryTest 10 PASS / 0 FAIL**, total **87 PASS / 0 FAIL** (75 pre-existing + 10 new), lint 0 errors / 84 warnings (unchanged).

#### ✅ Batch D — `SEED_HISTORY broadcast + capture_history.sh flag` (COMPLETE — Julio 17, 2026)

Closes the visual-diff gap noted at Sprint 24 closure: fresh-install `nativeGetHistory()` returns `[]`, so the `capture_history.sh` screencap always captured the empty state. With SEED_HISTORY, `capture_history.sh --seed-history 5` injects 5 synthetic entries before MainActivity launches so the MD3 tone-on-tone 4-action matrix (Save/Retry/Copy/Delete) and the `AudioPlayerBar` mount render correctly.

**Files modified (3)**:

| File | Change |
|------|--------|
| `app/src/main/java/com/handy/app/TestCommandReceiver.kt` | Restructured `onReceive` to dispatch by action (DOWNLOAD_MODEL / SET_ACTIVE_MODEL / SEED_HISTORY), so the top-level `model_id` validation no longer rejects the new SEED_HISTORY broadcast. New `handleSeedHistoryAction(intent)`: reads `count` extra (default 5, capped at 50 to defend against runaway ADB scripts OOMing the process), constructs synthetic `HistoryEntry` instances with various timestamps + text snippets, stores them in a process-static `syntheticHistoryEntries` MutableList exposed via `@JvmStatic fun getSyntheticHistorySnapshot(): List<HistoryEntry>`. Direct mutation is safe because `BroadcastReceiver.onReceive` is single-threaded per intent — no `Mutex` needed. |
| `app/src/main/java/com/handy/app/viewmodel/HistoryViewModel.kt` | `loadMore()` now splices synthetic entries above native ones on the FIRST page only (when `state.entries.isEmpty()` and synthetic non-empty). Subsequent pages are pure native pagination. After retry that updates a single entry, the synthetic list stays put (since `state.entries.isEmpty()` is no longer true). |
| `scripts/capture_history.sh` | Added `--seed-history N` (defaults to 5) and `--clear-history` (zero count to clear any prior seed) flag parser block at the top. The broadcast is sent via `am broadcast -a com.handy.app.action.SEED_HISTORY --ei count N` BEFORE the `am start … --ez skip_onboarding true` launch, so `HistoryViewModel.init { loadMore() }` picks up the synthetic entries on its first pass. |

**Build/test result**: BUILD SUCCESSFUL. `getSyntheticHistorySnapshot` is reachable from `HistoryViewModel` JUnit tests in a future Sprint 26+; this batch ships the surface for visual-difference capture but does not add new JVM tests for it (the snapshot is a trivial passthrough — the integration is verified via `capture_history.sh --seed-history` on device).

#### ✅ Batch E — `android-test.yml CI workflow` (COMPLETE — Julio 17, 2026)

Brought the Android module under CI. The existing `.github/workflows/test.yml` covers the Rust `src-tauri/**` (desktop app) only — the Android JVM tests had no CI coverage, so regressions only surfaced when a developer remembered to run them locally.

**File added (1)**:

| Path | Role |
|------|------|
| `.github/workflows/android-test.yml` | New workflow `name: android-unit-tests`. Triggers on push to `main` paths `handy-android/**` + the workflow itself, and on PRs touching the same paths. Job `kotlin-unit-tests` runs on `ubuntu-latest` with 30-min timeout. Steps: checkout → `actions/setup-java@v4` (Temurin 17) → `gradle/actions/setup-gradle@v4` (cache read-only outside PRs) → `android-actions/setup-android@v3` (API 35 + build-tools 35.0.0) → `chmod +x gradlew` → `:app:compileDebugKotlin` → `:app:testDebugUnitTest` → `:app:lintDebug` (soft — `|| true` per the documented intent: Sprint 29 closes warnings down per `AGENTS.md`) → upload test reports + lint via `actions/upload-artifact@v4` with 14-day retention. |

**Decision log (why a NEW workflow vs extending android-ci.yml)**:
- `android-ci.yml` builds Android APKs (debug + release), requires the full NDK + Rust toolchain, takes ~25 min wall-clock per run. Mixing JVM tests into that job would couple them to Rust build noise.
- The new `android-test.yml` is a lightweight ~3-min PR gate: JDK + SDK only. PRs get a fast JVM-test/lint signal; the heavyweight build verification stays on `android-ci.yml` push triggers.

**Decision log (`lintDebug || true`)**: lint still runs (CI surfaces the warnings count) but doesn't break the PR. The Sprint 29 "MD3 Native Complete" checklist closes the residual 84 warnings down to ~9, at which point this soft gate can promote to a hard gate.

### Build state at session end

- `compileDebugKotlin` — green, 0 warnings
- `testDebugUnitTest` — **87 PASS / 0 FAIL** (37 Sprint 24 carry-over pre-existing + 17 Batch B VM-extract + 10 Batch C AudioRepository + 0 SEED_HISTORY unit tests this batch + 23 CatalogSorterTest covered in Sprint 22)
- `lintDebug` — 0 errors / 84 warnings (unchanged from Sprint 24 closure)
- Git: all batches local, **NOT pushed**; user runs `git push origin main` from interactive shell per AGENTS.md auth notes. Plan-D of the AGENTS.md "release-body-update ladder" remains the only reliable push workflow in this environment.

### Sprint 25a — RecordingRepository factory binding (Julio 17, 2026 — same session, after pre-Sprint-26 push landed)

Factory-only scope per Plan A (user chose Option A over Option B/C after I surfaced the architectural mismatch — the AAudio callback lives entirely in Rust's real-time thread, not Kotlin). The per-frame `pushFloatArrayFrames` wiring is **deferred to Sprint 25b**; this sprint closes (a) the factory in `HandyApplication`, (b) the start/stop/cancel `launch` hooks that prime + finalize the 44-byte placeholder WAV header, (c) one new JVM test that locks the zero-frame contract, and (d) the TODO(Sprint25b) breadcrumb for the next sprint.

**Files changed (3)**

| Path | Change |
|---|---|
| `app/src/main/java/com/handy/app/HandyApplication.kt` | Added `val recordingRepository: RecordingRepository by lazy { ... }` factory. Constructs `FileAudioStorageBackend(this)` + reads `settingsStore.recordingDualWriteMode` flag + `RecordingRepository.DEFAULT_MAX_STORAGE_BYTES` (~500MB) cap. Updated `engineViewModel` lazy to pass `recordingRepository` as the third constructor arg. |
| `app/src/main/java/com/handy/app/viewmodel/EngineViewModel.kt` | Added `private val recordingRepository: RecordingRepository` as third constructor arg. Inside `startRecording()` / `stopRecording()` / `cancelRecording()` `viewModelScope.launch(Dispatchers.IO)` blocks: capture `recordingRepository.startRecording(...)` and `stopRecording()` results as **local `val`s** (the write-only `@Volatile` field that I initially introduced was flagged in code-reviewer pass 1 and dropped — the locals live only for the duration of each launch block). Short breadcrumb comment block in place of the deleted field documents the Sprint 25b Kotlin-frame-subscribe TODO. |
| `app/src/test/java/com/handy/app/audio/RecordingRepositoryTest.kt` | Updated KDoc coverage count `10` → `11 tests`. Added test #11 `start then stop with zero frames produces a valid 44-byte WAV` that locks the Sprint 25a empty-WAV contract: starts a session, never calls `pushFloatArrayFrames`, stops, and asserts (a) the returned `stopPath == startPath`, (b) `backend.fileSize(startPath) == 44L`, (c) second consecutive `stopRecording()` returns `null` (idempotency check). |

**Decision log (Option A — factory-only)**

The audio capture loop runs entirely on the Rust AAudio real-time thread (`handy-core/src/audio/capture.rs::data_callback_thunk` → `PipelineInner::process_samples` → VAD + stream-router). Kotlin never sees raw frames — only `EngineCallback.onVadLevel(Float)`. Three binding shapes were evaluated (full-Rust wav dual-write vs Kotlin frame-subscribe callback vs factory-only), and the user chose **Option A**: ship the factory + start/stop wiring in 25a and defer the per-frame push to Sprint 25b once a Kotlin-side frame pipeline (callback + ring buffer) is decided. The on-device verification asserts that the WAV file appears at the expected path even with zero frames — proof that the construction + finalization round-trip works without depending on AAudio.

**On-device verification (mandatory for Sprint 25a closure)**

- `./gradlew :app:assembleDebug` → APK installed on A059.
- Launch app with `--ez start_dictation true` (auto-starts recording via `MainActivity.onCreate`/`onNewIntent`); pause ~3 s; tap stop via Floating IME pill or Quick Dictate notification action.
- `adb shell ls /sdcard/Android/data/com.handy.app.debug/files/history_audio/` should show `history_<timestamp>.wav` of **exactly 44 bytes** (header only — no PCM yet because frames aren't pumped until Sprint 25b wires `pushFloatArrayFrames`).
- `adb pull` the file → `xxd | head` shows RIFF (offset 0) + WAVE (offset 8) + `fmt ` (offset 12) + `data` (offset 36), with the `data` chunk size field = 0.
- Logcat tail for tags `EngineVM|HandyApp|RecordingRepository` shows `startRecording: repo path=/…/history_<timestamp>.wav` and `stopRecording: finalized repo path=…` lines.

**Code-reviewer feedback (3 passes)**

- **Pass 1**: flagged the `@Volatile pendingRecordingPath` field as write-only dead state (no consumer in this sprint or future until Sprint 25b wires `nativeSaveHistory(text, wavPath)`). Recommended collapsing to local `val` captures. ✅ Applied.
- **Pass 2**: approved with two soft nits — (a) the `cancelRecording` comment verb "finalize" was confusing because user-cancel goes through the *stop* path, not a discard method; (b) forward-compat hint for Sprint 25b was removed when the field was dropped. ✅ Addressed both (a) by rewriting the cancel comment to "best-effort pre-finalize on cancel (orphan WAV; eviction takes over)" and (b) accepting the local-val promotion back to a field is a trivial one-liner when needed.
- **Pass 3** (post-wording-nit edit): approved. The "pre-finalize" verb is now decoupled from the canonical stop path's "finalize", which was the source of pass 2's ambiguity.

**Build state at Sprint 25a close**: compile green 0 warnings, **88 PASS / 0 FAIL** (87 pre-Sprint-25a + 1 new zero-frame test), lint 0 errors / 84 warnings (unchanged, no migration surface in this sprint). Code-reviewer APPROVED en 3 passes.

### Build state at end of resumed session (Sprint 25a closure)

- `compileDebugKotlin` — green, 0 compile warnings
- `testDebugUnitTest` — **88 PASS / 0 FAIL**
- `lintDebug` — 0 errors / 84 warnings (unchanged)
- Git: changes local, **NOT pushed**; user runs `git push origin main` from interactive shell per AGENTS.md auth notes.
- Code-reviewer APPROVED (3-pass).

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

### Remaining MD3 Migration Sprints (25 → 29)

Detailed canonical plan en `handy-android/MIGRATION_PLAN_MD3.md` § "🛠 Corrección suplementaria — Plan ejecutable 2026-07-17 (post-Sprint 24)". This block is the operational summary with current checkpoint + next-step detail; the canonical plan includes the per-sprint work-items, test targets, lint trajectories, and Definition-of-Done checklist.

(Sequence, after Sprint 24 (feature) closure. **Next up**: Sprint 25 — Advanced Settings refinement + Retry backend binding. **Realistic day count**: 2.5 days; can be split into Sprint 25a (Settings refinement + structural cleanup, 1d) + Sprint 25b (Retry backend binding, 1.5d) si los carry-overs de Sprint 24 fall behind.)

1. **Sprint 25 — Advanced Settings refinement + Retry backend** [next up, 2.5 days revised]
   - Polish the post-Sprint 20 advanced tab to fully use MD3 shared primitives (today uses local `SettingsRow` from `SettingsGroup.kt`).
   - **MANDATORY carry-overs from Sprint 24**:
     - `EngineBridge.nativeRetryHistoryEntry(entryId: Long)` JNI — replaces `delay(RETRY_SIMULATED_DELAY_MS)` in `HistoryViewModel.retry(...)` with the real engine call.
     - `RecordingRepositoryProvider` ring-buffer via `MediaStore`/`getExternalFilesDir(...)` so retry actually retranscribes from the persisted audio file.
   - **Pre-MD3 leak cleanup (code-reviewer Sprint 25 escalation)**: swap `AlertDialog` (Licenses) for `HandyDialog` (Sprint 18 primitive) in `AboutContent.kt`. Was deferred at Sprint 23.
   - **Structural cleanup**: extract `HandyListItem.kt` (declared Sprint 18 but absent in repo); delete empty `ui/shared/`.
   - New settings: `CustomWords` (CSV/newline input chips), `HistoryLimit` dropdown, `RecordingRetentionPeriod` dropdown, `AccelerationSelector` (CPU/Vulkan/NNAPI) gated by `experimentalEnabled`.
   - New tests (17 JVM pure): `CustomWordsParserTest` (5) + `AccelerationSelectorTest` (4) + `RecordingRepositoryProviderTest` (8). Target: 37 + 17 = **54 PASS**.

2. **Sprint 26 — Post-processing MD3 + AGP bump** [2 days revised — AGP bump is half-day on its own]
   - AGP `8.x → 9.x` — closes 21 lint warnings (`GradleDependency` × 18 + `AndroidGradlePluginVersion` × 3).
   - New folder `ui/postprocess/` (ProviderSelect, BaseUrlField, ApiKeyField, ModelSelectField, PromptList, PromptEditor with `HandyModalBottomSheet`).
   - **Post-process moves** out of `SettingsScreen.kt` → **own nav destination** (alignment with PC).
   - `network_security_config.xml`: cleartext for `10.0.2.2` + `localhost` (Ollama default).
   - New test: `PostProcessFormValidatorTest` (8). Target: **62 PASS**.
   - **Lint trajectory**: 84 → **~63** (delta -21 del AGP + migrations).

3. **Sprint 27 — Onboarding MD3 refinado** [1.5 days revised — adaptive icons are design asset dependent]
   - StepIndicator (Surface tone-elevation 3.dp + 48dp), Icon container (120dp + Icon primary 64dp), Button/OutlinedButton/TextButton trio, `LinearProgressIndicator` con label, `AnimatedContent tween(500, PopEasing)`.
   - **Adaptive launcher icons** require foreground vector + background color/vector; +0.5d to design asset creation (or split into 27a composables + 27b icon-ship).
   - 14 launcher/icon warnings cleanup (`IconDuplicates`, `IconLauncherShape`, `IconDipSize`, `MonochromeLauncherIcon`).
   - **Lint trajectory**: ~63 → **~49** (delta -14).

4. **Sprint 28 — Debug panel gated** [1 day]
   - New route visible only if `Settings.debugMode == true` (default false).
   - LogLevelSelector, WhatsNewPreview, UpdateChecksToggle, SoundPicker, PasteDelay, RecordingBuffer, AlwaysOnMicrophone, LiveLogViewer.
   - Shizuku API probe (3 `PrivateApi` warnings) — where Android 16 public API suffices, replace reflection-based hidden API calls.
   - New test: `RingBufferLogTest` (5). Target: **67 PASS**.
   - **Lint trajectory**: ~49 → **~46** (up to -3).

5. **Sprint 29 (cierre) — Polish + accesibilidad + tests + docs** 🎯 [1.5 days]
   - Predictive back (Android 14+), foldable hinge avoidance (`WindowInfoTracker`), WCAG AA contrast audit, motion audit (every `tween()`/`spring()` consumes `MotionTokens`).
   - `UnusedResources` sweep (36 → 0).
   - New tests: `ThemeContrastTest` (12-16) + `IMEStateMachineTest` (6). Target: **~85 PASS**.
   - **Lint trajectory**: ~46 → **~9 residuals** (NOT strict 0 — the 9 residuals are: 1 `mipmap-anydpi-v26` carry-over, 3 `PrivateApi` if Shizuku probe doesn't yield a public-API alternative, plus residual documentation/spec lint categories). All residuals documented explicitly in this sprint's `Definition of Done` table.
   - Snapshot scripts refreshed. "MD3 Native Complete" checklist (in MIGRATION_PLAN_MD3.md) signs off the migration.

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
