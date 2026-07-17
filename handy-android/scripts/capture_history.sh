#!/usr/bin/env bash
#
# capture_history.sh — per-state screenshot capture for HistoryScreen.
#
# Resolves the History tab coordinates via uiautomator dump so the script
# survives nav rearrangements (Sprint 28 adds a Debug tab) and the
# adaptive NavigationBar <→ NavigationRail split. Falls back to integer
# math only when uiautomator is genuinely unavailable.
#
# Usage:
#   ./capture_history.sh [DEVICE_ID] [OUTPUT_DIR]
#
# Defaults:
#   DEVICE_ID    sole connected device (errors when more than one is
#                attached unless \$1 is given explicitly)
#   OUTPUT_DIR   /tmp/handy_shots/history
#   --seed-history : broadcast SEED_HISTORY before MainActivity launches
#                    so the MD3 HistoryScreen renders seeded entries.
#                    Pre-Sprint-26 Batch D wires this; cleared via
#                    `--seed-history 0` (or `--clear-history`).
#
# Note: we deliberately do NOT use 'set -e' here — we want explicit exits
# at the documented decision points rather than silent failures.
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Pre-Sprint-26 Batch D: parse the --seed-history flag from the tail of
# the argv so DEVICE_ID and OUTPUT_DIR positional arguments still win.
# Recognized forms:
#   --seed-history            -> defaults to 5 entries
#   --seed-history=N          -> N entries (N coerced 0..50 server-side)
#   --clear-history           -> 0 entries (clears any prior seed)
SEED_HISTORY_COUNT=0
for arg in "${@:3}"; do
    case "$arg" in
        --seed-history)        SEED_HISTORY_COUNT=5 ;;
        --seed-history=*)      SEED_HISTORY_COUNT="${arg#--seed-history=}" ;;
        --clear-history)       SEED_HISTORY_COUNT=0 ;;
    esac
done

DEVICE_ID="${1:-}"
OUTPUT_DIR="${2:-/tmp/handy_shots/history}"

# Resolve device: explicit serial wins; otherwise require exactly one to
# avoid capturing an emulator by mistake.
if [[ -z "$DEVICE_ID" ]]; then
    CONNECTED_COUNT="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
    if (( CONNECTED_COUNT == 1 )); then
        DEVICE_ID="$(adb devices | awk 'NR>1 && $2=="device"{print $1}')"
    elif (( CONNECTED_COUNT == 0 )); then
        "${SCRIPT_DIR}/check_device.sh"
        log_err "No device connected. See $(basename \"$0\") troubleshooting."
        exit 1
    else
        "${SCRIPT_DIR}/check_device.sh"
        log_err "${CONNECTED_COUNT} devices attached — pass the A059 serial as \$1 to disambiguate."
        exit 1
    fi
fi

PACKAGE="com.handy.app.debug"
ACTIVITY="${PACKAGE}/com.handy.app.MainActivity"
mkdir -p "$OUTPUT_DIR"
TMP_DUMP="${OUTPUT_DIR}/.dump.xml"

log_ok()   { echo "[OK]    $*"; }
log_info() { echo "[INFO]  $*"; }
log_warn() { echo "[WARN]  $*"; }
log_err()  { echo "[ERROR] $*" >&2; }

log_info "Device: $DEVICE_ID"
log_info "Output: $OUTPUT_DIR"

# Permissions + launch (skip_onboarding shortcuts to the catalog screen).
adb -s "$DEVICE_ID" shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
adb -s "$DEVICE_ID" logcat -c

# Pre-Sprint-26 Batch D: optionally seed the synthetic history BEFORE
# launching MainActivity, so HistoryViewModel.loadMore() picks up the
# injected entries on its first pass. The flag --seed-history without
# a value means "default 5 entries".
if (( SEED_HISTORY_COUNT > 0 )); then
    adb -s "$DEVICE_ID" shell am broadcast \
        -a com.handy.app.action.SEED_HISTORY \
        -n "${PACKAGE}/com.handy.app.TestCommandReceiver" \
        --ei count "$SEED_HISTORY_COUNT" >/dev/null 2>&1 || true
    log_ok "SEED_HISTORY broadcast sent (count=$SEED_HISTORY_COUNT)."
fi

adb -s "$DEVICE_ID" shell am start -n "$ACTIVITY" --ez skip_onboarding true >/dev/null
log_ok "MainActivity launched."

# Wait until MainActivity gains window focus. Compose first-compose on a
# cold install can take 4-6s; running uiautomator dump before that
# captures the splash rather than the rendered nav bar. Capped at 10s
# so silent hangs don't block the script indefinitely.
FOCUS_OK=0
for _ in $(seq 1 10); do
    if adb -s "$DEVICE_ID" shell dumpsys window 2>/dev/null \
            | grep -qE "mCurrentFocus={[^{}]*${ACTIVITY}[^{}]*}"; then
        FOCUS_OK=1
        break
    fi
    sleep 1
done
if (( FOCUS_OK )); then
    log_ok "MainActivity has window focus; proceeding to uiautomator dump."
else
    log_warn "MainActivity did not gain focus within 10s; proceeding anyway."
fi

