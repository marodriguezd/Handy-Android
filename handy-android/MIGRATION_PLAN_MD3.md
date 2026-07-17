# 🪄 Plan de Migración a Material Design 3 Nativo — Handy Android Fork

> **Objetivo**: convertir Handy Android en una app 100 % Material Design 3 (expressive-ready), usando **exactamente** la misma paleta de colores de Handy PC, replicando y elevando a primitivas nativas de Compose Material3 todas las funcionalidades del PC.

**Fecha**: 17 de julio de 2026 (post-replan; supersedes 16-jul-2026 original) · **Autor**: sesión orquestada desde `AGENTS.md` · **Estado actual**: cierre de Sprint 24 (MD3 backbone completo backbone + lint sweep). **Sprint 25-29 replan entregado** — ver sección "🛠 Corrección suplementaria — Plan ejecutable 2026-07-17" al final del archivo.

---

## 📋 Resumen ejecutivo

El fork ya tiene MD3 como columna vertebral (theme centralizado, `ListItem`, `SuggestionChip`, `ElevatedCard`, navegación adaptativa `NavigationBar`/`NavigationRail`, IME con `Surface`/`shape.extraLarge`, Typography y Shape completas). Pero faltan tres cosas que lo separan de un **MD3 nativo pulido**:

1. **Sistema de tokens incompleto**: `themes.xml` todavía referencia `Theme.Material.Light.NoActionBar` (MD2); `darkColorScheme()` no expone la jerarquía tonal de superficies M3 (`surfaceContainer{Lowest,Low,…,Highest}`); hay colores hardcodeados fuera de tokens (`Color.Red`, `Color(0xFF4CAF50)`, `Color(0xFFFF9800)` en `SettingsScreen.kt`).
2. **Cobertura de funcionalidades**: faltan pantallas enteras del PC — paste-method, audio feedback, sound picker, theme selector, language picker, providers de post-processing, prompts custom, history con audio + retranscribe, debug mode, models con búsqueda/filtros por idioma, etc.
3. **Polishing MD3 nativo**: edge-to-edge, predictive back, motion springs, FAB extendido, search bar, filter chips, segmented button, dialog/sheet/navigation limpia.

La paleta Handy **se mantiene idéntica** (`background #2c2b29`, `text #fbfbfb`, `logo primary #f28cbb`, `ui pink #da5893`, `mid gray #808080`, separadores `#5a5753`). Solo la **jerarquía tonal** (surfaceContainer* levels) y los **esquemas** (light / dark / dynamic) se generan a partir de ese mismo seed.

---

## 🧭 Auditoría rápida del estado actual

### ✅ Lo que ya es MD3
- `Theme.kt` con `MaterialTheme(colorScheme = …, typography = …, shapes = …)`.
- `Color.kt` con todos los roles semánticos M3 (`primary`, `onPrimary`, `primaryContainer`, …, `outlineVariant`, `scrim`).
- `Type.kt` con escala completa Display/Headline/Title/Body/Label.
- `Shape.kt` con las 5 escalas M3 (`extraSmall=4dp` … `extraLarge=28dp`).
- Pantallas que ya usan M3: `SettingsScreen.kt` (`ListItem` + `AlertDialog`), `ModelCatalogScreen.kt` (`ElevatedCard` + `SuggestionChip` + `LinearProgressIndicator`), `HistoryScreen.kt` (`ElevatedCard` + `AlertDialog` + `ListItem`), `OnboardingScreen.kt` (`Button`/`OutlinedButton`/`TextButton` + `LinearProgressIndicator`), `AppNavigation.kt` (`Scaffold` + `TopAppBar` + `NavigationBar` + `NavigationRail` + `TabRow`), IME (`Surface` + `FilledIconButton` + `IconButton` + `AnimatedContent` + springs).
- Adaptativo `screenWidthDp < 600` → `NavigationBar`; `≥ 600` → `NavigationRail`.
- IME con `onComputeInsets` ajustado (`TOUCHABLE_INSETS_REGION`) y `Box.onGloballyPositioned` para `contentHeightPx`.
- Capability tier (`DeviceCapabilityHeader` + `CompatibilityBadgeChip`).
- Tests de sort (`CatalogSorterTest` 10 casos).
- i18n de strings en `res/values/strings.xml` + variante `values-es/strings.xml`.

### ❌ Lo que NO es MD3 nativo todavía

| # | Issue severidad | Archivo:Línea | Qué falta |
|---|-----------------|---------------|-----------|
| 1 | **Crítico** | `app/src/main/res/values/themes.xml` | `parent="android:Theme.Material.Light.NoActionBar"` es MD2, debe ser `Theme.Material3.*`. |
| 2 | **Crítico** | `app/src/main/java/.../ui/theme/Theme.kt` | Falta jerarquía tonal completa (`surfaceContainer*`, `surfaceDim`, `surfaceBright`). |
| 3 | **Crítico** | `Theme.kt` | No existe res de `HandyLightColorScheme` completa (light usa esquema distinto sin all roles). |
| 4 | **Crítico** | `color.xml`, `themes.xml` | `colors.xml` todavía contiene hex MD2/purple stock; debería apuntar a Handy o eliminarse. |
| 5 | **Alto** | `SettingsScreen.kt:194-200` | Hardcode `Color.Red`, `Color(0xFF4CAF50)`, `Color(0xFFFF9800)` para status Shizuku. → tokens `tertiary`, `secondary`, `error`. |
| 6 | **Alto** | `SettingsScreen.kt` | Tabs sin iconos (PC: cada sección tiene brand icon). |
| 7 | **Alto** | `AppNavigation.kt` | Falta soporte foldable (hinge avoidance) y compact/medium/expanded size class. |
| 8 | **Alto** | IME (`HandyInputMethodService.kt`) | El pill usa hardcoded `Surface(color = surfaceVariant.copy(alpha = 0.7f))` — debería usar `Surface(tonalElevation = 3.dp)` para legibilidad M3. |
| 9 | **Alto** | views generalmente | No hay edge-to-edge (`enableEdgeToEdge()`), `WindowInsets` no se aplican consistentemente. |
| 10 | **Alto** | Pantallas generales | Falta `SearchBar`, `FilterChip`, `SegmentedButton`, `FloatingActionButton`, `Snackbar`, `Badge`. |
| 11 | **Medio** | `Color.kt` | No existe plan de `dynamicColor` toggle. |
| 12 | **Medio** | General settings | Faltan: `MicrophoneSelector`, `OutputDevice*`, `AudioFeedback`, `VolumeSlider`, `SoundPicker`, `ModelSettingsCard`. |
| 13 | **Medio** | Advanced settings | Faltan: `PasteMethod` (IME/Shizuku/Clipboard), `ClipboardHandling`, `AutoSubmitKey`, `AppendTrailingSpace` rename, `CustomWords`, `HistoryLimit`, `RecordingRetentionPeriod`, `AccelerationSelector`. |
| 14 | **Medio** | Models | Falta búsqueda, filtro por idioma, rescan, secciones "Your models"/"Available models". |
| 15 | **Medio** | History | Falta `AudioPlayer`, retranscribe, copy-to-clipboard, abrir carpeta. |
| 16 | **Medio** | About | Faltan: `ThemeSelector`, `AppLanguageSelector`, `Donate`/`Source` distinction, `AppDataDirectory`, `LogDirectory`. |
| 17 | **Medio** | Post-processing | Faltan: provider select, model select con fetch, lista de prompts custom, prompt editor. |
| 18 | **Medio** | Strings | Mezcla español (settings_section_aplicacion, settings_post_processing, etc.) e inglés (settings_postproc, settings_llm_endpoint). Hay que unificar idioma. |
| 19 | **Bajo** | varios | No hay `MotionTokens.kt` centralizado; cada `tween()`/`CubicBezier` se calcula ad hoc. |
| 20 | **Bajo** | varios | No hay `Spacings` (4dp grid M3); paddings aleatorios. |

