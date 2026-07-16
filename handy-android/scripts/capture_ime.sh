#!/usr/bin/env bash
# Capture IME screenshots for visual regression reference.
# Usage: ./capture_ime.sh [DEVICE_SERIAL]
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

echo "Focusing text field..."
# Tap near the bottom-center where the message field usually is
tap 540 2100
sleep 1

echo "Capturing IME idle state..."
capture "ime_idle.png" 1

echo "IME screenshots saved to $OUT_DIR"
