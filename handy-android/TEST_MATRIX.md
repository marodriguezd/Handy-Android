# Sprint 5 — IME + Text Injection Test Matrix

Sprint 5 introduces the Handy Input Method (IME), three-tier text injection strategy (IME → Shizuku → Clipboard), Shizuku-powered KEYCODE_PASTE injection, recording notification with actions, settings sync to the Rust engine, and comprehensive error handling for model download and dictation flows.

---

## 1. IME Injection Tests

Inject text via `InputConnection.commitText()` when the Handy IME is active and focused.

### 1.1 Google Keep (text editor)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.1.1 | Basic text insertion | Open Keep note, tap mic, dictate short phrase, tap Insert | Text appears at cursor position | ⬜ |
| 1.1.2 | Cursor position after insertion | Type "hello " manually, dictate "world", tap Insert | "helloworld " with cursor after inserted text | ⬜ |
| 1.1.3 | Newlines | Dictate "line one\nline two\nline three" | Multi-line text rendered correctly in note | ⬜ |
| 1.1.4 | Emoji / special chars | Dictate "hello 😊 world café 100%" | Emoji and special chars display correctly | ⬜ |
| 1.1.5 | Long text (>1000 chars) | Dictate or inject 1500-char paragraph | Text inserted without truncation or lag | ⬜ |
| 1.1.6 | Orientation change | Insert text, rotate device | Text preserved, cursor at correct position | ⬜ |
| 1.1.7 | App switch + return | Insert text, switch to home, return to Keep | Text still present, app not crashed | ⬜ |
| 1.1.8 | Rapid successive commits | Tap Insert 5 times quickly on same transcription | Text inserted once per tap, no duplicates or crashes | ⬜ |

### 1.2 WhatsApp (messaging)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.2.1 | Basic text insertion | Open chat, tap input field, dictate, tap Insert | Text appears in compose box | ⬜ |
| 1.2.2 | Cursor in middle of existing text | Type "Hi ", move cursor, dictate "there", tap Insert | "Hi there" rendered correctly | ⬜ |
| 1.2.3 | Newlines in message | Dictate two-line message | Both lines visible in compose box | ⬜ |
| 1.2.4 | Emoji in message | Dictate "See you 😊 tomorrow" | Emoji renders, send works | ⬜ |
| 1.2.5 | Long message | Dictate 1000+ char message | Message fits in compose box, send succeeds | ⬜ |
| 1.2.6 | Orientation change | Rotate while in Confirm state | Text preserved, Insert button works | ⬜ |
| 1.2.7 | App switch + return | Dictate, switch to another app, return | Compose box retains text, IME reconnects | ⬜ |
| 1.2.8 | Rapid successive commits | Tap Insert multiple times | No duplicate messages sent | ⬜ |

### 1.3 Chrome URL bar & text fields (browser)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.3.1 | URL bar injection | Tap URL bar, dictate "example.com" | Text appears, navigation works | ⬜ |
| 1.3.2 | Text field in web page | Focus text field on webpage, dictate text | Text inserted into web form field | ⬜ |
| 1.3.3 | Newlines in textarea | Focus `<textarea>`, dictate multi-line text | All lines inserted correctly | ⬜ |
| 1.3.4 | Special chars in URL | Dictate "hello?q=test&lang=en" | URL-legal chars appear correctly | ⬜ |
| 1.3.5 | Long text in form field | Dictate 1000+ chars into form field | Field scrolls, no truncation | ⬜ |
| 1.3.6 | Orientation change | Rotate during Confirm state | Text preserved, Insert works | ⬜ |
| 1.3.7 | App switch + return | Dictate, switch, return to Chrome | Field retains text | ⬜ |

### 1.4 Gmail compose (email)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.4.1 | Subject line insertion | Focus Subject field, dictate subject | Subject filled correctly | ⬜ |
| 1.4.2 | Email body insertion | Focus body, dictate paragraph | Text inserted at cursor in body | ⬜ |
| 1.4.3 | Newlines in body | Dictate multi-paragraph body | Paragraphs separated correctly | ⬜ |
| 1.4.4 | Emoji in body | Dictate "Thanks 😊 for the update" | Emoji renders in compose | ⬜ |
| 1.4.5 | Long email body | Dictate 1500+ char body | Body scrollable, no truncation | ⬜ |
| 1.4.6 | Orientation change | Rotate while in Confirm | Text preserved in compose | ⬜ |
| 1.4.7 | App switch + return | Dictate, switch to browser, return to Gmail | Draft intact | ⬜ |