---

## 🧱 Gaps Android ↔ PC (funcionalidad)

### General settings (PC: `GeneralSettings.tsx`) → Android no existe equivalente

| Función PC | Estado Android | Acción |
|---|---|---|
| Shortcut transcripción / cancel | N/A (Android: IME + ForegroundService) | Documentar (IME buttons) |
| Push-to-talk | N/A | Documentar |
| **Shortcuts / Global hotkeys** | ❌ Sin equivalente directo | Decidir: ¿volume key trigger? ¿Accessibility Service? (ver Sprint 17.1) |
| `MicrophoneSelector` | ❌ Ausente | `ExposedDropdownMenuBox` + `AudioManager.getDevices(GET_DEVICES_INPUTS)` |
| `OutputDeviceSelector` (audio HW playback) | ❌ | Si exponemos audio feedback → `ExposedDropdownMenuBox` con `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` |
| `MuteWhileRecording` | ❌ | Switch MD3 |
| `AudioFeedback` (toggle) | ❌ UI | Switch MD3 + `SoundPicker` |
| `VolumeSlider` | ❌ UI | MD3 `Slider` 0-100% |
| `ModelSettingsCard` | ❌ | Card con `FilledTonalButton` unload |

### Advanced settings (PC: `AdvancedSettings.tsx`) → ❌ casi vacío

| Función PC | Estado Android | Acción |
|---|---|---|
| `StartHidden`, `AutostartToggle`, `ShowTrayIcon` | N/A | Stub "Próximamente en Android 14+" |
| `ShowOverlay` (top/bottom) | N/A | Stub — IME position ya se respeta parcialmente |
| `ModelUnloadTimeoutSetting` | ❌ | MD3 `Slider` discreto 1m..∞ |
| `ExperimentalToggle` | ✅ `experimentalEnabled` | Refactor nombre + label |
| `PasteMethodSetting` (direct/clipboard) | ❌ (Shizuku + IME) | MD3 `ExposedDropdownMenuBox` (IME / Shizuku / Clipboard) |
| `TypingToolSetting` | N/A (Linux only) | No aplica |
| `ClipboardHandlingSetting` | ❌ | MD3 `ExposedDropdownMenuBox` |
| `AutoSubmit` + `AutoSubmitKey` | ⚠️ `autoSend` (ime/disabled) | Refactor → `ExposedDropdownMenuBox` + secundario keypicker |
| `VoiceActivityDetection` | ✅ `vadEnabled` | Mantener, traducir label |
| `CustomWords` | ❌ | TextField + chips remover |
| `AppendTrailingSpace` | ✅ `addFinalSpace` | Refactor nombre |
| `HistoryLimit` | ❌ | Number input |
| `RecordingRetentionPeriod` | ❌ | `ExposedDropdownMenuBox` |
| `PostProcessingToggle` | ✅ `postProcessingEnabled` | Refactor → tab dedicado |
| `KeyboardImplementationSelector` | N/A | Stub |
| `AccelerationSelector` (CPU/Vulkan/NNAPI) | ❌ | `ExposedDropdownMenuBox` con capabilities |
| `LazyStreamClose` | N/A | Stub |

### Models (PC: `ModelsSettings.tsx`) → ⚠️ parcial

| Función PC | Estado Android | Acción |
|---|---|---|
| Búsqueda por nombre/descripción | ❌ | MD3 `SearchBar` (ExperimentalMaterial3Api) |
| Filtro por idioma | ❌ (chips estáticos en card) | MD3 `FilterChip` group |
| Rescan local models | ⚠️ Existe `loadModels()` sin UI dedicada | IconButton en TopAppBar |
| "Your models" / "Available models" sections | ❌ (lista plana) | Section headers con `titleSmall` emphasized |
| Compatible/Exceeds badges | ✅ `CompatibilityBadgeChip` | Mantener |
| Sort logic | ✅ `CatalogSorter.computeVisibleCatalog` | Mantener |

### History (PC: `HistorySettings.tsx`) → ❌ sin audio

| Función PC | Estado Android | Acción |
|---|---|---|
| `AudioPlayer` por entrada | ❌ | Custom MD3 + `MediaPlayer` |
| Copy text | ⚠️ Solo en IME Confirm | IconButton en card |
| Re-transcribe | ❌ | IconButton + spinner inline |
| Open recordings folder | N/A (scoped storage) | `ACTION_OPEN_DOCUMENT_TREE` |
| Saved star toggle | ✅ | Mantener |

### About (PC: `AboutSettings.tsx`) → ⚠️ básico

| Función PC | Estado Android | Acción |
|---|---|---|
| `AppLanguageSelector` | ❌ | `ExposedDropdownMenuBox` + `AppCompatDelegate.setApplicationLocales(...)` |
| `ThemeSelector` (system/light/dark) | ❌ | MD3 `SegmentedButton` 3-way + persistencia |
| Version | ✅ | Mantener |
| `ShowWhatsNewOnUpdate` | ❌ | Switch MD3 |
| Donate (handy.computer/donate) | ❌ | `FilledButton` + `openUrl` |
| Source (GitHub) | ✅ | Mantener, refactor con `ListItem` |
| `AppDataDirectory` | ❌ | `ListItem` con copiar path |
| `LogDirectory` (logs/) | ❌ | Botón "Ver logcat" con filtro app |
| `Acknowledgments` (ggml) | ✅ Parcial (Licenses dialog) | Mover a card dedicada |

### Post-processing (PC: `PostProcessingSettingsApi` + `Prompts`) → ❌ muy básico

| Función PC | Estado Android | Acción |
|---|---|---|
| ProviderSelect (OpenAI/Anthropic/Ollama) | ❌ (solo endpoint) | `ExposedDropdownMenuBox` |
| BaseUrlField | ✅ `postProcessEndpoint` | Mantener |
| ApiKeyField | ✅ `postProcessApiKey` | Mantener, refactor con visibility toggle |
| ModelSelect + fetch | ❌ | `OutlinedTextField` + IconButton refresh |
| Custom prompts CRUD | ❌ | `LazyColumn` + BottomSheet editor |

### Debug (PC: `DebugSettings.tsx`) → ❌ no existe

| Función PC | Estado Android | Acción |
|---|---|---|
| `LogLevelSelector` | ❌ | `ExposedDropdownMenuBox` |
| `WhatsNewPreview` | ❌ | Botón → `WhatsNewModal` existente |
| `UpdateChecksToggle` | ❌ | Switch MD3 |
| `SoundPicker` | ❌ | `ExposedDropdownMenuBox` |
| `WordCorrectionThreshold` | N/A | No aplica |
| `PasteDelay` | ❌ | MD3 `Slider` |
| `RecordingBuffer` | ❌ | MD3 `Slider` |
| `AlwaysOnMicrophone` | ❌ | Switch MD3 + descripción permisos |
| `ClamshellMicrophoneSelector` | N/A | No aplica |
| `LiveLogViewer` | ❌ | Custom (tail de buffer circular Log) |

