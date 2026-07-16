# Handy Android — Estado Actual y Diagnóstico

**Última actualización:** 2026-07-16  
**Checkpoint:** 🟢 FUNCIONAL — Onboarding download flow fixed + IME init ordering fix + UI exclusivity + ProGuard rules

---

## Estado Actual 🟢

**La aplicación funciona correctamente.** El dictado captura audio real del micrófono, lo transcribe mediante Whisper (GGUF vía transcribe-cpp) y muestra el resultado en la UI. Las pruebas en dispositivo confirman transcripción exitosa.

### Resultados de Transcripción (Whisper Tiny, test en vivo)

| Frase | Esperado | Obtenido | Precisión |
|-------|----------|----------|-----------|
| Corta | "Hola mundo, esto es una prueba" | "Hola, mundo. Esta es una prueba." | ✅ 95% |
| Media | "El reconocimiento de voz funciona perfectamente en el dispositivo" | **¡100% exacto!** | 🏆 100% |
| Larga | "Hoy es un gran día porque la aplicación Handy para Android..." | ~85% (errores menores en nombres propios) | ⚠️ 85% |

---

## Historial de Problemas Resueltos

### 🐛 Bug #1 (CRÍTICO) — Audio mudo: DIRECTION_INPUT = 0

**Síntoma:** El micrófono no capturaba audio real. Todos los logs mostraban `peak=0.0`, `rms=0.0`, transcripción vacía (`0 chars: ''`).

**Causa raíz:** El crate `aaudio-sys v0.1.0` tenía un bug: `DIRECTION_INPUT = 0` (idéntico a `DIRECTION_OUTPUT = 0`). Debería ser `1` según la especificación AAudio NDK. La app siempre abría streams de SALIDA en lugar de ENTRADA.

**Solución:** Reemplazar `aaudio_sys::DIRECTION_INPUT` (valor 0, incorrecto) por constante local `AAUDIO_DIRECTION_INPUT = 1` en `audio/capture.rs`. Además:
- Se añadió `AAudioStreamBuilder_setInputPreset(INPUT_PRESET_VOICE_RECOGNITION)`
- Se añadió `AAudioStreamBuilder_setContentType(CONTENT_TYPE_SPEECH)`
- Se añadió `AAudioStreamBuilder_setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)`

### 🐛 Bug #2 — Muestras de audio perdidas en remuestreo

**Síntoma:** La diferencia entre samples de entrada y salida del resampler no era exacta (relación 3:1 imperfecta para 48kHz→16kHz).

**Solución:** Acumular `input_pending` y `output_pending` en `FrameResampler` para no descartar samples sobrantes entre llamadas a `push()`.

### 🐛 Bug #3 — Audio silencioso para Whisper

**Síntoma:** Incluso con audio real, Whisper Tiny producía "you" de 3 caracteres para 3.6s de habla. El nivel RMS era ~0.01 (demasiado bajo).

**Solución:**
- `normalize_peak()` escala el audio pico a 0.95 antes de `session.run()`
- Pre-amp gain subido de 3.0x a 5.0x en el pipeline
- RMS logging añadido para diagnóstico: `rms=0.XXX` antes de cada inferencia

### 🐛 Bug #4 — Sin configuración de idioma

**Solución:** Forzar `language: Some("es")` en RunOptions para mejorar precisión en español.

### 🐛 Bug #5 — Cancel download / Skip no notificaban a la UI

**Síntoma:** Presionar "Cancel download" o "Skip for Now" no actualizaba la UI en tiempo real. Solo al reiniciar la app se veía el cambio.

**Solución:**
- El tokio task de descarga ahora llama `complete_cb(false, "Download cancelled")` cuando detecta el flag de cancelación.
- `OnboardingViewModel.skipDownload()` ahora llama `EngineBridge.nativeCancelDownload()`.

### 🐛 Bug #8 — Modelo por defecto incorrecto en onboarding

**Síntoma:** Al abrir la app por primera vez, el onboarding ofrecía descargar Whisper Small en lugar de Parakeet TDT 0.6B v3.

**Solución:** Actualizar `onboarding_model_body` en strings.xml para referenciar Parakeet TDT 0.6B v3 (485 MB).

### 🐛 Bug #9 — Cancel mostraba "Model Ready" en vez de feedback de cancelación

**Síntoma:** Al cancelar la descarga en el onboarding, la UI mostraba "Model Ready" con icono verde, confundiendo al usuario.

