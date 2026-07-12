pub mod engine;
pub mod jni_bridge;
pub mod jni_callback;
// pub mod audio;    // TODO: Sprint 1
// pub mod stt;      // TODO: Sprint 1
// pub mod model;    // TODO: Sprint 1
// pub mod history;  // TODO: Sprint 1

use engine::JAVA_VM;
use log::info;

/// Called when the native library is loaded.
/// Stores the JavaVM reference for use by callback dispatch helpers.
#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: jni::JavaVM, _: *mut std::ffi::c_void) -> jni::sys::jint {
    let _ = JAVA_VM.set(vm);

    info!("handy-core loaded");

    jni::sys::JNI_VERSION_1_6
}
