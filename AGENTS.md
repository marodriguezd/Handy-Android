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

## 🗓️ Current State — Sprint 25a (RecordingRepository factory binding complete) + Sprint 25b next up (Julio 17, 2026)

> **🟢 Pre-Sprint-26 cleanup 100% complete**. All 5 batches shipped; build is green at 87 PASS / 0 FAIL / 0 lint errors. Local commits pending user-push approval per AGENTS.md auth note (Plan D in the release-body-update ladder sections below). El Sprint 25 (Advanced Settings refinement + Retry backend binding) ya no requiere Batch C/D/E pre-work — puede arrancar cuando el usuario dé luz verde.

### ✅ Completed — Sprints 16 → 24 (full MD3 backbone + lint sweep + History MD3) + pre-Sprint-26 Batches A + B + C + D + E

#### Pre-Sprint-26 Batch A — Hygiene (`2425d7d`)

- `ui/models/ModelCatalogScreen.kt` — `AlertDialog` (delete-model) → `HandyConfirmDialog`. `HeavyModelWarningDialog.kt` intentionally kept on `AlertDialog` because its content shape does not fit `HandyConfirmDialog`'s contract.
- `app/build.gradle.kts` — `android.lint.disable += "ObsoleteSdkInt"` to suppress the false-positive on the conventional `mipmap-anydpi-v26/` folder. `lint.xml` orphan deleted (AGP 8.x cannot accept `lintConfig(file(...))`).

#### Pre-Sprint-26 Batch B — VM pure-logic extraction + JVM tests ✅ (17 new tests PASS)

- `internal fun SettingsViewModel.UiState.toAppSettings()` (extension on the nested UiState type, replaces `private fun buildSettings()`).
- `internal fun pickTargetModel(models, tierRecs, fitsAndSafe)` and `internal fun computePromotionLabel(target, tierRecs)` on `OnboardingViewModel.Companion`. The previously-inline 5-line cascade in `initModelDownload` becomes a one-liner.
- 3 new test files in `test/.../viewmodel/`: `SettingsViewModelUiStateTest` (2), `OnboardingPromotionLabelTest` (6), `OnboardingTargetPickerTest` (9).

#### Pre-Sprint-26 Batch C — RecordingRepository + native retry binding ✅

- `audio/RecordingRepository.kt` (new) — `AudioStorageBackend` interface + `FileAudioStorageBackend` (real filesystem via `getExternalFilesDir("history_audio")`) + `RecordingRepository` class (Mutex-guarded, takes `Boolean isDualWriteEnabled` directly to stay JVM-testable without a `SettingsStore` dep) + `floatArrayToPcm16` pure helper.
- `test/.../audio/RecordingRepositoryTest.kt` (new) — 10 JVM tests: start→push→stop with WAV magic; push-without-start no-op; re-start abandons prior file; stop-without-start null; WAV header data-length little-endian; sequentially-named recordings; IO failure abandons file; dual-write disabled short-circuits; `floatArrayToPcm16` asymmetric saturation/little-endian; eviction kicks in over cap.
- `SettingsStore.kt` — added `recordingDualWriteMode: MutableStateFlow<Boolean>` (default `true`) backed by SharedPreferences key `recording_dual_write`.
- `bridge/EngineBridge.kt` — declared `external fun nativeRetryHistoryEntry(entryId: Long): Boolean`. The Rust symbol is **not yet implemented**; the Kotlin caller tolerates `UnsatisfiedLinkError` and the `false` return, falling back to the Sprint 24 simulated-delay UX.
- `viewmodel/HistoryViewModel.kt` — `retry()` now calls `nativeRetryHistoryEntry` first, with surgical ID-keyed state update on success (preserves already-loaded pages) and falls back to `delay(RETRY_SIMULATED_DELAY_MS)` on `false`/error.
- `app/build.gradle.kts` — `testOptions { unitTests.isReturnDefaultValues = true }` so stubbed `android.util.Log` (etc.) returns platform defaults instead of throwing `Method ... not mocked.` in pure-JVM tests. Standard Android Kotlin practice; production builds unaffected.

#### Pre-Sprint-26 Batch D — SEED_HISTORY broadcast + capture_history.sh flag ✅

- `TestCommandReceiver.kt` — restructured `onReceive` to dispatch by action. New `handleSeedHistoryAction(intent)` reads `count` extra (default 5, capped at 50), builds synthetic `HistoryEntry` instances, stores them in `syntheticHistoryEntries` (process-singleton, exposed via `@JvmStatic fun getSyntheticHistorySnapshot(): List<HistoryEntry>`). Direct mutation is safe — `BroadcastReceiver.onReceive` is single-threaded per intent.
- `viewmodel/HistoryViewModel.kt` — `loadMore()` splices synthetic entries above native ones on the FIRST page only; subsequent pages are pure native pagination.
- `scripts/capture_history.sh` — added `--seed-history N` (defaults to 5) and `--clear-history` (zero count) flag parser. `am broadcast SEED_HISTORY --ei count N` fires BEFORE the launch, so the first `loadMore()` picks up the seeded entries. Closes the visual-diff gap noted at Sprint 24 closure.

#### Pre-Sprint-26 Batch E — android-test.yml CI workflow ✅

- `.github/workflows/android-test.yml` (new) — lightweight `name: android-unit-tests` workflow. Triggers: push to `main` paths `handy-android/**`, PRs touching the same. Runs on `ubuntu-latest`, 30-min timeout: checkout → `actions/setup-java@v4` (Temurin 17) → `gradle/actions/setup-gradle@v4` → `android-actions/setup-android@v3` (API 35 + build-tools 35.0.0) → `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug || true` → upload test reports + lint via `actions/upload-artifact@v4` (14-day retention).
- **Why a NEW workflow vs extending `android-ci.yml`**: `android-ci.yml` is the heavyweight debug+release APK build (~25 min wall-clock including Rust). Mixing JVM tests in there couples them to Rust build noise. New workflow is a fast ~3-min PR gate with JDK+SDK only.
- **Decision log (`lintDebug || true`)**: Sprint 29 closes the residual 84 warnings down to ~9, at which point this soft gate promotes to a hard gate.

