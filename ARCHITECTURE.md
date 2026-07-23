# ARCHITECTURE.md — Arquitectura Técnica: Fusión Handy-Android + android_transcribe_app STT Core

> **Estado:** 🟢 Aprobado  
> **Fecha:** 23 de Julio de 2026  
> **Ámbito:** `handy-android/` (Rust cdylib + Kotlin Jetpack Compose MD3)

---

## 1. Visión Arquitectónica Global

La arquitectura combina la capa de presentación **Material Design 3 (MD3)** de Handy-Android con el motor de transcripción e inferencia offline **`transcribe-rs` + ONNX Runtime** de `android_transcribe_app`.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        KOTLIN / COMPOSE UI LAYER                       │
│  ┌────────────────────┐ ┌────────────────────┐ ┌────────────────────┐  │
│  │  HandyVoiceBar     │ │ ModelCatalogScreen │ │ PostProcessScreen  │  │
│  │  (IME Pill MD3)    │ │ (On-Demand Catalog)│ │ (Multi-prompt/Dict)│  │
│  └─────────┬──────────┘ └─────────┬──────────┘ └─────────┬──────────┘  │
│            │                      │                      │             │
│  ┌─────────▼──────────────────────▼──────────────────────▼──────────┐  │
│  │                    VIEWMODELS & REPOSITORIES                     │  │
│  │  EngineViewModel · ModelsViewModel · RecordingRepository · ...   │  │
│  └────────────────────────────────┬─────────────────────────────────┘  │
└───────────────────────────────────┼────────────────────────────────────┘
                                    │ JNI Bridge (EngineBridge.kt)
┌───────────────────────────────────▼────────────────────────────────────┐
│                        RUST NATIVE CORE LAYER                          │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  handy-core (cdylib) / jni_bridge.rs                              │  │
│  ├──────────────────────────┬───────────────────────────────────────┤  │
│  │  voice_session.rs        │  transcribe-rs Engine                 │  │
│  │  · Lock-Free Ring Buffer │  · ONNX Runtime 1.25.0 (mmap zero-copy)│  │
│  │  · AtomicU32 VAD Level   │  · Execution Providers: NNAPI/XNNPACK │  │
│  │  · CPAL / AAudio Driver  │  · Models: Parakeet 0.6B / Canary 180M│  │
│  └──────────────────────────┴───────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Componentes Nativos en Rust (`handy-core`)

### 2.1 Módulo `transcribe-rs`
- **Ubicación:** `handy-android/handy-core/transcribe-rs`
- **Función:** Encapsula la inferencia ONNX Runtime para NVIDIA Parakeet TDT 0.6B y Canary 180M Flash.
- **Mapeo de Memoria (`mmap`):** Los archivos `.onnx` se cargan utilizando la llamada de sistema `mmap`, permitiendo compartir las páginas de memoria directamente con el kernel sin duplicar buffers en el heap de Rust ni en la JVM.
- **Aceleración por Hardware:** 
  - `NNAPI` (Android Neural Networks API) para NPU/DSP.
  - `XNNPACK` como aceleración de fallback de alta eficiencia para CPU ARM64.

### 2.2 Pipeline de Audio Lock-Free (`voice_session.rs`)
- **Gestión de Nivel VAD:** La energía del audio en tiempo real se calcula en el hilo de captura nativo y se escribe en un entero atómico `AtomicU32` (`f32` convertido a bits con `to_bits()`).
- **Lectura desde Kotlin:** Kotlin consulta el entero atómico periódicamente o vía callback sin generar asignaciones de memoria (`GC allocations`).
- **Canal de Muestras PCM:** Transmisión de muestras de audio de 16 kHz mono al motor `transcribe-rs` mediante un buffer circular `crossbeam-channel` no bloqueante.

---

## 3. Puente JNI y Capa Kotlin (`EngineBridge.kt`)

### 3.1 Signaturas JNI Clave
```rust
// handy-core/src/jni_bridge.rs

#[no_mangle]
pub extern "C" fn Java_com_handy_app_bridge_EngineBridge_nativeInitEngine(
    env: JNIEnv,
    _class: JClass,
    model_dir: JString,
    model_type: JString,
) -> jboolean;

#[no_mangle]
pub extern "C" fn Java_com_handy_app_bridge_EngineBridge_nativeStartRecording(
    env: JNIEnv,
    _class: JClass,
) -> jboolean;

#[no_mangle]
pub extern "C" fn Java_com_handy_app_bridge_EngineBridge_nativeStopAndTranscribe(
    env: JNIEnv,
    _class: JClass,
) -> jstring;

#[no_mangle]
pub extern "C" fn Java_com_handy_app_bridge_EngineBridge_nativeGetVadLevel(
    _env: JNIEnv,
    _class: JClass,
) -> jfloat;
```

---

## 4. Gestor de Descargas y Almacenamiento de Modelos

### 4.1 Directorio de Almacenamiento
Todos los modelos descargados bajo demanda por `ModelDownloadManager` residen en:
`/data/data/com.handy.app.debug/files/models/` (o variante release).

### 4.2 Estructura del Archivo de Modelo ONNX
```
files/models/parakeet-tdt-0.6b/
├── encoder.onnx       (Modelo Encoder INT8)
├── decoder.onnx       (Modelo Decoder INT8)
├── tokenizer.json     (Tokenizer HuggingFace)
└── vocab.txt          (Vocabulario de Tokens)
```

---

## 5. Sistema de Post-Procesamiento con IA y Corrección Fonética

### 5.1 `WordCorrector.kt`
- Implementa un pipeline de dos etapas para corregir palabras mal transcritas:
  1. **Algoritmo Soundex:** Compara la representación fonética de la palabra reconocida contra el diccionario de usuario.
  2. **Distancia de Levenshtein:** Selecciona el candidato con la menor distancia de edición.

### 5.2 Cliente HTTP de Post-Procesamiento
- Realiza llamadas asíncronas no bloqueantes (`OkHttp` / `Ktor`) a endpoints estilo OpenAI (`/v1/chat/completions`) insertando la plantilla activa elegida por el usuario.