---

## 🎨 Tokens MD3 alineados con la paleta PC

### Source-of-truth

```
Background        #2c2b29  (tonal 10)
Text              #fbfbfb  (tonal 95)
Logo primary      #f28cbb
UI pink           #da5893
Mid gray          #808080
Outline divide    #5a5753
```

### Color.kt — propuesta final (extracto)

```kotlin
// Ya existentes en Color.kt — mantener
val HandyBackground      = Color(0xFF2C2B29)
val HandyOnBackground    = Color(0xFFFDFBFB)
val HandyPrimary         = Color(0xFFF28CBB)  // ≈ logo
val HandyOnPrimary       = Color(0xFF1C1B1F)
val HandyPrimaryContainer= Color(0xFF5A3A4B)
val HandyOnPrimaryContainer = Color(0xFFFFD9E4)
val HandyError           = Color(0xFFF2B8B5)
val HandySuccess         = Color(0xFF81C995)
val HandyAccent          = Color(0xFFF28CBB)
val YellowStar           = Color(0xFFFFD700)

// Nuevos niveles tonales (jerarquía M3 dark)
// surfaceContainerLowest → más bajo (casi negro)
// surfaceContainer       → default
// surfaceContainerHighest → chips/cards altos
val HandySurfaceDim               = Color(0xFF1F1E1C)
val HandySurfaceBright            = Color(0xFF3A3835)
val HandySurfaceContainerLowest   = Color(0xFF1A1917)
val HandySurfaceContainerLow      = Color(0xFF25241F)
val HandySurfaceContainer         = Color(0xFF2C2B29) // ≈ background
val HandySurfaceContainerHigh     = Color(0xFF34322F)
val HandySurfaceContainerHighest  = Color(0xFF3F3D3A)

// Light scheme (FEGEN desde la paleta PC)
// background #fbfbfb, primary #faa2ca, ui pink #da5893
val HandyLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val HandyLightSurfaceContainerLow    = Color(0xFFF7EDF1)
val HandyLightSurfaceContainer       = Color(0xFFF1E7EB)
val HandyLightSurfaceContainerHigh   = Color(0xFFEBE0E4)
val HandyLightSurfaceContainerHighest= Color(0xFFE5DADE)
```

### Theme.kt — propuesta

```kotlin
private val HandyDarkColorScheme = darkColorScheme(
    primary = HandyPrimary,
    onPrimary = HandyOnPrimary,
    primaryContainer = HandyPrimaryContainer,
    onPrimaryContainer = HandyOnPrimaryContainer,
    secondary = HandySecondary,
    onSecondary = HandyOnSecondary,
    secondaryContainer = HandySecondaryContainer,
    onSecondaryContainer = HandyOnSecondaryContainer,
    tertiary = HandyTertiary,
    onTertiary = HandyOnTertiary,
    tertiaryContainer = HandyTertiaryContainer,
    onTertiaryContainer = HandyOnTertiaryContainer,
    background = HandyBackground,
    onBackground = HandyOnBackground,
    surface = HandySurface,
    onSurface = HandyOnSurface,
    surfaceVariant = HandySurfaceVariant,
    onSurfaceVariant = HandyOnSurfaceVariant,
    surfaceTint = HandyPrimary,
    surfaceBright = HandySurfaceBright,
    surfaceDim = HandySurfaceDim,
    surfaceContainerLowest = HandySurfaceContainerLowest,
    surfaceContainerLow = HandySurfaceContainerLow,
    surfaceContainer = HandySurfaceContainer,
    surfaceContainerHigh = HandySurfaceContainerHigh,
    surfaceContainerHighest = HandySurfaceContainerHighest,
    inverseSurface = HandyInverseSurface,
    inverseOnSurface = HandyInverseOnSurface,
    inversePrimary = HandyInversePrimary,
    error = HandyError,
    onError = HandyOnError,
    errorContainer = HandyErrorContainer,
    onErrorContainer = HandyOnErrorContainer,
    outline = HandyOutline,
    outlineVariant = HandyOutlineVariant,
    scrim = HandyScrim,
)
```

### Shape.kt — propuesta (mantener + extras)

```kotlin
val HandyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Para la píldora flotante del IME: usar CircleShape (RoundedCornerShape con 50% radius)
// ⛔ Compose M3 NO expone `MaterialTheme.shapes.full` — el spec MD3 web lo define
// pero la API de Compose Material3 solo expone extraSmall..extraLarge.
// Para el pill, usa CircleShape directamente o RoundedCornerShape(percent = 50).
val PillShape = CircleShape
```

> **Nota**: `MaterialTheme.shapes.full` (que sí existe en el spec MD3 web y que el plan inicial mencionó erróneamente) **no existe en Compose Material3**. Se sustituye por `CircleShape` o por `RoundedCornerShape(percent = 50)`.

### Typography.kt — agregar emphasized variants

```kotlin
val HandyTypography = Typography(
    displayLarge = ..., headlineLarge = ..., titleLarge = ...,
    bodyLarge = ..., labelLarge = ...,
    // Nuevas para M3 Expressive:
    // displayLargeEmphasized, headlineMediumEmphasized, ...
)
```

### MotionTokens.kt — helper centralizado

```kotlin
object HandyMotion {
    val PopEasing       = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)        // overlay pop (PC)
    val EnterEasing     = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)         // screen enter
    val Emphasized      = CubicBezierEasing(0.2f, 0f, 0f, 1f)            // MD3 emphasized
    val Standard        = CubicBezierEasing(0.2f, 0f, 0f, 1f)            // MD3 standard
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val StandardDecel   = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccel   = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    val DurationShort  = 150
    val DurationMedium = 300
    val DurationLong   = 500
}

object HandySpacing {
    val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp
    val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp; val huge = 48.dp
}
```

---

## 🏗️ Plan por sprints

### Sprint 17 — Fundamentos MD3

**Tema**: tema base 100 % MD3, sin residuo MD2, tokens completos.

Tareas:
1. **`themes.xml`** → cambiar a `parent="@style/Theme.Material3.DayNight.NoActionBar"` (Compose-bound) y agregar:
   ```xml
   <item name="android:statusBarColor">@android:color/transparent</item>
   <item name="android:navigationBarColor">@android:color/transparent</item>
   <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
   ```
2. **`MainActivity.kt`** → `enableEdgeToEdge()` antes de `setContent`.
3. **`Theme.kt`** → poblar TODOS los roles de `darkColorScheme()` y `lightColorScheme()` incluyendo `surfaceContainer*`, `surfaceDim/Bright`, `outlineVariant`.
4. **`HandyTheme()`** → añadir parámetro `dynamicColor: Boolean = false` y caso `Build.VERSION_CODES.S`+.
5. **Eliminar hardcodes** en `SettingsScreen.kt` (líneas 194-200) → usar `MaterialTheme.colorScheme.{tertiary, secondary, error}`.
6. **`AppColors.kt`** (centraliza tokens semánticos secundarios como `StatusGreen`, `StatusOrange`, `YellowStar`).
7. Validación: `./gradlew :app:compileDebugKotlin`, lint, manual visual dark/light/dynamic.

Done: app builda, tonal surfaces se aplican, no hay hardcodes.

