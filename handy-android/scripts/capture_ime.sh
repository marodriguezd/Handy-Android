#!/usr/bin/env bash
#
# capture_ime.sh -- per-state screenshot capture for the Handy IME pill.
#
# Resolves the SMS text-input field bounds via uiautomator dump so the
# script survives nav rearrangements (Sprint 27b adaptive launcher,
# Sprint 28b-v15 modal debug panel gated by Settings.debugMode) and
# the adaptive NavigationBar <-> NavigationRail split.
#
# Usage:
#   ./capture_ime.sh [DEVICE_SERIAL]
#
# Context (Sprint 29f refresh):
#   - Sprint 21: IME pill spec landed (6 states, BottomCenter default).
#   - Sprint 27b: Adaptive launcher icon -- not visible in IME capture
#     but the package's app icon changed; banner is unchanged.
#   - Sprint 28b-v11: DEV tools toggle feedback Snackbar -- not relevant
#     for IME capture but documented for the gate-flip path.
#   - Sprint 28c #1+#2: PostProcess and AboutContent migrated to
#     LazyColumn to defeat the Sprint 28b-v8..v14 AnimatedContent ->
#     Constraints.Infinity runtime crash cascade.
#   - Sprint 29d: motion audit complete (8/12 spring sites use tokens).
#
# Output: screensh/ime/ with ime_idle.png + (future) ime_recording.png,
# ime_transcribing.png, ime_confirm.png, ime_error.png.
#
# Note: synthetic 'input tap' from agent subprocesses hits the
# NothingLauncher gesture-nav bottom edge on A059 Android 16. Manual
# finger taps are required for reliable on-device verification; this
# script can be run in CI by replacing the SMS-text field focus step
# with a swipe-and-screencap loop.
#
set -euo pipefail

DEVICE="${1:-adb-00143154F001971-AbAnvz._adb-tls-connect._tcp}"
PKG="com.handy.app.debug"
IME="com.handy.app.debug/com.handy.app.ime.HandyInputMethodService"
OUT_DIR="$(dirname "$0")/../screenshots/ime"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

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

echo "Enabling and setting Handy as default IME..."
adb -s "$DEVICE" shell ime enable "$IME" || true
adb -s "$DEVICE" shell ime set "$IME"

echo "Opening Messages app..."
adb -s "$DEVICE" shell am start -a android.intent.action.SENDTO -d "sms:" --ez android.intent.extra.FORCE_NEW_TASK true || true
sleep 2

echo "Focusing SMS text field via uiautomator dump..."
# Sprint 28c-#1+#2 + Sprint 28b-v15 reshuffled bottom-nav but the SMS
# field position is now device-specific (1080x2392 on A059 vs emulator).
# Walk the uiautomator dump for an EditText node with hint-bearing
# bounds; fall back to integer math if dump fails.
DUMP_TMP="/.dump_ime_setup.xml"
FOCUS_X=""
FOCUS_Y=""
if adb -s "" shell uiautomator dump /sdcard/handy_ime_setup.xml >/dev/null 2>&1; then
    if adb -s "" pull /sdcard/handy_ime_setup.xml "" >/dev/null 2>&1; then
        adb -s "" shell rm /sdcard/handy_ime_setup.xml >/dev/null 2>&1
        COORDS=
        if [[ -n "" ]]; then
            NUMS=
            if [[ -n "" ]]; then
                FOCUS_X=
                FOCUS_Y=
            fi
        fi
        rm -f ""
    fi
fi

if [[ -z "" || -z "" ]]; then
    echo "[WARN] uiautomator did not yield a text-field target; falling back to integer math."
    SCREEN=
    W=""
    H=""
    if [[ -n "" && -n "" ]]; then
        # SMS compose field typically at ~88% of H on Android default SMS
        FOCUS_X=0
        FOCUS_Y=0
        echo "[INFO] Fallback coords: (, )"
    else
        FOCUS_X=540
        FOCUS_Y=2100
        echo "[WARN] Hardcoded fallback: (, )"
    fi
fi
tap "" ""
sleep 1

echo "Capturing IME idle state..."
capture "ime_idle.png" 1

echo "IME screenshots saved to $OUT_DIR"
