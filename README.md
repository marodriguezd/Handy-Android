# Handy Android 🎙️⚡

> **Versión 0.9.0 — Release Completa para Android**  
> *Fork oficial enfocado 100% en la adaptación nativa de Handy (Speech-to-Text) para Android.*

**Handy Android** es la versión nativa para Android de [Handy](https://github.com/cjpais/Handy), la aplicación de dictado por voz y reconocimiento del habla privada, ultrarrápida y 100% offline.

---

## 🌟 Características Principales

### 🧠 Motor Local de Inferencia (100% Privado & Offline)
- **Rust `handy-core` JNI Bridge**: Motor nativo compilado en Rust con **Whisper.cpp / GGML** y **Silero VAD**.
- **Procesamiento On-Device**: Tus grabaciones de voz nunca salen de tu dispositivo. Sin latencia de servidor, sin suscripciones, sin envío de datos a la nube.

### ⌨️ Integración Profunda en el Sistema Android
- **Teclado IME Nativo (`HandyInputMethodService`)**: Barra flotante animada estilo píldora (Material Design 3) para dictar directamente en cualquier aplicación de Android.
- **Servicio de Reconocimiento de Voz (`HandyVoiceRecognitionService`)**: Reemplaza el servicio de voz predeterminado del sistema (`android.speech.RecognitionService`) con integración por Intent `RECOGNIZE_SPEECH` y `RecognizeActivity`.
- **Estrategias de Inyección de Texto**: Inyección directa por Shizuku, Servicio de Accesibilidad, Pegado Directo o Portapapeles.

### 🔤 Corrector Fonético & Diccionario Personalizado
- **Diccionario de Usuario**: Agrega términos personalizados, jerga o nombres propios (`DictionaryScreen`).
- **Motor `WordCorrector`**: Corrección avanzada fonética combinando **Soundex estándar**, distancia **Levenshtein** y análisis de **N-gramas**, junto con eliminación configurable de muletillas ("eh", "um", "este").

### 🤖 Posprocesamiento por LLM
- **Prompts Personalizables (`PromptsRepository`)**: Transforma, resume, corrige gramática o traduce automáticamente tus transcripciones.
- **Conexión con APIs LLM (`PostProcessor`)**: Compatible con endpoints OpenAI u Ollama locales.

### 🎨 Diseño Adaptativo Material Design 3
- **Fidelidad Estética 1:1**: Conserva la paleta cromática icónica de Handy (`#2c2b29`, `#f28cbb`, `#da5893`).
- **Layout Adaptativo**: Barra de navegación inferior (`NavigationBar`) en teléfonos y riel lateral (`NavigationRail`) en tablets o dispositivos plegables.
- **Cambio de Tema e Idioma en Caliente**: Cambio dinámico de idioma y tema (Oscuro / Claro / Sistema) sin reiniciar la actividad ni interrumpir la grabación activa.

### 📼 Historial & Grabación Dual WAV
- **Repositorio de Grabaciones (`RecordingRepository`)**: Guardado automático de archivos WAV de audio con reproducción integrada, reintentos de transcripción y política inteligente de evicción por espacio.

---

## 🚀 Arquitectura del Proyecto

```
Handy-Android/
├── handy-core/           # Motor Rust en C/C++ (Whisper.cpp, VAD, JNI bindings)
└── handy-android/        # Proyecto Android (Kotlin, Jetpack Compose, Material 3)
    ├── app/src/main/java/com/handy/app/
    │   ├── audio/        # RecordingRepository & backend WAV
    │   ├── bridge/       # EngineBridge.kt (Binding JNI nativo con handy-core)
    │   ├── capability/   # CatalogSorter.kt (Selección inteligente de modelos)
    │   ├── corrector/    # WordCorrector.kt (Soundex + Levenshtein + N-gramas)
    │   ├── postprocessing/# PostProcessor.kt & PromptsRepository.kt (LLM API)
    │   ├── service/      # IME keyboard service & Speech Recognition service
    │   └── ui/           # Pantallas Compose (Catalog, Dictionary, Settings, History, About)
    └── app/src/test/     # Suite completa de 182 tests unitarios JVM (100% PASS)
```

---

## 🛠️ Compilación e Instalación

### Requisitos
- Android Studio Ladybug / ME2024.2+
- JDK 17 / Android NDK 27+
- Dispositivo Android (API 26+) o Emulador

### Comandos de Compilación
```bash
# Navegar al directorio de Android
cd handy-android

# Compilar Kotlin y recursos
./gradlew :app:compileDebugKotlin

# Ejecutar la suite completa de tests unitarios (182 tests)
./gradlew :app:testDebugUnitTest

# Compilar APK de Depuración
./gradlew :app:assembleDebug

# Instalar en el dispositivo conectado vía ADB
./gradlew :app:installDebug
```

---

## 📜 Licencia

Basado en el trabajo original de [CJPais/Handy](https://github.com/cjpais/Handy). Licenciado bajo la Licencia MIT.
