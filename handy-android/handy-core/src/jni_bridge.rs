use crate::engine::{ensure_engine_init, EngineState, ENGINE, JAVA_VM};
use crate::history::retry as wav_decode;
use crate::jni_callback;
use jni::objects::{JByteBuffer, JClass, JObject, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jstring};
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

        // Sprint 25b — start the per-frame audio recording sink so the
        // consumer thread is alive and waiting before any recording
        // session begins. The sink dispatches each Float32 frame to
        // Kotlin's `EngineCallback.onAudioFrames` via the existing
        // jni_callback helper, feeding `RecordingRepository`.
        with_engine(|state| {
            state.recording_sink.start(state.callback.clone());
        });

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
            // Sprint 25b — stop the per-frame audio sink and detach from
            // the pipeline before tearing down the audio machinery.
            state.audio_pipeline.set_recording_sink(None);
            state.recording_sink.stop();
            if state.is_streaming {
                state.transcription_engine.cancel_stream();
                state.audio_pipeline.set_stream_router(None);
                state.is_streaming = false;
            }
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
            // Don't dispatch LOADING if recording is already active
            if !state.is_recording {
                jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 1);
            }

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
            // Don't dispatch IDLE if recording is active (model loaded mid-recording)
            if !state.is_recording {
                jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 0);
            }
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
            info!("nativeStartRecording: is_recording={}", state.is_recording);

            // Stop idle watcher (recording is active now)
            state.idle_watcher.stop();

            // Start microphone immediately. Model loading & streaming
            // setup happens asynchronously via nativeAttemptStreaming().
            // Audio accumulates in the pipeline buffer until then.
            state.audio_pipeline.set_stream_router(None);
            // Sprint 25b — attach the per-frame audio sink so each
            // resampled Float32 frame is forwarded into Kotlin's
            // RecordingRepository.pushFloatArrayFrames. The sink was
            // started once in nativeInit; here we just bind it to the
            // pipeline for this recording session. Detachment happens
            // in nativeFinalizeStream / nativeCancelRecording to drop
            // the pipeline ref while the sink is still alive (the
            // RecordingSink Arc outlives any single session).
            state.audio_pipeline.set_recording_sink(Some(state.recording_sink.clone()));

            if let Err(e) = state.audio_pipeline.start(sample_rate) {
                jni_callback::dispatch_error(&mut attached_env, &state.callback, 2, &e);
                return;
            }

            state.is_recording = true;
            info!("Recording started immediately");
            jni_callback::dispatch_state_change(&mut attached_env, &state.callback, 2);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeAttemptStreaming<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    with_guard(&mut _env, "nativeAttemptStreaming", |_env| {
        // Attach JVM for potential error dispatch, but don't need the env object here
        let _ = get_env_attached();

        with_engine(|state| {
            if !state.is_recording {
                info!("nativeAttemptStreaming: not recording, skipping");
                return false as jboolean;
            }
            if state.is_streaming || !state.model_loaded {
                return (state.is_streaming) as jboolean;
            }

            info!("nativeAttemptStreaming: attempting to start streaming mid-recording");

            let cb = state.callback.clone();
            let on_partial = move |text: String| {
                if let Ok(mut env) = get_env_attached() {
                    jni_callback::dispatch_transcription(&mut env, &cb, &text, true);
                }
            };

            // Try native streaming first (works for models that support
            // transcribe-cpp's streaming API like Nemotron Streaming).
            match state.transcription_engine.start_stream(on_partial.clone()) {
                Ok(true) => {
                    info!("nativeAttemptStreaming: streaming started");

                    // Drain accumulated audio buffer and feed it to the stream
                    let router = state.transcription_engine.stream_router().clone();
                    if let Ok(acc) = state.audio_pipeline.drain_buffer() {
                        if !acc.is_empty() {
                            info!("nativeAttemptStreaming: feeding {} accumulated samples", acc.len());
                            router.feed(acc);
                        }
                    }

                    state.audio_pipeline.set_stream_router(Some(router));
                    state.is_streaming = true;
                    true as jboolean
                }
                Ok(false) => {
                    // Native streaming not supported — fall back to periodic batch.
                    // This works with ALL models (Whisper, Parakeet, etc.) by running
                    // session.run() every ~3 seconds on the accumulated audio.
                    info!("nativeAttemptStreaming: model doesn't support native streaming, trying periodic batch");
                    match state.transcription_engine.start_periodic(on_partial) {
                        Ok(true) => {
                            info!("nativeAttemptStreaming: periodic batch started");
                            // Connect the stream router so audio frames from the pipeline
                            // are forwarded to the PeriodicWorker via the StreamRouter.
                            let router = state.transcription_engine.stream_router().clone();
                            // Feed any pre-streaming accumulated audio to the periodic worker
                            // so partial/final results include audio from the full recording.
                            if let Ok(acc) = state.audio_pipeline.drain_buffer() {
                                if !acc.is_empty() {
                                    info!("nativeAttemptStreaming: feeding {} accumulated samples to periodic worker", acc.len());
                                    router.feed(acc);
                                }
                            }
                            state.audio_pipeline.set_stream_router(Some(router));
                            state.is_streaming = true;
                            true as jboolean
                        }
                        Ok(false) => {
                            info!("nativeAttemptStreaming: periodic batch not started");
                            false as jboolean
                        }
                        Err(e) => {
                            warn!("nativeAttemptStreaming: start_periodic failed: {e}");
                            false as jboolean
                        }
                    }
                }
                Err(e) => {
                    warn!("nativeAttemptStreaming: start_stream failed: {e}");
                    false as jboolean
                }
            }
        })
    })
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
            state.is_recording = false;

            if state.is_streaming {
                // Streaming mode: pipeline.stop() flushes remaining audio to router
                let accumulated = match state.audio_pipeline.stop() {
                    Ok(a) => a,
                    Err(e) => {
                        warn!("nativeFinalizeStream: pipeline stop failed: {e}");
                        Vec::new()
                    }
                };
                state.audio_pipeline.set_stream_router(None);

                // Sprint 25b — detach the per-frame sink so subsequent
                // frames arriving in post-recording teardown are dropped.
                state.audio_pipeline.set_recording_sink(None);
                match state.transcription_engine.finalize_stream() {
                    Some(text) => {
                        info!("Streaming transcription complete ({} chars): {}", text.len(), text);
                        let processed = crate::transcription::engine::post_process(&text);
                        jni_callback::dispatch_transcription(&mut env, &state.callback, &processed, false);
                    }
                    None => {
                        warn!("nativeFinalizeStream: streaming returned None, falling back to batch");
                        if accumulated.is_empty() {
                            jni_callback::dispatch_error(&mut env, &state.callback, 3, "No audio captured");
                            jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
                        } else {
                            match state.transcription_engine.run(&accumulated) {
                                Ok(text) => {
                                    let processed = crate::transcription::engine::post_process(&text);
                                    jni_callback::dispatch_transcription(&mut env, &state.callback, &processed, false);
                                }
                                Err(e) => {
                                    jni_callback::dispatch_error(&mut env, &state.callback, 3, &e);
                                    jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
                                }
                            }
                        }
                    }
                }
            } else {
                // Sprint 25b — same sink detachment in batch mode.
                state.audio_pipeline.set_recording_sink(None);
                // Batch mode (existing code)
                let accumulated = match state.audio_pipeline.stop() {
                    Ok(a) => a,
                    Err(e) => {
                        warn!("nativeFinalizeStream: pipeline stop failed: {e}");
                        Vec::new()
                    }
                };

                if accumulated.is_empty() {
                    warn!("nativeFinalizeStream: no audio captured");
                    jni_callback::dispatch_error(&mut env, &state.callback, 3, "No audio captured");
                    jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
                } else {
                    info!("nativeFinalizeStream: {} samples accumulated", accumulated.len());

                    // Run batch transcription (correct API for Whisper GGUF models)
                    match state.transcription_engine.run(&accumulated) {
                        Ok(text) => {
                            info!("Final transcription ({} chars): {}", text.len(), text);
                            let processed = crate::transcription::engine::post_process(&text);
                            // Dispatch transcription result — Kotlin will handle state transitions
                            // (including auto-insert in IME mode). We do NOT dispatch STATE_IDLE here
                            // to avoid a StateFlow conflation race where IDLE overrides CONFIRM before
                            // the main-thread collector can process it.
                            jni_callback::dispatch_transcription(&mut env, &state.callback, &processed, false);
                        }
                        Err(e) => {
                            warn!("nativeFinalizeStream: run failed: {e}");
                            jni_callback::dispatch_error(&mut env, &state.callback, 3, &e);
                            jni_callback::dispatch_state_change(&mut env, &state.callback, 0);
                        }
                    }
                }
            }

            state.is_streaming = false;
            state.idle_watcher.start();
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
            // Sprint 25b — detach sink before cancel so frames
            // arriving mid-cancel don't leak into a stale recording.
            state.audio_pipeline.set_recording_sink(None);
            if state.is_streaming {
                state.transcription_engine.cancel_stream();
                let _ = state.audio_pipeline.cancel();
            } else {
                let _ = state.audio_pipeline.cancel();
                state.transcription_engine.cancel();
            }
            state.is_recording = false;
            state.is_streaming = false;

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

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeGetVadLevel<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jfloat {
    with_guard(&mut _env, "nativeGetVadLevel", |_env| {
        with_engine_or(|state| {
            let bits = state.audio_pipeline.vad_level.load(std::sync::atomic::Ordering::Relaxed);
            f32::from_bits(bits) as jfloat
        }, 0.0f32)
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

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetLanguage<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    language: JString<'local>,
) {
    with_guard(&mut env, "nativeSetLanguage", |env| {
        let lang: String = match env.get_string(&language) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid language: {e}"));
                return;
            }
        };
        let l = if lang.is_empty() || lang == "auto" {
            None
        } else {
            Some(lang)
        };
        with_engine(|state| {
            state.set_selected_language(l);
        });
    });
}

