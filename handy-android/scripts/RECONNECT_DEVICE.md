# Reconnect the A059 over ADB — Troubleshooting

> **TL;DR**: open **Settings → System → Developer options → Wireless debugging**, copy the IP:port shown, run `adb connect <ip>:<port>`. Once `adb devices -l` shows `device` status, `./scripts/check_device.sh` will report OK and `./scripts/capture_history.sh` (plus every other on-device script) will start working again.

This document is the canonical reference when `adb connect 192.168.1.36:42813` returns **No route to host**, **Connection refused**, or **Unauthorized**.

---

## 1. The A059 reference state

The A059 (serial `adb-00143154F001971-AbAnvz._adb-tls-connect._tcp`) used in past sessions lives on the development LAN at `192.168.1.36`:

| Property | Value | How to verify |
|----------|-------|---------------|
| LAN IP | `192.168.1.36` | `ping -c 1 192.168.1.36` from the host |
| Default wireless-ADB port | `42813` (Android 14+) — used historically | `adb connect 192.168.1.36:42813` |
| **Active pairing (17 julio 2026)** | `192.168.1.36:40293` | already paired via mDNS / persistent paring |
| Port volatility | **The TCP port is reassigned every time the user Re-Pairs** on the phone. Always read the current IP:port from the Wireless debugging screen before `adb connect`. | Open Wireless debugging → IP address / port |
| adb-paired persistence | Once paired with one host, persistent pairing survives Wi-Fi reconnects (Android 14+) | `adb devices -l` after the phone reboots |
| Min Android version | 11+ (wireless debugging) | n/a |
| Permissions required | The host's `~/.android/adbkey.pub` must be paired via "Pair device with pairing code" on the phone | Open Wireless debugging → tap **Pair device with pairing code** |

If the phone's LAN IP changes, you can find it quickly via:

```bash
echo '== candidates =='; for ip in 192.168.1.{1,36,42,50,100}; do ping -c1 -W1 $ip | grep -oE 'from [^ ]+' | head -1; done
echo '---'; echo '== arp alive =='; arp -a 2>/dev/null | grep -v incomplete
```

---

## 2. The four failure modes

### 2.1 `failed to connect to '…': No route to host`

The phone and the host are on the same subnet, but the **TCP port is not open** on the phone. The causes, ranked by likelihood:

1. **Wireless debugging OFF on the phone.** Settings → System → Developer options → Wireless debugging must be ON. When it is off, the adb daemon is not listening on any TCP port.
2. **Phone on a different Wi-Fi network or VLAN.** `cellular fallback` (Androids doing Wi-Fi+data) sometimes pushes an interface change.
3. Phone asleep / screen off. Android doze may suspend the adb daemon on some builds; wake & unlock, then retry.

**Action**: open Wireless debugging → copy the freshly-shown IP:port → `adb connect <ip>:<port>`. If port differs from `42813`, document it in `LIMPIA.md` and use it.

### 2.2 `Connection refused`

Phone is reachable on the LAN, port is open (Wireless debugging on) but nothing is bound to it. **The phone has dropped its stored pairing.** This happens after:

- Phone reboot on some Android 13 builds before the fix landed in 14;
- A factory reset;
- A "Reset pairing" tap on the Wireless debugging screen.

**Action**: on the phone, Wireless debugging → **Reset pairing code** → **Pair device with pairing code** → note the 6-digit code + IP:port → on the host:

```bash
adb pair <phone_ip>:<phone_port>           # enter the 6-digit code at the prompt
adb connect <phone_ip>:<phone_port>        # use the AFTER-pair IP:port shown on phone
```

### 2.3 `Unauthorized`

The phone sees a host key but does not trust it. Either:

- The host's `~/.android/adbkey.pub` was rotated and never re-paired;
- A second host (with a different key) tried to connect.

**Action**: same as 2.2 — Reset pairing code on the phone, then Run `adb pair` + `adb connect`.

### 2.4 `offline` / `unauthorized` (left of `devices -l` output)

```
$ adb devices -l
List of devices attached
192.168.1.36:42813   offline   device:brcmbtla
```

**Action**: `adb disconnect 192.168.1.36:42813 && adb connect 192.168.1.36:42813`. If still offline, kill adb-server and retry: `adb kill-server && adb start-server && adb connect …`.

---

## 3. The USB → TCP fallback (always works)

If the Wi-Fi wireless route is unstable you can fall back to a cable:

```bash
adb -s <serial> tcpip 5555         # USB still plugged in
adb connect 127.0.0.1:5555         # now Wi-Fi-style without Wireless debugging
# Unplug the USB — adb traffic keeps flowing over TCP at 127.0.0.1:5555
```

Pros: deterministic, works without toggling Developer options.
Cons: only useful while the host is physically near the phone.

---

## 4. Verifying that ADB is healthy

```bash
./scripts/check_device.sh    # idempotent: reports OK + lists devices OR tells you
                             # what to do when no device is connected.
```

If the script reports `[OK]`, you can proceed with:

```bash
./scripts/adb_test_flow.sh <device_serial> <model_id>          # build + install + test
./scripts/capture_history.sh [device_serial] [output_dir]      # screenshots of History
```

---

## 5. Per-session reconnect checklist

1. `adb devices -l`
   - **shows a device**: continue.
   - **shows no devices**: open the foldable device → Settings → System → Developer options → Wireless debugging → tap the IP:port → on the host: `adb connect <ip>:<port>`.
   - **shows `unauthorized`**: reset pairing code on the phone, then `adb pair` + `adb connect` (see §2.2/§2.3).
2. `./scripts/check_device.sh` (sanity gate)
3. `./scripts/adb_test_flow.sh <serial> <model>` if a clean install is needed.
4. `./scripts/capture_history.sh` (screenshot regression after each MD3 HUD/interaction change).
5. If anything still fails, run `adb logcat -d | grep -E 'HandyApp|EngineVM|handy-core'` for Handy-side diagnostics.

---

## 6. References

- `LIMPIA.md` — first-session setup & test commands.
- `AGENTS.md` — project conventions, sprint roadmap, and known limitations.
- Android developer docs — *Run apps on a hardware device*: <https://developer.android.com/studio/run/device>
- Android developer docs — *Connect wirelessly*: <https://developer.android.com/studio/run/device#wireless>
