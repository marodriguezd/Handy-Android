# Sprint 29 — Polish + Accessibility + Final Lint Residual

> **Status**: PLANNED. Sprint 28b-v12 (Compose layout regression fix) closed in this branch; Sprint 28b-v13 (DeveloperToolsDisabled+DebugModeToggle for symmetric Snackbar UX) is the **pre-Sprint-29 mandatory carry-over** before (a)-(g) begin.

**Sprint 29 closes the MD3 migration plan** per the Definition of Done in `handy-android/PC_HANDY_REFERENCE.md §11`. Three operational tracks:

- **(a) + (e) + (f) + (g)** — Docs, lint sweeps, scripts. Low-risk, isolated. ~2 days.
- **(b) + (c) + (d)** — Cross-cutting behavioral + bulk refactor. Higher risk. ~3 days.
- **Pre-Sprint-29 carry-over**: Sprint 28b-v13 (developer-toggle-outside-DebugScreen + 'enabled' Snackbar symmetric UX) before any of (a)-(g). ~1 day.

## Sub-feature breakdown

### (a) WCAG AA contrast audit — `ThemeContrastTest.kt`
- **File added**: `app/src/test/java/com/handy/app/ui/theme/ThemeContrastTest.kt` (12-16 assertions)
- **Validates**: For each (foreground, background) color pair used in readable text, the contrast ratio satisfies WCAG AA ≥ 4.5:1 (body text).
- **Coverage pairs**: PC seed palette (`#f28cbb` × `#2c2b29`, light + dark), PC outline (`#5a5753` × surface), PC mid-gray on dark BG, M3 tonal hierarchy (primary/onPrimary, primaryContainer/onPrimaryContainer, secondary/onSecondary, tertiary/onTertiary, surfaceVariant/onSurfaceVariant, error/onError, etc.) for both light + dark schemes.
- **Test framework**: JUnit4 mirror of `CatalogSorterTest`/`RecordingRepositoryTest`; pure JVM (no Compose runtime). `rgbToRelativeLuminance` + `contrastRatio(fg, bg)` helpers live alongside the assertions.
- **Risk**: ⬇ (isolated test file; deterministic color math; runs in ~50ms).
- **Acceptance**: 12-16 `@Test` methods, all passing. `lintDebug` × `--warning-mode=summary` reports no new `UnusedResources` or string-resolution errors.

### (b) Predictive back gesture (Android 14+)
- **Files modified**: `AndroidManifest.xml` (`android:enableOnBackInvokedCallback="true"`); each `Screen.kt` (`SettingsScreen`, `ModelCatalogScreen`, `HistoryScreen`, `AboutContent`, `PostProcessScreen`, `DebugContent`, `OnboardingScreen`) replaces `BackHandler { onBackPressedDispatcher.onBackPressed() }` with `OnBackPressedDispatcher.addCallback` integration.
- **Behavior**: System back gesture preview shows app's destination icon (Android 14+ predictive back). Cross-screen via `androidx.activity.compose.BackHandler` integration.
- **Risk**: ⬆ (cross-cutting per-screen migration; requires Android 14+ device or emulator for verify).
- **Acceptance**: Manual verify on A059 Android 16: each screen's back button pops correctly with preview. Predictive-back affordance visible on Android 14+ emulator.

### (c) Foldable hinge avoidance — `WindowInfoTracker`
- **File modified**: `app/src/main/java/com/handy/app/MainActivity.kt` + `app/src/main/java/com/handy/app/ime/HandyInputMethodService.kt` (IME pill position).
- **Behavior**: When device is folded (posture FOLDED_TABLETOP), IME pill avoids the hinge via `WindowInfoTracker.windowLayoutInfo()`. Defaults to BottomCenter placement; switches to TopCenter when hinge covers the lower half.
- **Risk**: ◐ (small code footprint ~50 LoC each; requires foldable emulator to verify).
- **Acceptance**: Manual verify on a foldable emulator (Pixel Fold AVD); documented fallback for non-foldable devices.

