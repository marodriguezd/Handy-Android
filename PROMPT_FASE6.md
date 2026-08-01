# Master Prompt — Implementación Fase 6: Paridad Avanzada Handy DPC

> Este archivo contiene el prompt maestro independiente para ser entregado directamente al agente de IA para la ejecución secuencial completa de la **Fase 6** de Handy Android.

---

```text
Por favor, implementa de forma secuencial y completa la Fase 6 del plan de paridad Handy DPC para Android siguiendo las especificaciones en spec.md y la lista de tareas en plan.md:

1. Silero VAD (ONNX Runtime):
   - Añade com.microsoft.onnxruntime:onnxruntime-android a app/build.gradle.kts.
   - Crea SileroVadDetector.kt utilizando silero_vad_v4.onnx sobre AudioRecorder.kt para detectar probabilidad de voz y auto-stop tras 1.2s continuos de silencio.

2. Gesto Dual Push-to-Talk & Toggle:
   - Modifica FloatingButtonService.kt y HandyInputMethodService.kt para soportar Tap (Toggle encendido/apagado) y Hold (Push-to-Talk con transcripción al soltar).
   - Ajusta AudioFeedbackManager.kt con tonos y pulsos hápticos diferenciados.

3. Post-procesador Inteligente LLM:
   - Implementa LlmPostProcessor.kt (OpenAI API / Groq / Ollama / OpenRouter) con fallback automático al PostProcessor local de reglas en caso de error o timeout (5s).
   - Crea LlmSettingsActivity.kt para configurar endpoint, API Key, modelo y prompt de sistema.

4. Resiliencia de Inserción y Componentes de Sistema:
   - Añade el Fallback Automático Multinivel a AutoTypeAccessibilityService.kt (1. ACTION_SET_TEXT -> 2. ClipboardManager + GLOBAL_ACTION_PASTE -> 3. Notificación Toast flotante).
   - Implementa HandyTileService.kt para Quick Settings Tile de dictado en Android.
   - Añade animación de forma de onda (audio waveform) en el overlay flotante de FloatingButtonService.kt.

5. Validación Canónica:
   Ejecuta el arnés de pruebas canónico antes de finalizar:
   ./gradlew checkModelCatalog testDebugUnitTest lintDebug assembleDebug
```

## Referencias Técnicas
- **Especificaciones**: [`spec.md`](spec.md) (Secciones 14, 15 y 16)
- **Plan de Tareas**: [`plan.md`](plan.md) (Fase 6)
- **Guía de Agentes**: [`AGENTS.md`](AGENTS.md)
