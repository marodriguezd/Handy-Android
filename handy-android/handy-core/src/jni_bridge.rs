use crate::engine::{ensure_engine_init, EngineState, ENGINE, JAVA_VM};
use crate::jni_callback;
use jni::objects::{JByteBuffer, JClass, JObject, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use log::{info, warn};
use std::panic::{self, AssertUnwindSafe};

fn with_engine_or<F, R>(f: F, default: R) -> R
where
    F: FnOnce(&mut EngineState) -> R,
{
    let lock = ENGINE.get();
    if lock.is_none() {
        warn!("with_engine: ENGINE not initialized");
        return default;
    }
    let mut guard = match lock.unwrap().lock() {
        Ok(g) => g,
        Err(e) => {
            warn!("with_engine: ENGINE lock poisoned: {e}");
            return default;
        }
    };
    let state = guard.as_mut();
    if state.is_none() {
        warn!("with_engine: ENGINE state not initialized");
        return default;
    }
    f(state.unwrap())
}

fn with_engine<F, R>(f: F) -> R
where
    F: FnOnce(&mut EngineState) -> R,
    R: Default,
{
    with_engine_or(f, R::default())
}

/// Wraps a JNI entry point with catch_unwind.
/// If the closure panics, the panic message is dispatched via onError.
fn with_guard<F, R>(env: &mut jni::JNIEnv, entry: &str, f: F) -> R
where
    F: FnOnce(&mut jni::JNIEnv) -> R + std::panic::UnwindSafe,
    R: Default,
{
    let result = panic::catch_unwind(AssertUnwindSafe(|| {
        // Try to get attached env for error dispatch
        let _attached = get_env_attached();
        f(env)
    }));
    match result {
        Ok(r) => r,
        Err(panic_val) => {
            let msg = if let Some(s) = panic_val.downcast_ref::<&str>() {
                format!("{entry}: {s}")
            } else if let Some(s) = panic_val.downcast_ref::<String>() {
                format!("{entry}: {s}")
            } else {
                format!("{entry}: unknown panic")
            };
            warn!("{msg}");
            // Try to dispatch error to Kotlin via callback
            if let Ok(mut env) = get_env_attached() {
                // We need the callback from the engine. Use with_engine but don't panic again.
                let lock = ENGINE.get().expect("ENGINE not initialized");
                if let Ok(guard) = lock.lock() {
                    if let Some(ref state) = *guard {
                        jni_callback::dispatch_error(&mut env, &state.callback, 99, &msg);
                    }
                }
            }
            R::default()
        }
    }
}

pub(crate) fn get_env_attached() -> Result<jni::JNIEnv<'static>, jni::errors::Error> {
    let jvm = JAVA_VM.get().expect("JAVA_VM not initialized");
    jvm.attach_current_thread_as_daemon()
}

#[allow(dead_code)]
/// Helper that calls with_engine inside a panic-safe guard.
pub(crate) fn with_engine_guard<F, R>(f: F) -> R
where
    F: FnOnce(&mut EngineState) -> R + std::panic::UnwindSafe,
    R: Default,
{
    let result = panic::catch_unwind(AssertUnwindSafe(|| {
        with_engine(f)
    }));
    match result {
        Ok(r) => r,
        Err(panic_val) => {
            let msg = if let Some(s) = panic_val.downcast_ref::<&str>() {
                format!("engine guard: {s}")
            } else if let Some(s) = panic_val.downcast_ref::<String>() {
                format!("engine guard: {s}")
            } else {
                "engine guard: unknown panic".to_string()
            };
            warn!("{msg}");
            R::default()
        }
    }
}

