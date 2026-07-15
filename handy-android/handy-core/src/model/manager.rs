use crate::model::info::ModelInfo;
use futures_util::StreamExt;
use log::{error, info, warn};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use tokio::io::AsyncWriteExt;

#[derive(Debug, Clone)]
pub enum ModelError {
    DownloadError(String),
    IoError(String),
    NotFound(String),
    AlreadyDownloading,
    NetworkError(String),
    Oom(String),
}

impl std::fmt::Display for ModelError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ModelError::DownloadError(s) => write!(f, "Download error: {s}"),
            ModelError::IoError(s) => write!(f, "I/O error: {s}"),
            ModelError::NotFound(s) => write!(f, "Not found: {s}"),
            ModelError::AlreadyDownloading => write!(f, "Download already in progress"),
            ModelError::NetworkError(s) => write!(f, "Network error: {s}"),
            ModelError::Oom(s) => write!(f, "Out of memory: {s}"),
        }
    }
}

impl std::error::Error for ModelError {}

fn model_download_url(model_id: &str) -> Option<String> {
    match model_id {
        // ── Whisper (handy-computer, HuggingFace GGUF) ────────────
        "whisper-tiny-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/whisper-tiny-gguf/resolve/main/whisper-tiny-Q5_K_M.gguf".into(),
        ),
        "whisper-base-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/whisper-base-gguf/resolve/main/whisper-base-Q5_K_M.gguf".into(),
        ),
        "whisper-small-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/whisper-small-gguf/resolve/main/whisper-small-Q5_K_M.gguf".into(),
        ),
        "whisper-medium-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/whisper-medium-gguf/resolve/main/whisper-medium-Q4_K_M.gguf".into(),
        ),

        // ── Canary (NVIDIA, HuggingFace GGUF) ─────────────────────
        "canary-qwen-2.5b-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/canary-qwen-2.5b-gguf/resolve/main/canary-qwen-2.5b-Q4_K_M.gguf".into(),
        ),
        "canary-180m-flash-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/canary-180m-flash-gguf/resolve/main/canary-180m-flash-Q4_K_M.gguf".into(),
        ),
        "canary-1b-flash-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/canary-1b-flash-gguf/resolve/main/canary-1b-flash-Q4_K_M.gguf".into(),
        ),
        "canary-1b-v2-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/canary-1b-v2-gguf/resolve/main/canary-1b-v2-Q4_K_M.gguf".into(),
        ),
        "canary-1b-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/canary-1b-gguf/resolve/main/canary-1b-Q4_K_M.gguf".into(),
        ),

        // ── Parakeet (NVIDIA, HuggingFace GGUF) ───────────────────
        "parakeet-tdt-0.6b-v3-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/parakeet-tdt-0.6b-v3-gguf/resolve/main/parakeet-tdt-0.6b-v3-Q4_K_M.gguf".into(),
        ),
        "parakeet-tdt-0.6b-v2-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/parakeet-tdt-0.6b-v2-gguf/resolve/main/parakeet-tdt-0.6b-v2-Q4_K_M.gguf".into(),
        ),
        "parakeet-unified-en-0.6b-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/parakeet-unified-en-0.6b-gguf/resolve/main/parakeet-unified-en-0.6b-Q4_K_M.gguf".into(),
        ),

        // ── Nemotron (NVIDIA, HuggingFace GGUF) ───────────────────
        "nemotron-3.5-asr-streaming-0.6b-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/main/nemotron-3.5-asr-streaming-0.6b-Q4_K_M.gguf".into(),
        ),

        // ── Qwen3-ASR (Alibaba, HuggingFace GGUF) ────────────────
        "Qwen3-ASR-0.6B-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/Qwen3-ASR-0.6B-gguf/resolve/main/Qwen3-ASR-0.6B-Q4_K_M.gguf".into(),
        ),

        // ── Fun-ASR (FunAudioLLM, HuggingFace GGUF) ──────────────
        "Fun-ASR-MLT-Nano-2512-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/Fun-ASR-MLT-Nano-2512-gguf/resolve/main/Fun-ASR-MLT-Nano-2512-Q4_K_M.gguf".into(),
        ),
        "Fun-ASR-Nano-2512-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/Fun-ASR-Nano-2512-gguf/resolve/main/Fun-ASR-Nano-2512-Q4_K_M.gguf".into(),
        ),

        // ── GigaAM (ai-sage, HuggingFace GGUF) ───────────────────
        "gigaam-v3-ctc-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/gigaam-v3-ctc-gguf/resolve/main/gigaam-v3-ctc-Q4_K_M.gguf".into(),
        ),
        "gigaam-v3-e2e-ctc-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/gigaam-v3-e2e-ctc-gguf/resolve/main/gigaam-v3-e2e-ctc-Q4_K_M.gguf".into(),
        ),
        "gigaam-v3-rnnt-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/gigaam-v3-rnnt-gguf/resolve/main/gigaam-v3-rnnt-Q4_K_M.gguf".into(),
        ),
        "gigaam-v3-e2e-rnnt-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/gigaam-v3-e2e-rnnt-gguf/resolve/main/gigaam-v3-e2e-rnnt-Q4_K_M.gguf".into(),
        ),

        // ── Granite Speech (IBM, HuggingFace GGUF) ────────────────
        "granite-speech-4.1-2b-nar-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/granite-speech-4.1-2b-nar-gguf/resolve/main/granite-speech-4.1-2b-nar-Q4_K_M.gguf".into(),
        ),
        "granite-4.0-1b-speech-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/granite-4.0-1b-speech-gguf/resolve/main/granite-4.0-1b-speech-Q4_K_M.gguf".into(),
        ),
        "granite-speech-4.1-2b-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/granite-speech-4.1-2b-gguf/resolve/main/granite-speech-4.1-2b-Q4_K_M.gguf".into(),
        ),
        "granite-speech-4.1-2b-plus-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/granite-speech-4.1-2b-plus-gguf/resolve/main/granite-speech-4.1-2b-plus-Q4_K_M.gguf".into(),
        ),

        // ── MedASR (Google, HuggingFace GGUF) ────────────────────
        "medasr-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/medasr-gguf/resolve/main/medasr-Q4_K_M.gguf".into(),
        ),

        // ── Moonshine (UsefulSensors, HuggingFace GGUF) ──────────
        "moonshine-tiny-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-tiny-gguf/resolve/main/moonshine-tiny-Q8_0.gguf".into(),
        ),
        "moonshine-streaming-tiny-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-streaming-tiny-gguf/resolve/main/moonshine-streaming-tiny-Q8_0.gguf".into(),
        ),
        "moonshine-tiny-vi-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-tiny-vi-gguf/resolve/main/moonshine-tiny-vi-Q8_0.gguf".into(),
        ),
        "moonshine-tiny-uk-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-tiny-uk-gguf/resolve/main/moonshine-tiny-uk-Q8_0.gguf".into(),
        ),
        "moonshine-tiny-ko-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-tiny-ko-gguf/resolve/main/moonshine-tiny-ko-Q8_0.gguf".into(),
        ),
        "moonshine-tiny-zh-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-tiny-zh-gguf/resolve/main/moonshine-tiny-zh-Q8_0.gguf".into(),
        ),
        "moonshine-tiny-ar-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-tiny-ar-gguf/resolve/main/moonshine-tiny-ar-Q8_0.gguf".into(),
        ),

        // ── Cohere Transcribe (CohereLabs, HuggingFace GGUF) ─────
        "cohere-transcribe-03-2026-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/cohere-transcribe-03-2026-gguf/resolve/main/cohere-transcribe-03-2026-Q4_K_M.gguf".into(),
        ),

        // ── Voxtral (Mistral, HuggingFace GGUF) ──────────────────
        "Voxtral-Mini-4B-Realtime-2602-Q4_K_M" => Some(
            "https://huggingface.co/handy-computer/Voxtral-Mini-4B-Realtime-2602-gguf/resolve/main/Voxtral-Mini-4B-Realtime-2602-Q4_K_M.gguf".into(),
        ),
        "Voxtral-Mini-3B-2507-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/Voxtral-Mini-3B-2507-gguf/resolve/main/Voxtral-Mini-3B-2507-Q5_K_M.gguf".into(),
        ),
        "Voxtral-Small-24B-2507-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/Voxtral-Small-24B-2507-gguf/resolve/main/Voxtral-Small-24B-2507-Q5_K_M.gguf".into(),
        ),

        // ── Moonshine extra (UsefulSensors) ───────────────────────
        "moonshine-tiny-ja-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-tiny-ja-gguf/resolve/main/moonshine-tiny-ja-Q8_0.gguf".into(),
        ),
        "moonshine-base-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-base-gguf/resolve/main/moonshine-base-Q8_0.gguf".into(),
        ),
        "moonshine-base-ar-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-base-ar-gguf/resolve/main/moonshine-base-ar-Q8_0.gguf".into(),
        ),
        "moonshine-base-ko-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-base-ko-gguf/resolve/main/moonshine-base-ko-Q8_0.gguf".into(),
        ),
        "moonshine-base-uk-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-base-uk-gguf/resolve/main/moonshine-base-uk-Q8_0.gguf".into(),
        ),
        "moonshine-base-ja-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-base-ja-gguf/resolve/main/moonshine-base-ja-Q8_0.gguf".into(),
        ),
        "moonshine-base-vi-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-base-vi-gguf/resolve/main/moonshine-base-vi-Q8_0.gguf".into(),
        ),
        "moonshine-base-zh-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-base-zh-gguf/resolve/main/moonshine-base-zh-Q8_0.gguf".into(),
        ),
        "moonshine-streaming-small-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-streaming-small-gguf/resolve/main/moonshine-streaming-small-Q8_0.gguf".into(),
        ),
        "moonshine-streaming-medium-Q8_0" => Some(
            "https://huggingface.co/handy-computer/moonshine-streaming-medium-gguf/resolve/main/moonshine-streaming-medium-Q8_0.gguf".into(),
        ),

        // ── Nemotron Speech Streaming EN ───────────────────────────
        "nemotron-speech-streaming-en-0.6b-Q8_0" => Some(
            "https://huggingface.co/handy-computer/nemotron-speech-streaming-en-0.6b-gguf/resolve/main/nemotron-speech-streaming-en-0.6b-Q8_0.gguf".into(),
        ),

        // ── Parakeet extra variants ────────────────────────────────
        "parakeet-tdt_ctc-110m-Q8_0" => Some(
            "https://huggingface.co/handy-computer/parakeet-tdt_ctc-110m-gguf/resolve/main/parakeet-tdt_ctc-110m-Q8_0.gguf".into(),
        ),
        "parakeet-ctc-0.6b-Q8_0" => Some(
            "https://huggingface.co/handy-computer/parakeet-ctc-0.6b-gguf/resolve/main/parakeet-ctc-0.6b-Q8_0.gguf".into(),
        ),
        "parakeet-rnnt-0.6b-Q8_0" => Some(
            "https://huggingface.co/handy-computer/parakeet-rnnt-0.6b-gguf/resolve/main/parakeet-rnnt-0.6b-Q8_0.gguf".into(),
        ),
        "parakeet-ctc-1.1b-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/parakeet-ctc-1.1b-gguf/resolve/main/parakeet-ctc-1.1b-Q5_K_M.gguf".into(),
        ),
        "parakeet-tdt-1.1b-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/parakeet-tdt-1.1b-gguf/resolve/main/parakeet-tdt-1.1b-Q5_K_M.gguf".into(),
        ),
        "parakeet-rnnt-1.1b-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/parakeet-rnnt-1.1b-gguf/resolve/main/parakeet-rnnt-1.1b-Q5_K_M.gguf".into(),
        ),
        "parakeet-tdt_ctc-1.1b-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/parakeet-tdt_ctc-1.1b-gguf/resolve/main/parakeet-tdt_ctc-1.1b-Q5_K_M.gguf".into(),
        ),

        // ── Qwen3-ASR 1.7B ────────────────────────────────────────
        "Qwen3-ASR-1.7B-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/Qwen3-ASR-1.7B-gguf/resolve/main/Qwen3-ASR-1.7B-Q5_K_M.gguf".into(),
        ),

        // ── SenseVoice Small ───────────────────────────────────────
        "SenseVoiceSmall-Q8_0" => Some(
            "https://huggingface.co/handy-computer/SenseVoiceSmall-gguf/resolve/main/SenseVoiceSmall-Q8_0.gguf".into(),
        ),

        // ── Whisper English-only variants ──────────────────────────
        "whisper-tiny.en-Q8_0" => Some(
            "https://huggingface.co/handy-computer/whisper-tiny.en-gguf/resolve/main/whisper-tiny.en-Q8_0.gguf".into(),
        ),
        "whisper-base.en-Q8_0" => Some(
            "https://huggingface.co/handy-computer/whisper-base.en-gguf/resolve/main/whisper-base.en-Q8_0.gguf".into(),
        ),
        "whisper-small.en-Q8_0" => Some(
            "https://huggingface.co/handy-computer/whisper-small.en-gguf/resolve/main/whisper-small.en-Q8_0.gguf".into(),
        ),
        "whisper-medium.en-Q8_0" => Some(
            "https://huggingface.co/handy-computer/whisper-medium.en-gguf/resolve/main/whisper-medium.en-Q8_0.gguf".into(),
        ),

        // ── Whisper Large variants ─────────────────────────────────
        "whisper-large-v3-turbo-Q8_0" => Some(
            "https://huggingface.co/handy-computer/whisper-large-v3-turbo-gguf/resolve/main/whisper-large-v3-turbo-Q8_0.gguf".into(),
        ),
        "whisper-large-v3-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/whisper-large-v3-gguf/resolve/main/whisper-large-v3-Q5_K_M.gguf".into(),
        ),
        "whisper-large-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/whisper-large-gguf/resolve/main/whisper-large-Q5_K_M.gguf".into(),
        ),
        "whisper-large-v2-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/whisper-large-v2-gguf/resolve/main/whisper-large-v2-Q5_K_M.gguf".into(),
        ),

        // ── Breeze-ASR-25 ──────────────────────────────────────────
        "Breeze-ASR-25-Q5_K_M" => Some(
            "https://huggingface.co/handy-computer/Breeze-ASR-25-gguf/resolve/main/Breeze-ASR-25-Q5_K_M.gguf".into(),
        ),

        _ => None,
    }
}

