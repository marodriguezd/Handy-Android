use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex};

use crate::audio::capture::{AudioCapture, CaptureError};
use crate::audio::recording_sink::RecordingSink;
use crate::audio::resampler::FrameResampler;
use crate::audio::vad::{
    EnergyVad, SmoothedVad, VadFrame, VAD_FRAME_SAMPLES, VAD_ONSET_FRAMES,
    VAD_PREFILL_FRAMES, VAD_STREAMING_HANGOVER_FRAMES, VAD_THRESHOLD,
};

use crate::transcription::router::StreamRouter;

/// Simple fixed-gain pre-amp to boost quiet microphone signals.
/// Whisper models work best with audio RMS around 0.1–0.3.
/// Raw AAudio float32 samples from mobile microphones can be
/// very quiet (RMS ~0.01–0.05), causing poor transcription.
/// A fixed gain of 2.0–4.0x brings quiet speech into the optimal range.
/// This is NOT adaptive AGC (which was removed because it boosted noise).
const PREAMP_GAIN: f32 = 5.0;

struct PipelineInner {
    resampler: FrameResampler,
    smoothed_vad: SmoothedVad,
    audio_buffer: Vec<f32>,
    on_vad_level: Option<Arc<dyn Fn(f32) + Send + Sync>>,
    on_audio_frame: Option<Arc<dyn Fn(Vec<f32>) + Send + Sync>>,
    /// Sprint 25b — optional per-frame audio sink that forwards each
    /// resampled Float32 frame into Kotlin's
    /// `RecordingRepository.pushFloatArrayFrames`. Set via
    /// [`AudioPipeline::set_recording_sink`] from
    /// `nativeStartRecording` so frames are pumped only while
    /// recording. The underlying `RecordingSink::feed` is lock-free
    /// (OnceLock-stored `mpsc::Sender` + `try_send`) so this
    /// incurs no Mutex cost on the AAudio real-time callback thread.
    recording_sink: Option<Arc<RecordingSink>>,
    /// The actual sample rate of the capture stream (device native rate).
    capture_sample_rate: u32,
    /// Running tally of raw input samples received (for diagnostic logging).
    total_raw_samples: u64,
    /// Pre-allocated scratch buffer for applying gain.
    /// Avoids heap allocation on the real-time AAudio callback thread.
    gain_scratch: Vec<f32>,
    stream_router: Option<Arc<StreamRouter>>,
    /// Flag indicating whether streaming mode is active.
    /// When true, audio frames are NOT double-buffered into audio_buffer
    /// since they are already forwarded to the stream router in real-time.
    streaming_active: bool,
    /// Lock-free VAD level mirroring `voice_session.rs`.
    /// Stores an `f32` value via `to_bits()` so Kotlin can poll it with
    /// `nativeGetVadLevel()` without taking the pipeline Mutex.
    vad_level: Arc<AtomicU32>,
}



impl PipelineInner {
    fn process_samples(&mut self, samples: &[f32]) {
        const MAX_BUFFER_SAMPLES: usize = 300 * 16000; // ~19.2MB float32 (at 16kHz equivalent)
        if self.audio_buffer.len() > MAX_BUFFER_SAMPLES {
            log::warn!("Audio buffer exceeded max ({} samples), truncating", MAX_BUFFER_SAMPLES);
            self.audio_buffer.clear();
        }

        self.total_raw_samples += samples.len() as u64;

        // Apply fixed pre-amp gain using a reusable scratch buffer
        // to avoid heap allocation on the real-time audio callback thread.
        self.gain_scratch.clear();
        if PREAMP_GAIN != 1.0 {
            self.gain_scratch.extend(
                samples.iter().map(|&s| (s * PREAMP_GAIN).clamp(-1.0, 1.0))
            );
        } else {
            self.gain_scratch.extend_from_slice(samples);
        }
        let gained = &self.gain_scratch;

        self.resampler.push(gained, &mut |frame: &[f32]| {
            // Only buffer audio when streaming is NOT active.
            // During streaming, frames are forwarded to the stream router
            // in real-time, so double-buffering would waste ~19MB for
            // a 60-second recording.
            if !self.streaming_active {
                self.audio_buffer.extend_from_slice(frame);
            }

            // VAD still runs for the level meter visualization in the UI.
            let vad_result = self.smoothed_vad.push_frame(frame);
            if let Some(ref cb) = self.on_vad_level {
                let energy = frame.iter().map(|&s| s * s).sum::<f32>() / frame.len() as f32;
                let level = (energy.sqrt() * 5.0).min(1.0);
                self.vad_level.store(level.to_bits(), Ordering::Relaxed);
                match vad_result {
                    VadFrame::Speech(_) => cb(level),
                    VadFrame::Noise => cb(level * 0.5),
                }
            }

            // Feed frame to active stream router (zero-cost when not set)
            if let Some(ref router) = self.stream_router {
                // Only one allocation per frame: the mpsc channel takes
                // ownership of the Vec, so we must clone from &[f32].
                router.feed(frame.to_vec());
            }

            // Sprint 25b — pump the same frame into the Kotlin-side
            // RecordingRepository via the RecordingSink. Both paths
            // share the same Float32 frame so the disk WAV and the
            // on-the-fly transcription stay in sync. `feed()` is
            // lock-free + non-blocking on backpressure (drops frames
            // rather than blocking the AAudio real-time thread).
            if let Some(ref sink) = self.recording_sink {
                sink.feed(frame.to_vec());
            }
        });
    }
}

