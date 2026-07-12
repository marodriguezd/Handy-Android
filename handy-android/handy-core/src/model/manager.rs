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
    let hf_name = match model_id {
        "whisper-tiny-q5_1" => "ggml-tiny-q5_1.bin",
        "whisper-tiny-en-q5_1" => "ggml-tiny.en-q5_1.bin",
        "whisper-base-q5_1" => "ggml-base-q5_1.bin",
        "whisper-small-q5_1" => "ggml-small-q5_1.bin",
        "whisper-medium-q5_1" => "ggml-medium-q5_1.bin",
        _ => return None,
    };
    Some(format!(
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/{hf_name}"
    ))
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
        if let Some(state) = guard.as_ref() {
            state.cancel_flag.store(true, Ordering::SeqCst);
            info!("Cancel requested for {}", state.model_id);
        }
        *guard = None;
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

        // OOM: check model file size
        if let Ok(meta) = std::fs::metadata(&path) {
            let size_mb = meta.len() / (1024 * 1024);
            if size_mb > 1500 {
                return Err(ModelError::Oom(format!(
                    "Model is {}MB (max 1500MB)", size_mb
                )));
            }
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
