use crate::engine::{ensure_engine_init, EngineState, ENGINE, JAVA_VM};
use crate::jni_callback;
use jni::objects::{JByteBuffer, JClass, JObject, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use log::{info, warn};

fn with_engine<F, R>(f: F) -> R
where
    F: FnOnce(&mut EngineState) -> R,
{
    let lock = ENGINE.get().expect("ENGINE not initialized");
    let mut guard = lock.lock().expect("ENGINE lock poisoned");
    let state = guard.as_mut().expect("ENGINE state not initialized");
    f(state)
}

fn get_env_attached() -> Result<jni::JNIEnv<'static>, jni::errors::Error> {
    let jvm = JAVA_VM.get().expect("JAVA_VM not initialized");
    jvm.attach_current_thread_as_daemon()
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

    // Set up VAD and audio callbacks
    with_engine(|state| {
        let cb = state.callback.clone();
        state.audio_pipeline.set_vad_callback(move |level: f32| {
            if let Ok(mut env) = get_env_attached() {
                jni_callback::dispatch_vad_level(&mut env, &cb, level);
            }
        });

        let router = state.transcription_engine.router();
        state.audio_pipeline.set_audio_callback(move |frame: Vec<f32>| {
            router.feed(frame);
        });
    });

    info!("Handy engine initialized");

    // Dispatch initial state: Idle
    if let Ok(mut attached_env) = get_env_attached() {
        with_engine(|state| {
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDestroy<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_engine(|state| {
        if state.is_recording {
            let _ = state.audio_pipeline.cancel();
            state.transcription_engine.cancel_stream();
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
}

// ── Engine Control ─────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeLoadModel<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    let mut attached_env = match get_env_attached() {
        Ok(e) => e,
        Err(e) => {
            warn!("nativeLoadModel: failed to attach JVM: {e}");
            return;
        }
    };

    with_engine(|state| {
        // Dispatch state 1 (Loading)
        jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 1);

        let path = match state.model_manager.active_model_path() {
            Some(p) => p,
            None => {
                jni_callback::dispatch_error(&mut attached_env, &state.callback, 1, "No active model selected");
                return;
            }
        };

        if let Err(e) = state.transcription_engine.load_model(&path) {
            jni_callback::dispatch_error(&mut attached_env, &state.callback, 1, &e);
            return;
        }

        state.model_loaded = true;
        info!("Model loaded from: {:?}", path);

        // Dispatch state 0 (Idle) on success
        jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeUnloadModel<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    let mut attached_env = match get_env_attached() {
        Ok(e) => e,
        Err(e) => {
            warn!("nativeUnloadModel: failed to attach JVM: {e}");
            return;
        }
    };

    with_engine(|state| {
        state.transcription_engine.unload_model();
        state.model_loaded = false;
        info!("Model unloaded");
        jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeIsModelLoaded<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    let loaded = with_engine(|state| state.model_loaded);
    loaded as jboolean
}

// ── Recording / Transcription ──────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeStartRecording<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    sample_rate: jint,
    _channel_count: jint,
) {
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
        let cb_partial = state.callback.clone();
        let on_partial = move |text: String| {
            if let Ok(mut env) = get_env_attached() {
                jni_callback::dispatch_transcription(&mut env, &cb_partial, &text, true);
            }
        };

        let cb_final = state.callback.clone();
        let on_final = move |text: String| {
            if let Ok(mut env) = get_env_attached() {
                jni_callback::dispatch_transcription(&mut env, &cb_final, &text, false);
            }
        };

        // Start the transcription stream
        match state.transcription_engine.start_stream(on_partial, on_final) {
            Ok(worker_id) => {
                state.worker_id = Some(worker_id);
            }
            Err(e) => {
                jni_callback::dispatch_error(&mut attached_env, &state.callback, 2, &e);
                return;
            }
        }

        // Start the audio pipeline
        if let Err(e) = state.audio_pipeline.start(sample_rate) {
            state.transcription_engine.cancel_stream();
            jni_callback::dispatch_error(&mut attached_env, &state.callback, 2, &e);
            return;
        }

        state.is_recording = true;
        info!("Recording started with worker_id={}", state.worker_id.unwrap_or(0));

        // Dispatch state 2 (Listening)
        jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 2);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativePushAudio<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    buffer: JObject<'local>,
    frame_count: jint,
) {
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
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeFinalizeStream<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    info!("nativeFinalizeStream");

    let mut env = match get_env_attached() {
        Ok(e) => e,
        Err(e) => {
            warn!("nativeFinalizeStream: failed to attach JVM: {e}");
            return;
        }
    };

    with_engine(|state| {
        // Stop pipeline and get accumulated audio
        let accumulated = match state.audio_pipeline.stop() {
            Ok(a) => a,
            Err(e) => {
                warn!("nativeFinalizeStream: pipeline stop failed: {e}");
                Vec::new()
            }
        };

        // Feed accumulated audio to router if not empty
        if !accumulated.is_empty() {
            state.transcription_engine.router().feed(accumulated);
        }

        // Finalize the stream
        if let Some(worker_id) = state.worker_id.take() {
            match state.transcription_engine.finalize_stream(worker_id) {
                Ok(text) => {
                    info!("Final transcription: {}", text);
                    let processed = crate::transcription::engine::post_process(&text);
                    jni_callback::dispatch_transcription(
                        &mut env,
                        &state.callback,
                        &processed,
                        false,
                    );
                }
                Err(e) => {
                    warn!("nativeFinalizeStream: finalize failed: {e}");
                    jni_callback::dispatch_error(&mut env, &state.callback, 3, &e);
                }
            }
        } else {
            warn!("nativeFinalizeStream: no active worker");
            jni_callback::dispatch_error(&mut env, &state.callback, 3, "No active stream");
        }

        state.is_recording = false;

        // Dispatch state 0 (Idle)
        jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeCancelRecording<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
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
        state.transcription_engine.cancel_stream();
        state.worker_id = None;
        state.is_recording = false;

        // Dispatch state 0 (Idle)
        jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeIsRecording<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    let recording = with_engine(|state| state.is_recording);
    recording as jboolean
}

// ── Model Management ───────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeGetAvailableModels<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
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
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDownloadModel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_id: JString<'local>,
) {
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
                if success {
                    let _ = env.call_method(cb2.as_obj(), "refreshModels", "()V", &[]);
                }
            }
        };

        state
            .model_manager
            .download_model(&mid, progress_cb, complete_cb);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeCancelDownload<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    info!("nativeCancelDownload");
    with_engine(|state| {
        state.model_manager.cancel_download();
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDeleteModel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_id: JString<'local>,
) {
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
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetActiveModel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_id: JString<'local>,
) {
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
}

// ── Settings ───────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetIdleTimeout<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    idle_timeout_seconds: jint,
) {
    with_engine(|state| {
        state.set_idle_timeout(idle_timeout_seconds as u32);
    });
    info!("Idle timeout set to {idle_timeout_seconds}s");
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetPostProcessEndpoint<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    endpoint: JString<'local>,
) {
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
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetPostProcessApiKey<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    api_key: JString<'local>,
) {
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
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeGetHistory<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    offset: jint,
    limit: jint,
) -> jstring {
    let entries = with_engine(|state| {
        state
            .history_manager
            .get_entries(offset as i64, limit as i64)
    });

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
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeDeleteHistoryEntry<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    entry_id: jlong,
) {
    with_engine(|state| {
        if let Err(e) = state.history_manager.delete_entry(entry_id as i64) {
            warn!("nativeDeleteHistoryEntry: delete failed: {e}");
        }
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeToggleHistorySaved<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    entry_id: jlong,
) {
    with_engine(|state| {
        if let Err(e) = state.history_manager.toggle_saved(entry_id as i64) {
            warn!("nativeToggleHistorySaved: toggle failed: {e}");
        }
    });
}
