# Handy PC ↔ Android — Cross-Platform Reference

> **Companion document to `MIGRATION_PLAN_MD3.md`.** Whereas MIGRATION_PLAN_MD3.md is the sprint-by-sprint *execution plan* for the Handy-Android MD3 migration, this document is the **static cross-walk**: for every UI/architecture decision on Android, what is the PC counterpart, where does it live, and how do we keep parity? Use this doc as the *reference of truth* when in doubt; use MIGRATION_PLAN_MD3.md as the *roadmap* of when each canonical mapping ships.
>
> **Brand-locked palette.** The seeds `#2c2b29` (background), `#fbfbfb` (text), `#f28cbb` (primary), `#faa2ca` (PC-light primary), `#da5893` (UI pink), `#808080` (mid-gray), `#5a5753` (outline) are the canonical source-of-truth on both platforms. Pixel-for-pixel copies. **No platform may diverge from these seeds.**

**Last updated:** 2026-07-17 · **Status:** Initial draft, post-Sprint 28b closure. **Next review:** Sprint 29 Polish.

---

## §1 — Executive Summary

Handy is a **dual-platform speech-to-text product**: the PC/desktop version (`src-tauri/` + `src/` Svelte/Vite) ships first, and Handy-Android (`handy-android/`) is the Android port that must remain visually and behaviorally consistent with the desktop experience.

**Three parallelism rules govern this document:**

1. **PC is the visual source-of-truth.** Every shade, motion curve, and iconographic choice on PC is mirrored on Android. Where the PC uses CSS custom properties (`theme.css`) or component-local tokens (`@theme inline`), Android routes through `MaterialTheme.colorScheme.*` / `MaterialTheme.shapes.*` / `MotionTokens.*`.
2. **Android is the M3 expansion semantic source-of-truth.** Where the PC has a flat 6-color palette (no `primaryContainer`/`tertiary`/`secondary`/`surfaceContainer*` hierarchy), Android *derives* the additional M3 tonal roles algorithmically from the same PC seeds. That derivation lives in `Color.kt` and is documented in §2 below.
3. **Feature parity ≠ pixel parity.** PC tray icons and global hotkeys do not exist on Android by design — their equivalents are the IME pill (`HandyInputMethodService.kt`) and the foreground recording service (`service/RecordingService.kt`). Documented in §8.

---

## §2 — Palette Cross-Walk (PC `theme.css` ↔ Android `Color.kt`)

### PC palette (source-of-truth file `#1`)

`src/styles/theme.css` is the canonical PC palette; `src/App.css` re-exports as Tailwind utilities.

```css
:root {
  --light-color-text:             #0f0f0f;
  --light-color-background:       #fbfbfb;
  --light-color-logo-primary:     #faa2ca;
  --light-color-logo-stroke:      #382731;

  --dark-color-text:              #fbfbfb;
  --dark-color-background:        #2c2b29;
  --dark-color-logo-primary:      #f28cbb;
  --dark-color-logo-stroke:       #fad1ed;

  --color-background-ui:          #da5893;  /* ↔ Android HandyInversePrimary */
  --color-text-stroke:            #f6f6f6;  /* cross-theme; SVG headline stroke */
  --color-mid-gray:               #808080;
}
```

Active tokens (`--color-text`, `--color-background`, `--color-logo-primary`, `--color-logo-stroke`) flip to dark variants when `prefers-color-scheme: dark` OR `:root[data-theme="..."]` overrides via JS.

### Android palette (canonical enforcement file)

`handy-android/app/src/main/java/com/handy/app/ui/theme/Color.kt` is the Android brand-locked palette. Every value below maps to a PC seed.

