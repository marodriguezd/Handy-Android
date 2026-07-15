use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ModelInfo {
    pub id: String,
    #[serde(rename = "display_name")]
    pub name: String,
    pub size_bytes: u64,
    pub downloaded: bool,
    pub active: bool,
    pub download_progress: f32,
    pub description: String,
    pub recommended: bool,
    pub requires: String,
    pub language: String,
    pub quant: String,
    pub license: String,
}

impl ModelInfo {
    pub fn catalog() -> Vec<ModelInfo> {
        vec![
            ModelInfo {
                id: "whisper-tiny-Q5_K_M".into(),
                name: "Whisper Tiny (Q5_K_M)".into(),
                size_bytes: 77_000_000,
                downloaded: false,
                active: false,
                download_progress: 0.0,
                description: "Fastest Whisper model, ~32x real-time. Best for short dictation and commands on mobile devices.".into(),
                recommended: true,
                requires: "1 GB RAM".into(),
                language: "multilingual".into(),
                quant: "Q5_K_M".into(),
                license: "MIT".into(),
            },
            ModelInfo {
                id: "whisper-base-Q5_K_M".into(),
                name: "Whisper Base (Q5_K_M)".into(),
                size_bytes: 155_000_000,
                downloaded: false,
                active: false,
                download_progress: 0.0,
                description: "Good accuracy/speed trade-off. ~16x real-time. Handles moderate noise well on mobile.".into(),
                recommended: true,
                requires: "2 GB RAM".into(),
                language: "multilingual".into(),
                quant: "Q5_K_M".into(),
                license: "MIT".into(),
            },
            ModelInfo {
                id: "whisper-small-Q5_K_M".into(),
                name: "Whisper Small (Q5_K_M)".into(),
                size_bytes: 488_000_000,
                downloaded: false,
                active: false,
                download_progress: 0.0,
                description: "Balanced accuracy and speed. ~6x real-time. Recommended for most daily use cases including meetings and lectures.".into(),
                recommended: false,
                requires: "3 GB RAM".into(),
                language: "multilingual".into(),
                quant: "Q5_K_M".into(),
                license: "MIT".into(),
            },
            ModelInfo {
                id: "whisper-medium-Q5_K_M".into(),
                name: "Whisper Medium (Q5_K_M)".into(),
                size_bytes: 1_550_000_000,
                downloaded: false,
                active: false,
                download_progress: 0.0,
                description: "Most accurate quantized Whisper model. Near-human transcription quality. Best for noisy environments and long-form transcription.".into(),
                recommended: false,
                requires: "4+ GB RAM".into(),
                language: "multilingual".into(),
                quant: "Q5_K_M".into(),
                license: "MIT".into(),
            },
            ModelInfo {
                id: "parakeet-unified-en-0.6b-Q4_K_M".into(),
                name: "Parakeet EN 0.6B (Q4_K_M)".into(),
                size_bytes: 380_000_000,
                downloaded: false,
                active: false,
                download_progress: 0.0,
                description: "NVIDIA Parakeet: fast, robust English ASR optimized for edge devices. Excellent noise robustness. ~8x real-time.".into(),
                recommended: true,
                requires: "2 GB RAM".into(),
                language: "en".into(),
                quant: "Q4_K_M".into(),
                license: "CC-BY-4.0".into(),
            },
        ]
    }

    pub fn from_filename(path: &Path) -> Option<ModelInfo> {
        let ext = path.extension()?;
        if ext != "gguf" {
            return None;
        }
        let stem = path.file_stem()?.to_str()?;
        let file_size = std::fs::metadata(path).ok().map(|m| m.len()).unwrap_or(0);
        let mut matched = Self::catalog().into_iter().find(|m| m.id == stem)?;
        matched.downloaded = true;
        matched.download_progress = 100.0;
        matched.size_bytes = file_size;
        Some(matched)
    }
}