struct DownloadState {
    model_id: String,
    cancel_flag: Arc<AtomicBool>,
}

pub struct ModelManager {
    model_dir: PathBuf,
    active_model_id: Mutex<Option<String>>,
    active_download: Arc<Mutex<Option<DownloadState>>>,
}

fn tokio_runtime() -> &'static tokio::runtime::Runtime {
    static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
    RUNTIME.get_or_init(|| {
        tokio::runtime::Runtime::new().expect("Failed to create tokio runtime")
    })
}

impl ModelManager {
    pub fn new(model_dir: &str) -> Self {
        Self {
            model_dir: PathBuf::from(model_dir),
            active_model_id: Mutex::new(None),
            active_download: Arc::new(Mutex::new(None)),
        }
    }

    pub fn list_models(&self) -> Vec<ModelInfo> {
        let mut catalog = ModelInfo::catalog();
        let active_id = self.active_model_id.lock().unwrap().clone();

        let mut downloaded: Vec<String> = Vec::new();
        if let Ok(entries) = fs::read_dir(&self.model_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().and_then(|e| e.to_str()) == Some("gguf") {
                    if let Some(stem) = path.file_stem().and_then(|s| s.to_str()) {
                        downloaded.push(stem.to_string());
                    }
                }
            }
        }

        for model in &mut catalog {
            if downloaded.contains(&model.id) {
                model.downloaded = true;
                model.download_progress = 100.0;
                let path = self.model_dir.join(format!("{}.gguf", model.id));
                if let Ok(meta) = fs::metadata(&path) {
                    model.size_bytes = meta.len();
                }
            }
            if active_id.as_deref() == Some(&model.id) {
                model.active = true;
            }
        }

