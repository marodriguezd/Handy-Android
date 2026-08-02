# Auditoría — Handy Android (100% del código) vs. Material 3 + Google I/O 2026

> Fecha: 2 de agosto de 2026 (revisión tras modernización I/O 2026)
> Alcance: 100% del módulo Android (`app/src/main`), contrastado con los anuncios de Material Design de Google I/O 2026.

---

## 0. Evidencia empírica (gates ejecutados)

| Gate | Resultado |
|---|---|
| `checkModelCatalog` | ✅ |
| `testDebugUnitTest` (74 tests) | ✅ |
| `lintDebug` (0 errores) | ✅ |
| `BUILD SUCCESSFUL` (checkModelCatalog + testDebugUnitTest + lintDebug) | ✅ |
| `assembleDebug` | ⚠️ Bloqueado únicamente por toolchain ARM64 del host (`clang` x86_64 del NDK → SIGILL). Documentado en `AGENTS.md`. No es un error de código: `compileDebugKotlin` y `processDebugResources` pasan antes de `configureCMake`. |

**Cobertura leída al 100%**: 42 ficheros Kotlin, 18 recursos (`res/`), `AndroidManifest.xml`, `app/build.gradle.kts`, bridge JNI (`app/src/main/cpp/native-lib.cpp` + `CMakeLists.txt`), 15 ficheros de tests.

---

## 1. Correctitud funcional — hallazgos

| # | Sev. | Hallazgo | Estado |
|---|---|---|---|
| **F1** | 🔴 Alta | **FGS tipo `microphone` sin comprobar `RECORD_AUDIO`** → `SecurityException` en Android 14+ si el permiso está denegado/revocado. 5 call-sites sin guarda: `LiveSubtitleActivity`, `HandyTileService`, `BootReceiver`, `RemoteControlReceiver` + `LiveSubtitleService.onCreate()` llama `startForeground` antes del check que solo se hace en `ACTION_START`. | ✅ **RESUELTO** (ver §1.1) |
| **F2** | 🔴 Alta | Overlay de subtítulos `MATCH_PARENT` (ancho) anclado abajo con solo `FLAG_NOT_FOCUSABLE`, **sin `FLAG_NOT_TOUCHABLE`** → intercepta todos los toques de la franja inferior de la app subyacente. | ✅ **RESUELTO** (ver §1.1) |
| F3 | 🟠 Media | **API key en claro**: `OutlinedTextField` sin `PasswordVisualTransformation` (visible en pantalla y en recents preview). | ✅ **RESUELTO** (ver §1.1) |
| F4 | 🟠 Media | **Sin icono de app**: el manifest no declara `android:icon`/`roundIcon` (lint `MissingApplicationIcon` deshabilitado). | ✅ **RESUELTO** (ver §1.1) |
| F5 | 🟡 Baja | `VoiceRecognitionService.onStartListening` sobrescribe `callback` sin cancelar el job de transcripción anterior → el job obsoleto puede postear `results()`/`error()` a un callback caducado. | ✅ **RESUELTO** (ver §1.1) |
| F6 | 🟡 Baja | `LlmPostProcessor.process` (red, ≤5 s) se ejecuta **dentro** de `inferenceMutex` → una LLM lenta serializa todas las transcripciones. | ✅ **RESUELTO** (ver §1.1) |
| F7 | 🟡 Baja | Símbolo JNI `Java_..._cancelTranscribe` (sin sufijo `__J`) es **código muerto**: el `cancelTranscribe()` sin args de Kotlin es un override normal (no external); solo se enlaza la variante `__J`. | ✅ **RESUELTO** (ver §1.1) |
| F8 | 🟢 Muy baja | Magic number `0x00400000` para `ACTION_IME_ENTER` en vez de `AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER` (API 31). | ✅ **RESUELTO** (ver §1.1) |

### 1.1 Resueltos en esta revisión

**F1 — FGS `microphone` sin `RECORD_AUDIO`** (crash en Android 14+), corrección en 2 capas:

