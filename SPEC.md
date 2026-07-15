# Handy Android — Estado Actual y Diagnóstico

**Última actualización:** 2026-07-15  
**Checkpoint:** 🟢 FUNCIONAL — IME burbuja flotante + fixes UI + cancel/retry

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

**Síntoma:** El IME ocupaba toda el área de teclado con una UI completa que no se parecía al overlay flotante de PC.

**Solución:** Reescribir completamente `HandyInputMethodService.kt` como una burbuja flotante compacta (56dp). Estados: Idle (pill pulsante con mic + "Dictate"), Recording (waveform bars de 9 barras + texto parcial + botón stop rojo), Confirm (texto + checkmark verde insert + retry gris), Error (error + retry rosa). Usa AccentPink #E85D75 del overlay de PC.

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
| ~~UI refresh en cancel download~~ | ~~Media~~ | ✅ RESUELTO — Ahora muestra "Download canceled" con retry. |
| Voces largas con nombres propios | Baja | Whisper Tiny produce ~85% precisión en frases largas con nombres compuestos. Usar Whisper Small o Parakeet v3 para mejor resultado. |
| IME hardcoded strings | Baja | La burbuja IME usa strings hardcoded en lugar de string resources. Mejora de mantenibilidad pendiente. |
