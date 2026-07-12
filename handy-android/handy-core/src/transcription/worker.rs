use std::sync::mpsc;
use std::thread;

use crate::transcription::router::StreamCmd;

/// Threshold for partial result emission (~500ms at 16kHz)
const PARTIAL_FRAME_THRESHOLD: usize = 8000;
/// Reference sample rate for audio statistics
const SAMPLE_RATE: f64 = 16000.0;

pub struct StreamWorker {
    worker_id: u64,
    handle: Option<thread::JoinHandle<()>>,
}

impl StreamWorker {
    pub fn new(worker_id: u64) -> Self {
        Self {
            worker_id,
            handle: None,
        }
    }

    pub fn worker_id(&self) -> u64 {
        self.worker_id
    }

    /// Spawn a background thread that processes streaming audio.
    /// The thread accumulates audio frames, emits partial results every ~500ms,
    /// and produces a final result on Finalize.
    pub fn spawn(
        &mut self,
        rx: mpsc::Receiver<StreamCmd>,
        on_partial: impl Fn(String) + Send + 'static,
        on_final: impl Fn(String) + Send + 'static,
    ) -> u64 {
        let worker_id = self.worker_id;
        let handle = thread::spawn(move || {
            let mut buffer: Vec<f32> = Vec::new();
            let mut last_partial_pos: usize = 0;

            for cmd in rx {
                match cmd {
                    StreamCmd::Feed(audio) => {
                        buffer.extend(audio);
                        if buffer.len().saturating_sub(last_partial_pos) >= PARTIAL_FRAME_THRESHOLD
                        {
                            let n = buffer.len();
                            on_partial(format!("Processing {} frames...", n));
                            last_partial_pos = buffer.len();
                        }
                    }
                    StreamCmd::Finalize => {
                        let n = buffer.len();
                        let duration = n as f64 / SAMPLE_RATE;
                        let text = format!(
                            "Transcribed {} frames at {}Hz ({:.2}s)",
                            n, SAMPLE_RATE as u64, duration
                        );
                        on_final(text);
                        break;
                    }
                    StreamCmd::Cancel => {
                        break;
                    }
                }
            }
        });
        self.handle = Some(handle);
        worker_id
    }

    /// Join the worker thread, blocking until it completes.
    pub fn join(&mut self) {
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
    }
}

impl Drop for StreamWorker {
    fn drop(&mut self) {
        self.join();
    }
}
