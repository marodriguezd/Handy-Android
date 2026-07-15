# Handy Android — Estado Actual y Diagnóstico

**Última actualización:** 2026-07-13  
**Sprint activo:** Diagnóstico de grabación

---

## Problema Reportado

En la zona de dictado, al presionar grabar, no ocurre nada. El retry no resuelve.

---

## Diagnóstico

### Síntomas (logcat)

```
EngineVM: startRecording: currentState=0
EngineVM: nativeLoadModel starting...
handy-core: nativeLoadModel: active_model_id=Some("whisper-base-q5_1")
handy-core: loading model from: ".../whisper-base-q5_1.gguf"
handy-core: whisper: gpu_device must be >= 0 (got -1)
EngineVM: nativeLoadModel done, checking if model loaded...
EngineVM: nativeLoadModel reported model NOT loaded
EngineVM: nativeStartRecording starting...
handy-core: nativeStartRecording: model_loaded=false, is_recording=false
```

**Hechos:**
- `nativeLoadModel` corre pero el modelo nunca se marca como cargado (`model_loaded=false`).
- `Model::load_with()` retorna en ~30ms, pero no se ve "model loaded successfully" ni "Failed to load model" en logs.
- Único log de whisper.cpp: `gpu_device must be >= 0 (got -1)` (warning benigno).
- `ModelInfo` de Rust reporta `downloaded:true, active:true` — el modelo existe en disco y está seleccionado.
- El archivo `.gguf` tiene 59MB y existe (`/data/user/0/com.handy.app.debug/files/models/whisper-base-q5_1.gguf`).

### Hipótesis Principal

**Incompatibilidad de formato GGML vs GGUF.**

Los archivos servidos por `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin` están en formato **GGML antiguo** (magic bytes `lmgg` = `0x67676d6c`). `transcribe-cpp` / `whisper.cpp` en versión actual espera **GGUF** (magic `GGUF`).

La carga falla dentro de C++ (`whisper_init_from_file`) sin lanzar excepción visible al lado Rust, probablemente porque el error es un return code que `transcribe-cpp` no propaga correctamente, o el error muere en el logging callback sin que se vea en logcat.

### Hipótesis Alternativa

`Model::load_with()` podría devolver `Ok` con un modelo inválido (puntero nulo interno), haciendo que `self.is_loaded.store(true, ...)` se ejecute pero la transcripción falle. Sin embargo, los logs muestran que `is_loaded` queda en `false`, lo que apunta a que `load_model()` retorna `Err` o nunca completa.

---

## Cambios Aplicados

| Archivo | Cambio | Propósito |
|---------|--------|-----------|
| `OnboardingViewModel.kt` | Llama `setActiveModel()` automáticamente al completar descarga. Usa flag `activated` anti-repetición. | Antes el modelo se descargaba pero nunca se activaba → `nativeLoadModel` fallaba con "No active model selected". |
| `EngineViewModel.kt` | Guard en `startRecording()`: si ya está en `STATE_LISTENING` o `STATE_TRANSCRIBING`, ignora. Agrega logging con TAG `EngineVM`. Diagnóstico post-`nativeLoadModel` (verifica `nativeIsModelLoaded`, `nativeGetAvailableModels`). | Evita doble-grabación. Permite ver en logs si el modelo activo existe. |
| `jni_bridge.rs` | Logs extra: `active_model_id=?` en `nativeLoadModel`, `model_loaded, is_recording` en `nativeStartRecording`. | Ver exactamente qué encuentra Rust al intentar cargar. |
| `transcription/engine.rs` | `use log::warn`, `fs::metadata` check previo a carga, error exacto de `Model::load_with` logueado con `warn!`. | Capturar el mensaje de error real de `transcribe-cpp`. |
| `model/manager.rs` | URLs cambiadas de `q5_0` → `q5_1` en sesión anterior. | Los archivos `q5_0` fueron eliminados de HuggingFace. |

---

## Próximo Paso Recomendado

1. **Reconstruir con los nuevos logs** (`./gradlew assembleDebug`), instalar, probar.
2. **Verificar en logcat** si aparece `Failed to load model: ...` o `model loaded successfully`.
3. **Si el error es GGML vs GGUF:**
   - Cambiar las URLs en `handy-core/src/model/manager.rs` para apuntar a archivos `.gguf` válidos (ej: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-gguf.bin` o similar).
   - Alternativa: verificar si `transcribe-cpp` soporta ambos formatos y necesita un flag de compatibilidad.
4. **Si el error es otro** (corrupción de archivo, OOM, etc.), ajustar según el mensaje capturado.


## Actualización del Checkpoint (15 de Julio de 2026)
- **Problema Actual:** La aplicación compila e instala vía ADB, pero el dictado en Android falla en la transcripción. 
- **Cambios Recientes:** 
  - Se ha eliminado el AGC (normalize_audio) que distorsionaba el audio subiendo el volumen del ruido.
  - Se modificó a `Backend::Auto` para mejorar la inferencia.
  - Se resolvió un problema de CMake inyectando `CMAKE_ARGS` para enlazar correctamente el NDK.
- **Próximos Pasos:** Analizar los logs de ADB y revisar el proceso de captura de audio y la configuración de Whisper en Android.