### 1.5 Google Messages (SMS)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.5.1 | Basic SMS insertion | Open conversation, dictate short message | Text in compose box | ⬜ |
| 1.5.2 | Newlines in SMS | Dictate two-line SMS | Both lines visible (or SMS-appropriate) | ⬜ |
| 1.5.3 | Emoji in SMS | Dictate "On my way 😊" | Emoji renders, send works | ⬜ |
| 1.5.4 | Long SMS | Dictate >160 chars | Message handled as MMS or multi-part | ⬜ |
| 1.5.5 | Orientation change | Rotate during Confirm | Text preserved | ⬜ |
| 1.5.6 | App switch + return | Dictate, switch, return | Compose box retains text | ⬜ |

### 1.6 Telegram (messaging)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.6.1 | Basic text insertion | Open chat, dictate, Insert | Text in input field | ⬜ |
| 1.6.2 | Newlines | Dictate multi-line message | Lines preserved on send | ⬜ |
| 1.6.3 | Emoji / markdown | Dictate "Hello **world** 😊" | Emoji renders, markdown not interpreted as text | ⬜ |
| 1.6.4 | Long text | Dictate 1000+ char message | Message sent successfully | ⬜ |
| 1.6.5 | Orientation change | Rotate | Text preserved | ⬜ |
| 1.6.6 | App switch + return | Background + foreground | Input field retains text | ⬜ |

### 1.7 Samsung Notes (notes)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.7.1 | Basic text insertion | Open note, dictate paragraph | Text inserted at cursor | ⬜ |
| 1.7.2 | Newlines and paragraphs | Dictate multi-paragraph note | Paragraph structure preserved | ⬜ |
| 1.7.3 | Emoji and special chars | Dictate with emoji, accents | Rendered correctly | ⬜ |
| 1.7.4 | Long document | Dictate 2000+ char document | Document scrollable, no performance issues | ⬜ |
| 1.7.5 | Orientation change | Rotate | Text preserved | ⬜ |
| 1.7.6 | App switch + return | Switch away and back | Note unchanged | ⬜ |

### 1.8 System Settings search (system UI)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.8.1 | Search bar injection | Open Settings, tap search, dictate "WiFi" | "WiFi" appears in search bar | ⬜ |
| 1.8.2 | Special chars in search | Dictate "Bluetooth & NFC" | Query matches results | ⬜ |
| 1.8.3 | Clear and re-dictate | Clear search, dictate new term | Second insertion works | ⬜ |
| 1.8.4 | Orientation change | Rotate during Confirm | Search bar retains text | ⬜ |
| 1.8.5 | App switch + return | Dictate, switch, return | Search query preserved | ⬜ |

### 1.9 File rename dialog (system UI)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.9.1 | File name insertion | Long-press file → Rename, dictate new name | Name field populated | ⬜ |
| 1.9.2 | Special chars in filename | Dictate "My File (v2).txt" | Dateiname akzeptiert (oder ungültige Zeichen ersetzt) | ⬜ |
| 1.9.3 | Overwrite existing name | Select all, dictate replacement | Name fully replaced | ⬜ |
| 1.9.4 | Cancel rename after dictation | Dictate, tap Cancel | Original filename preserved | ⬜ |
| 1.9.5 | Orientation change | Rotate during Confirm | Dialog text preserved | ⬜ |

---

## 2. Shizuku Injection Tests

With Shizuku enabled and the HandyUserService bound, text is injected via clipboard + KEYCODE_PASTE at UID 2000.

### 2.1 Apps matrix (same targets)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 2.1.1 | Google Keep | Enable Shizuku, open Keep note, dictate, auto-inject | Text injected via KEYCODE_PASTE | ⬜ |
| 2.1.2 | WhatsApp | Same flow in WhatsApp chat | Text arrives in compose box | ⬜ |
| 2.1.3 | Chrome URL bar | Same flow in URL bar | Text pasted into URL bar | ⬜ |
| 2.1.4 | Gmail compose | Same flow in email body | Text pasted into body | ⬜ |
| 2.1.5 | Google Messages | Same flow | Text pasted into SMS compose | ⬜ |
| 2.1.6 | Telegram | Same flow | Text pasted into Telegram input | ⬜ |
| 2.1.7 | Samsung Notes | Same flow | Text pasted into note | ⬜ |
| 2.1.8 | System Settings search | Same flow | Text pasted into search | ⬜ |
| 2.1.9 | File rename dialog | Same flow | Text pasted into rename field | ⬜ |