pub struct AudioPipeline {
    capture: Option<AudioCapture>,
    inner: Arc<Mutex<PipelineInner>>,
    running: Arc<AtomicBool>,
    /// Lock-free VAD level exposed to Kotlin via `nativeGetVadLevel`.
    /// Mirrors the `AtomicU32` pattern from the source `voice_session.rs`.
    pub vad_level: Arc<AtomicU32>,
}

impl AudioPipeline {
    pub fn new() -> Self {
        let energy_vad = Box::new(EnergyVad::new(VAD_THRESHOLD, VAD_FRAME_SAMPLES));
        let smoothed_vad = SmoothedVad::new(
            energy_vad,
            VAD_PREFILL_FRAMES,
            VAD_STREAMING_HANGOVER_FRAMES,
            VAD_ONSET_FRAMES,
        );

        let resampler = FrameResampler::new(16000, 16000);
        let vad_level = Arc::new(AtomicU32::new(0));

        Self {
            capture: None,
            inner: Arc::new(Mutex::new(PipelineInner {
                resampler,
                smoothed_vad,
                audio_buffer: Vec::new(),
                on_vad_level: None,
                on_audio_frame: None,
                recording_sink: None,
                capture_sample_rate: 0,
                total_raw_samples: 0,
                gain_scratch: Vec::new(),
                stream_router: None,
                streaming_active: false,
                vad_level: vad_level.clone(),
            })),
            running: Arc::new(AtomicBool::new(false)),
            vad_level,
        }
    }

    pub fn set_vad_callback<F>(&mut self, cb: F)
    where
        F: Fn(f32) + Send + Sync + 'static,
    {
        let mut guard = self.inner.lock().unwrap();
        guard.on_vad_level = Some(Arc::new(cb));
    }

    pub fn set_audio_callback<F>(&mut self, cb: F)
    where
        F: Fn(Vec<f32>) + Send + Sync + 'static,
    {
        let mut guard = self.inner.lock().unwrap();
        guard.on_audio_frame = Some(Arc::new(cb));
    }

    pub fn set_stream_router(&mut self, router: Option<Arc<StreamRouter>>) {
        let mut guard = self.inner.lock().unwrap();
        guard.streaming_active = router.is_some();
        guard.stream_router = router;
    }

    /// Sprint 25b — bind the per-frame audio sink. Called from
    /// `nativeStartRecording` to begin pumping frames during this
    /// session. Pass `None` to detach (e.g. between sessions, or when
    /// dual-write is disabled). Safe to call repeatedly — the
    /// underlying `RecordingSink` is referenced via `Arc` and remains
    /// alive while any reference exists.
    pub fn set_recording_sink(&mut self, sink: Option<Arc<RecordingSink>>) {
        let mut guard = self.inner.lock().unwrap();
        guard.recording_sink = sink;
    }

    /// Drain accumulated audio buffer without stopping the pipeline.
    /// Used to feed pre-recorded audio into a streaming session that
    /// starts mid-recording (after model loads in parallel).
    /// NOTE: `streaming_active` is NOT set here — it's set atomically
    /// in `set_stream_router()` to avoid a race window where frames
    /// arriving between drain_buffer() and set_stream_router() would
    /// be dropped (skipped from buffer AND not forwarded to router).
    pub fn drain_buffer(&self) -> Result<Vec<f32>, String> {
        if !self.running.load(Ordering::SeqCst) {
            return Err("Pipeline is not running".to_string());
        }
        let mut guard = self.inner.lock().unwrap();
        let drained = std::mem::take(&mut guard.audio_buffer);
        Ok(drained)
    }

