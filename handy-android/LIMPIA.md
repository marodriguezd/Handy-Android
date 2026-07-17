# Handy Android — Nueva Sesión Limpia

Use este documento al arrancar una sesión nueva para continuar exactamente desde el punto actual del proyecto.

---

## 1. Contexto del Proyecto

- **Repositorio:** `Handy-Android` (fork Android de Handy PC).
- **Módulos principales:**
  - `handy-android/handy-core/` — librería Rust con JNI (`libhandy_core.so`).
  - `handy-android/app/` — app Kotlin/Jetpack Compose.
- **Sprint actual:** Sprint 28 (Debug panel gated MVP - debugMode flag + Screen.Debug route + RingBufferLog) **fully closed**, pendiente el push desde interactive shell per AGENTS.md auth notes. **Sprint 28 MVP 100% complete.** Build green: `:app:compileDebugKotlin` BUILD SUCCESSFUL / 0 warnings, `:app:testDebugUnitTest --rerun-tasks` **122 PASS / 0 FAIL** (117 prior + 5 nuevos RingBufferLog tests), `:app:lintDebug --rerun-tasks` 0 errors / 76 warnings (sin regresión). Próximo sprint (Sprint 28b): implementar los 7 componentes MD3 Debug (LogLevelSelector, UpdateChecksToggle, SoundPicker reuse, PasteDelaySlider, RecordingBufferSlider, AlwaysOnMicrophoneSwitch, LiveLogViewer) + settings UI reactiva para debugMode + Shizuku Android 16 probe para los 3 PrivateApi warnings.
- **Documentos clave:**
  - `handy-android/PROGRESS.md` — estado actual y pasos pendientes.
  - `handy-android/SPEC.md` — especificación de UI/UX.
  - `handy-android/ARCHITECTURE.md` — arquitectura del sistema.
  - `AGENTS.md` — guía para asistentes (comandos de build, arquitectura, convenciones).

---

## 2. Cómo Empezar una Sesión Nueva

### 2.1 Revisa el estado del repo

```bash
git status
git log --oneline -5
```

### 2.2 Lee los documentos de contexto

Lee en este orden:

1. `handy-android/PROGRESS.md`
2. `handy-android/SPEC.md`
3. `handy-android/ARCHITECTURE.md`
4. `AGENTS.md`

### 2.3 Verifica que el proyecto compila

```bash
cd handy-android
./gradlew :app:compileDebugKotlin
```

### 2.4 Verifica dispositivos ADB conectados

```bash
adb devices -l
./scripts/check_device.sh    # idempotente, también imprimer diagnóstico si falta
```

El dispositivo de prueba habitual es un **A059** con serial histórico `adb-00143154F001971-AbAnvz._adb-tls-connect._tcp` y, vía mDNS o pairing persistente, suele aparecer también como `192.168.1.36:<puerto>`.

> **Sesión activa verificada (17 julio 2026)**: emparejamiento vía Wi-Fi en `192.168.1.36:40293`. El usuario tenía Wireless debugging desactivado accidentalmente; tras reactivarlo en Developer options y volver a emparejar, ese es el puerto actual. **El puerto cambia en cada emparejamiento** — lee el que muestra la pantalla Wireless debugging del A059 antes de hacer `adb connect`.

> **Si no aparece nada en `adb devices`**, abre **Settings → System → Developer options → Wireless debugging** en el A059, copia el IP:port que aparece, y ejecuta:
>
> ```bash
> adb connect <teléfono_ip>:<teléfono_port>
> ```
>
> Path completo, USB→TCP fallback, troubleshooting de `Connection refused` / `Unauthorized` / `offline` en [`scripts/RECONNECT_DEVICE.md`](scripts/RECONNECT_DEVICE.md).

---

## 3. Flujo de Trabajo ADB Automatizado

Este flujo reproduce exactamente lo que se validó en la sesión anterior.

### 3.1 Build limpio

```bash
cd handy-android
./gradlew clean assembleDebug
```

### 3.2 Ejecutar el flujo completo con un solo comando

```bash
./scripts/adb_test_flow.sh adb-00143154F001971-AbAnvz._adb-tls-connect._tcp canary-180m-flash-Q4_K_M
```

