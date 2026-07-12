pub mod engine;
pub mod jni_bridge;
pub mod jni_callback;
pub mod audio;
pub mod transcription;
pub mod model;
pub mod history;

use engine::JAVA_VM;
use log::info;

/// Called when the native library is loaded.
/// Stores the JavaVM reference for use by callback dispatch helpers.
#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: jni::JavaVM, _: *mut std::ffi::c_void) -> jni::sys::jint {
    let _ = JAVA_VM.set(vm);

    // Initialize transcribe-cpp native backends (loads ggml CPU backend)
    transcribe_cpp::init_logging();
    if let Err(e) = transcribe_cpp::init_backends_default() {
        log::warn!("transcribe-cpp backend init failed (CPU-only fallback): {e}");
    }

    info!("handy-core loaded");
    jni::sys::JNI_VERSION_1_6
}