# Pull uiautomator dump.
DUMP_OK=0
if adb -s "$DEVICE_ID" shell uiautomator dump /sdcard/handy_ui_dump.xml >/dev/null 2>&1; then
    if adb -s "$DEVICE_ID" pull /sdcard/handy_ui_dump.xml "$TMP_DUMP" >/dev/null 2>&1; then
        adb -s "$DEVICE_ID" shell rm /sdcard/handy_ui_dump.xml >/dev/null 2>&1
        DUMP_OK=1
    fi
fi
if (( ! DUMP_OK )); then
    log_warn "uiautomator dump failed; will fall back to integer math."
fi

# Resolve the parent <node …> bounds that wraps a descendant line whose
# text or content-desc contains 'history' (case-insensitive). Material 3
# NavigationBarItem renders as a <View> wrapper containing a <TextView>
# that carries `text="History"` — without bounds of its own in some
# builds. Walking the dump with awk and remembering the most recent
# bounds on a `<node …>` open tag lets us recover the parent wrapper
# bounds, which is what we want for the tap.
#
# Output: bounds attr (e.g. bounds="[549,2148][805,2336]") or empty.
HISTORY_BOUNDS=""
if (( DUMP_OK )); then
    HISTORY_BOUNDS="$(awk '
        function extract_bounds(s) {
            if (match(s, /bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/)) {
                return substr(s, RSTART, RLENGTH)
            }
            return ""
        }
        {
            if (match($0, /<node /)) {
                last_bounds = extract_bounds($0)
            }
            if (match(tolower($0), /(content-desc|text)="[^"]*history[^"]*"/)) {
                if (last_bounds != "") {
                    print last_bounds
                    exit 0
                }
            }
        }
    ' "$TMP_DUMP")"
fi

HISTORY_X=""
HISTORY_Y=""
if [[ -n "$HISTORY_BOUNDS" ]]; then
    NUMBERS="$(echo "$HISTORY_BOUNDS" | grep -oE '[0-9]+')"
    if [[ -n "$NUMBERS" ]]; then
        CX_CY="$(printf '%s\n' "$NUMBERS" \
            | awk 'NR==1{x1=$1} NR==2{y1=$1} NR==3{x2=$1} NR==4{y2=$1} END{printf "%d %d\n", (x1+x2)/2, (y1+y2)/2}')"
        HISTORY_X="$(echo "$CX_CY" | cut -d' ' -f1)"
        HISTORY_Y="$(echo "$CX_CY" | cut -d' ' -f2)"
    fi
fi

if [[ -n "$HISTORY_X" && -n "$HISTORY_Y" ]]; then
    log_info "History tap target via uiautomator: (${HISTORY_X}, ${HISTORY_Y})"
    rm -f "$TMP_DUMP"
else
    # Preserve the dump for debugging when matching fails.
    if [[ -f "$TMP_DUMP" ]]; then
        TS="$(date +%s)"
        KEPT_DUMP="${OUTPUT_DIR}/.dump.failed.${TS}.xml"
        mv "$TMP_DUMP" "$KEPT_DUMP"
        log_warn "Kept dump for inspection: $KEPT_DUMP"
    fi
fi

# Fallback to integer math (less reliable) when uiautomator did not yield
# a target — e.g. History tab masked behind a sheet, custom UI for the
# build flavour, etc. Used only as a last resort.
if [[ -z "$HISTORY_X" || -z "$HISTORY_Y" ]]; then
    log_warn "uiautomator did not yield a History target — falling back to integer math."
    SCREEN="$(adb -s "$DEVICE_ID" shell wm size | grep -oE '[0-9]+x[0-9]+' | head -1)"
    W="$(echo "$SCREEN" | cut -dx -f1)"
    H="$(echo "$SCREEN" | cut -dx -f2)"
    if [[ -z "$W" || -z "$H" ]]; then
        log_err "Could not determine screen size."
        exit 1
    fi
    if (( W >= 600 )); then
        # Material 3 NavigationRail: top-justified with system-bars offset.
        # 3rd of 4 items at ~30% of H (top padding ~3% + 3 items of ~9% each).
        HISTORY_X=$(( W / 12 ))
        HISTORY_Y=$(( H * 30 / 100 ))
        log_info "Fallback (NavigationRail): ($HISTORY_X, $HISTORY_Y)"
    else
        # Compact NavigationBar: bottom strip, 3rd of 4 slots.
        HISTORY_X=$(( W * 5 / 8 ))
        HISTORY_Y=$(( H * 94 / 100 ))
        log_info "Fallback (NavigationBar): ($HISTORY_X, $HISTORY_Y)"
    fi
fi

# Capture helper with a single retry on a bad/empty screencap.
take_shot() {
    local label="$1"
    local file="${OUTPUT_DIR}/${label}.png"
    adb -s "$DEVICE_ID" shell input tap "$HISTORY_X" "$HISTORY_Y"
    sleep 2
    for attempt in 1 2; do
        if adb -s "$DEVICE_ID" exec-out screencap -p > "$file" && [[ -s "$file" ]]; then
            log_ok "Captured: $file"
            return 0
        fi
        log_warn "Screencap attempt ${attempt} failed for $label; retrying after 1s."
        sleep 1
    done
    log_err "Screencap failed for $label after 2 attempts."
}

# Capture sequence — extend once Sprint 24 introduces more states:
#   02_card_expanded     03_audio_player_idle    04_audio_player_playing
#   05_retry_pending     06_delete_dialog        07_saved_star_active
take_shot "01_default"

log_info "Files written:"
ls -1 "$OUTPUT_DIR" | sed 's/^/  /'
