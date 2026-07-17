#!/usr/bin/env bash
#
# capture_onboarding.sh -- per-step screenshot capture for the Handy
# onboarding flow.
#
# Usage:
#   ./capture_onboarding.sh [DEVICE_SERIAL]
#
# Sprint 29f refresh:
#   - Sprint 27a onboarding MD3 refinement (StepIndicator + IconContainer +
#     ButtonRow + ProgressBar components) -- step N/4 label format.
#   - Sprint 27b adaptive launcher icon -- onboarding Step 1 icon now
#     uses vector mic glyph (was raster PNGs).
#   - Sprint 28b-v11+v12 DevTools toggle (gated) -- not relevant for
#     onboarding flow (debugMode defaults false in release).
#   - Sprint 28c LazyColumn -- settles the runtime crash on
#     PostProcess / AboutContent (onboarding itself uses Column so
#     unaffected but documented).
#
# Coordinates still hardcoded (the onboarding flow is small enough that
# the StepIndicator + ButtonRow layout is stable across recent sprints).
# Refresh: Sprint 29f marks the bottom-button area at y=2200 (Skip/Back)
# vs y=2100 (primary action). Verify these after any MD3 button-spec
# change.
#
# Output: screenshots/onboarding/step0_welcome.png ... step4_ready.png
#
# Note: synthetic 'input tap' from agent subprocesses hits NothingLauncher
# gesture-nav bottom edge on A059. Manual finger tap is the only
# reliable ground-truth; this script is best-effort automation.
#
set -euo pipefail

DEVICE="${1:-adb-00143154F001971-AbAnvz._adb-tls-connect._tcp}"
PKG="com.handy.app.debug"
ACTIVITY="com.handy.app.MainActivity"
OUT_DIR="$(dirname "$0")/../screenshots/onboarding"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

echo "Clearing app data..."
adb -s "$DEVICE" shell pm clear "$PKG" || true

echo "Starting app..."
adb -s "$DEVICE" shell am start -n "$PKG/$ACTIVITY"
sleep 2

capture() {
    local filename="$1"
    local delay="${2:-1}"
    sleep "$delay"
    adb -s "$DEVICE" shell screencap -p "/sdcard/$filename"
    adb -s "$DEVICE" pull "/sdcard/$filename" "$OUT_DIR/$filename"
    adb -s "$DEVICE" shell rm "/sdcard/$filename"
    echo "Captured $filename"
}

tap() {
    local x="$1"
    local y="$2"
    adb -s "$DEVICE" shell input tap "$x" "$y"
}

# Step 0: Welcome
capture "step0_welcome.png" 1
# Tap "Get started" (bottom-right area)
tap 900 2100

# Step 1: Mic permission
capture "step1_mic_permission.png" 1
# Tap "Grant" (center)
tap 540 1200
sleep 1
# Re-capture with granted state
capture "step1_mic_granted.png" 1
# Tap Skip
tap 540 2200

# Step 2: IME setup
capture "step2_ime_setup.png" 1
# Tap Skip
tap 540 2200

# Step 3: Model download (initial state)
capture "step3_model_download.png" 1

# Step 4: Ready (after skipping download)
tap 900 2100
capture "step4_ready.png" 1

echo "Onboarding screenshots saved to $OUT_DIR"