### Sprint 18 — Componentes shared MD3

**Tema**: catalogar primitivas reusables (mismo rol que `ui/components/` del PC).

Carpeta nueva `app/src/main/java/com/handy/app/ui/components/`:

| Componente | MD3 base | Variantes |
|---|---|---|
| `SettingsGroup.kt` | `ElevatedCard`+`Column` | Titled card con `HorizontalDivider` interno |
| `HandySlider.kt` | `Slider` | WithValueLabel, DiscreteSteps |
| `HandySwitch.kt` | `Switch` | Con icono opcional leading/trailing |
| `HandyChipGroup.kt` | `FilterChip`/`AssistChip`/`InputChip` | Single/multi select |
| `HandySearchBar.kt` | `SearchBar` (exp) | Active/inactive |
| `HandySegmentedButton.kt` | `SingleChoiceSegmentedButtonRow` | 2..5 opciones |
| `HandyBadge.kt` | `Badge` | Small, Large, Dot |
| `HandySnackbar.kt` | `Snackbar`+`SnackbarHost` | Con action |
| `HandyDialog.kt` | `AlertDialog`+`BasicAlertDialog` | Confirm / Text |
| `HandyFab.kt` | `FloatingActionButton` | Small/Medium/Large + Extended |
| `HandyListItem.kt` | `ListItem` | 1/2/3-line, clickable wrapper |
| `HandyDropdown.kt` | `ExposedDropdownMenuBox` + `OutlinedTextField.readOnly` | Wrapper para todos los selects — necesario porque Sprints 19–27 lo usan intensivamente |
| `HandyTonalBlock.kt` | `Surface(tonalElevation=…)` | Wrappers de elevation 1/3/6 |
| `HandyModalBottomSheet.kt` | `ModalBottomSheet` | Para PromptEditor (Sprint 26) y listas de keys |
| `MotionTokens.kt` | tokens | Easing/duration/spacing |
| `StatusDot.kt` | `Box+Canvas` | Success/Warning/Error/Info |

Done: carpeta `ui/components/` completa + 1 test por componente con `compose.ui.test`.

### Sprint 19 — General settings MD3

**Tema**: replicar PC `GeneralSettings.tsx`.

Estructura nueva `ui/settings/`:
```
ui/settings/
├── SettingsScreen.kt          (CompositionRoot)
├── components/
│   ├── SettingsGroup.kt       (definido en sprint 18)
│   ├── SettingsRow.kt
│   ├── MicrophoneSelector.kt
│   ├── AudioFeedbackToggle.kt
│   ├── SoundPicker.kt
│   ├── VolumeSlider.kt
│   ├── ModelSettingsCard.kt
│   └── ShortcutsHint.kt       (volume-key/A11y trigger stub — sprint 17.1)
```

Acción:
- `GeneralSettingsContent(viewModel)` reescrita con:
  1. `SettingsGroup("Audio")` → MicrophoneSelector + OutputDeviceSelector (si se expone) + MuteWhileRecording + AudioFeedback + VolumeSlider + SoundPicker.
  2. `SettingsGroup("Model")` → ModelSettingsCard.
  3. `SettingsGroup("Atajos")` → ShortcutsHint (volume-key / A11y trigger).

> ⚠️ **Lo que NO va aquí**: Shizuku / paste method / clipboard handling. Esos son **Text injection** y van en Sprint 20 (Advanced settings), porque así los organiza el PC. Esto corrige un mapeo incorrecto del plan inicial.

### Sprint 25 — Advanced settings MD3 + Experimental gated

**Tema**: replicar PC `AdvancedSettings.tsx`. Incluye **text injection** (Shizuku / paste method / clipboard handling), que pertenece aquí y NO en General (corregido del plan inicial).

Acción sobre `AdvancedSettingsContent(viewModel)`:
- Groups: **App**, **Output (= Text injection)**, **Transcription**, **History**, **Experimental** (gated).
- Cada item usa `SettingsGroup` + `HandyRows` (switch/dropdown/slider/number).
- Strings i18n: refactor todas las keys (`settings_section_*`, `settings_postproc` etc.) → nombres consistentes.

Done: mismo árbol visual que PC.

### Sprint 21 — Models: SearchBar + filtros + secciones

**Tema**: PC `ModelsSettings.tsx` con búsqueda y filtros.

Refactor `ModelCatalogScreen.kt`:
1. `SearchBar` MD3 arriba (debajo del TopAppBar).
2. Filter chip row: idioma (top 5 + "All"), "Recommended".
3. Section headers MD3 ("Your models" / "Available models").
4. Cards rediseñadas con `FilledCard` cuando active, `OutlinedCard` cuando disponible.
5. Botón rescan en TopAppBar.

`ModelsViewModel`:
- Añadir `filteredModels = combine(searchQuery, languageFilter, showExperimental, models, recommendationTier)` con `combine()`.
- `CatalogSorter.computeVisibleCatalog` extendido para estos filters.

Done: catálogo navegable y filtrable.

### Sprint 23 — History con audio + retry

**Tema**: PC `HistorySettings.tsx`.

`HistoryScreen.kt`:
1. Card rediseñado con `HandyListItem` 3-line.
2. `AudioPlayer` custom:
   - **Bar de progreso = MD3 `Slider`** (value 0..duration, valueRange 0..duration). Track secondary con `surfaceContainerHigh`.
   - **Buffering = MD3 `CircularProgressIndicator`** (24dp) cuando se carga el archivo (especialmente Linux/PC: build con `readFile` + `URL.createObjectURL`).
3. Botones: copy, save/star, retry (`FilledIconButton` primary), delete (`FilledIconButton` error container).
4. Retry flow: click → `Engine.retryHistoryEntryTranscription` con spinner inline + animation de pulse (`rememberInfiniteTransition`).
5. `FilledTonalButton` "Open recordings folder" arriba del grupo.

`HistoryViewModel`:
- Añadir `retry(entry)` action.
- `RecordingRepositoryProvider` (scoped storage) — exponer path vía `MediaStore` o `getExternalFilesDir(…)`.

Done: history con audio y acciones (Slider MD3 + CircularProgressIndicator para estados de carga).

### Sprint 22 — About + ThemeSelector + Language picker

**Tema**: PC `AboutSettings.tsx`.

`AboutContent.kt`:
1. `SettingsGroup("Idioma")` → `AppLanguageSelector` (HandyDropdown + `AppCompatDelegate.setApplicationLocales(...)`).
2. `SettingsGroup("Apariencia")` → `ThemeSelector` (SegmentedButton 3-way + persistencia).
3. `SettingsGroup("Acerca de")` → version, donate, source, app data dir, log dir, acknowledgments.

`HandyTheme()`:
- Parámetro `themeMode: ThemeMode = SYSTEM/LIGHT/DARK`.
- `LocaleListCompat` aplicado con `AppCompatDelegate.setApplicationLocales(...)`.

Persistencia:
- `SettingsStore.themeMode: ThemeMode`.
- `SettingsStore.appLanguage: String ("en"|"es")`.

> ⚠️ **Edge case (corregido)**: `AppCompatDelegate.setApplicationLocales(...)` recrea la `Activity`. `MainActivity.onCreate` ya registra estado de recording en `savedInstanceState`, pero el `EngineViewModel` NO se conserva (es singleton de proceso). Plan: añadir `recordarIMEPlacement()` en `EngineViewModel` y `SettingsStore.onboardedFlow`, validar que el IME `onStartInput` + `lastInputConnection` se reesetan limpiamente en cada `onCreate` post-config-change. **No loguear warning** durante locale-switch (es esperado).

