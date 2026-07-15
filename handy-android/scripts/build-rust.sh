#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CORE_DIR="$PROJECT_DIR/handy-core"
JNILIBS_DIR="$PROJECT_DIR/app/src/main/jniLibs"

if ! command -v cargo-ndk &>/dev/null; then
    echo "Error: cargo-ndk is not installed. Install it with:"
    echo "  cargo install cargo-ndk"
    exit 1
fi

# Set CMake args for NDK cross-compilation (used by transcribe-cpp crate)
export CMAKE_ARGS="-DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_HOME:-$NDK}/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24"

echo "Building for aarch64-linux-android..."
(cd "$CORE_DIR" && cargo ndk --target aarch64-linux-android --platform 26 build --release)

mkdir -p "$JNILIBS_DIR/arm64-v8a"
cp "$CORE_DIR/target/aarch64-linux-android/release/libhandy_core.so" "$JNILIBS_DIR/arm64-v8a/libhandy_core.so"
echo "Copied libhandy_core.so to jniLibs/arm64-v8a/"

if rustup target list --installed | grep -q "x86_64-linux-android"; then
    echo "Building for x86_64-linux-android..."
    (cd "$CORE_DIR" && cargo ndk --target x86_64-linux-android --platform 26 build --release)

    mkdir -p "$JNILIBS_DIR/x86_64"
    cp "$CORE_DIR/target/x86_64-linux-android/release/libhandy_core.so" "$JNILIBS_DIR/x86_64/libhandy_core.so"
    echo "Copied libhandy_core.so to jniLibs/x86_64/"
else
    echo "Skipping x86_64-linux-android (target not installed)"
fi

echo "Rust build complete!"