#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeSetAccelerationBackend<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    backend: JString<'local>,
) {
    with_guard(&mut env, "nativeSetAccelerationBackend", |env| {
        let backend: String = match env.get_string(&backend) {
            Ok(s) => s.into(),
            Err(e) => {
                let _ = env.throw_new("java/lang/IllegalArgumentException", format!("Invalid backend: {e}"));
                return;
            }
        };
        let b = if backend.is_empty() {
            None
        } else {
            Some(backend)
        };
        with_engine(|state| {
            state.set_acceleration_backend(b);
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
            if let Err(e) = state.history_manager.delete_entry(entry_id) {
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
            if let Err(e) = state.history_manager.toggle_saved(entry_id) {
                warn!("nativeToggleHistorySaved: toggle failed: {e}");
            }
        });
    });
}

// ── Sprint 25b: native retry ─────────────────────────────────────────────
//
// Sprint 24 declared `EngineBridge.nativeRetryHistoryEntry` in Kotlin
// (Grep that file: line 143). The symbol was missing from Rust, so the
// framework fell back to a simulated 2-second delay in
// `HistoryViewModel.retry()`. Sprint 25b lands the actual JNI binding.
//
// Concurrency note (Sprint 25b Thinker Q10): the function holds the
// global `ENGINE` Mutex for the duration of `transcription_engine.run`
// (typically ~3-10s for a 30s clip on mobile hardware). This means
// concurrent JNI calls (`nativeStartRecording`,
// `nativeCancelRecording`, `nativeSetIdleTimeout`, the IdleWatcher
// tick, etc.) will STALL during retry until transcription returns.
//
// Documented mitigations:
//   * `HistoryViewModel.UiState.retryingId` already disables the
//     Retry button on the History card while a retry is in flight.
//   * `nativeRetryHistoryEntry` bails out (`is_recording` check) if a
//     recording session is active, so the user can always start fresh
//     after Cancel.
//   * If the stall becomes a UX problem in Sprint 26+, the proper fix
//     is to make `TranscriptionEngine` lock-free for read-only
//     inference (wrap model in `Arc<Mutex<>>` and clone the handle
//     outside the global mutex). Tracked in PROGRESS.md.
#[no_mangle]
pub extern "system" fn Java_com_handy_app_bridge_EngineBridge_nativeRetryHistoryEntry<'local>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
    entry_id: jlong,
) -> jboolean {
    with_guard(&mut _env, "nativeRetryHistoryEntry", |_env| {
        let entry_id = entry_id;
        with_engine(|state| {
            // Bail out if a recording session is currently active — the
            // caller must cancel first. See Thinker Q10 caveat above.
            if state.is_recording {
                info!(
                    "nativeRetryHistoryEntry({entry_id}): rejected — recording active"
                );
                return false as jboolean;
            }

            // Phase 1: lookup the entry by id. Returns EntryNotFound
            // if a concurrent delete landed between retry() and
            // nativeGetHistory.
            let entry = match state.history_manager.get_entry_by_id(entry_id) {
                Ok(e) => e,
                Err(crate::history::manager::HistoryError::EntryNotFound(_)) => {
                    info!("nativeRetryHistoryEntry({entry_id}): entry not found");
                    return false as jboolean;
                }
                Err(e) => {
                    warn!("nativeRetryHistoryEntry({entry_id}): lookup failed: {e}");
                    return false as jboolean;
                }
            };

            // Phase 2: decode WAV. Strict 16-bit mono 16 kHz (matches
            // RecordingRepository.writeBytes shape; verified by the
            // WAV header test in RecordingRepositoryTest).
            let wav_path = match entry.wav_path.as_deref() {
                Some(p) => p,
                None => {
                    info!(
                        "nativeRetryHistoryEntry({entry_id}): entry has no wav_path"
                    );
                    return false as jboolean;
                }
            };
            let samples = match wav_decode::decode_wav_to_f32(wav_path) {
                Ok(s) => s,
                Err(e) => {
                    warn!(
                        "nativeRetryHistoryEntry({entry_id}): wav decode failed: {e}"
                    );
                    // Surface the format error to the Kotlin onError
                    // channel with a dedicated code (4) so the History
                    // card can render a localizable message.
                    if let Ok(mut env_attached) = get_env_attached() {
                        jni_callback::dispatch_error(
                            &mut env_attached,
                            &state.callback,
                            4,
                            &format!("retry failed: {e}"),
                        );
                    }
                    return false as jboolean;
                }
            };
            if samples.is_empty() {
                warn!("nativeRetryHistoryEntry({entry_id}): WAV has zero samples");
                return false as jboolean;
            }

            // Phase 3: run inference. This is the long pole (~3-10s
            // on mobile). Holds the ENGINE Mutex for the entire
            // duration — see Thinker Q10 caveat.
            let text = match state.transcription_engine.run(&samples) {
                Ok(t) => t,
                Err(e) => {
                    warn!(
                        "nativeRetryHistoryEntry({entry_id}): run failed: {e}"
                    );
                    return false as jboolean;
                }
            };

            // Phase 4: writeback. We pass `None` for `post_processed`
            // to INVALIDATE the previous LLM cleanup — keeping an old
            // post_processed while the raw text has been refreshed is
            // user-confusing (Thinker Q2 verdict).
            if state
                .history_manager
                .update_text_full(entry_id, &text, None)
                .is_err()
            {
                warn!(
                    "nativeRetryHistoryEntry({entry_id}): update_text_full failed"
                );
                return false as jboolean;
            }

            info!(
                "nativeRetryHistoryEntry({entry_id}) ok: {} chars",
                text.len()
            );
            true as jboolean
        })
    })
}
