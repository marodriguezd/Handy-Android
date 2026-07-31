# AGENT PROMPT — Handy Android

> Este archivo es el handoff para la siguiente sesión. El trabajo activo es exclusivamente el port Android local en este repositorio; no reinicies ninguna importación ni dependas de proyectos externos.

## Objetivo y límites

Mantener y completar el port de Handy de escritorio a Android bajo el paquete `com.handy.android`, usando Kotlin/Compose, servicios Android y Whisper/GGML local mediante JNI. El objetivo funcional es grabar voz, transcribirla localmente y escribir el resultado en la aplicación enfocada.

El módulo Android es un proyecto Gradle independiente en la raíz. La aplicación Tauri de escritorio (`src/` y `src-tauri/`) es únicamente referencia de producto y arquitectura; no debe modificarse para resolver problemas exclusivos de Android salvo petición expresa.

## Lectura obligatoria al comenzar

1. `spec.md`: arquitectura y contrato técnico vigente.
2. `plan.md`: fases completadas y backlog priorizado.
3. `ANDROID.md`: requisitos, build, CI y smoke tests.
4. `TEST_HANDY.txt`: evidencia de dispositivo y runbook ADB.
5. `AGENTS.md`: reglas generales y separación Android/Tauri.
6. `git status --short` y `git log -1 --oneline`: confirmar el estado del checkout antes de editar.

## Reglas de ejecución

1. Usa el prefijo `rtk` en comandos de terminal cuando ejecutes validaciones locales.
2. No hagas `git reset --hard`, limpiezas destructivas ni borres modelos/caches sin autorización explícita.
3. Antes de cambiar un símbolo exportado o una API compartida, busca y actualiza todas sus referencias.
4. Para cambios de tres pasos o más, crea un plan de tareas y valida cada fase.
5. Tras cambios relevantes, ejecuta revisión independiente, lint, tests y builds apropiados.
6. Mantén los textos Android sujetos a una futura estrategia de recursos/i18n; no introduzcas dependencias sin verificar su uso.
7. Mantén ignorados `.gradle/`, `app/build/`, `app/.cxx/`, `local.properties`, APK/AAB, logs, `.part` y modelos binarios.
8. No uses el sidecar local como prueba de procedencia remota: un SHA-256 local demuestra consistencia después de validar, no autenticidad del origen.

## Contrato técnico Android

- Package/application ID: `com.handy.android`.
- Native library: `handy_whisper_jni`.
- Firmas JNI:
  - `Java_com_handy_android_WhisperLib_initContext`
  - `Java_com_handy_android_WhisperLib_freeContext`
  - `Java_com_handy_android_WhisperLib_fullTranscribe`
- Captura: PCM 16 kHz, mono, 16-bit, normalizado a `[-1, 1]`.
- Modelos: `files/models/*.bin`.
- Modelo activo: `files/active_model` mediante `SettingsManager`.
- Sidecar de integridad: `<modelo>.bin.sha256`.
- Componentes principales:
  - `MainActivity`: onboarding, permisos y acceso a funciones.
  - `FloatingButtonService`: grabación foreground y overlay.
  - `AutoTypeAccessibilityService`: inserción en el campo enfocado.
  - `HandyInputMethodService`: IME de voz.
  - `VoiceRecognitionService`/`RecognizeActivity`: reconocimiento del sistema.
  - `ModelsActivity`/`ModelDownloader`: descarga, importación, validación y activación explícita.
  - `ModelValidator`: SHA-256, sidecar y carga real de Whisper antes de activar.
  - `TranscribeFileActivity`: `ACTION_VIEW`/`ACTION_SEND` para audio.
  - `LiveSubtitleService`: overlay y transcripción periódica local.
  - `WhisperLib`/`native-lib.cpp`: puente JNI a whisper.cpp/GGML.

## Estado verificado

El módulo Android, workflow, wrapper, tienda de modelos, generador y documentación forman parte del estado de trabajo que debe quedar versionado en el repositorio. La tienda se alimenta de `src-tauri/src/catalog/catalog.json` a través de `scripts/generate_android_model_catalog.py`; `scripts/android_model_catalog_overrides.json` conserva los tres artefactos legacy `.bin` que el backend JNI actual puede activar. `ModelCatalog.kt` es generado: no editarlo manualmente. El subconjunto contiene 52 entradas de hasta 1,2B parámetros; solo Whisper Small, Medium y Large v3 Turbo están disponibles ahora, y las demás se muestran como coming soon.

Validaciones locales completadas:

- generador `--check` correcto;
- `generateModelCatalog` y `checkModelCatalog` correctos;
- `lintDebug` correcto;
- `testDebugUnitTest` correcto;
- `assembleDebug` correcto;
- `assembleRelease` correcto;
- `git diff --check` correcto;
- instalación incremental en Android 16 ARM64;
- permisos de micrófono/notificaciones, overlay, accesibilidad y foreground service;
- carga de modelo GGML real y `libhandy_whisper_jni.so`;
- decoder e inferencia sin crash JNI/native;
- tests JVM de SHA-256, sidecars, catálogo y detección de manipulación.

La fixture sintética produjo `No speech detected`; eso valida ejecución, no precisión con voz humana.

## Siguiente prompt recomendado para una sesión limpia

> Continúa el port Android de Handy desde el estado actual. Lee `AGENTS.md`, `agent_prompt.md`, `plan.md`, `ANDROID.md` y `TEST_HANDY.txt`; comprueba `git status --short` y no reinicies el trabajo de la tienda. La tienda de modelos ya está implementada y `ModelCatalog.kt` se genera desde `src-tauri/src/catalog/catalog.json` con `scripts/generate_android_model_catalog.py`, overrides en `scripts/android_model_catalog_overrides.json`, y verificación Gradle mediante `generateModelCatalog`/`checkModelCatalog`. Pregunta antes de tomar decisiones de producto. Prioriza el siguiente ítem pendiente del backlog, empieza por procedencia autenticada de checksums remotos o pruebas instrumentadas, y valida con lint, tests y builds Android.

## Siguiente trabajo prioritario

### P1 — Confianza y pruebas de release

- Publicar y fijar checksums remotos autenticados para cada modelo del catálogo. Los modelos actuales aún tienen `expectedSha256 = null`.
- Añadir tests instrumentados para rechazo de GGML inválido mediante JNI, URI `content://`, `ACTION_SEND`, permisos, IME, accesibilidad y foreground services.
- Ejecutar una prueba con audio humano conocido y texto esperado.
- Añadir cancelación, reintentos y progreso fiable de descargas; la serialización global de descargas de la tienda ya está implementada.
- Diseñar cancelación o cola explícita alrededor de la inferencia JNI síncrona.

### P2 — Robustez y paridad

- Revisar fallback de inserción cuando una app no soporta `ACTION_SET_TEXT`.
- Completar cache/manager de modelo para no recargar Whisper en cada operación.
- Completar postprocesado configurable sin inventar proveedores ni credenciales.
- Internacionalizar textos Android mediante recursos.
- Medir aceleración ARM/GPU antes de habilitar optimizaciones.
- Preparar firma segura, AAB y distribución Play.

## Comandos canónicos

```bash
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

Para pruebas físicas, usa siempre `adb -s <DEVICE_SERIAL>` cuando haya más de un dispositivo conectado. No versiones modelos ni fixtures; deben suministrarse localmente para la prueba autorizada.
