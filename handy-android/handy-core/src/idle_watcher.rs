use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;
use crate::engine::ENGINE;
use std::panic::{self, AssertUnwindSafe};

pub struct IdleWatcher {
    running: Arc<AtomicBool>,
    handle: Arc<Mutex<Option<thread::JoinHandle<()>>>>,
    timeout_secs: Arc<Mutex<u32>>,
}

impl IdleWatcher {
    pub fn new() -> Self {
        Self {
            running: Arc::new(AtomicBool::new(false)),
            handle: Arc::new(Mutex::new(None)),
            timeout_secs: Arc::new(Mutex::new(30)),
        }
    }

    pub fn set_timeout(&self, secs: u32) {
        *self.timeout_secs.lock().unwrap() = secs;
    }

    pub fn start(&self) {
        let running = self.running.clone();
        running.store(true, Ordering::SeqCst);
        let timeout_secs = self.timeout_secs.clone();
        let handle = self.handle.clone();
        let running_flag = running.clone();

        let join = thread::spawn(move || {
            let timeout = *timeout_secs.lock().unwrap();
            if timeout == 0 {
                running_flag.store(false, Ordering::SeqCst);
                return;
            }
            thread::sleep(Duration::from_secs(timeout as u64));
            if !running_flag.load(Ordering::SeqCst) {
                return;
            }
            // Unload model if idle (with panic guard for robustness)
            let _ = panic::catch_unwind(AssertUnwindSafe(|| {
                let lock = ENGINE.get().expect("ENGINE not initialized");
                let mut guard = lock.lock().expect("ENGINE lock poisoned");
                if let Some(ref mut state) = *guard {
                    if !state.is_recording && state.model_loaded {
                        state.transcription_engine.unload_model();
                        state.model_loaded = false;
                        log::info!("IdleWatcher: model unloaded after {}s timeout", timeout);
                        if let Ok(mut env) = crate::jni_bridge::get_env_attached() {
                            crate::jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
                        }
                    }
                }
            }));
            running_flag.store(false, Ordering::SeqCst);
        });

        *handle.lock().unwrap() = Some(join);
    }

    pub fn reset(&self) {
        // Stop current watcher and restart
        self.running.store(false, Ordering::SeqCst);
        let old = self.handle.lock().unwrap().take();
        drop(old);
        self.start();
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
        let old = self.handle.lock().unwrap().take();
        drop(old);
    }

    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }
}

impl Default for IdleWatcher {
    fn default() -> Self {
        Self::new()
    }
}