// ── Lifecycle ──────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeInit<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_dir: JString<'local>,
    config_dir: JString<'local>,
    callback: JObject<'local>,
) {
    with_guard(&mut env, "nativeInit", |env| {
        let model_dir: String = match env.get_string(&model_dir) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid model_dir: {e}"));
                return;
            }
        };
        let config_dir: String = match env.get_string(&config_dir) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid config_dir: {e}"));
                return;
            }
        };
        let callback_ref = match env.new_global_ref(&callback) {
            Ok(r) => r,
            Err(e) => {
                let _ = env.throw_new("java/lang/RuntimeException", format!("Failed to create GlobalRef: {e}"));
                return;
            }
        };

        ensure_engine_init();
        {
            let lock = ENGINE.get().expect("ENGINE not initialized");
            let mut guard = lock.lock().expect("ENGINE lock poisoned");
            *guard = Some(EngineState::new(model_dir, config_dir, callback_ref));
        }

        with_engine(|state| {
            let cb = state.callback.clone();
            state.audio_pipeline.set_vad_callback(move |level: f32| {
                if let Ok(mut env) = get_env_attached() {
                    jni_callback::dispatch_vad_level(&mut env, &cb, level);
                }
            });

            // No audio callback needed for batch — audio accumulates in pipeline buffer
            // and will be processed at finalize via transcription_engine.run().
        });

        info!("Handy engine initialized");

        if let Ok(mut attached_env) = get_env_attached() {
            with_engine(|state| {
                jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
            });
        }
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDestroy<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_guard(&mut _env, "nativeDestroy", |_env| {
        with_engine(|state| {
            state.idle_watcher.stop();
            if state.is_recording {
                let _ = state.audio_pipeline.cancel();
                state.transcription_engine.cancel();
                state.is_recording = false;
            }
            state.transcription_engine.unload_model();
            state.model_loaded = false;
        });

        {
            let lock = ENGINE.get().expect("ENGINE not initialized");
            let mut guard = lock.lock().expect("ENGINE lock poisoned");
            *guard = None;
        }

        info!("Handy engine destroyed");
    });
}

