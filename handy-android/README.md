# Handy para Android

**Reconocimiento de voz offline, en el dispositivo, para Android 8.0+ (API 26).**  
Sin conexión a internet, sin enviar datos a la nube. Privacidad total.

---

## Características

- **IME integrado** — Dictado en cualquier campo de texto activando el teclado Handy, con UI flotante tipo pill
- **Inserción inteligente** — 3 estrategias en cascada: IME `InputConnection` → Shizuku `KEYCODE_PASTE` → Clipboard
- **Offline total** — Todo el procesamiento (VAD + transcripción) ocurre en el dispositivo
- **65+ modelos** — Whisper, Parakeet, Canary, Moonshine, Nemotron, Qwen3-ASR, Cohere, Granite y más
- **Multi-idioma** — Soporte para 99 idiomas según el modelo
- **Consciente del dispositivo** — Clasificación automática del hardware (LOW/MID/HIGH/FLAGSHIP/TABLET) con recomendaciones por tier
- **Material Design 3** — UI adaptativa con NavigationRail en tablets, tema oscuro PC-aligned
- **Foreground Service** — Grabación fiable con notificación persistente y acciones (Stop, Switch Keyboard)
- **VAD Energético** — Detección de actividad de voz liviana con adaptación rápida al ruido ambiente
- **Normalización de audio** — Peak normalization para mejor precisión en la transcripción

---

## Requisitos

- Android 8.0+ (API 26)
- Conexión a internet solo para descargar modelos (luego funciona offline)
- ~500 MB de almacenamiento libre para modelos ligeros

---

## Quick Start

### Prerrequisitos

