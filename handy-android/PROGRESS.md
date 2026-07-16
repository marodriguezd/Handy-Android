# Handy Android — Progress & Current State

**Last updated:** 2026-07-16
**Current checkpoint:** Sprint 16 — Material Design 3 Redesign + ADB Test Automation Hooks + Backend Investigation

---

## ✅ Completed Work

### Sprint 16 — Material Design 3 Redesign

See `SPEC.md` for the full design spec. The following MD3 gaps were closed in this session:

| Area | Change | Files |
|------|--------|-------|
| **Settings** | Custom `SettingsRow` replaced with Material3 `ListItem` | `ui/settings/SettingsScreen.kt` |
| **Model Catalog** | Non-clickable `AssistChip` for language tags replaced with `SuggestionChip` | `ui/models/ModelCatalogScreen.kt` |
| **IME** | Hardcoded `RoundedCornerShape` replaced with `MaterialTheme.shapes` tokens; `FilledIconButton` touch targets enlarged from 34.dp to 48.dp | `ime/HandyInputMethodService.kt` |
| **Badges** | Hardcoded `RoundedCornerShape(4.dp)` replaced with `MaterialTheme.shapes.extraSmall` | `ui/models/components/CompatibilityBadge.kt` |
| **Version** | Bumped to `versionCode=3`, `versionName="1.0.0-alpha2"` | `app/build.gradle.kts` |
| **Lint** | Fixed `MissingSuperCall` in `HandyApplication.onLowMemory()` | `HandyApplication.kt` |

### ADB Test Automation (Debug Builds Only)

To enable fully automated end-to-end testing via ADB, the following test-only hooks were added:

| Hook | Purpose | Files |
|------|---------|-------|
| **Shizuku disabled in debug** | Prevents Shizuku permission/security exceptions from blocking automated runs | `HandyApplication.kt`, `injection/InjectorRouter.kt`, `ui/settings/SettingsScreen.kt` |
| **Skip onboarding intent** | `MainActivity` reads `skip_onboarding=true` and marks onboarding complete | `MainActivity.kt` |
| **TestCommandReceiver** | Manifest-declared receiver handling `com.handy.app.action.DOWNLOAD_MODEL` and `com.handy.app.action.SET_ACTIVE_MODEL` broadcasts with `model_id` extra | `TestCommandReceiver.kt`, `AndroidManifest.xml` |
| **ADB test script** | `scripts/adb_test_flow.sh` automates uninstall/install/grant/launch/download/activate/verify | `scripts/adb_test_flow.sh` |

### TestCommandReceiver Security Hardening

The receiver is now restricted to ADB/shell use only:

| Measure | Implementation |
|---------|----------------|
| Manifest permission | `android:permission="android.permission.DUMP"` |
| Disabled in release | `android:enabled="${debugReceiverEnabled}"` placeholder |
| Model validation | Exact match against `ModelInfo.id` from `EngineBridge.nativeGetAvailableModels()` |
| No runtime permission check | Removed `checkCallingPermission` because it does not work inside `BroadcastReceiver.onReceive()` |

### Catalog Sort Tests

Extracted the sort logic from `ModelsViewModel.computeVisibleList` into a pure, testable function:

| Change | Files |
|--------|-------|
| Pure sort function | `capability/CatalogSorter.kt` (`computeVisibleCatalog`) |
| ViewModel delegates to sorter | `viewmodel/ModelsViewModel.kt` |
| Unit tests (10 tests) | `test/java/com/handy/app/capability/CatalogSorterTest.kt` |

Tests cover: ACTIVE first, status ordering, promotion bucket ordering, size tie-breaker, experimental filtering, full sort chain, EXCEEDS/FIT behavior on MID devices, and a regression guard ensuring Voxtral Small 24B does not float above an active lightweight model.

### MD3 Visual Verification

Screenshots captured on device (A059, Android 16) for visual regression reference:

