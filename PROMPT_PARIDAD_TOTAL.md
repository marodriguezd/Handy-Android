# Master Prompt — Implementación de Paridad Total Handy Android

> Este archivo contiene el prompt maestro independiente para ser entregado directamente al siguiente agente de IA para la ejecución secuencial completa de la paridad de Handy Desktop a Handy Android.

---

Por favor, ejecuta de forma secuencial y completa el Plan Maestro de Paridad Total de Handy Android siguiendo la especificación técnica en `spec.md`, la lista de tareas en `plan.md` y las instrucciones detalladas a continuación:

### 1. FASE A: Motor Nativo JNI, Idioma, Aceleración GPU y RAM
- **Firmas JNI & Parámetros Whisper**: Actualiza `Java_com_handy_android_WhisperLib_fullTranscribe` en `app/src/main/cpp/native-lib.cpp`, `WhisperLib.kt`, `TranscriptionEngine.kt` y `SettingsManager.kt` para pasar `language`, `translate` e `initialPrompt`.
- **UI de Ajustes de Motor**: Crea `TranscriptionSettingsActivity.kt` con selector de idioma (Auto, ES, EN, FR, DE, ZH, JA, RU), conmutador "Traducir a Inglés" y campo "Prompt Inicial".
- **Descarga de RAM**: Implementa temporizador configurable de descarga de modelo nativo en `TranscriptionEngine.kt`.
- **Aceleración GPU Vulkan**: Habilita backend Vulkan NDK en `CMakeLists.txt` (`-DGGML_VULKAN=ON`) y selector UI.

### 2. FASE B: Pipeline de Audio, Buffer Amortiguador y Mute de Sistema
- **Extra Recording Buffer**: Añadir amortiguador `extraRecordingBufferMs` (default 300ms) en `AudioRecorder.kt` para no cortar la última palabra.
- **Mute de Sistema**: Solicitar `AudioFocusRequest` (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) en `AudioRecorder.kt` durante la grabación.
- **Selección de Micrófono**: Permitir elegir micrófono (Bluetooth, Headset, Interno) mediante `AudioManager.getDevices()`.
- **Micrófono Cálido**: Opción `alwaysOnMicrophoneEnabled` para eliminar latencia de arranque.

### 3. FASE C: Retención de Audio en Historial y Visualización Avanzada
- **Guardado de Audio**: Guardar notas de voz `.wav` en `files/recordings/` y añadir columna `audio_file_path` en `HistoryDatabase.kt`.
- **Reproductor de Historial**: Añadir reproductor `MediaPlayer` en `HistoryScreen.kt` para escuchar notas grabadas.
- **Panel Streaming Live**: Expandir overlay flotante en `FloatingButtonService.kt` para mostrar panel con texto transcrito en vivo.

### 4. FASE D: Integración de Sistema, Auto-Submit, Boot y Control Remoto
- **Auto-Submit (`Enter`)**: Enviar automáticamente tecla `Enter` post-inserción en `AutoTypeAccessibilityService.kt` e `HandyInputMethodService.kt`.
- **Boot Receiver**: Crear `BootReceiver.kt` (`ACTION_BOOT_COMPLETED`) para autostart del servicio si está activo.
- **Limpiador de Muletillas**: Añadir eliminador de palabras de relleno ("eh", "um", "este") y espacio final opcional en `PostProcessor.kt`.
- **Control Remoto ADB**: Crear `RemoteControlReceiver.kt` para intents `com.handy.android.ACTION_TOGGLE_RECORDING` y `com.handy.android.ACTION_CANCEL`.

### 5. FASE E: Post-Procesado LLM Extendido, Visor de Logs e i18n
- **Biblioteca de Prompts LLM**: Permitir crear, editar y guardar múltiples plantillas de prompts LLM en `LlmSettingsActivity.kt`.
- **Live Log Viewer**: Crear pantalla `LiveLogViewerActivity.kt` para inspeccionar logs en vivo en la app.
- **i18n**: Añadir traducciones `strings.xml` para Francés, Alemán, Chino, Japonés y Portugués.

### 6. Validación Canónica Obligatoria
Tras completar las fases, ejecuta la suite de verificación canónica:
```bash
./gradlew checkModelCatalog testDebugUnitTest lintDebug assembleDebug
```
