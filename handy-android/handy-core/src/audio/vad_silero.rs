use crate::audio::vad::{EnergyVad, VoiceActivityDetector, VAD_FRAME_SAMPLES, VAD_THRESHOLD};

/// Feature-gated Silero VAD wrapper.
///
/// This module is compiled only when the `silero` Cargo feature is enabled.
/// When enabled, the wrapper attempts to load the Silero ONNX model from
/// `SILERO_VAD_MODEL_PATH` (or a well-known default). If loading fails for
/// any reason, it transparently falls back to the existing `EnergyVad` so
/// audio capture continues to work.
///
/// The actual ONNX inference is intentionally left as a placeholder for the
/// next phase; this file establishes the trait contract and build wiring so
/// future work only needs to swap in the real `vad-rs` calls.
pub struct SileroVad {
    /// Path to the ONNX model file on device storage.
    model_path: String,
    /// Fallback energy detector used when the model is not available.
    fallback: EnergyVad,
    /// True if the model file exists at runtime.
    model_available: bool,
}

impl SileroVad {
    /// Default model path inside the Android app files directory.
    /// The Kotlin side is responsible for copying the asset to this location
    /// when the `silero` feature is enabled.
    pub const DEFAULT_PATH: &str = "silero_vad_v4.onnx";

    pub fn new(threshold_factor: f32, frame_samples: usize) -> Self {
        let model_path = std::env::var("SILERO_VAD_MODEL_PATH")
            .unwrap_or_else(|_| Self::DEFAULT_PATH.to_string());

        let model_available = std::path::Path::new(&model_path).is_file();
        if !model_available {
            log::warn!(
                "[handy-core] Silero model not found at '{}'; falling back to EnergyVad",
                model_path
            );
        } else {
            log::info!(
                "[handy-core] Silero model found at '{}'; placeholder inference active",
                model_path
            );
        }

        Self {
            fallback: EnergyVad::new(threshold_factor, frame_samples),
            model_path,
            model_available,
        }
    }

    fn frame_energy_is_voice(&mut self, frame: &[f32]) -> Result<bool, String> {
        // TODO(Sprint 30d+ VAD implementation): replace this placeholder with
        // real `vad-rs` inference. The feature flag and trait wiring are
        // intentionally landed first so the actual ONNX integration is a
        // drop-in change inside this method. For now the feature-gated path
        // still provides VAD via the EnergyVad fallback, keeping builds green
        // while the `vad-rs` / `ort` Android NDK cross-compilation story is
        // worked out.
        self.fallback.is_voice(frame)
    }
}

impl VoiceActivityDetector for SileroVad {
    fn is_voice(&mut self, frame: &[f32]) -> Result<bool, String> {
        if !self.model_available {
            return self.fallback.is_voice(frame);
        }

        // Placeholder: real implementation will call vad-rs here.
        // If the call ever fails, fall back to energy detection rather than
        // failing the whole audio pipeline.
        match self.frame_energy_is_voice(frame) {
            Ok(v) => Ok(v),
            Err(e) => {
                log::warn!("[handy-core] Silero inference failed ({}); using EnergyVad", e);
                self.model_available = false;
                self.fallback.is_voice(frame)
            }
        }
    }

    fn reset(&mut self) {
        self.fallback.reset();
    }
}