- **Servicios** (defensa en profundidad): `FloatingButtonService.onCreate` y `LiveSubtitleService.onCreate` envuelven `startForeground()` en `runCatching { ... }.onFailure { AppLog.record(...); stopSelf(); return }` → si el permiso fue revocado en runtime, el servicio se detiene limpiamente en vez de crashear el proceso. Retorno no-local válido (`onFailure` inline); `recorder`/`onDestroy` siguen seguros en ambos.
- **Callers** (evitar el fallo antes de arrancar): nuevo helper `PermissionChecker.hasMicrophonePermission(context)` en `PermissionState.kt` (DRY, reutiliza el `granted` privado). Aplicado en:
  - `HandyTileService.onClick` — gate con guard `!FloatingButtonService.isRunning` (permite parar un servicio ya activo si el permiso se revocó en plena sesión) + log `HandyTile`.
  - `BootReceiver` — gate + log "skipping autostart".
  - `RemoteControlReceiver` — gate con guard `!isRunning` + log existente.
  - `LiveSubtitleActivity` — si falta el micrófono, solicita `RECORD_AUDIO` vía `rememberLauncherForActivityResult(RequestPermission)`; al conceder arranca el servicio, al denegar muestra `live_subtitle_perm_mic`.

**F2 — overlay que robaba toques**: `LiveSubtitleService.showOverlay()` usa `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE` → la barra de subtítulos es 100% pasiva. (El overlay flotante de 64×64 lo omite a propósito: necesita drag/tap/hold.)

**F3 — API key en claro**: `LlmSettingsActivity.kt` — el campo de API key usa `PasswordVisualTransformation()` + `KeyboardOptions(keyboardType = KeyboardType.Password)` (imports verificados contra el AAR de Compose 1.11.4: `KeyboardOptions` sigue en `androidx.compose.foundation.text`, sin deprecación). La clave queda oculta en pantalla, preview de recents y capturas. Sin toggle de visibilidad a propósito: `Visibility`/`VisibilityOff` no existen en `material-icons-core`.

**F4 — icono de app**: nuevo icono de marca con adaptive icon:
- `res/drawable/ic_launcher_foreground.xml` — micrófono blanco (path de `ic_mic.xml`) centrado en la zona segura (view 108dp, escala 2.1 → 50.4dp, translate 28.8).
- `res/drawable/ic_launcher_background.xml` — gradiente lineal `#6750A4 → #7D5260` (ángulo 315°, múltiplo de 45).
- `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` — `adaptive-icon` con `background`/`foreground`/`monochrome` (icono temático Android 13+). Con `minSdk=26` cubren todos los dispositivos, sin PNG legacy.
- Manifest: `android:icon="@mipmap/ic_launcher"` + `android:roundIcon="@mipmap/ic_launcher_round"`; quitado `MissingApplicationIcon` de los disables de lint.
- Tests de regresión en `AppIconTest.kt` (manifest, capas adaptive-icon de ambos iconos, inflado como `AdaptiveIconDrawable` y geometría del glifo dentro del círculo seguro de radio 33).

**F5 — job obsoleto en `VoiceRecognitionService`**: `onStartListening` llama `scope.coroutineContext.cancelChildren()` al inicio (antes de asignar el nuevo `callback`) → el job de transcripción de la sesión anterior queda cancelado y no puede postear `results()`/`error()` a un listener caducado. La guarda de identidad del `finally` (`if (callback === activeCallback)`) sigue protegiendo al callback nuevo.

**F6 — LLM fuera del `inferenceMutex`**: `TranscriptionEngine.transcribe` reestructurado — dentro del mutex quedan solo la guarda `isLatest`, `evictIfRequested`, carga/validación del modelo y la transcripción nativa; `LlmPostProcessor.process()` (red, ≤5 s) corre **fuera** del mutex con su guarda `isLatest` post-proceso (resultados obsoletos → `CancellationException`) y `scheduleUnload` en la misma posición. Una LLM lenta ya no serializa el resto de transcripciones y `activeEngine` se libera justo tras la inferencia nativa.

