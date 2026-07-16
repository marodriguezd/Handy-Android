use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use log::{info, warn};
use transcribe_cpp::{Backend, Model, ModelOptions, RunOptions, StreamOptions, Task};

use crate::transcription::periodic::PeriodicWorker;
use crate::transcription::router::{StreamCmd, StreamRouter};
use crate::transcription::worker::StreamWorker;

/// Union type for either a streaming worker or a periodic batch worker.
/// Both implement the same channel protocol (Feed/Finalize/Cancel via StreamRouter).
enum StreamWorkerOrPeriodic {
    Streaming(StreamWorker),
    Periodic(PeriodicWorker),
}

pub struct TranscriptionEngine {
    active_model_path: Option<PathBuf>,
    model: Mutex<Option<Model>>,
    is_loaded: AtomicBool,
    post_process_endpoint: Option<String>,
    post_process_api_key: Option<String>,
    stream_router: Arc<StreamRouter>,
    stream_worker: Mutex<Option<StreamWorkerOrPeriodic>>,
    is_streaming: AtomicBool,
    worker_id_counter: std::sync::atomic::AtomicU64,
    /// Shared flag to signal cancellation of a running batch transcription.
    /// Checked before and after session.run() to skip/discard results when
    /// the user cancels recording while inference is queued or in progress.
    cancel_flag: Arc<AtomicBool>,
}