### Sprint 21 — IME rediseño MD3 (overlay-equivalent) ⬆️ **movido aquí, justo después de los componentes shared**

> **Justificación**: el IME es la superficie flagship de Handy Android. Tratarlo tarde es contraproducente — correcciones tardías afectan a las capturas de cada sprint siguiente.

**Tema**: píldora flotante MD3 con `CircleShape` y motion springs (alineado visualmente con PC overlay).

`HandyInputMethodService.kt`:
1. `Surface` con `shape = CircleShape` (Compose M3 no expone `shapes.full`; ver corrección Sprint 17 ↗).
2. Tonal elevation 3.dp en lugar de `surfaceVariant.copy(alpha=0.7f)`.
3. IconButton sizes a 48dp (touch target).
4. **Waveform reactivo**: 9 BoxBars con `animateFloat(spring(...))` (`stiffness = MediumLow`, `dampingRatio = 0.6`).
5. **Pulsing dot** y **IdlePulsingDot**: usar `rememberInfiniteTransition` con `spec.spring` + `RepeatMode.Reverse`.
6. **Caret blink**: preservar `tween(1050, steps(1))`.
7. **Estados (6)**: AnimatedContent con `tween(300, EnterEasing)` y offset vertical 1/4 alto.
8. **Insert**: `FilledTonalButton` (token primary container).
9. **Discard**: `TextButton`.
10. **Error**: surface = `errorContainer.copy(alpha = 0.08f)`, border = `error.copy(alpha = 0.2f)`.
11. **Top vs bottom placement** → cambiar `padding(bottom = 56.dp)` ↔ `padding(top = 56.dp)`, controlado por `SettingsStore.imePlacement`.
12. **Confirm bar**: 4 líneas max + `HorizontalDivider` MD3 antes de accionables.
13. **Confirm text**: copy button siempre visible (FilledIconButton secondary container).

`HandySpringTokens.kt`:
```kotlin
object HandySpring {
    fun gentle() = spring<Float>(stiffness = 380f, dampingRatio = 0.8f)
    fun bouncy() = spring<Float>(stiffness = 380f, dampingRatio = 0.6f)
}
```

**Reordenación del plan** (Sprint 21.x):

- ~~Sprint 24 original~~ (IME) → ahora **Sprint 21** justo después de Shared Components.
- Sprint 22 pasa a ser Models (búsqueda/filtros).
- Sprint 23 pasa a ser History (audio + retry).
- Sprint 24 pasa a ser About + Theme/Language.
- Sprint 25 pasa a ser Advanced settings + Experimental.
- Sprint 26 pasa a ser Post-processing.
- Sprint 27 pasa a ser Debug.
- Sprint 28 pasa a ser Onboarding (pulido).
- Sprint 29 pasa a ser Polish + a11y + tests.

### Sprint 26 — Onboarding MD3

**Tema**: wizard con primitivas M3.

`OnboardingScreen.kt`:
1. StepIndicator: `Surface(shape=CircleShape, tonalElevation=…)` + `AnimatedContent`.
2. Icon container: `Box(size=120, color=surfaceContainerHigh)` + `Icon(tint=primary, size=64)`.
3. Botones: `Button` (filled primary), `OutlinedButton`, `TextButton`.
4. Progress: `LinearProgressIndicator` con label visible.
5. AnimatedContent con `tween(500, PopEasing)`.

Done: onboarding MD3 pulido.

### Sprint 26 — Post-processing MD3

**Tema**: PC `PostProcessingSettingsApi/Prompts`.

Carpeta nueva `ui/postprocess/`:
```
ui/postprocess/
├── PostProcessScreen.kt
├── components/
│   ├── ProviderSelect.kt         (HandyDropdown)
│   ├── BaseUrlField.kt
│   ├── ApiKeyField.kt
│   ├── ModelSelectField.kt
│   ├── PromptList.kt             (LazyColumn con cards)
│   └── PromptEditor.kt           (HandyModalBottomSheet — no BasicAlertDialog)
```

> ⚠️ **Corrección sobre UX**: en el plan inicial sugerí `BasicAlertDialog` para el editor de prompts. Un `BasicAlertDialog` no escala bien para un editor multilínea (provoca teclado cortado). Usar `HandyModalBottomSheet` (definido en Sprint 18).

`PostProcessContent` actual → refactor a este árbol.

Done: post-processing completo.

### Sprint 27 — Debug panel (gated)

**Tema**: PC `DebugSettings.tsx`.

Nueva ruta en `AppNavigation` solo si `Settings.debugMode == true` (default false en release).
- LogLevelSelector
- WhatsNewPreview
- UpdateChecksToggle
- SoundPicker
- LiveLogViewer (custom: ring buffer de `Log.d/w/e`)

Done: debug screen MD3, gated.

### Sprint 28 (cierre) — Polish, accesibilidad, motion, tests, docs

1. **Predictive back** (Android 14+): verificar en `MainActivity` que `OnBackPressedDispatcher` esté conectado.
2. **Foldable hinge avoidance**: validar que la pill IME no caiga sobre hinges — usar `WindowInfoTracker.getOrCreate(activity).windowLayoutInfo` en IME.
3. **Touch targets**: 48 dp mínimo en toolbar/IME/TopAppBar.
4. **Contraste**: WCAG AA en TODOS los pares (background × onSurface, surfaceContainer × onSurface, etc.).
5. **Motion audit**: cada transición usa tokens de `HandyMotion`.
6. **Tests**:
   - `ThemeTest`: `darkColorScheme`/`lightColorScheme` returns correct surface hierarchy.
   - `SettingsGroupTest`: accessibility.
   - `IMEStateMachineTest`: state transitions.
   - `PostProcessFormTest`: form validation.
   - `AudioPlayerTest`: MediaPlayer lifecycle.
7. **Snapshots**: actualizar `scripts/capture_ime.sh` y `scripts/capture_onboarding.sh`.
8. **`AGENTS.md` + `PROGRESS.md`**: actualizar al cierre de cada sprint.

---

## 🧩 Tabla resumen de sprints

| Sprint | Entregable | Riesgo | Dependencias |
|---|---|---|---|
| **17** | Theme MD3 + edge-to-edge + tokens | Bajo | — |
| **18** | Componentes shared | Medio | 17 |
| **19** | General settings | Medio | 18 |
| **20** | Advanced settings + Experimental | Alto | 18 |
| **17** | Theme MD3 + edge-to-edge + tokens | Bajo | — |
| **18** | Componentes shared | Medio | 17 |
| **19** | General settings | Medio | 18 |
| **20** | Models con búsqueda/filtros | Medio | 18 |
| **21** | **IME MD3 rediseño** (flagship) | **Alto** | **17, 18** |
| **22** | About + ThemeSelector + Language picker | Bajo | 17, 18 |
| **23** | History con audio | Alto | 18 |
| **24** | Advanced settings + Experimental | Alto | 18 |
| **25** | Post-processing MD3 | Alto | 18 |
| **26** | Onboarding MD3 | Medio | 18 |
| **27** | Debug panel gated | Bajo | 18 |
| **28** | Compose Current: see table summary — Polish, a11y, motion, tests, docs | Bajo | Todos |