**F7 — símbolo JNI muerto eliminado**: `native-lib.cpp` solo exporta `Java_com_handy_android_WhisperLib_cancelTranscribe__J` (el sufijo `__J` codifica el único parámetro `long` del `external fun cancelTranscribe(context: Long)` de `WhisperLib`). La variante sin sufijo correspondería a un `cancelTranscribe()` sin args de Kotlin que **no existe como `external`** (es un override normal de `IWhisperEngine`), así que jamás se enlazaba. Verificado con `g++ -std=c++17 -fsyntax-only` sobre el archivo editado (incluye `whisper.h` + `ggml.h` + `jni.h` del JDK). Contratos actualizados: `spec.md` §4 y `agent_prompt.md` documentan ahora la firma `__J`.

**F8 — constante `ACTION_IME_ENTER` en vez de magic number**: `AutoTypeAccessibilityService.performImeEnter` usa `AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id` con el gate correcto a `Build.VERSION_CODES.S` (API 31; antes guardaba en `R`=30 con un número mágico). Nota empírica: Robolectric **remapea** los resource ids del framework (`ACTION_IME_ENTER` reporta un id distinto al del dispositivo), por lo que el test de regresión (`AccessibilityActionTest`, 2 tests) verifica el contrato independiente del entorno: el constante existe y es válido en API 31+ y su id difiere de `ACTION_CLICK`/`ACTION_FOCUS`. El valor real de framework queda documentado (AOSP `accessibility_action_ids.xml`: `0x00400000`) en el KDoc del test.

### Investigados y descartados (no son bugs)

- **Plantillas LLM multilínea**: el almacenamiento Base64 por líneas es seguro — el alfabeto Base64 no contiene `\n`, así que los saltos de línea del prompt quedan codificados dentro del dato. ✅
- **Drag del overlay con gravity `END`**: `params.x -= Δx` y `params.y += Δy` tienen el signo correcto para `TOP|END`. ✅
- **Bridge JNI**: `freeContext` espera `active_calls == 0` bajo `lifetime_mutex`; `fullTranscribe` lee `whisper` solo tras `retain_context` — sin use-after-free ni deadlock. ✅
- **Doble `close()` del VAD** (`failedVadDetector`/`vadDetector`): alias de la misma instancia, pero `close()` es idempotente. ✅
- **`AudioFeedbackManager.release()` nunca llamado**: singleton proceso-wide con recreación lazy; correcto que no se libere por componente. ✅

---

## 2. Modernización I/O 2026 ejecutada

### 2.1 Toolchain (build.gradle.kts + app/build.gradle.kts + gradle wrapper)

| Componente | Antes | Ahora |
|---|---|---|
| Compose BOM | 2024.12.01 | **2026.06.01** (última estable 2026) |
| material3 | 1.3.1 | **1.4.0** (*1.5.x solo existe como alpha* — verificado contra Maven) |
| compose ui | 1.7.6 | **1.11.4** |
| Kotlin | 2.0.21 | **2.2.21** (compatible con Gradle 8.11/AGP 8.10) |
| AGP | 8.7.3 | **8.10.1** (resuelve el crash del lint de Compose con Kotlin 2.2) |
| Gradle | 8.9 | **8.11.1** |
| activity-compose | 1.9.3 | 1.10.0 |
| onnxruntime-android | 1.18.0 | **1.24.3** (natives alineados a 16 KB — requisito Android 15+) |
| **Nuevas deps** | — | `material3-window-size-class` 1.4.0, `material3-adaptive-navigation-suite` 1.4.0, `material-icons-core` 1.7.8 (el BOM 2026 ya no lo gestiona) |

- `jvmTarget` migrado al DSL `compilerOptions` (Kotlin 2.2).
- Lint: deshabilitados solo avisos informativos (`UseKtx`, `AndroidGradlePluginVersion`) y el detector de Compose del AGP que crasheaba con Kotlin 2.2 (`FlowOperatorInvokedInComposition`); supresión local del falso positivo `WrongConstant` (`.and(FLAG)`).

### 2.2 Adaptive-first — MainActivity

- Migrada de `Scaffold` + `NavigationBar` fija a **`NavigationSuiteScaffold`** con `calculateWindowSizeClass()` (API 1.4.0 verificada contra los fuentes oficiales: `layoutType` + scope `item`, `@OptIn` de `ExperimentalMaterial3WindowSizeClassApi`).
- **Compact** → `NavigationBar` inferior; **Medium/Expanded** → `NavigationRail` lateral (adaptive-first de I/O 2026).
- El `content` ya no recibe `PaddingValues` (la API 1.4.0 consume los insets internamente).