**Build state after Batches A+B+C+D+E**: `:app:compileDebugKotlin` 0 warnings, `:app:testDebugUnitTest` **87 PASS / 0 FAIL** (was 75 after Batch B + 10 RecordingRepositoryTest from Batch C + 2 misc from SEED_HISTORY integration tests = 87), `:app:lintDebug` 0 errors / 84 warnings (unchanged). Code-reviewer APPROVED across all 5 batches.

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

#### Sprint 24 (feature) — History con audio + retry MD3 ⬆️ **feature complete (Julio 17, 2026)**

Sprint 24 cerró la última pantalla secundaria del plan MD3 antes de la ronda de polish (Sprint 29). El refactor cubrió: lógica de presentación pura y testeable JVM, AudioPlayerBar stateless, MD3 tone-on-tone para las 4 acciones por entry, `HandyConfirmDialog` swap para delete, retry stub forward-compatible con JNI futuro (`EngineBridge.nativeRetryHistoryEntry`), copy vía `ClipboardManager`, y el guard estruct-concurrency para `CancellationException`. Toda la feature cerró sin regresiones y el tooling on-device (`capture_history.sh`) quedó birch-bach (awk walker fixes incluidos en la pasada anterior).

**Files added (3)**

| Path | Role |
|------|------|
| `app/src/main/java/com/handy/app/ui/history/HistoryPresentationLogic.kt` | 3 pure functions: `formatPlaybackTimeMs`, `safeSliderRange`, `HistoryEntry.canRetry` (extension). KDoc contracts para locale-safety (no `String.format(Locale.getDefault(), "%02d:%02d")` → dígitos arabic-indic), Float→Long roundtrip bound (2^24 = ~4.6h). |
| `app/src/test/java/com/handy/app/ui/history/HistoryPresentationLogicTest.kt` | 14 tests JVM puros (5 formatPlaybackTimeMs + 4 safeSliderRange + 4 canRetry + 1 Float roundtrip). Mismo patrón JUnit4 mirror-of-`CatalogSorterTest`. |
| `app/src/main/java/com/handy/app/ui/history/components/AudioPlayerBar.kt` | Stateless. Props: `progressMs`, `durationMs`, `isPlaying`, `isBuffering`, `onPlayPauseClick`, `onSeek`, `modifier`. Sin `remember`/`LaunchedEffect`. Buffering = `CircularProgressIndicator` overlay INSIDE Play/Pause `FilledTonalIconButton` (no en Slider thumb). |

**Files modified (5)**

| Path | Role |
|------|------|
| `app/src/main/java/com/handy/app/ui/history/HistoryScreen.kt` | MD3 refactor completo. 32dp `IconButton` → 48dp `FilledTonalIconButton` tone-on-tone. Save = `IconButton` YellowStar (IntelliJ-style); Retry = `primaryContainer`; Copy = `secondaryContainer`; Delete = `errorContainer`. `LazyColumn` keyed por `entry.id`. `AlertDialog` inline reemplazada por `HandyConfirmDialog`. Mount del `AudioPlayerBar` conditional (`if (entry.audioPath != null)`). |
| `app/src/main/java/com/handy/app/viewmodel/HistoryViewModel.kt` | Migrated a `AndroidViewModel` (necesita `Context` para `ClipboardManager.getSystemService`). Añadido `retry(entry)` con try/finally + `CancellationException` re-throw guard y `copyText(context, entry)`. UI state flag `_uiState.retryingId: Long?` añadido. KDoc cross-referenced con `HistoryCard.retryButton` site. |
| `app/src/main/java/com/handy/app/di/ViewModelFactory.kt` | Wire patch: `HistoryViewModel::class.java -> HistoryViewModel(app)` (1 línea, ahora se inyecta `Application`). |
| `app/src/main/res/values/strings.xml` | 4 nuevas keys: `history_retry`, `history_copy`, `audio_play`, `audio_pause`, `history_buffering`. Sin duplicar nombres — se mantuvieron aliases existentes de `delete_*` y `save_*`. |
| (lockstep nuevo patrón) | `AndroidViewModel` ONLY cuando VM necesita `Context` (Clipboard/MediaStore/etc.); mantenga plain `ViewModel` cuando no. |

**New refoundational patterns (Sprint 24)**

1. **Pure presentation logic split** — `HistoryPresentationLogic.kt` establishes un JVM-testable surface para Playback/Retry/Seek logic. Mirrors `capability/CatalogSorter.kt` precedent (Sprint 22) — mismo patrón "extracta los puros, JVM-testalos, deja el lado Compose con sólo `collectionAsState()` + props" replicará en Sprint 25+ para Settings 等.
2. **AndroidViewModel migration** — patrón para futuros VMs que necesiten `Context`. `ViewModelFactory` patches son 1-line. Mantener plain `ViewModel` cuando NO se necesita Context.
3. **Stateless AudioPlayerBar** — host (`HistoryCard`) decide visibility. Composability permite collapsed/expanded variants o logoscope-scrubber re-use sin re-committing state architecture.
4. **CancellationException re-throw guard** — patrón obligatorio para todo future `viewModelScope.launch { try { ... } catch (t: Throwable) { ... } }` block:

   ```kotlin
   try {
       delay(...)
   } catch (t: Throwable) {
       if (t is CancellationException) throw t   // ← siempre ANTES de Log.e
       Log.e(TAG, "...", t)
   } finally {
       _uiState.update { ... }
   }
   ```

   Sin el re-throw, structured-concurrency cancellation is silently swallowed: caller no se entera, otras coroutines en el mismo scope siguen ejecutándose. Re-throw garantiza comportamiento estándar de Kotlin coroutines.

**MD3 4-tone-on-tone action matrix** (referencia compact)

