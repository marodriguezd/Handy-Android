# Plan de implementación — Handy Android

Este documento es el plan vivo del módulo Android. El trabajo activo debe realizarse directamente sobre el código Android presente en este repositorio.

## Fase 0 — Contexto y límites

- [x] Mantener la aplicación Tauri de escritorio separada del módulo Gradle Android.
- [x] Adoptar `com.handy.android` como namespace y application ID.
- [x] Mantener Whisper/GGML local como motor principal del port.
- [x] Usar el dispositivo Android 16 ARM64 conectado por ADB para smoke tests.

## Fase 1 — Estructura Android importada

- [x] Incorporar proyecto Gradle raíz y wrapper.
- [x] Incorporar módulo `app` y recursos Android.
- [x] Incorporar fuentes Kotlin, JNI/C++, CMake y whisper.cpp/GGML.
- [x] Separar artefactos generados (`.gradle`, `app/build`, `app/.cxx`) mediante `.gitignore`.
- [x] Preparar la incorporación Git de todos los fuentes Android y documentación, excluyendo `.gradle/`, `app/build/`, `app/.cxx/` y otros artefactos generados.

## Fase 2 — JNI, Whisper y audio

- [x] Corregir firmas JNI al paquete `com.handy.android`.
- [x] Cargar y liberar `whisper_context` con ciclo de vida explícito.
- [x] Proteger inferencia y liberación mediante mutex por contexto.
- [x] Capturar PCM 16 kHz mono 16-bit y normalizar a float.
- [x] Decodificar archivos mediante MediaExtractor/MediaCodec.
- [x] Resamplear audio compartido a 16 kHz.
- [x] Verificar carga e inferencia real en dispositivo sin crash native.
- [x] Diseñar cache/manager de modelo para evitar recargarlo en cada operación.
- [x] Diseñar cancelación o cola para inferencias JNI bloqueantes.
- [x] Implementar cache singleton de modelo y cancelación JNI nativa en `native-lib.cpp`, `WhisperLib.kt` y `TranscriptionEngine.kt`, con estrategia latest-wins y evicción por presión de memoria.

## Fase 3 — Componentes de sistema

- [x] Implementar onboarding de micrófono y notificaciones Android 13+.
- [x] Guiar al usuario para activar overlay y accesibilidad.
- [x] Implementar `FloatingButtonService` foreground.
- [x] Implementar `AutoTypeAccessibilityService`.
- [x] Implementar `HandyInputMethodService`.
- [x] Implementar `VoiceRecognitionService` y `RecognizeActivity`.
- [x] Implementar overlay y transcripción periódica de `LiveSubtitleService` (ventanas acotadas; no streaming incremental completo).
- [x] Añadir fallback de inserción cuando `ACTION_SET_TEXT` no esté soportado (3 niveles: `ACTION_SET_TEXT` -> `ClipboardManager` + `ACTION_PASTE` -> Toast/Notificación).
- [ ] Añadir tests instrumentados de permisos, IME, accesibilidad y foreground services.

## Fase 4 — Modelos e importación

- [x] Descargar modelos remotos a almacenamiento privado.
- [x] Importar modelos mediante Android Document Picker.
- [x] Persistir modelo activo en `files/active_model`.
- [x] Escribir descargas/importaciones a `.part` antes de moverlas al destino.
- [x] Reemplazar modelos existentes de forma segura cuando el filesystem lo permita.
- [x] Mostrar modelos instalados y disponibles.
- [x] Añadir tienda con búsqueda, filtro de disponibilidad y sección coming soon para modelos de hasta 1,2B parámetros.
- [x] Generar `ModelCatalog.kt` desde `src-tauri/src/catalog/catalog.json` mediante script reproducible y overrides legacy explícitos.
- [x] Verificar el catálogo generado desde Gradle/`preBuild` y CI.
- [x] Calcular SHA-256 para cada descarga/importación y persistirlo en un sidecar local.
- [x] Cargar el archivo con Whisper/GGML antes de moverlo al destino y antes de activarlo.
- [x] Rechazar modelos modificados o sin digest validado durante selección/transcripción.
- [ ] Publicar y fijar SHA-256 autenticados para cada modelo del catálogo remoto; los tres overrides actuales tienen digest verificado localmente, pero la procedencia remota aún debe formalizarse.
- [ ] Añadir cancelación, reintentos y progreso fiable de descargas.
- [x] Añadir tests JVM de SHA-256, metadata de digest y detección de manipulación.
- [ ] Añadir tests de archivo corrupto, descarga interrumpida y URI `content://`.

## Fase 5 — Compartición y UX

