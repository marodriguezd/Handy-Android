use std::collections::VecDeque;

#[derive(Debug, Clone, PartialEq)]
pub enum VadFrame {
    Speech(Vec<f32>),
    Noise,
}

pub trait VoiceActivityDetector: Send {
    fn is_voice(&mut self, frame: &[f32]) -> Result<bool, String>;
    fn reset(&mut self);
}

pub struct EnergyVad {
    threshold_factor: f32,
    frame_samples: usize,
    noise_floor: f32,
    noise_floor_min: f32,
    alpha: f32,
}

impl EnergyVad {
    pub fn new(threshold_factor: f32, frame_samples: usize) -> Self {
        Self {
            threshold_factor,
            frame_samples,
            noise_floor: 0.01,
            noise_floor_min: 0.001,
            alpha: 0.01,
        }
    }

    fn rms(samples: &[f32]) -> f32 {
        let sum_sq: f32 = samples.iter().map(|&s| s * s).sum();
        (sum_sq / samples.len() as f32).sqrt()
    }
}

impl VoiceActivityDetector for EnergyVad {
    fn is_voice(&mut self, frame: &[f32]) -> Result<bool, String> {
        if frame.len() != self.frame_samples {
            return Err(format!(
                "EnergyVad: expected {} samples, got {}",
                self.frame_samples,
                frame.len()
            ));
        }

        let energy = Self::rms(frame);
        if energy > self.noise_floor * self.threshold_factor {
            Ok(true)
        } else {
            self.noise_floor =
                self.noise_floor * (1.0 - self.alpha) + energy * self.alpha;
            self.noise_floor = self.noise_floor.max(self.noise_floor_min);
            Ok(false)
        }
    }

    fn reset(&mut self) {
        self.noise_floor = 0.01;
    }
}

pub struct SmoothedVad {
    inner: Box<dyn VoiceActivityDetector>,
    prefill_frames: usize,
    hangover_frames: usize,
    onset_frames: usize,
    in_speech: bool,
    onset_counter: usize,
    hangover_counter: usize,
    frame_buffer: VecDeque<Vec<f32>>,
    total_frames: usize,
}

impl SmoothedVad {
    pub fn new(
        detector: Box<dyn VoiceActivityDetector>,
        prefill: usize,
        hangover: usize,
        onset: usize,
    ) -> Self {
        Self {
            inner: detector,
            prefill_frames: prefill,
            hangover_frames: hangover,
            onset_frames: onset,
            in_speech: false,
            onset_counter: 0,
            hangover_counter: 0,
            frame_buffer: VecDeque::with_capacity(prefill),
            total_frames: 0,
        }
    }

    pub fn set_hangover_frames(&mut self, frames: usize) {
        self.hangover_frames = frames;
    }

    pub fn push_frame(&mut self, frame: &[f32]) -> VadFrame {
        self.frame_buffer.push_back(frame.to_vec());
        if self.frame_buffer.len() > self.prefill_frames {
            self.frame_buffer.pop_front();
        }

        if self.total_frames < self.prefill_frames {
            self.total_frames += 1;
            return VadFrame::Noise;
        }

        let is_voice = match self.inner.is_voice(frame) {
            Ok(v) => v,
            Err(e) => {
                log::warn!("VAD error: {}", e);
                false
            }
        };

        match (is_voice, self.in_speech) {
            (true, false) => {
                self.onset_counter += 1;
                if self.onset_counter >= self.onset_frames {
                    self.in_speech = true;
                    self.onset_counter = 0;
                    self.hangover_counter = 0;
                    let speech_frames = self.drain_prefill();
                    let combined: Vec<f32> = speech_frames.into_iter().flatten().collect();
                    VadFrame::Speech(combined)
                } else {
                    VadFrame::Noise
                }
            }
            (true, true) => {
                self.hangover_counter = 0;
                VadFrame::Speech(frame.to_vec())
            }
            (false, true) => {
                self.hangover_counter += 1;
                if self.hangover_counter >= self.hangover_frames {
                    self.in_speech = false;
                    self.hangover_counter = 0;
                    VadFrame::Noise
                } else {
                    VadFrame::Speech(frame.to_vec())
                }
            }
            (false, false) => {
                self.onset_counter = 0;
                VadFrame::Noise
            }
        }
    }

    pub fn drain_prefill(&mut self) -> Vec<Vec<f32>> {
        self.frame_buffer.drain(..).collect()
    }

    pub fn reset(&mut self) {
        self.inner.reset();
        self.in_speech = false;
        self.onset_counter = 0;
        self.hangover_counter = 0;
        self.frame_buffer.clear();
        self.total_frames = 0;
    }

    pub fn in_speech(&self) -> bool {
        self.in_speech
    }
}

pub const VAD_SAMPLE_RATE: u32 = 16000;
pub const VAD_FRAME_MS: u32 = 30;
pub const VAD_FRAME_SAMPLES: usize = (VAD_SAMPLE_RATE as usize * VAD_FRAME_MS as usize) / 1000;

pub const VAD_PREFILL_FRAMES: usize = 15;
pub const VAD_ONSET_FRAMES: usize = 2;
pub const VAD_OFFLINE_HANGOVER_FRAMES: usize = 15;
pub const VAD_STREAMING_HANGOVER_FRAMES: usize = 55;
pub const VAD_THRESHOLD: f32 = 0.3;