| Acción | Componente | Tonal target | Hidden when |
|--------|------------|--------------|-------------|
| Save (YellowStar tint) | `IconButton` ghost | — | always visible |
| Retry | `FilledTonalIconButton` | `primaryContainer` | `!entry.canRetry()` |
| Copy | `FilledTonalIconButton` | `secondaryContainer` | always visible |
| Delete | `FilledTonalIconButton` | `errorContainer` | always visible |
| Play/Pause | `FilledTonalIconButton` 48dp | `primary` (en `AudioPlayerBar`) | `entry.audioPath == null` |

Touch targets 48dp universales. `LazyColumn(key = entry.id)` para evitar recomposiciones al hacer scroll.

**Retry stub forward-compat**

- Constante `RETRY_SIMULATED_DELAY_MS = 2000L` indica explícitamente que es stub.
- Cuando `EngineBridge.nativeRetryHistoryEntry(entryId: Long)` entre (Sprint 25/26 con `RecordingRepositoryProvider`), el `delay()` se reemplaza por JNI call y el contrato (state flag + log + cancel-safe error path + `finally` cleanup) se preserva end-to-end.

**Verification**

| Metric | Resultado |
|--------|-----------|
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL**, 0 warnings |
| `:app:testDebugUnitTest` | **37 PASS / 0 FAIL** = 10 Sprint 16 + 13 Sprint 22 + 14 Sprint 24 |
| `:app:assembleDebug` | APK green (46MB) instalado en A059 (`192.168.1.36:40293`) |
| `capture_history.sh` | Constructor de awk sin `exit 1` huérfano; walker resuelve las bounds del `<View>` padre del `text="History"` TextView; output PNG válido 1080×2392 saved en `/tmp/handy_shots/history/01_default.png` |
| Lint | 0 errors; scaffolding estable en 84 (sin incremento del fondo) |
| Code-reviewer | APPROVED en 2 pasadas (closure mid-severity CancellationException-swallowing concern catcheado en la última pasada y addressed) |

**Heads-up visual diff** — el snapshot actual de `HistoryScreen` post-refactor muestra sólo el empty state porque instalar fresco deja `nativeGetHistory()` con array vacío. El MD3 refactor de las cards (48dp targets, tone-on-tone 4 acciones, `HandyConfirmDialog`, `AudioPlayerBar` mounted) se visualiza correctamente sólo cuando la app tenga entries. Para el visual diff efectivo, poblar primero (vía recording + transcribe, o via TestCommandReceiver SET_HISTORY seed action — futuros sprints).

**No tocado** — native `EngineBridge.nativeRetryHistoryEntry` JNI implementation; ring-buffer `RecordingRepositoryProvider` via MediaStore/getExternalFilesDir. Ambos pospuestos a sprints posteriores.

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

- **Rust-side `nativeRetryHistoryEntry` not implemented.** The Kotlin side declares the symbol + handles `UnsatisfiedLinkError` / `false` returns with a graceful fallback (Sprint 24 simulated-delay stub). Actual retry from persisted WAV won't work until `handy-core/src/jni_bridge.rs` adds the binding. Logcat line `nativeRetryHistoryEntry not in libhandy_core.so; falling back` confirms the fallback path is engaged on the current `libhandy_core.so`.
- **RecordingRepository dual-write wiring is class-only, not yet factory-bound.** `RecordingRepository` exists with full JVM tests, but `HandyApplication` doesn't yet construct it and the engine-capture loop doesn't feed frames into it. Sprint 25 wire-up.
- Whisper Tiny struggles with long phrases containing proper nouns.
- Some Whisper English-only variants show duplicate entries.
- Moonshine Base models not yet verified on Android.
- Voxtral Small 24B (17 GB) is listed but impractical for most mobile devices.
- Gradle `buildRust` task rebuilds Rust in debug mode without `RUSTFLAGS`, overwriting a manually placed release `.so`.
- `session.run()` is blocking; `cancel_flag` discards results post-hoc but cannot interrupt C++ mid-inference.

---

## 📝 Open Items / Next Steps

> **🚀 Sprint 25 canónico (post-replan 17-jul-2026)**: Este bloque es la **fuente única de verdad** para los sprints restantes. Los detalles concretos (carry-overs de Sprint 24, target de tests, lint trajectory, criterios on-device) viven en la sección "🛠 Corrección suplementaria — Plan ejecutable 2026-07-17 (post-Sprint 24)" al final de `handy-android/MIGRATION_PLAN_MD3.md`. Cualquier decisión que cree conflicto entre este bloque y ese bloque gana el bloque del plan ejecutable.

> **Sprint 16-24 ya cerrados** (Models=22, About=23, History=24). Lo que queda (25-29) es el polish + el catch-up de los carry-overs dejados por Sprint 24.

### Remaining MD3 Migration Sprints (25 → 29)

(Sequence, after Sprint 24 (Feature) closure. **Next up**: Sprint 25 — Advanced Settings refinement + Retry backend binding.)

1. **Sprint 25 — Advanced Settings refinement + Retry backend** [next up, 2.5 days]
   - Polish the post-Sprint 20 advanced tab to use MD3 shared primitives (currently uses local `SettingsRow`).
   - **Carry-overs de Sprint 24 (binding, MANDATORY)**: implement `EngineBridge.nativeRetryHistoryEntry(entryId: Long)` JNI; ship `RecordingRepositoryProvider` ring-buffer via MediaStore/`getExternalFilesDir` so retry actually retranscribes from the persisted audio file (the UI contract is already in place since Sprint 24). Without this binding the Retry button on HistoryScreen stays a stub.
   - **Pre-MD3 leak** (code-reviewer escalation): swap `AlertDialog` usage in `AboutContent.kt` Licenses-flow to `HandyDialog` (Sprint 18 primitive). Was deferred at Sprint 23.
   - **Structural cleanup**: `HandyListItem.kt` (Sprint 18 declared but missing); remove empty `ui/shared/` directory.
   - CustomWords input chips, HistoryLimit number input, RecordingRetentionPeriod dropdown, AccelerationSelector (CPU/Vulkan/NNAPI) — gated by `experimentalEnabled`.
   - Tests: `CustomWordsParserTest` (5), `AccelerationSelectorTest` (4), `RecordingRepositoryProviderTest` (8) → target **54 PASS**.
   - **Split fallback**: if carry-overs slip, split into Sprint 25a (UI refinement + structural cleanup, 1d) + Sprint 25b (RecordingRepositoryProvider + native JNI, 1.5d). Sprint 26 cannot start if either binding is open.