Estimación total: ~13 sprints. Cada sprint ≈ 1 día si se sigue el patrón actual del repo.

---

## ✅ Definition of Done (universal)

Por sprint:
- [ ] `./gradlew :app:compileDebugKotlin` verde.
- [ ] `./gradlew :app:testDebugUnitTest` verde (con nuevos tests si aplica).
- [ ] `./gradlew :app:lintDebug` sin warnings.
- [ ] Cero `Color(0x)` hardcoded fuera de `Color.kt` (grep verificable).
- [ ] Componentes custom solo cuando MD3 no provea equivalente.
- [ ] Touch targets verificados ≥ 48 dp.
- [ ] Strings 100 % en `strings.xml` (sin literal `stringResource` crudo).

Cierre global de la migración:
- [ ] Auditoría MD3 self-score ≥ 9/10 en cada categoría (color, typography, shape, elevation, components, layout, navigation, motion, accessibility, theming).
- [ ] Capturas MD3 generadas para IME, Models, Settings, Onboarding (light/dark/dynamic).
- [ ] `AGENTS.md` y `PROGRESS.md` actualizados.
- [ ] README.md refleja el estado MD3.
- [ ] Cero referencias a `Theme.Material.Light.NoActionBar` en repo.

---

## ❓ Decisiones pendientes (con el usuario)

| # | Decisión | Sugerencia |
|---|---|---|
| 1 | **Idioma único**: ¿Inglés o español como source? | Inglés (alineado con i18n Sprint 12 y docs recientes). Español en `values-es/`. |
| 2 | **Dynamic color**: ¿Wallpaper colors? | OFF por defecto. Toggle opcional en ThemeSelector. |
| 3 | **Iconografía**: ¿Material Symbols Outlined o Lucide (PC)? | `androidx.compose.material:material-icons-extended` (M3 nativo). |
| 4 | **MD3 Expressive**: ¿Adoptar ahora o esperar? | Adoptar **progresivamente** (spring motion primero, después expressive layout si BOM lo permite). |
| 5 | **Post-processing providers**: ¿Todos o solo OpenAI? | OpenAI + Anthropic + Ollama + "Custom" (4 opciones). |
| 6 | **Donate button**: ¿Mostrar en About? | Sí, abre `https://handy.computer/donate`. |
| 7 | **Live log viewer**: ¿Implementar o marcar como TODO? | Implementar como ring buffer de `android.util.Log`. |
| 8 | **Locale switching**: ¿Per-app en tiempo real? | Sí con `AppCompatDelegate.setApplicationLocales(...)` (API 33+ nativo, menor con AppCompat). |

---

## 📚 Referencias

- Material 3 Compose: https://developer.android.com/jetpack/compose/designsystems/material3
- Color tokens: https://m3.material.io/styles/color/the-color-system/tokens
- Shapes: https://m3.material.io/styles/shape/applying-shape
- Typography: https://m3.material.io/styles/typography/type-scale-tokens
- Motion: https://m3.material.io/styles/motion/easing-and-duration/tokens
- Adaptive layout: https://developer.android.com/develop/ui/compose/layouts/adaptive
- SearchBar (exp): https://developer.android.com/reference/kotlin/androidx/compose/material3/SearchBar
- Edge-to-edge: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- Predictive back: https://developer.android.com/guide/navigation/custom-back/predictive-back
- Material color utilities (generadores): https://github.com/material-foundation/material-color-utilities
- PC Handy (referencia visual): `src/styles/theme.css`, `src/App.css`, `src/overlay/RecordingOverlay.css`, `src/components/Sidebar.tsx`, `src/components/ui/*`

---

## 🚀 Siguiente paso recomendado

Empezar **Sprint 17** — Fundamentos MD3:
1. Cambiar `themes.xml` a `Theme.Material3.DayNight.NoActionBar`.
2. `enableEdgeToEdge()` en `MainActivity`.
3. Poblar `surfaceContainer*`, `surfaceDim`, `surfaceBright` en `Theme.kt`.
4. Quitar hardcodes de color en `SettingsScreen.kt`.

Validar con `./gradlew :app:compileDebugKotlin` y screenshot en device.

Cuando lo confirmes, empezamos **Sprint 18** (componentes shared) y a continuación **Sprint 21 (IME rediseño MD3)** — el flagship — antes que cualquier pantalla secundaria, ya que correcciones tardías en la pill afectan a todos los sprints siguientes.

---

## 🩹 Correcciones aplicadas a este plan (revisión thinker)

1. **`MaterialTheme.shapes.full` no existe en Compose M3** → usar `CircleShape` o `RoundedCornerShape(percent = 50)` para la pill del IME.
2. **`OutputDeviceSelector` mapeo mal escrito** → es audio hardware playback (no text injection); separado de Shizuku/paste method.
3. **`Shizuku / PasteMethod` movidos de General → Advanced** → correctamente alineado con la organización PC.
4. **IME sprint movido de #24 → #21** → justo después de Shared Components, antes que cualquier pantalla secundaria, para evitar retrabajos tardíos.
5. **`HandyDropdown.kt` agregado al Sprint 18** → `ExposedDropdownMenuBox` aparecerá en casi todas las pantallas siguientes.
6. **`HandyModalBottomSheet.kt` agregado al Sprint 18** → necesario para el editor de prompts (sustituye a `BasicAlertDialog` que no escala).
7. **Shortcuts / hotkeys PC mapeados** → volume-key o Accessibility Service.
8. **`AppCompatDelegate.setApplicationLocales`** recrea la activity → edge case documentado en Sprint 22.
9. **AudioPlayer**: usar MD3 `Slider` para seek bar y `CircularProgressIndicator` para buffering.
10. **Foldable hinge avoidance** agregado al Sprint 28 con `WindowInfoTracker`.

---

## 🛠 Corrección suplementaria — Plan ejecutable 2026-07-17 (post-Sprint 24)

> Esta sección es el **plan canónico ejecutable para Sprints 25 → 29**. Las correcciones 1-10 arriba siguen vigentes, pero ahora se aplican con el contexto completo del Sprint 24 cerrado.
>
> Las inconsistencias detectadas entre el plan original (arriba) y la realidad se cierran aquí:
>
> - El plan original usa dos enumeraciones distintas para los sprints post-21: la sección "Plan por sprints" lista 25 = Advanced, 26 = Onboarding, 26 = Post-processing (numeración duplicada), 27 = Debug, 28 = Polish; mientras que la "Reordenación del plan (Sprint 21.x)" los renumera a 25 = Advanced, 26 = Post-processing, 27 = Onboarding, 28 = Debug, 29 = Polish. **La reordenación es la autoridad**.
> - El plan lista `HandyListItem.kt` en Sprint 18 pero la auditoría del 17-jul-2026 revela que el fichero no existe — `SettingsRow` y `SettingsRowDivider` viven en `SettingsGroup.kt` como primitivos locales. Sprint 25 lo extrae a su propio archivo.
> - El plan dejaba `EngineBridge.nativeRetryHistoryEntry` + `RecordingRepositoryProvider` como carry-over "sprints posteriores" indefinidos. Sprint 25 los absorbe explícitamente porque es la única manera de que el botón Retry del History (shipped en Sprint 24) deje de ser stub.

### Sprint 25 — Advanced Settings refinement + Retry backend

