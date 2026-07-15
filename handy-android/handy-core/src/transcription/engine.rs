use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;

use log::{info, warn};
use transcribe_cpp::{Backend, Model, ModelOptions, RunOptions, Task};

pub struct TranscriptionEngine {
    active_model_path: Option<PathBuf>,
    model: Mutex<Option<Model>>,
    is_loaded: AtomicBool,
    post_process_endpoint: Option<String>,
    post_process_api_key: Option<String>,
}

impl TranscriptionEngine {
    pub fn new() -> Self {
        Self {
            active_model_path: None,
            model: Mutex::new(None),
            is_loaded: AtomicBool::new(false),
            post_process_endpoint: None,
            post_process_api_key: None,
        }
    }

    pub fn load_model(&mut self, path: &PathBuf) -> Result<(), String> {
        info!("[handy-core] loading model from: {:?}", path);

        // Use explicit CPU backend (only backend available on Android).
        // GPU device 0 is ignored for CPU backend but must NOT be -1, as
        // whisper.cpp internally asserts gpu_device >= 0.
        // This matches desktop Handy's convention for explicit CPU selection.
        let model_options = ModelOptions {
            backend: Backend::Cpu,
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
    pub fn run(&self, pcm: &[f32]) -> Result<String, String> {
        let mut model_guard = self.model.lock().map_err(|e| e.to_string())?;
        let model = model_guard
            .as_mut()
            .ok_or_else(|| "No model loaded".to_string())?;

        let mut session = model
            .session()
            .map_err(|e| format!("Failed to create session: {e}"))?;
        drop(model_guard);

        let run_options = RunOptions {
            task: Task::Transcribe,
            language: None,
            target_language: None,
            ..Default::default()
        };

        info!("[handy-core] running transcription on {} samples", pcm.len());
        let transcript = session
            .run(pcm, &run_options)
            .map_err(|e| format!("Transcription run failed: {e}"))?;

        info!("[handy-core] transcription complete: {} chars", transcript.text.len());
        Ok(transcript.text)
    }

    pub fn cancel(&self) {
        // Batch run is synchronous and can't be cancelled mid-flight easily.
        // For now, just log the request.
        info!("[handy-core] cancel requested (batch run synchronous)");
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