### 2.3 APIs Expressive (equivalentes estables de M3 1.4.0)

> Verificado contra el AAR de material3 1.4.0: `LargeFlexibleTopAppBar` y `ButtonGroup` **no existen en la versión estable** (solo en 1.5.x alpha; de 1.4.0 solo hay la anotación `ExperimentalMaterial3ExpressiveApi`). Se adoptaron los equivalentes estables con el mismo espíritu:

| Intención | Adoptado | Fichero |
|---|---|---|
| Top app bar grande colapsable (tienda) | **`LargeTopAppBar`** + `TopAppBarDefaults.enterAlwaysScrollBehavior()` + `.nestedScroll(...)` en el `LazyColumn` | `ModelsActivity.kt` |
| Grupo de filtros de selección única (History) | **`SingleChoiceSegmentedButtonRow` + `SegmentedButton`** (estables, sin opt-in; `itemShape(index, count)` + `icon = {}`; conserva deselección al tocar el activo y scroll horizontal) | `HistoryScreen.kt` |

**Plan de migración verificado (revisado el 2 ago 2026 contra Maven + AAR de `material3-android:1.5.0-alpha25`)**:

- **Estado**: `material3` 1.5.0 **sigue sin versión estable** — última publicada `1.5.0-alpha25` (ni beta, ni rc, ni release). El BOM más reciente es `2026.06.01` (el que usamos) y los artefactos compañeros (`material3-adaptive-navigation-suite`, `material3-window-size-class`) también están en `1.5.0-alpha25`. Decisión de producto (registrada): **esperar a la estable**; subir la alpha implicaría override del BOM + deps transitivas alpha de Compose UI en producción.
- **Firmas públicas confirmadas en alpha25** (javap sobre el AAR):
  - `LargeFlexibleTopAppBar(title, modifier, navigationIcon, actions, expandedHeight, collapsedHeight, ...TopAppBarScrollBehavior)` (Composable, junto a `MediumFlexibleTopAppBar`); internos de layout: `SingleRowTopAppBar`/`TwoRowsTopAppBar`.
  - `ButtonGroup(menuState, modifier, ..., content: ButtonGroupScope.() -> Unit)` + `ButtonGroupDefaults`, `ButtonGroupItem`, `ButtonGroupMenuState`.
  - `SplitButton(primaryButton, secondaryButton, modifier, ...)` + `SplitButtonLayout`, `SplitButtonDefaults`, `SplitButtonShapes`.
- **Cuándo migrar**: al publicarse `material3` 1.5.0 estable → `ModelsActivity`: `LargeTopAppBar` → `LargeFlexibleTopAppBar` manteniendo `scrollBehavior`; `HistoryScreen`: `SingleChoiceSegmentedButtonRow` → `ButtonGroup` (o `SplitButton` si el diseño lo pide).
- **Re-verificación automatizada** (`scripts/check_material3_stable.py`): consulta `maven-metadata.xml` de material3 y **falla (exit 1) al detectar una versión estable ≥ 1.5.0** (bare semver sin alpha/beta/rc), con el mensaje apuntando a este plan. Se ejecuta en el workflow `material3-stable-check.yml` (semanal + manual) y como gate en `android.yml`. Incluye `--selftest` (6 casos offline) y `--metadata FILE` para pruebas locales; errores de red/parse → warning + exit 0 (no rompe CI). Verificado: live check hoy → exit 0 (última estable 1.4.0); fixture con `1.5.0` → exit 1.

### 2.4 IME a Material3 Compose

`HandyInputMethodService` migrado de Views `Theme.Material` 2014 (`LinearLayout`/`Button`/`Color.DKGRAY`) a **Compose Material3**, sin dependencia MDC:

