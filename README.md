# Handy para Android

Reconocimiento de voz offline, en el dispositivo, para Android 8.0+ (API 26). Sin conexión a internet, sin enviar datos a la nube.

## Características

- **IME integrado** — Dictado en cualquier campo de texto activando el teclado Handy
- **Inserción directa** — La transcripción se pega automáticamente vía `InputConnection.commitText()`
- **Offline total** — Todo el procesamiento ocurre en el dispositivo
- **65+ modelos** — Whisper, Parakeet, Canary, Moonshine, Qwen3-ASR, y más
- **Multi-idioma** — Soporte para 99 idiomas (según el modelo)
- **Foreground Service** — Grabación fiable con notificación persistente
- **Detección de Voz (VAD)** — Energy-based VAD liviano
- **Normalización de audio** — Peak normalization para mejor precisión

## Capturas

<table>
  <tr>
    <td><strong>IME — Estado IDLE</strong></td>
    <td><strong>IME — Grabando</strong></td>
    <td><strong>Modelos</strong></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/ime_idle.png" alt="IME Idle" width="200"/></td>
    <td><img src="docs/screenshots/ime_recording.png" alt="IME Recording" width="200"/></td>
    <td><img src="docs/screenshots/models.png" alt="Models" width="200"/></td>
  </tr>
</table>

## Requisitos

- Android 8.0+ (API 26)
- Conexión a internet solo para descargar modelos (luego funciona offline)

## Build & Install

### Prerrequisitos

- [Rust](https://rustup.rs/) (latest stable)
- [Android NDK](https://developer.android.com/ndk/) r26+ (via Android Studio SDK Manager)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`
- Rust Android target: `rustup target add aarch64-linux-android`
- Java 17+ JDK
- Android SDK (compileSdk 35)

### Compilar e instalar

```bash
cd handy-android
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version> ./gradlew assembleDebug

# Instalar en dispositivo
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Nota:** El APK de debug incluye la librería Rust compilada en modo debug (~131 MB). Para un APK más pequeño, véanse las instrucciones de release en [handy-android/README.md](handy-android/README.md).

### Logs

```bash
adb logcat | grep -E '(handy-core|HandyApp|EngineVM|HandyRecording)'
```

## Arquitectura

```

handy-android/
├── handy-core/          # Motor Rust (cdylib JNI)
│   ├── src/
│   │   ├── jni_bridge.rs    # 21 funciones JNI #[no_mangle]
│   │   ├── audio/           # Captura AAudio + resampler + VAD
│   │   ├── transcription/   # batch inference via transcribe-cpp
│   │   └── model/           # Catálogo + descarga de modelos
│   └── Cargo.toml
└── app/                 # App Android (Kotlin + Jetpack Compose)
    └── src/main/java/com/handy/app/
        ├── ime/              # Input Method Service + UI flotante
        ├── injection/        # Estrategias: IME, Shizuku, Clipboard
        ├── viewmodel/        # EngineViewModel + SettingsViewModel
        ├── bridge/           # JNI bindings + callback interface
        └── service/          # RecordingService foreground
```

Ver [ARCHITECTURE.md](ARCHITECTURE.md) para la especificación técnica completa.

## Estrategias de Inserción

| Estrategia | Prioridad | Descripción |
|---|---|---|
| **IME InputConnection** | 1.ª | Inserción directa via `commitText()` — la más fiable |
| **Shizuku** | 2.ª | Inyección via `KEYCODE_PASTE` con permisos UID 2000 |
| **Clipboard** | 3.ª | Copia al portapapeles (fallback) |

## Modelos Disponibles

| Prioridad | Modelo | Tamaño | Idiomas |
|---|---|---|---|
| 🥇 | **Parakeet TDT 0.6B v3** (Q4_K_M) | 485 MB | 25 idiomas |
| 🥈 | **Canary 180M Flash** (Q4_K_M) | 139 MB | 4 idiomas + traducción |
| 🥉 | **Nemotron 3.5 Streaming** (Q4_K_M) | 496 MB | 28 idiomas, auto-detect |
| | Whisper familia | 46 MB–1.2 GB | 99 idiomas |
| | Canary 1B/1B Flash/Qwen 2.5B | 139 MB–1.7 GB | 4–25 idiomas |
| | Moonshine familia | 35–296 MB | Ultra-livianos |
| | +30 modelos más | Varios | Varios |

## Limitaciones Conocidas

- Sin transcripción en streaming (solo batch)
- Sin i18n (strings hardcoded en español/inglés)
- Whisper Tiny tiene baja precisión con frases largas
- El IME puede causar cambios de layout inesperados en algunas apps
- Modelos muy grandes (Voxtral 24B, 17 GB) son imprácticos en móvil

## Licencia

MIT License — ver [LICENSE](LICENSE).

*Handy para Android es un fork de [Handy](https://github.com/cjpais/Handy) de cjpais. El nombre, logo y marcas no son open-source. Los forks no oficiales deben usar su propia marca.*