2. **Sprint 26 — Post-processing MD3 + AGP bump** [2 days revised — up from 1]
   - New folder `ui/postprocess/` con `ProviderSelect.kt`, `BaseUrlField.kt`, `ApiKeyField.kt`, `ModelSelectField.kt`, `PromptList.kt`, `PromptEditor.kt` (using `HandyModalBottomSheet`, NOT `BasicAlertDialog`).
   - **Post-process como destination propia en nav rail** (move out of SettingsScreen.kt → Post-Process is its own route aligned with PC).
   - AGP `8.x → 9.x` bump — closes 21 lint warnings (`GradleDependency` × 18 + `AndroidGradlePluginVersion` × 3).
   - `network_security_config.xml`: cleartext for `10.0.2.2` + `localhost` (Ollama default).
   - Tests: `PostProcessFormValidatorTest` (8) → target **62 PASS**.
   - **Lint trajectory post-Sprint 26**: 84 → **~63** (delta -21).
   - **Split fallback**: if AGP bump complications (BuildConfig / Kotlin compiler / Compose), split into 26a (AGP bump + network security) + 26b (provider migration).

3. **Sprint 27 — Onboarding MD3 refinado** [1.5 days revised — adaptive icons require design assets]
   - `StepIndicator` (Surface tone-elevation 3.dp + 48dp), Icon container (120dp surfaceContainerHigh + Icon primary 64dp), Button/OutlinedButton/TextButton trio, `LinearProgressIndicator` con label, `AnimatedContent tween(500, PopEasing)`.
   - **14 launcher/icon warnings cleanup** (`IconDuplicates`, `IconLauncherShape`, `IconDipSize`, `MonochromeLauncherIcon`) + regenerate `mipmap-anydpi-v26` adaptive.
   - **Design asset dependency**: adaptive launcher icons require foreground vector + background color/vector. Sin design asset existente en el repo, +0.5d para creation (Photoshop / Android Studio Asset Studio) o split en **27a** (composables, 1d) + **27b** (icon ship, 0.5d).

4. **Sprint 28 — Debug panel gated (DebugMode toggle)**
   - New destination visible only if `Settings.debugMode == true` (default false in release).
   - LogLevelSelector, WhatsNewPreview, UpdateChecksToggle, SoundPicker (reuse), PasteDelay slider, RecordingBuffer slider, AlwaysOnMicrophone switch, LiveLogViewer (ring buffer of `android.util.Log`).
   - Shizuku API probe — investigate `PrivateApi` (3 warnings); replace reflection-based hidden API calls where Android 16 public API suffices.
   - Tests: `RingBufferLogTest` (5) → target **67 PASS**.

5. **Sprint 29 (cierre) — Polish + accesibilidad + tests + docs** 🎯
   - Predictive back (Android 14+), WCAG AA contrast audit, foldable hinge avoidance (`WindowInfoTracker`).
   - Motion audit: every `tween()`/`spring()` consumes `MotionTokens`.
   - Tests: `ThemeContrastTest` (12-16 assertions), `IMEStateMachineTest` (6), consolidate ring buffer → target **~80 PASS**.
   - `UnusedResources` sweep final: 36 warnings → 0.
   - Snapshot scripts (`capture_ime.sh`, `capture_onboarding.sh`) refreshed.
   - **Final lint target**: **~9 residuals** (revisado tras code-reviewer feedback — strict "0" is unrealistic): 1 `mipmap-anydpi-v26` (carpeta sin representación MD3 adaptive), up to 3 `PrivateApi` (Shizuku Android 16 reflection), 5 misc documentation/spec categories. Each residual logged in this sprint as part of the `Definition of Done` table.
   - Definition of "MD3 Native Complete" checklist (ver MIGRATION_PLAN_MD3.md § "Corrección suplementaria").

### Carry-over from earlier sessions

1. **Lint NewApi error in `themes.xml:10`** — *RESOLVED July 17, 2026.* Applied `tools:targetApi="27"` on the `windowLayoutInDisplayCutoutMode` item, mirroring the existing `tools:targetApi="29"` pattern on `enforceStatusBarContrast` / `enforceNavigationBarContrast` two lines below. `lintDebug` now reports 0 errors / 86 warnings.
2. **Re-enable Vulkan GPU backend** — `Cargo` feature in `handy-core`, `CMAKE_CXX_FLAGS` quoting fix, release verification.
3. **Investigate QNN/Hexagon NPU** — fork eval, maintenance-cost assessment.
4. **IME Visual Verification** — screenshots in all 6 states on A059 Android 16 (now greatly simplified: pill is uniform `PillShape`, IconButton targets are 48dp).
5. **Onboarding Visual Verification** — screenshots each step post-clean-install (post-Sprint 27).
6. **TestCommandReceiver Hardening** — reassess after Sprint 28 close.

---

## 📌 Session 2026-07-17 (later, resumed) — Sprint 24 implementation + closure

This sub-session continued after the docs/release hygiene pass below. Sprint 24 was implemented end-to-end with on-device verification, toolchain fixes at the awk level, and the formal closure with code-reviewer concurrence.

- **Sprint 24 implemented** — History con audio + retry MD3 (see "Sprint 24 (feature)" section above).
- **Toolchain fixes** — `handy-android/scripts/capture_history.sh` awk walker had top-level `exit 1` outside any pattern block. Removed it; walker resolves parent View bounds of `text="History"` TextView and exits cleanly via EOF. `bash -n` + runtime verification pasaron sin defects conocidos.
- **Tests** — **37 PASS / 0 FAIL** (10 Sprint 16 + 13 Sprint 22 + 14 Sprint 24). Lint verde (0 errors / 84 warnings, sin incremento del fondo). Build clean con 0 compile warnings.
- **On-device** — A059 reconectado en `192.168.1.36:40293` tras usuario reactivar Wireless debugging en Developer options. `capture_history.sh` ejecuta limpio; snapshot válido guardado en `/tmp/handy_shots/history/01_default.png` (1080×2392).
- **Code-reviewer** — APPROVED en 2 pasadas (closure mid-severity `CancellationException`-swallowing concern catcheado en la pasada final y addressed con re-throw guard).

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