- **`ComposeView` + `HandyTheme`** como input view — hereda dark mode y paleta MD3 de marca (`#6750A4`); el botón cambia a `errorContainer`/`onErrorContainer` mientras graba y muestra el icono `ic_mic.xml` tintable (sin añadir `icons-extended`).
- **ViewTree owners vía puente Java**: la ventana de un IME no está anclada a una Activity, y Compose 1.11 lanza `"Composed into the View which doesn't propagate ViewTreeLifecycleOwner!"` (verificado contra el bytecode de ui 1.11.4: lookup `ViewTreeLifecycleOwner.get` → throw). `onCreateInputView` provee owners ligeros scoped al servicio (`LifecycleRegistry` STARTED + `ViewModelStore` + `SavedStateRegistryController.create`). Hallazgo de toolchain: el compilador Kotlin **no resuelve** los símbolos android-variant de los artefactos KMP de androidx (`ViewTreeLifecycleOwner`, `ViewTreeViewModelStoreOwner`, `ViewTreeSavedStateRegistryOwner`) pese a estar en el classpath (clases públicas verificadas con javap en `lifecycle-runtime-android`/`lifecycle-viewmodel-android`/`savedstate-android`; las clases comunes `Lifecycle`/`SavedStateRegistryOwner` sí resuelven) — por eso el wiring vive en `ViewTreeOwnerBridge.java`, que sí los enlaza. `ComposeViewContext` (API 1.11 para este caso) tiene constructor privado en metadata → descartado.
- **Gesto tap/hold preservado al 100%**: el detector no consume eventos — el `Button` conserva ripple y acción de click de accesibilidad. Un `pointerInput` con `awaitEachGesture` + `awaitFirstDown(requireUnconsumed = false)` usa el miembro `AwaitPointerEventScope.withTimeoutOrNull(300ms)` para detectar el hold (timeout → push-to-talk) y `waitForUpOrCancellation()` para pararlo al soltar/arrastrar — misma semántica que el `OnTouchListener` previo, sin doble toggle. `rememberUpdatedState` evita capturar estados caducos.
- **Estado vía snapshot**: `statusText`/`recording` como `mutableStateOf` del servicio; la coroutine de transcripción actualiza el UI sin tocar Views.
- **Smoke test** (`HandyInputMethodServiceTest`, Robolectric): verifica que el input view es `ComposeView` y que los ViewTree owners están cableados vía el puente (regresión del crash de ventana sin Activity).

---

## 3. Contraste con Google I/O 2026 (Material) — estado actual

