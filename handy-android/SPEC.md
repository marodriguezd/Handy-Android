# Handy Android - UI Redesign Specification (Sprint 10) — ✅ COMPLETED

**Última actualización:** 2026-07-16
**Checkpoint:** 🟢 Sprint 15 — Curated Mobile Recommended Subset + Capability Tests

---

## Sprint 15 — Nuevas Implementaciones

### ✅ Subset Móvil Definitive (`assets/mobile_recommended.json` + `MobileRecommendations.kt`)

- **19 modelos promovidos** distribuidos en los 5 DeviceTier (4 LOW + 5 MID + 4 HIGH + 3 FLAGSHIP + 3 TABLET).
- Cada tier declara **un primary + alternatives (1–4)** que sobresalen en el catálogo por encima de la lista global.
- **Loader thread-safe** con `@Volatile` cached + doble verificación de bloqueo. El asset (~2 KB) se lee una sola vez por proceso; las llamadas siguientes retornan la instancia cacheada.

**Priority chain** (alta → baja):

```
LOW  primary: whisper-base-gguf
           alts: whisper-tiny, moonshine-streaming-tiny, medasr
MID  primary: nemotron-3.5-asr-streaming-0.6b
           alts: canary-180m-flash, parakeet-tdt-0.6b-v3, whisper-medium, whisper-small
HIGH primary: whisper-large-v3-turbo
           alts: Qwen3-ASR-1.7B, canary-1b-v2, whisper-large-v3
FLGS primary: whisper-large-v3
           alts: granite-speech-4.1-2b-plus, canary-qwen-2.5b
TBLT primary: cohere-transcribe-03-2026
           alts: granite-speech-4.1-2b, granite-4.0-1b-speech
```

### ✅ Bug Latente Corregido — heavyGate/experimental ID matching

El bug P0 detectado: las `heavyGateIds` y `experimentalIds` hardcoded en `ModelCapability.kt` usaban IDs con `-Q5_K_M` / `-Q8_0` suffix, mientras que `ModelInfo.id` viene del catálogo como `"handy-computer/<slug>-gguf"`. **Resultado**: `isHeavyGate(id)` siempre retornaba `false`, dejando los Voxtral sin gating en el onboarding.

**Fix**: renombrar a `heavyGateSlugs` / `experimentalSlugs` con bare slugs + helper privado `slugOf(modelId)` que normaliza (quita prefix `"handy-computer/"` y suffix `"-gguf"`). Constantes `CATALOG_ID_PREFIX` y `CATALOG_ID_SUFFIX` explícitas para evolución.

### ✅ Tests Unitarios JUnit 4 (`app/src/test/...`)

Cubre **21 assertions** en dos archivos:

`ModelCapabilityTest.kt` (11 tests):
- 3 Voxtral `isHeavyGate` (covers Small 24B, Mini 4B Realtime, Mini 3B)
- 7 Moonshine Base `isExperimental` (en + 6 monolingües: ar, ko, uk, ja, vi, zh)
- 11 negative cases (whisper, parakeet, canary, granite, funASR, cohere, qwen, mojhshine-tiny)
- 2 slug-idempotence positive tests (slugs bare matchean correctamente)

`MobileRecommendationsTest.kt` (10 tests):
- `parseJson` happy path (5 tiers + alternatives)
- `parseJson` partial (uno de 5 tiers) → los demas null
- `parseJson` tier sin alternatives (default empty list)
- `parseJson` tier con primary blank (skip)
- `parseJson` malformado (throws JSONException)
- `parseJson` sin `tiers` key (root con sólo `version` → empty file)
- `promotionBucket` retorna 0 (primary) para cada uno de 5 tiers
- `promotionBucket` retorna 1 (alternative) para cada uno de 5 tiers
- `promotionBucket` retorna 2 (not promoted) cross-tier matrix
- Cross-tier lookup proves que el bucket es relativo al tier del dispositivo

**Ejecución verificada**: 21/21 PASS en rig JVM puro con `kotlinc 1.9.24 + JUnit 4.13.2 + org.json 20231013` + Android stubs mínimos. Sin Robolectric.

### ✅ Integración ViewModels

`OnboardingViewModel.kt`:
- Priority chain `tier.primary → tier.alternative → recommended.global → firstOrNull`
- Helper puro `computePromotionLabel(target, tierRecs)` → log `"Selected target: ... promotion=tier-primary"`
- `fitsAndSafe` filter preservado (heavyGate + EXCEEDS check)

