//! Sprint 25b — pure-function WAV decoder used by `nativeRetryHistoryEntry`.
//!
//! Reads a 16-bit-mono-16 kHz WAV file from disk and returns its f32 PCM
//! samples, normalized to `[-1.0, +1.0]`. The format is strict because
//! `RecordingRepository` is the only writer in the system and it always
//! emits this exact shape (verified by `RecordingRepositoryTest` test #5:
//! "WAV header after finalize declares the correct data length").
//!
//! Strict acceptance is intentional: any WAV produced by the app on a
//! different device route (e.g. shared from another recorder) must come
//! from a clearly documented migration path. Failing fast with a clear
//! error beats silently re-sampling and producing a subtly off-spec
//! transcription that the user cannot debug from the History card.
//!
//! Bug-fix policy: this module MUST NOT mutate global state, MUST NOT
//! involve JNI attachments, and MUST be safe to call from a worker
//! thread holding the global `ENGINE` mutex for the entire
//! transcription run duration (the JNI side-effect documented in the
//! Sprint 25b architecture paragraph of PROGRESS.md).

use hound::{SampleFormat, WavReader};

/// Strict-format WAV → f32 PCM decoder.
///
/// Accepts only: 16 kHz, mono, 16-bit PCM. Anything else returns
/// `Err(String)` with a precise reason so the JNI caller can surface
/// it via `dispatch_error` with `code=4` ("retry_audio_format_unsupported").
pub fn decode_wav_to_f32(path: &str) -> Result<Vec<f32>, String> {
    let mut reader =
        WavReader::open(path).map_err(|e| format!("open '{path}' failed: {e}"))?;
    let spec = reader.spec();

    if spec.sample_rate != 16_000 {
        return Err(format!(
            "unsupported sample_rate={} Hz (expected 16000)",
            spec.sample_rate
        ));
    }
    if spec.channels != 1 {
        return Err(format!(
            "unsupported channel_count={} (expected mono)",
            spec.channels
        ));
    }
    if spec.bits_per_sample != 16 || spec.sample_format != SampleFormat::Int {
        return Err(format!(
            "unsupported format={:?} bits={} (expected Int/16)",
            spec.sample_format, spec.bits_per_sample
        ));
    }

    let samples = reader
        .samples::<i16>()
        .map(|s| s.map(|v| v as f32 / 32_767.0))
        .collect::<Result<Vec<f32>, _>>()
        .map_err(|e| format!("sample conversion failed: {e}"))?;

    Ok(samples)
}

#[cfg(test)]
mod tests {
    use super::*;
    use hound::{WavSpec, WavWriter};
    use std::io::Cursor;

    /// Build an in-memory WAV matching the app's writer shape.
    fn make_test_wav(samples: &[f32]) -> Vec<u8> {
        let spec = WavSpec {
            channels: 1,
            sample_rate: 16_000,
            bits_per_sample: 16,
            sample_format: SampleFormat::Int,
        };
        let mut buf = Vec::new();
        {
            let mut writer = WavWriter::new(Cursor::new(&mut buf), spec).unwrap();
            for &s in samples {
                let pcm = (s.clamp(-1.0, 1.0) * 32_767.0) as i16;
                writer.write_sample(pcm).unwrap();
            }
            writer.finalize().unwrap();
        }
        buf
    }

    #[test]
    fn round_trip_16k_mono_16bit_pcm() {
        // Generate a WAV file in a tempdir and decode it back.
        let tmp = std::env::temp_dir().join(format!(
            "handy_retry_test_{}.wav",
            std::process::id()
        ));
        let bytes = make_test_wav(&[0.0, 0.5, -0.5, 1.0, -1.0]);
        std::fs::write(&tmp, &bytes).unwrap();

        let decoded = decode_wav_to_f32(tmp.to_str().unwrap()).unwrap();
        // Sample-count must round-trip exactly (no resampling).
        assert_eq!(decoded.len(), 5);

        let _ = std::fs::remove_file(&tmp);
    }

    #[test]
    fn rejects_non_16khz_sample_rate() {
        let spec = WavSpec {
            channels: 1,
            sample_rate: 48_000,
            bits_per_sample: 16,
            sample_format: SampleFormat::Int,
        };
        let mut buf = Vec::new();
        {
            let mut w = WavWriter::new(Cursor::new(&mut buf), spec).unwrap();
            w.write_sample(0_i16).unwrap();
            w.finalize().unwrap();
        }
        let tmp = std::env::temp_dir().join(format!("handy_retry_48k_{}.wav", std::process::id()));
        std::fs::write(&tmp, &buf).unwrap();
        let err = decode_wav_to_f32(tmp.to_str().unwrap()).unwrap_err();
        assert!(err.contains("sample_rate"), "got: {err}");
        let _ = std::fs::remove_file(&tmp);
    }
}
