# 📝 Draft Release v0.9.0 — Handy Android

> **Estado:** Borrador preparado para publicación futura (Release pausada temporalmente mientras se resuelven detalles pendientes).

---

## 🎙️ Notas de Release para GitHub (`v0.9.0`)

### 📱 Capturas de Pantalla / Screenshots

| Historial & Grabaciones | Reconocimiento de Voz (Modal) | Catálogo de Modelos |
| :---: | :---: | :---: |
| <img src=".screenshots/app_main.png" width="240" alt="Historial" /> | <img src=".screenshots/app_voice_recognize.png" width="240" alt="Voz" /> | <img src=".screenshots/app_models.png" width="240" alt="Modelos" /> |

| Diccionario Fonético | Ajustes del Sistema |
| :---: | :---: |
| <img src=".screenshots/app_dictionary.png" width="240" alt="Diccionario" /> | <img src=".screenshots/app_settings.png" width="240" alt="Ajustes" /> |

---

### 🌟 Destacados de la Release

- **Motor Local `handy-core` (Rust JNI)**:
  - Inferencia local 100% offline con Whisper.cpp / GGML y Silero VAD.
  - Cero dependencia de servidores en la nube y privacidad garantizada.

- **Integración Nativa en Android**:
  - **Teclado IME Nativo (`HandyInputMethodService`)**: Formato de píldora flotante (Material Design 3) con animación de 6 estados e integración directa en cualquier app.
  - **Servicio de Voz (`HandyVoiceRecognitionService`)**: Implementación nativa de `android.speech.RecognitionService` con Intent `RECOGNIZE_SPEECH` y Modal Bottom Sheet (`RecognizeActivity`).
  - **Estrategias de Inyección de Texto**: Compatibilidad con Shizuku, Servicio de Accesibilidad, Pegado Directo y Portapapeles.

- **Corrección Fonética y Posprocesamiento**:
  - **Corrector Fonético (`WordCorrector`)**: Combinación de Soundex estándar, distancia Levenshtein y N-gramas con eliminación de muletillas.
  - **Diccionario Personalizado (`DictionaryScreen`)**: Gestión gráfica de palabras clave y términos propios.
  - **Posprocesamiento por LLM (`PostProcessor` & `PromptsRepository`)**: Pulido de transcripciones compatible con APIs estilo OpenAI y Ollama.

- **Diseño Adaptativo & Experiencia de Usuario**:
  - Interfaz Material Design 3 con la paleta de colores icónica de Handy (`#2c2b29`, `#f28cbb`, `#da5893`).
  - Adaptación fluida para móviles (`NavigationBar`) y tablets (`NavigationRail`).
  - Selector de temas (`ThemeSelector`) e idiomas (`LocaleSelector`) dinámicos sin necesidad de reiniciar la actividad.

- **Historial y Grabación Dual WAV**:
  - Repositorio local (`RecordingRepository`) con guardado de audio WAV, reproducción integrada y política de retención/evicción por límite de almacenamiento.

---

### 📦 Archivos a Incluir en la Publicación Futura
- `app-debug.apk` (o APK release firmado)
- Archivos de imágenes en `.screenshots/`:
  - `app_main.png`
  - `app_voice_recognize.png`
  - `app_models.png`
  - `app_dictionary.png`
  - `app_settings.png`

---

## 🛠️ Comando para Publicar en el Futuro (vía gh CLI)

```bash
cd handy-android
./gradlew :app:assembleDebug
git tag -a v0.9.0 -m "Handy Android v0.9.0 Full Release"
git push origin v0.9.0
gh release create v0.9.0 app/build/outputs/apk/debug/app-debug.apk .screenshots/*.png --repo marodriguezd/Handy-Android --title "Handy Android v0.9.0 (Release Completa)" --notes-file ../draft.md
```
