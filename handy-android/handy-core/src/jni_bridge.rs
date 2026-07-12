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
    // Cancel any in-progress recording
    with_engine(|state| {
        state.set_recording(false);
    });

    // Drop the engine state by taking the Option
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
    // Stub: will delegate to ModelManager in Sprint 1
    with_engine(|state| {
        state.set_model_loaded(true);
    });

    info!("Model loaded (stub)");

    if let Ok(mut attached_env) = get_env_attached() {
        with_engine(|state| {
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeUnloadModel<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_engine(|state| {
        state.set_model_loaded(false);
    });

    info!("Model unloaded");

    if let Ok(mut attached_env) = get_env_attached() {
        with_engine(|state| {
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
        });
    }
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
        // TODO: Feed samples into audio pipeline (resampler → VAD → stream router)
        // engine.audio_engine.push_samples(samples);
        info!("nativePushAudio: received {} frames", samples.len());
    }
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeStartRecording<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    sample_rate: jint,
    channel_count: jint,
) {
    info!(
        "nativeStartRecording: sample_rate={}, channels={}",
        sample_rate, channel_count
    );

    with_engine(|state| {
        state.set_recording(true);
    });

    // Dispatch state change to Listening (2)
    if let Ok(mut attached_env) = get_env_attached() {
        with_engine(|state| {
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 2);
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeFinalizeStream<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    info!("nativeFinalizeStream");

    with_engine(|state| {
        state.set_recording(false);
    });

    // Dispatch final transcription (stub empty text)
    if let Ok(mut attached_env) = get_env_attached() {
        with_engine(|state| {
            jni_callback::dispatch_transcription(
                &mut attached_env,
                &state.callback,
                "",
                false,
            );
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeCancelRecording<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    info!("nativeCancelRecording");

    with_engine(|state| {
        state.set_recording(false);
    });

    // Return to Idle
    if let Ok(mut attached_env) = get_env_attached() {
        with_engine(|state| {
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
        });
    }
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
    // Stub: return empty JSON array
    let json = "[]";
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
    info!("nativeDownloadModel: {model_id} (stub)");
    let _ = env;
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeCancelDownload<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    info!("nativeCancelDownload (stub)");
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
    info!("nativeDeleteModel: {model_id} (stub)");
    let _ = env;
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
    info!("nativeSetActiveModel: {model_id} (stub)");
    let _ = env;
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
    let ep = if endpoint.is_empty() { None } else { Some(endpoint) };
    with_engine(|state| {
        state.set_post_process_endpoint(ep);
    });
    let _ = env;
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
    let key = if api_key.is_empty() { None } else { Some(api_key) };
    with_engine(|state| {
        state.set_post_process_api_key(key);
    });
    let _ = env;
}

// ── History ────────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSaveHistory<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    transcription_text: JString<'local>,
    post_processed_text: JString<'local>,
    wav_path: JString<'local>,
) {
    let text: String = match env.get_string(&transcription_text) {
        Ok(s) => s.into(),
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid text: {e}"));
            return;
        }
    };
    info!("nativeSaveHistory: {text} (stub)");
    let _ = text;
    let _ = post_processed_text;
    let _ = wav_path;
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeGetHistory<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    _offset: jint,
    _limit: jint,
) -> jstring {
    // Stub: return empty JSON array
    let output = match env.new_string("[]") {
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
    info!("nativeDeleteHistoryEntry: {entry_id} (stub)");
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeToggleHistorySaved<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    entry_id: jlong,
) {
    info!("nativeToggleHistorySaved: {entry_id} (stub)");
}