### 2.2 Shizuku edge cases

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 2.2.1 | Shizuku permission NOT granted | Revoke Shizuku permission, dictate | Falls back to IME injector (or clipboard), no crash | ⬜ |
| 2.2.2 | Shizuku service not running | Kill HandyUserService, dictate | `isAvailable()` returns false, falls back to IME -> clipboard | ⬜ |
| 2.2.3 | Shizuku not installed | Uninstall Shizuku, dictate | `Shizuku.pingBinder()` returns false, falls back gracefully | ⬜ |
| 2.2.4 | Paste delay measurement | Log timestamps around `inject()` | Less than 200ms end-to-end | ⬜ |
| 2.2.5 | Concurrent Shizuku + IME | Shizuku enabled + IME focused | Shizuku selected (priority), text injected | ⬜ |
| 2.2.6 | Shizuku disabled in settings | Toggle Shizuku off, dictate | Uses IME injector instead | ⬜ |

---

## 3. Clipboard Fallback Tests

When neither IME nor Shizuku is available, text is placed on the system clipboard for manual paste.

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 3.1 | Clipboard set with Shizuku off, IME not in focus | Switch IME to system keyboard, dictate | Clipboard contains transcribed text | ⬜ |
| 3.2 | User can manually paste | After step 3.1, long-press any text field → Paste | Text is pasted correctly | ⬜ |
| 3.3 | Clipboard label visible | Check clip data description | "Handy Dictation" label attached | ⬜ |
| 3.4 | Clipboard NOT overwritten by other apps | After dictate, copy something else in another app | Handy's clipboard content replaced (standard behavior) | ⬜ |
| 3.5 | Clipboard overwritten before paste | Copy from another app, then try to paste Handy text | Other app's content is in clipboard (expected limitation) | ⬜ |
| 3.6 | Fallback notification | Check that no error is shown to user | Success state shown, clipboard hint displayed | ⬜ |
| 3.7 | Rapid clipboard sets | Dictate multiple times in succession | Each transcription overwrites clipboard cleanly | ⬜ |

---

## 4. IME State Transitions

State machine: `Idle(0)` → `Loading(1)` → `Listening(2)` → `Transcribing(3)` → `Confirm(5)` → `Idle(0)`.

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 4.1 | Idle → Listening | Tap mic button in IME | UI shows listening state, VAD bar animates | ⬜ |
| 4.2 | Listening → Transcribing | Tap stop button during recording | UI shows transcribing, audio finalizing | ⬜ |
| 4.3 | Transcribing → Confirm | Transcription result received | UI shows Confirm mode with text preview | ⬜ |
| 4.4 | Confirm → Idle (tap Insert) | Tap Insert button | Text injected, IME returns to Idle | ⬜ |
| 4.5 | Confirm → Idle (tap Retry → new recording) | Tap Retry | Text discarded, returns to Idle (or starts new recording) | ⬜ |
| 4.6 | Listening → Idle (tap Cancel) | Tap Cancel during recording | Recording cancelled, IME returns to Idle | ⬜ |
| 4.7 | Error → Idle (tap Retry) | Trigger an error, tap Retry | State resets to Idle, new recording can start | ⬜ |
| 4.8 | Keyboard switching | Tap ⌨ button in IME | System IME picker opens, Handy IME dismissed | ⬜ |
| 4.9 | Device rotation during dictation | Rotate while Listening | Recording continues, UI re-renders correctly | ⬜ |
| 4.10 | App switch during dictation + return | Switch to another app while Listening, return | IME reconnects, recording state preserved or gracefully reset | ⬜ |
| 4.11 | Idle → Loading → Listening | Model load triggered automatically | Loading state visible briefly, then transitions to Listening | ⬜ |
| 4.12 | Confirm → Idle (auto-inject) | Final transcription received with auto-inject enabled | Text injected automatically, IME returns to Idle | ⬜ |

---

