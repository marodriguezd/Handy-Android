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
- [x] Preparar la incorporación Git de todos los fuentes Android y documentación, excluyendo `.gradle/`, `app/build/`, `app/.cxx/` y otros artefactos generados. El commit y su sincronización remota se verifican al cerrar esta sesión.

## Fase 2 — JNI, Whisper y audio

- [x] Corregir firmas JNI al paquete `com.handy.android`.
- [x] Cargar y liberar `whisper_context` con ciclo de vida explícito.
- [x] Proteger inferencia y liberación mediante mutex por contexto.
- [x] Capturar PCM 16 kHz mono 16-bit y normalizar a float.
- [x] Decodificar archivos mediante MediaExtractor/MediaCodec.
- [x] Resamplear audio compartido a 16 kHz.
- [x] Verificar carga e inferencia real en dispositivo sin crash native.
- [ ] Validar texto esperado con una grabación humana reproducible.
- [ ] Diseñar cache/manager de modelo para evitar recargarlo en cada operación.
- [ ] Diseñar cancelación o cola para inferencias JNI bloqueantes.

## Fase 3 — Componentes de sistema

- [x] Implementar onboarding de micrófono y notificaciones Android 13+.
- [x] Guiar al usuario para activar overlay y accesibilidad.
- [x] Implementar `FloatingButtonService` foreground.
- [x] Implementar `AutoTypeAccessibilityService`.
- [x] Implementar `HandyInputMethodService`.
- [x] Implementar `VoiceRecognitionService` y `RecognizeActivity`.
- [x] Implementar overlay y transcripción periódica de `LiveSubtitleService` (ventanas acotadas; no streaming incremental completo).
- [ ] Añadir fallback de inserción cuando `ACTION_SET_TEXT` no esté soportado.
- [ ] Añadir tests instrumentados de permisos, IME, accesibilidad y foreground services.

## Fase 4 — Modelos e importación

- [x] Descargar modelos remotos a almacenamiento privado.
- [x] Importar modelos mediante Android Document Picker.
- [x] Persistir modelo activo en `files/active_model`.
- [x] Escribir descargas/importaciones a `.part` antes de moverlas al destino.
- [x] Reemplazar modelos existentes de forma segura cuando el filesystem lo permita.
- [x] Mostrar modelos instalados y disponibles.
- [x] Calcular SHA-256 para cada descarga/importación y persistirlo en un sidecar local.
- [x] Cargar el archivo con Whisper/GGML antes de moverlo al destino y antes de activarlo.
- [x] Rechazar modelos modificados o sin digest validado durante selección/transcripción.
- [ ] Publicar y fijar SHA-256 autenticados para cada modelo del catálogo remoto.
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
- [ ] Migrar strings Android a recursos/i18n.
- [ ] Completar postprocesado o retirar el placeholder hasta definir contrato de proveedor.
- [ ] Añadir historial y equivalentes móviles de las funciones prioritarias de escritorio.

## Fase 6 — CI y release

- [x] Añadir `.github/workflows/android.yml`.
- [x] Instalar en CI SDK 35, Build Tools 35.0.0, CMake 3.22.1 y NDK 27.0.12077973.
- [x] Ejecutar lint, unit tests, debug APK y release compilation en CI.
- [x] Fijar `ndkVersion` y conservar reglas R8 para JNI.
- [x] Versionar mediante commit los fuentes Android, wrapper, workflow y documentación.
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