// ── Engine Control ─────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeLoadModel<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_guard(&mut _env, "nativeLoadModel", |_env| {
        let mut attached_env = match get_env_attached() {
            Ok(e) => e,
            Err(e) => {
                warn!("nativeLoadModel: failed to attach JVM: {e}");
                return;
            }
        };

        with_engine(|state| {
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 1);

            let active_id = state.model_manager.active_model_id();
            info!("nativeLoadModel: active_model_id={:?}", active_id);

            let path = match state.model_manager.active_model_path() {
                Some(p) => p,
                None => {
                    let msg = format!("No active model selected (active_id={:?})", active_id);
                    jni_callback::dispatch_error(&mut attached_env, &state.callback, 1, &msg);
                    return;
                }
            };

            // Log model size (user chose to download it, so let them use it)
            if let Ok(meta) = std::fs::metadata(&path) {
                let size_mb = meta.len() / (1024 * 1024);
                if size_mb > 512 {
                    log::warn!("Loading large model: {}MB - may cause OOM on low-end devices", size_mb);
                }
            }

            if let Err(e) = state.transcription_engine.load_model(&path) {
                jni_callback::dispatch_error(&mut attached_env, &state.callback, 1, &e);
                return;
            }

            state.model_loaded = true;
            info!("Model loaded from: {:?}", path);
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeUnloadModel<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_guard(&mut _env, "nativeUnloadModel", |_env| {
        let mut attached_env = match get_env_attached() {
            Ok(e) => e,
            Err(e) => {
                warn!("nativeUnloadModel: failed to attach JVM: {e}");
                return;
            }
        };

        with_engine(|state| {
            state.idle_watcher.stop();
            state.transcription_engine.unload_model();
            state.model_loaded = false;
            info!("Model unloaded");
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeIsModelLoaded<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    with_guard(&mut _env, "nativeIsModelLoaded", |_env| {
        with_engine(|state| state.model_loaded) as jboolean
    })
}

// ── Recording / Transcription ──────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeStartRecording<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
    sample_rate: jint,
    _channel_count: jint,
) {
    with_guard(&mut _env, "nativeStartRecording", |_env| {
        let sample_rate = sample_rate as u32;
        info!("nativeStartRecording: sample_rate={}", sample_rate);

        let mut attached_env = match get_env_attached() {
            Ok(e) => e,
            Err(e) => {
                warn!("nativeStartRecording: failed to attach JVM: {e}");
                return;
            }
        };

        with_engine(|state| {
            info!("nativeStartRecording: model_loaded={}, is_recording={}", state.model_loaded, state.is_recording);

            // Stop idle watcher (recording is active now)
            state.idle_watcher.stop();

            // No streaming session needed — audio accumulates in pipeline buffer
            // for batch processing via session.run() at finalize time.

            if let Err(e) = state.audio_pipeline.start(sample_rate) {
                jni_callback::dispatch_error(&mut attached_env, &state.callback, 2, &e);
                return;
            }

            state.is_recording = true;
            info!("Recording started");
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 2);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativePushAudio<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    buffer: JObject<'local>,
    frame_count: jint,
) {
    with_guard(&mut env, "nativePushAudio", |env| {
        let jbuf: &JByteBuffer = &buffer.into();
        let ptr = match env.get_direct_buffer_address(jbuf) {
            Ok(p) => p,
            Err(e) => {
                let _ = env.throw_new(
                    "java/lang/IllegalArgumentException",
                    format!("Buffer must be a direct ByteBuffer: {e}"),
                );
                return;
            }
        };

        if ptr.is_null() {
            let _ = env.throw_new(
                "java/lang/IllegalArgumentException",
                "get_direct_buffer_address returned null",
            );
            return;
        }

        let capacity = match env.get_direct_buffer_capacity(jbuf) {
            Ok(c) => c as i64,
            Err(e) => {
                warn!("nativePushAudio: get_direct_buffer_capacity failed: {e}");
                -1i64
            }
        };

        let byte_count = (frame_count as i64) * 4;
        if capacity >= 0 && byte_count > capacity {
            let _ = env.throw_new(
                "java/lang/IllegalArgumentException",
                format!(
                    "frame_count {} exceeds buffer capacity {}",
                    frame_count, capacity
                ),
            );
            return;
        }

        unsafe {
            let samples: &[f32] = std::slice::from_raw_parts(ptr as *const f32, frame_count as usize);
            with_engine(|state| {
                if let Err(e) = state.audio_pipeline.push_audio(samples, frame_count as usize) {
                    warn!("nativePushAudio: push_audio failed: {e}");
                }
            });
        }
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeFinalizeStream<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_guard(&mut _env, "nativeFinalizeStream", |_env| {
        info!("nativeFinalizeStream");

        let mut env = match get_env_attached() {
            Ok(e) => e,
            Err(e) => {
                warn!("nativeFinalizeStream: failed to attach JVM: {e}");
                return;
            }
        };

        with_engine(|state| {
            let accumulated = match state.audio_pipeline.stop() {
                Ok(a) => a,
                Err(e) => {
                    warn!("nativeFinalizeStream: pipeline stop failed: {e}");
                    Vec::new()
                }
            };

            state.is_recording = false;

            if accumulated.is_empty() {
                warn!("nativeFinalizeStream: no audio captured");
                jni_callback::dispatch_error(&mut env, &state.callback, 3, "No audio captured");
            } else {
                info!("nativeFinalizeStream: {} samples accumulated", accumulated.len());

                // Run batch transcription (correct API for Whisper GGUF models)
                match state.transcription_engine.run(&accumulated) {
                    Ok(text) => {
                        info!("Final transcription ({} chars): {}", text.len(), text);
                        let processed = crate::transcription::engine::post_process(&text);
                        jni_callback::dispatch_transcription(&mut env, &state.callback, &processed, false);
                    }
                    Err(e) => {
                        warn!("nativeFinalizeStream: run failed: {e}");
                        jni_callback::dispatch_error(&mut env, &state.callback, 3, &e);
                    }
                }
            }

            // Start idle watcher
            state.idle_watcher.start();

            jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeCancelRecording<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_guard(&mut _env, "nativeCancelRecording", |_env| {
        info!("nativeCancelRecording");

        let mut env = match get_env_attached() {
            Ok(e) => e,
            Err(e) => {
                warn!("nativeCancelRecording: failed to attach JVM: {e}");
                return;
            }
        };

        with_engine(|state| {
            let _ = state.audio_pipeline.cancel();
            state.transcription_engine.cancel();
            state.is_recording = false;

            // Start idle watcher
            state.idle_watcher.start();

            jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeIsRecording<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    with_guard(&mut _env, "nativeIsRecording", |_env| {
        with_engine(|state| state.is_recording) as jboolean
    })
}

// ── Model Management ───────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeGetAvailableModels<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    with_guard(&mut env, "nativeGetAvailableModels", |env| {
        let models = with_engine(|state| state.model_manager.list_models());

        let json = match serde_json::to_string(&models) {
            Ok(s) => s,
            Err(e) => {
                warn!("nativeGetAvailableModels: serde_json failed: {e}");
                return std::ptr::null_mut();
            }
        };

        let output = match env.new_string(json) {
            Ok(s) => s,
            Err(e) => {
                warn!("nativeGetAvailableModels: failed to create string: {e}");
                return std::ptr::null_mut();
            }
        };
        output.into_raw()
    })
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDownloadModel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_id: JString<'local>,
) {
    with_guard(&mut env, "nativeDownloadModel", |env| {
        let model_id: String = match env.get_string(&model_id) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid model_id: {e}"));
                return;
            }
        };

        let mid = model_id.clone();
        info!("nativeDownloadModel: {model_id}");

        with_engine(|state| {
            let cb = state.callback.clone();
            let progress_cb = move |id: String, sofar: u64, total: u64| {
                if let Ok(mut env) = get_env_attached() {
                    jni_callback::dispatch_download_progress(
                        &mut env,
                        &cb,
                        &id,
                        sofar as i64,
                        total as i64,
                    );
                }
            };

            let cb2 = state.callback.clone();
            let complete_cb = move |id: String, success: bool, error: Option<String>| {
                if let Ok(mut env) = get_env_attached() {
                    jni_callback::dispatch_download_complete(
                        &mut env,
                        &cb2,
                        &id,
                        success,
                        error.as_deref(),
                    );

                }
            };

            state
                .model_manager
                .download_model(&mid, progress_cb, complete_cb);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeCancelDownload<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_guard(&mut _env, "nativeCancelDownload", |_env| {
        info!("nativeCancelDownload");
        with_engine(|state| {
            state.model_manager.cancel_download();
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDeleteModel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_id: JString<'local>,
) {
    with_guard(&mut env, "nativeDeleteModel", |env| {
        let model_id: String = match env.get_string(&model_id) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid model_id: {e}"));
                return;
            }
        };
        info!("nativeDeleteModel: {model_id}");
        with_engine(|state| {
            if let Err(e) = state.model_manager.delete_model(&model_id) {
                warn!("nativeDeleteModel: delete failed: {e}");
            }
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetActiveModel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_id: JString<'local>,
) {
    with_guard(&mut env, "nativeSetActiveModel", |env| {
        let model_id: String = match env.get_string(&model_id) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid model_id: {e}"));
                return;
            }
        };
        info!("nativeSetActiveModel: {model_id}");
        with_engine(|state| {
            if let Err(e) = state.model_manager.set_active_model(&model_id) {
                warn!("nativeSetActiveModel: set_active failed: {e}");
            }
        });
    });
}

// ── Settings ───────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetIdleTimeout<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
    idle_timeout_seconds: jint,
) {
    with_guard(&mut _env, "nativeSetIdleTimeout", |_env| {
        with_engine(|state| {
            state.set_idle_timeout(idle_timeout_seconds as u32);
            state.idle_watcher.set_timeout(idle_timeout_seconds as u32);
        });
        info!("Idle timeout set to {}s", idle_timeout_seconds);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetPostProcessEndpoint<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    endpoint: JString<'local>,
) {
    with_guard(&mut env, "nativeSetPostProcessEndpoint", |env| {
        let endpoint: String = match env.get_string(&endpoint) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid endpoint: {e}"));
                return;
            }
        };
        let ep = if endpoint.is_empty() {
            None
        } else {
            Some(endpoint)
        };
        with_engine(|state| {
            state.set_post_process_endpoint(ep);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetPostProcessApiKey<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    api_key: JString<'local>,
) {
    with_guard(&mut env, "nativeSetPostProcessApiKey", |env| {
        let api_key: String = match env.get_string(&api_key) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid api_key: {e}"));
                return;
            }
        };
        let key = if api_key.is_empty() {
            None
        } else {
            Some(api_key)
        };
        with_engine(|state| {
            state.set_post_process_api_key(key);
        });
    });
}

// ── History ────────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSaveHistory<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    transcription_text: JString<'local>,
    post_processed_text: JObject<'local>,
    wav_path: JObject<'local>,
) {
    with_guard(&mut env, "nativeSaveHistory", |env| {
        let text: String = match env.get_string(&transcription_text) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid text: {e}"));
                return;
            }
        };

        let post_processed: Option<String> = if post_processed_text.is_null() {
            None
        } else {
            match env.get_string(&JString::from(post_processed_text)) {
                Ok(s) => Some(s.into()),
                Err(e) => {
                    warn!("nativeSaveHistory: invalid post_processed_text: {e}");
                    None
                }
            }
        };

        let wav_path: Option<String> = if wav_path.is_null() {
            None
        } else {
            match env.get_string(&JString::from(wav_path)) {
                Ok(s) => Some(s.into()),
                Err(e) => {
                    warn!("nativeSaveHistory: invalid wav_path: {e}");
                    None
                }
            }
        };

        with_engine(|state| {
            match state
                .history_manager
                .save_entry(&text, post_processed.as_deref(), wav_path.as_deref())
            {
                Ok(id) => info!("Saved history entry {id}"),
                Err(e) => warn!("nativeSaveHistory: save failed: {e}"),
            }
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeGetHistory<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    offset: jint,
    limit: jint,
) -> jstring {
    with_guard(&mut env, "nativeGetHistory", |env| {
        let entries = with_engine_or(|state| {
            state
                .history_manager
                .get_entries(offset as i64, limit as i64)
        }, Ok(vec![]));

        let json = match entries {
            Ok(e) => serde_json::to_string(&e).unwrap_or_else(|_| "[]".to_string()),
            Err(e) => {
                warn!("nativeGetHistory: query failed: {e}");
                "[]".to_string()
            }
        };

        let output = match env.new_string(json) {
            Ok(s) => s,
            Err(e) => {
                warn!("nativeGetHistory: failed to create string: {e}");
                return std::ptr::null_mut();
            }
        };
        output.into_raw()
    })
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDeleteHistoryEntry<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
    entry_id: jlong,
) {
    with_guard(&mut _env, "nativeDeleteHistoryEntry", |_env| {
        with_engine(|state| {
            if let Err(e) = state.history_manager.delete_entry(entry_id as i64) {
                warn!("nativeDeleteHistoryEntry: delete failed: {e}");
            }
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeToggleHistorySaved<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
    entry_id: jlong,
) {
    with_guard(&mut _env, "nativeToggleHistorySaved", |_env| {
        with_engine(|state| {
            if let Err(e) = state.history_manager.toggle_saved(entry_id as i64) {
                warn!("nativeToggleHistorySaved: toggle failed: {e}");
            }
        });
    });
}