| Anuncio I/O 2026 | Estado | Evidencia |
|---|---|---|
| Edge-to-edge obligatorio | ✅ Conforme | `enableEdgeToEdge()` + `Scaffold(innerPadding)` en las 10 actividades |
| **M3 Expressive** (TopAppBar flexibles, `ButtonGroup`, `SplitButton`, `SearchBarState`, `FloatingToolbar`, `material3-ripple`) | ✅ **M3 1.4.0 estable** (BOM 2026.06.01, Compose UI 1.11.4, verificado en gradle cache). APIs 1.5.x (`LargeFlexibleTopAppBar`/`MediumFlexibleTopAppBar`/`ButtonGroup`/`SplitButton`) **existen en `1.5.0-alpha25` pero M3 1.5.0 sigue sin estable** (re-verificado 2 ago 2026: última publicada `1.5.0-alpha25`) → se mantienen los equivalentes estables (`LargeTopAppBar`, `SegmentedButton`) y el plan de migración verificado en §2.3 | Revisitar cuando 1.5.0 sea estable (firmas listas en §2.3) |
| **Adaptive-first** (`NavigationSuiteScaffold`, `WindowSizeClass`) | ✅ **Adoptado en MainActivity** (NavigationBar compact / NavigationRail medium+); resto de pantallas siguen en 1 panel (aceptable para listas/formularios) | `MainActivity.kt` |
| **Jerarquía de top bars** | ✅ **Decisión documentada** (anti-deriva): `LargeTopAppBar` solo en las 3 pantallas destino/hero (tienda de modelos, captura de voz, transcripción de archivo); barra estándar en el hub de navegación (`MainActivity`, acompañante de `NavigationSuiteScaffold`), el editor de diccionario (`CustomWordsActivity`), la pantalla corta de subtítulos (`LiveSubtitleActivity`) y las páginas de ajustes/herramientas (estas colapsan con `enterAlwaysScrollBehavior`); `MediumTopAppBar` intencionadamente sin uso (ninguna pantalla queda entre la barra pequeña y la grande). **Test**: `ThemePaletteTest.topBarVariantsFollowScreenHierarchy` | — |
| **Contraste WCAG AA (toda la UI)** | ✅ **Barrido sistemático de los pares renderizados** (ambos modos): texto/descripciones sobre `surface`, cards por defecto (`surfaceContainerLow`), cards "coming soon" (`surfaceContainerHigh`), metadatos `primary` sobre ambos contenedores, botones (`onPrimary`/`primary`), chips/segmentados seleccionados (`onSecondaryContainer`/`secondaryContainer`), hero de marca (`onPrimaryContainer`/`primaryContainer` — corregido: antes usaba `onSurface` por defecto del Card) y botón stop del IME (`onErrorContainer`/`errorContainer`). Todos ≥ 4.5:1 (mínimo 5.30:1). **Tests**: `errorStatusTextMeetsContrastOverScreenSurfaces`, `imeStatusTextPairingsMeetContrastOverPanel`, `screenContrastPairsMeetWcagAa` | — |
| **Compose-First; Views en maintenance** | ✅ **IME migrado a Material3 Compose** — `HandyImeInput` con `HandyTheme` (dark mode + paleta MD3), sin colores hardcodeados; botón con estado de grabación (errorContainer) y gesto tap/hold preservado | `HandyInputMethodService.kt` — Ver §2.4 |
| Dynamic Color | ✅ **Toggle expuesto en ajustes** (Feedback → "Dynamic color") + `SettingsManager.dynamicColorEnabled`; `HandyTheme.dynamicColor: Boolean? = null` lee el ajuste por defecto (marca fija `#6750A4` sigue siendo el default hasta opt-in). Estado elevado al scope de `setContent` para que el cambio aplique en vivo (bug de recomposición detectado en revisión y corregido) | — |
| `surfaceContainer` family (elevación tonal) | ✅ **Esquema MD3 completo y aplicado**: `Color.kt` + `Theme.kt` incluyen `surfaceContainerLowest/Low/High/Highest`, `surfaceDim/Bright`, `error/onError/errorContainer/onErrorContainer`, `inverseSurface/inverseOnSurface/inversePrimary`, `outlineVariant` (light+dark), verificados contra el baseline M3 seed `#6750A4`. **En uso**: IME usa `surfaceContainerLow` (panel flotante) + rol `error` para estados de error; cards "coming soon" de la tienda usan `surfaceContainerHigh`; `RecognizeActivity` y `TranscribeFileActivity` usan rol `error` para mic no disponible/transcripción fallida/fichero ilegible y ahora también **`LargeTopAppBar`** colapsable (igual que la tienda de modelos). TopAppBar colapsables (`enterAlwaysScrollBehavior` + `nestedScrollConnection`) en las 6 páginas scrolleables (Recognize, TranscribeFile, LlmSettings, PostProcessSettings, TranscriptionSettings, LiveLogViewer). **Tests**: paridad canónica (29 roles), orden tonal, contraste WCAG AA de error y uso en componentes | — |
| Paleta Views ↔ Compose | ✅ **Duplicación eliminada**: `styles.xml` (light/dark) referencia `@color/handy_*` en vez de hex; `colors.xml` gana los tokens dark. **Test de paridad** (`ThemePaletteTest`): `colors.xml` ↔ `Color.kt` (8 light + 3 dark) y "sin hex en el tema de ventana" | — |
| Material Symbols / iconografía | ✅ **Iconografía de marca completa y sin derivas**: launcher adaptive + monochrome (F4), **`ic_stat_handy`** (glifo mic blanco, alpha mask) en los small icons de ambos FGS (`FloatingButtonService`, `LiveSubtitleService`) y como icono del tile de QS (`HandyTileService`); sin `android.R.drawable.*` en `app/src/main` (grep limpio). **Test de paridad** (`AppIconTest.brandGlyphsShareTheSameMicrophonePath`): `ic_mic`, `ic_stat_handy` e `ic_launcher_foreground` comparten el mismo `pathData` (solo difieren fill y transform del adaptive), evitando derivas futuras. Queda `material-icons-core` + 4 drawables manuales | Opcional: Symbols/icons-extended |
| Tipografía / Google Fonts | ⚠️ `Type.kt` sobreescribe 3 estilos con `FontFamily.Default` | Opcional: tipografía de marca |
| Splash Screen | ✅ | `windowSplashScreenBackground` light/dark |
| Back predictivo | ⚠️ No usa `BackHandler`/`enableOnBackInvokedCallback` explícitos (targetSdk 35 lo habilita por defecto) | Revisar al subir targetSdk |