`ModelsViewModel.kt`:
- `computeVisibleList` sort chain extendido con `recs.promotionBucket(tier, it.first.id)` entre status y recommended
- Sin cambios al UI badge system existente (deuda pendiente: render visual del badge_tier_*)

### 🟡 Pendiente (no incluido en Sprint 15)

- **Renderizado visual del badge** en `CompatibilityBadge.kt` para tier-primary / tier-alternative (strinngs ya existen pero no se consumen)
- **`androidTest`** end-to-end para `MobileRecommendations.load(context)` (via Robolectric o androidTest real)

## Sprint 14 — Nuevas Implementaciones

### ✅ Sistema de Capacidades del Dispositivo (`com.handy.app.capability`)
- `DeviceTier` + `CapabilitySnapshot`: Clasifica el dispositivo en 5 bandas (LOW ≤1.5GB, MID ≤3.5GB, HIGH ≤6.5GB, FLAGSHIP ≤12.5GB, TABLET >12.5GB) consultando `ActivityManager.MemoryInfo`.
- `ModelCapability`: Divide los 65 modelos del catálogo en cinco perfiles de consumo (ULTRA_LIGHT / LIGHT / MEDIUM / HEAVY / EXTREME).
- `CompatibilityResolver`: Función pura que cruza tier del dispositivo vs tier del modelo. Devuelve `CompatibilityStatus` (ACTIVE / TIER_RECOMMENDED / TIER_RECOMMENDED_DEEP / FIT / EXCEEDS / IMPOSSIBLE), `CompatibilityBadge` (EXPERIMENTAL / HEAVY_GATE / EXCEEDS_RAM / LARGE_HEAP_REQUIRED), y flags `requiresConsent` / `hidden`.

### ✅ Componentes UI Conscientes (`ui/models/components/`)
- `DeviceCapabilityHeader`: Card top del catálogo que muestra `totalMemGB`, tier, botón Refresh, y `Switch` para `showExperimentalModels` (visible solo en MID+).
- `CompatibilityBadgeChip` + `ActiveBadge`: Chips visuales que notifican al usuario sobre incompatibilidades (EXPERIMENTAL, HEAVY_GATE, EXCEEDS_RAM, LARGE_HEAP_REQUIRED).
- `HeavyModelWarningDialog`: `AlertDialog` que confronta peso del modelo vs RAM del sistema. **Checkbox required** para habilitar Confirm. Diferencia title/body para HEAVY vs EXTREME.

### ✅ Gating de Descargas
- `ModelsViewModel.attemptDownload(model)` evalúa `computeCompatibility(...)` antes de invocar la descarga. Si `requiresConsent=true`, dispara `HeavyModelWarningDialog`; el usuario debe aceptar explícitamente.
- `downloadModel(modelId)` non-gating preservado para flows imperativos (e.g., onboarding auto-download).

### ✅ Onboarding Tier-Aware
- `OnboardingViewModel.fitsAndSafe` filter: chain de selección `recommended+safe` → `any+safe` → dead-end fallback (`Log.w` + `isDownloadReady=true` para que el usuario pueda avanzar).
- Garantiza que modelos pesados (Voxtral 24B/4B/3B) NUNCA se descargan silenciosamente durante el wizard — solo via flujo manual con dialog en la pantalla de catálogo.

### ✅ Observabilidad via Logcat
- `EngineViewModel.init`: `Log.d` tras `nativeInit(...)` con tier + RAM + flag experimental.
- `OnboardingViewModel`: TAG="OnboardingVM" + 11 logs (nextStep / previousStep / skipToModelDownload / skipDownload / retryDownload / completeOnboarding / initModelDownload / Selected target / All models downloaded / models loaded / Download complete-failed). De-dup vía `lastLoggedFailureId` sentinel separado de `downloadTargetId`.

## Sprint 13 — Nuevas Implementaciones

### ✅ Persistencia de modelo activo (`model/manager.rs`)

El modelo activo se persiste en `model_dir/.active_model` entre reinicios de la app.

- **Carga automática:** `ModelManager::new()` lee el archivo y restaura `active_model_id` si el `.gguf` existe
- **Guardado:** `set_active_model()` escribe el ID en el archivo
- **Limpieza:** `delete_model()` borra el archivo si el modelo eliminado era el activo
- **Defensa:** Si el `.gguf` fue borrado externamente, se limpia el archivo huérfano