El script hace:
1. `wait-for-device`
2. `./gradlew clean assembleDebug`
3. `adb uninstall com.handy.app.debug`
4. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
5. `pm grant RECORD_AUDIO`
6. `am start ... --ez skip_onboarding true`
7. Broadcast `DOWNLOAD_MODEL`
8. Polling de logcat hasta `Download complete: <model_id>`
9. Broadcast `SET_ACTIVE_MODEL`
10. Polling de logcat hasta `Active model set to <model_id>`

### 3.3 Instalación limpia manual en el dispositivo

```bash
DEVICE="adb-00143154F001971-AbAnvz._adb-tls-connect._tcp"
adb -s "$DEVICE" uninstall com.handy.app.debug
adb -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3.4 Permisos y lanzamiento (salta onboarding)

```bash
adb -s "$DEVICE" shell pm grant com.handy.app.debug android.permission.RECORD_AUDIO
adb -s "$DEVICE" logcat -c
adb -s "$DEVICE" shell am start -n com.handy.app.debug/com.handy.app.MainActivity --ez skip_onboarding true
```

### 3.5 Descargar y activar un modelo ligero

```bash
MODEL_ID="canary-180m-flash-Q4_K_M"
adb -s "$DEVICE" shell am broadcast -a com.handy.app.action.DOWNLOAD_MODEL --es model_id "$MODEL_ID"
# Espera a que termine la descarga (monitorea logcat)
adb -s "$DEVICE" shell am broadcast -a com.handy.app.action.SET_ACTIVE_MODEL --es model_id "$MODEL_ID"
```

### 3.6 Verificación

```bash
# Verifica que el modelo está activo
adb -s "$DEVICE" logcat -d | grep -E 'active model|canary-180m' | tail -20

# Verifica que MainActivity está en primer plano
adb -s "$DEVICE" shell dumpsys activity activities | grep 'mFocusedApp'
```

---

## 4. Hooks de Test Disponibles (Debug Builds)

| Acción | Broadcast / Extra | Efecto |
|--------|-------------------|--------|
| Saltar onboarding | `am start ... --ez skip_onboarding true` | `MainActivity` marca onboarding como completado |
| Descargar modelo | `com.handy.app.action.DOWNLOAD_MODEL` `--es model_id <id>` | Llama `EngineBridge.nativeDownloadModel` |
| Activar modelo | `com.handy.app.action.SET_ACTIVE_MODEL` `--es model_id <id>` | Llama `EngineBridge.nativeSetActiveModel` |

IDs de modelos útiles para pruebas:

- `canary-180m-flash-Q4_K_M` — 139 MB, ligero.
- `parakeet-tdt-0.6b-v3-Q4_K_M` — 485 MB, recomendado móvil.

---

## 5. Convenciones Importantes

- **Shizuku está desactivado en debug builds** (`BuildConfig.DEBUG`). En release se comporta normalmente.
- **TestCommandReceiver** solo existe en debug builds y requiere `android.permission.DUMP` (ADB shell).
- **Vulkan está deshabilitado en debug builds** para iteración rápida. Release builds intentan usar `GGML_VULKAN=ON` con headers vendoreados.
- **No modifiques código de producción sin revisar** `AGENTS.md` y el estilo existente.
- **Ejecuta siempre antes de commit:**
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:lintDebug`
- **Versiones:** si cambias funcionalidad, sube `versionCode` y `versionName` en `app/build.gradle.kts`.

---

## 6. Próximas Tareas Pendientes (Prioridad)

1. **Re-activar Vulkan GPU backend:** añadir feature de Cargo, arreglar include paths, probar en release.
2. **Investigar QNN/Hexagon NPU:** evaluar fork `zhouwg/ggml-hexagon`.
3. **Verificación visual IME:** capturar screenshots de todos los estados del IME.
4. **Verificación visual onboarding:** capturar screenshots de cada paso del wizard.
5. **Hardening adicional de TestCommandReceiver:** considerar un permiso propio o verificación de sender si el manifest-level `DUMP` no es suficiente.
