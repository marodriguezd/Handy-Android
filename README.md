# Handy Android 🎙️⚡

> *Native Android version of Handy (Speech-to-Text), 100% offline and currently in active development.*

**Handy Android** is the native Android port of [Handy](https://github.com/cjpais/Handy), a private, ultra-fast, 100% offline voice dictation and speech recognition application.

---

## 🌟 Key Features

### 🧠 Local Inference Engine (100% Private & Offline)
- **Rust `handy-core` JNI Bridge**: Native engine compiled in Rust with **Whisper.cpp / GGML** and **Silero VAD**.
- **On-Device Processing**: Your voice recordings never leave your device. No server latency, no subscriptions, no cloud data uploads.

### ⌨️ Deep Android System Integration
- **Native IME Keyboard (`HandyInputMethodService`)**: Animated floating Material Design 3 pill bar for dictating directly into any Android app.
- **Speech Recognition Service (`HandyVoiceRecognitionService`)**: Replaces the system's default voice service (`android.speech.RecognitionService`) with Intent-based `RECOGNIZE_SPEECH` integration and a Compose `RecognizeActivity`.
- **Text Injection Strategies**: Direct injection via Shizuku, Accessibility Service, Direct Paste, or Clipboard.

### 🔤 Phonetic Corrector & Custom Dictionary
- **User Dictionary**: Add custom terms, jargon, or proper nouns (`DictionaryScreen`).
- **`WordCorrector` Engine**: Advanced phonetic correction combining standard **Soundex**, **Levenshtein** distance, and **N-gram** analysis, along with configurable filler-word ("um", "uh", "like") removal.

### 🤖 LLM Post-Processing
- **Custom Prompts (`PromptsRepository`)**: Automatically transform, summarize, grammar-correct, or translate your transcripts.
- **LLM API Connections (`PostProcessor`)**: Compatible with OpenAI endpoints or local Ollama servers.

### 🎨 Adaptive Material Design 3 Design
- **1:1 Aesthetic Fidelity**: Preserves Handy's iconic color palette (`#2c2b29`, `#f28cbb`, `#da5893`).
- **Adaptive Layout**: Bottom navigation bar (`NavigationBar`) on phones, side rail (`NavigationRail`) on tablets and foldables.
- **Hot Theme & Language Switching**: Dynamically switch language and theme (Dark / Light / System) without restarting the activity or interrupting an active recording.

### 📼 History & Dual-Write WAV Recording
- **Recording Repository (`RecordingRepository`)**: Automatic persistence of audio WAV files with integrated playback, transcription retry, and smart space-based eviction policy.

---

## 🚀 Project Architecture

```
Handy-Android/
├── handy-core/           # Rust engine with C/C++ (Whisper.cpp, VAD, JNI bindings)
└── handy-android/        # Android project (Kotlin, Jetpack Compose, Material 3)
    ├── app/src/main/java/com/handy/app/
    │   ├── audio/        # RecordingRepository & WAV backend
    │   ├── bridge/       # EngineBridge.kt (Native JNI binding to handy-core)
    │   ├── capability/   # CatalogSorter.kt (intelligent model selection)
    │   ├── corrector/    # WordCorrector.kt (Soundex + Levenshtein + N-grams)
    │   ├── postprocessing/# PostProcessor.kt & PromptsRepository.kt (LLM API)
    │   ├── service/      # IME keyboard service & Speech Recognition service
    │   └── ui/           # Compose screens (Catalog, Dictionary, Settings, History, About)
    └── app/src/test/     # Full suite of 182 JVM unit tests (100% PASS)
```

---

## 🛠️ Build & Install

### Requirements
- Android Studio Ladybug / ME2024.2+
- JDK 17 / Android NDK 27+
- Android device (API 26+) or emulator

### Build Commands
```bash
# Navigate to the Android directory
cd handy-android

# Compile Kotlin and resources
./gradlew :app:compileDebugKotlin

# Run the full unit test suite (182 tests)
./gradlew :app:testDebugUnitTest

# Build the debug APK
./gradlew :app:assembleDebug

# Install on a connected device via ADB
./gradlew :app:installDebug
```

---

## 📜 License

Based on the original work of [CJPais/Handy](https://github.com/cjpais/Handy). Licensed under the MIT License.