### ✅ IME — onComputeInsets restaurado (`HandyInputMethodService.kt`)

- `contentHeightPx` medida dinámicamente vía `onGloballyPositioned`
- `onComputeInsets` fija `contentTopInsets`/`visibleTopInsets` a la altura del pill
- `TOUCHABLE_INSETS_CONTENT`: toques fuera del pill pasan a la app host
- Elimina layout shifts inesperados en apps host

### ✅ Cancelación de batch transcription (`transcription/engine.rs` + `periodic.rs`)

- `cancel_flag: Arc<AtomicBool>` compartido entre `TranscriptionEngine` y `PeriodicWorker`
- `run()` verifica el flag antes de `session.run()` y descarta el resultado si se activó durante la inferencia
- `PeriodicWorker` verifica el flag antes y después de cada `session.run()` parcial (~3s)
- El flag se resetea en `start_stream()` y `start_periodic()` al iniciar nueva grabación
- `cancel()`, `cancel_stream()`, y `cancel_periodic()` activan el flag

---

## Overview
The goal of this specification was to align the Android Jetpack Compose UI with the premium aesthetic of the Handy Desktop application. This involved adopting a new dark/cream color palette with pastel pink accents, and simplifying the main navigation into a 4-item bottom navigation bar, while utilizing top tabs to group sub-sections.

**Status:** ✅ All objectives implemented, built, installed, and verified on device (A059).

## 1. Aesthetic and Theming

### Colors (implemented in `Color.kt` + `Theme.kt`)
- **Background (`background`):** `#252422` — warm dark cream.
- **Surface (`surface`):** `#2C2B29` — slightly lighter than background.
- **Primary Accent (`primary`):** `#F48FB1` — pastel pink.
- **Text/Icons (`onBackground`, `onSurface`):** `#F0EDE9` — warm white.
- **Theme:** Forced dark (`darkColorScheme`), no dynamic color.

### Shape & Typography (preserved from pre-Sprint 10)
- **Shapes:** Material 3 rounded corners (small=8dp, medium=12dp, large=16dp).
- **Typography:** HandyTypography with system sans-serif, Material 3 typescales.

## 2. Navigation Architecture

### Main Navigation (BottomNavigationBar) ✅
A `Scaffold` with `NavigationBar` at the bottom. The navigation items are:
1. **General** (⚙️ icon)
2. **Modelos** (🔧 icon)
3. **Historial** (📅 icon)
4. **Acerca de** (ℹ️ icon)

The bottom bar is hidden during the onboarding flow.

### Sub-Navigation (Tabs) ✅
- **General Section:** `TabRow` with "General" (audio, injection, battery settings) and "Avanzado" (advanced config matching desktop: APLICACIÓN → SALIDA → TRANSCRIPCIÓN → EXPERIMENTAL).
- **Modelos Section:** `TabRow` with "Modelos" (model catalog) and "Post Proceso" (LLM endpoint + API key).
- **Historial Section:** No tabs — displays past transcriptions directly.
- **Acerca de Section:** No tabs — displays version, licenses, and GitHub link.

## 3. Implementation Summary

### Files Modified (Sprint 10)
| File | Change |
|---|---|
| `Color.kt` | New dark/cream + pastel pink palette |
| `Theme.kt` | Forced dark theme, no dynamic color |
| `Screen.kt` | 4 routes (General, Models, History, About) + Onboarding |
| `AppNavigation.kt` | Bottom nav 4 items + TabRow for General and Models |
| `SettingsScreen.kt` | Added AdvancedSettingsContent (4 desktop sections), GitHub link in AboutContent |
| `MainActivity.kt` | Shared SettingsViewModel across 3 tabs |
| `SettingsStore.kt` | 5 new fields: experimentalEnabled, vadEnabled, addFinalSpace, postProcessingEnabled, autoSend |
| `AppSettings.kt` | Same 5 fields in data class |
| `SettingsViewModel.kt` | UI state + setters + buildSettings for all 5 new fields |

### Files Deleted
| File | Reason |
|---|---|
| `DictationScreen.kt` | Dead code — no route, no references. IME is the primary dictation interface |

### Build Status
- `./gradlew assembleDebug` → **BUILD SUCCESSFUL**
- **Zero warnings** (menuAnchor() deprecation fixed)
- APK installed and launched on device (A059)
