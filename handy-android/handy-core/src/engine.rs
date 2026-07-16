use crate::audio::pipeline::AudioPipeline;
use crate::history::manager::HistoryManager;
use crate::model::manager::ModelManager;
use crate::transcription::engine::TranscriptionEngine;
use jni::objects::GlobalRef;
use jni::JavaVM;
use log::warn;
use std::sync::{Mutex, OnceLock};

pub struct EngineState {
    pub model_dir: String,
    pub config_dir: String,
    pub callback: GlobalRef,
    pub model_loaded: bool,
    pub is_recording: bool,
    pub is_streaming: bool,
    pub idle_timeout_secs: u32,
    pub post_process_endpoint: Option<String>,
    pub post_process_api_key: Option<String>,
    pub audio_pipeline: AudioPipeline,
    pub transcription_engine: TranscriptionEngine,
    pub model_manager: ModelManager,
    pub history_manager: HistoryManager,
    pub idle_watcher: crate::idle_watcher::IdleWatcher,
}

impl EngineState {
    pub fn new(model_dir: String, config_dir: String, callback: GlobalRef) -> Self {
        let history_path = format!("{}/history.db", config_dir);
        let history_manager = HistoryManager::new(&history_path).unwrap_or_else(|e| {
            warn!("History init failed (continuing without history): {e}");
            HistoryManager::new_empty()
        });

        Self {
            model_dir: model_dir.clone(),
            config_dir,
            callback,
            model_loaded: false,
            is_recording: false,
            is_streaming: false,
            idle_timeout_secs: 30,
            post_process_endpoint: None,
            post_process_api_key: None,
            audio_pipeline: AudioPipeline::new(),
            transcription_engine: TranscriptionEngine::new(),
            model_manager: ModelManager::new(&model_dir),
            history_manager,
            idle_watcher: crate::idle_watcher::IdleWatcher::new(),
        }
    }

    pub fn set_model_loaded(&mut self, loaded: bool) {
        self.model_loaded = loaded;
    }

    pub fn set_recording(&mut self, recording: bool) {
        self.is_recording = recording;
    }

    pub fn set_idle_timeout(&mut self, secs: u32) {
        self.idle_timeout_secs = secs;
    }

    pub fn set_post_process_endpoint(&mut self, endpoint: Option<String>) {
        self.post_process_endpoint = endpoint;
    }

    pub fn set_post_process_api_key(&mut self, api_key: Option<String>) {
        self.post_process_api_key = api_key;
    }
}

pub static ENGINE: OnceLock<Mutex<Option<EngineState>>> = OnceLock::new();
pub static JAVA_VM: OnceLock<JavaVM> = OnceLock::new();

pub fn ensure_engine_init() {
    ENGINE.get_or_init(|| Mutex::new(None));
}
