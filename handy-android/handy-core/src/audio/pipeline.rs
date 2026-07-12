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
}

impl PipelineInner {
    fn process_samples(&mut self, samples: &[f32]) {
        self.resampler.push(samples, &mut |frame| {
            let vad_result = self.smoothed_vad.push_frame(frame);
            match vad_result {
                VadFrame::Speech(data) => {
                    self.audio_buffer.extend_from_slice(&data);
                    if let Some(ref cb) = self.on_audio_frame {
                        cb(data);
                    }
                    if let Some(ref cb) = self.on_vad_level {
                        let energy = frame.iter().map(|&s| s * s).sum::<f32>() / frame.len() as f32;
                        let level = (energy.sqrt() * 5.0).min(1.0);
                        cb(level);
                    }
                }
                VadFrame::Noise => {
                    if let Some(ref cb) = self.on_vad_level {
                        let energy = frame.iter().map(|&s| s * s).sum::<f32>() / frame.len() as f32;
                        let level = (energy.sqrt() * 5.0).min(1.0);
                        cb(level * 0.5);
                    }
                }
            }
        });
    }
}

pub struct AudioPipeline {
    capture: Option<AudioCapture>,
    inner: Arc<Mutex<PipelineInner>>,
    sample_rate: u32,
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
            })),
            sample_rate: 0,
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

    pub fn start(&mut self, sample_rate: u32) -> Result<(), String> {
        if self.running.load(Ordering::SeqCst) {
            return Err("Pipeline is already running".to_string());
        }

        self.sample_rate = sample_rate;

        {
            let mut guard = self.inner.lock().unwrap();
            guard.resampler = FrameResampler::new(sample_rate, 16000);
            guard.smoothed_vad.reset();
            guard.audio_buffer.clear();
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

        capture.start().map_err(|e| match e {
            CaptureError::Aaudio(msg) => format!("AAudio: {}", msg),
            CaptureError::AlreadyOpen => "Capture already open".to_string(),
            CaptureError::NotOpen => "Capture not open".to_string(),
            CaptureError::Callback(msg) => format!("Callback: {}", msg),
        })?;

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

        {
            let mut resampler = std::mem::replace(
                &mut guard.resampler,
                FrameResampler::new(16000, 16000),
            );
            let mut extra = Vec::new();
            resampler.finish(|frame| {
                extra.extend_from_slice(frame);
            });

            for frame in extra.chunks(VAD_FRAME_SAMPLES) {
                if frame.len() == VAD_FRAME_SAMPLES {
                    let vad_result = guard.smoothed_vad.push_frame(frame);
                    if let VadFrame::Speech(data) = vad_result {
                        result.extend_from_slice(&data);
                    }
                } else {
                    result.extend_from_slice(frame);
                }
            }

            guard.resampler = resampler;
        }

        guard.smoothed_vad.reset();

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