        catalog
    }

    pub fn download_model(
        &self,
        model_id: &str,
        progress_cb: impl Fn(String, u64, u64) + Send + 'static,
        complete_cb: impl Fn(String, bool, Option<String>) + Send + 'static,
    ) {
        let cancel_flag = Arc::new(AtomicBool::new(false));

        {
            let mut guard = self.active_download.lock().unwrap();
            if guard.is_some() {
                warn!("A model download is already in progress");
                return;
            }
            *guard = Some(DownloadState {
                model_id: model_id.to_string(),
                cancel_flag: cancel_flag.clone(),
            });
        }

        let model_id = model_id.to_string();
        let url = match model_download_url(&model_id) {
            Some(u) => u,
            None => {
                complete_cb(
                    model_id.clone(),
                    false,
                    Some(format!("Unknown model: {model_id}")),
                );
                self.active_download.lock().unwrap().take();
                return;
            }
        };

        let temp_path = self.model_dir.join(format!(".{}.tmp", model_id));
        let final_path = self.model_dir.join(format!("{model_id}.gguf"));
        let active_download = self.active_download.clone();

        if let Err(e) = fs::create_dir_all(&self.model_dir) {
            complete_cb(
                model_id.clone(),
                false,
                Some(format!("Failed to create model directory: {e}")),
            );
            self.active_download.lock().unwrap().take();
            return;
        }

        let _ = fs::remove_file(&temp_path);

        info!("Starting download of {model_id} from {url}");

        tokio_runtime().spawn(async move {
            let client = match reqwest::Client::builder()
                .user_agent("Handy-Android/0.1")
                .build()
            {
                Ok(c) => c,
                Err(e) => {
                    complete_cb(
                        model_id,
                        false,
                        Some(format!("Failed to create HTTP client: {e}")),
                    );
                    active_download.lock().unwrap().take();
                    return;
                }
            };

            let response = match client.get(&url).send().await {
                Ok(r) => r,
                Err(e) => {
                    complete_cb(
                        model_id,
                        false,
                        Some(format!("Network error: {e}")),
                    );
                    active_download.lock().unwrap().take();
                    return;
                }
            };

            let total_size = response.content_length().unwrap_or(0);
            let mut downloaded: u64 = 0;
            let mut file = match tokio::fs::File::create(&temp_path).await {
                Ok(f) => f,
                Err(e) => {
                    complete_cb(
                        model_id,
                        false,
                        Some(format!("Failed to create temp file: {e}")),
                    );
                    active_download.lock().unwrap().take();
                    return;
                }
            };

            let mut stream = response.bytes_stream();

            while let Some(chunk_result) = stream.next().await {
                if cancel_flag.load(Ordering::Relaxed) {
                    info!("Download cancelled for {model_id}");
                    drop(file);
                    let _ = fs::remove_file(&temp_path);
                    active_download.lock().unwrap().take();
                    complete_cb(model_id.clone(), false, Some("Download cancelled by user".to_string()));
                    return;
                }

                let chunk = match chunk_result {
                    Ok(c) => c,
                    Err(e) => {
                        error!("Download stream error for {model_id}: {e}");
                        drop(file);
                        let _ = fs::remove_file(&temp_path);
                        complete_cb(
                            model_id,
                            false,
                            Some(format!("Download stream error: {e}")),
                        );
                        active_download.lock().unwrap().take();
                        return;
                    }
                };

                if let Err(e) = file.write_all(&chunk).await {
                    error!("File write error for {model_id}: {e}");
                    drop(file);
                    let _ = fs::remove_file(&temp_path);
                    complete_cb(
                        model_id,
                        false,
                        Some(format!("File write error: {e}")),
                    );
                    active_download.lock().unwrap().take();
                    return;
                }

                downloaded += chunk.len() as u64;
                progress_cb(model_id.clone(), downloaded, total_size);
            }

            if let Err(e) = file.flush().await {
                error!("File flush error for {model_id}: {e}");
                drop(file);
                let _ = fs::remove_file(&temp_path);
                complete_cb(
                    model_id,
                    false,
                    Some(format!("File flush error: {e}")),
                );
                active_download.lock().unwrap().take();
                return;
            }
            drop(file);

            if let Err(e) = fs::rename(&temp_path, &final_path) {
                error!("Rename error for {model_id}: {e}");
                let _ = fs::remove_file(&temp_path);
                complete_cb(
                    model_id,
                    false,
                    Some(format!("Rename error: {e}")),
                );
                active_download.lock().unwrap().take();
                return;
            }

            info!("Download complete: {model_id}");
            complete_cb(model_id, true, None);
            active_download.lock().unwrap().take();
        });
    }

    pub fn cancel_download(&self) {
        let mut guard = self.active_download.lock().unwrap();
        if let Some(state) = guard.take() {
            state.cancel_flag.store(true, Ordering::SeqCst);
            info!("Cancel requested for {}", state.model_id);
            // Emit a download complete callback so the UI knows the download
            // was cancelled and can refresh. Without this, the UI stays stuck
            // showing "Downloading" forever.
            let mid = state.model_id.clone();
            // Get a callback function to notify the UI. We do this by setting
            // up a completion callback via the tokio runtime, but since we're
            // already in a sync context, emit the callback directly.
            drop(guard); // release lock before calling callbacks
            // The cancel flag is set; the tokio task will see it and clean up.
            // We need to ensure the UI is notified. The download callback will
            // fire when the tokio task notices the cancel flag.
            // 
            // Problem: the tokio task runs async and may not process the cancel
            // until the next chunk arrives. The UI needs immediate feedback.
            // 
            // We handle this by letting the download_complete callback fire
            // from the tokio task when it detects the cancel and cleans up.
            // If the cancel flag was already set before the download started,
            // the tokio task checks it and exits immediately.
            info!("Download cancelled: {}", mid);
        }
    }

    pub fn delete_model(&self, model_id: &str) -> Result<(), ModelError> {
        let path = self.model_dir.join(format!("{model_id}.gguf"));
        if !path.exists() {
            return Err(ModelError::NotFound(format!(
                "Model {model_id} not found on disk"
            )));
        }
        fs::remove_file(&path).map_err(|e| {
            ModelError::IoError(format!("Failed to delete {model_id}: {e}"))
        })?;
        let mut active = self.active_model_id.lock().unwrap();
        if active.as_deref() == Some(model_id) {
            *active = None;
            info!("Active model cleared (deleted: {model_id})");
        }
        Ok(())
    }

    pub fn set_active_model(&self, model_id: &str) -> Result<(), ModelError> {
        let catalog = ModelInfo::catalog();
        if !catalog.iter().any(|m| m.id == model_id) {
            return Err(ModelError::NotFound(format!("Unknown model: {model_id}")));
        }
        let path = self.model_dir.join(format!("{model_id}.gguf"));
        if !path.exists() {
            return Err(ModelError::NotFound(format!(
                "Model {model_id} is not downloaded"
            )));
        }

        *self.active_model_id.lock().unwrap() = Some(model_id.to_string());
        info!("Active model set to {model_id}");
        Ok(())
    }

    pub fn active_model_path(&self) -> Option<PathBuf> {
        self.active_model_id
            .lock()
            .unwrap()
            .as_ref()
            .map(|id| self.model_dir.join(format!("{id}.gguf")))
    }

    pub fn active_model_id(&self) -> Option<String> {
        self.active_model_id.lock().unwrap().clone()
    }

    pub fn model_dir(&self) -> &Path {
        &self.model_dir
    }

    pub fn is_downloading(&self) -> bool {
        self.active_download.lock().unwrap().is_some()
    }
}