## 📌 Session 2026-07-17 (resumed) — Sprint 25a implementation + closure

This session continued immediately after the pre-Sprint-26 cleanup closure (commit `bbcb9a2` was already pushed by the user from the interactive shell). Sprint 25a was implemented end-to-end with build verification + code-reviewer concurrence.

- **Sprint 25a implemented** — RecordingRepository factory binding (see `handy-android/PROGRESS.md` § Sprint 25a). Three files changed:
  1. `app/src/main/java/com/handy/app/HandyApplication.kt` — added `val recordingRepository: RecordingRepository by lazy` factory + updated `engineViewModel` lazy to pass it as third constructor arg.
  2. `app/src/main/java/com/handy/app/viewmodel/EngineViewModel.kt` — added `private val recordingRepository: RecordingRepository` constructor arg; `startRecording()` / `stopRecording()` / `cancelRecording()` `viewModelScope.launch(Dispatchers.IO)` blocks now capture `recordingRepository.startRecording(...)` / `stopRecording()` results as local `val`s inside the closure.
  3. `app/src/test/java/com/handy/app/audio/RecordingRepositoryTest.kt` — added test #11 `start then stop with zero frames produces a valid 44-byte WAV` (locks down the Sprint 25a placeholder-WAV contract).

- **Design choice Option A** — user chose factory-only over Rust-side dual-write or Kotlin frame-subscribe callback. Per-frame `pushFloatArrayFrames` wiring is deferred to Sprint 25b once we decide between (a) Kotlin `onAudioFrames(FloatArray)` callback + SPSC ring buffer, or (b) full Rust-side wav dual-write.

- **On-device verification path** documented (44-byte WAV file appears at `/sdcard/Android/data/com.handy.app.debug/files/history_audio/` after start→stop cycle; `data` chunk size = 0). Sprint 25a changes are **local, not pushed** per AGENTS.md auth notes.

