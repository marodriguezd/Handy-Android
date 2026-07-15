use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use crate::audio::capture::{AudioCapture, CaptureError};
use crate::audio::resampler::FrameResampler;
use crate::audio::vad::{
    EnergyVad, SmoothedVad, VadFrame, VAD_FRAME_SAMPLES, VAD_ONSET_FRAMES,
    VAD_PREFILL_FRAMES, VAD_STREAMING_HANGOVER_FRAMES, VAD_THRESHOLD,
};

struct PipelineInner {
    resampler: FrameResampler,
    smoothed_vad: SmoothedVad,
    audio_buffer: Vec<f32>,
    on_vad_level: Option<Arc<dyn Fn(f32) + Send + Sync>>,
    on_audio_frame: Option<Arc<dyn Fn(Vec<f32>) + Send + Sync>>,
    /// The actual sample rate of the capture stream (device native rate).
    capture_sample_rate: u32,
}



impl PipelineInner {
    fn process_samples(&mut self, samples: &[f32]) {
        const MAX_BUFFER_SAMPLES: usize = 300 * 16000; // ~19.2MB float32 (at 16kHz equivalent)
        if self.audio_buffer.len() > MAX_BUFFER_SAMPLES {
            log::warn!("Audio buffer exceeded max ({} samples), truncating", MAX_BUFFER_SAMPLES);
            self.audio_buffer.clear();
        }
        self.resampler.push(samples, &mut |frame| {
            let frame = frame.to_vec();
            self.audio_buffer.extend_from_slice(&frame);

            // VAD still runs for the level meter visualization in the UI.
            let vad_result = self.smoothed_vad.push_frame(&frame);
            if let Some(ref cb) = self.on_vad_level {
                let energy = frame.iter().map(|&s| s * s).sum::<f32>() / frame.len() as f32;
                let level = (energy.sqrt() * 5.0).min(1.0);
                match vad_result {
                    VadFrame::Speech(_) => cb(level),
                    VadFrame::Noise => cb(level * 0.5),
                }
            }
        });
    }
}

pub struct AudioPipeline {
    capture: Option<AudioCapture>,
    inner: Arc<Mutex<PipelineInner>>,
    running: Arc<AtomicBool>,
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

        Self {
            capture: None,
            inner: Arc::new(Mutex::new(PipelineInner {
                resampler,
                smoothed_vad,
                audio_buffer: Vec::new(),
                on_vad_level: None,
                on_audio_frame: None,
                capture_sample_rate: 0,
            })),
            running: Arc::new(AtomicBool::new(false)),
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

        log::info!("Audio capture started at {} Hz (native device rate)", actual_rate);

        // Configure resampler to convert from device rate → 16 kHz
        {
            let mut guard = self.inner.lock().unwrap();
            guard.resampler = FrameResampler::new(actual_rate, 16000);
            guard.smoothed_vad.reset();
            guard.audio_buffer.clear();
            guard.capture_sample_rate = actual_rate;
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
                result.extend_from_slice(frame);
            });
            guard.resampler = resampler;
        }

        guard.smoothed_vad.reset();

        log::info!(
            "Pipeline stopped: {} samples ({} s at 16 kHz)",
            result.len(),
            result.len() as f64 / 16000.0
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