**Solución:** Añadir estado `isDownloadCanceled` en `OnboardingViewModel`. Ahora muestra "Download canceled" con botón de reintento. El retry resetea correctamente el flag `downloadStarted` para permitir una nueva descarga.

### 🐛 Bug #10 — Retry tras cancelación no funcionaba

**Síntoma:** Tras cancelar una descarga, pulsar "Download Recommended Model" no iniciaba una nueva descarga porque `initModelDownload()` retornaba temprano (ModelsViewModel ya existía) y `downloadStarted` seguía en `true`.

**Solución:** `retryDownload()` resetea `downloadStarted`, `retryingDownload`, `activated`. `initModelDownload()` ahora siempre llama `loadModels()` incluso si el ModelsViewModel ya existe. La condición de descarga acepta entradas completadas (con error) para permitir retry.

### 🐛 Bug #11 — ModelCard UI desalineada

**Síntoma:** En la pantalla de modelos, los chips de idioma, tamaños y botones de descarga estaban desalineados y el texto de idiomas overflowing.

**Solución:** Restructurar `ModelCard` de layout Row-based a Column-based con 3 filas: (1) icono + título + badge activo, (2) chip idioma con `weight(1f)` + `maxLines=1` + info tamaño/quant, (3) botones de acción alineados a la derecha.

### 🐛 Bug #12 — IME no funcionaba como micrófono

**Síntoma:** El IME ocupaba toda el área de teclado con una UI completa que no se parecía al overlay flotante de PC. La UX era incompleta: sin timer, sin waveform animada, paso de confirmación incómodo, sin theme-aware.

**Solución:** Reescribir completamente `HandyInputMethodService.kt` from scratch como un panel de voz completo que coincide con el overlay de PC:

- **Estado Idle:** Pill con dot pulsante + emoji mic + "Dictate" + botón ⌨ para cambiar teclado
- **Estado Recording:** 9 barras de waveform con animación de fase offset + timer MM:SS + botón stop rojo (32dp)
- **Estado Transcribing:** `CircularProgressIndicator` de Material3 + "Transcribing…" + botón cancel rojo
- **Estado Error:** Icono ⚠ + mensaje de error rojo + botón retry rosa
- **Auto-commit:** La transcripción se inserta automáticamente via `InputConnection.commitText()` sin paso de confirmación (como HandyPC)
- **Theme-aware:** Colores via `MaterialTheme.colorScheme` (soporte light/dark)
- **Animaciones:** Pop-in (460ms cubic-bezier), dot pulsante (1.9s), waveform bars con fase offset, timer en tiempo real
- **Keyboard switcher:** `showInputMethodPicker()` con fallback a `ACTION_INPUT_METHOD_SETTINGS`
- **Model check:** `startRecording()` verifica `nativeIsModelLoaded()` antes de iniciar
- **Injection failure feedback:** `confirmInsert()` error muestra `STATE_ERROR` en vez de reset silencioso
- **Auto-commit guard:** Flag `autoCommitted` previene loops infinitos de retry

### 🐛 Bug #13 (CRÍTICO) — IME: ViewTreeLifecycleOwner not found

**Síntoma:** Al seleccionar Handy como teclado IME y tocar cualquier campo de texto, la app crashea con `IllegalStateException: ViewTreeLifecycleOwner not found from android.widget.LinearLayout{...}`. El dictado también deja de funcionar porque el IME no puede inicializarse.

**Causa raíz:** `InputMethodService` no provee un `LifecycleOwner` en el árbol de vistas, pero `ComposeView.onAttachedToWindow()` lo requiere para crear el `WindowRecomposer`.

**Solución (v1 — Sprint 8):** Crear `ImeContainer` (FrameLayout + LifecycleOwner) con reflexión sobre `androidx.lifecycle.R$id.view_tree_lifecycle_owner`. Funcionaba en algunos dispositivos pero fallaba con `ClassNotFoundException` en otros.

**Solución (v2 — Sprint 9, actual):** Reemplazar la reflexión sobre `R$id` (fragile, interna) por reflexión sobre la clase pública estable `androidx.lifecycle.ViewTreeLifecycleOwner`:
```kotlin
val clazz = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
val setMethod = clazz.getMethod("set", View::class.java, LifecycleOwner::class.java)
setMethod.invoke(null, this, this)
```
Esta clase es una API pública estable de AndroidX lifecycle-runtime, a diferencia de `R$id` que es un recurso interno. Se usa `getMethod` (no `getDeclaredMethod`) porque es un método público estático.