| Screen | File |
|--------|------|
| Settings — General | `screenshots/settings.png` |
| Settings — Avanzado | `screenshots/advanced.png` |
| Model Catalog | `screenshots/models.png` |
| History | `screenshots/history.png` |
| About | `screenshots/about.png` |

### Backend / NPU Investigation

Investigated NPU/QNN/NNAPI/Vulkan support for Android:

| Backend | Status | Recommendation |
|---------|--------|----------------|
| **CPU** | ✅ Stable baseline | Default for debug builds |
| **Vulkan** | ⚠️ Supported by ggml but disabled in debug builds while vendored headers are wired up | Next realistic GPU step |
| **NNAPI** | ⚠️ Supported but deprecated in Android 15 | Not recommended for new development |
| **QNN/Hexagon NPU** | ⚠️ Experimental, requires fork ggml-hexagon | Future, high maintenance cost |

Current build behavior:
- `handy-core/Cargo.toml`: `transcribe-cpp` is built **without** the `"vulkan"` feature by default.
- `app/build.gradle.kts`: `GGML_VULKAN=OFF` for debug builds; `GGML_VULKAN=ON` for release builds (with vendored SPIRV/Vulkan include paths).
- `scripts/build-rust.sh`: default is CPU-only; pass `--vulkan` to enable GPU build.

To re-enable Vulkan fully:
1. Add a `vulkan` feature to `handy-core` that enables `transcribe-cpp/vulkan`.
2. Make Gradle pass `--features vulkan` only for release builds.
3. Fix the `CMAKE_CXX_FLAGS` quoting issue so the vendored `Vulkan-Headers/include` path is picked up.
4. Test on a Vulkan-capable device and verify `Backend::Auto` selects GPU in logcat.

---

## 🟡 Open Items / Next Steps

1. **Re-enable Vulkan GPU backend**
   - Add a Cargo feature in `handy-core` to toggle `transcribe-cpp/vulkan`.
   - Fix include path passing for `vulkan/vulkan.hpp` (consider a CMake toolchain file).
   - Verify release build and test on a Vulkan-capable device.

2. **Investigate QNN/Hexagon NPU further**
   - Evaluate `zhouwg/ggml-hexagon` fork feasibility.
   - Determine if maintenance cost is justified by target device base.

3. **IME visual verification**
   - Capture screenshots of the IME in each state (idle, loading, recording, transcribing, confirm, error).
   - Verify 48.dp touch targets and MD3 shapes.

4. **Onboarding visual verification**
   - Capture screenshots of each onboarding step after a clean install.

---

## 🔧 Quick Reference

### Build & Install

```bash
cd handy-android
./gradlew clean assembleDebug
adb -s <device> uninstall com.handy.app.debug
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
```

### Automated Test Flow

```bash
# Run the full automated flow (build, install, grant, launch, download, activate, verify)
./scripts/adb_test_flow.sh <device_serial> <model_id>

# Example:
./scripts/adb_test_flow.sh adb-00143154F001971-AbAnvz._adb-tls-connect._tcp canary-180m-flash-Q4_K_M
```

### Manual ADB Commands

```bash
# 1. Grant permission
adb -s <device> shell pm grant com.handy.app.debug android.permission.RECORD_AUDIO

# 2. Launch app, skipping onboarding
adb -s <device> shell am start -n com.handy.app.debug/com.handy.app.MainActivity --ez skip_onboarding true

# 3. Download a lightweight model
adb -s <device> shell am broadcast -a com.handy.app.action.DOWNLOAD_MODEL --es model_id canary-180m-flash-Q4_K_M

# 4. Set it as active
adb -s <device> shell am broadcast -a com.handy.app.action.SET_ACTIVE_MODEL --es model_id canary-180m-flash-Q4_K_M
```

### Useful Logcat Filters

```bash
adb -s <device> logcat -d | grep -E '(HandyApp|EngineVM|handy-core|TestCommandReceiver|canary)'
```

### Run Tests & Lint

```bash
cd handy-android
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```
