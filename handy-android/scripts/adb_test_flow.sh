#!/usr/bin/env bash
#
# Reproducible ADB test flow for Handy Android (debug build).
#
# Usage:
#   ./adb_test_flow.sh [DEVICE_SERIAL] [MODEL_ID]
#
# Defaults:
#   DEVICE_SERIAL = adb-00143154F001971-AbAnvz._adb-tls-connect._tcp
#   MODEL_ID      = canary-180m-flash-Q4_K_M
#
set -euo pipefail

DEFAULT_DEVICE="adb-00143154F001971-AbAnvz._adb-tls-connect._tcp"
DEFAULT_MODEL="canary-180m-flash-Q4_K_M"

DEVICE="${1:-$DEFAULT_DEVICE}"
MODEL_ID="${2:-$DEFAULT_MODEL}"

# Derive package/activity from the debug build configuration.
PACKAGE="com.handy.app.debug"
ACTIVITY="${PACKAGE}/com.handy.app.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"

# Path resolution for sibling scripts (check_device.sh lives next door).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for terminal output.
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

run_adb() {
    adb -s "$DEVICE" "$@"
}

wait_for_device() {
    log_info "Waiting for device $DEVICE..."
    adb -s "$DEVICE" wait-for-device
}

build_apk() {
    log_info "Building debug APK..."
    cd "$(dirname "$0")/.."
    ./gradlew clean assembleDebug
}

install_app() {
    log_info "Uninstalling any existing $PACKAGE..."
    run_adb uninstall "$PACKAGE" || true

    log_info "Installing $APK..."
    run_adb install -r "$APK"
}

grant_permissions() {
    log_info "Granting permissions..."
    run_adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
    run_adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
}

launch_app() {
    log_info "Launching $ACTIVITY with skip_onboarding=true..."
    run_adb shell am start -n "$ACTIVITY" --ez skip_onboarding true
}

download_model() {
    log_info "Requesting download of model $MODEL_ID..."
    run_adb shell am broadcast \
        -a com.handy.app.action.DOWNLOAD_MODEL \
        --es model_id "$MODEL_ID" \
        "$PACKAGE"
}

poll_for_download_complete() {
    local timeout=${1:-300}
    log_info "Polling logcat for download completion (timeout=${timeout}s)..."
    local start_ts
    start_ts=$(date +%s)
    while true; do
        local now
        now=$(date +%s)
        if (( now - start_ts > timeout )); then
            log_error "Timeout waiting for download completion."
            return 1
        fi

        if run_adb logcat -d | grep -qE "Download complete: ${MODEL_ID}|Download complete.*${MODEL_ID}"; then
            log_info "Download completed for $MODEL_ID."
            return 0
        fi
        sleep 2
    done
}

set_active_model() {
    log_info "Requesting activation of model $MODEL_ID..."
    run_adb shell am broadcast \
        -a com.handy.app.action.SET_ACTIVE_MODEL \
        --es model_id "$MODEL_ID" \
        "$PACKAGE"
}

poll_for_active_model() {
    local timeout=${1:-60}
    log_info "Polling logcat for active model confirmation (timeout=${timeout}s)..."
    local start_ts
    start_ts=$(date +%s)
    while true; do
        local now
        now=$(date +%s)
        if (( now - start_ts > timeout )); then
            log_error "Timeout waiting for model activation."
            return 1
        fi

        if run_adb logcat -d | grep -qE "Active model (set to|persisted).*${MODEL_ID}"; then
            log_info "Model activation confirmed for $MODEL_ID."
            return 0
        fi
        sleep 2
    done
}

main() {
    log_info "Device: $DEVICE"
    log_info "Model:  $MODEL_ID"

    # Bail out gracefully when no ADB device is connected. We do NOT want
    # to spend 60s on `clean assembleDebug` if there is nothing to install
    # on. check_device.sh prints an actionable diagnostic and exits 0.
    if ! "${SCRIPT_DIR}/check_device.sh" >/dev/null 2>&1; then
        "${SCRIPT_DIR}/check_device.sh"
        exit 0
    fi
    if ! adb devices | grep -q "^${DEVICE}[[:space:]]\+device"; then
        "${SCRIPT_DIR}/check_device.sh"
        log_warn "Device $DEVICE not connected. Reconnect Wireless debugging (${SCRIPT_DIR}/RECONNECT_DEVICE.md) and re-run."
        exit 0
    fi

    wait_for_device
    build_apk
    install_app
    grant_permissions
    launch_app

    # Give the app a moment to initialize the engine.
    sleep 3

    download_model
    poll_for_download_complete
    set_active_model
    poll_for_active_model

    log_info "Test flow completed successfully."
}

main "$@"