### (d) Motion audit — every `tween`/`spring` consumes `MotionTokens` / `HandySpringTokens`
- **Files modified**: every `*.kt` containing `tween(`, `spring(`, `animateFloatAsState`, `animateContentSize`, `InfiniteTransition`, etc. (~12-20 files per audit; estimates after grep):
  - `app/src/main/java/com/handy/app/ui/components/MotionTokens.kt` (extend if missing constants)
  - `app/src/main/java/com/handy/app/ui/components/HandySpringTokens.kt` (extend if missing presets)
  - `app/src/main/java/com/handy/app/ime/HandyInputMethodService.kt`
  - `app/src/main/java/com/handy/app/ui/debug/DebugContent.kt`
  - `app/src/main/java/com/handy/app/ui/postprocess/PromptEditor.kt`
  - `app/src/main/java/com/handy/app/ui/onboarding/OnboardingScreen.kt`
  - `app/src/main/java/com/handy/app/navigation/AppNavigation.kt`
- **Behavior**: Bulk refactor direct magic-number durations (e.g., `tween(durationMillis = 300)`) into `MotionTokens.EnterDurationMs` etc. Direct spring specs (`spring(dampingRatio = 0.85f, stiffness = 380f)`) into `HandySpringTokens.gentle()`/`bouncy()`/`snappy()`. Where the existing spec doesn't match any preset, ADD a new constant in `MotionTokens.kt` with KDoc explaining the use case.
- **Risk**: ⬆ (bulk refactor across many files). Mitigation: per-file commits; `compileDebugKotlin` between each.
- **Acceptance**: `grep -E 'tween\(.*durationMillis|tween\(.*\(0?\d+\)|spring\(.*dampingRatio' app/src/main/java/com/handy/app -r` returns ZERO direct magic numbers (all go through tokens).

### (e) `UnusedResources` final sweep (36 → 0)
- **Audit method**: extract IDs from `app/build/reports/lint-results-debug.xml` × `UnusedResources`. For each candidate: run `grep -r 'R\.string\.X\b' app/src/main/java app/src/test/java`. If ZERO matches, delete the string + Spanish localization entry.
- **Files modified**: `app/src/main/res/values/strings.xml` (bulk deletions); Spanish localization (if exists) `app/src/main/res/values-es/strings.xml`.
- **Expected removals**: see audit. Likely candidates from prior closures: leftover `appcompat`/`core-ktx` dep bump strings, `about_*` strings not surfaced in AboutContent, `HandyListItem` migration leftovers.
- **Risk**: ◐ (careful per-string audit; risk of deleting strings consumed by `stringResource()` indirect refs). Mitigation: ALWAYS grep before delete; revisit lint after each batch.
- **Acceptance**: `lintDebug` reports 0 `UnusedResources` entries.

### (f) Refresh `capture_ime.sh` + `capture_onboarding.sh`
- **Files modified**: `handy-android/scripts/capture_ime.sh`, `handy-android/scripts/capture_onboarding.sh`.
- **Behavior**: Update device-target to `192.168.1.36:40241` (or `env DEVICE` override). Use **absolute-class component target** (`-n com.handy.app.debug/com.handy.app.MainActivity`) per Sprint 28b-v10 lesson learned. Re-record IME pill in all 6 states (IDLE / LOADING / LISTENING / TRANSCRIBING / CONFIRM / ERROR) + onboarding flow steps.
- **Risk**: ⬇ (script-only).
- **Acceptance**: Both scripts execute cleanly on A059 (192.168.1.36:40241). Output PNG paths under `/tmp/handy_shots/{ime,onboarding}/01_*.png` are valid (file size > 50 KB; uiautomator dump captures expected text).

