# Handy Android - UI Redesign Specification (Sprint 10) — ✅ COMPLETED

## Overview
The goal of this specification was to align the Android Jetpack Compose UI with the premium aesthetic of the Handy Desktop application. This involved adopting a new dark/cream color palette with pastel pink accents, and simplifying the main navigation into a 4-item bottom navigation bar, while utilizing top tabs to group sub-sections.

**Status:** ✅ All objectives implemented, built, installed, and verified on device (A059).

## 1. Aesthetic and Theming

### Colors (implemented in `Color.kt` + `Theme.kt`)
- **Background (`background`):** `#252422` — warm dark cream.
- **Surface (`surface`):** `#2C2B29` — slightly lighter than background.
- **Primary Accent (`primary`):** `#F48FB1` — pastel pink.
- **Text/Icons (`onBackground`, `onSurface`):** `#F0EDE9` — warm white.
- **Theme:** Forced dark (`darkColorScheme`), no dynamic color.

### Shape & Typography (preserved from pre-Sprint 10)
- **Shapes:** Material 3 rounded corners (small=8dp, medium=12dp, large=16dp).
- **Typography:** HandyTypography with system sans-serif, Material 3 typescales.

## 2. Navigation Architecture

### Main Navigation (BottomNavigationBar) ✅
A `Scaffold` with `NavigationBar` at the bottom. The navigation items are:
1. **General** (⚙️ icon)
2. **Modelos** (🔧 icon)
3. **Historial** (📅 icon)
4. **Acerca de** (ℹ️ icon)

The bottom bar is hidden during the onboarding flow.

### Sub-Navigation (Tabs) ✅
- **General Section:** `TabRow` with "General" (audio, injection, battery settings) and "Avanzado" (advanced config matching desktop: APLICACIÓN → SALIDA → TRANSCRIPCIÓN → EXPERIMENTAL).
- **Modelos Section:** `TabRow` with "Modelos" (model catalog) and "Post Proceso" (LLM endpoint + API key).
- **Historial Section:** No tabs — displays past transcriptions directly.
- **Acerca de Section:** No tabs — displays version, licenses, and GitHub link.

## 3. Implementation Summary

### Files Modified (Sprint 10)
| File | Change |
|---|---|
| `Color.kt` | New dark/cream + pastel pink palette |
| `Theme.kt` | Forced dark theme, no dynamic color |
| `Screen.kt` | 4 routes (General, Models, History, About) + Onboarding |
| `AppNavigation.kt` | Bottom nav 4 items + TabRow for General and Models |
| `SettingsScreen.kt` | Added AdvancedSettingsContent (4 desktop sections), GitHub link in AboutContent |
| `MainActivity.kt` | Shared SettingsViewModel across 3 tabs |
| `SettingsStore.kt` | 5 new fields: experimentalEnabled, vadEnabled, addFinalSpace, postProcessingEnabled, autoSend |
| `AppSettings.kt` | Same 5 fields in data class |
| `SettingsViewModel.kt` | UI state + setters + buildSettings for all 5 new fields |

### Files Deleted
| File | Reason |
|---|---|
| `DictationScreen.kt` | Dead code — no route, no references. IME is the primary dictation interface |

### Build Status
- `./gradlew assembleDebug` → **BUILD SUCCESSFUL**
- **Zero warnings** (menuAnchor() deprecation fixed)
- APK installed and launched on device (A059)