- [Rust](https://rustup.rs/) (latest stable)
- [Android NDK](https://developer.android.com/ndk/) r26+ (via Android Studio SDK Manager)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`
- Rust Android target: `rustup target add aarch64-linux-android`
- Java 17+ JDK
- Android SDK (compileSdk 35)
- Un dispositivo con depuración USB habilitada

### Compilar e instalar

```bash
cd handy-android
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version> ./gradlew assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Nota:** El APK de debug incluye la librería Rust compilada en modo debug (~131 MB). Para un APK release (~6 MB), véase [Building for Release](#building-for-release).

### Logs

```bash
adb logcat | grep -E '(handy-core|HandyApp|EngineVM|HandyRecording|TestCommandReceiver)'
```

---

## Arquitectura

```
handy-android/
├── handy-core/                    # Motor Rust (cdylib JNI)
│   ├── src/
│   │   ├── jni_bridge.rs          # 22 funciones JNI #[no_mangle]
│   │   ├── audio/                 # Captura AAudio + resampler (rubato) + VAD energético
│   │   ├── transcription/         # Batch inference via transcribe-cpp (GGML)
│   │   └── model/                 # Catálogo + descarga de modelos (GGUF)
│   └── Cargo.toml
├── app/                           # App Android (Kotlin + Jetpack Compose)
│   └── src/main/java/com/handy/app/
│       ├── ime/                   # Input Method Service + UI flotante tipo pill
│       ├── injection/             # Estrategias de inserción: IME → Shizuku → Clipboard
│       ├── viewmodel/             # EngineViewModel + SettingsViewModel + ModelsViewModel
│       ├── bridge/                # JNI bindings + callback interface
│       ├── capability/            # DeviceTier, CapabilitySnapshot, CompatibilityResolver
│       ├── service/               # RecordingService foreground
│       └── ui/                    # Compose screens (Settings, Models, History, Onboarding)
│           └── theme/             # MD3 Color, Type, Shape tokens
├── scripts/
│   ├── build-rust.sh              # Compilación Rust con soporte --vulkan
│   └── adb_test_flow.sh           # Flujo ADB automatizado (build → install → download → activate)
```

Ver [ARCHITECTURE.md](./ARCHITECTURE.md) para la especificación técnica completa.

---

## Estrategias de Inserción de Texto

| Estrategia | Prioridad | Descripción |
|---|---|---|
| **IME InputConnection** | 1.ª | Inserción directa vía `commitText()` — la más fiable |
| **Shizuku** | 2.ª | Inyección vía `KEYCODE_PASTE` con permisos UID 2000 (requiere Shizuku runtime) |
| **Clipboard** | 3.ª | Copia al portapapeles con label "Handy Dictation" (fallback automático) |

El `InjectorRouter` selecciona automáticamente la mejor estrategia disponible y cae en cascada si falla.

---

## Modelos Disponibles

65+ modelos en el catálogo completo. El sistema [Capability-Aware](./ARCHITECTURE.md#capability-aware-model-catalog-sprint-14) clasifica el dispositivo y recomienda modelos según la RAM disponible.

### Recomendaciones por Tier

| Tier | Modelo Principal | Alternativas |
|---|---|---|
| **LOW** (≤1.5 GB) | Whisper Base | Whisper Tiny, Moonshine Streaming Tiny, MedASR |
| **MID** (≤3.5 GB) | Nemotron 3.5 ASR Streaming | Canary 180M Flash, Parakeet TDT 0.6B, Whisper Medium, Whisper Small |
| **HIGH** (≤6.5 GB) | Whisper Large V3 Turbo | Qwen3-ASR 1.7B, Canary 1B V2, Whisper Large V3 |
| **FLAGSHIP** (≤12.5 GB) | Whisper Large V3 | Granite Speech 4.1 2B+, Canary Qwen 2.5B |
| **TABLET** (>12.5 GB) | Cohere Transcribe | Granite Speech 4.1 2B, Granite 4.0 1B Speech |

### Modelos Ligeros Populares

| Modelo | Tamaño | Idiomas | Ideal para |
|---|---|---|---|
| Canary 180M Flash (Q4_K_M) | 139 MB | EN/ES/FR/DE + traducción | MID+ |
| Parakeet TDT 0.6B V3 (Q4_K_M) | 485 MB | 25 idiomas | HIGH+ |
| Nemotron 3.5 Streaming (Q4_K_M) | 496 MB | 28 idiomas + auto-detect | HIGH+ |

---

## Feature Flags & Gating

El sistema incluye protecciones contra OOM y malas experiencias:

| Feature | Descripción |
|---|---|
| **DeviceTier** | Clasificación automática del hardware en 5 bandas según RAM |
| **HeavyGate** | Modelos XXL (Voxtral 24B/4B/3B) requieren consentimiento explícito con checkbox |
| **Experimental Gate** | Modelos inestables (Moonshine Base monolingües) ocultos por defecto |
| **Compatibility Badges** | Chips visuales: HEAVY_GATE, EXCEEDS_RAM, LARGE_HEAP, EXPERIMENTAL |
| **Mobile Recommendations** | 19 modelos curados organizados por tier con promoción en UI |

---

## ADB Test Automation

Para pruebas automatizadas vía ADB (debug builds solamente):

```bash
# Flujo completo: build → install → grant → launch → download → activate
./scripts/adb_test_flow.sh <device_serial> <model_id>

# Ejemplo:
./scripts/adb_test_flow.sh adb-00143154F001971-AbAnvz._adb-tls-connect._tcp canary-180m-flash-Q4_K_M
```

### Hooks Disponibles (Debug)

| Acción | Comando |
|---|---|
| Saltar onboarding | `am start ... --ez skip_onboarding true` |
| Descargar modelo | `am broadcast -a com.handy.app.action.DOWNLOAD_MODEL --es model_id <id>` |
| Activar modelo | `am broadcast -a com.handy.app.action.SET_ACTIVE_MODEL --es model_id <id>` |

---

## Building for Release

```bash
export HANDY_KEYSTORE_PATH=../handy-release.keystore
export HANDY_KEYSTORE_PASSWORD=<password>
export HANDY_KEY_ALIAS=handy
export HANDY_KEY_PASSWORD=<password>
export SENTRY_DSN=<your-sentry-dsn>

./gradlew assembleRelease bundleRelease
```

El release build compila Rust en modo `--release` (~6 MB `.so`) y habilita el backend Vulkan.  
Ver [BUILD.md](./BUILD.md) para instrucciones detalladas.

---

## Limitaciones Conocidas

- Sin transcripción en streaming nativa (solo batch con `session.run()`)
- Algunos modelos Whisper English-only muestran entradas duplicadas en el catálogo
- Whisper Tiny tiene baja precisión con frases largas con nombres propios
- `session.run()` es bloqueante; el `cancel_flag` descarta resultados post-hoc pero no interrumpe C++ mid-inferencia
- Moonshine Base no verificado en Android aún
- Voxtral Small 24B (17 GB) está listado pero es impráctico para la mayoría de dispositivos móviles
- El IME puede causar cambios de layout menores en algunas apps host

---

## Licencia

MIT License — ver [LICENSE](../LICENSE).

*Handy para Android es un fork de [Handy](https://github.com/cjpais/Handy) de cjpais, adaptado exclusivamente para dispositivos Android. El nombre, logo y marcas no son open-source. Los forks no oficiales deben usar su propia marca.*
