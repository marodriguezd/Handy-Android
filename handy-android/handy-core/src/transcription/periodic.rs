use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc;
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

use transcribe_cpp::{RunOptions, Task};

use crate::transcription::engine::normalize_peak;
use crate::transcription::router::StreamCmd;

/// Interval between partial batch transcriptions (in seconds).
/// Every PARTIAL_INTERVAL_SECS, the accumulated audio is transcribed
/// and dispatched as partial text, giving the user live feedback
/// even with non-streaming models.
const PARTIAL_INTERVAL_SECS: f32 = 3.0;

/// Minimum audio duration required to attempt a partial transcription (in seconds).
/// Avoids running transcription on near-silence or very short segments.
const MIN_AUDIO_SECS: f32 = 1.0;

pub struct PeriodicWorker {
    worker_id: u64,
    handle: Option<thread::JoinHandle<()>>,
}

impl PeriodicWorker {
    pub fn new(worker_id: u64) -> Self {
        Self {
            worker_id,
            handle: None,
        }
    }

    /// Spawn a background thread that accumulates audio frames and periodically
    /// runs batch transcription to produce partial results.
    ///
    /// This is the fallback for models that don't support native streaming
    /// (e.g., most Whisper GGUF models). Instead of using transcribe-cpp's
    /// streaming API, we run `session.run()` every few seconds on the growing
    /// audio buffer, dispatching the result as partial text.
    ///
    /// `cancel_flag` is shared with `TranscriptionEngine` — when set to true,
    /// the worker aborts any in-progress or queued `session.run()` and exits.
    ///
    /// On `Finalize`, the final accumulated audio is transcribed and the result
    /// is sent back via the reply channel.
    pub fn spawn(
        &mut self,
        rx: mpsc::Receiver<StreamCmd>,
        mut session: transcribe_cpp::Session,
        cancel_flag: Arc<AtomicBool>,
        language: Option<String>,
        on_partial: impl Fn(String) + Send + 'static,
    ) -> u64 {
        let worker_id = self.worker_id;

        let handle = thread::spawn(move || {
            let run_options = RunOptions {
                task: Task::Transcribe,
                language,
                target_language: None,
                family: None,
                ..Default::default()
            };

            // Accumulate all audio received via Feed commands
            let mut accumulated: Vec<f32> = Vec::new();
            let mut last_partial_time = Instant::now();
            let interval = Duration::from_secs_f32(PARTIAL_INTERVAL_SECS);
            let min_audio_samples = (16000.0 * MIN_AUDIO_SECS) as usize;

            // Use a non-blocking receive with timeout so we can periodically
            // check if it's time to run a partial transcription.
            'recv: loop {
                // Check for new commands with a short timeout
                match rx.recv_timeout(Duration::from_millis(500)) {
                    Ok(cmd) => match cmd {
                        StreamCmd::Feed(audio) => {
                            accumulated.extend_from_slice(&audio);

                            // Check if it's time for a partial update
                            if accumulated.len() >= min_audio_samples
                                && last_partial_time.elapsed() >= interval
                            {
                                // Check cancellation before expensive inference
                                if cancel_flag.load(Ordering::SeqCst) {
                                    log::info!("[handy-core] periodic partial cancelled");
                                    break 'recv;
                                }
                                last_partial_time = Instant::now();
                                let t0 = Instant::now();
                                let normalized = normalize_peak(&accumulated);
                                match session.run(&normalized, &run_options) {
                                    Ok(transcript) => {
                                        // Discard result if cancelled during inference
                                        if cancel_flag.load(Ordering::SeqCst) {
                                            log::info!("[handy-core] periodic partial result discarded (cancelled)");
                                            break 'recv;
                                        }
                                        let text = transcript.text;
                                        if !text.is_empty() {
                                            log::debug!(
                                                "[handy-core] periodic partial ({} chars) in {:?}: '{}'",
                                                text.len(),
                                                t0.elapsed(),
                                                text,
                                            );
                                            on_partial(text);
                                        }
                                    }
                                    Err(e) => {
                                        log::warn!(
                                            "[handy-core] periodic partial error: {e:?}"
                                        );
                                    }
                                }
                            }
                        }
                        StreamCmd::Finalize(reply) => {
                            let t0 = Instant::now();
                            let normalized = normalize_peak(&accumulated);
                            match session.run(&normalized, &run_options) {
                                Ok(transcript) => {
                                    let text = transcript.text;
                                    log::debug!(
                                        "[handy-core] periodic final ({} chars) in {:?}",
                                        text.len(),
                                        t0.elapsed(),
                                    );
                                    let _ = reply.send(Some(text));
                                }
                                Err(e) => {
                                    log::warn!(
                                        "[handy-core] periodic final error: {e:?}"
                                    );
                                    let _ = reply.send(None);
                                }
                            }
                            break 'recv;
                        }
                        StreamCmd::Cancel => {
                            break 'recv;
                        }
                    },
                    Err(mpsc::RecvTimeoutError::Timeout) => {
                        // No message received in this interval.
                        // Check if we should run a partial based on time.
                        if accumulated.len() >= min_audio_samples
                            && last_partial_time.elapsed() >= interval
                        {
                            // Check cancellation before expensive inference
                            if cancel_flag.load(Ordering::SeqCst) {
                                log::info!("[handy-core] periodic partial cancelled (timeout branch)");
                                break 'recv;
                            }
                            last_partial_time = Instant::now();
                            let t0 = Instant::now();
                            let normalized = normalize_peak(&accumulated);
                            match session.run(&normalized, &run_options) {
                                Ok(transcript) => {
                                    // Discard result if cancelled during inference
                                    if cancel_flag.load(Ordering::SeqCst) {
                                        log::info!("[handy-core] periodic partial result discarded (cancelled)");
                                        break 'recv;
                                    }
                                    let text = transcript.text;
                                    if !text.is_empty() {
                                        log::debug!(
                                            "[handy-core] periodic partial ({} chars) in {:?}: '{}'",
                                            text.len(),
                                            t0.elapsed(),
                                            text,
                                        );
                                        on_partial(text);
                                    }
                                }
                                Err(e) => {
                                    log::warn!(
                                        "[handy-core] periodic partial error: {e:?}"
                                    );
                                }
                            }
                        }
                    }
                    Err(mpsc::RecvTimeoutError::Disconnected) => {
                        break 'recv;
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

impl Drop for PeriodicWorker {
    fn drop(&mut self) {
        self.join();
    }
}
