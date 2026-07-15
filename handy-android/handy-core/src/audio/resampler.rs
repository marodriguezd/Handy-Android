use rubato::{FftFixedIn, Resampler};

pub struct FrameResampler {
    resampler: Option<FftFixedIn<f32>>,
    frame_samples: usize,
    pending: Vec<f32>,
    /// Buffer for input samples that didn't make a complete resampler
    /// input chunk yet. Preserved between push() calls so no samples
    /// are lost when AAudio delivers partial buffers.
    input_pending: Vec<f32>,
    input_rate: u32,
    output_rate: u32,
    /// Total input samples fed (for diagnostic logging).
    total_input_samples: u64,
    /// Total frames emitted (for diagnostic logging).
    total_output_frames: u64,
}

impl FrameResampler {
    pub fn new(input_rate: u32, output_rate: u32) -> Self {
        let chunk_in = 1024;
        let frame_samples = 480; // 30ms @ 16kHz

        let resampler = if input_rate != output_rate {
            match FftFixedIn::<f32>::new(
                input_rate as usize,
                output_rate as usize,
                chunk_in,
                4,
                1,
            ) {
                Ok(r) => Some(r),
                Err(e) => {
                    log::warn!("Failed to create resampler ({}), falling back to passthrough", e);
                    None
                }
            }
        } else {
            None
        };

        Self {
            resampler,
            frame_samples,
            pending: Vec::new(),
            input_pending: Vec::new(),
            input_rate,
            output_rate,
            total_input_samples: 0,
            total_output_frames: 0,
        }
    }

    /// Push audio samples through the resampler.
    /// Samples are resampled from device native rate → 16 kHz.
    /// Any leftover input samples that don't fill a complete resampler
    /// input chunk are stored in self.input_pending for the next call.
    pub fn push(&mut self, src: &[f32], emit: &mut impl FnMut(&[f32])) {
        self.total_input_samples += src.len() as u64;

        if let Some(ref mut resampler) = self.resampler {
            // Accumulate new samples into the input pending buffer
            self.input_pending.extend_from_slice(src);

            // Process as many complete input chunks as we have buffered
            while self.input_pending.len() >= resampler.input_frames_next() {
                let need = resampler.input_frames_next();
                let chunk: Vec<f32> = self.input_pending.drain(..need).collect();
                let waves_in = vec![chunk];
                match resampler.process(&waves_in, None) {
                    Ok(output) => {
                        if let Some(channel) = output.first() {
                            self.pending.extend_from_slice(channel);
                        }
                    }
                    Err(e) => {
                        log::warn!("Resampler process error: {}", e);
                        break;
                    }
                }
            }
            // Remaining samples stay in self.input_pending for next call.
            // This is the core fix: previously, leftovers were dropped.
        } else {
            // No resampler needed (input_rate == output_rate == 16000)
            self.pending.extend_from_slice(src);
        }

        // Emit complete 480-sample frames (30ms @ 16kHz)
        while self.pending.len() >= self.frame_samples {
            let frame = self.pending[..self.frame_samples].to_vec();
            self.total_output_frames += 1;
            emit(&frame);
            self.pending.drain(..self.frame_samples);
        }
    }

    /// Flush any remaining samples through the resampler and emit
    /// all remaining output. Called at pipeline stop.
    pub fn finish(&mut self, mut emit: impl FnMut(&[f32])) {
        if let Some(ref mut resampler) = self.resampler {
            // Push any leftover input samples through the resampler
            // by padding with zeros to reach the next required input size.
            if !self.input_pending.is_empty() {
                let need = resampler.input_frames_next();
                let mut chunk = std::mem::take(&mut self.input_pending);
                if chunk.len() < need {
                    chunk.resize(need, 0.0);
                }
                let waves_in = vec![chunk];
                match resampler.process(&waves_in, None) {
                    Ok(output) => {
                        if let Some(channel) = output.first() {
                            self.pending.extend_from_slice(channel);
                        }
                    }
                    Err(e) => {
                        log::warn!("Resampler finish process error: {}", e);
                    }
                }
            }

            // Flush any remaining state from the resampler
            match resampler.process_partial::<Vec<f32>>(None, None) {
                Ok(output) => {
                    if let Some(channel) = output.first() {
                        self.pending.extend_from_slice(channel);
                    }
                }
                Err(e) => {
                    log::warn!("Resampler finish partial error: {}", e);
                }
            }
        }

        // Emit all remaining pending frames
        if !self.pending.is_empty() {
            emit(&self.pending);
            self.pending.clear();
        }

        // Diagnostic: log sample accounting
        let expected_output_samples = if self.output_rate > 0 && self.input_rate > 0 {
            (self.total_input_samples as u64 * self.output_rate as u64) / self.input_rate as u64
        } else {
            0
        };
        log::info!(
            "Resampler: {} input samples → {} frames ({} samples) output, ~{} expected at {}→{} kHz",
            self.total_input_samples,
            self.total_output_frames,
            self.total_output_frames * self.frame_samples as u64,
            expected_output_samples,
            self.input_rate,
            self.output_rate,
        );
    }

    pub fn reset(&mut self) {
        self.pending.clear();
        self.input_pending.clear();
        self.total_input_samples = 0;
        self.total_output_frames = 0;
        if let Some(ref mut resampler) = self.resampler {
            let _ = resampler.reset();
        }
    }

    pub fn input_rate(&self) -> u32 {
        self.input_rate
    }

    pub fn output_rate(&self) -> u32 {
        self.output_rate
    }
}
