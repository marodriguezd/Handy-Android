use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Instant;

use log::info;
use transcribe_cpp::{Backend, Model, ModelOptions, RunOptions, Session, StreamOptions, Task};

use crate::transcription::router::{StreamCmd, StreamRouter};
use crate::transcription::worker::StreamWorker;

pub struct TranscriptionEngine {
    model_dir: Option<String>,
    active_model_path: Option<PathBuf>,
    router: Arc<StreamRouter>,
    next_worker_id: AtomicU64,
    active_worker: Mutex<Option<StreamWorker>>,
    is_loaded: AtomicBool,
    session: Mutex<Option<Session>>,
    last_final_result: Arc<Mutex<Option<String>>>,
    post_process_endpoint: Option<String>,
    post_process_api_key: Option<String>,
}

impl TranscriptionEngine {
    pub fn new() -> Self {
        Self {
            model_dir: None,
            active_model_path: None,
            router: Arc::new(StreamRouter::new()),
            next_worker_id: AtomicU64::new(1),
            active_worker: Mutex::new(None),
            is_loaded: AtomicBool::new(false),
            session: Mutex::new(None),
            last_final_result: Arc::new(Mutex::new(None)),
            post_process_endpoint: None,
            post_process_api_key: None,
        }
    }

    pub fn router(&self) -> Arc<StreamRouter> {
        self.router.clone()
    }

    pub fn set_model_dir(&mut self, dir: &str) {
        self.model_dir = Some(dir.to_string());
    }

    pub fn load_model(&mut self, path: &PathBuf) -> Result<(), String> {
        info!("[handy-core] loading model from: {:?}", path);

        let model_options = ModelOptions {
            backend: Backend::Cpu,
            gpu_device: -1,
        };

        let model = Model::load_with(path, &model_options)
            .map_err(|e| format!("Failed to load model: {e}"))?;

        let session = model
            .session()
            .map_err(|e| format!("Failed to create session: {e}"))?;

        self.active_model_path = Some(path.clone());
        *self.session.lock().unwrap() = Some(session);
        self.is_loaded.store(true, Ordering::Release);

        info!("[handy-core] model loaded successfully");
        Ok(())
    }

    pub fn unload_model(&self) {
        *self.session.lock().unwrap() = None;
        self.is_loaded.store(false, Ordering::Release);
        info!("[handy-core] model unloaded");
    }

    pub fn is_model_loaded(&self) -> bool {
        self.is_loaded.load(Ordering::Acquire)
    }

    pub fn start_stream(
        &self,
        on_partial: impl Fn(String) + Send + 'static,
        on_final: impl Fn(String) + Send + 'static,
    ) -> Result<u64, String> {
        let worker_id = self.next_worker_id.fetch_add(1, Ordering::Relaxed);
        let rx = self.router.open_channel();

        let mut stream = {
            let mut guard = self.session.lock().map_err(|e| e.to_string())?;
            let session = guard
                .as_mut()
                .ok_or_else(|| "No model loaded".to_string())?;

            let run_options = RunOptions {
                task: Task::Transcribe,
                language: None,
                target_language: None,
                family: None,
                ..Default::default()
            };

            session
                .stream(&run_options, &StreamOptions::default())
                .map_err(|e| format!("Failed to create stream: {e}"))?
        };

        let last_result = self.last_final_result.clone();
        let on_final = move |text: String| {
            *last_result.lock().unwrap() = Some(text.clone());
            on_final(text);
        };

        let mut worker = StreamWorker::new(worker_id);
        worker.spawn(rx, stream, on_partial, on_final);
        *self.active_worker.lock().unwrap() = Some(worker);
        Ok(worker_id)
    }

    pub fn finalize_stream(&self, worker_id: u64) -> Result<String, String> {
        let t0 = Instant::now();
        self.router
            .send(StreamCmd::Finalize)
            .map_err(|_| "failed to send Finalize: no active stream".to_string())?;

        let mut guard = self.active_worker.lock().map_err(|e| e.to_string())?;
        if let Some(mut worker) = guard.take() {
            if worker.worker_id() != worker_id {
                return Err("worker_id mismatch".to_string());
            }
            worker.join();
        } else {
            return Err("no active worker".to_string());
        }

        log::debug!("finalize_stream total_latency_ms={}", t0.elapsed().as_millis());

        self.last_final_result
            .lock()
            .map_err(|e| e.to_string())?
            .take()
            .ok_or_else(|| "no final result available".to_string())
    }

    pub fn cancel_stream(&self) {
        let _ = self.router.send(StreamCmd::Cancel);
        self.router.close();
        *self.active_worker.lock().unwrap() = None;
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
            result.extend(std::iter::repeat(current).take(count));
        }
        i += count;
    }
    result.join(" ")
}

/// Apply both post-processing filters: remove filler words and collapse stutters.
pub fn post_process(text: &str) -> String {
    let text = remove_filler_words(text);
    collapse_stutters(&text)
}