**Cambios de lifecycle (permanecen igual):**
- `onCreate()` → `ON_CREATE`
- `onStartInput()` → `ON_RESUME`
- `onFinishInput()` → `ON_PAUSE`
- `onDestroy()` → `ON_DESTROY`

### 🐛 Bug #14 — Modelo no se activa automáticamente tras descarga

**Síntoma:** Tras completar la descarga del modelo en el onboarding o en la pantalla de modelos, el modelo descargado no se establece como activo. El usuario tiene que ir manualmente a la pantalla de modelos y pulsar "Use".

**Causa raíz:** `OnboardingViewModel` intentaba llamar a `setActiveModel()` tras detectar la descarga completada, pero el timing de la corrutina y los SharedFlow causaba que la activación no se ejecutara consistentemente.

**Solución:** Añadir `EngineBridge.nativeSetActiveModel(modelId)` directamente en `EngineViewModel.onDownloadComplete()` antes de `refreshModels()`. Así el modelo se activa inmediatamente al completarse cualquier descarga exitosa, independientemente de qué ViewModel esté manejando el flujo.

### 🐛 Bug #15 — Idiomas de modelo en una sola línea fea

**Síntoma:** En las tarjetas de modelo (`ModelCard`), el campo `language` contenía todos los idiomas separados por coma en un solo chip (ej: "English, Spanish, French, German..."), lo que causaba overflow visual o truncamiento feo.

**Solución:** Cambiar de un solo `Row` con un `Surface` chip a un `FlowRow` con chips individuales por idioma:
- `model.language.split(",").map { it.trim() }.filter { it.isNotEmpty() }`
- Cada idioma es un `Surface` independiente con `RoundedCornerShape(4.dp)`
- Usar `@OptIn(ExperimentalLayoutApi::class)` para `FlowRow`
- El tamaño/quant del modelo va inline después de los chips

### 🐛 Bug #16 (CRÍTICO) — Onboarding: collector no detectaba completación de download

**Síntoma:** El download mostraba 1% y se congelaba, o mostraba "Download canceled" sin razón aparente.

**Causa raíz (múltiple):**
1. **ModelsViewModel removía entries del map** al completarse (`state.downloads - event.modelId`), antes de que OnboardingViewModel las consumiera
2. **OnboardingViewModel buscaba por `activeDownloadId`** que se ponía `null` en completación → nunca veía eventos
3. **`downloadStarted` se reseteaba** en cada `initModelDownload()`, causando re-descargas automáticas tras fallos
4. **No había path automático a `isDownloadCanceled`** cuando el download fallaba en Rust

**Solución (Round 2):**
- Revertir `ModelsViewModel` a mantener entries en el map (sin eliminar)
- OnboardingViewModel ahora escanea `modelState.downloads.entries` directamente
- El collector maneja completaciones con error: setea `downloadError`, `isDownloading = false`
- `downloadStarted` ya no se resetea al inicio de `initModelDownload()`

### 🐛 Bug #17 (CRÍTICO) — UI de onboarding con estados no exclusivos

**Síntoma:** Dos botones "Download Recommended Model" visibles simultáneamente.

**Causa raíz:** 5 bloques `if` independientes que no eran mutuamente excluyentes. Cuando `isDownloadCanceled = true` y `isDownloading = false`, coincidían las condiciones de idle y cancelado.

**Solución:** Reemplazar `if` independientes por `when` con prioridad: error > downloading > cancelado > ready > idle.

### 🐛 Bug #18 (CRÍTICO) — IME: ViewTreeLifecycleOwner seteado demasiado tarde

**Síntoma:** IME crashea al seleccionar Handy como teclado en algunos dispositivos. `ComposeView` no encuentra LifecycleOwner.

**Causa raíz:** `onAttachedToWindow()` en Android se llama depth-first (hijos antes que padres). `ImeContainer.onAttachedToWindow()` setea el LifecycleOwner DESPUÉS de que `ComposeView.onAttachedToWindow()` ya intentó encontrarlo y falló.

**Solución:** Mover la reflexión del LifecycleOwner al `init{}` del `ImeContainer`, ANTES de agregar el `ComposeView` como hijo. Eliminar `onAttachedToWindow()`.