    /// Start capture, query the actual device sample rate, and configure the
    /// resampler to convert from that rate to 16 kHz for the model.
    pub fn start(&mut self, _requested_rate: u32) -> Result<(), String> {
        if self.running.load(Ordering::SeqCst) {
            return Err("Pipeline is already running".to_string());
        }

        let inner = self.inner.clone();
        let running_flag = self.running.clone();

        let mut capture = AudioCapture::new();
        capture.set_callback(move |samples: &[f32]| {
            if !running_flag.load(Ordering::SeqCst) {
                return;
            }
            if let Ok(mut guard) = inner.lock() {
                guard.process_samples(samples);
            }
        });

        // Start capture — returns the actual sample rate of the device
        let actual_rate = capture.start().map_err(|e| match e {
            CaptureError::Aaudio(msg) => format!("AAudio: {msg}"),
            CaptureError::AlreadyOpen => "Capture already open".to_string(),
            CaptureError::NotOpen => "Capture not open".to_string(),
            CaptureError::Callback(msg) => format!("Callback: {msg}"),
        })?;

        log::info!("Audio capture started at {} Hz (native device rate), resampling to 16000 Hz", actual_rate);

        // Configure resampler to convert from device rate → 16 kHz
        {
            let mut guard = self.inner.lock().unwrap();
            guard.resampler = FrameResampler::new(actual_rate, 16000);
            guard.smoothed_vad.reset();
            guard.audio_buffer.clear();
            // Pre-allocate buffer for ~16 seconds of audio (262K f32 samples)
            // to avoid repeated reallocations during recording.
            guard.audio_buffer.reserve(262144);
            guard.capture_sample_rate = actual_rate;
            guard.total_raw_samples = 0;
            guard.streaming_active = false;
        }

        self.capture = Some(capture);
        self.running.store(true, Ordering::SeqCst);

        Ok(())
    }

    pub fn push_audio(&mut self, buffer: &[f32], _frame_count: usize) -> Result<(), String> {
        if !self.running.load(Ordering::SeqCst) {
            return Err("Pipeline is not running".to_string());
        }

        let mut guard = self.inner.lock().unwrap();
        guard.process_samples(buffer);
        Ok(())
    }

    pub fn stop(&mut self) -> Result<Vec<f32>, String> {
        self.running.store(false, Ordering::SeqCst);

        if let Some(ref mut capture) = self.capture {
            let _ = capture.stop();
            let _ = capture.close();
        }
        self.capture = None;

        let mut guard = self.inner.lock().unwrap();
        let mut result = std::mem::take(&mut guard.audio_buffer);

        // Flush resampler and append all remaining audio.
        {
            let mut resampler = std::mem::replace(
                &mut guard.resampler,
                FrameResampler::new(16000, 16000),
            );
            resampler.finish(|frame| {
                if let Some(ref router) = guard.stream_router {
                    router.feed(frame.to_vec());
                }
                result.extend_from_slice(frame);
            });
            guard.resampler = resampler;
        }

        guard.smoothed_vad.reset();
        guard.streaming_active = false;

        let raw_seconds = guard.total_raw_samples as f64 / guard.capture_sample_rate as f64;
        let out_seconds = result.len() as f64 / 16000.0;
        log::info!(
            "Pipeline stopped: {} raw samples ({:.2}s at {} Hz) → {} output samples ({:.2}s at 16 kHz)",
            guard.total_raw_samples,
            raw_seconds,
            guard.capture_sample_rate,
            result.len(),
            out_seconds,
        );

        Ok(result)
    }

    pub fn cancel(&mut self) -> Result<(), String> {
        self.running.store(false, Ordering::SeqCst);

        if let Some(ref mut capture) = self.capture {
            let _ = capture.stop();
            let _ = capture.close();
        }
        self.capture = None;

        let mut guard = self.inner.lock().unwrap();
        guard.audio_buffer.clear();
        guard.stream_router = None;
        guard.streaming_active = false;
        guard.smoothed_vad.reset();

        Ok(())
    }

    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }
}

impl Default for AudioPipeline {
    fn default() -> Self {
        Self::new()
    }
}