**Goal**: Polish Advance Settings sobre primitivos MD3 shared + implementar `EngineBridge.nativeRetryHistoryEntry` + `RecordingRepositoryProvider` (binding dos carry-overs de Sprint 24).

**Work items (en orden de dependencia)**:
1. **Limpieza estructural**: extraer `HandyListItem.kt` (estaba declarado en Sprint 18 pero faltaba en repo). Borrar `ui/shared/` (carpeta vacía carry-over).
2. **`RecordingRepositoryProvider`** (`app/data/RecordingRepositoryProvider.kt`, nuevo) — ring-buffer que persiste audio recordings. Backea inicialmente a `Context.getExternalFilesDir("recordings")` + manifest JSON (`recording_index.json`). Thread-safe (`Mutex`). API: `list()`, `get(id)`, `store(bytes)`, `delete(id)`, `cap()`.
3. **AudioRecorder.persist() integration** — `RecordingService` ahora llama `repository.store(bytes, timestamp)` en cada `nativeStopRecording`. Primero dual-write (legacy `transcription_log.db` + repository) flag-gated por `SettingsStore.recordingRepositoryShouldDualWrite = true` durante las primeras 2 semanas de transición.
4. **`EngineBridge.nativeRetryHistoryEntry(entryId: Long): Boolean`** — nuevo JNI. `history.rs` busca el entry en el manifest index, lee el .wav del repository, regenera el `HistoryEntry` con texto nuevo, actualiza la `HistoryTable`. Kotlin: substituye `delay(RETRY_SIMULATED_DELAY_MS)` por `EngineBridge.nativeRetryHistoryEntry(entryId)` en `HistoryViewModel.retry(...)`. Preservation del state-flag + structured-concurrency pattern.
5. **`AdvancedSettingsContent` refactor** (`SettingsScreen.kt`):
   - **App**: `HandySwitch` (experimentalEnabled).
   - **Output** (= Text injection): `HandyDropdown` con Shizuku / IME / Clipboard (default Shizuku > IME > Clipboard, alineado con PC).
   - **Transcription**: `HandySwitch` (vad) + `HandySwitch` (addFinalSpace) + **NEW** `CustomWords` (`OutlinedTextField` + chip group via `HandyChipGroup`).
   - **History**: `HandyDropdown` (HistoryLimit: 50/100/250/500/∞), `HandyDropdown` (RecordingRetentionPeriod: 1day/1week/1month/forever).
   - **Experimental** (gated): `HandyDropdown` (AccelerationSelector: CPU/Vulkan/NNAPI).
6. **`GeneralSettingsContent` polish** — mover filas a `HandyListItem` (de `SettingsGroup.kt`).
7. **Tests nuevos (JVM puro)**:
   - `CustomWordsParserTest` (5) — separadores `,` / newline, trim, blank handling.
   - `AccelerationSelectorTest` (4) — CPU fallback cuando Vulkan/NNAPI no disponibles.
   - `RecordingRepositoryProviderTest` (8) — store/list/get/delete idempotencia, size cap.
   - **Target**: 37 + 17 = **54 PASS** total.
8. **Lint trajectory**: estable en 84. AGP bump se difiere a Sprint 26 para evitar scope creep.
9. **Éxito on-device**: el botón Retry de History reproduce texto usando el audio persistido (logcat `HistoryVM: Retry completed for id=...`); Advanced toggle cambios persisten y se ven reflejados en el picker.

**Esfuerzo realista**: 2.5 días (revisado tras code-reviewer feedback). Si los carry-overs de Sprint 24 atrasan, Split en **Sprint 25a** (Settings refinement + structural cleanup, 1d) + **Sprint 25b** (RecordingRepositoryProvider + native JNI binding, 1.5d) preserva shippability independiente.

> **IMPORTANTE — listar siempre como objetivos**: Si `EngineBridge.nativeRetryHistoryEntry` o `RecordingRepositoryProvider` quedan sin cerrar al final del sprint, Sprint 26 NO puede iniciar (sigue bloqueado por el Retry stub de Sprint 24). El Retry stub es inviable de mantener post-release.

### Sprint 26 — Post-processing MD3 + AGP bump

**Goal**: Replicar PC `PostProcessingSettingsApi` + prompts custom; bump AGP/Gradle para cerrar las 18 `GradleDependency` + 3 `AndroidGradlePluginVersion` warnings.