- [x] Aceptar `ACTION_VIEW` y `ACTION_SEND` para audio.
- [x] Resolver `EXTRA_STREAM`, `Intent.data` y `ClipData` con compatibilidad API 26–35.
- [x] Validar acceso mediante `ContentResolver`.
- [x] Cancelar trabajos obsoletos al recibir un nuevo intent.
- [x] Corregir lint del intent filter multimedia.
- [x] Añadir documentación Android en `ANDROID.md`.
- [x] Documentar el flujo de generación/verificación del catálogo Android y su handoff agéntico.
- [x] Diseñar el historial de transcripciones SQLite, UI de navegación en MainActivity y registro automático en servicios.
- [x] Implementar `HistoryDatabase.kt`, `HistoryRepository.kt`, `HistoryScreen.kt`, navegación en `MainActivity` y registro automático en los servicios.
- [x] Diseñar el sistema de señales de audio (SoundPool) y respuestas hápticas de vibración en el inicio, fin y éxito de transcripción.
- [x] Implementar `AudioFeedbackManager.kt`, recursos de audio en `res/raw/`, ajustes independientes en `SettingsManager.kt` y conmutadores UI en `MainActivity.kt`.
- [x] Diseñar el motor local de postprocesado (PostProcessor.kt), sustitución de vocabulario / reglas `clave = valor`, normalización de puntuación y autocapitalización.
- [x] Implementar `PostProcessor.kt`, llamada transparente en `TranscriptionEngine.kt`, preferencias en `SettingsManager.kt` y pantallas UI `PostProcessSettingsActivity.kt` y `CustomWordsActivity.kt`.
- [x] Migrar strings Android a recursos/i18n (`res/values/strings.xml` como fuente, traducciones es/de/fr/ja/zh/pt, migración completa de pantallas Compose y servicios).

## Fase 6 — Paridad Avanzada Handy DPC (VAD, Push-to-Talk, LLM & Sistema)

- [x] Integrar `com.microsoft.onnxruntime:onnxruntime-android` en `app/build.gradle.kts`.
- [x] Implementar `SileroVadDetector.kt` cargando `silero_vad_v4.onnx` en `AudioRecorder.kt` para detección de probabilidad de voz y auto-stop por silencio de 1.2s.
- [x] Implementar Gesto Dual Inteligente en `FloatingButtonService.kt` y `HandyInputMethodService.kt` (Tap para Toggle, Hold para Push-to-Talk con transcripción al soltar).
- [x] Implementar `LlmPostProcessor.kt` compatible con la API de OpenAI (OpenAI, Groq, OpenRouter, Ollama) con prompts personalizables y fallback automático por error/timeout de red.
- [x] Crear pantalla de ajustes UI para LLM (`LlmSettingsActivity.kt`) con configuración de endpoint, API key, modelo y prompt de sistema.
- [x] Implementar Fallback Automático Multinivel de Inserción de Texto en `AutoTypeAccessibilityService.kt` (Nivel 1: `ACTION_SET_TEXT`, Nivel 2: `ClipboardManager` + `GLOBAL_ACTION_PASTE`, Nivel 3: Toast / Notificación).
- [x] Implementar `HandyTileService.kt` para control de dictado desde el panel de Ajustes Rápidos del sistema.
- [x] Añadir indicador visual de forma de onda (Audio Waveform) en el overlay flotante durante la grabación.
- [x] Migrar strings Android a recursos/i18n (`res/values/strings.xml` como fuente y traducciones es/de/fr/ja/zh/pt).

## Fase 7 — CI y release

- [x] Añadir `.github/workflows/android.yml`.
- [x] Instalar en CI SDK 35, Build Tools 35.0.0, CMake 3.22.1 y NDK 27.0.12077973.
- [x] Ejecutar lint, unit tests, debug APK y release compilation en CI.
- [x] Fijar `ndkVersion` y conservar reglas R8 para JNI.
- [x] Versionar mediante commit los fuentes Android, wrapper, workflow, tienda, generador y documentación.
- [ ] Configurar firma segura de release fuera del repositorio.
- [ ] Generar y verificar AAB/APK firmado.
- [ ] Preparar distribución y política de privacidad para accesibilidad, micrófono y modelos locales.

## Criterio de beta interna

La beta interna puede avanzar cuando:

1. el módulo Android esté en un checkout limpio;
2. CI pase en ese checkout;
3. una voz humana conocida produzca el texto esperado;
4. el flujo de permisos, overlay, accesibilidad, IME y archivo compartido tenga smoke test;
5. no existan modelos corruptos activables silenciosamente; cada modelo activo tenga digest local y carga Whisper validada.

## Criterio de release público

Además de lo anterior:

- release firmado y reproducible;
- AAB validado;
- checksums autenticados y procedencia de modelos documentados;
- cobertura instrumentada de rutas críticas;
- cancelación/concurrencia resueltas;
- privacidad e internacionalización revisadas.