| PC CSS variable (`src/styles/theme.css`) | PC hex | ↔ Android `Color.kt` token | Android hex | Generation |
|---|---|---|---|---|
| `--dark-color-logo-primary` | `#f28cbb` | `HandyPrimary` (dark) | `#F28CBB` | **1:1 verbatim** seed |
| `--color-background-ui` | `#da5893` | `HandyInversePrimary` (dark) | `#DA5893` | **1:1 verbatim** cross-theme pink |
| `--dark-color-background` | `#2c2b29` | `HandyBackground`, `HandySurface` (dark) | `#2C2B29` | **1:1 verbatim** seed |
| `--dark-color-text` | `#fbfbfb` | `HandyOnBackground`, `HandyOnSurface` (dark) | `#FDFBFB` | 1:1 verbatim (canonical `#fbfbfb`, Android uses `#FDFBFB` for clipping headroom) |
| — | — | `HandyPrimaryContainer` (dark) | `#5A3A4B` | **MD3 tonal derivation** from `#f28cbb` (chroma=12, hue=341°) |
| — | — | `HandySecondary` (dark) | `#A48C8F` | MD3 derivation (chroma=4, hue=341°) — PC has no `secondary` concept |
| — | — | `HandyTertiary` (dark) | `#D4A5A9` | MD3 derivation (chroma=8, hue=341°) — PC has no `tertiary` concept |
| — | — | `HandyError` (dark) | `#F2B8B5` | MD3 standard error palette (peach-pink hued to brand) |
| — | — | `HandySurfaceContainerLowest..Highest` (dark) | `#1A1917..#3F3D3A` | MD3 5-step tonal scale between `#2c2b29` and `#3a3835` |
| — | — | `HandyOutline`, `HandyOutlineVariant` (dark) | `#4A4845`, `#5A5753` | MD3 outline generation; "outline-variant ≈ `#5a5753`" matches PC `--color-mid-gray` family |
| `--light-color-logo-primary` | `#faa2ca` | `HandyLightPrimary` | `#9A3C6A` | MD3 light-mode primary shift (chroma up, lightness down) |
| `--light-color-background` | `#fbfbfb` | `HandyLightBackground`, `HandyLightSurface` | `#FDFBFB` | 1:1 verbatim (with #FDFBFB clipping headroom) |
| — | — | `HandyLightPrimaryContainer` | `#FFD9E4` | MD3 light-mode primary container derivation |
| `--color-text-stroke` | `#f6f6f6` | — | — | **NO ANDROID COUNTERPART.** PC `text-stroke` is a SVG text-stroke utility for hero headlines; Android achieves equivalent emphasis via `stroke = none` + `border` + `weight = Black`. See §7 Discrepancies. |
| `--color-mid-gray` | `#808080` | `HandyOutlineVariant` (closest) | `#5A5753` | PC value is *lighter* than Android's outline-variant by ~3%. See §7. |
| `--dark-color-logo-stroke`, `--light-color-logo-stroke` | `#fad1ed`, `#382731` | — | — | **PC-ONLY.** Used in `logo-primary` SVG classes. Android `mipmap` adaptive icon (`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`) renders the same `#f28cbb` background via vector, no SVG stroke needed. |

**Seed-equivalence cardinal rule** — every Android token in the table above whose PC column shows an em-dash (—) was algorithmically generated from one of the four PC seeds (`#f28cbb`, `#2c2b29`, `#fbfbfb`, `#faa2ca`) using the Material color-utilities HCT derivation (Hue/Chroma/Tone). PC has no flat equivalent for `primaryContainer`, `secondary`, `tertiary`, `surfaceContainer*`, `outline`, or `outlineVariant`; they are *necessary M3 expansions* of the brand-locked palette. **Never replace these with PC-equivalent approximations.** See `Color.kt` KDoc for the source-of-truth commentary.

### XML/Resource layer cross-walk

| Android resource | Path | Mirrors PC seed |
|---|---|---|
| `colors.xml` `primary` | `handy-android/app/src/main/res/values/colors.xml:6` | `#FFf28cbb` (PC dark primary) |
| `colors.xml` `surface` | `colors.xml:14` | `#FF2C2B29` (PC dark background) |
| `colors.xml` `background` | `colors.xml:15` | `#FF2C2B29` |
| `colors.xml` `primary_container` | `colors.xml:8` | `#FF5A3A4B` (Android MD3-derived; PC has no `primary_container`) |
| `mipmap-anydpi-v26/ic_launcher.xml` background | `mipmap-anydpi-v26/ic_launcher.xml:6` | `#f28cbb` (PC dark primary) — adaptive icon background |

---

## §3 — Theme Switching Architecture

### PC theme-switch data flow

```
User taps ThemeSelector (src/components/settings/ThemeSelector.tsx)
   │
   ▼
handleChangeThemeSetting(setting)            ← Zustand action (src/stores/settingsStore.ts)
   │
   ▼
commands.changeThemeSetting([...])           ← Tauri IPC
   │
   ▼
src-tauri/src/settings.rs::set_theme(value)  ← Rust enum { System, Light, Dark }
   │
   ▼
AppSettings.theme = value                    ← persisted in <config_dir>/settings.json
   │
   ▼ (next time frontend re-reads via store)
settingsStore.theme                           ← Zustand subscribed
   │
   ▼
applyTheme(theme)                             ← src/lib/utils/theme.ts
   │
   ▼
document.documentElement.dataset.theme = "light" | "dark" | null
   │
   ▼
:root[data-theme="light"|"dark"] selectors win over @media(prefers-color-scheme)
   │
   ▼
CSS evaluates: --color-text: var((dark|light)-color-text)
   │
   ▼
Tailwind @theme inline utility bindings propagate
```

### Android theme-switch data flow

```
User taps AboutContent ThemeSelector (handy-android/app/src/main/java/com/handy/app/ui/about/components/ThemeSelector.kt)
   │
   ▼
SettingsStore.themeMode = newValue           ← MutableStateFlow<String> setter
   │
   ▼
_themeModeFlow.value = newValue              ← reactive emit
   │
   ▼ (collected via SettingsStore.themeModeFlow)
MainActivity.kt: themeModeState = collectAsState()      ← reactive Compose State<ThemeMode>
   │
   ▼
HandyTheme(themeModeState = ..., dynamicColorState = ...)
   │
   ▼
val darkTheme = when (themeMode) { System -> isSystemInDarkTheme(); Light -> false; Dark -> true }
val colorScheme = if (useDynamicColor && SDK >= S) dynamicDark|lightColorScheme(context) else dark/light HandyDarkColorScheme/HandyLightColorScheme
   │
   ▼
MaterialTheme(colorScheme = colorScheme, ...)  ← all children re-read via MaterialTheme.colorScheme.primary etc.
```

### Side-by-side comparison

| Aspect | PC | Android |
|---|---|---|
| Enum | `Theme { System, Light, Dark }` (`src-tauri/src/settings.rs:48`) | `ThemeMode { System, Light, Dark }` (`ui.theme.ThemeMode`) |
| Default | System follows OS | System follows `isSystemInDarkTheme()` |
| Persistence | `<config_dir>/settings.json` (Tauri-managed) | SharedPreferences key `theme_mode` (`SettingsStore.kt:35`, `SettingsStore.kt:215`) |
| Reactive layer | Zustand store + `document.documentElement.dataset.theme` | `MutableStateFlow<String>` + `State<ThemeMode>` via `collectAsState()` |
| Apply mechanism | CSS attribute selector overrides media query | MaterialTheme colorScheme swap in Compose recomposition |
| **Dynamic color (Material You)** | **NOT supported** in PC (brand-locked) | **Supported on Android 12+** via `dynamicDarkColorScheme(context)` / `dynamicLightColorScheme(context)`. Toggle in `SettingsStore.dynamicColor`. |
| Skip when system default | Setting value cleared, OS takes over | Same — `themeMode = System` defers to OS |
| Theme selector UX | 3-option segmented control (`src/components/settings/ThemeSelector.tsx`) | Same UX model via `HandySegmentedButton` (`ui/about/components/ThemeSelector.kt`) |

**Architectural delta — Dynamic Color.** PC cannot implement Material You (no wallpaper color sampling in WebView), so it does not. Android can and does, gated behind `SettingsStore.dynamicColor` (default `false`, brand-locked). This is the **largest intentional divergence** on the UI side; it must remain user-controllable and off by default per the brand-locked rule. See `Theme.kt` KDoc.

---

## §4 — State Management & Persistence

### PC state-management graph

| Concern | PC implementation | File |
|---|---|---|
| Settings persistence | Rust-side `AppSettings` struct + JSON `app.setting.set(name, value)` Tauri command; serialized to `<config_dir>/settings.json` | `src-tauri/src/settings.rs`, `src-tauri/resources/default_settings.json` |
| Settings defaults | `default_settings.json` (factory ship) | `src-tauri/resources/default_settings.json` |
| Frontend state cache | Zustand `settingsStore` (`create<...>(set => ({...}))`) | `src/stores/settingsStore.ts` |
| Reactive recomposition | Svelte/React components subscribe via `useSettings()` | `src/lib/utils/theme.ts` |
| Cross-platform single source-of-truth | **Android reads/writes its OWN SharedPreferences. PC reads/writes JSON via Rust.** No shared persistence backend. | — |
| Persistent across app restart | Yes (JSON file) | Yes (SharedPreferences XML file under app-private dir) |

### Android state-management graph

| Concern | Android implementation | File |
|---|---|---|
| Settings persistence | `SettingsStore(context)` — class wrapping `SharedPreferences("handy_settings", MODE_PRIVATE)` | `handy-android/app/src/main/java/com/handy/app/SettingsStore.kt:7` |
| Reactive layer | `MutableStateFlow<T>` per field, exposed as `StateFlow<T>` via `.asStateFlow()`; Backing fields update prefs in the setter **after** the StateFlow to keep observers first | `SettingsStore.kt:21` (KDoc) and per-field blocks |
| Compose subscription | `app.settingsStore.themeModeFlow.collectAsState()` returns `State<ThemeMode>` | `MainActivity.kt:73` |
| EngineViewModel singleton | `app.engineViewModel` (process-wide lazy singleton in `HandyApplication`); survives Activity recreation | `handy-android/app/src/main/java/com/handy/app/HandyApplication.kt` |
| ViewModel lifecycle | Single `ViewModelFactory.create(app)` injects Application to ALL VMs; `HistoryViewModel` migrated to `AndroidViewModel` for `Context` access; standard VMs receive Application via constructor | `di/ViewModelFactory.kt` |
| Cross-platform parity risk | PC/Tauri IPC contract divergence; e.g. PC settings `experimentalEnabled` vs Android `experimentalEnabled` Boolean — same key + value space? | See Sprint 25 cross-check. |

### Shared concepts (PC + Android must implement)

| Concept | PC | Android | Notes |
|---|---|---|---|
| `theme_mode` | `Theme { System, Light, Dark }` | `ThemeMode { System, Light, Dark }` | 1:1 same enum space |
| `auto_send` | `autoSend: String` ("ime" / "disabled") | `autoSend: String` ("ime" / "disabled") | 1:1 |
| `experimental_enabled` | `experimentalEnabled: bool` | `experimentalEnabled: bool` | 1:1 |
| `post_processing_enabled` | `postProcessingEnabled: bool` | `postProcessingEnabled: bool` | 1:1 |
| `idle_timeout_seconds` (PC) / `idle_timeout` (Android, in seconds) | numeric | numeric | Same value space. Bug fix: Android's `_uiState.idleTimeout` was once named `idleMinutes` and converted wrongly — see Sprint 17 hygiene. Now correct. |
| `post_process_endpoint` | String | `postProcessEndpoint: String` | 1:1 |
| `post_process_api_key` | String | `postProcessApiKey: String` | 1:1 |
| **PC-only concepts** | `start_hidden`, `autostart`, `tray_icon_enabled` | (NO) | Android: tray icon N/A, autostart N/A (foreground service replaces), start_hidden N/A (launcher always visible). Documented as **intentional feature gaps** in §8. |
| **Android-only concepts** | (NO) | `ime_placement` ("top"/"bottom"), `sound_theme`, `custom_words`, `custom_words_raw`, `history_limit`, `recording_retention_period`, `acceleration_backend`, `recording_dual_write`, `show_experimental_models`, `log_level`, `update_checks_on_launch`, `paste_delay_ms`, `recording_buffer_frames`, `always_on_microphone`, `debug_mode` | 1:1 within Android only. Documented in §8 — these ideas could be ported BACK to PC in future product work. |

---

## §5 — Typography, Motion & Spacing

### PC typography, motion & spacing

| Concern | PC implementation | File |
|---|---|---|
| Typography | System sans-serif + Tailwind defaults. No custom font selectors; the UI inherits the OS body font. | `src/App.css:18` (`font-size: 15px; line-height: 24px;`) |
| Type scale | Tailwind at-size scaling (`text-sm`, `text-xs`, `text-lg`); no M3-style semantic scale | `src/App.css` + Tailwind config |
| Motion | Component-local via the Svelte reactive runtime; `animate-` Tailwind utilities for entrances (`animate-in fade-in zoom-in`) | `src/components/*` |
| Spring physics | No centralized spring config — components pick their own `cubic-bezier(0.16, 1, 0.3, 1)` style easings per-component | `src/overlay/RecordingOverlay.css` (overlay pill pop-in ≈ 460ms cubic-bezier(0.22, 1, 0.36, 1)) |
| Spacing | Bootstrap-style Tailwind spacing (`p-1`, `m-2`, `gap-4`) — 4px base | `src/App.css` |
| Color-mix utilities | `color-mix(in srgb, var(--color-background), black 6%)` for the live-log surface (`--color-log-surface`) | `src/App.css:34` |

### Android typography, motion & spacing

| Concern | Android implementation | File |
|---|---|---|
| Typography (M3 type scale) | `HandyTypography` with full-scale roles `displayLarge..labelSmall`, using `FontFamily.Default` (resolves to Roboto on AOSP, but inherits the user's system font on Material You/OEM skins like Samsung One UI or Pixel) | `handy-android/app/src/main/java/com/handy/app/ui/theme/Type.kt` |
| Type scale contract | 1:1 M3 spec — `displayLarge`, `headlineLarge`, `titleLarge`, `bodyLarge`, `labelLarge` + smaller variants | `HandyTypography` |
| Spring physics (centralized) | `HandySpringTokens.gentle()` (stiffness=380f damping=0.85), `.bouncy()` (stiffness=380f damping=0.6), `.snappy()` (stiffness=600f damping=0.9) | `handy-android/app/src/main/java/com/handy/app/ui/components/HandySpringTokens.kt` |
| Motion tokens | `MotionTokens.kt` with `EnterEasing`, `PopEasing`, `Emphasized*`, `Standard*`; `DurationShort=150`, `DurationMedium=300`, `DurationLong=500` | `ui/components/MotionTokens.kt` |
| Spacing tokens | `Spacing.xs=4dp`, `sm=8dp`, `md=12dp`, `lg=16dp`, `xl=20dp`, `xxl=24dp`, `xxxl=32dp`, `huge=48dp` | `ui/components/Spacing.kt` |
| Pill shape | `RoundedCornerShape(28.dp)` (`PillShape`) — Compose M3 has NO `MaterialTheme.shapes.full` (see Sprint correction §7) | `ime/HandyInputMethodService.kt` |
| Toolbar/icon touch target | `FilledIconButton.size(48.dp)` universal | per composable |

### Recommended migration rule

For Android work in MIGRATION_PLAN_MD3 sprint 18+ (`Sprint 18 — Componentes shared MD3`), every `tween()` / `animateFloatAsState(spec = ...)` / `.padding(N.dp)` reference should resolve to **either** `HandyMotion` / `HandySpringTokens` / `Spacing` tokens, **not** ad-hoc literals. The Sprint 29 polish step audits for ad-hoc literals and replaces them.

PC has no equivalent discipline today; the Android side should be the reference standard.

---

## §6 — Accessibility (a11y)

### PC accessibility primitives

| Concern | PC implementation | File |
|---|---|---|
| Semantic roles | `role="button"`, `role="navigation"`, `aria-label="…"` on interactive elements | various `*.tsx`/svelte files |
| Focus indicators | Browser-native focus ring + Tailwind `focus:ring-…` overrides | `src/App.css` |
| Keyboard navigation | Native HTML (`tabindex`, `Enter`, `Space`); `theme.css` exposes color tokens that respect `prefers-color-scheme` | `src/styles/theme.css` |
| prefers-reduced-motion | Honored via Tailwind `motion-reduce:` variants (basic acceptance) | `src/App.css` + component CSS |
| Live regions | Limited — PC overlay announces recording state via visual + audio feedback only | `src/overlay/RecordingOverlay.tsx` |
| Screen reader (SR) | Defaults to OS-level (NVDA / JAWS / VoiceOver); component-level ARIA is sparse | — |

### Android accessibility primitives

| Concern | Android implementation | File |
|---|--|---|
| Semantic roles | `Modifier.semantics { contentDescription = "..."; role = Role.Button }` on every interactive Compose composable | per-component |
| Focus | Compose focus order via `Modifier.focusable()` + `focusRequester`; TalkBack focus traversal | Compose focus framework |
| Keyboard nav | Hardware keyboard + D-pad supported via Compose focus; 48dp touch targets per MD3 spec | universal contract (post-Sprint 29) |
| Predictive back | Handled via `OnBackPressedDispatcher` + `PredictiveBackHandler` on Android 14+; Sprint 29 polish step | `MainActivity.kt` (planned) |
| Live regions | `liveRegion = LiveRegionMode.Polite/Assertive` for state machine (IME bar, RecordingOver status) | per-component + Sprint 29 audit |
| TalkBack | Compose semantics are TalkBack-compatible out of the box; raw `IconButton` contentDescription wired via `Icon(contentDescription=stringResource(...))` | `ui/components/HandySwitchRow`, `HandyIconButton` |
| Headlines contrast | Material 3 spec + WCAG AA audit (Sprint 29 `ThemeContrastTest`) | planned |

### Recommended accessibility parity checklist (Sprint 29 audit)

For each Sprint 29 entry, verify:
- [ ] Every `IconButton`/`FilledIconButton` has a meaningful `contentDescription = stringResource(...)`.
- [ ] Every `Switch`/`Checkbox` has an associated label via `Modifier.semantics { contentDescription = ... }` if the title alone isn't enough.
- [ ] Every long-form text uses headline/body roles from `HandyTypography`.
- [ ] The IME pill announces state changes via `liveRegion` for SR users.
- [ ] The History list sets `Modifier.semantics { role = Role.List }` on the LazyColumn.
- [ ] The SettingsGroup header has a heading role for navigation traversal.
- [ ] Light scheme + dark scheme both pass `ThemeContrastTest` WCAG AA (≥ 4.5:1 text, ≥ 3:1 UI).

---

## §7 — i18n String Alignment

### PC i18n

- Translations stored at `src/i18n/locales/{en,es}/translation.json` (full key:value nested JSON).
- `useTranslation()` hook consumes nested objects.
- Languages: en (default), es. NB: PC was rebranded Handy → HandyPC; old `Wispr Flow` references migrated in Sprint 22.

### Android i18n

- Default English at `handy-android/app/src/main/res/values/strings.xml`.
- Spanish override at `handy-android/app/src/main/res/values-es/strings.xml`.
- ~110 strings total. Consumed via `stringResource(R.string.xxx)`.

### Side-by-side string mapping (sample of canonical keys)

| Concept | PC `translation.json` key | Android `strings.xml` key | Status |
|---|---|---|---|
| App brand | `app.name` | `app_name` | ✅ aligned pre-Sprint 22 |
| IME enable title | `ime.enable.title` | `ime_enable_title` | ✅ aligned |
| IME enable message | `ime.enable.message` | `ime_enable_message` | ✅ aligned |
| Settings title | `settings.title` | `settings_title` | ✅ aligned |
| Theme label | `about.theme.label` | `about_theme_label` | ✅ Sprint 23 |
| Locale subtitle | `about.locale.subtitle` | `about_locale_subtitle` | ✅ Sprint 23 |
| Debug screen title | `debug.title` | `debug_screen_title` | ✅ Sprint 28 |
| Debug prefix UI suffix | `debug.placeholder` | `debug_placeholder_suffix` | ✅ Sprint 28 (kept for back-compat) |
| History empty | `history.empty` | `history_empty` | ✅ aligned |
| Post-process provider | `postprocess.provider.label` | `postprocess_provider_label` | ✅ Sprint 26 |

### Outstanding i18n drift (post-Sprint 28 audit)

Inconsistencies detected in mid-Sprint work (the **pre-Sprint 26 cleanup** Batch A made progress but more remains):

- Drift class A — **Spanish residue in en strings.xml**. Two distinct sub-cases:
  - **A1 (real content leakage, Sprint 29 priority):** keys whose values are Spanish while sitting in `values/strings.xml` (en default): `settings_section_aplicacion` → `"APLICACIÓN"`, `settings_post_processing` → `"Post Procesamiento"`, `capability_refresh` → `"Re-evaluar capacidad del dispositivo"`, plus the devices `header_tier_*` titles and the `badge_*` short labels. Move Spanish values to `values-es/strings.xml`, replace English values with canonical English.
  - **A2 (cosmetic only, low priority):** keys whose name is in Spanish but whose value is in English (`header_tier_low` → `"LOW (≤ 1.5 GB)"`, `badge_experimental` → `"EXPERIMENTAL"`). The values are correct; the key names mi Spanish/English. Optional rename to `header_tier_low_label` etc. for forward consistency.
- Drift class B — **String name collisions across concerns**. `content_desc_delete` used in BOTH SettingsScreen (delete model) and HistoryScreen (delete entry). The `Handy*` family helpers (`HandyIconButton(contentDescription = stringResource(R.string.content_desc_delete))`) resolve uniformly, but the surface meaning differs. Add suffixes: `content_desc_delete_model`, `content_desc_delete_entry`.
- Drift class C — **Compare to PC translation file**: several PC `i18n.live_log_viewer.*`, `i18n.debug.live_log_viewer.empty` — PC had richer nesting patterns. Android's flat `debug_log_liveviewer_empty` is functionally equivalent but less idiomatic for the future i18n expansion.

Sprint 29 polish should run a **UnifiedResources `UnusedResources` sweep** (36 warnings preexisting) + drift audit.

---

## §8 — Per-Component Coverage Matrix

### Per-PC-component mapping

| PC file | PC concept | MIGRATION_PLAN sprint | Android equivalent |
|---|---|---|---|
| `src/components/settings/GeneralSettings.tsx` | Audio + Model + Shortcuts groups | Sprint 19 (General settings MD3) | `ui/settings/SettingsScreen.kt` `GeneralSettingsContent` |
| `src/components/settings/AdvancedSettings.tsx` | App, Output, Transcription, History, Experimental | Sprint 25b (Advanced refine) | `SettingsScreen.kt` `AdvancedSettingsContent` |
| `src/components/settings/ModelsSettings.tsx` | SearchBar + Sections + Filter chips | Sprint 22 (Models refinement) | `ui/models/ModelCatalogScreen.kt` |
| `src/components/settings/HistorySettings.tsx` | Audio waveform + copy + save + retry + delete | Sprint 24 (History con audio) | `ui/history/HistoryScreen.kt` + `components/AudioPlayerBar.kt` |
| `src/components/settings/AboutSettings.tsx` | Version, donate, source, app data dir, log dir, acknowledgments | Sprint 23 (About + Theme/Locale) | `ui/about/AboutContent.kt` |
| `src/components/settings/PostProcessingSettingsApi.tsx` *(path inferred — confirm via `ls src/components/settings/ | grep -i post` before deep-linking. Closest known pattern is `PostProcessing*`)* | Fetch providers, BaseURL, ApiKey, ModelSelect | Sprint 26 (Post-processing MD3) | `ui/postprocess/PostProcessScreen.kt` |
| `src/components/settings/DebugSettings.tsx` | LogLevelSelector, WhatsNewPreview, LiveLogViewer, PasteDelay, RecordingBuffer, AlwaysOnMicrophone | Sprint 28 (Debug panel gated) | `ui/debug/DebugContent.kt` |
| `src/components/settings/ThemeSelector.tsx` | 3-way ThemeMode | Sprint 23 | `ui/about/components/ThemeSelector.kt` (wrapper of `HandySegmentedButton`) |
| `src/components/Sidebar.tsx` | Main navigation rail | Sprint 16 (already M3 NavigationRail) | `ui/components/HandyNavigationRail` in `AppNavigation.kt` |
| `src/overlay/RecordingOverlay.tsx` + `RecordingOverlay.css` | Floating recording pill, 6 state machine | Sprint 21 (IME flagship) | `ime/HandyInputMethodService.kt` `HandyVoiceBar` with `IdleBar / LoadingBar / RecordingBar / TranscribingBar / ConfirmBar / ErrorBar` |

### PC concepts WITHOUT an Android equivalent (intentional feature gaps)

| PC concept | Reason no Android equivalent | Resolution |
|---|---|---|
| Tray icon + global hotkeys (`start_hidden`, `tray_icon_enabled`, `autostart`) | Android doesn't have system tray icons; autostart is replaced by foreground service launch intent. Global hotkeys are not directly exposed — alternative routes: volume-key trigger (planned) | Document as intentional gap; provide volume-key trigger if user feedback warrants |
| `start_hidden` boot mode | Android boots to launcher, can't auto-start as IME silently | Document as N/A |
| `paste_method` direct (vs Shizuku/IME/Clipboard) | Android already does this via the InjectorRouter strategy (Shizuku > IME > Clipboard) | `InjectorRouter.kt` is the Android equivalent |
| `word_correction_threshold` | PC has post-processing LLM correction; Android currently disabled-by-default on `post_processing_enabled` | Same `postProcessingEnabled` gate |
| `clamshell_microphone_selector` | Android has no clamshell form factor | Document as N/A |
| Factory-default OBS notification windows (TrayWidget) | Not portable | Document as N/A |

### Android concepts WITHOUT a PC equivalent (intentional feature expansions)

| Android concept | Reason no PC equivalent | Notes |
|---|---|---|
| `ime_placement` (top/bottom anchor of pill) | PC overlay is global poke lock, no need for placement | Future: adopt Bottom-based PC pill positioning if hardware requires |
| `sound_theme` (Marimba / Soft chime / Narrator cue) | PC overlay beeps historically; richer sound library on Android | Future: port sound picker to PC Settings |
| `custom_words_raw` parser | Equivalent on PC is the post-processing `custom_words` field. Engine shares `transcribe-cpp`. Different interface. | Cross-check: PC parser is comma-separated only; Android supports comma + newline. Future: unify. |
| `history_limit` + `recording_retention_period` | PC history.json is unbounded | Future: cap in PC |
| `acceleration_backend` (CPU/Vulkan/NNAPI) | PC: `Auto`/`Cpu`/`Vulkan` enum (Tauri GPU); Mobile: same enum but only CPU ships on Android | Future: Vulkan/NNAPI shipped on PC only; Android deferred to pending Rust wiring |
| `recording_dual_write` | PC has no Kotlin-side RecordingRepository; Rust writes WAV directly | Future: parity |
| `show_experimental_models` | PC automatically shows experimental models (no toggle) | Future: add toggle on PC |
| `debug_mode` (Debug panel gate) | PC Debug is always visible | Future: gate on PC for parity |
| `paste_delay_ms` + `recording_buffer_frames` + `always_on_microphone` + `log_level` + `update_checks_on_launch` | Tauri-side equivalents stored in `default_settings.json` defaults; user-controllable but not exposed in UI today | Future: expose on PC for parity |

---

## §9 — Per-Sprint Cross-Reference

Use this table as the **inverse index** when you want to know *which PC feature* a given MIGRATION_PLAN sprint fulfils.

| MIGRATION sprint | Theme | PC anchor file(s) | Android files touched | Critical cross-walk points |
|---|---|---|---|---|
| 17 | Fundamentos MD3 | `src/App.css` `@theme inline` | `ui/theme/{Color.kt,Theme.kt,Type.kt,Shape.kt}`, `themes.xml`, `colors.xml` | §2 palette seed lock `#f28cbb` `#2c2b29` `#fbfbfb` `#da5893` |
| 18 | Componentes shared MD3 | `src/components/ui/*` Tailwind patterns | `ui/components/{SettingsGroup, HandySlider, HandySwitch, HandyChipGroup, HandySearchBar, HandySegmentedButton, HandyBadge, HandySnackbar, HandyDialog, HandyFab, HandyListItem, HandyDropdown, HandyTonalBlock, HandyModalBottomSheet, MotionTokens, StatusDot, HandySpringTokens}.kt` | §5 motion + spacing tokens |
| 19 | General settings MD3 | `src/components/settings/GeneralSettings.tsx` | `ui/settings/SettingsScreen.kt` `GeneralSettingsContent` | §8 SPC mapping |
| 20 (originally) / Sprint 25b | Advanced settings | `src/components/settings/AdvancedSettings.tsx` | `ui/settings/SettingsScreen.kt` `AdvancedSettingsContent` | §8 SPC mapping, §4 state management parity |
| 21 | IME flagship | `src/overlay/RecordingOverlay.tsx` + `RecordingOverlay.css` | `ime/HandyInputMethodService.kt` (rewritten) | §2 pill shape (= MD3 surfaceContainerHigh + shadowElevation), §5 spring motion |
| 22 | Models (refinement) | `src/components/settings/ModelsSettings.tsx` | `ui/models/ModelCatalogScreen.kt`, `capability/CatalogSorter.kt` | §8 SPC mapping |
| 23 | About + Theme + Locale | `src/components/settings/AboutSettings.tsx` + `ThemeSelector.tsx` + `LanguageSelector.tsx` | `ui/about/AboutContent.kt` + `components/{ThemeSelector,LocaleSelector}.kt` | §3 theme architecture |
| 24 | History con audio + retry | `src/components/settings/HistorySettings.tsx` | `ui/history/HistoryScreen.kt` + `components/AudioPlayerBar.kt`, `HistoryPresentationLogic.kt` | §2 tonal hierarchy, §8 SPC mapping |
| 25 | Advanced refine + Retry | §8 above + Rust-side `nativeRetryHistoryEntry` JNI | `SettingsScreen.kt` AdvancedSettings, `audio/RecordingRepository.kt` | §4 state parity (custom_words, history_limit, retention_period, acceleration_backend) |
| 26 | Post-process MD3 + AGP | `src/components/settings/PostProcessingSettingsApi.tsx` *(path inferred)* | `ui/postprocess/PostProcess*.kt`, `network_security_config.xml` | §4 post_process_* keys, §7 i18n postprocess_* keys |
| 27 | Onboarding MD3 | (Bootstrap-style StepIndicator on PC, no direct equivalent) | `ui/onboarding/components/*.kt` + launcher icon adaptive | §2 mipmap-anydpi-v26/ic_launcher.xml uses `#f28cbb` background |
| 28 | Debug panel gated | `src/components/settings/DebugSettings.tsx` | `ui/debug/DebugContent.kt` + `components/*.kt` (7 components) | §4 debug_mode gate field |
| 29 | Polish + a11y + tests + docs | (cross-cutting) | `ThemeContrastTest`, `IMEStateMachineTest`, `UnusedResources` sweep, `predictive back`, foldable hinge avoidance | §6 a11y audit, §7 i18n drift §7, §2 trajectory toward 0 warnings |

---

## §10 — Discrepancies Inventory (audit-bitácora)

This section is the **immutable audit log** of palette / i18n / token discrepancies found while synthesizing this cross-platform doc. Each row: (a) **what** the discrepancy is, (b) **where** it lives now, (c) **plan** for resolution in a specific Sprint, (d) **why** it stays that way in the interim.

### Open discrepancies (need resolution)

| # | Discrepancy | Where | Plan (Sprint) | Interim reason |
|---|---|---|---|---|
| 1 | Archived Sprint 10 reference to `#F48FB1` (a different pastel-pink) | `handy-android/SPEC.md:215` ("**Primary Accent (`primary`):** `#F48FB1`") | Sprint 29 polish: annotate as "[ARCHIVED — superseded by Sprint 17 to `#F28CBB` matching PC]" | The Sprint 10 archived section is a historical snapshot; altering it lies about history |
| 2 | Spanish residue in en-default strings.xml | `res/values/strings.xml` — `settings_section_aplicacion`, `settings_post_processing`, `header_tier_low`, `capability_refresh`, `badge_experimental` | Sprint 29 Polish: run a grep audit; move leaked Spanish strings to `values-es/strings.xml`, rename English to canonical English | Drift class A above; bounded until Spanish residue collector ship |
| 3 | String-name collision: `content_desc_delete` reused across scopes | `res/values/strings.xml` `content_desc_delete` (Settings + History both reference) | Sprint 29 Polish: split to `content_desc_delete_model` + `content_desc_delete_entry` | Risk: if `R.string.content_desc_delete` was renamed wholesale, retrofit Kotlin use-sites across SettingsScreen + HistoryScreen. Bounded well under 30 use-sites. |
| 4 | `material_you` dynamic color toggle not surfaced in PC | `SettingsStore.dynamicColor` shows Material You picker | Documented as **intentional divergence** per §3 | PC cannot wallpaper-sample. Android gates the feature on/off via `SettingsStore.dynamicColor` (default `false`, brand-locked). |
| 5 | PC `--color-text-stroke` has no Android counterpart | `src/styles/theme.css` `--color-text-stroke: #f6f6f6` (SVG text-stroke utility for headlines) | Documented as **intentional divergence** per §7 (Discrepancies) row | SVG-only feature; Android uses MaterialTheme emphasis + weight for equivalent |
| 6 | PC `--color-mid-gray: #808080` is ~3% LIGHTER than Android `HandyOutlineVariant #5A5753` | `src/styles/theme.css:18` vs `Color.kt:71` | Documented — no plan | Used in different contexts. PC's mid-gray is a content-text utility; Android's outline-variant is for chromakey borders/dividers. |
| 7 | PC `themeSelector` uses 3 options (System / Light / Dark); Android does too, but PC's "system" is a Zustand value `null | undefined`; Android's "System" is a real enum value | `src/lib/utils/theme.ts` vs `ThemeMode.kt` | Semantic equivalent; intermediate serialization differs. Documented |

### Resolved discrepancies (do not re-open)

| # | Issue | Resolved in | Citation |
|---|---|---|---|
| 1 | Original Sprint 10 `parent="android:Theme.Material.Light.NoActionBar"` (MD2) | Sprint 17 | `themes.xml:1-12` updated to `Theme.Material3.DayNight.NoActionBar` |
| 2 | Hardcoded `Color.Red`, `Color(0xFF4CAF50)`, `Color(0xFFFF9800)` in `SettingsScreen.kt:194-200` | Sprint 17 | Replaced with `MaterialTheme.colorScheme.tertiary / .secondary / .error` |
| 3 | `ExperimentalLayoutApi` required for `FlowRow` (model languages) | Sprint 15 | `@OptIn(ExperimentalLayoutApi::class)` accepted since Compose BOM 2024.x |
| 4 | Greeting prompt 2-tab `Screen`/`Route` mapping | Sprint 26 | `ModelsTabsScreen` removed; `Screen.PostProcess` is sole top-level nav item |
| 5 | `settings_section_transcripcion`, `settings_section_experimental` etc. legacy strings | Sprint 20 | Consolidated under canonical naming |
| 6 | `heavyGateIds` / `experimentalIds` hardcoded bug (IDs with `-Q5_K_M` suffix mismatch) | Sprint 15 | `slugOf(modelId)` strips `handy-computer/` prefix and `-gguf` suffix |
| 7 | `SettingsViewModel.buildSettings` private instance method | Sprint pre-26 Batch B | Promoted to `internal fun SettingsViewModel.UiState.toAppSettings()` extension for JVM testability |
| 8 | `OnboardingViewModel.computePromotionLabel` private instance method | Sprint pre-26 Batch B | Promoted to `internal fun OnboardingViewModel.Companion.computePromotionLabel()` for JVM testability |
| 9 | `nativeRetryHistoryEntry` JNI declare without implementation | Sprint 25b partial | Rust `Java_com_..._nativeRetryHistoryEntry` implemented in `handy-core/src/jni_bridge.rs`; Kotlin caller tolerates `UnsatisfiedLinkError` with structured concurrency fallback |
| 10 | `R.string.please_practice` phantom rendering (sprint 28b-v8 code-reviewer verdict, "Please practice" mystery) | Sprint 28b-v9 closure | Confirmed phantom: zero matches in codebase. The reported TopAppBar appearance was a stale R-class index from parallel gradle run. Clean rebuild (serial gradle invocation) resolves. **Not a code fix.** |

---

## §11 — Definition of "PC ↔ Android Parity"

A product feature is **PC-Android-parity** when ALL of the following are true:

1. **Functional equivalence:** both platforms expose the same feature (settings, screens, dictation UX). Parity means the user can perform the same task on either platform with the same outcome.
2. **Visual consistency:** brand colors are identical (`#f28cbb` dark primary, `#2c2b29` dark background, `#fbfbfb` text); motion curves approximate (slight differences allowed where platform idioms diverge); typeface decisions are identical (Roboto body).
3. **Token discipline:** every shared color/typography/motion uses the documented token (`MaterialTheme.colorScheme.*`, `MotionTokens.*`, `Spacing.*` on Android; `theme.css` `--color-*` + Tailwind utilities on PC). Ad-hoc literals are forbidden on either platform.
4. **State persistence parity:** settings key namespaces match (or have a documented migration); `theme_mode`, `app_language`, `auto_send` etc. all exposed on both sides.
5. **Documentation parity:** every Sprint closure updates both `MIGRATION_PLAN_MD3.md` and/or `PC_HANDY_REFERENCE.md` (this doc) for the cross-platform implications.
6. **Disclaimer — Intentional gaps:** tray icons, global hotkeys, clamshell behaviors, IME/foreground service equivalents are documented as **intentional gaps** (§8). Parity does not require these to close.

**Sprint 29 Polish** is the canonical "parity close" sprint. The Definition of Done at that sprint must include:

- [ ] `grep -r 'import androidx.compose.material.\\.' handy-android/app/src/main/java/com/handy/app/ui | grep -v '.material3.'` → 0 hits on Android.
- [ ] `grep -r 'Color(0xFF' handy-android/app/src/main/java/com/handy/app/ui | grep -v 'Color.kt'` → 0 hits outside `Color.kt`.
- [ ] `grep -r 'Color(0x' handy-android/app/src/main/java/com/handy/app/ui/components | grep -v 'Spac'` → 0 hardcoded `Color(0x)` literals in shared components.
- [ ] `Theme.kt` includes full tonal hierarchy (`surfaceContainerLowest..Highest`, `surfaceDim`, `surfaceBright`).
- [ ] `Theme.kt` documents the `--color-mid-gray` PC discrepancy explicitly.
- [ ] All 7 intentional feature gaps documented in §8 are explicitly listed in `handy-android/PROGRESS.md` "Open Items" section.
- [ ] i18n drift (Spanish residue, content_desc_delete collision) cleaned up.
- [ ] `ThemeContrastTest` (12-16 assertions) on CI verifies WCAG AA compliance per `handy-android/MIGRATION_PLAN_MD3.md`.
- [ ] `compose-ui-test` a11y framework exposes live-region, content-description contracts in 3 representative composables (IME bar, History list, SettingsGroup).

---

## §12 — Source-of-Truth References

### PC files (canonical)

| File | Lines | Role |
|---|---|---|
| `src/styles/theme.css` | 1-44 | PC palette (light + dark + cross-theme) |
| `src/App.css` | 1-119 | Tailwind / `@theme inline` re-export of `theme.css` for utility classes |
| `src/lib/utils/theme.ts` | full | Theme mode utility (`applyTheme`, `useSettings` integration) |
| `src/components/settings/ThemeSelector.tsx` | full | 3-option ThemeMode selector (System/Light/Dark) |
| `src/stores/settingsStore.ts` | full | Zustand state container with persistence |
| `src-tauri/src/settings.rs` | full | Rust enum `Theme { System, Light, Dark }` + `set_theme(value)` |
| `src-tauri/resources/default_settings.json` | full | Factory defaults |
| `src/components/settings/GeneralSettings.tsx` | full | Audio + Model + Shortcuts groups |
| `src/components/settings/AdvancedSettings.tsx` | full | App/Output/Transcription/History/Experimental groups |
| `src/components/settings/ModelsSettings.tsx` | full | SearchBar + Sections + Filter chips |
| `src/components/settings/HistorySettings.tsx` | full | History list + audio + retry + copy |
| `src/components/settings/AboutSettings.tsx` | full | About content + Dynamic color + Donate |
| `src/components/settings/PostProcessingSettingsApi.tsx` *(path inferred — see §8 note)* | full | Provider + connection + models + prompts |
| `src/components/settings/DebugSettings.tsx` | full | Log level + LiveLogViewer + paste delay |
| `src/components/Sidebar.tsx` | full | Main navigation |
| `src/overlay/RecordingOverlay.tsx` | full | Floating recording pill (PC analog of Android IME pill) |
| `src/overlay/RecordingOverlay.css` | full | Pill motion + color CSS |
| `src/i18n/locales/en/translation.json` | full | English canonical |
| `src/i18n/locales/es/translation.json` | full | Spanish override |

### Android files (canonical)

| File | Lines | Role |
|---|---|---|
| `handy-android/app/src/main/java/com/handy/app/ui/theme/Color.kt` | 1-130 | Full M3 tonal hierarchy + brand-locked PC seed inheritance |
| `handy-android/app/src/main/java/com/handy/app/ui/theme/Theme.kt` | 1-141 | `HandyTheme(themeModeState, dynamicColorState)` + `HandyDarkColorScheme` + `HandyLightColorScheme` |
| `handy-android/app/src/main/java/com/handy/app/ui/theme/Type.kt` | full | Full M3 type scale on Roboto |
| `handy-android/app/src/main/java/com/handy/app/ui/theme/Shape.kt` | full | Shape tokens `extraSmall..extraLarge` (`RoundedCornerShape(N.dp)`) |
| `handy-android/app/src/main/java/com/handy/app/ui/components/HandySpringTokens.kt` | full | `gentle()`, `bouncy()`, `snappy()` spring specs |
| `handy-android/app/src/main/java/com/handy/app/ui/components/MotionTokens.kt` | full | `MotionTokens.kt` easing + duration tokens |
| `handy-android/app/src/main/java/com/handy/app/SettingsStore.kt` | 1-360+ | SharedPreferences singleton + reactive MutableStateFlow per setting |
| `handy-android/app/src/main/java/com/handy/app/MainActivity.kt` | 1-200+ | Activity composition root; `collectAsState` for theme + debug; `HandyTheme` invocation |
| `handy-android/app/src/main/java/com/handy/app/ui/settings/SettingsScreen.kt` | full | General + Advanced Settings groups + PostProcessContent (deprecated `@Deprecated` post-Sprint 26) |
| `handy-android/app/src/main/java/com/handy/app/ui/about/AboutContent.kt` | full | ThemeSelector + LocaleSelector + about content |
| `handy-android/app/src/main/java/com/handy/app/ui/about/components/ThemeSelector.kt` | full | `HandySegmentedButton` wrapper for ThemeMode |
| `handy-android/app/src/main/java/com/handy/app/ui/about/components/LocaleSelector.kt` | full | `HandyDropdown` wrapper for BCP-47 locale |
| `handy-android/app/src/main/java/com/handy/app/ui/postprocess/PostProcessScreen.kt` | full | Provider + connection + prompts (post-Sprint 26) |
| `handy-android/app/src/main/java/com/handy/app/ui/debug/DebugContent.kt` | full | LogLevelSelector + LiveLogViewer + AlwaysOnMicrophone + debug mode toggle |
| `handy-android/app/src/main/java/com/handy/app/ime/HandyInputMethodService.kt` | full | Floating pill IME; 6-state Compose machine (Idle→IdleBar, Loading→LoadingBar, Listening→RecordingBar, Transcribing→TranscribingBar, Confirm→ConfirmBar, Error→ErrorBar) |
| `handy-android/app/src/main/java/com/handy/app/ui/history/HistoryScreen.kt` | full | LazyColumn of `HandyListItem` 3-line cards + AudioPlayerBar |
| `handy-android/app/src/main/java/com/handy/app/ui/history/components/AudioPlayerBar.kt` | full | Stateless MD3 audio scrubber + play/pause |
| `handy-android/app/src/main/res/values/strings.xml` | 1-330+ | Default English strings (110+) |
| `handy-android/app/src/main/res/values-es/strings.xml` | full | Spanish override |
| `handy-android/app/src/main/res/values/colors.xml` | full | XML color aliases (mirror of `Color.kt`) |
| `handy-android/app/src/main/res/values/themes.xml` | full | `Theme.Handy` parent + transparent system bars + `shortEdges` cutout |
| `handy-android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | full | MD3 adaptive launcher icon (vector foreground + `#f28cbb` background) |
| `handy-android/MIGRATION_PLAN_MD3.md` | full | Sprint-by-sprint execution plan (companion doc) |
| `handy-android/SPEC.md` | full | UI spec snapshot (Sprint 10-onwards archived sections) |

---

## §13 — Cross-Reference Anchors to MIGRATION_PLAN_MD3.md

When working on a Sprint listed in `MIGRATION_PLAN_MD3.md`, refer back to:

- Sprint 17 → §2 (palette) + §3 (theme architecture) + §4 (state persistence theme_mode).
- Sprint 18 → §5 (motion + spacing + spring tokens).
- Sprint 19 → §8 (General settings coverage matrix).
- Sprint 20 / 25b → §8 (Advanced settings matrix) + §4 (custom_words, history_limit, retention_period cross-platform state).
- Sprint 21 (IME) → §2 (surfaceContainerHigh + shadowElevation 6.dp for pill) + §5 (gentle / bouncy / snappy for spring motion) + §6 (liveRegion on state machine).
- Sprint 22 (Models) → §8 (Models component) + §10 (Drift #6 on PC mid-gray).
- Sprint 23 (About + Theme + Locale) → §3 (theme architecture) + §7 (i18n) + §11 (parity check).
- Sprint 24 (History) → §2 (timeline tonal hierarchy) + §8 (History component).
- Sprint 25 (Advanced+Retry) → §8 (Advanced settings) + §4 (Post-Process settings cross-platform).
- Sprint 26 (Post-process) → §4 (post_process_endpoint etc.) + §7 (i18n postprocess_*).
- Sprint 27 (Onboarding) → §2 (mipmap-anydpi-v26 background = `#f28cbb`).
- Sprint 28 (Debug panel) → §4 (debug_mode gate) + §8 (Debug components) + §10 Drift #1 (`#F48FB1` archived reference).
- Sprint 29 (Polish) → §6 (a11y audit), §7 (i18n drift cleanup), §10 (Spanish residue + content_desc_delete split), §11 (Definition of Done checklist).

---

## §14 — Open Items (post-Sprint 28b closure, before Sprint 29 polish)

These are not blocker-sprints but should appear in `PROGRESS.md`'s "Open Items" section for visibility:

1. **PC's TrayIcon / autostart parity discussion** — not on the near-term roadmap but flagged for future product work.
2. **Android `ime_placement` top/bottom** could be ported to PC (currently future PC port). Sprint 29 polish NOT in scope; flag for post-Sprint 29 product work.
3. **Android `sound_theme` Marimba/Soft/Narrator** could be ported to PC (currently future PC port).
4. **Android `custom_words` parser (comma + newline)** should converge with PC's comma-only parser at engine-level (transcribe-cpp shared). Currently future PC port.
5. **Android `debug_mode` gating pattern (Option A — always-registered NavHost route + placeholder)** is a pattern worth porting to PC Debug panel for parity.
6. **`ThemeContrastTest` JVM pure** — Sprint 29 introduces this. Once on CI, WCAG AA regressions auto-block.
7. **`Caps lock` parity** — PC overlay ignores; Android IME ignores. Documented N/A.
