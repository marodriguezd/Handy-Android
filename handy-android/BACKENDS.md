# Handy Android — GPU/NPU Backend Investigation

**Last updated:** 2026-07-16
**Status:** Sprint 16 — Material Design 3 Redesign + Backend Investigation

---

## 1. Executive Summary

| Backend | Maturity on Android | Effort | Recommendation |
|---------|---------------------|--------|----------------|
| **CPU (NEON)** | ✅ Production-ready | Low | **Default for now** — stable, mature, sufficient for most mobile use cases |
| **Vulkan** | ⚠️ Experimental on Android | Medium | **Next realistic GPU step** — already partially wired, but needs include-path fixes and stability testing |
| **QNN / Hexagon NPU** | ⚠️ Requires fork + proprietary SDK | High | **Future, Snapdragon-only** — high performance but high maintenance cost |
| **NNAPI** | ❌ Not supported by whisper.cpp/ggml | Very High | **Not recommended** — would require a custom GGML→NNAPI graph mapper |

---

## 2. Current State in Handy Android

### 2.1 CPU (Baseline)

- Default backend for debug builds (`GGML_VULKAN=OFF`).
- Uses `transcribe-cpp` with `Backend::Auto` → binds to CPU on Android.
- Verified working on A059 (Nothing Phone 3a), Android 16, ARM64.

### 2.2 Vulkan (Partially Wired)

- `handy-core/Cargo.toml`: `transcribe-cpp` is built **without** the `"vulkan"` feature by default.
- `app/build.gradle.kts`: `GGML_VULKAN=OFF` for debug; `GGML_VULKAN=ON` for release with vendored SPIRV/Vulkan include paths.
- `scripts/build-rust.sh`: default CPU-only; pass `--vulkan` to enable GPU build.
- Vendored headers exist under `handy-android/deps/`:
  - `Vulkan-Headers/`
  - `SPIRV-Headers/` (installed under `deps/spirv-install`)

### 2.3 QNN / Hexagon / NNAPI

- Not currently integrated.
- No build flags or source references exist in the project.

---

## 3. Backend Options

### 3.1 Vulkan

**What it is:** Cross-platform GPU compute API. `ggml` supports a Vulkan backend that compiles shaders at build time.

**Pros:**
- Broad device support (most modern Android devices have Vulkan 1.1+).
- Already supported by `whisper.cpp` / `ggml` upstream.
- Can significantly speed up matrix-heavy inference on GPUs.

**Cons:**
- **Stability issues on Android:** community reports crashes (especially on Adreno GPUs) and accuracy degradation/hallucinations.
- Build complexity: requires NDK cross-compilation, Vulkan loader, SPIRV headers, and shader compilation toolchain.
- Driver bugs are common; performance varies widely across chipsets.

**Integration steps for Handy Android:**
1. Add a `vulkan` feature to `handy-core/Cargo.toml` that enables `transcribe-cpp/vulkan`.
2. Fix the `CMAKE_CXX_FLAGS` / `CMAKE_C_FLAGS` quoting in `app/build.gradle.kts` and `scripts/build-rust.sh` so the vendored `Vulkan-Headers/include` path is picked up.
3. Ensure `vulkan-shaders-gen` can run on the build host (may require host Vulkan SDK).
4. Build release APK and verify `Backend::Auto` selects Vulkan in logcat.
5. Test on multiple devices/chipsets (Qualcomm Adreno, Samsung Exynos, MediaTek).

### 3.2 Qualcomm QNN / Hexagon NPU

**What it is:** Proprietary Qualcomm SDK for running inference on the Hexagon DSP / NPU on Snapdragon SoCs.

**Pros:**
- Best power efficiency and performance on supported Snapdragon devices.
- Purpose-built for quantized neural networks.

**Cons:**
- **Snapdragon-only** — no benefit on Exynos or MediaTek devices.
- Requires proprietary Hexagon SDK and non-trivial build setup.
- Maintenance cost is high; upstream `ggml` does not support it directly.
- Community fork `zhouwg/ggml-hexagon` exists but is experimental.

**Integration steps (high-level):**
1. Evaluate `zhouwg/ggml-hexagon` fork feasibility.
2. Set up Qualcomm Hexagon SDK and NDK toolchain.
3. Build `libggml-hexagon.so` / FastRPC stubs for target DSP.
4. Patch `transcribe-cpp` or maintain a fork that links against the Hexagon backend.
5. Gate the backend behind device detection (Qualcomm-only).

### 3.3 NNAPI (Android Neural Networks API)

**What it is:** Google's generic Android API for accelerating inference on NPU/GPU/DSP.

**Pros:**
- Theoretically broad device support.
- Official Android API.

**Cons:**
- **Not implemented in whisper.cpp/ggml.**
- Would require writing a custom GGML graph → NNAPI model converter, which is a large engineering effort.
- NNAPI is deprecated in Android 15, reducing its future value.

**Recommendation:** Do not pursue unless upstream ggml adds support.

---

## 4. Recommended Roadmap

### Short term (Sprint 16–17)
1. **Keep CPU as default.** It is stable and verified.
2. **Fix and enable Vulkan for release builds.**
   - Add `vulkan` feature to `handy-core`.
   - Fix include-path quoting for vendored headers.
   - Build release APK and test on A059.
   - Add a runtime toggle or build flavor to disable Vulkan if issues are found.

### Medium term (Sprint 18+)
3. **Evaluate QNN/Hexagon** if Snapdragon performance becomes a hard requirement.
   - Spike: build `zhouwg/ggml-hexagon` for A059.
   - Compare latency/power against CPU and Vulkan.
   - Decide if maintenance cost is justified.

### Long term
4. **Monitor upstream ggml** for NNAPI or other NPU backends.

---

## 5. Known Risks

| Risk | Mitigation |
|------|------------|
| Vulkan crashes on Adreno | Test on multiple chipsets; provide CPU fallback |
| Vulkan accuracy degradation | A/B test transcription quality vs CPU |
| Build fragility with vendored headers | Pin header versions; document exact NDK/SDK versions |
| QNN/Hexagon locks us into Qualcomm | Gate behind device detection; keep CPU fallback |

---

## 6. References

- `handy-android/app/build.gradle.kts` — Gradle build with Vulkan args
- `handy-android/scripts/build-rust.sh` — Rust/Vulkan build script
- `handy-android/handy-core/Cargo.toml` — transcribe-cpp feature flags
- `handy-android/deps/Vulkan-Headers/` — vendored Vulkan headers
- `zhouwg/ggml-hexagon` — community Hexagon backend fork
