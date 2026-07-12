# Building Handy for Android

## Prerequisites

- **Rust** (latest stable) — `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`
- **Android NDK** (r26+) — via Android Studio SDK Manager or sdkmanager
- **cargo-ndk** — `cargo install cargo-ndk`
- **Rust Android targets** — `rustup target add aarch64-linux-android x86_64-linux-android`
- **Java 17** (JDK 17) — via SDKMAN, asdf, or your package manager
- **Android SDK** (compileSdk 35) — via Android Studio

## Environment Setup

Set these environment variables:

```bash
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/<version>
# or
export NDK=$HOME/Android/Sdk/ndk/<version>
```

Add to your `~/.bashrc` or `~/.zshrc` for persistence.

## Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/cjpais/Handy.git
cd Handy/handy-android

# 2. Build the Rust native library (ARM64)
./scripts/build-rust.sh

# 3. Build the debug APK
./gradlew assembleDebug

# 4. Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Building for Release

```bash
# 1. Build Rust native library (release)
./scripts/build-rust.sh

# 2. Set signing environment variables
export HANDY_KEYSTORE_PATH=../handy-release.keystore
export HANDY_KEYSTORE_PASSWORD=<your-store-password>
export HANDY_KEY_ALIAS=handy
export HANDY_KEY_PASSWORD=<your-key-password>
export SENTRY_DSN=<your-sentry-dsn>

# 3. Build release APK and App Bundle
./gradlew assembleRelease bundleRelease
```

Output:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

## Manual Build Steps (without build-rust.sh)

If you prefer to run cargo-ndk directly:

```bash
cd handy-android/handy-core

export CMAKE_ARGS="-DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24"

cargo ndk --target aarch64-linux-android --platform 26 -- -p handy-core build --release

# Copy the .so to jniLibs
mkdir -p ../app/src/main/jniLibs/arm64-v8a
cp target/aarch64-linux-android/release/libhandy_core.so ../app/src/main/jniLibs/arm64-v8a/
```

## Build Architecture

```
Rust source (handy-core/)
  │ cargo ndk (cross-compile for aarch64-linux-android)
  ▼
libhandy_core.so
  │ copied to jniLibs/arm64-v8a/ via build-rust.sh or Gradle buildRust task
  ▼
Gradle assembly
  │ compile Kotlin + merge JNI libs + package
  ▼
app-debug.apk / app-release.apk / app-release.aab
```

## Troubleshooting

### cargo-ndk fails: "could not find NDK"

Set `ANDROID_NDK_HOME` to your NDK installation path. Common locations:
- **Linux:** `~/Android/Sdk/ndk/<version>/`
- **macOS:** `~/Library/Android/sdk/ndk/<version>/`
- **Windows:** `%LOCALAPPDATA%\Android\Sdk\ndk\<version>\`

### Rust build fails: "cannot find -lOpenSLES"

Install the NDK. The `build.rs` in handy-core links OpenSLES for Android audio.

### Gradle fails: "No compatible NDK version found"

Ensure your NDK version is r26 or newer. Run `sdkmanager --list | grep ndk` to see available versions.

### "java.lang.UnsatisfiedLinkError: dlopen failed: library not found"

Run `./scripts/build-rust.sh` before Gradle. The `.so` must be in `app/src/main/jniLibs/arm64-v8a/`.

### APK installs but crashes on launch (arm64 device)

Verify your APK contains the native library:
```bash
unzip -l app-debug.apk | grep libhandy_core
# Should show: lib/arm64-v8a/libhandy_core.so
```

### ProGuard/R8 release build crashes

Ensure `app/proguard-rules.pro` contains the JNI keep rules. Without them, R8 obfuscation renames JNI classes/methods.

## Gradle Tasks

| Task | Description |
|---|---|
| `assembleDebug` | Build debug APK (unsigned, `.debug` suffix) |
| `assembleRelease` | Build release APK (signed with keystore) |
| `bundleRelease` | Build release Android App Bundle (AAB) |
| `buildRust` | Build Rust native library via cargo-ndk (automatic) |
| `copyRustLib` | Copy .so to jniLibs (automatic) |

## X86_64 Emulator Build

For testing on the Android emulator:

```bash
rustup target add x86_64-linux-android
cd handy-core
cargo ndk --target x86_64-linux-android --platform 26 -- -p handy-core build --release
mkdir -p ../app/src/main/jniLibs/x86_64
cp target/x86_64-linux-android/release/libhandy_core.so ../app/src/main/jniLibs/x86_64/
```

Then build with Gradle as usual.
