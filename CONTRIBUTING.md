# Contributing to Handy

Thank you for your interest in contributing to Handy! This guide will help you get started with contributing to this open source speech-to-text application.

## ⚠️ Feature Freeze

**Handy is currently undergoing a feature freeze.** If you are submitting a PR which is a new feature that the community has not asked for, it will be rejected. If the community has asked for it, or you have explicitly gathered support, it may still be considered.

**Bug fixes are the top priority.** There are 60+ issues to fix. Please focus your contributions on fixing bugs and improving stability.

## 📖 Philosophy

Handy aims to be the most forkable speech-to-text app. The goal is to create both a useful tool and a foundation for others to build upon—a well-patterned, simple codebase that serves the community. We prioritize:

- **Simplicity**: Clear, maintainable code over clever solutions
- **Extensibility**: Make it easy for others to fork and customize
- **Privacy**: Keep everything local and offline
- **Accessibility**: Free tooling that belongs in everyone's hands

## 🚀 Getting Started

### Prerequisites

**Desktop (Tauri):**
- [Rust](https://rustup.rs/) (latest stable)
- [Bun](https://bun.sh/) package manager
- Platform-specific build tools (see [BUILD.md](BUILD.md))

**Android:**
- [Rust](https://rustup.rs/) (latest stable)
- [Android NDK](https://developer.android.com/ndk) (r26+)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`
- Rust target: `rustup target add aarch64-linux-android`
- Java 17 (JDK 17)
- Android SDK (compileSdk 35)

See [handy-android/BUILD.md](handy-android/BUILD.md) for detailed Android build instructions.

### Setting Up Your Development Environment

1. **Fork the repository** on GitHub

2. **Clone your fork**:

   ```bash
   git clone git@github.com:YOUR_USERNAME/Handy.git
   cd Handy
   ```

3. **Add upstream remote**:

   ```bash
   git remote add upstream git@github.com:cjpais/Handy.git
   ```

4. **Install dependencies**:

   ```bash
   bun install
   ```

5. **Download required models**:

   ```bash
   mkdir -p src-tauri/resources/models
   curl -o src-tauri/resources/models/silero_vad_v4.onnx https://blob.handy.computer/silero_vad_v4.onnx
   ```

6. **Run in development mode**:
   ```bash
   bun run tauri dev
   # On macOS if you encounter cmake errors:
   CMAKE_POLICY_VERSION_MINIMUM=3.5 bun run tauri dev
   ```

For detailed platform-specific setup instructions, see [BUILD.md](BUILD.md).

### Understanding the Codebase

Handy follows a clean architecture pattern:

**Backend (Rust - `src-tauri/src/`):**

- `lib.rs` - Main application entry point with Tauri setup
- `managers/` - Core business logic (audio, model, transcription)
- `audio_toolkit/` - Low-level audio processing (recording, VAD)
- `commands/` - Tauri command handlers for frontend communication
- `shortcut.rs` - Global keyboard shortcut handling
- `settings.rs` - Application settings management

**Frontend (React/TypeScript - `src/`):**

- `App.tsx` - Main application component
- `components/` - React UI components
- `hooks/` - Reusable React hooks
- `lib/types.ts` - Shared TypeScript types

For more details, see the Architecture section in [README.md](README.md) or [AGENTS.md](AGENTS.md).

**Android (Kotlin + Rust — `handy-android/`):**

The Android build lives in the `handy-android/` subdirectory:

- `handy-core/` — Rust engine (cdylib, 22 JNI functions) handling audio capture, VAD, transcription, model management, and history
- `app/src/main/java/com/handy/app/` — Kotlin application code:
  - `bridge/` — JNI bridge declarations (EngineBridge.kt, EngineCallback.kt)
  - `viewmodel/` — ViewModels with StateFlow-based state management
  - `ui/` — Jetpack Compose screens (Dictation, Models, Settings, History, Onboarding)
  - `ime/` — InputMethodService for text injection
  - `injection/` — Strategy pattern for text injection (Shizuku, IME, Clipboard)
  - `service/` — Foreground RecordingService
  - `model/` — Data model classes (ModelInfo, HistoryEntry, AppSettings)

Key architecture rules:
- It is a **single Activity** with Compose Navigation
- **No DI framework** — manual factory pattern via `HandyApplication`
- The Rust engine is a **process-wide singleton** — `nativeInit` called exactly once
- The IME does NOT own the engine — it accesses the ViewModel via `HandyApplication`
- **ProGuard rules** are mandatory for release builds (JNI + AIDL keep rules)

## 🐛 Reporting Bugs

### Before Submitting a Bug Report

1. **Search existing issues** at [github.com/cjpais/Handy/issues](https://github.com/cjpais/Handy/issues)
2. **Check discussions** at [github.com/cjpais/Handy/discussions](https://github.com/cjpais/Handy/discussions)
3. **Try the latest release** to see if the issue has been fixed
4. **Enable debug mode** (`Cmd/Ctrl+Shift+D`) to gather diagnostic information

### Submitting a Bug Report

When creating a bug report, please include:

**System Information:**

- App version (found in settings or about section)
- Operating System (e.g., macOS 14.1, Windows 11, Ubuntu 22.04)
- CPU (e.g., Apple M2, Intel i7-12700K, AMD Ryzen 7 5800X)
- GPU (e.g., Apple M2 GPU, NVIDIA RTX 4080, Intel UHD Graphics)

**Bug Details:**

- Clear description of the bug
- Steps to reproduce
- Expected behavior
- Actual behavior
- Screenshots or logs if applicable
- Information from debug mode if relevant

Use the [Bug Report template](.github/ISSUE_TEMPLATE/bug_report.md) when creating an issue.

## 💡 Suggesting Features

We use GitHub Discussions for feature requests rather than issues. This keeps issues focused on bugs and actionable tasks while allowing more open-ended conversations about features.

### Before Suggesting a Feature

1. **Search existing discussions** at [github.com/cjpais/Handy/discussions](https://github.com/cjpais/Handy/discussions)
2. **Check common feature requests**:
   - [Post-processing / Editing Transcripts](https://github.com/cjpais/Handy/discussions/168)
   - [Keyboard Shortcuts / Hotkeys](https://github.com/cjpais/Handy/discussions/211)

### Submitting a Feature Request

1. Go to [Discussions](https://github.com/cjpais/Handy/discussions)
2. Click "New discussion"
3. Choose the appropriate category (Ideas, Feature Requests, etc.)
4. Describe your feature idea including:
   - The problem you're trying to solve
   - Your proposed solution
   - Any alternatives you've considered
   - How it fits with Handy's philosophy

## 🔧 Making Code Contributions

### Before You Start

**This is critical:** Before writing any code, please do the following:

1. **Search existing issues and PRs** - Check both open AND closed issues and pull requests. Someone may have already addressed this, or there may be a reason it was closed.
   - [Open issues](https://github.com/cjpais/Handy/issues)
   - [Closed issues](https://github.com/cjpais/Handy/issues?q=is%3Aissue+is%3Aclosed)
   - [Open PRs](https://github.com/cjpais/Handy/pulls)
   - [Closed PRs](https://github.com/cjpais/Handy/pulls?q=is%3Apr+is%3Aclosed)

2. **If something was previously closed** - If you want to revisit a closed issue or PR, you need to:
   - Provide a strong argument for why it should be reconsidered
   - Gather community feedback first via [Discussions](https://github.com/cjpais/Handy/discussions)
   - Link to that discussion in your PR

3. **Get community feedback for features** - PRs with demonstrated community interest are **much more likely to be merged**. Start a discussion, get feedback, and link to it in your PR. This helps ensure Handy stays focused and useful for the most people without becoming bloated.

Community feedback is essential to keeping Handy the best it can be for everyone. It helps prioritize what matters most and prevents feature creep.

### Development Workflow

1. **Create a feature branch**:

   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

2. **Make your changes**:
   - Write clean, maintainable code
   - Follow existing code style and patterns
   - Add comments for complex logic
   - Keep commits focused and atomic

3. **Test thoroughly**:
   - Test on your target platform(s)
   - Verify existing functionality still works
   - Test edge cases and error conditions
   - Use debug mode to verify audio/transcription behavior

4. **Commit your changes**:

   ```bash
   git add .
   git commit -m "feat: add your feature description"
   # or
   git commit -m "fix: describe the bug fix"
   ```

   Use conventional commit messages:
   - `feat:` for new features
   - `fix:` for bug fixes
   - `docs:` for documentation changes
   - `refactor:` for code refactoring
   - `test:` for test additions/changes
   - `chore:` for maintenance tasks

5. **Keep your fork updated**:

   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

6. **Push to your fork**:

   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**:
   - Go to the [Handy repository](https://github.com/cjpais/Handy)
   - Click "New Pull Request"
   - Select your fork and branch
   - Fill out the PR template completely, including:
     - Clear description of changes
     - Links to related issues or discussions
     - **Community feedback** (especially important for features)
     - How you tested the changes
     - Screenshots/videos if applicable
     - Breaking changes (if any)

   **Remember:** PRs with community support are prioritized. If you haven't already, start a [discussion](https://github.com/cjpais/Handy/discussions) to gather feedback before or alongside your PR. It is not explicitly required to gather feedback, but it certainly helps your PR get merged faster.

### AI Assistance Disclosure

**AI-assisted PRs are welcome!** Use whatever tools help you contribute, just be upfront about it.

In your PR description, please include:

- Whether AI was used (yes/no)
- Which tools were used (e.g., "Claude Code", "GitHub Copilot", "ChatGPT")
- How extensively it was used (e.g., "generated boilerplate", "helped debug", "wrote most of the code")

### Code Style Guidelines

**Rust:**

- Follow standard Rust formatting (`cargo fmt`)
- Run `cargo clippy` and address warnings
- Use descriptive variable and function names
- Add doc comments for public APIs
- Handle errors explicitly (avoid unwrap in production code)

**TypeScript/React:**

- Use TypeScript strictly, avoid `any` types
- Follow React hooks best practices
- Use functional components
- Keep components small and focused
- Use Tailwind CSS for styling

**General:**

- Write self-documenting code
- Add comments for non-obvious logic
- Keep functions small and single-purpose
- Prioritize readability over cleverness

**Kotlin/Android:**
- Follow Kotlin coding conventions (no semicolons, PascalCase for classes, camelCase for functions)
- Use Jetpack Compose for all UI (no XML layouts beyond manifest)
- Use Material 3 with dynamic colors (Material You) on API 31+
- Use StateFlow + collectAsState with Lifecycle.repeatOnLifecycle for state collection
- No `kotlinx.serialization` — use `org.json` for Rust JSON interop
- All user-facing strings in `res/values/strings.xml` for i18n
- Crash protection: `catch_unwind` guards on all 22 JNI entry points

### Testing Your Changes

**Manual Testing:**

- Run the app in development mode: `bun run tauri dev`
- Test your changes with debug mode enabled
- Verify on multiple platforms if possible
- Test with different audio devices
- Try various transcription scenarios

**Building for Production:**

```bash
bun run tauri build
```

Test the production build to ensure it works as expected.

**Android Testing:**

- Build debug APK: `cd handy-android && ./gradlew assembleDebug`
- Install on device/emulator: `adb install app/build/outputs/apk/debug/app-debug.apk`
- Test IME flow: enable Handy in Settings → System → Languages & input → On-screen keyboard
- Test dictation: tap microphone button in IME, speak, verify text appears
- See [handy-android/TEST_MATRIX.md](handy-android/TEST_MATRIX.md) for comprehensive test cases (142 cases)

## 📝 Documentation Contributions

Documentation improvements are highly valued! You can contribute by:

- Improving README.md, BUILD.md, or this CONTRIBUTING.md
- Adding code comments and doc comments
- Creating tutorials or guides
- Improving error messages
- Updating the project website content

## 🤝 Community Guidelines

- **Be respectful and inclusive** - We welcome contributors of all skill levels
- **Be patient** - This is maintained by a small team, responses may take time
- **Be constructive** - Focus on solutions and improvements
- **Be collaborative** - Help others and share knowledge
- **Search first** - Check existing issues/discussions before creating new ones

## 🎯 Good First Issues

Look for issues labeled `good first issue` or `help wanted` if you're new to the project. These are typically:

- Well-defined and scoped
- Good for learning the codebase
- Mentor support available

## 📞 Getting Help

- **Discord**: Join our [Discord community](https://discord.com/invite/WVBeWsNXK4)
- **Discussions**: Ask questions in [GitHub Discussions](https://github.com/cjpais/Handy/discussions)
- **Email**: Reach out at [contact@handy.computer](mailto:contact@handy.computer)

## 📜 License

By contributing to Handy, you agree that your contributions will be licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

**Thank you for contributing to Handy!** Your efforts help make speech-to-text technology more accessible, private, and extensible for everyone.
