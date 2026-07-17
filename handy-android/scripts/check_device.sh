#!/usr/bin/env bash
#
# Handy Android — Idempotent ADB device presence checker.
#
# Behaviour:
#   exit 0   — at least one ADB device is connected and ready; OR no device
#              is connected but a diagnostic was printed (CI-safe).
#   exit 1   — adb itself is missing or non-functional on this host.
#
# This script is intentionally permissive so it can head gate other
# on-device scripts (adb_test_flow.sh, capture_*.sh) without ever breaking
# pure-JVM gradle pipelines (compileDebugKotlin / testDebugUnitTest).
#
# Note: we deliberately do NOT use 'set -e' here — the script ends with
# either an 'exit 0' or 'exit 1' decision that we want to be explicit about,
# and 'set -e' would suppress our early-exit paths. We DO keep 'set -u'
# (unbound-variable detection) and 'pipefail' (cascading-pipe detection).
#
# Note: we use awk + wc instead of the more idiomatic 'mapfile' to keep
# bash 3.2 compatibility (macOS default shell until macOS 11+ transition).
#
# Usage:
#   ./check_device.sh           # prints status, exits 0/1 per above
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOC="${SCRIPT_DIR}/RECONNECT_DEVICE.md"

log_ok()   { echo "[OK]    $*"; }
log_warn() { echo "[WARN]  $*"; }
log_err()  { echo "[ERROR] $*" >&2; }

# 1. adb must exist.
if ! command -v adb >/dev/null 2>&1; then
    log_err "'adb' not in PATH. Install Android platform-tools (Android SDK)."
    exit 1
fi

# 2. adb must be responsive (8s safeguard against half-dead adb-server).
ADB_LISTING="$(timeout 8 adb devices 2>&1 || true)"
if [[ "$ADB_LISTING" == *"daemon not running"* || -z "$ADB_LISTING" ]]; then
    log_warn "adb daemon not responding — attempting to start server."
    adb start-server >/dev/null 2>&1 || true
    ADB_LISTING="$(timeout 8 adb devices 2>&1 || true)"
fi

# 3. Count devices whose state is exactly "device" using awk + wc.
#    Avoid mapfile for macOS bash 3.2 compatibility.
DEVICE_COUNT="$(printf '%s\n' "$ADB_LISTING" | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"

if [[ -n "$DEVICE_COUNT" && "$DEVICE_COUNT" -gt 0 ]]; then
    log_ok "ADB device(s) available (${DEVICE_COUNT}):"
    adb devices -l | sed 's/^/         /'
    exit 0
fi

# 4. No device — print actionable diagnostic, exit 0 so CI doesn't scream.
cat <<'EOF'
[WARN]  No ADB devices connected.

To reconnect the A059 (192.168.1.36) over Wi-Fi wireless debugging:
  1. On the phone — Settings → System → Developer options
        - Enable Developer options (toggle at top, if greyed out).
        - Enable Wireless debugging.
        - On the Wireless debugging screen, tap the IP:port  (e.g. 192.168.1.36:42813).
  2. On the host (this machine):
        adb connect <phone_ip>:<phone_port>
        adb devices -l     # confirm "device" status next to the serial
  3. Re-run your script  (capture_history.sh, adb_test_flow.sh, …).

Alternative (over USB cable, then promote to TCP):
        adb tcpip 5555            # while USB-connected
        adb connect 127.0.0.1:5555
        # Now disconnect the USB cable.

EOF

if [[ -f "$DOC" ]]; then
    echo "  Full troubleshooting:    $DOC"
fi
exit 0