**Work items**:
1. **AGP 8.x → 9.x bump** — actualizar `gradle-wrapper.properties` + scripts. Target: 9.7+ estable.
2. **Folder `ui/postprocess/`**:
   - `ProviderSelect.kt` — `HandyDropdown` con OpenAI / Anthropic / Ollama / Custom. Default Ollama si `prefill_endpoint = http://10.0.2.2:11434`.
   - `BaseUrlField.kt` + `ApiKeyField.kt` — `OutlinedTextField` con visibility toggle para ApiKey.
   - `ModelSelectField.kt` — Input + refresh `IconButton` que dispare `EngineBridge.nativeFetchModels(provider, endpoint, apiKey)`. Si provider=Custom → input manual.
   - `PromptList.kt` + `PromptEditor.kt` — `LazyColumn` de cards via `HandyTonalBlock` + editor en `HandyModalBottomSheet` (NO `BasicAlertDialog` — ver corrección #9).
3. **`PostProcessContent.kt`** (`ui/postprocess/`) — composition root con 3 SettingsGroups: PROVIDER / CONNECTION / PROMPTS. **Move out de `SettingsScreen.kt`** → Post-processing es destination propia en nav rail (alignment PC).
4. **Network security** — `network_security_config.xml` con `<domain-config cleartextTrafficPermitted="true">` para `10.0.2.2` y `localhost` (Ollama default).
5. **`PostProcessFormValidator.kt`** (puro) + `PostProcessFormValidatorTest` (8 tests) — validate endpoint URL, non-empty api key, model fetchable, prompts list non-empty.
6. **Tests**: 8 nuevos. Target: **62 PASS**.
7. **Lint trajectory**: 84 → ~63 (delta -21 del AGP bump: 18 + 3 resolved).

**Esfuerzo realista**: 2 días (revisado — AGP bump solo consume 0.5d, pero el provider migration + nuevas components + network security + 8 tests require el resto). Si AGP bump se complica (BuildConfig / Kotlin compiler / Compose compatibility), split en **26a** (AGP + network security) + **26b** (provider migration).

### Sprint 27 — Onboarding MD3 refinado

**Goal**: Onboarding completamente en primitivos M3 natives; cierre de 14 launcher/icon warnings.

**Work items**:
1. **Layout overhaul** (`OnboardingScreen.kt`) — `AnimatedContent(tween(500, PopEasing))` entre pages. `EmptyPulsingDot` reuso de Sprint 21 pill.
2. **StepIndicator** — `Surface(shape=RoundedCornerShape(percent=50), tonalElevation=3.dp)` + `Box.size(48.dp)` con step number.
3. **Icon container** — `Box(size=120, color=surfaceContainerHigh, shape=MaterialTheme.shapes.large)` con `Icon(tint=primary, size=64)`.
4. **Action trio** — `Button` (Primary filled) + `OutlinedButton` + `TextButton`. Min 48dp touch targets.
5. **Progress** — `LinearProgressIndicator` con label visible. Solver el percentage via state-derived.
6. **Launcher icon cleanup** — `IconDuplicates` + `IconLauncherShape` + `IconDipSize` + `MonochromeLauncherIcon` (~14 warnings). Regenerar `mipmap-anydpi-v26` con MD3 adaptive icon proper.

**Tests**: no strictly required (layout binding). Coverage ya OK.

**Lint trajectory**: ~63 → ~49 (delta -14).

**Esfuerzo realista**: 1.5 días (revisado — adaptive launcher icons requieren un foreground vector + background color/vector design asset, que no existe aún en el repo. Sin un design asset, +0.5d para crearlo o split en **27a** (onboarding composables, 1d) + **27b** (icon ship post-design, 0.5d)).

### Sprint 28 — Debug panel gated

**Goal**: Vista de developer tools gated por `Settings.debugMode = true` + Shizuku API probe.

**Work items**:
1. **Routing** (`AppNavigation.kt`) — nueva nav rail/bar item solo si `Settings.debugMode = true`. Default `false`. Setting nuevo en `SettingsStore`.
2. **`ui/debug/DebugContent.kt`**:
   - `LogLevelSelector` (HandyDropdown: VERBOSE/DEBUG/INFO/WARN/ERROR).
   - `WhatsNewPreview` (botón → `WhatsNewModal` ya existente).
   - `UpdateChecksToggle` (HandySwitch).
   - `SoundPicker` (reuse de Sprint 19).
   - `LiveLogViewer` — ring buffer circular (500 lines) hooking `android.util.Log` via wrapper. UI: `LazyColumn` reverse-order.
3. **`RingBufferLog.kt`** (puro) + `RingBufferLogTest` (5 tests) — JVM-testable ring buffer.
4. **`DebugViewModel.kt`** — captura logs via logger interface + tail a UI via `StateFlow` debounced (200ms sliding).
5. **Shizuku API probe** — investigar `PrivateApi` (3 warnings) — afecta Android 16 compat. Si `checkSelfPermission` + `requestPermission` (API pública) cubren lo que hacemos, replace las reflection-based hidden API calls. Documentar todo en AGENTS.md.

**Lint trajectory**: estable o -3 si Shizuku probe resuelve. Expect ~46 warnings.

**Auth/genre risk**: log spam stall UI thread. Mitigated via 200ms debounce.

**Esfuerzo**: 1 día.

### Sprint 29 — Polish + accesibilidad + tests + docs (cierre)

**Goal**: WCAG AA audit + predictive back + foldable hinge + final lint sweep.

**Work items**:
1. **Motion token audit** — todo `tween()` / `spring()` referencia via `MotionTokens`. Pickups: HistoryScreen, AppNavigation, Settings.
2. **`UnusedResources` sweep** — 36 warnings. Grep `strings.xml`/`drawables/`/`mipmap/`. Eliminar solo las referencias completamente inexistentes (cuidado con `stringResource()` indirecto en lambda capture).
3. **`mipmap-anydpi-v26` folder cleanup** — resolución final.
4. **Predictive back** (Android 14+) — verificar `OnBackPressedDispatcher` unwind state correctamente. `OnBackPressedCallback(true) { ... }` para IME + Navigation root.
5. **Foldable hinge avoidance** — `WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(...)` en IME service. Puro logic test via reflection si posible.
6. **`ThemeContrastTest.kt`** (jvm pure) — itera todos los pares (background × onSurface, surfaceContainer × onSurface, primary × onPrimary, etc.), compute luminance, asserts ≥ 4.5:1 (texto) y ≥ 3:1 (UI components). Pin the Handy palette + light scheme.
7. **`BACKENDS.md` update** — Vulkan re-enable test report + QNN/Hexagon eval (o nota "deferred indefinitely" si no llegamos).
8. **`capture_ime.sh` + `capture_onboarding.sh`** snapshots refresh.
9. **`IMEStateMachineTest`** (puro) — state transitions over tipos (IDLE → LOADING, etc.) para codificar el contrato del pill.

**Tests**: `ThemeContrastTest` (12-16 assertions) + `IMEStateMachineTest` (6) + (`RingBufferLogTest` consolidado de Sprint 28).

**Lint trajectory**: 46 → **~9 residuals** (revisado tras code-reviewer feedback — strict "0 warnings" no es realista dado el scope). Las 9 residuals esperadas son: 1 `mipmap-anydpi-v26` carry-over (estructural), 3 `PrivateApi` si Shizuku probe no encuentra alternative public API, 5 misc documentation/spec lint categories documenting choices deliberados. **Cada residual se documenta explícitamente en este bloque con causa + decisión arquitectónica**. |

**Pre-MD3 leak cleanup (encubierto por code-reviewer)**: Antes del polish, swap `AlertDialog` uso en `AboutContent.kt` (Licenses flow) por `HandyDialog` (primitivo Sprint 18). Type-changes en `SettingsScreen.kt` (ShizukuStatusDialog también) heredan el mismo primitive. **Esta cleanup se hace en Sprint 25**, no Sprint 29 — es una pre-MD3 residue del Sprint 23, no parte del polish.

**Esfuerzo**: 1.5 días.

### 🏁 Definition of "MD3 Native Complete"

Para cerrar oficialmente la migración, **todos** los siguientes deben ser verdaderos el 17 de julio de 2026 ± un sprint:

- [ ] **0 imports Compose Material 1.x** (`grep -r 'import androidx.compose.material\\.' handy-android/app/src/main/java/com/handy/app/ui | grep -v '.material3.'` → empty).
- [ ] **0 `Color(0x...)` hardcoded fuera de `Color.kt`** — grep verifiable.
- [ ] `ui/components/` cubre los 17 primitivos Shared declarados en Sprint 18: SettingsGroup, Slider, Switch, ChipGroup, SearchBar, SegmentedButton, Badge, Snackbar, Dialog, Fab, ListItem, Dropdown, TonalBlock, ModalBottomSheet, MotionTokens, StatusDot, SpringTokens.
- [ ] `ui/shared/` (carpeta vacía carry-over) eliminado.
- [ ] 6 nav destinations: General / Models / History / About + Debug (gated) + Post-process dedicado (post-Sprint 26).
- [ ] IME Pill: 6-state machine, 48dp touch targets, top/bottom placement, spring motion, post-Sprint 29 fix `confirming-cursor` blink.
- [ ] **All touch targets ≥ 48dp** — grep `: Modifier.size(N.dp)` donde `N < 48` debería retornar 0 en code paths visibles (excluyendo internal animations + spacers).
- [ ] Post-processing + Debug screens completamente en primitivos Shared.
- [ ] `compileDebugKotlin` + `testDebugUnitTest` + `lintDebug` todos verdes: **0 errors + ≤ 10 lint warnings residuals documentados explícitamente en el Sprint 29 Definition of Done table**. No aspirar a 0 strict — el alcance de migration deja residuals estructurales (1 mipmap-anydpi-v26 carry-over, up-to 3 PrivateApi si Shizuku probe falla, ~5 documentation/spec categories).
- [ ] `ThemeContrastTest` en CI asegura WCAG AA compliance going forward.
- [ ] `BACKENDS.md` + `LIMPIA.md` actualizados con estado final + decisiones deferred.
- [ ] Snapshots MD3 generadas para: IME (6 estados), Models, Settings (General + Advanced), Onboarding (post-Sprint 27), History (con audio transcript).
- [ ] Release v2.0.0-alpha con `RecordingRepositoryProvider` + native retry operativo.
