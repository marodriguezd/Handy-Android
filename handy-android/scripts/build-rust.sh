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
# SPIRV-Headers and Vulkan-Headers are vendored because the build host may not
# have the system packages installed (required when GGML_VULKAN=ON).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default to CPU-only builds for fast debug iteration. Pass --vulkan to enable
# GPU acceleration (requires a fully configured Vulkan toolchain).
USE_VULKAN=""
if [[ "${1:-}" == "--vulkan" ]]; then
    USE_VULKAN="1"
    shift
fi

if [[ -n "$USE_VULKAN" ]]; then
    SPIRV_PREFIX="${SCRIPT_DIR}/../deps/spirv-install"
    SPIRV_INCLUDE="${SPIRV_PREFIX}/include"
    SPIRV_CMAKE_DIR="${SPIRV_PREFIX}/share/cmake/SPIRV-Headers"
    VULKAN_INCLUDE="${SCRIPT_DIR}/../deps/Vulkan-Headers/include"
    COMMON_FLAGS="-I${VULKAN_INCLUDE} -I${SPIRV_INCLUDE}"
    export CMAKE_ARGS="-DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_HOME:-$NDK}/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DGGML_NATIVE=OFF -DGGML_VULKAN=ON -DSPIRV-Headers_DIR=${SPIRV_CMAKE_DIR} -DCMAKE_CXX_FLAGS=${COMMON_FLAGS} -DCMAKE_C_FLAGS=${COMMON_FLAGS}"
    export TRANSCRIBE_CMAKE_ARGS="-DGGML_NATIVE=OFF -DGGML_VULKAN=ON -DSPIRV-Headers_DIR=${SPIRV_CMAKE_DIR} -DCMAKE_CXX_FLAGS=${COMMON_FLAGS} -DCMAKE_C_FLAGS=${COMMON_FLAGS}"
else
    export CMAKE_ARGS="-DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_HOME:-$NDK}/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DGGML_NATIVE=OFF -DGGML_VULKAN=OFF"
    export TRANSCRIBE_CMAKE_ARGS="-DGGML_NATIVE=OFF -DGGML_VULKAN=OFF"
fi

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
