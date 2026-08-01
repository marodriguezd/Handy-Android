cmake_minimum_required(VERSION 3.22.1)

project(handy_whisper_jni)

# Build whisper.cpp/GGML as static libraries inside the app's shared JNI
# library. Android's CMake toolchain provides pthreads through libc, and the
# CPU backend is the portable baseline for all supported Android ABIs.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# Vulkan can be enabled in a toolchain that provides Android Vulkan headers and
# glslc. Keep the portable CPU build as the default until those artifacts are
# provisioned; the engine setting remains forward-compatible with this backend.
option(HANDY_ENABLE_VULKAN "Build the optional GGML Vulkan backend" OFF)
set(GGML_VULKAN ${HANDY_ENABLE_VULKAN} CACHE BOOL "ggml: use Vulkan" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)

# The upstream standalone CMake project normally supplies this metadata from
# its root project. The Android module includes only ggml/src and whisper/src,
# so provide the version expected by whisper_version().
set(WHISPER_VERSION "handy-whisper" CACHE STRING "" FORCE)

add_subdirectory(whisper-upstream/ggml)
add_subdirectory(whisper-upstream/src)

# The upstream standalone project normally adds this definition from its root
# CMakeLists.txt. We include src/ directly, so provide it on the target.
target_compile_definitions(whisper PRIVATE
    WHISPER_VERSION=\"handy-whisper\"
    WHISPER_BUILD
)

add_library(handy_whisper_jni SHARED native-lib.cpp)

target_compile_features(handy_whisper_jni PRIVATE cxx_std_17)
target_compile_options(handy_whisper_jni PRIVATE -Wall -Wextra -Wpedantic)
target_include_directories(handy_whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper-upstream/include
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper-upstream/ggml/include
)
target_link_libraries(handy_whisper_jni PRIVATE whisper)

find_library(log-lib log)
target_link_libraries(handy_whisper_jni PRIVATE ${log-lib})
