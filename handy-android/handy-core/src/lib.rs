pub mod engine;
pub mod jni_bridge;
pub mod jni_callback;
pub mod audio;
pub mod transcription;
pub mod model;
pub mod history;
pub mod idle_watcher;

use engine::JAVA_VM;
use log::info;

/// Called when the native library is loaded.
/// Stores the JavaVM reference for use by callback dispatch helpers.
#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: jni::JavaVM, _: *mut std::ffi::c_void) -> jni::sys::jint {
    let _ = JAVA_VM.set(vm);

    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("handy-core"),
    );

    // Initialize ONNX Runtime via ort (transcribe-rs dependency).
    // The commit() call returns a bool indicating whether this invocation
    // performed the one-time initialization. Errors are non-fatal.
    let _ = ort::init().with_name("handy").commit();

    info!("handy-core loaded");
    jni::sys::JNI_VERSION_1_6
}