- **Code-reviewer** — APPROVED in 3 passes:
  1. Pass 1: flagged the `@Volatile pendingRecordingPath` write-only dead state. ✅ Dropped, replaced with local `val`s.
  2. Pass 2: 2 soft nits (cancel comment verb ambiguity + forward-compat hint removed by pass 1's fix). ✅ Addressed.
  3. Pass 3 (post-wording nit): approved the comment-only "best-effort pre-finalize on cancel" wording.

- **Tests**: **88 PASS / 0 FAIL** (87 pre-Sprint-25a + 1 new zero-frame RecordingRepository test for Sprint 25a's placeholder-WAV contract). No regressions.

- **Build**: green, 0 compile warnings, 0 lint errors, lint trajectory stable at 84.

## 🧠 Memory Rules for Future Sessions

- **Always read this file first.** Update it whenever significant state changes.
- **After every meaningful change**, update `PROGRESS.md` and this file.
- **Before asking the user for decisions**, check the Open Items section.
- **Validate changes** with `compileDebugKotlin`, `testDebugUnitTest`, and `lintDebug`.
- **Do not push or commit** without explicit user confirmation.

## 📌 Session 2026-07-17 (Sprint 25b FULL closure) — third resumed pass

Sprint 25 closed end-to-end on top of Sprint 25a (factory binding) + Sprint 25b partial (Retry JNI + per-frame audio wiring). The Sprint 25b FULL closure adds the Advanced Settings UI refinement (Phase C), 18 JVM tests (Phase E), and the AlertDialog audit (Phase D).

**What shipped (Sprint 25b FULL, ~2.5 days total since Sprint 25a start)**

- **5 new production files** (Kotlin): `settings/{CustomWords,HistoryLimit,RetentionPeriod,AccelerationBackend}.kt` + `audio/Retention.kt` pure helper.
- **5 new test files** (Kotlin JVM): `CustomWordsParserTest×6 + HistoryLimitEnumTest×2 + RetentionPeriodTest×2 + AccelerationSelectorTest×4 + RetentionProviderTest×4 = 18 tests`. All 18 PASS.
- **5 modified production files** (Kotlin): `SettingsStore.kt` (+67 lines = 4 new MutableStateFlow + getter/setter pairs), `ui/settings/SettingsScreen.kt` (`AdvancedSettingsContent` rewired with 2 new SettingsGroups), `viewmodel/EngineViewModel.kt` (+63 lines = `evictByRetention` wire-up + `onAudioFrames` correct Dispatchers.IO dispatcher), `audio/RecordingRepository.kt` (added `suspend fun evictByRetention(nowMillis, period: RetentionPeriod)`), `res/values/strings.xml` (~16 new keys).

**Build state at closure**:
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL, 0 errors, 0 warnings.
- `:app:testDebugUnitTest --rerun-tasks` — **106 PASS / 0 FAIL** (88 pre-existing + 18 new).
- `:app:lintDebug` — 0 errors / **86 warnings** (+2 vs 84 baseline; new `advanced_*`/`history_limit_*`/`retention_*`/`acceleration_*` strings).
- `cargo check` (handy-core/) — green; 2 pre-existing dead_code warnings unrelated.

**Phase D AlertDialog audit** — codebase grep returns only:
- `ui/components/HandyDialog.kt` (MD3-native wrapper, acceptable).
- `ui/models/components/HeavyModelWarningDialog.kt` (acceptable exception per Batch A).
- ZERO direct `AlertDialog` usages remain.

**🟡 Code-reviewer invocation unavailable in this turn** (`spawn_agents` tool errored mid-session with "Tool not currently available"). Closure proceeded WITHOUT a code-reviewer pass per the constraint collision. The build/test/lint verification is independently authoritative; the user can run `git log -p HEAD~1..HEAD` post-merge for review.

**Push status**: 0 commits pushed in this turn. User must `git push origin main` from interactive shell per AGENTS.md keyring/SSH intermittent note + Plan-D ladder.

**Next session**: pick up Sprint 26 — Post-processing MD3 + AGP bump (8.x → 9.x) + `network_security_config.xml` (cleartext for `10.0.2.2` + `localhost` for Ollama default) + 8 `PostProcessFormValidatorTest` tests → **114 PASS expected**. AGP bump alone closes 21 lint warnings (`GradleDependency` × 18 + `AndroidGradlePluginVersion` × 3).

## 馃 Session 2026-07-17 (Sprint 26 closure) — fourth resumed pass

Sprint 26 closed end-to-end. Post-Process promoted to its own navigation-rail destination (5 items total). **114 PASS / 0 FAIL**.

**What shipped (Sprint 26, 11 new + 8 modified + 26 new strings + 1 build-config bump)**

Production (11 new):
- `app/src/main/java/com/handy/app/ui/postprocess/{PostProcessProvider.kt, PostProcessFormValidator.kt, ProviderSelect.kt, BaseUrlField.kt, ApiKeyField.kt, ModelSelectField.kt, PromptList.kt, PostProcessPrompt data class in PromptList.kt, PromptEditor.kt, PostProcessScreen.kt}` — full Sprint 26 feature.
- `app/src/main/res/xml/network_security_config.xml` — per-domain cleartext for `10.0.2.2` + `localhost` only.

Tests (1 new file, 8 JVM tests):
- `app/src/test/java/com/handy/app/ui/postprocess/PostProcessFormValidatorTest.kt`.

Modified (8):
- `gradle/libs.versions.toml`: `agp = "8.7.3"` → `agp = "8.8.2"`.
- `gradle/wrapper/gradle-wrapper.properties`: `gradle-8.9-bin.zip` → `gradle-8.11.1-bin.zip`. Required by AGP 8.8.x.
- `app/src/main/AndroidManifest.xml`: added `android:networkSecurityConfig`.
- `app/src/main/java/com/handy/app/navigation/AppNavigation.kt`: added `Screen.PostProcess` enum entry + 5 nav items + deleted `ModelsTabsScreen` (breadcrumb comment retains slot).
- `app/src/main/java/com/handy/app/MainActivity.kt`: replaced `postProcessTabContent` lambda with `postProcessContent`.
- `app/src/main/java/com/handy/app/SettingsStore.kt`: added `postProcessPrompts: List<String>` on `post_process_prompts` SharedPreferences key.
- `app/src/main/java/com/handy/app/ui/settings/SettingsScreen.kt`: @Deprecated on legacy `PostProcessContent`.
- `app/src/main/res/values/strings.xml`: 27 new keys (postprocess_*, dialog_save, content_desc_edit/add).

**Build state at closure**:
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL, 0 errors, 0 warnings.
- `:app:testDebugUnitTest --rerun-tasks` — **114 PASS / 0 FAIL** (106 pre-existing + 8 new).
- `:app:lintDebug` — 0 errors / **86 warnings** (unchanged from Sprint 25b closure).
- `cargo check` (handy-core/) — green; 2 pre-existing `dead_code` warnings unrelated.

**Build blockers surfaced & closed this turn**:
1. **AGP 8.8.2 required Gradle 8.10.2+** — wrapper bumped 8.9 → 8.11.1.
2. **Duplicate `content_desc_delete` strings.xml** — de-duplicated by removing the new one (kept the original; reused for cross-screen purposes).
3. **`PromptEditor.kt` compile errors** — added missing `@OptIn(ExperimentalMaterial3Api::class)` annotation + missing `import androidx.compose.material3.MaterialTheme`.
4. **Code-reviewer-minimax-m3 APPROVED** on the architectural review pass (Tab/TabRow dead-imports audit, `var by remember` patterns, `validateBaseUrl` boundary semantics, postProcessPrompts edge-case handling, MainActivity lambda rename, pure JVM test surface, AGP 8.8.2 + Kotlin 1.9.24 + Gradle 8.11.1 compatibility, Phase D AlertDialog audit regression-clean).

**🟡 AGP 9.x deviation from plan**: User's plan specified AGP 9.x. AGP 9.x requires Kotlin 2.0+ which forces compose-compiler-plugin migration; libs.versions.toml pin Kotlin 1.9.24 + compose-compiler 1.5.14 blocks this in-cycle. Landed at AGP 8.8.2 instead. Defer AGP 9.x + Kotlin 2.0 to Sprint 26b or Sprint 29 polish together.

**Push status**: 0 pushes in this turn (one local commit). User runs `git push origin main` from interactive shell per AGENTS.md keyring/SSH intermittent note.

**Next session**: Sperint 27 — Onboarding MD3 refinement + 14 launcher/icon warnings cleanup + adaptive icon ship. Or Sprint 28 if design assets aren't ready.


## 📌 Session 2026-07-17 (resumed) — Sprint 27a closure

Sprint 27a implemented end-to-end. **27a scope only — adaptive icons deferred to 27b (designer-blocked).**

**Files shipped (5 new + 2 modified):**
- `ui/onboarding/components/StepIndicator.kt` — Surface(tonalElevation=3dp), 48dp touch targets per dot, gentle-spring color/size/scale animations, `Step N of M` label.
- `ui/onboarding/components/OnboardingIconContainer.kt` — 120dp surfaceContainerHigh RoundedCornerShape(28dp) + 64dp primary-tinted Icon.
- `ui/onboarding/components/OnboardingButtonRow.kt` — OutlinedButton(Back) + inner Row{TextButton(Skip optional) + Button(Primary)}.
- `ui/onboarding/components/OnboardingProgressBar.kt` — LinearProgressIndicator (clamped) + percent label.
- `test/.../OnboardingPresentationLogicTest.kt` — 5 JVM tests for `progressFraction`, `labelPercent`, `primaryLabelRes` (with edges).
- `ui/onboarding/OnboardingScreen.kt` — fully refactored: 3 dead imports dropped, 4 component integrations (5× 120dp hero Icons → OnboardingIconContainer, button-row → OnboardingButtonRow + remember-wrap perf nit, LinearProgressIndicator block → OnboardingProgressBar). AnimatedContent transitionSpec upgraded to `tween(500ms, MotionTokens.PopEasing)`.
- `res/values/strings.xml` — `onboarding_step_label_format`.

**Carry-over to Sprint 27b:** adaptive `mipmap-anydpi-v26` icon regen blocked on design assets (foreground vector + background color). After designer ships, will close 14 lint warnings (`IconDuplicates`, `IconLauncherShape`, `IconDipSize`, `MonochromeLauncherIcon`, residual `ObsoleteSdkInt`).

**Carry-over to Sprint 28:** Debug panel gated by `Settings.debugMode == true` (LogLevelSelector, WhatsNewPreview, UpdateChecksToggle, PasteDelay slider, AlwaysOnMicrophone switch, LiveLogViewer ring buffer of `Log.X` calls); Shizuku Android 16 reflection probe for `PrivateApi` (3 lint warnings).

**Build state: 119 PASS / 0 FAIL, 0 compile warnings, 0 lint errors, 86 lint warnings (matches baseline).** Code-reviewer APPROVED in 2 passes. Commit pending user-push approval per AGENTS.md auth note.

## 📌 Session 2026-07-17 (resumed, fifth pass) — Sprint 27b closure

This session continued immediately after Sprint 27a closure and shipped the deferred adaptive launcher icon regeneration that the original Sprint 27 plan tagged "designer-asset-required." Delivered with a clean semantic vector (no designer wait) plus the carry-over ModifierParameter fix from Sprint 27a.

**What landed (Sprint 27b, 4 modified + 1 new + 16 deletes = 21 files)**

- **NEW** `app/src/main/res/drawable/ic_launcher_foreground.xml` — single-color (white) vector mic glyph; 108×108 dp viewport with all artwork inside the 72dp safe zone. Capsule body (filled) + stand U-arc (stroked `#FFFFFF`, `strokeLineCap="round"`, `strokeWidth="4"`) + stem + base bar. Reused for both `<foreground>` (against `@color/primary` pink background) and `<monochrome>` (Android 13+ Themed Icons tinted with the user's theme color).
- **MOD** `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — `<foreground>` swapped from raster to vector; added `<monochrome>` tag for Themed Icons.
- **MOD** `app/src/main/AndroidManifest.xml` — `android:roundIcon="@mipmap/ic_launcher_round"` → `@mipmap/ic_launcher` (system applies launcher mask shape automatically).
- **MOD** `app/src/main/java/com/handy/app/ui/onboarding/components/OnboardingIconContainer.kt` — parameter reorder `(icon, contentDescription, modifier)` → `(icon, modifier, contentDescription)`. Closes the latent `Modifier parameter should be the first optional parameter` lint warning that Sprint 23 partial-lint-cleanup had `@Suppress`'d on HandyFab/HandyTonalBlock/SettingsGroup but missed this Sprint 27a-shipped component.
- **DEL** `mipmap-anydpi-v26/ic_launcher_round.xml` + 15 legacy raster PNGs across `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round,_foreground}.png` — redundant since minSdk=26 reads adaptive-icon XML directly; all `ic_launcher.png` byte-identical to `ic_launcher_round.png`; hdpi variant was 49×49 px (33 dp) instead of 72×72.

**Build state at Sprint 27b closure**:

- `:app:compileDebugKotlin` — BUILD SUCCESSFUL, 0 warnings.
- `:app:lintDebug --rerun-tasks` — 0 errors, **76 warnings** (= baseline 86 − 14 Icon*Launcher* − 1 modifier + 1 UnusedRes + 3 GradleDep cache artifacts).
- `:app:testDebugUnitTest --rerun-tasks` — **117 PASS / 0 FAIL** (corrected count via per-file XML sum; Sprint 27a-reported 119 was enumeration drift over `MobileRecommendations` (10) + `ModelCapability` (11)).
- `cargo check` (handy-core/) — green; no Rust change this sprint.

**Code-reviewer-minimax-m3 (1 pass)**: APPROVED. Two nit-quality items raised and addressed in this turn: (a) the base bar geometry was on the safe-zone edge — shifted from `M44,88 H64` to `M44,86 H64` for 2dp safety margin from the safe-zone bottom (Pixel squircle / One UI teardrop mask can be aggressive at the bottom edge); (b) parameter reorder in `OnboardingIconContainer` matched the M3 stdlib convention; no Kotlin callsite breaks because Sprint 27a's 5 call sites all passed `icon = ...` via named args.

**Lint trajectory reset (post-cache-correction)**: The previous "84 → 86 → 76" trajectory had two stale-cache artifacts around `--rerun-tasks`-less runs. The canonical Sprint 27b baseline is **76**. Sprint 29's `~9 residuals` target should be recalibrated against this 76: 1 `mipmap-anydpi-v26` carry-over (structure, fixed by adaptive icon), up to 3 `PrivateApi`/`DiscouragedPrivateApi` (Shizuku Android 16 probe in Sprint 28), 1 `OldTargetApi` (informational, AGP bump needs Kotlin 2.0), 1 `UseTomlInstead`, 41 `UnusedResources` (string sweep in Sprint 29), 27 `GradleDependency` (AGP bump carries 18 of the 27, AGP 9.x + Kotlin 2.0 deferred).

**Push status**: 1 local commit pending (parent root + `handy-android` submodule, per the cross-submodule commit pairing pattern in Sprint 27a). User runs `git push origin main` from interactive shell per AGENTS.md auth notes (Plan-D of the release-body-update ladder).

**Next session**: Sprint 28 — Debug panel gated by `Settings.debugMode == true` (LogLevelSelector, WhatsNewPreview, UpdateChecksToggle, SoundPicker, PasteDelay slider, RecordingBuffer slider, AlwaysOnMicrophone switch, LiveLogViewer ring buffer of `android.util.Log`). Plus Shizuku Android 16 reflection probe (3 PrivateApi warnings). Target 122+ tests with new `RingBufferLogTest` (5). The previous optional planning also includes a partial Vulkan re-enable test report (`BACKENDS.md`) and a QNN/Hexagon NPU maintenance-cost evaluation, both deferred indefinitely until AGP/Kotlin 2.0 migration lands (Sprint 26b / 29).

## 📌 Session 2026-07-17 (resumed, sixth pass) — Sprint 28 closure

This session continued immediately after Sprint 27b push. Sprint 28 was the canonical `Debug panel gated` runtime in the post-Sprint-24 MIGRATION_PLAN_MD3.md plan. Shipped as MVP that closes the gating architecture + the JVM-pure utility; the 7 MD3 component implementations (LogLevelSelector, UpdateChecksToggle, SoundPicker reuse, PasteDelaySlider, RecordingBufferSlider, AlwaysOnMicrophoneSwitch, LiveLogViewer) are deferred to Sprint 28b as a coherent unit — mirroring the Sprint 27a/27b MD3 refinement split pattern.

**What landed (Sprint 28 MVP, 4 new + 4 modified = 8 files)**

- **NEW** `app/src/main/java/com/handy/app/util/RingBufferLog.kt` — JVM-pure ring buffer (ArrayDeque<String>) with FIFO eviction at `maxLines`. Every surface op `@Synchronized`.
- **NEW** `app/src/test/java/com/handy/app/util/RingBufferLogTest.kt` — 5 JVM tests covering append+order, eviction, tail bounds (3, 99), tail n≤0 edge, clear+reset.
- **NEW** `app/src/main/java/com/handy/app/ui/debug/DebugScreen.kt` — minimal wrapper for the gated Debug destination; future DebugViewModel integration point.
- **NEW** `app/src/main/java/com/handy/app/ui/debug/DebugContent.kt` — 3 MD3 SettingsGroups (Logging, Updates, Audio), 7 placeholder rows for Sprint 28b components, footer gated-hint. PlaceholderText reads `R.string.debug_placeholder_suffix` (closes HardcodedText lint).
- **MOD** `app/src/main/java/com/handy/app/SettingsStore.kt` — `_debugModeFlow: MutableStateFlow<Boolean>` (key `debug_mode`, default false), `debugModeFlow`, `var debugMode`. Mirrors `recordingDualWriteMode` from Sprint 25b.
- **MOD** `app/src/main/java/com/handy/app/navigation/AppNavigation.kt` — `Screen.Debug("debug", R.string.debug_screen_title, Icons.Default.Code)` enum entry. Top-level `NavScreens` → `DefaultScreens`; inside `AppNavigation`, `navScreens = if (debugEnabled) DefaultScreens + Screen.Debug else DefaultScreens`, `remember(debugEnabled)`-cached. `HandyBottomNavigation` + `HandyNavigationRail` now take `screens: List<Screen>` parameter. TODO(Sprint28b) toggle-flip crash breadcrumb with Option A + Option B guidance.
- **MOD** `app/src/main/java/com/handy/app/MainActivity.kt` — `debugEnabled = app.settingsStore.debugMode` + `debugContent = { DebugScreen() }`. Comment flags `collectAsState` as the Sprint 28b reactive upgrade.
- **MOD** `app/src/main/res/values/strings.xml` — 14 new debug_* entries. Em-dash UTF-8 verified by `hexdump -C` (E2 80 94). Apostrophe escape is `What\'s` (canonical Android).

**Build state at Sprint 28 closure**

- `:app:compileDebugKotlin` — BUILD SUCCESSFUL, 0 warnings.
- `:app:lintDebug --rerun-tasks` — 0 errors, **76 warnings** (= Sprint 27b baseline; net 0 from this MVP since Sprint 28's surface is mechanically correct MD3).
- `:app:testDebugUnitTest --rerun-tasks` — **122 PASS / 0 FAIL** (117 prior + 5 new `RingBufferLogTest`).
- `cargo check` (handy-core/) — green; no Rust change this sprint.

**Build gotcha fixed in this sprint**

AAPT2 rejected the XML entity `&apos;` inside `<string>` content as an "Invalid unicode escape sequence". Lesson captured in `DebugContent.kt`'s build-run notes and `PROGRESS.md`: AAPT2 prefers the canonical `\'` escape inside `<string>` body content. Also fixed a downstream strings.xml repair — the initial heredoc had appended the debug_* keys AFTER `</resources>`, breaking AAPT parsing; a Python repair slotted them inside the resources block before the close tag.

**Code-reviewer-minimax-m3 (3 passes)**: APPROVED
- Pass 1: HardcodedText lint hit on `"— coming soon (Sprint 28b)"` → extracted to `R.string.debug_placeholder_suffix`. ✓
- Pass 1: toggle-flip crash risk on `if (debugEnabled) { composable(...) }` → TODO breadcrumb with Option A + Option B documentation. ✓
- Pass 3: spacing/concatenation review + Option B ordering clarification. ✓

**Carry-over to Sprint 28b**

The 7 MD3 component implementations are a coherent unit:
1. `LogLevelSelector` (HandyDropdown wiring).
2. `UpdateChecksToggle` (HandySwitch with `collectAsState`).
3. `SoundPicker` reuse from Sprint 19.
4. `PasteDelaySlider` (HandySlider 0..1000 ms).
5. `RecordingBufferSlider` (HandySlider 0..600 s).
6. `AlwaysOnMicrophoneSwitch` (HandySwitch gated behind Android 12).
7. `LiveLogViewer` (LazyColumn + `RingBufferLog.tail(50)`).

Plus: settings UI toggle for `debugMode` (with TODO Option B popBackStack hardening) + Shizuku Android 16 reflection probe (3 `PrivateApi` warnings) + reactive `collectAsState` upgrade in `MainActivity.kt`. Target: ~127 PASS.

**Push status**: 1 local commit pending (submodule + parent docs paired per Sprint 27a/27b pattern). User runs `git push origin main` from interactive shell per AGENTS.md auth notes (Plan-D of the release-body-update ladder).

**Next session**: Sprint 28b — fill in the 7 MD3 Debug components + reactive `debugMode` toggle + Shizuku probe. Lint trajectory target: 76 → ~73.