impl TranscriptionEngine {
    pub fn new() -> Self {
        Self {
            active_model_path: None,
            model: Mutex::new(None),
            is_loaded: AtomicBool::new(false),
            post_process_endpoint: None,
            post_process_api_key: None,
            stream_router: Arc::new(StreamRouter::new()),
            stream_worker: Mutex::new(None),
            is_streaming: AtomicBool::new(false),
            worker_id_counter: std::sync::atomic::AtomicU64::new(1),
            cancel_flag: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn load_model(&mut self, path: &PathBuf) -> Result<(), String> {
        info!("[handy-core] loading model from: {:?}", path);

        // Use explicit CPU backend (only backend available on Android).
        // GPU device 0 is ignored for CPU backend but must NOT be -1, as
        // whisper.cpp internally asserts gpu_device >= 0.
        // This matches desktop Handy's convention for explicit CPU selection.
        let model_options = ModelOptions {
            backend: Backend::Auto,
            gpu_device: 0,
        };

        // Check file exists and is readable
        match std::fs::metadata(path) {
            Ok(meta) => info!("[handy-core] model file: {} bytes", meta.len()),
            Err(e) => warn!("[handy-core] model file metadata failed: {e}"),
        }

        let model = match Model::load_with(path, &model_options) {
            Ok(m) => m,
            Err(e) => {
                let err_msg = format!("Failed to load model: {e}");
                warn!("[handy-core] {err_msg}");
                return Err(err_msg);
            }
        };

        // Log the backend that transcribe-cpp actually bound to. On Android
        // this is typically "cpu" today, but if the build ever includes
        // Vulkan/QNN support, Auto will bind to the best available backend.
        info!("[handy-core] model loaded, backend='{}'", model.backend());

        self.active_model_path = Some(path.clone());
        *self.model.lock().unwrap() = Some(model);
        self.is_loaded.store(true, Ordering::Release);

        info!("[handy-core] model loaded successfully");
        Ok(())
    }

    pub fn unload_model(&self) {
        *self.model.lock().unwrap() = None;
        self.is_loaded.store(false, Ordering::Release);
        info!("[handy-core] model unloaded");
    }

    pub fn is_model_loaded(&self) -> bool {
        self.is_loaded.load(Ordering::Acquire)
    }

    /// Run batch transcription on a full PCM buffer using session.run().
    /// This is the correct API for Whisper GGUF models (they don't support
    /// streaming via session.stream()).
    ///
    /// Checks `cancel_flag` before and after `session.run()`. If cancellation
    /// was requested, the call returns early or discards the result.
    pub fn run(&self, pcm: &[f32]) -> Result<String, String> {
        // Reset cancel flag at the start of a fresh run
        self.cancel_flag.store(false, Ordering::SeqCst);

        // Check cancellation before doing any work
        if self.cancel_flag.load(Ordering::SeqCst) {
            info!("[handy-core] batch run cancelled before inference");
            return Err("Transcription cancelled".to_string());
        }

        let mut model_guard = self.model.lock().map_err(|e| e.to_string())?;
        let model = model_guard
            .as_mut()
            .ok_or_else(|| "No model loaded".to_string())?;

        let mut session = model
            .session()
            .map_err(|e| format!("Failed to create session: {e}"))?;
        drop(model_guard);

        // Peak-normalize the audio so Whisper gets a full-range signal.
        let normalized = normalize_peak(pcm);
        let rms = compute_rms(&normalized);

        let run_options = RunOptions {
            task: Task::Transcribe,
            language: Some("es".to_string()),
            target_language: None,
            ..Default::default()
        };

        // Check cancellation again before the expensive inference call
        if self.cancel_flag.load(Ordering::SeqCst) {
            info!("[handy-core] batch run cancelled before session.run()");
            return Err("Transcription cancelled".to_string());
        }

        info!("[handy-core] running transcription on {} samples, rms={:.4}, language=es", pcm.len(), rms);
        let transcript = session
            .run(&normalized, &run_options)
            .map_err(|e| format!("Transcription run failed: {e}"))?;

        // Discard the result if cancellation was requested during inference
        if self.cancel_flag.load(Ordering::SeqCst) {
            info!("[handy-core] batch run result discarded (cancelled during inference)");
            return Err("Transcription cancelled".to_string());
        }

        info!("[handy-core] transcription complete: {} chars: '{}'", transcript.text.len(), transcript.text);
        Ok(transcript.text)
    }

    pub fn supports_streaming(&self) -> Result<bool, String> {
        let mut model_guard = self.model.lock().map_err(|e| e.to_string())?;
        let model = model_guard
            .as_mut()
            .ok_or_else(|| "No model loaded".to_string())?;
        let session = model
            .session()
            .map_err(|e| format!("Failed to create session: {e}"))?;
        let caps = session.model().capabilities();
        Ok(caps.supports_streaming)
    }

    pub fn start_stream<F>(&self, on_partial: F) -> Result<bool, String>
    where
        F: Fn(String) + Send + 'static,
    {
        if self.is_streaming_active() {
            return Err("A stream is already active".to_string());
        }

        // Reset cancel flag for the new recording session
        self.cancel_flag.store(false, Ordering::SeqCst);

        let mut model_guard = self.model.lock().map_err(|e| e.to_string())?;
        let model = model_guard
            .as_mut()
            .ok_or_else(|| "No model loaded".to_string())?;

        let mut session = model
            .session()
            .map_err(|e| format!("Failed to create session: {e}"))?;

        let arch = session.model().arch();
        let variant = session.model().variant();

        // Try to create a stream to verify support.
        // If it fails, fall back to batch.
        // We drop the test stream first, then move the session into the worker
        // where it creates its own stream internally (Stream borrows Session
        // with a non-'static lifetime, so they must live in the same thread).
        let run_options = RunOptions {
            task: Task::Transcribe,
            language: None,
            target_language: None,
            family: None,
            ..Default::default()
        };

        match session.stream(&run_options, &StreamOptions::default()) {
            Ok(_) => {
                // Stream dropped immediately, releasing the mutable borrow on session
                // so it can be moved into the worker thread below.
                info!(
                    "[handy-core] model supports streaming: arch='{arch}' variant='{variant}'"
                );
            }
            Err(e) => {
                info!(
                    "[handy-core] model does not support streaming (arch='{arch}' variant='{variant}'): {e}; using batch"
                );
                return Ok(false);
            }
        };

        let rx = self.stream_router.open_channel();
        let worker_id = self.worker_id_counter.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        let mut worker = StreamWorker::new(worker_id);
        // Move the session into the worker — stream was dropped so session is free
        worker.spawn(rx, session, on_partial);

        *self.stream_worker.lock().unwrap() = Some(StreamWorkerOrPeriodic::Streaming(worker));
        self.is_streaming.store(true, std::sync::atomic::Ordering::Release);
        info!("[handy-core] streaming transcription started");
        Ok(true)
    }

    pub fn finalize_stream(&self) -> Option<String> {
        use std::time::Duration;
        let (reply_tx, reply_rx) = std::sync::mpsc::channel();
        if self.stream_router.send(StreamCmd::Finalize(reply_tx)).is_err() {
            warn!("[handy-core] finalize_stream: router send failed");
            self.is_streaming.store(false, std::sync::atomic::Ordering::Release);
            *self.stream_worker.lock().unwrap() = None;
            return None;
        }
        match reply_rx.recv_timeout(Duration::from_secs(30)) {
            Ok(result) => {
                self.is_streaming.store(false, std::sync::atomic::Ordering::Release);
                *self.stream_worker.lock().unwrap() = None;
                self.stream_router.close();
                result
            }
            Err(e) => {
                warn!("[handy-core] finalize_stream: timeout/error waiting for worker: {e}");
                self.is_streaming.store(false, std::sync::atomic::Ordering::Release);
                *self.stream_worker.lock().unwrap() = None;
                self.stream_router.close();
                None
            }
        }
    }

    pub fn cancel_stream(&self) {
        self.cancel_flag.store(true, Ordering::SeqCst);
        let _ = self.stream_router.send(StreamCmd::Cancel);
        self.is_streaming.store(false, std::sync::atomic::Ordering::Release);
        *self.stream_worker.lock().unwrap() = None;
        self.stream_router.close();
    }

    // ── Periodic (fallback) Transcription ─────────────────────

    /// Start periodic batch transcription as a fallback when the model
    /// doesn't support native streaming. Every ~3 seconds, runs
    /// `session.run()` on the accumulated audio and dispatches the
    /// result as partial text via `on_partial`.
    pub fn start_periodic<F>(&self, on_partial: F) -> Result<bool, String>
    where
        F: Fn(String) + Send + 'static,
    {
        if self.is_streaming_active() {
            return Err("A stream is already active".to_string());
        }

        // Reset cancel flag for the new recording session
        self.cancel_flag.store(false, Ordering::SeqCst);

        let mut model_guard = self.model.lock().map_err(|e| e.to_string())?;
        let model = model_guard
            .as_mut()
            .ok_or_else(|| "No model loaded".to_string())?;

        let session = model
            .session()
            .map_err(|e| format!("Failed to create session: {e}"))?;
        drop(model_guard);

        let rx = self.stream_router.open_channel();
        let worker_id = self.worker_id_counter.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        let mut worker = PeriodicWorker::new(worker_id);
        worker.spawn(rx, session, self.cancel_flag.clone(), on_partial);

        *self.stream_worker.lock().unwrap() = Some(StreamWorkerOrPeriodic::Periodic(worker));
        self.is_streaming.store(true, std::sync::atomic::Ordering::Release);
        info!("[handy-core] periodic batch transcription started");
        Ok(true)
    }

    /// Finalize periodic transcription. Sends Finalize command and waits
    /// for the worker to return the final result.
    pub fn finalize_periodic(&self) -> Option<String> {
        self.finalize_stream()  // Same channel protocol — Finalize command works for both
    }

    /// Cancel periodic transcription.
    pub fn cancel_periodic(&self) {
        self.cancel_stream()  // Same channel protocol — Cancel command works for both
    }

    pub fn is_streaming_active(&self) -> bool {
        self.is_streaming.load(std::sync::atomic::Ordering::Acquire)
    }

    pub fn stream_router(&self) -> &Arc<StreamRouter> {
        &self.stream_router
    }

    pub fn cancel(&self) {
        self.cancel_flag.store(true, Ordering::SeqCst);
        info!("[handy-core] cancel requested — cancel_flag set");
    }

    pub fn set_post_process_config(
        &mut self,
        endpoint: Option<String>,
        api_key: Option<String>,
    ) {
        self.post_process_endpoint = endpoint;
        self.post_process_api_key = api_key;
    }
}

impl Default for TranscriptionEngine {
    fn default() -> Self {
        Self::new()
    }
}

/// Remove common filler words from transcription text.
pub fn remove_filler_words(text: &str) -> String {
    let fillers = [
        "um", "uh", "hmm", "mm", "ah", "er", "huh",
        "like", "you know", "you see", "i mean",
        "sort of", "kind of", "actually", "basically",
        "literally", "seriously", "honestly", "right",
    ];
    let mut result = text.to_string();

    for filler in &fillers {
        loop {
            let lower = result.to_lowercase();
            let filler_lower = filler.to_lowercase();

            let mut pos = 0;
            let mut found = false;
            let bytes = lower.as_bytes();
            let filler_bytes = filler_lower.as_bytes();

            while pos + filler_bytes.len() <= bytes.len() {
                if &bytes[pos..pos + filler_bytes.len()] == filler_bytes {
                    let boundary_before = pos == 0
                        || bytes[pos - 1].is_ascii_whitespace()
                        || bytes[pos - 1].is_ascii_punctuation();
                    let boundary_after = pos + filler_bytes.len() >= bytes.len()
                        || bytes[pos + filler_bytes.len()].is_ascii_whitespace()
                        || bytes[pos + filler_bytes.len()].is_ascii_punctuation();

                    if boundary_before && boundary_after {
                        result.drain(pos..pos + filler_bytes.len());
                        found = true;
                        break;
                    }
                }
                pos += 1;
            }

            if !found {
                break;
            }
        }
    }

    let mut cleaned = String::new();
    let mut prev_space = false;
    for c in result.chars() {
        if c == ' ' {
            if prev_space {
                continue;
            }
            prev_space = true;
        } else {
            prev_space = false;
        }
        cleaned.push(c);
    }
    cleaned = cleaned.trim().to_string();

    let bytes: Vec<u8> = cleaned.bytes().collect();
    let mut filtered: Vec<u8> = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if i + 1 < bytes.len() && bytes[i] == b' ' && bytes[i + 1].is_ascii_punctuation() {
            i += 1;
            continue;
        }
        filtered.push(bytes[i]);
        i += 1;
    }
    cleaned = String::from_utf8(filtered).unwrap_or(cleaned);

    let start = cleaned.find(|c: char| c.is_alphanumeric());
    let end = cleaned.rfind(|c: char| c.is_alphanumeric());
    match (start, end) {
        (Some(s), Some(e)) if s <= e => cleaned[s..=e].to_string(),
        _ => cleaned,
    }
}

/// Collapse 3+ consecutive identical words into a single word.
pub fn collapse_stutters(text: &str) -> String {
    let words: Vec<&str> = text.split_whitespace().collect();
    if words.is_empty() {
        return String::new();
    }

    let mut result: Vec<&str> = Vec::new();
    let mut i = 0;
    while i < words.len() {
        let current = words[i];
        let mut count = 1;
        while i + count < words.len() && words[i + count] == current {
            count += 1;
        }
        if count >= 3 {
            result.push(current);
        } else {
            result.extend(std::iter::repeat_n(current, count));
        }
        i += count;
    }
    result.join(" ")
}

/// Peak-normalize audio samples so the maximum absolute value reaches ~0.95.
/// This ensures Whisper receives audio with adequate signal level regardless
/// of microphone sensitivity. Returns a new Vec<f32> with the normalized samples.
pub fn normalize_peak(samples: &[f32]) -> Vec<f32> {
    if samples.is_empty() {
        return Vec::new();
    }

    // Find the peak (maximum absolute) sample value
    let peak = samples.iter().fold(0.0f32, |max, &s| max.max(s.abs()));

    if peak < 0.0001 {
        // Near-silence — return as-is
        log::warn!("[handy-core] normalize_peak: near-silence (peak={:.6})", peak);
        return samples.to_vec();
    }

    let target_peak = 0.95f32;
    let gain = target_peak / peak;

    log::info!("[handy-core] normalize_peak: peak={:.6}, gain={:.2}x", peak, gain);

    if (gain - 1.0).abs() < 0.01 {
        // Already at good level, no scaling needed
        samples.to_vec()
    } else {
        samples.iter().map(|&s| (s * gain).clamp(-1.0, 1.0)).collect()
    }
}

/// Compute the RMS (root mean square) energy of audio samples.
pub fn compute_rms(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }
    let sum_sq: f32 = samples.iter().map(|&s| s * s).sum();
    (sum_sq / samples.len() as f32).sqrt()
}

/// Apply both post-processing filters: remove filler words and collapse stutters.
pub fn post_process(text: &str) -> String {
    let text = remove_filler_words(text);
    collapse_stutters(&text)
}