### 🐛 Bug #19 (MEDIUM) — ProGuard ofusca ViewTreeLifecycleOwner

**Síntoma:** En builds release, la reflexión sobre `ViewTreeLifecycleOwner.set()` falla silenciosamente porque ProGuard ofusca la clase o el método.

**Solución:** Agregar `-keep class androidx.lifecycle.ViewTreeLifecycleOwner { *; }` en `proguard-rules.pro`.

### 🐛 Bug #20 (HIGH) — downloadStarted desincronizado en retry

**Síntoma:** Tras cancelar un download y hacer retry, a veces se descargaba el modelo dos veces o no se descargaba.

**Causa raíz:** `initModelDownload()` reseteaba `downloadStarted = false` antes de que el collector pudiera verificar si ya había un download en curso.

**Solución:** Eliminar `downloadStarted = false` de `initModelDownload()`. Usar flag `initialized` separado.

### 🐛 Bug #21 (HIGH) — Sin feedback automático en error de download

**Síntoma:** Si el download fallaba en Rust (error de red, stream error), la UI se quedaba congelada en "Downloading 1%" sin error visible.

**Causa raíz:** No había código que detectara completaciones con error y actualizara `downloadError` o `isDownloadCanceled`.

**Solución:** El collector ahora detecta eventos completados con `event.error != null` y setea `downloadError`, `isDownloading = false` automáticamente.

### 🐛 Bug #6 — Backend lento (CPU forzado)

**Solución:** Cambiar `Backend::Cpu` → `Backend::Auto` para aprovechar aceleración hardware si disponible.

### 🐛 Bug #7 — Linker errors NDK

**Solución:** Inyectar `CMAKE_ARGS` y dummy `libpthread.a` para enlazar correctamente transcribe-cpp con el NDK.

---

## Modelos Soportados

Ahora hay **65 modelos** en el catálogo, exactamente los mismos que Handy PC. Ver `AGENTS.md` para la lista completa. Los 3 recomendados para móvil:

1. 🥇 **Parakeet TDT 0.6B v3** (Q4_K_M, 485 MB) — 25 idiomas, rápido
2. 🥈 **Canary 180M Flash** (Q4_K_M, 139 MB) — 4 idiomas + traducción
3. 🥉 **Nemotron 3.5 Streaming** (Q4_K_M, 496 MB) — 28 idiomas, streaming

No hay límite OOM — el usuario puede activar cualquier modelo.

---

## Pendientes / Issues Abiertos

| Issue | Prioridad | Descripción |
|-------|-----------|-------------|
| Whisper Large en móvil | Baja | Modelos >1.5 GB pueden causar OOM en dispositivos con poca RAM. El usuario es responsable de elegir. |
| ~~UI refresh en cancel download~~ | ~~Media~~ | ✅ RESUELTO (Round 2) — Collector escanea `downloads.entries` directamente. |
| ~~Download se congela al 1%~~ | ~~CRÍTICA~~ | ✅ RESUELTO (Round 2) — Error path automático + fix de `ModelsViewModel.revert`. |
| ~~Dos botones "Download" simultáneos~~ | ~~CRÍTICA~~ | ✅ RESUELTO (Round 2) — `when` en vez de `if` independientes. |
| ~~IME crash (LifecycleOwner timing)~~ | ~~CRÍTICA~~ | ✅ RESUELTO (Round 2) — Reflection movida a `init{}` antes de `addView()`. |
| Voces largas con nombres propios | Baja | Whisper Tiny produce ~85% precisión en frases largas con nombres compuestos. Usar Whisper Small o Parakeet v3 para mejor resultado. |
| IME hardcoded strings | Baja | El panel IME usa strings hardcoded ("Dictate", "Transcribing…", "Error") en lugar de string resources. Pendiente i18n. |
| IME onComputeInsets | Media | `onComputeInsets` fue eliminado. El panel IME puede causar layout shifts inesperados en apps host. Necesita restaurar control de insets. |
| IME streaming text | Media | Sin preview de texto en vivo durante grabación (solo batch transcription). Necesita `session.stream()` en Rust para live text como el overlay de PC. |
| Gradle buildRust overwrites .so | Baja | `assembleDebug` reconstruye Rust en modo debug (131MB) sin `RUSTFLAGS`. El release build manual de 6MB se pierde. Necesita arreglar el task de Gradle. |