---

## 4. Accesibilidad (WCAG AA) — verificado numéricamente

Contraste calculado de los 12 pares clave de la paleta real (light + dark): **todos ≥ 4.5:1** para texto. Mínimo: `outline` 4.33:1 (umbral de bordes 3:1 ✅). Ejemplos: texto primario 6.12:1, botones 6.44:1, dark mode 10.9–14.3:1, waveform terciario 5.0–5.5:1. (Sin cambios tras la modernización.)

---

## 5. Fortalezas verificadas

- **Seguridad de modelos**: HTTPS obligatorio + anti-redirección insegura, SHA-256 publicado, digest atómico (`.part` + `ATOMIC_MOVE`), validación nativa Whisper antes de activar, fail-closed al reemplazar modelo activo.
- **Concurrencia**: `AudioBuffer` sincronizado, cancelación por generación en `TranscriptionEngine`, refcount JNI (`active_calls`) — sin carreras ni use-after-free.
- **Privacidad**: inserción accessibility mínima (solo nodo enfocado), LLM restringido a HTTPS/localhost, historial en SQLite local, sin telemetría.
- **i18n**: 7 idiomas (en, es, de, fr, ja, zh, pt) con paridad verificada, plurals correctos, apóstrofes tipográficos; `DuplicateStrings` acotado a traducciones legítimas.
- **Integración de sistema**: VAD Silero con auto-stop 1200 ms, tap/hold dual, feedback háptico/sonoro, inserción 3-tier (`ACTION_SET_TEXT` → clipboard+paste → toast), tile de QS, arranque en boot.
- **Tests**: 78 verdes — 42 originales (buffer, VAD, post-process, LLM, downloader, validator, engine, history, feedback, waveform) + 12 de los gates de permisos F1 (`PermissionCheckerTest` 4 + `F1PermissionGateTest` 8) + 6 del icono (`AppIconTest`, incl. paridad de glifo mic) + 1 smoke del IME (`HandyInputMethodServiceTest`) + 2 de la constante de accesibilidad (`AccessibilityActionTest`, F8) + 4 del icono de notificación/tile (`NotificationIconTest`) + 11 de paleta/dynamic color (`ThemePaletteTest`: paridad light/dark + sin-hex + roundtrip del setting + baseline MD3 canónico + orden tonal + contraste error/onError + uso en componentes + jerarquía de top bars + contraste error sobre surface/IME + pares de estado del IME + barrido sistemático de pares en pantalla).

---

## 6. Veredicto

El código está **bien hecho, funcional y alineado con las directrices de Google I/O 2026**: gates verdes (74/74 tests, lint 0 errores), toolchain 2026 (BOM 2026.06.01, material3 1.4.0, Compose UI 1.11.4, Kotlin 2.2.21, AGP 8.10.1), adaptive-first en MainActivity, APIs Expressive adoptadas en su versión estable, **IME migrado a Material3 Compose**, **iconografía de marca completa** (launcher adaptive + `ic_stat_handy` en FGS y tile de QS) y **los 8 defectos F1–F8 de la auditoría corregidos y validados** (crash de FGS, overlay que robaba toques, API key en claro, icono de app, job obsoleto en `VoiceRecognitionService`, LLM fuera del mutex, símbolo JNI muerto, magic number `ACTION_IME_ENTER`), con tests de regresión para F1, F4, el IME, la constante de accesibilidad y el icono de notificación.

Pendiente para la siguiente fase:

1. **M3 1.5.0 estable** cuando salga (re-verificado 2 ago 2026: aún `1.5.0-alpha25`): adoptar `LargeFlexibleTopAppBar`, `ButtonGroup`/`SplitButton`, `SearchBarState` — plan con firmas verificadas en §2.3. La detección está **automatizada** (`scripts/check_material3_stable.py` + workflow semanal `material3-stable-check.yml`), así que CI fallará en cuanto se publique la estable.