## 5. Error Handling

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 5.1 | Dictation without mic permission | Revoke microphone permission, tap mic | Error state shown with "Microphone permission required" | ⬜ |
| 5.2 | Dictation without model downloaded | Delete all models, tap mic | Error state with "No model downloaded" message | ⬜ |
| 5.3 | Network error during model download | Enable airplane mode, start download | Download fails, error callback with network error message | ⬜ |
| 5.4 | Disk full during download | Fill device storage, start download | Download fails, error callback with storage error | ⬜ |
| 5.5 | Interrupted download → Resume | Start download, kill app, reopen, download same model | Resume from last checkpoint (or restart gracefully) | ⬜ |
| 5.6 | Interrupted download → Retry | Click Retry after failed download | Download restarts from beginning | ⬜ |
| 5.7 | Engine native crash | Simulate native crash in Rust engine | `onError()` called, state transitions to Error | ⬜ |
| 5.8 | Rapid start/stop recording | Tap mic then stop repeatedly | No crash, engine remains in valid state | ⬜ |
| 5.9 | Empty transcription result | Dictate silence, tap stop | Empty or zero-length result, confirm or error state | ⬜ |

---

## 6. Notification Actions

The `RecordingService` posts a foreground notification while recording.

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 6.1 | Recording notification visible | Tap mic to start dictation | Foreground notification "Handy is recording" appears | ⬜ |
| 6.2 | Notification "Stop" action | Tap Stop in notification | Recording stops, transcription result delivered | ⬜ |
| 6.3 | Notification "Switch Keyboard" action | Tap action in notification | IME picker opens | ⬜ |
| 6.4 | Quick Dictate notification (backgrounded) | Start dictation in IME, press Home | Notification persists, app is backgrounded | ⬜ |
| 6.5 | Quick Dictate triggers flow | Tap notification from background | Returns to app/IME, dictation state restored | ⬜ |
| 6.6 | Notification dismissed on stop | Stop dictation via UI | Notification removed automatically | ⬜ |
| 6.7 | Multiple notifications | Start/stop dictation several times | Only one notification at a time, no duplicates | ⬜ |

---

## 7. Settings Sync

Settings written to `SharedPreferences` are forwarded to the Rust engine via JNI.

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 7.1 | Change idle timeout | Set idle timeout from 30s to 10s, verify via logging | `nativeSetIdleTimeout(10)` called on Rust engine | ⬜ |
| 7.2 | Change post-processing endpoint | Set endpoint URL in settings | `nativeSetPostProcessEndpoint()` called with new URL | ⬜ |
| 7.3 | Change post-processing API key | Set API key in settings | `nativeSetPostProcessApiKey()` called with new key | ⬜ |
| 7.4 | Debounce works (rapid changes) | Change endpoint text rapidly (typing) | Only final value sent after 500ms debounce, no intermediate calls | ⬜ |
| 7.5 | Settings persist across restart | Change settings, kill app, reopen | Loaded values match what was saved | ⬜ |
| 7.6 | Shizuku toggle persistence | Enable Shizuku, restart app | Shizuku still enabled in settings | ⬜ |
| 7.7 | Multiple settings applied at startup | Set idle timeout + endpoint + API key, restart | All three forwarded to engine on init | ⬜ |

---

## 8. Shizuku Robustness

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 8.1 | Kill Shizuku service while running | Start dictation with Shizuku injection, kill shizukud via ADB | `onServiceDisconnected` called, `userService = null`, next injection falls back | ⬜ |
| 8.2 | Auto-reconnect after service restart | Kill shizukud, wait, restart | Shizuku reconnects, `onServiceConnected` fires, `isAvailable()` returns true | ⬜ |
| 8.3 | Grant Shizuku permission | Revoke → Grant Shizuku permission | `checkSelfPermission()` returns GRANTED, strategy selects Shizuku | ⬜ |
| 8.4 | Revoke Shizuku permission | Grant → Revoke Shizuku permission | `isAvailable()` returns false, falls back to IME | ⬜ |
| 8.5 | Toggle Shizuku enabled in settings | Enable → Disable → Enable | Strategy selection changes accordingly, no stale state | ⬜ |
| 8.6 | Shizuku binder ping failure | Lose Shizuku binder (adb shell stop shizuku) | `pingBinder()` returns false, falls back immediately | ⬜ |
| 8.7 | Bind/unbind lifecycle | Multiple binds/unbinds during app lifecycle | No resource leaks, no duplicate bind calls | ⬜ |
| 8.8 | UserService AIDL version mismatch | Build with stale AIDL, deploy | Graceful failure or clear error message | ⬜ |