### (g) Comprehensive MD3 migration plan (this doc + cross-link)
- **File added**: this `handy-android/SPRINT_29_PLAN.md` (you're reading it).
- **Behavior**: Consolidates the 7 sub-features, file targets, ordering, and Definition of Done checklist. Cross-links to `handy-android/MIGRATION_PLAN_MD3.md` § "🛠 Corrección suplementaria — Plan ejecutable 2026-07-17 (post-Sprint 24)" and `handy-android/PC_HANDY_REFERENCE.md §11`.
- **Risk**: ⬇ (docs-only).
- **Acceptance**: New user agents reading this doc can pick up the 7 sub-features in the recommended order without re-reading the full AGENTS.md history.

## Pre-Sprint-29 commit-pairing (recommended)

Per AGENTS.md Plan-D, commit in this order:

1. **Sprint 28b-v13 first** (carry-over): add `DebugModeToggle` inside `DeveloperToolsDisabled` + 1-2 JVM tests. ~1 day.
2. **Sprint 29a** (sub-features `a` + `e` + `f` + `g`): docs + tests + scripts — low-risk, can ship fast. ~1.5 days.
3. **Sprint 29b** (sub-features `b` + `c` + `d`): cross-cutting + bulk refactor — needs staged rollout. ~2-3 days.

Each commit pairs a submodule-side change (if any) with the parent `AGENTS.md` + `PROGRESS.md` closure log entry, per the AGENTS.md Plan-D convention.

## Definition of Done for Sprint 29 (per `PC_HANDY_REFERENCE.md §11`)

- ✅ `ThemeContrastTest` passes with 12+ assertions covering all material text pairs.
- ✅ All `*.kt` source contains MotionTokens reference (no direct `tween(`/`spring(` magic numbers).
- ✅ Predictive back gesture works in Settings, Models, History, About, Post-Process, Debug, Onboarding screens.
- ✅ Foldable-mode test passes on emulator (posture manipulation).
- ✅ `UnusedResources` lint reports 0 entries (`lintDebug` ≤ 9 total warnings).
- ✅ `capture_ime.sh` + `capture_onboarding.sh` execute cleanly on A059 with the latest APK.
- ✅ `PC_HANDY_REFERENCE.md §11` Definition of Done checklist: every item checked.

## Final lint target: ~9 residuals (down from current ~75)

After Sprint 29 closes, expected residuals:

| Category | Count | Justification |
|---|---|---|
| `mipmap-anydpi-v26` | 1 | Structural folder convention; carried forward indefinitely. |
| `PrivateApi`/`DiscouragedPrivateApi` | 0-3 | Shizuku Android 16 reflection; investigate via `tools:targetApi` if bumped. |
| `OldTargetApi` | 1 | AGP bump required to drop this (deferred to Sprint 26b / beyond Kotlin 2.0). |
| `GradleDependency` | 0-3 | Resolvable in Sprint 26b with AGP 9.x + Kotlin 2.0 paired migration. |
| `UnusedResources` | 0 | Sprint 29 (e) audit wipes these. |
| Documentation/spec lint | ~2 | Style nits deferred per AGENTS.md convention. |

## Carry-over notes (separate from Sprint 29 scope)

1. **Git `handy-android/` setup**: Currently a plain directory tracked by parent's git (no real submodule). The historical "submodule commit + parent commit" pairing convention works because both git operations write to the same `.git/` (since `handy-android/` lacks its own `.git/`). Investigate in a separate sprint if dual-repo commit pairings continue to be used and need proper submodule semantics.
2. **AGP 9.x + Kotlin 2.0 migration** (paired): currently on AGP 8.8.2 + Gradle 8.11.1 + Kotlin 1.9.24. The next major AGP bump unlocks Kotlin 2.0 + Compose Compiler plugin migration — both of which close ~21 lint warnings + unlock `primaryFixed*` MD3 tokens. Paired Sprint 26b/29c scope.
3. **Sprint 28b-v12 Compose regression root-cause** (educational): The post-Sprint-28b-v11 unwrap of `Modifier.fillMaxSize()` on the Scaffold is a DebugScreen-specific quirk. Future lint rule suggestion: "Scaffold inside NavHost without explicit fillMaxSize() at root" — defer to Sprint 29 polish slack.

## Reference links

- `handy-android/PC_HANDY_REFERENCE.md` §11 — Definition of Done (canonical)
- `handy-android/MIGRATION_PLAN_MD3.md` § "🛠 Corrección suplementaria — Plan ejecutable 2026-07-17 (post-Sprint 24)" — historical plan
- `AGENTS.md` (root) § "Sprint 29 (cierre) — Polish + accesibilidad + tests + docs" — top-level plan pointer
- `handy-android/PROGRESS.md` § Sprint 28b-v12 — most-recent closure log (Compose layout regression fix)

