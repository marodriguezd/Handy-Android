use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use log::{info, warn};

use crate::transcription::router::StreamRouter;
use transcribe_rs::engines::parakeet::{ParakeetEngine, ParakeetInferenceParams, ParakeetModelParams, QuantizationType};
use transcribe_rs::TranscriptionEngine as TranscribeRsEngine;

pub struct TranscriptionEngine {
    active_model_path: Option<PathBuf>,
    engine: Mutex<Option<ParakeetEngine>>,
    is_loaded: AtomicBool,
    post_process_endpoint: Option<String>,
    post_process_api_key: Option<String>,
    stream_router: Arc<StreamRouter>,
    is_streaming: AtomicBool,
    selected_language: Mutex<Option<String>>,
    acceleration_backend: Mutex<Option<String>>,
    cancel_flag: Arc<AtomicBool>,
}

impl TranscriptionEngine {
    pub fn new() -> Self {
        Self {
            active_model_path: None,
            engine: Mutex::new(None),
            is_loaded: AtomicBool::new(false),
            post_process_endpoint: None,
            post_process_api_key: None,
            stream_router: Arc::new(StreamRouter::new()),
            is_streaming: AtomicBool::new(false),
            selected_language: Mutex::new(None),
            acceleration_backend: Mutex::new(None),
            cancel_flag: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn set_language(&self, lang: Option<String>) {
        *self.selected_language.lock().unwrap() = lang;
    }

    pub fn set_acceleration_backend(&self, backend: Option<String>) {
        *self.acceleration_backend.lock().unwrap() = backend;
    }

    pub fn load_model(&mut self, path: &PathBuf) -> Result<(), String> {
        info!("[handy-core] loading model from: {:?}", path);

        let backend_str = self.acceleration_backend.lock().unwrap().clone();
        let backend = backend_str.as_deref().unwrap_or("auto").to_lowercase();
        info!("[handy-core] requested acceleration backend: {}", backend);

        let quantization = match backend.as_str() {
            "cpu" => QuantizationType::Int8,
            "nnapi" | "xnnpack" | "vulkan" => QuantizationType::Int8,
            _ => QuantizationType::Int8,
        };

        let mut engine = ParakeetEngine::new();
        let params = ParakeetModelParams { quantization };
        if let Err(e) = engine.load_model_with_params(path, params) {
            let err_msg = format!("Failed to load model: {e}");
            warn!("[handy-core] {}", err_msg);
            return Err(err_msg);
        }

        self.active_model_path = Some(path.clone());
        *self.engine.lock().unwrap() = Some(engine);
        self.is_loaded.store(true, Ordering::Release);

        info!("[handy-core] model loaded successfully from {:?}", path);
        Ok(())
    }

    pub fn unload_model(&self) {
        *self.engine.lock().unwrap() = None;
        self.is_loaded.store(false, Ordering::Release);
        info!("[handy-core] model unloaded");
    }

    pub fn is_model_loaded(&self) -> bool {
        self.is_loaded.load(Ordering::Acquire)
    }

    pub fn run(&self, pcm: &[f32]) -> Result<String, String> {
        self.cancel_flag.store(false, Ordering::SeqCst);

        if self.cancel_flag.load(Ordering::SeqCst) {
            info!("[handy-core] batch run cancelled before inference");
            return Err("Transcription cancelled".to_string());
        }

        let mut engine_guard = self.engine.lock().map_err(|e| e.to_string())?;
        let engine = engine_guard
            .as_mut()
            .ok_or_else(|| "No model loaded".to_string())?;

        let lang_guard = self.selected_language.lock().unwrap();
        let _language = match lang_guard.as_deref() {
            Some("auto") | None | Some("") => None,
            Some(l) => Some(l.to_string()),
        };
        drop(lang_guard);
        // TODO(Phase 1b): forward language to ParakeetInferenceParams when
        // transcribe-rs exposes a language hint parameter.

        if self.cancel_flag.load(Ordering::SeqCst) {
            info!("[handy-core] batch run cancelled before inference");
            return Err("Transcription cancelled".to_string());
        }

        let normalized = normalize_peak(pcm);
        let rms = compute_rms(&normalized);
        info!("[handy-core] running transcription on {} samples, rms={:.4}", pcm.len(), rms);

        let params = ParakeetInferenceParams::default();
        match engine.transcribe_samples(normalized, Some(params)) {
            Ok(result) => {
                if self.cancel_flag.load(Ordering::SeqCst) {
                    info!("[handy-core] batch run result discarded (cancelled during inference)");
                    return Err("Transcription cancelled".to_string());
                }
                info!("[handy-core] transcription complete: {} chars", result.text.len());
                Ok(result.text)
            }
            Err(e) => {
                warn!("[handy-core] transcription run failed: {e}");
                Err(format!("Transcription run failed: {e}"))
            }
        }
    }

    pub fn supports_streaming(&self) -> Result<bool, String> {
        Ok(false)
    }

    pub fn start_stream<F>(&self, _on_partial: F) -> Result<bool, String>
    where
        F: Fn(String) + Send + 'static,
    {
        // TODO(Phase 2): implement lock-free streaming over the audio ring buffer
        // once transcribe-rs exposes a streaming API or chunked inference.
        info!("[handy-core] streaming not yet implemented with transcribe-rs; falling back to batch");
        Ok(false)
    }

    pub fn finalize_stream(&self) -> Option<String> {
        self.is_streaming.store(false, Ordering::Release);
        None
    }

    pub fn cancel_stream(&self) {
        self.cancel_flag.store(true, Ordering::SeqCst);
        self.is_streaming.store(false, Ordering::Release);
    }

    pub fn start_periodic<F>(&self, _on_partial: F) -> Result<bool, String>
    where
        F: Fn(String) + Send + 'static,
    {
        // TODO(Phase 2): implement periodic chunked transcription for live feedback.
        info!("[handy-core] periodic transcription not yet implemented with transcribe-rs; falling back to batch");
        Ok(false)
    }

    pub fn finalize_periodic(&self) -> Option<String> {
        self.finalize_stream()
    }

    pub fn cancel_periodic(&self) {
        self.cancel_stream()
    }

    pub fn is_streaming_active(&self) -> bool {
        self.is_streaming.load(Ordering::Acquire)
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

pub fn normalize_peak(samples: &[f32]) -> Vec<f32> {
    if samples.is_empty() {
        return Vec::new();
    }

    let peak = samples.iter().fold(0.0f32, |max, &s| max.max(s.abs()));

    if peak < 0.0001 {
        log::warn!("[handy-core] normalize_peak: near-silence (peak={:.6})", peak);
        return samples.to_vec();
    }

    let target_peak = 0.95f32;
    let gain = target_peak / peak;

    log::info!("[handy-core] normalize_peak: peak={:.6}, gain={:.2}x", peak, gain);

    if (gain - 1.0).abs() < 0.01 {
        samples.to_vec()
    } else {
        samples.iter().map(|&s| (s * gain).clamp(-1.0, 1.0)).collect()
    }
}

pub fn compute_rms(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }
    let sum_sq: f32 = samples.iter().map(|&s| s * s).sum();
    (sum_sq / samples.len() as f32).sqrt()
}

pub fn post_process(text: &str) -> String {
    let text = remove_filler_words(text);
    collapse_stutters(&text)
}
